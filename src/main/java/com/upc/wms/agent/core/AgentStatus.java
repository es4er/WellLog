package com.upc.wms.agent.core;

/**
 * 智能体 / 步骤 / 任务的执行状态。
 */
public enum AgentStatus {
    /** 等待中 */
    WAITING,
    /** 执行中 */
    RUNNING,
    /** 执行成功 */
    SUCCESS,
    /** 执行失败 */
    FAILED,
    /** 跳过(如质检全部不合格时跳过入库) */
    SKIPPED,
    /** 已取消 */
    CANCELLED,
    /** 需要人工介入(库位不可用、库存不足、复核异常等) */
    MANUAL_REQUIRED
}
