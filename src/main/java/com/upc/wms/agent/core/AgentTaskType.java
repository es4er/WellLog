package com.upc.wms.agent.core;

/**
 * 智能体任务类型：总控智能体依据该类型决定业务流程与首个领域智能体。
 */
public enum AgentTaskType {
    /** 收货 -> 质检 -> 入库 -> 库存 */
    RECEIPT_INSPECTION_INBOUND,
    /** PMC：订单/计划 -> 齐套校验 -> 领料单 -> 审计 */
    ORDER_PLAN_REQUISITION,
    /** 仓管：领料单接收 → 库存校验 → 批次库位推荐 → 生成出库单 → 生成出库明细 → 审计 */
    REQUISITION_OUTBOUND,
    /** @deprecated 请使用 ORDER_PLAN_REQUISITION + REQUISITION_OUTBOUND 分阶段执行 */
    ORDER_REQUISITION_OUTBOUND,
    /** 盘点 -> 差异 -> 调整 -> 库存修正 */
    STOCKTAKE_ADJUSTMENT,
    /** 库位移库 */
    INVENTORY_TRANSFER,
    /** 库存冻结 */
    INVENTORY_FREEZE,
    /** 库存解冻 */
    INVENTORY_UNFREEZE,
    /** 安全库存检查与预警 */
    SAFETY_STOCK_CHECK,
    /** 外部系统(ERP/MES)消息处理 */
    INTEGRATION_MESSAGE_PROCESS,
    /** 智能仓储(条码/RFID/AGV/IoT)事件处理 */
    SMART_WAREHOUSE_EVENT_PROCESS,
    /** 生产工人：扫码核对 */
    WORKER_PICKING_SCAN,
    /** 生产工人：异常反馈 */
    WORKER_EXCEPTION_FEEDBACK
}
