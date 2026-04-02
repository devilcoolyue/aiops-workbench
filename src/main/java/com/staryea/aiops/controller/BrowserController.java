package com.staryea.aiops.controller;

import com.staryea.aiops.service.BrowserSessionHubService;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/browser")
public class BrowserController {

    private final BrowserSessionHubService browserSessionHubService;

    public BrowserController(BrowserSessionHubService browserSessionHubService) {
        this.browserSessionHubService = browserSessionHubService;
    }

    @GetMapping("/status")
    public BrowserSessionHubService.BrowserSessionView getStatus(
            @RequestParam(defaultValue = "default") String agent_id
    ) {
        return browserSessionHubService.getStatus(agent_id);
    }

    @GetMapping("/preview")
    public BrowserSessionHubService.BrowserSessionView getPreview(
            @RequestParam(defaultValue = "default") String agent_id,
            @RequestParam(required = false) String start_url
    ) {
        return browserSessionHubService.getOrCreateSession(agent_id, start_url);
    }

    @PostMapping("/touch")
    public BrowserSessionHubService.BrowserSessionView touch(
            @RequestParam(defaultValue = "default") String agent_id
    ) {
        return browserSessionHubService.touch(agent_id);
    }

    @PostMapping("/navigate")
    public BrowserSessionHubService.BrowserSessionView navigate(
            @RequestParam(defaultValue = "default") String agent_id,
            @RequestBody BrowserNavigateRequest request
    ) {
        return browserSessionHubService.navigate(agent_id, request.getUrl());
    }

    @GetMapping("/tabs")
    public List<Map<String, Object>> getTabs() {
        return Collections.emptyList();
    }

    @Data
    public static class BrowserNavigateRequest {
        private String url;
    }
}
