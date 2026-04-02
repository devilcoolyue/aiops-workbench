package com.staryea.aiops.model;

import lombok.Data;

import java.util.Date;

@Data
public class FocusEvent {

    private Long id;

    private String userId;

    private String systemCode;

    /** pending_upgrade / completed / not_started / to_be_determined */
    private String eventType;

    private Integer eventCount;

    private String ratio;

    private String eventLink;

    private Date createdAt;

    private Date updatedAt;
}
