package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体执行日志表：记录更细粒度的执行日志。
 */
@Data
@TableName("agent_execution_log")
public class AgentExecutionLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long stepId;
    private String agentName;
    /** INFO / WARN / ERROR / DECISION / SERVICE_CALL */
    private String logType;
    private String content;
    private LocalDateTime createdAt;
}
