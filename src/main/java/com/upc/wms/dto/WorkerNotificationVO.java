package com.upc.wms.dto;

import lombok.Data;

@Data
public class WorkerNotificationVO {
    private Long id;
    private String type;
    private String title;
    private String content;
    private String relatedDoc;
    private Long pickingTaskId;
    private Boolean read;
    private String createdAt;
}
