package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体状态快照表：供前端展示每个 Agent 当前运行状态与统计。
 */
@Data
@TableName("agent_status_snapshot")
public class AgentStatusSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String agentName;
    private String agentLabel;
    /** IDLE / RUNNING / SUCCESS / FAILED / WAITING */
    private String status;
    private Long currentTaskId;
    private Integer totalTaskCount;
    private Integer successCount;
    private Integer failedCount;
    private Long avgDurationMs;
    private LocalDateTime lastActiveTime;
    private LocalDateTime updatedAt;
}
