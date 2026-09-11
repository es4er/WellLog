package com.upc.wms.agent.domain;

import com.upc.wms.agent.core.Agent;
import com.upc.wms.agent.core.AgentContext;
import com.upc.wms.agent.core.AgentDataUtils;
import com.upc.wms.agent.core.AgentNames;
import com.upc.wms.agent.core.AgentResult;
import com.upc.wms.agent.core.AgentTaskType;
import com.upc.wms.entity.OutOrder;
import com.upc.wms.entity.OutOrderLine;
import com.upc.wms.entity.OutPickingLine;
import com.upc.wms.entity.OutPickingTask;
import com.upc.wms.entity.PmcRequisitionLine;
import com.upc.wms.entity.PmcRequisitionOrder;
import com.upc.wms.mapper.OutOrderMapper;
import com.upc.wms.mapper.OutPickingTaskMapper;
import com.upc.wms.service.OutGenerateExceptionService;
import com.upc.wms.service.OutboundService;
import com.upc.wms.service.PmcPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 出库智能体：仓管员触发 REQUISITION_OUTBOUND 后，按阶段链式执行：
 * <ol>
 *   <li>接收领料单 → InventoryAgent 库存校验 / 批次库位推荐</li>
 *   <li>生成出库单</li>
 *   <li>按推荐结果生成出库明细与拣货任务 → AuditAgent</li>
 * </ol>
 * 拣货任务由仓管员分配给生产工人执行扫码领料；仓管员负责监管、复核与异常闭环，本智能体不自动完成拣货。
 */
@Component
@RequiredArgsConstructor
public class OutboundAgent implements Agent {

    private final OutboundService outboundService;
    private final PmcPlanService pmcPlanService;
    private final OutGenerateExceptionService outGenerateExceptionService;
    private final OutPickingTaskMapper outPickingTaskMapper;
    private final OutOrderMapper outOrderMapper;

    @Override
    public String getName() {
        return AgentNames.OUTBOUND;
    }

    @Override
    public boolean support(String taskType) {
        return AgentTaskType.REQUISITION_OUTBOUND.name().equals(taskType)
                || AgentTaskType.ORDER_REQUISITION_OUTBOUND.name().equals(taskType)
                || AgentTaskType.WORKER_EXCEPTION_FEEDBACK.name().equals(taskType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public AgentResult handle(AgentContext context) {
        if (AgentTaskType.WORKER_EXCEPTION_FEEDBACK.name().equals(context.getTaskType())) {
            return handleWorkerException(context);
        }

        Map<String, Object> data = context.getData();
        Long requisitionId = AgentDataUtils.getLong(data, "requisitionId");
        if (requisitionId == null) {
            return AgentResult.failed("缺少 requisitionId，无法生成出库单");
        }

        // 阶段 3：已有出库单 → 生成出库明细 / 拣货任务
        if (AgentDataUtils.getLong(data, "outboundId") != null
                && !AgentDataUtils.getBoolean(data, "outboundLinesCreated")) {
            return createOutboundLines(context, data);
        }

        // 阶段 2：库存推荐完成 → 生成出库单
        if (AgentDataUtils.getBoolean(data, "allocationRecommended")
                && AgentDataUtils.getLong(data, "outboundId") == null) {
            return createOutboundOrder(context, data, requisitionId);
        }

        // 阶段 1：接收领料单
        return receiveRequisition(context, data, requisitionId);
    }

    @SuppressWarnings("unchecked")
    private AgentResult receiveRequisition(AgentContext context, Map<String, Object> data, Long requisitionId) {
        Map<String, Object> detail = pmcPlanService.getRequisitionDetail(requisitionId);
        if (detail == null || detail.get("requisition") == null) {
            return AgentResult.failed("领料单不存在: " + requisitionId);
        }

        PmcRequisitionOrder requisition = (PmcRequisitionOrder) detail.get("requisition");
        List<PmcRequisitionLine> lines = (List<PmcRequisitionLine>) detail.get("lines");
        List<Map<String, Object>> requisitionLines = toRequisitionLineMaps(lines);

        String requisitionNo = requisition.getRequisitionNo() != null
                ? requisition.getRequisitionNo()
                : "REQ-" + requisitionId;

        context.put("requisitionId", requisitionId);
        context.put("requisitionNo", requisitionNo);
        context.put("requisitionLines", requisitionLines);
        context.put("requisitionReceived", true);
        if (requisition.getSourcePlanId() != null) {
            context.put("planId", requisition.getSourcePlanId());
        }

        return AgentResult.success("OutboundAgent 已接收领料单 " + requisitionNo
                        + "，共 " + requisitionLines.size() + " 项物料，交 InventoryAgent 校验库存")
                .stepLabel("出库智能体·接收领料单")
                .business(requisitionNo)
                .next(AgentNames.INVENTORY)
                .processing("从数据库读取领料单及明细，写入上下文后交库存校验")
                .evidence("pmc_requisition_order#" + requisitionId, "requisitionNo=" + requisitionNo,
                        "明细行=" + requisitionLines.size())
                .put("requisitionId", requisitionId)
                .put("requisitionNo", requisitionNo)
                .put("requisitionLines", requisitionLines)
                .put("itemCount", requisitionLines.size());
    }

    @SuppressWarnings("unchecked")
    private AgentResult createOutboundOrder(AgentContext context, Map<String, Object> data, Long requisitionId) {
        Long warehouseId = AgentDataUtils.getLong(data, "warehouseId");
        OutOrder outOrder = outboundService.createOutboundOrder(requisitionId, warehouseId);

        context.put("outboundId", outOrder.getOutboundId());
        context.put("outboundNo", outOrder.getOutboundNo());
        outGenerateExceptionService.autoResolveByRequisition(requisitionId, outOrder.getOutboundNo());

        Map<String, Object> detail = outboundService.getOutboundDetail(outOrder.getOutboundId());
        List<OutOrderLine> orderLines = (List<OutOrderLine>) detail.get("lines");
        int lineCount = orderLines == null ? 0 : orderLines.size();

        return AgentResult.success("OutboundAgent 已生成出库单 " + outOrder.getOutboundNo()
                        + "，状态待拣货，继续生成出库明细")
                .stepLabel("出库智能体·生成出库单")
                .business(outOrder.getOutboundNo())
                .next(AgentNames.OUTBOUND)
                .processing("根据领料单与库存推荐结果创建出库单")
                .evidence("out_order#" + outOrder.getOutboundId(),
                        "outboundNo=" + outOrder.getOutboundNo(),
                        "pmc_requisition_order#" + requisitionId)
                .put("outboundId", outOrder.getOutboundId())
                .put("outboundNo", outOrder.getOutboundNo())
                .put("orderLineCount", lineCount);
    }

    @SuppressWarnings("unchecked")
    private AgentResult createOutboundLines(AgentContext context, Map<String, Object> data) {
        Long outboundId = AgentDataUtils.getLong(data, "outboundId");
        // 拣货由生产工人执行：未指定 assignedTo 时先创建未分配任务，由仓管员后续分配
        Long assignedTo = AgentDataUtils.getLong(data, "assignedTo");
        String outboundNo = AgentDataUtils.getString(data, "outboundNo");

        Map<String, Object> detail = outboundService.getOutboundDetail(outboundId);
        List<OutOrderLine> orderLines = (List<OutOrderLine>) detail.get("lines");
        if (orderLines == null || orderLines.isEmpty()) {
            return AgentResult.failed("出库单无明细行，无法生成拣货任务");
        }

        List<Map<String, Object>> recommendations = AgentDataUtils.getMapList(data, "recommendedAllocations");
        if (recommendations.isEmpty()) {
            return AgentResult.manualRequired("缺少批次库位推荐结果，无法生成出库明细")
                    .business(outboundNo)
                    .put("outboundId", outboundId);
        }

        // 按出库行匹配推荐分配
        Map<Long, OutOrderLine> lineByItem = new HashMap<>();
        for (OutOrderLine ol : orderLines) {
            lineByItem.put(ol.getItemId(), ol);
        }

        OutPickingTask pickingTask = outboundService.createPickingTask(outboundId, assignedTo);
        List<Map<String, Object>> pickedLines = new ArrayList<>();
        int created = 0;

        for (Map<String, Object> alloc : recommendations) {
            Long itemId = AgentDataUtils.getLong(alloc, "itemId");
            OutOrderLine ol = lineByItem.get(itemId);
            if (ol == null) {
                continue;
            }
            BigDecimal qty = AgentDataUtils.getBigDecimal(alloc.get("qty"));
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            OutPickingLine pl = new OutPickingLine();
            pl.setPickingTaskId(pickingTask.getPickingTaskId());
            pl.setOutboundLineId(ol.getOutboundLineId());
            pl.setInventoryId(AgentDataUtils.getLong(alloc, "inventoryId"));
            pl.setItemId(itemId);
            pl.setBatchId(AgentDataUtils.getLong(alloc, "batchId"));
            pl.setLocationId(AgentDataUtils.getLong(alloc, "locationId"));
            pl.setPlanPickQty(qty);
            pl.setActualPickQty(BigDecimal.ZERO);
            outboundService.addPickingLine(pl);
            created++;

            Map<String, Object> m = new HashMap<>();
            m.put("itemId", itemId);
            m.put("batchId", AgentDataUtils.getLong(alloc, "batchId"));
            m.put("locationId", AgentDataUtils.getLong(alloc, "locationId"));
            m.put("planPickQty", qty);
            pickedLines.add(m);
        }

        if (created == 0) {
            return AgentResult.failed("未能根据推荐结果生成任何出库明细");
        }

        context.put("outboundLinesCreated", true);
        context.put("pickingTaskId", pickingTask.getPickingTaskId());
        context.put("pickedLines", pickedLines);
        context.put("pickLineCount", created);

        String assignHint = assignedTo == null
                ? "拣货任务待仓管员 PDA 扫码拣货"
                : "拣货任务已预指定工人 #" + assignedTo;
        return AgentResult.success("OutboundAgent 已生成出库明细 " + created + " 行，" + assignHint + "，交 AuditAgent 记录")
                .stepLabel("出库智能体·生成出库明细")
                .business(outboundNo)
                .next(AgentNames.AUDIT)
                .processing("按 FIFO 推荐结果生成出库明细与拣货任务")
                .evidence("out_order#" + outboundId, "out_picking_task#" + pickingTask.getPickingTaskId(),
                        "拣货行=" + created)
                .put("outboundId", outboundId)
                .put("outboundNo", outboundNo)
                .put("pickingTaskId", pickingTask.getPickingTaskId())
                .put("assignedTo", assignedTo)
                .put("pickLineCount", created)
                .put("pickedLines", pickedLines);
    }

    private AgentResult handleWorkerException(AgentContext context) {
        Long pickingTaskId = AgentDataUtils.getLong(context.getData(), "pickingTaskId");
        OutPickingTask pickingTask = pickingTaskId != null ? outPickingTaskMapper.selectById(pickingTaskId) : null;
        if (pickingTask != null) {
            pickingTask.setTaskStatus("PAUSED");
            outPickingTaskMapper.updateById(pickingTask);
            OutOrder order = outOrderMapper.selectById(pickingTask.getOutboundId());
            if (order != null) {
                order.setOutboundStatus("SHORTAGE_HOLD");
                outOrderMapper.updateById(order);
                context.put("outboundId", order.getOutboundId());
                context.put("outboundNo", order.getOutboundNo());
                return AgentResult.success("出库侧已收到工人异常，拣货任务暂停等待补拣")
                        .business(order.getOutboundNo())
                        .next(AgentNames.INVENTORY)
                        .put("outboundId", order.getOutboundId())
                        .put("pickingTaskId", pickingTaskId);
            }
        }
        return AgentResult.success("工人异常已同步至出库协同")
                .next(AgentNames.INVENTORY);
    }

    private List<Map<String, Object>> toRequisitionLineMaps(List<PmcRequisitionLine> lines) {
        List<Map<String, Object>> lineMaps = new ArrayList<>();
        if (lines == null) {
            return lineMaps;
        }
        for (PmcRequisitionLine line : lines) {
            Map<String, Object> m = new HashMap<>();
            m.put("itemId", line.getItemId());
            m.put("requiredQty", line.getRequiredQty());
            m.put("requisitionLineId", line.getRequisitionLineId());
            lineMaps.add(m);
        }
        return lineMaps;
    }
}
