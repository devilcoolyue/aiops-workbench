package com.staryea.aiops.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "browser-hub")
public class BrowserSessionHubConfig {

    private String baseUrl = "http://127.0.0.1:8091";

    private boolean persistProfile = true;

    private boolean kiosk = false;
}
