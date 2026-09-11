package com.upc.wms.agent.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体任务步骤视图对象：供前端流程图与步骤详情面板展示。
 */
@Data
public class AgentTaskStepVO {
    private Long stepId;
    private Integer stepNo;
    private String agentName;
    private String agentLabel;
    private String stepName;
    private String status;
    private String inputData;
    private String outputData;
    private String nextAgent;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private String errorMessage;
}
