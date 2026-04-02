package com.staryea.aiops.controller;

import com.staryea.aiops.model.ChatRequest;
import com.staryea.aiops.model.Result;
import com.staryea.aiops.service.CopawService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Chat controller that relays all requests to CoPaw.
 * No local session/message storage — CoPaw is the single source of truth.
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final CopawService copawService;

    public ChatController(CopawService copawService) {
        this.copawService = copawService;
    }

    /**
     * Send a message and receive streaming SSE response from CoPaw.
     */
    @PostMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter streamChat(@RequestBody ChatRequest request) {
        if (isBlank(request.getAgentId())) {
            request.setAgentId("default");
        }
        log.info("Chat stream request: sessionId={}, agentId={}, message={}",
                request.getSessionId(), request.getAgentId(), request.getMessage());
        return copawService.streamChat(request);
    }

    /**
     * Stop a running chat.
     */
    @PostMapping("/stop")
    public Result<Boolean> stopChat(@RequestParam String chatId,
                                    @RequestParam(defaultValue = "default") String agentId) {
        boolean stopped = copawService.stopChat(chatId, agentId);
        return Result.success(stopped);
    }

    // ========== Chat Management (proxy to CoPaw) ==========

    /**
     * Create a new chat in CoPaw.
     */
    @PostMapping("/chats")
    public String createChat(@RequestParam(defaultValue = "default") String userId,
                             @RequestParam(required = false) String name,
                             @RequestParam(defaultValue = "default") String agentId) {
        return copawService.createChat(userId, name, agentId);
    }

    /**
     * List chats from CoPaw.
     */
    @GetMapping("/chats")
    public String listChats(@RequestParam(defaultValue = "default") String userId,
                            @RequestParam(required = false) String channel,
                            @RequestParam(defaultValue = "default") String agentId) {
        return copawService.listChats(userId, channel, agentId);
    }

    /**
     * Get chat detail with messages from CoPaw.
     */
    @GetMapping("/chats/{chatId}")
    public String getChat(@PathVariable String chatId,
                          @RequestParam(defaultValue = "default") String agentId) {
        return copawService.getChat(chatId, agentId);
    }

    /**
     * Delete a chat in CoPaw.
     */
    @DeleteMapping("/chats/{chatId}")
    public String deleteChat(@PathVariable String chatId,
                             @RequestParam(defaultValue = "default") String agentId) {
        return copawService.deleteChat(chatId, agentId);
    }

    // ========== Agent Management ==========

    /**
     * List all available CoPaw agents.
     */
    @GetMapping("/agents")
    public String listAgents() {
        return copawService.listAgents();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
