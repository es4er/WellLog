package com.upc.wms.agent.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体任务视图对象：供前端展示任务详情。
 */
@Data
public class AgentTaskVO {
    private Long taskId;
    private String taskNo;
    private String taskType;
    private String businessNo;
    private String taskName;
    private String status;
    private String currentAgent;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String errorMessage;
}
