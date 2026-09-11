package com.upc.wms.agent.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体状态视图对象：供前端左侧 Agent 状态卡片展示。
 */
@Data
public class AgentStatusVO {
    private String agentName;
    private String agentLabel;
    private String status;
    private Long currentTaskId;
    private Integer totalTaskCount;
    private Integer successCount;
    private Integer failedCount;
    private Long avgDurationMs;
    private LocalDateTime lastActiveTime;
}
