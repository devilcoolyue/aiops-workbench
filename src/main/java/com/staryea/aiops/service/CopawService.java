package com.staryea.aiops.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.staryea.aiops.config.CopawConfig;
import com.staryea.aiops.model.ChatRequest;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for communicating with CoPaw backend via SSE.
 * Forwards user chat requests to CoPaw and relays the SSE stream back.
 */
@Slf4j
@Service
public class CopawService {

    private final CopawConfig copawConfig;
    private final OkHttpClient okHttpClient;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public CopawService(CopawConfig copawConfig, OkHttpClient okHttpClient) {
        this.copawConfig = copawConfig;
        this.okHttpClient = okHttpClient;
    }

    /**
     * Send a chat request to CoPaw and stream the response back via SseEmitter.
     */
    public SseEmitter streamChat(ChatRequest chatRequest) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 minutes timeout
        // Track whether the client has disconnected
        final java.util.concurrent.atomic.AtomicBoolean disconnected = new java.util.concurrent.atomic.AtomicBoolean(false);

        executorService.execute(() -> {
            Response response = null;
            try {
                String agentId = (chatRequest.getAgentId() != null && !chatRequest.getAgentId().isEmpty())
                        ? chatRequest.getAgentId() : copawConfig.getAgentId();
                String url = buildChatUrl(agentId);
                String requestBody = buildRequestBody(chatRequest);

                log.debug("Sending request to CoPaw: {} body: {}", url, requestBody);

                Request request = new Request.Builder()
                        .url(url)
                        .post(RequestBody.create(requestBody, okhttp3.MediaType.parse("application/json")))
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Accept", "text/event-stream")
                        .addHeader("X-Agent-Id", agentId)
                        .build();

                response = okHttpClient.newCall(request).execute();

                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    log.error("CoPaw returned error: {} - {}", response.code(), errorBody);
                    if (!disconnected.get()) {
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data("{\"error\": \"CoPaw service error: " + response.code() + "\"}", MediaType.TEXT_PLAIN));
                        emitter.complete();
                    }
                    return;
                }

                ResponseBody body = response.body();
                if (body == null) {
                    if (!disconnected.get()) {
                        emitter.complete();
                    }
                    return;
                }

                // Read SSE stream line by line and forward to the frontend
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8))) {
                    String line;
                    String currentEventName = null;
                    StringBuilder currentEventData = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        if (disconnected.get()) {
                            log.debug("Client disconnected, stopping CoPaw stream relay");
                            break;
                        }

                        // Empty line marks the end of a single SSE event.
                        if (line.isEmpty()) {
                            emitSseEvent(emitter, currentEventName, currentEventData);
                            currentEventName = null;
                            currentEventData.setLength(0);
                            continue;
                        }

                        if (line.startsWith(":")) {
                            // SSE comment / heartbeat line
                            continue;
                        }

                        if (line.startsWith("event:")) {
                            currentEventName = line.substring(6).trim();
                            continue;
                        }

                        if (line.startsWith("data:")) {
                            String dataPart = line.substring(5);
                            if (dataPart.startsWith(" ")) {
                                dataPart = dataPart.substring(1);
                            }
                            if (currentEventData.length() > 0) {
                                currentEventData.append('\n');
                            }
                            currentEventData.append(dataPart);
                        }
                    }

                    // Flush the trailing event
                    if (!disconnected.get()) {
                        emitSseEvent(emitter, currentEventName, currentEventData);
                    }
                }

                if (!disconnected.get()) {
                    emitter.complete();
                }
                log.debug("SSE stream completed for session: {}", chatRequest.getSessionId());

            } catch (IOException e) {
                if (disconnected.get()) {
                    log.debug("Stream relay ended (client disconnected)");
                } else {
                    log.error("Error streaming from CoPaw", e);
                    try { emitter.completeWithError(e); } catch (Exception ignored) {}
                }
            } finally {
                if (response != null) {
                    response.close();
                }
            }
        });

        emitter.onCompletion(() -> {
            disconnected.set(true);
            log.debug("SseEmitter completed");
        });
        emitter.onTimeout(() -> {
            disconnected.set(true);
            log.warn("SseEmitter timed out");
        });
        emitter.onError(e -> {
            disconnected.set(true);
            log.debug("SseEmitter client disconnected");
        });

        return emitter;
    }

    /**
     * Stop a running chat session in CoPaw.
     */
    public boolean stopChat(String chatId) {
        return stopChat(chatId, null);
    }

    public boolean stopChat(String chatId, String agentId) {
        try {
            String resolvedAgentId = resolveAgentId(agentId);
            String url = copawConfig.getBaseUrl() + "/api/agents/" + resolvedAgentId
                    + "/console/chat/stop?chat_id=" + chatId;

            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create("", okhttp3.MediaType.parse("application/json")))
                    .addHeader("X-Agent-Id", resolvedAgentId)
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    JSONObject result = JSONObject.parseObject(response.body().string());
                    return result.getBooleanValue("stopped");
                }
            }
        } catch (IOException e) {
            log.error("Error stopping chat in CoPaw", e);
        }
        return false;
    }

    private String buildChatUrl() {
        return copawConfig.getBaseUrl() + "/api/agents/" + copawConfig.getAgentId() + "/console/chat";
    }

    private void emitSseEvent(SseEmitter emitter, String eventName, StringBuilder eventData) throws IOException {
        if (eventData.length() == 0) {
            return;
        }

        String payload = compactJson(eventData.toString());
        SseEmitter.SseEventBuilder eventBuilder = SseEmitter.event();
        if (eventName != null && !eventName.isEmpty()) {
            eventBuilder.name(eventName);
        }
        emitter.send(eventBuilder.data(payload, MediaType.TEXT_PLAIN));
    }

    private String compactJson(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }

        try {
            Object parsed = JSON.parse(raw);
            return JSON.toJSONString(parsed);
        } catch (Exception ignored) {
            return raw;
        }
    }

    /**
     * List all available agents from CoPaw.
     */
    public String listAgents() {
        try {
            String url = copawConfig.getBaseUrl() + "/api/agents";
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    return response.body().string();
                }
            }
        } catch (IOException e) {
            log.error("Error listing agents from CoPaw", e);
        }
        return "{\"agents\":[]}";
    }

    // ========== Chat Management (proxy to CoPaw /chats API) ==========

    /**
     * List chats from CoPaw with optional filters.
     */
    public String listChats(String userId, String channel) {
        return listChats(userId, channel, null);
    }

    public String listChats(String userId, String channel, String agentId) {
        try {
            String resolvedAgentId = resolveAgentId(agentId);
            StringBuilder url = new StringBuilder(buildChatsUrl(resolvedAgentId));
            String sep = "?";
            if (userId != null && !userId.isEmpty()) {
                url.append(sep).append("user_id=").append(userId);
                sep = "&";
            }
            if (channel != null && !channel.isEmpty()) {
                url.append(sep).append("channel=").append(channel);
            }

            Request request = new Request.Builder()
                    .url(url.toString())
                    .get()
                    .addHeader("X-Agent-Id", resolvedAgentId)
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    return response.body().string();
                }
            }
        } catch (IOException e) {
            log.error("Error listing chats from CoPaw", e);
        }
        return "[]";
    }

    /**
     * Create a new chat in CoPaw.
     */
    public String createChat(String userId, String name) {
        return createChat(userId, name, null);
    }

    public String createChat(String userId, String name, String agentId) {
        try {
            String resolvedAgentId = resolveAgentId(agentId);
            String sessionId = copawConfig.getDefaultChannel() + ":" + UUID.randomUUID().toString();
            JSONObject body = new JSONObject();
            body.put("session_id", sessionId);
            body.put("user_id", userId != null ? userId : "default");
            body.put("channel", copawConfig.getDefaultChannel());
            body.put("name", name != null ? name : "New Chat");

            Request request = new Request.Builder()
                    .url(buildChatsUrl(resolvedAgentId))
                    .post(RequestBody.create(body.toJSONString(), okhttp3.MediaType.parse("application/json")))
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-Agent-Id", resolvedAgentId)
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    return response.body().string();
                }
            }
        } catch (IOException e) {
            log.error("Error creating chat in CoPaw", e);
        }
        return null;
    }

    /**
     * Get chat detail (with messages) from CoPaw.
     */
    public String getChat(String chatId) {
        return getChat(chatId, null);
    }

    public String getChat(String chatId, String agentId) {
        try {
            String resolvedAgentId = resolveAgentId(agentId);
            Request request = new Request.Builder()
                    .url(buildChatsUrl(resolvedAgentId) + "/" + chatId)
                    .get()
                    .addHeader("X-Agent-Id", resolvedAgentId)
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (response.body() != null) {
                    return response.body().string();
                }
            }
        } catch (IOException e) {
            log.error("Error getting chat from CoPaw", e);
        }
        return null;
    }

    /**
     * Delete a chat in CoPaw.
     */
    public String deleteChat(String chatId) {
        return deleteChat(chatId, null);
    }

    public String deleteChat(String chatId, String agentId) {
        try {
            String resolvedAgentId = resolveAgentId(agentId);
            Request request = new Request.Builder()
                    .url(buildChatsUrl(resolvedAgentId) + "/" + chatId)
                    .delete()
                    .addHeader("X-Agent-Id", resolvedAgentId)
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (response.body() != null) {
                    return response.body().string();
                }
            }
        } catch (IOException e) {
            log.error("Error deleting chat from CoPaw", e);
        }
        return null;
    }

    private String buildChatsUrl(String agentId) {
        return copawConfig.getBaseUrl() + "/api/agents/" + resolveAgentId(agentId) + "/chats";
    }

    private String buildChatUrl(String agentId) {
        return copawConfig.getBaseUrl() + "/api/agents/" + resolveAgentId(agentId) + "/console/chat";
    }

    private String resolveAgentId(String agentId) {
        return (agentId != null && !agentId.isEmpty()) ? agentId : copawConfig.getAgentId();
    }

    private String buildRequestBody(ChatRequest chatRequest) {
        JSONObject body = new JSONObject();

        // Build input as List[Message] — each Message has role, type, content
        JSONArray input = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("type", "message");

        // content is a list of Content objects, e.g. [{"type":"text","text":"..."}]
        JSONArray contentParts = new JSONArray();
        JSONObject textContent = new JSONObject();
        textContent.put("type", "text");
        textContent.put("text", chatRequest.getMessage());
        contentParts.add(textContent);

        message.put("content", contentParts);
        input.add(message);

        body.put("input", input);
        body.put("session_id", chatRequest.getSessionId());
        body.put("user_id", chatRequest.getUserId() != null ? chatRequest.getUserId() : "default");
        body.put("channel", copawConfig.getDefaultChannel());
        body.put("stream", chatRequest.isStream());
        body.put("reconnect", chatRequest.isReconnect());

        return body.toJSONString();
    }
}
