package com.staryea.aiops.model;

import lombok.Data;

import java.util.Date;

@Data
public class TodoSummary {

    private Long id;

    private String userId;

    /** LLM-generated summary text for the user's current todo list. */
    private String summaryContent;

    private Date createdAt;

    private Date updatedAt;
}
