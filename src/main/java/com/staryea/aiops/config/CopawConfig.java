package com.staryea.aiops.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "copaw")
public class CopawConfig {

    private String baseUrl = "http://127.0.0.1:8088";

    private String agentId = "default";

    private String defaultChannel = "console";
}
