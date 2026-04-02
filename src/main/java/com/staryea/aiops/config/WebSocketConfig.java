package com.staryea.aiops.config;

import com.staryea.aiops.websocket.BrowserWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final BrowserWebSocketHandler browserWebSocketHandler;

    public WebSocketConfig(BrowserWebSocketHandler browserWebSocketHandler) {
        this.browserWebSocketHandler = browserWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(browserWebSocketHandler, "/api/browser/ws")
                .setAllowedOriginPatterns("*");
    }
}
