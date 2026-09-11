package com.upc.wms.dto;

import lombok.Data;

import java.util.Map;

/**
 * 发起智能体任务请求。
 */
@Data
public class AgentTaskStartRequest {
    /** 任务类型，见 AgentTaskType，如 RECEIPT_INSPECTION_INBOUND */
    private String taskType;
    private String taskName;
    private String businessNo;
    private Long createdBy;
    /** 业务参数，透传给领域智能体 */
    private Map<String, Object> data;
}
