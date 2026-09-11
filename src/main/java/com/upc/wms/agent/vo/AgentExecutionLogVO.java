package com.upc.wms.agent.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体执行日志视图对象：供前端时间线展示。
 */
@Data
public class AgentExecutionLogVO {
    private Long logId;
    private String agentName;
    private String agentLabel;
    private String logType;
    private String content;
    private LocalDateTime createdAt;
}
