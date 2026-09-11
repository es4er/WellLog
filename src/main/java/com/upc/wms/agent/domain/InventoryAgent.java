package com.upc.wms.agent.domain;

import com.upc.wms.agent.core.Agent;
import com.upc.wms.agent.core.AgentContext;
import com.upc.wms.agent.core.AgentDataUtils;
import com.upc.wms.agent.core.AgentNames;
import com.upc.wms.agent.core.AgentResult;
import com.upc.wms.agent.core.AgentTaskType;
import com.upc.wms.dto.KitShortageAnalysis;
import com.upc.wms.entity.InvAlertRecord;
import com.upc.wms.entity.InvFreezeRecord;
import com.upc.wms.entity.InvInventory;
import com.upc.wms.service.InventoryService;
import com.upc.wms.service.KitAvailabilityService;
import com.upc.wms.service.PmcPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存智能体：WMS 核心智能体。
 * <ul>
 *   <li>作为流程首个智能体时：执行冻结、解冻、安全库存检查等库存动作。</li>
 *   <li>作为收货/出库/盘点/移库流程的一环时：核对库存余额与流水，供审计留痕。</li>
 *   <li>PMC 计划段：对生产计划物料需求做齐套校验，再交由 OrderPlanAgent 生成领料单。</li>
 *   <li>仓管出库段：先校验可用库存，再按 FIFO 推荐批次与库位，交 OutboundAgent 生成出库单。</li>
 * </ul>
 * 所有库存变更均通过 {@code InventoryService} 在事务内完成并生成 inv_transaction 流水。
 */
@Component
@RequiredArgsConstructor
public class InventoryAgent implements Agent {

    private final InventoryService inventoryService;
    private final KitAvailabilityService kitAvailabilityService;
    private final PmcPlanService pmcPlanService;

    @Override
    public String getName() {
        return AgentNames.INVENTORY;
    }

    @Override
    public boolean support(String taskType) {
        return AgentTaskType.INVENTORY_FREEZE.name().equals(taskType)
                || AgentTaskType.INVENTORY_UNFREEZE.name().equals(taskType)
                || AgentTaskType.SAFETY_STOCK_CHECK.name().equals(taskType)
                || AgentTaskType.RECEIPT_INSPECTION_INBOUND.name().equals(taskType)
                || AgentTaskType.ORDER_PLAN_REQUISITION.name().equals(taskType)
                || AgentTaskType.REQUISITION_OUTBOUND.name().equals(taskType)
                || AgentTaskType.ORDER_REQUISITION_OUTBOUND.name().equals(taskType)
                || AgentTaskType.STOCKTAKE_ADJUSTMENT.name().equals(taskType)
                || AgentTaskType.INVENTORY_TRANSFER.name().equals(taskType)
                || AgentTaskType.WORKER_PICKING_SCAN.name().equals(taskType)
                || AgentTaskType.WORKER_EXCEPTION_FEEDBACK.name().equals(taskType);
    }

    @Override
    public AgentResult handle(AgentContext context) {
        String taskType = context.getTaskType();
        Map<String, Object> data = context.getData();
        Long operatedBy = AgentDataUtils.getLong(data, "operatedBy", context.getCreatedBy());

        if (AgentTaskType.INVENTORY_FREEZE.name().equals(taskType)) {
            Long inventoryId = AgentDataUtils.getLong(data, "inventoryId");
            BigDecimal qty = AgentDataUtils.getBigDecimal(data.get("qty"));
            String reason = AgentDataUtils.getString(data, "reason");
            InvFreezeRecord record = inventoryService.freezeInventory(inventoryId, qty, reason, operatedBy);
            return AgentResult.success("库存冻结完成")
                    .business(record.getFreezeNo())
                    .next(AgentNames.AUDIT)
                    .put("freezeId", record.getFreezeId())
                    .put("freezeNo", record.getFreezeNo());
        }

        if (AgentTaskType.INVENTORY_UNFREEZE.name().equals(taskType)) {
            Long inventoryId = AgentDataUtils.getLong(data, "inventoryId");
            BigDecimal qty = AgentDataUtils.getBigDecimal(data.get("qty"));
            String reason = AgentDataUtils.getString(data, "reason");
            InvFreezeRecord record = inventoryService.unfreezeInventory(inventoryId, qty, reason, operatedBy);
            return AgentResult.success("库存解冻完成")
                    .business(record.getFreezeNo())
                    .next(AgentNames.AUDIT)
                    .put("freezeId", record.getFreezeId())
                    .put("freezeNo", record.getFreezeNo());
        }

        if (AgentTaskType.SAFETY_STOCK_CHECK.name().equals(taskType)) {
            List<InvAlertRecord> alerts = inventoryService.checkSafetyStock();
            context.put("alertCount", alerts.size());
            boolean pushToErp = AgentDataUtils.getBoolean(data, "pushToErp");
            String next = pushToErp ? AgentNames.INTEGRATION : AgentNames.AUDIT;
            return AgentResult.success("安全库存检查完成，生成/命中预警 " + alerts.size() + " 条")
                    .next(next)
                    .put("alertCount", alerts.size())
                    .put("alerts", alerts);
        }

        if (AgentTaskType.ORDER_PLAN_REQUISITION.name().equals(taskType)
                && !AgentDataUtils.getBoolean(data, "kittingComplete")) {
            return performKittingCheck(context, data);
        }

        // 仓管出库：库存校验 → 批次库位推荐（须先由 OutboundAgent 加载领料单）
        if (isOutboundFlow(taskType)) {
            if (!AgentDataUtils.getBoolean(data, "requisitionReceived")) {
                return AgentResult.success("领料单尚未加载，按标准出库链转交 OutboundAgent 接收领料单")
                        .stepLabel("库存智能体·等待领料上下文")
                        .next(AgentNames.OUTBOUND)
                        .processing("检测到缺少 requisitionLines，回退至出库首节点");
            }
            if (!AgentDataUtils.getBoolean(data, "inventoryValidated")) {
                return validateOutboundInventory(context, data);
            }
            if (!AgentDataUtils.getBoolean(data, "allocationRecommended")) {
                return recommendBatchAndLocation(context, data);
            }
            return AgentResult.failed("出库库存阶段状态异常：已完成推荐但未生单，请检查 OutboundAgent 上下文")
                    .stepLabel("库存智能体·出库状态异常");
        }

        if (AgentTaskType.WORKER_PICKING_SCAN.name().equals(taskType)
                || AgentTaskType.WORKER_EXCEPTION_FEEDBACK.name().equals(taskType)) {
            return AgentResult.success("工人领料相关库存状态已核对并留痕")
                    .business(context.getBusinessNo())
                    .next(AgentNames.AUDIT);
        }

        // 其余流程：库存核对(收货入库/盘点调整/移库后的余额确认)
        List<Map<String, Object>> summary = new ArrayList<>();
        List<Map<String, Object>> refLines = collectRefLines(data);
        for (Map<String, Object> ref : refLines) {
            Long batchId = AgentDataUtils.getLong(ref, "batchId");
            Long itemId = AgentDataUtils.getLong(ref, "itemId");
            List<InvInventory> invList;
            if (batchId != null) {
                invList = inventoryService.queryInventoryByBatch(batchId);
            } else if (itemId != null) {
                invList = inventoryService.queryInventoryByItem(itemId);
            } else {
                invList = List.of();
            }
            BigDecimal onhand = invList.stream()
                    .map(i -> i.getOnhandQty() == null ? BigDecimal.ZERO : i.getOnhandQty())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> m = new HashMap<>();
            m.put("itemId", itemId);
            m.put("batchId", batchId);
            m.put("onhandQty", onhand);
            summary.add(m);
        }
        context.put("inventorySummary", summary);
        String message = "库存核对与流水确认完成，共核对 " + summary.size() + " 项";
        return AgentResult.success(message)
                .next(AgentNames.AUDIT)
                .put("inventorySummary", summary);
    }

    private boolean isOutboundFlow(String taskType) {
        return AgentTaskType.REQUISITION_OUTBOUND.name().equals(taskType)
                || AgentTaskType.ORDER_REQUISITION_OUTBOUND.name().equals(taskType);
    }

    /**
     * 出库前库存校验：按领料行核对可用量，不足则转人工。
     */
    private AgentResult validateOutboundInventory(AgentContext context, Map<String, Object> data) {
        Long warehouseId = AgentDataUtils.getLong(data, "warehouseId");
        List<Map<String, Object>> refLines = collectRefLines(data);
        if (refLines.isEmpty()) {
            return AgentResult.failed("缺少领料明细，无法校验库存");
        }

        List<Map<String, Object>> validationLines = new ArrayList<>();
        List<Map<String, Object>> shortageLines = new ArrayList<>();
        List<Map<String, Object>> summary = new ArrayList<>();
        int satisfied = 0;

        for (Map<String, Object> ref : refLines) {
            Long itemId = AgentDataUtils.getLong(ref, "itemId");
            BigDecimal requiredQty = AgentDataUtils.getBigDecimal(ref.get("requiredQty"));
            if (requiredQty == null) {
                requiredQty = BigDecimal.ZERO;
            }

            BigDecimal available = sumAvailable(itemId, warehouseId);
            boolean enough = available.compareTo(requiredQty) >= 0;
            if (enough) {
                satisfied++;
            }

            Map<String, Object> line = new HashMap<>();
            line.put("itemId", itemId);
            line.put("requiredQty", requiredQty);
            line.put("availableQty", available);
            line.put("enough", enough);
            if (!enough) {
                BigDecimal shortage = requiredQty.subtract(available).max(BigDecimal.ZERO);
                line.put("shortageQty", shortage);
                shortageLines.add(line);
            }
            validationLines.add(line);

            Map<String, Object> snap = new HashMap<>();
            snap.put("itemId", itemId);
            snap.put("onhandQty", available);
            snap.put("requiredQty", requiredQty);
            snap.put("enough", enough);
            summary.add(snap);
        }

        if (!shortageLines.isEmpty()) {
            context.put("outboundManual", true);
            context.put("inventoryValidated", false);
            context.put("inventoryValidationLines", validationLines);
            context.put("shortageLines", shortageLines);
            context.put("inventorySummary", summary);
            Map<String, Object> first = shortageLines.get(0);
            return AgentResult.manualRequired("物料 " + first.get("itemId")
                            + " 可用库存不足（缺 " + first.get("shortageQty") + "），出库任务需人工处理")
                    .business(context.getBusinessNo())
                    .stepLabel("库存智能体·库存校验")
                    .put("shortItemId", first.get("itemId"))
                    .put("shortageLines", shortageLines)
                    .put("inventoryValidationLines", validationLines)
                    .put("inventorySummary", summary);
        }

        context.put("inventoryValidated", true);
        context.put("inventoryValidationLines", validationLines);
        context.put("inventorySummary", summary);

        String message = "InventoryAgent 库存校验通过，" + satisfied + "/" + refLines.size()
                + " 项物料可用库存满足，继续推荐批次与库位";
        return AgentResult.success(message)
                .stepLabel("库存智能体·库存校验")
                .business(context.getBusinessNo())
                .next(AgentNames.INVENTORY)
                .processing("按领料明细核对可用库存（质检放行+未冻结）")
                .evidence("inv_inventory", "校验行=" + validationLines.size(), "满足=" + satisfied)
                .put("inventoryValidated", true)
                .put("inventoryValidationLines", validationLines)
                .put("inventorySummary", summary);
    }

    /**
     * 按 FIFO / 可用库存推荐批次与库位，供 OutboundAgent 生成出库明细。
     */
    private AgentResult recommendBatchAndLocation(AgentContext context, Map<String, Object> data) {
        Long warehouseId = AgentDataUtils.getLong(data, "warehouseId");
        List<Map<String, Object>> refLines = collectRefLines(data);
        List<Map<String, Object>> allocations = new ArrayList<>();
        List<Map<String, Object>> recommendations = new ArrayList<>();

        for (Map<String, Object> ref : refLines) {
            Long itemId = AgentDataUtils.getLong(ref, "itemId");
            BigDecimal need = AgentDataUtils.getBigDecimal(ref.get("requiredQty"));
            if (need == null) {
                need = BigDecimal.ZERO;
            }

            List<InvInventory> candidates = listAvailableCandidates(itemId, warehouseId);
            List<Map<String, Object>> itemAllocs = new ArrayList<>();
            BigDecimal remaining = need;

            for (InvInventory inv : candidates) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                BigDecimal take = remaining.min(inv.getAvailableQty());
                Map<String, Object> alloc = new HashMap<>();
                alloc.put("itemId", itemId);
                alloc.put("inventoryId", inv.getInventoryId());
                alloc.put("batchId", inv.getBatchId());
                alloc.put("locationId", inv.getLocationId());
                alloc.put("qty", take);
                alloc.put("availableQty", inv.getAvailableQty());
                allocations.add(alloc);
                itemAllocs.add(alloc);
                remaining = remaining.subtract(take);
            }

            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                context.put("outboundManual", true);
                return AgentResult.manualRequired("物料 " + itemId + " 可用库存不足，无法完成批次库位推荐")
                        .business(context.getBusinessNo())
                        .stepLabel("库存智能体·批次库位推荐")
                        .put("shortItemId", itemId)
                        .put("shortageQty", remaining);
            }

            Map<String, Object> rec = new HashMap<>();
            rec.put("itemId", itemId);
            rec.put("requiredQty", need);
            rec.put("allocations", itemAllocs);
            if (!itemAllocs.isEmpty()) {
                Map<String, Object> primary = itemAllocs.get(0);
                rec.put("recommendedBatchId", primary.get("batchId"));
                rec.put("recommendedLocationId", primary.get("locationId"));
            }
            recommendations.add(rec);
        }

        context.put("allocationRecommended", true);
        context.put("recommendedAllocations", allocations);
        context.put("batchLocationRecommendations", recommendations);

        String message = "InventoryAgent 已完成批次与库位推荐，共 " + allocations.size()
                + " 条分配建议，交 OutboundAgent 生成出库单";
        return AgentResult.success(message)
                .stepLabel("库存智能体·批次库位推荐")
                .business(context.getBusinessNo())
                .next(AgentNames.OUTBOUND)
                .processing("按 FIFO 从可用库存推荐批次与库位，供出库明细生成")
                .evidence("inv_inventory", "推荐分配=" + allocations.size())
                .put("allocationRecommended", true)
                .put("recommendedAllocations", allocations)
                .put("batchLocationRecommendations", recommendations)
                .put("allocationCount", allocations.size());
    }

    private BigDecimal sumAvailable(Long itemId, Long warehouseId) {
        return kitAvailabilityService.sumKitAvailableQty(itemId, warehouseId);
    }

    private List<InvInventory> listAvailableCandidates(Long itemId, Long warehouseId) {
        return kitAvailabilityService.listKitAvailableCandidates(itemId, warehouseId);
    }

    private AgentResult performKittingCheck(AgentContext context, Map<String, Object> data) {
        List<Map<String, Object>> refLines = collectRefLines(data);
        List<Map<String, Object>> kittingLines = new ArrayList<>();
        List<Map<String, Object>> summary = new ArrayList<>();
        List<Map<String, Object>> shortageAnalysis = new ArrayList<>();
        int satisfied = 0;

        for (Map<String, Object> ref : refLines) {
            Long itemId = AgentDataUtils.getLong(ref, "itemId");
            BigDecimal requiredQty = AgentDataUtils.getBigDecimal(ref.get("requiredQty"));
            if (requiredQty == null) {
                requiredQty = BigDecimal.ZERO;
            }

            KitShortageAnalysis analysis = kitAvailabilityService.analyze(itemId, requiredQty);
            if (analysis.isKitReady()) {
                satisfied++;
            }

            Map<String, Object> kitLine = new HashMap<>();
            kitLine.put("itemId", itemId);
            kitLine.put("requiredQty", requiredQty);
            kitLine.put("availableQty", analysis.getKitAvailableQty());
            kitLine.put("onhandQty", analysis.getOnhandQty());
            kitLine.put("kitReady", analysis.isKitReady());
            if (!analysis.isKitReady()) {
                kitLine.put("shortageQty", analysis.getShortageQty());
                kitLine.put("shortageType", analysis.getShortageType());
                kitLine.put("shortageTypeLabel", analysis.getShortageTypeLabel());
                kitLine.put("pendingInspectionQty", analysis.getPendingInspectionQty());
                kitLine.put("qualityBlockedQty", analysis.getQualityBlockedQty());
                kitLine.put("locationUnavailableQty", analysis.getLocationUnavailableQty());

                Map<String, Object> shortageRow = new HashMap<>(kitLine);
                shortageAnalysis.add(shortageRow);
            }
            kittingLines.add(kitLine);

            Map<String, Object> invSnapshot = new HashMap<>();
            invSnapshot.put("itemId", itemId);
            invSnapshot.put("onhandQty", analysis.getOnhandQty());
            invSnapshot.put("availableQty", analysis.getKitAvailableQty());
            invSnapshot.put("requiredQty", requiredQty);
            invSnapshot.put("kitReady", analysis.isKitReady());
            invSnapshot.put("shortageType", analysis.getShortageType());
            summary.add(invSnapshot);
        }

        int kittingRate = refLines.isEmpty()
                ? 100
                : BigDecimal.valueOf(satisfied)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(refLines.size()), 0, RoundingMode.HALF_UP)
                .intValue();

        context.put("kittingComplete", true);
        context.put("kittingRate", kittingRate);
        context.put("kittingLines", kittingLines);
        context.put("inventorySummary", summary);
        context.put("shortageAnalysis", shortageAnalysis);

        Long planId = AgentDataUtils.getLong(data, "planId");
        if (planId != null) {
            pmcPlanService.saveKittingSnapshot(planId, kittingRate, kittingLines, shortageAnalysis);
        }

        String message = "齐套校验完成（已入库+质检合格/放行+未冻结+可用>0），齐套率 "
                + kittingRate + "%，共核对 " + refLines.size() + " 项，缺料分型 "
                + shortageAnalysis.size() + " 项"
                + (planId != null ? "，已回写生产计划" : "");

        return AgentResult.success(message)
                .stepLabel("库存·齐套校验")
                .next(AgentNames.ORDER_PLAN)
                .processing("按已入库+质检合格/放行+未冻结+可用>0 规则计算齐套率与缺料分型")
                .evidence("inv_inventory", "pmc_production_plan#" + planId,
                        "齐套率=" + kittingRate + "%", "缺料项=" + shortageAnalysis.size())
                .put("kittingComplete", true)
                .put("kittingRate", kittingRate)
                .put("kittingLines", kittingLines)
                .put("inventorySummary", summary)
                .put("shortageAnalysis", shortageAnalysis)
                .put("planId", planId)
                .put("shortageCount", shortageAnalysis.size());
    }

    /**
     * 从上下文中收集需要核对的物料/批次维度。
     */
    private List<Map<String, Object>> collectRefLines(Map<String, Object> data) {
        List<Map<String, Object>> planLines = AgentDataUtils.getMapList(data, "planLines");
        if (!planLines.isEmpty()) {
            return planLines;
        }
        List<Map<String, Object>> qualified = AgentDataUtils.getMapList(data, "qualifiedLines");
        if (!qualified.isEmpty()) {
            return qualified;
        }
        List<Map<String, Object>> picked = AgentDataUtils.getMapList(data, "pickedLines");
        if (!picked.isEmpty()) {
            return picked;
        }
        List<Map<String, Object>> requisitionLines = AgentDataUtils.getMapList(data, "requisitionLines");
        if (!requisitionLines.isEmpty()) {
            return requisitionLines;
        }
        return AgentDataUtils.getMapList(data, "affectedLines");
    }
}
