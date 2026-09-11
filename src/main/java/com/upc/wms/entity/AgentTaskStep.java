package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体任务步骤表：记录一次任务中每个智能体的执行步骤。
 */
@Data
@TableName("agent_task_step")
public class AgentTaskStep {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Integer stepNo;
    private String agentName;
    private String stepName;
    private String status;
    private String inputData;
    private String outputData;
    private String nextAgent;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
