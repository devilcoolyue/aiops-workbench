package com.staryea.aiops.model;

import lombok.Data;

import java.util.Date;

@Data
public class TodoTask {

    private Long id;

    private String userId;

    private String systemCode;

    private String taskName;

    /** P1 / P2 / P3 */
    private String taskLevel;

    private String taskDetail;

    private String taskLink;

    private String taskSuggestion;

    private String estimatedTime;

    private Date createdAt;

    private Date updatedAt;
}
