package com.upc.wms.agent.capability;

import com.upc.wms.agent.core.AgentNames;
import com.upc.wms.agent.core.AgentTaskType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 15 位领域智能体 + 总控的能力目录：供 Orchestrator 规划、数据范围过滤与报告引用。
 */
@Component
public class AgentCapabilityCatalog {

    private final Map<String, AgentCapability> catalog = new LinkedHashMap<>();

    public AgentCapabilityCatalog() {
        registerAll();
    }

    public AgentCapability get(String agentName) {
        return catalog.get(agentName);
    }

    public List<AgentCapability> listAll() {
        return Collections.unmodifiableList(new ArrayList<>(catalog.values()));
    }

    public String buildCatalogSummaryForLlm() {
        StringBuilder sb = new StringBuilder();
        for (AgentCapability c : catalog.values()) {
            if (AgentNames.ORCHESTRATOR.equals(c.getAgentName())) {
                continue;
            }
            sb.append("- ").append(c.getAgentName()).append("（").append(c.getLabel()).append("）")
                    .append(" [").append(c.getLayerLabel()).append("]")
                    .append("\n  职责: ").append(c.getResponsibility())
                    .append("\n  能力: ").append(String.join("、", c.getCapabilities()))
                    .append("\n  数据表: ").append(String.join("、", c.getDataTables()))
                    .append("\n  任务类型: ").append(String.join("、", c.getSupportedTaskTypes()))
                    .append("\n");
        }
        return sb.toString();
    }

    /**
     * 按智能体数据范围过滤上下文，供步骤 input 落库与前端展示。
     * 含 "*" 时返回全量副本。
     */
    public Map<String, Object> filterDataScope(String agentName, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return Map.of();
        }
        AgentCapability cap = catalog.get(agentName);
        if (cap == null || cap.getDataScope() == null || cap.getDataScope().isEmpty()
                || cap.getDataScope().contains("*")) {
            return new LinkedHashMap<>(data);
        }
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (String key : cap.getDataScope()) {
            if (data.containsKey(key)) {
                filtered.put(key, data.get(key));
            }
        }
        // 始终保留编排元数据（若存在）
        for (String meta : List.of("promptText", "userGoal", "_orchestratorPlan")) {
            if (data.containsKey(meta) && !filtered.containsKey(meta)) {
                filtered.put(meta, data.get(meta));
            }
        }
        return filtered;
    }

    private void registerAll() {
        put(AgentCapability.builder()
                .agentName(AgentNames.ORCHESTRATOR)
                .label("总控智能体")
                .layer("decision")
                .layerLabel("决策层")
                .responsibility("理解用户任务、识别类型、规划智能体链、调度执行、汇总报告；不直接改业务单据")
                .capabilities(List.of("任务理解", "任务类型识别", "业务模块判断", "执行链规划", "链式调度", "步骤日志", "报告汇总"))
                .dataScope(List.of("*"))
                .dataTables(List.of("agent_task", "agent_task_step", "agent_execution_log"))
                .returnFields(List.of("taskType", "modules", "plannedChain", "reasoning", "userGoal"))
                .supportedTaskTypes(List.of("ALL"))
                .canMutate(false)
                .build());

        put(AgentCapability.builder()
                .agentName(AgentNames.ORDER_PLAN)
                .label("订单计划智能体")
                .layer("strategy")
                .layerLabel("策略层")
                .responsibility("客户订单转生产计划，齐套后生成领料单")
                .capabilities(List.of("生成生产计划", "写入计划明细", "按齐套率生成领料单", "缺料预警记录"))
                .dataScope(List.of("orderId", "planId", "planNo", "planLines", "kittingComplete", "kittingRate",
                        "shortageCount", "shortageAnalysis", "inventorySummary", "requisitionId", "autoApproveOrder", "promptText"))
                .dataTables(List.of("ord_customer_order", "pmc_production_plan", "pmc_production_plan_line", "pmc_requisition_order"))
                .returnFields(List.of("planId", "planNo", "planLines", "requisitionId", "conclusion", "dbEvidence", "processingContent"))
                .supportedTaskTypes(List.of(
                        AgentTaskType.ORDER_PLAN_REQUISITION.name(),
                        AgentTaskType.ORDER_REQUISITION_OUTBOUND.name()))
                .canMutate(true)
                .build());

        put(AgentCapability.builder()
                .agentName(AgentNames.INVENTORY)
                .label("库存智能体")
                .layer("strategy")
                .layerLabel("策略层")
                .responsibility("齐套校验、库存核对、冻结/解冻、安全库存、FIFO 批次库位推荐")
                .capabilities(List.of("齐套校验", "缺料分型", "库存余额核对", "冻结解冻", "安全库存检查", "FIFO 推荐"))
                .dataScope(List.of("planId", "planLines", "requisitionId", "requisitionLines", "outboundId",
                        "kittingComplete", "allocationRecommended", "itemId", "warehouseId", "freezeReason", "promptText"))
                .dataTables(List.of("inv_inventory", "inv_transaction", "inv_alert_record", "inv_freeze_record", "md_item"))
                .returnFields(List.of("kittingRate", "kittingComplete", "shortageAnalysis", "inventorySummary",
                        "allocationRecommended", "recommendedAllocations", "conclusion", "dbEvidence"))
                .supportedTaskTypes(List.of(
                        AgentTaskType.ORDER_PLAN_REQUISITION.name(),
                        AgentTaskType.REQUISITION_OUTBOUND.name(),
                        AgentTaskType.ORDER_REQUISITION_OUTBOUND.name(),
                        AgentTaskType.RECEIPT_INSPECTION_INBOUND.name(),
                        AgentTaskType.STOCKTAKE_ADJUSTMENT.name(),
                        AgentTaskType.INVENTORY_TRANSFER.name(),
                        AgentTaskType.INVENTORY_FREEZE.name(),
                        AgentTaskType.INVENTORY_UNFREEZE.name(),
                        AgentTaskType.SAFETY_STOCK_CHECK.name(),
                        AgentTaskType.WORKER_PICKING_SCAN.name(),
                        AgentTaskType.WORKER_EXCEPTION_FEEDBACK.name()))
                .canMutate(true)
                .build());

        put(AgentCapability.builder()
                .agentName(AgentNames.INTEGRATION)
                .label("集成智能体")
                .layer("strategy")
                .layerLabel("策略层")
                .responsibility("处理 ERP/MES 外部消息，推送安全库存等预警")
                .capabilities(List.of("外部消息接收", "消息状态管理", "预警推送", "订单同步摘要"))
                .dataScope(List.of("messageId", "messageType", "payload", "pushToErp", "alertRecords", "promptText"))
                .dataTables(List.of("int_message_log", "inv_alert_record"))
                .returnFields(List.of("messageStatus", "pushed", "conclusion", "dbEvidence"))
                .supportedTaskTypes(List.of(
                        AgentTaskType.INTEGRATION_MESSAGE_PROCESS.name(),
                        AgentTaskType.SAFETY_STOCK_CHECK.name()))
                .canMutate(true)
                .build());

        put(AgentCapability.builder()
                .agentName(AgentNames.AUDIT)
                .label("审计智能体")
                .layer("strategy")
                .layerLabel("策略层")
                .responsibility("流程收尾：归档任务上下文到系统审计日志")
                .capabilities(List.of("审计落库", "全链路证据归档", "流程终审标记"))
                .dataScope(List.of("*"))
                .dataTables(List.of("sys_audit_log", "agent_task"))
                .returnFields(List.of("auditRecorded", "conclusion", "dbEvidence"))
                .supportedTaskTypes(List.of("ALL"))
                .canMutate(true)
                .build());

        put(AgentCapability.builder()
                .agentName(AgentNames.RECEIVING)
                .label("收货智能体")
                .layer("execution")
                .layerLabel("执行层")
                .responsibility("收货登记，生成收货单与明细")
                .capabilities(List.of("收货登记", "收货明细生成", "触发质检流转"))
                .dataScope(List.of("asnId", "receiptId", "receiptNo", "supplierId", "lines", "promptText"))
                .dataTables(List.of("rcv_receipt", "rcv_receipt_line", "rcv_asn"))
                .returnFields(List.of("receiptId", "receiptNo", "receiptLines", "conclusion", "dbEvidence"))
                .supportedTaskTypes(List.of(AgentTaskType.RECEIPT_INSPECTION_INBOUND.name()))
                .canMutate(true)
                .build());

        put(AgentCapability.builder()
                .agentName(AgentNames.QUALITY)
                .label("质检智能体")
                .layer("execution")
                .layerLabel("执行层")
                .responsibility("质检判定，输出合格/不合格/放行结果")
                .capabilities(List.of("质检任务生成", "合格判定", "不合格登记", "放行决策"))
                .dataScope(List.of("receiptId", "receiptLines", "inspectionId", "qualifiedLines", "rejectedLines", "promptText"))
                .dataTables(List.of("qua_inspection", "qua_inspection_line", "qua_issue"))
                .returnFields(List.of("inspectionId", "qualifiedLines", "rejectedLines", "conclusion", "dbEvidence"))
                .supportedTaskTypes(List.of(AgentTaskType.RECEIPT_INSPECTION_INBOUND.name()))
                .canMutate(true)
                .build());

        put(AgentCapability.builder()
                .agentName(AgentNames.INBOUND)
                .label("入库智能体")
                .layer("execution")
                .layerLabel("执行层")
                .responsibility("合格物料入库上架")
                .capabilities(List.of("入库单生成", "库位上架", "库存增加触发"))
                .dataScope(List.of("receiptId", "qualifiedLines", "inboundId", "inboundNo", "locationId", "promptText"))
                .dataTables(List.of("in_order", "in_order_line", "wh_location"))
                .returnFields(List.of("inboundId", "inboundNo", "putawayLines", "conclusion", "dbEvidence"))
                .supportedTaskTypes(List.of(AgentTaskType.RECEIPT_INSPECTION_INBOUND.name()))
                .canMutate(true)
                .build());

        put(AgentCapability.builder()
                .agentName(AgentNames.OUTBOUND)
                .label("出库智能体")
                .layer("execution")
                .layerLabel("执行层")
                .responsibility("领料出库：接收领料单、生成出库单与明细/拣货任务")
                .capabilities(List.of("接收领料单", "生成出库单", "生成出库明细", "生成拣货任务", "异常协同"))
                .dataScope(List.of("requisitionId", "requisitionNo", "requisitionLines", "outboundId", "outboundNo",
                        "allocationRecommended", "recommendedAllocations", "outboundLinesCreated", "promptText"))
                .dataTables(List.of("pmc_requisition_order", "out_order", "out_order_line", "out_picking_task", "out_picking_line"))
                .returnFields(List.of("outboundId", "outboundNo", "pickLineCount", "conclusion", "dbEvidence", "processingContent"))
                .supportedTaskTypes(List.of(
                        AgentTaskType.REQUISITION_OUTBOUND.name(),
                        AgentTaskType.ORDER_REQUISITION_OUTBOUND.name(),
                        AgentTaskType.WORKER_EXCEPTION_FEEDBACK.name()))
                .canMutate(true)
                .build());

        put(AgentCapability.builder()
                .agentName(AgentNames.STOCKTAKE)
                .label("盘点智能体")
                .layer("execution")
                .layerLabel("执行层")
                .responsibility("盘点单处理与账实差异调整建议")
                .capabilities(List.of("盘点单读取", "差异计算", "调整建议", "人工确认触发"))
                .dataScope(List.of("stocktakeId", "stocktakeNo", "lines", "adjustments", "promptText"))
                .dataTables(List.of("stk_stocktake", "stk_stocktake_line"))
                .returnFields(List.of("stocktakeId", "diffCount", "adjustments", "conclusion", "dbEvidence"))
                .supportedTaskTypes(List.of(AgentTaskType.STOCKTAKE_ADJUSTMENT.name()))
                .canMutate(true)
                .build());

        put(AgentCapability.builder()
                .agentName(AgentNames.TRANSFER)
                .label("移库智能体")
                .layer("execution")
                .layerLabel("执行层")
                .responsibility("库内移库执行")
                .capabilities(List.of("移库单生成", "源/目标库位校验", "库存转移"))
                .dataScope(List.of("transferId", "fromLocationId", "toLocationId", "itemId", "qty", "promptText"))
                .dataTables(List.of("inv_transfer", "inv_inventory", "wh_location"))
                .returnFields(List.of("transferId", "transferNo", "conclusion", "dbEvidence"))
                .supportedTaskTypes(List.of(AgentTaskType.INVENTORY_TRANSFER.name()))
                .canMutate(true)
                .build());

        put(AgentCapability.builder()
                .agentName(AgentNames.SMART_WAREHOUSE)
                .label("智能仓储智能体")
                .layer("execution")
                .layerLabel("执行层")
                .responsibility("IoT 事件处理与工人扫码领料协同")
                .capabilities(List.of("IoT 事件解析", "扫码领料确认", "设备状态联动"))
                .dataScope(List.of("eventType", "deviceId", "pickingTaskId", "scanCode", "qty", "promptText"))
                .dataTables(List.of("out_picking_task", "out_picking_line", "iot_event_log"))
                .returnFields(List.of("eventHandled", "scanResult", "conclusion", "dbEvidence"))
                .supportedTaskTypes(List.of(
                        AgentTaskType.SMART_WAREHOUSE_EVENT_PROCESS.name(),
                        AgentTaskType.WORKER_PICKING_SCAN.name()))
                .canMutate(true)
                .build());

        put(AgentCapability.builder()
                .agentName(AgentNames.WORKER_FEEDBACK)
                .label("异常反馈智能体")
                .layer("execution")
                .layerLabel("执行层")
                .responsibility("生产工人现场异常反馈并驱动出库/库存协同")
                .capabilities(List.of("异常登记", "缺件反馈", "通知仓管协同"))
                .dataScope(List.of("pickingTaskId", "exceptionType", "itemId", "qty", "remark", "promptText"))
                .dataTables(List.of("out_review_exception", "out_picking_task"))
                .returnFields(List.of("exceptionId", "exceptionType", "conclusion", "dbEvidence"))
                .supportedTaskTypes(List.of(AgentTaskType.WORKER_EXCEPTION_FEEDBACK.name()))
                .canMutate(true)
                .build());

        put(AgentCapability.builder()
                .agentName(AgentNames.USER)
                .label("用户权限智能体")
                .layer("auxiliary")
                .layerLabel("辅助层")
                .responsibility("用户与权限基础查询，支撑审计与授权校验")
                .capabilities(List.of("用户查询", "角色权限校验", "操作人解析"))
                .dataScope(List.of("userId", "roleId", "permissionCode", "promptText"))
                .dataTables(List.of("sys_user", "sys_role", "sys_role_permission"))
                .returnFields(List.of("userCount", "userInfo", "conclusion", "dbEvidence"))
                .supportedTaskTypes(List.of("AUXILIARY"))
                .canMutate(false)
                .build());

        put(AgentCapability.builder()
                .agentName(AgentNames.MASTER_DATA)
                .label("基础资料智能体")
                .layer("auxiliary")
                .layerLabel("辅助层")
                .responsibility("物料等主数据存在性与基础属性校验")
                .capabilities(List.of("物料存在性校验", "主数据属性读取"))
                .dataScope(List.of("itemId", "itemCode", "promptText"))
                .dataTables(List.of("md_item", "md_supplier", "md_customer"))
                .returnFields(List.of("itemExists", "itemInfo", "conclusion", "dbEvidence"))
                .supportedTaskTypes(List.of("AUXILIARY"))
                .canMutate(false)
                .build());

        put(AgentCapability.builder()
                .agentName(AgentNames.WAREHOUSE)
                .label("仓库库位智能体")
                .layer("auxiliary")
                .layerLabel("辅助层")
                .responsibility("可用库位查询与库位推荐辅助")
                .capabilities(List.of("可用库位查询", "库区库位属性读取"))
                .dataScope(List.of("warehouseId", "zoneId", "locationId", "itemId", "promptText"))
                .dataTables(List.of("wh_warehouse", "wh_zone", "wh_location"))
                .returnFields(List.of("availableLocations", "conclusion", "dbEvidence"))
                .supportedTaskTypes(List.of("AUXILIARY"))
                .canMutate(false)
                .build());
    }

    private void put(AgentCapability capability) {
        catalog.put(capability.getAgentName(), capability);
    }
}
