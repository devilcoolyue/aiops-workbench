package com.staryea.aiops.model;

import lombok.Data;

@Data
public class ChatRequest {

    /** User input message */
    private String message;

    /** Session ID for conversation continuity */
    private String sessionId;

    /** User ID */
    private String userId;

    /** Whether to stream the response */
    private boolean stream = true;

    /** Whether to reconnect to an existing stream */
    private boolean reconnect = false;

    /** CoPaw agent ID (optional, uses default if empty) */
    private String agentId;
}
