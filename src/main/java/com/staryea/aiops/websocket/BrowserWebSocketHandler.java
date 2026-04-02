package com.staryea.aiops.websocket;

import com.staryea.aiops.config.CopawConfig;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * WebSocket proxy handler that forwards browser live-view messages
 * between the frontend and CoPaw backend.
 *
 * Frontend ←→ this handler ←→ CoPaw /browser/ws
 *
 * Supports both text (JSON metadata) and binary (JPEG frames) messages.
 * Buffers frontend messages until the upstream CoPaw connection is ready.
 */
@Slf4j
@Component
public class BrowserWebSocketHandler extends AbstractWebSocketHandler {

    private final CopawConfig copawConfig;
    private final OkHttpClient okHttpClient;

    /**
     * Maps each frontend session to its upstream CoPaw WebSocket connection.
     */
    private final Map<String, okhttp3.WebSocket> upstreamConnections = new ConcurrentHashMap<>();

    /**
     * Buffers messages from the frontend until the upstream CoPaw connection is ready.
     */
    private final Map<String, Queue<String>> pendingMessages = new ConcurrentHashMap<>();

    public BrowserWebSocketHandler(CopawConfig copawConfig, OkHttpClient okHttpClient) {
        this.copawConfig = copawConfig;
        this.okHttpClient = okHttpClient;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String agentId = extractParam(session, "agent_id", copawConfig.getAgentId());
        log.info("Browser WS connected: sessionId={}, agentId={}", session.getId(), agentId);

        // Prepare message buffer
        pendingMessages.put(session.getId(), new ConcurrentLinkedQueue<>());

        // Build upstream CoPaw WebSocket URL
        String copawWsUrl = buildCopawWsUrl(agentId);
        log.debug("Connecting to CoPaw WS: {}", copawWsUrl);

        Request request = new Request.Builder()
                .url(copawWsUrl)
                .build();

        // Connect to CoPaw and relay messages back to the frontend
        okHttpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(okhttp3.WebSocket webSocket, Response response) {
                log.debug("Upstream CoPaw WS connected for session: {}", session.getId());
                upstreamConnections.put(session.getId(), webSocket);

                // Flush buffered messages
                Queue<String> queue = pendingMessages.get(session.getId());
                if (queue != null) {
                    String msg;
                    while ((msg = queue.poll()) != null) {
                        log.debug("Flushing buffered message to CoPaw: {}", msg);
                        webSocket.send(msg);
                    }
                }
            }

            @Override
            public void onMessage(okhttp3.WebSocket webSocket, String text) {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(text));
                    }
                } catch (IOException e) {
                    log.error("Error forwarding text to frontend: {}", e.getMessage());
                }
            }

            @Override
            public void onMessage(okhttp3.WebSocket webSocket, okio.ByteString bytes) {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new BinaryMessage(ByteBuffer.wrap(bytes.toByteArray())));
                    }
                } catch (IOException e) {
                    log.error("Error forwarding binary to frontend: {}", e.getMessage());
                }
            }

            @Override
            public void onClosing(okhttp3.WebSocket webSocket, int code, String reason) {
                log.debug("Upstream CoPaw WS closing: code={}, reason={}", code, reason);
                webSocket.close(code, reason);
                cleanup(session.getId());
                try {
                    if (session.isOpen()) {
                        session.close(new CloseStatus(code, reason));
                    }
                } catch (IOException e) {
                    log.error("Error closing frontend WS: {}", e.getMessage());
                }
            }

            @Override
            public void onFailure(okhttp3.WebSocket webSocket, Throwable t, Response response) {
                log.error("Upstream CoPaw WS failed: {}", t.getMessage());
                cleanup(session.getId());
                try {
                    if (session.isOpen()) {
                        session.close(CloseStatus.SERVER_ERROR);
                    }
                } catch (IOException e) {
                    log.error("Error closing frontend WS after upstream failure: {}", e.getMessage());
                }
            }
        });
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String sessionId = session.getId();
        okhttp3.WebSocket upstream = upstreamConnections.get(sessionId);
        if (upstream != null) {
            // Upstream ready, send directly
            upstream.send(message.getPayload());
        } else {
            // Upstream not ready yet, buffer the message
            Queue<String> queue = pendingMessages.get(sessionId);
            if (queue != null) {
                log.debug("Buffering message (upstream not ready): {}", message.getPayload());
                queue.add(message.getPayload());
            }
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        okhttp3.WebSocket upstream = upstreamConnections.get(session.getId());
        if (upstream != null) {
            upstream.send(okio.ByteString.of(message.getPayload()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("Browser WS disconnected: sessionId={}, status={}", session.getId(), status);
        String sessionId = session.getId();
        okhttp3.WebSocket upstream = upstreamConnections.remove(sessionId);
        pendingMessages.remove(sessionId);
        if (upstream != null) {
            upstream.close(1000, "Frontend disconnected");
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Browser WS transport error: sessionId={}, error={}", session.getId(), exception.getMessage());
        String sessionId = session.getId();
        okhttp3.WebSocket upstream = upstreamConnections.remove(sessionId);
        pendingMessages.remove(sessionId);
        if (upstream != null) {
            upstream.close(1011, "Transport error");
        }
    }

    private void cleanup(String sessionId) {
        upstreamConnections.remove(sessionId);
        pendingMessages.remove(sessionId);
    }

    private String buildCopawWsUrl(String agentId) {
        String baseUrl = copawConfig.getBaseUrl();
        String wsBase = baseUrl.replaceFirst("^http", "ws");
        return UriComponentsBuilder.fromUriString(wsBase)
                .path("/api/browser/ws")
                .queryParam("agent_id", agentId)
                .build()
                .toUriString();
    }

    private String extractParam(WebSocketSession session, String name, String defaultValue) {
        URI uri = session.getUri();
        if (uri == null) return defaultValue;

        String query = uri.getQuery();
        if (query == null) return defaultValue;

        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) {
                return kv[1];
            }
        }
        return defaultValue;
    }
}
