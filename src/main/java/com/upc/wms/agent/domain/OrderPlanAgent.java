package com.upc.wms.agent.domain;

import com.upc.wms.agent.core.Agent;
import com.upc.wms.agent.core.AgentContext;
import com.upc.wms.agent.core.AgentDataUtils;
import com.upc.wms.agent.core.AgentNames;
import com.upc.wms.agent.core.AgentResult;
import com.upc.wms.agent.core.AgentTaskType;
import com.upc.wms.common.PlanNoFormatter;
import com.upc.wms.entity.PmcProductionPlan;
import com.upc.wms.entity.PmcProductionPlanLine;
import com.upc.wms.entity.PmcRequisitionLine;
import com.upc.wms.entity.PmcRequisitionOrder;
import com.upc.wms.service.OrderService;
import com.upc.wms.service.PmcPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单计划智能体：将客户订单转化为生产计划，经齐套校验后再生成领料单（PMC 计划段）。
 * 出库执行由 {@link OutboundAgent} 在 REQUISITION_OUTBOUND 任务中单独触发。
 */
@Component
@RequiredArgsConstructor
public class OrderPlanAgent implements Agent {

    private final PmcPlanService pmcPlanService;
    private final OrderService orderService;

    @Override
    public String getName() {
        return AgentNames.ORDER_PLAN;
    }

    @Override
    public boolean support(String taskType) {
        return AgentTaskType.ORDER_PLAN_REQUISITION.name().equals(taskType)
                || AgentTaskType.ORDER_REQUISITION_OUTBOUND.name().equals(taskType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public AgentResult handle(AgentContext context) {
        Map<String, Object> data = context.getData();
        Long orderId = AgentDataUtils.getLong(data, "orderId");
        Long planId = AgentDataUtils.getLong(data, "planId");
        Long requestedBy = AgentDataUtils.getLong(data, "requestedBy", context.getCreatedBy());
        String dept = AgentDataUtils.getString(data, "requisitionDept");
        Long requisitionId = AgentDataUtils.getLong(data, "requisitionId");

        if (requisitionId != null) {
            return finishWithExistingRequisition(context, requisitionId, planId, data);
        }

        if (AgentDataUtils.getBoolean(data, "kittingComplete")) {
            return createRequisitionAfterKitting(context, planId, orderId, requestedBy, dept, data);
        }

        return createPlanAndStartKitting(context, orderId, planId, requestedBy, data);
    }

    private AgentResult finishWithExistingRequisition(AgentContext context, Long requisitionId,
                                                      Long planId, Map<String, Object> data) {
        context.put("requisitionId", requisitionId);
        List<Map<String, Object>> requisitionLines = loadRequisitionLines(requisitionId);
        context.put("requisitionLines", requisitionLines);

        String displayPlanNo = AgentDataUtils.getString(data, "planNo");
        boolean legacyFullChain = AgentTaskType.ORDER_REQUISITION_OUTBOUND.name().equals(context.getTaskType());
        String nextAgent = legacyFullChain ? AgentNames.OUTBOUND : AgentNames.INVENTORY;
        String message = displayPlanNo != null
                ? "沿用已有领料单，生产计划 " + displayPlanNo
                : "沿用已有领料单";

        return AgentResult.success(message)
                .business("REQ-" + requisitionId)
                .next(nextAgent)
                .processing("读取已有领料单并加载明细，准备进入后续流程")
                .evidence("pmc_requisition_order#" + requisitionId,
                        displayPlanNo != null ? "pmc_production_plan:" + displayPlanNo : null)
                .put("requisitionId", requisitionId)
                .put("planId", planId)
                .put("planNo", displayPlanNo)
                .put("requisitionLines", requisitionLines);
    }

    private AgentResult createRequisitionAfterKitting(AgentContext context, Long planId, Long orderId,
                                                      Long requestedBy, String dept, Map<String, Object> data) {
        int kittingRate = resolveKittingRate(data);
        List<Map<String, Object>> kittingLines = AgentDataUtils.getMapList(data, "kittingLines");
        String displayPlanNo = AgentDataUtils.getString(data, "planNo");

        PmcRequisitionOrder requisition = pmcPlanService.createRequisitionAfterKitting(
                planId, orderId, requestedBy, dept, kittingRate, kittingLines);

        if (requisition == null) {
            String message = "齐套率 " + kittingRate + "% 低于 90%，未生成正式领料单，已记录缺料预警与调拨/采购建议";
            return AgentResult.success(message)
                    .stepLabel("订单计划·生成领料单")
                    .business(displayPlanNo != null ? displayPlanNo : ("PLAN-" + planId))
                    .next(AgentNames.AUDIT)
                    .processing("按齐套率判断是否生成领料单；低于 90% 仅记录缺料预警")
                    .evidence("pmc_production_plan#" + planId, "齐套率=" + kittingRate + "%", "pmc_requisition_order:skipped")
                    .put("planId", planId)
                    .put("planNo", displayPlanNo)
                    .put("kittingRate", kittingRate)
                    .put("requisitionSkipped", true);
        }

        Long requisitionId = requisition.getRequisitionId();
        context.put("requisitionId", requisitionId);
        List<Map<String, Object>> requisitionLines = loadRequisitionLines(requisitionId);
        context.put("requisitionLines", requisitionLines);

        String message = kittingRate >= 100
                ? "齐套率 100%，已生成完整领料单 " + requisition.getRequisitionNo()
                : "齐套率 " + kittingRate + "%，已生成部分领料单 " + requisition.getRequisitionNo() + "，缺料预警已记录";

        return AgentResult.success(message)
                .stepLabel("订单计划·生成领料单")
                .business(requisition.getRequisitionNo())
                .next(AgentNames.AUDIT)
                .processing("根据齐套校验结果生成领料单并写入领料明细")
                .evidence("pmc_requisition_order#" + requisitionId,
                        "pmc_production_plan#" + planId,
                        "齐套率=" + kittingRate + "%")
                .put("requisitionId", requisitionId)
                .put("requisitionNo", requisition.getRequisitionNo())
                .put("planId", planId)
                .put("planNo", displayPlanNo)
                .put("kittingRate", kittingRate)
                .put("requisitionLines", requisitionLines);
    }

    private AgentResult createPlanAndStartKitting(AgentContext context, Long orderId, Long planId,
                                                  Long requestedBy, Map<String, Object> data) {
        PmcProductionPlan plan = null;

        if (orderId != null && planId == null) {
            if (AgentDataUtils.getBoolean(data, "autoApproveOrder")) {
                orderService.approveOrder(orderId, requestedBy);
            }
            if (!pmcPlanService.orderHasPlan(orderId)) {
                plan = pmcPlanService.createPlanFromOrder(orderId, requestedBy);
                planId = plan.getPlanId();
                context.put("planId", planId);
                context.put("planNo", PlanNoFormatter.display(plan.getPlanNo(), plan.getPlanId()));
            } else if (planId == null) {
                planId = pmcPlanService.findPlanIdByOrderId(orderId);
                if (planId != null) {
                    context.put("planId", planId);
                }
            }
        }

        if (planId == null) {
            return AgentResult.failed("缺少 orderId / planId / requisitionId，无法生成生产计划");
        }

        if (plan == null) {
            Map<String, Object> planDetail = pmcPlanService.getPlanDetail(planId);
            plan = (PmcProductionPlan) planDetail.get("plan");
            context.put("planNo", PlanNoFormatter.display(plan.getPlanNo(), plan.getPlanId()));
        }

        List<Map<String, Object>> planLines = loadPlanLines(planId);
        context.put("planLines", planLines);

        String displayPlanNo = PlanNoFormatter.display(plan.getPlanNo(), plan.getPlanId());
        String message = "生产计划 " + displayPlanNo + " 已生成，进入齐套校验";

        return AgentResult.success(message)
                .stepLabel("订单计划·生成计划")
                .business(displayPlanNo)
                .next(AgentNames.INVENTORY)
                .processing("根据客户订单生成/确认生产计划，加载 BOM 计划明细后交库存齐套")
                .evidence("pmc_production_plan#" + planId, "planNo=" + displayPlanNo,
                        "planLines=" + planLines.size())
                .put("planId", planId)
                .put("planNo", displayPlanNo)
                .put("planLines", planLines);
    }

    private static int resolveKittingRate(Map<String, Object> data) {
        Object rate = data.get("kittingRate");
        if (rate instanceof Number n) {
            return n.intValue();
        }
        Long longRate = AgentDataUtils.getLong(data, "kittingRate");
        return longRate == null ? 0 : longRate.intValue();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadPlanLines(Long planId) {
        Map<String, Object> detail = pmcPlanService.getPlanDetail(planId);
        List<PmcProductionPlanLine> lines = (List<PmcProductionPlanLine>) detail.get("lines");
        List<Map<String, Object>> lineMaps = new ArrayList<>();
        if (lines == null) {
            return lineMaps;
        }
        for (PmcProductionPlanLine line : lines) {
            Map<String, Object> m = new HashMap<>();
            m.put("itemId", line.getItemId());
            m.put("requiredQty", line.getRequiredQty());
            lineMaps.add(m);
        }
        return lineMaps;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadRequisitionLines(Long requisitionId) {
        Map<String, Object> detail = pmcPlanService.getRequisitionDetail(requisitionId);
        List<PmcRequisitionLine> lines = (List<PmcRequisitionLine>) detail.get("lines");
        List<Map<String, Object>> lineMaps = new ArrayList<>();
        if (lines == null) {
            return lineMaps;
        }
        for (PmcRequisitionLine line : lines) {
            Map<String, Object> m = new HashMap<>();
            m.put("itemId", line.getItemId());
            m.put("requiredQty", line.getRequiredQty());
            lineMaps.add(m);
        }
        return lineMaps;
    }
}
