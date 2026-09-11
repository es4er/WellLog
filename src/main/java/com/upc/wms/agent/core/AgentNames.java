package com.upc.wms.agent.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 智能体名称常量与中文标签，避免调度时字符串硬编码出错。
 */
public final class AgentNames {

    public static final String ORCHESTRATOR = "WmsAgentOrchestrator";
    public static final String USER = "UserAgent";
    public static final String MASTER_DATA = "MasterDataAgent";
    public static final String WAREHOUSE = "WarehouseAgent";
    public static final String ORDER_PLAN = "OrderPlanAgent";
    public static final String RECEIVING = "ReceivingAgent";
    public static final String QUALITY = "QualityAgent";
    public static final String INBOUND = "InboundAgent";
    public static final String OUTBOUND = "OutboundAgent";
    public static final String INVENTORY = "InventoryAgent";
    public static final String STOCKTAKE = "StocktakeAgent";
    public static final String TRANSFER = "TransferAgent";
    public static final String INTEGRATION = "IntegrationAgent";
    public static final String SMART_WAREHOUSE = "SmartWarehouseAgent";
    public static final String WORKER_FEEDBACK = "WorkerFeedbackAgent";
    public static final String AUDIT = "AuditAgent";

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put(ORCHESTRATOR, "总控智能体");
        LABELS.put(USER, "用户权限智能体");
        LABELS.put(MASTER_DATA, "基础资料智能体");
        LABELS.put(WAREHOUSE, "仓库库位智能体");
        LABELS.put(ORDER_PLAN, "订单计划智能体");
        LABELS.put(RECEIVING, "收货智能体");
        LABELS.put(QUALITY, "质检智能体");
        LABELS.put(INBOUND, "入库智能体");
        LABELS.put(OUTBOUND, "出库智能体");
        LABELS.put(INVENTORY, "库存智能体");
        LABELS.put(STOCKTAKE, "盘点智能体");
        LABELS.put(TRANSFER, "移库智能体");
        LABELS.put(INTEGRATION, "集成智能体");
        LABELS.put(SMART_WAREHOUSE, "智能仓储智能体");
        LABELS.put(WORKER_FEEDBACK, "异常反馈智能体");
        LABELS.put(AUDIT, "审计智能体");
    }

    private AgentNames() {
    }

    public static String label(String agentName) {
        return LABELS.getOrDefault(agentName, agentName);
    }

    public static Map<String, String> allLabels() {
        return LABELS;
    }
}
