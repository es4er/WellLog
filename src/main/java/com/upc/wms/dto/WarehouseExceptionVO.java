package com.upc.wms.dto;

import lombok.Data;

@Data
public class WarehouseExceptionVO {
    private String id;
    private Long exceptionId;
    /** GENERATE / WORKER / REVIEW */
    private String source;
    private String relatedDoc;
    private String relatedType;
    private String stage;
    private String type;
    private String description;
    private String material;
    private String suggestion;
    private String result;
    private String status;
    private String createdAt;
    /** 出库生成异常：便于前端重试 */
    private Long requisitionId;
    private Long planId;
    private String planNo;
    private Long agentTaskId;
}
