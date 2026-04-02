package com.staryea.aiops.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staryea.aiops.config.BrowserSessionHubConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
public class BrowserSessionHubService {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");
    private static final MediaType TEXT_MEDIA_TYPE = MediaType.parse("text/plain");

    private final BrowserSessionHubConfig browserSessionHubConfig;
    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, String> sessionIdsByAgent = new ConcurrentHashMap<>();

    public BrowserSessionHubService(
            BrowserSessionHubConfig browserSessionHubConfig,
            OkHttpClient okHttpClient,
            ObjectMapper objectMapper
    ) {
        this.browserSessionHubConfig = browserSessionHubConfig;
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
    }

    public BrowserSessionView getStatus(String agentId) {
        HubSession session = findSession(agentId, false);
        if (session == null) {
            return BrowserSessionView.stopped(agentId);
        }
        return toView(agentId, session);
    }

    public BrowserSessionView getOrCreateSession(String agentId, String startUrl) {
        HubSession session = findSession(agentId, true);
        if (session == null) {
            session = createSession(agentId, startUrl);
        }
        return toView(agentId, session);
    }

    public BrowserSessionView touch(String agentId) {
        HubSession session = findSession(agentId, true);
        if (session == null) {
            return BrowserSessionView.stopped(agentId);
        }
        return toView(agentId, session);
    }

    public BrowserSessionView navigate(String agentId, String url) {
        String targetUrl = normalizeUrl(url);
        HubSession session = findSession(agentId, false);
        if (session == null) {
            session = createSession(agentId, targetUrl);
            return toView(agentId, session);
        }

        openUrlInBrowser(session, targetUrl);
        HubSession refreshed = touchSession(session.getSessionId());
        return toView(agentId, refreshed != null ? refreshed : session);
    }

    private HubSession findSession(String agentId, boolean touch) {
        String cachedSessionId = sessionIdsByAgent.get(agentId);
        if (hasText(cachedSessionId)) {
            HubSession cachedSession = getSession(cachedSessionId);
            if (isActiveSession(cachedSession, agentId)) {
                if (!touch) {
                    return cachedSession;
                }
                HubSession refreshed = touchSession(cachedSessionId);
                return refreshed != null ? refreshed : cachedSession;
            }
            sessionIdsByAgent.remove(agentId, cachedSessionId);
        }

        HubSession discovered = findSessionByOwnerId(agentId);
        if (discovered == null) {
            return null;
        }
        sessionIdsByAgent.put(agentId, discovered.getSessionId());
        if (!touch) {
            return discovered;
        }
        HubSession refreshed = touchSession(discovered.getSessionId());
        return refreshed != null ? refreshed : discovered;
    }

    private HubSession findSessionByOwnerId(String agentId) {
        HubSessionListResponse response = listSessions();
        if (response == null || response.getSessions() == null) {
            return null;
        }

        String ownerId = buildOwnerId(agentId);
        for (HubSession session : response.getSessions()) {
            if (ownerId.equals(session.getOwnerId()) && isActiveSession(session, agentId)) {
                return session;
            }
        }
        return null;
    }

    private HubSession createSession(String agentId, String startUrl) {
        CreateSessionPayload payload = new CreateSessionPayload();
        payload.setOwnerId(buildOwnerId(agentId));
        payload.setStartUrl(trimToNull(startUrl));
        payload.setPersistProfile(browserSessionHubConfig.isPersistProfile());
        payload.setKiosk(browserSessionHubConfig.isKiosk());
        payload.setMetadata(Collections.singletonMap("agent_id", agentId));

        String body = writeJson(payload);
        Request request = new Request.Builder()
                .url(buildHubUrl("/api/sessions"))
                .post(RequestBody.create(body, JSON_MEDIA_TYPE))
                .build();

        HubCreateSessionResponse response = executeJson(request, HubCreateSessionResponse.class);
        if (response == null || response.getSession() == null) {
            throw new IllegalStateException("Browser session hub did not return a session.");
        }

        HubSession session = response.getSession();
        sessionIdsByAgent.put(agentId, session.getSessionId());
        return session;
    }

    private HubSession getSession(String sessionId) {
        Request request = new Request.Builder()
                .url(buildHubUrl("/api/sessions/" + sessionId))
                .get()
                .build();
        return executeOptionalJson(request, HubSession.class);
    }

    private HubSession touchSession(String sessionId) {
        Request request = new Request.Builder()
                .url(buildHubUrl("/api/sessions/" + sessionId + "/touch"))
                .post(RequestBody.create("", JSON_MEDIA_TYPE))
                .build();
        return executeOptionalJson(request, HubSession.class);
    }

    private HubSessionListResponse listSessions() {
        Request request = new Request.Builder()
                .url(buildHubUrl("/api/sessions"))
                .get()
                .build();
        return executeOptionalJson(request, HubSessionListResponse.class);
    }

    private void openUrlInBrowser(HubSession session, String targetUrl) {
        String encodedUrl = encodeUrl(targetUrl);
        String baseUrl = stripTrailingSlash(session.getCdpHttpEndpoint());
        String openUrl = baseUrl + "/json/new?" + encodedUrl;

        Request putRequest = new Request.Builder()
                .url(openUrl)
                .put(RequestBody.create("", TEXT_MEDIA_TYPE))
                .build();

        if (executeCdpOpenRequest(putRequest, session.getSessionId(), targetUrl)) {
            return;
        }

        Request getRequest = new Request.Builder()
                .url(openUrl)
                .get()
                .build();

        if (!executeCdpOpenRequest(getRequest, session.getSessionId(), targetUrl)) {
            throw new IllegalStateException("Failed to open URL in remote browser: " + targetUrl);
        }
    }

    private boolean executeCdpOpenRequest(Request request, String sessionId, String targetUrl) {
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return true;
            }
            String responseBody = readBody(response.body());
            log.warn(
                    "CDP navigation request failed: sessionId={}, code={}, url={}, body={}",
                    sessionId,
                    response.code(),
                    targetUrl,
                    responseBody
            );
            return false;
        } catch (IOException e) {
            log.warn("CDP navigation request errored: sessionId={}, url={}, error={}", sessionId, targetUrl, e.getMessage());
            return false;
        }
    }

    private BrowserSessionView toView(String agentId, HubSession session) {
        BrowserSessionView view = new BrowserSessionView();
        view.setAgentId(agentId);
        view.setRunning(session != null && isRunningStatus(session.getStatus()));
        view.setStatus(session != null ? session.getStatus() : "stopped");
        view.setSessionId(session != null ? session.getSessionId() : null);
        view.setOwnerId(session != null ? session.getOwnerId() : buildOwnerId(agentId));
        view.setPreviewUrl(session != null ? session.getPreviewUrl() : null);
        view.setRfbUrl(session != null ? buildRfbUrl(session.getPreviewUrl()) : null);
        view.setCdpHttpEndpoint(session != null ? session.getCdpHttpEndpoint() : null);
        return view;
    }

    private boolean isActiveSession(HubSession session, String agentId) {
        return session != null
                && buildOwnerId(agentId).equals(session.getOwnerId())
                && isRunningStatus(session.getStatus());
    }

    private boolean isRunningStatus(String status) {
        return "running".equalsIgnoreCase(status) || "starting".equalsIgnoreCase(status);
    }

    private String buildOwnerId(String agentId) {
        return "agent:" + agentId;
    }

    private String buildHubUrl(String path) {
        return stripTrailingSlash(browserSessionHubConfig.getBaseUrl()) + path;
    }

    private String stripTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String buildRfbUrl(String previewUrl) {
        if (!hasText(previewUrl)) {
            return null;
        }

        URI parsed;
        try {
            parsed = new URI(previewUrl);
        } catch (URISyntaxException e) {
            return null;
        }

        if (!hasText(parsed.getHost())) {
            return null;
        }

        String scheme = "https".equalsIgnoreCase(parsed.getScheme()) ? "wss" : "ws";
        String path = getQueryParam(parsed.getRawQuery(), "path");
        String wsPath = hasText(path) ? path : "websockify";
        if (!wsPath.startsWith("/")) {
            wsPath = "/" + wsPath;
        }

        try {
            return new URI(
                    scheme,
                    parsed.getUserInfo(),
                    parsed.getHost(),
                    parsed.getPort(),
                    wsPath,
                    null,
                    null
            ).toString();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private String normalizeUrl(String url) {
        String trimmed = trimToNull(url);
        if (trimmed == null) {
            throw new IllegalArgumentException("Browser URL must not be empty.");
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return "https://" + trimmed;
    }

    private String encodeUrl(String targetUrl) {
        try {
            return URLEncoder.encode(targetUrl, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 encoding is unavailable.", e);
        }
    }

    private String getQueryParam(String rawQuery, String key) {
        if (!hasText(rawQuery) || !hasText(key)) {
            return null;
        }

        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && key.equals(parts[0])) {
                try {
                    return URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name());
                } catch (UnsupportedEncodingException e) {
                    return parts[1];
                }
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize browser session request.", e);
        }
    }

    private String readBody(ResponseBody body) throws IOException {
        return body != null ? body.string() : "";
    }

    private <T> T executeJson(Request request, Class<T> responseType) {
        try (Response response = okHttpClient.newCall(request).execute()) {
            String responseBody = readBody(response.body());
            if (!response.isSuccessful()) {
                throw new IllegalStateException(
                        "Browser session hub request failed: " + response.code() + " " + responseBody
                );
            }
            return objectMapper.readValue(responseBody, responseType);
        } catch (IOException e) {
            throw new IllegalStateException("Browser session hub request failed.", e);
        }
    }

    private <T> T executeOptionalJson(Request request, Class<T> responseType) {
        try (Response response = okHttpClient.newCall(request).execute()) {
            String responseBody = readBody(response.body());
            if (!response.isSuccessful()) {
                log.warn("Browser session hub request returned {} for {}: {}", response.code(), request.url(), responseBody);
                return null;
            }
            return objectMapper.readValue(responseBody, responseType);
        } catch (IOException e) {
            log.warn("Browser session hub request failed for {}: {}", request.url(), e.getMessage());
            return null;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrowserSessionView {
        private String agentId;
        private boolean running;
        private String status;
        private String sessionId;
        private String ownerId;
        private String previewUrl;
        private String rfbUrl;
        private String cdpHttpEndpoint;

        public static BrowserSessionView stopped(String agentId) {
            return new BrowserSessionView(agentId, false, "stopped", null, "agent:" + agentId, null, null, null);
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static class CreateSessionPayload {
        @JsonProperty("owner_id")
        private String ownerId;

        @JsonProperty("start_url")
        private String startUrl;

        @JsonProperty("persist_profile")
        private boolean persistProfile;

        private Boolean kiosk;

        private Map<String, String> metadata = new LinkedHashMap<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class HubCreateSessionResponse {
        private HubSession session;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class HubSessionListResponse {
        private List<HubSession> sessions = Collections.emptyList();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class HubSession {
        @JsonProperty("session_id")
        private String sessionId;

        @JsonProperty("owner_id")
        private String ownerId;

        private String status;

        @JsonProperty("preview_url")
        private String previewUrl;

        @JsonProperty("cdp_http_endpoint")
        private String cdpHttpEndpoint;
    }
}
