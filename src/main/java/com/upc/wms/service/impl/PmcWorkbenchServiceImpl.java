package com.upc.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.wms.agent.core.AgentDataUtils;
import com.upc.wms.common.PlanNoFormatter;
import com.upc.wms.dto.KitShortageAnalysis;
import com.upc.wms.dto.PmcExceptionVO;
import com.upc.wms.dto.PmcOutboundRowVO;
import com.upc.wms.dto.PmcPlanCardVO;
import com.upc.wms.dto.PmcReadyTrendVO;
import com.upc.wms.dto.PmcShortageRowVO;
import com.upc.wms.dto.PmcTimelineStepVO;
import com.upc.wms.dto.PmcWorkbenchOverview;
import com.upc.wms.entity.InvAlertRecord;
import com.upc.wms.entity.InvSafetyStockRule;
import com.upc.wms.entity.InvTransaction;
import com.upc.wms.entity.MdItem;
import com.upc.wms.entity.OrdCustomerOrder;
import com.upc.wms.entity.OrdCustomerOrderLine;
import com.upc.wms.entity.OutOrder;
import com.upc.wms.entity.OutPickingTask;
import com.upc.wms.entity.OutReviewException;
import com.upc.wms.entity.OutReviewLine;
import com.upc.wms.entity.OutReviewTask;
import com.upc.wms.entity.PmcProductionPlan;
import com.upc.wms.entity.PmcProductionPlanLine;
import com.upc.wms.entity.PmcRequisitionOrder;
import com.upc.wms.entity.SysUser;
import com.upc.wms.mapper.InvAlertRecordMapper;
import com.upc.wms.mapper.InvSafetyStockRuleMapper;
import com.upc.wms.mapper.InvTransactionMapper;
import com.upc.wms.mapper.MdItemMapper;
import com.upc.wms.mapper.OrdCustomerOrderLineMapper;
import com.upc.wms.mapper.OrdCustomerOrderMapper;
import com.upc.wms.mapper.OutOrderMapper;
import com.upc.wms.mapper.OutPickingTaskMapper;
import com.upc.wms.mapper.OutReviewExceptionMapper;
import com.upc.wms.mapper.OutReviewLineMapper;
import com.upc.wms.mapper.OutReviewTaskMapper;
import com.upc.wms.mapper.PmcProductionPlanLineMapper;
import com.upc.wms.mapper.PmcProductionPlanMapper;
import com.upc.wms.mapper.PmcRequisitionOrderMapper;
import com.upc.wms.mapper.SysUserMapper;
import com.upc.wms.service.IntegrationService;
import com.upc.wms.service.KitAvailabilityService;
import com.upc.wms.service.PmcExceptionAiSuggestionService;
import com.upc.wms.service.PmcWorkbenchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PmcWorkbenchServiceImpl implements PmcWorkbenchService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST =
            new TypeReference<>() {};

    private static final Map<Long, Long> PLAN_PRODUCT_ITEM = Map.of(
            1L, 1L, 2L, 2L, 3L, 3L, 4L, 4L
    );

    private final PmcProductionPlanMapper planMapper;
    private final PmcProductionPlanLineMapper planLineMapper;
    private final PmcRequisitionOrderMapper requisitionMapper;
    private final OrdCustomerOrderMapper orderMapper;
    private final OrdCustomerOrderLineMapper orderLineMapper;
    private final MdItemMapper itemMapper;
    private final InvSafetyStockRuleMapper safetyStockRuleMapper;
    private final OutOrderMapper outOrderMapper;
    private final OutPickingTaskMapper pickingTaskMapper;
    private final OutReviewTaskMapper reviewTaskMapper;
    private final OutReviewLineMapper reviewLineMapper;
    private final OutReviewExceptionMapper reviewExceptionMapper;
    private final InvAlertRecordMapper alertRecordMapper;
    private final InvTransactionMapper transactionMapper;
    private final SysUserMapper userMapper;
    private final IntegrationService integrationService;
    private final PmcExceptionAiSuggestionService exceptionAiSuggestionService;
    private final KitAvailabilityService kitAvailabilityService;

    @Override
    public PmcWorkbenchOverview getOverview() {
        List<PmcProductionPlan> plans = planMapper.selectList(
                new LambdaQueryWrapper<PmcProductionPlan>().orderByDesc(PmcProductionPlan::getCreatedAt));
        List<PmcRequisitionOrder> requisitions = requisitionMapper.selectList(null);
        Map<Long, PmcRequisitionOrder> reqByPlanId = requisitions.stream()
                .filter(req -> req.getSourcePlanId() != null)
                .collect(Collectors.toMap(PmcRequisitionOrder::getSourcePlanId, req -> req, (a, b) -> a));
        Map<Long, MdItem> itemMap = itemMapper.selectList(null).stream()
                .collect(Collectors.toMap(MdItem::getItemId, item -> item));
        Map<Long, BigDecimal> availableByItem = kitAvailabilityService.sumKitAvailableByItem();

        PmcWorkbenchOverview overview = new PmcWorkbenchOverview();
        List<PmcPlanCardVO> planCards = buildPlanCards(plans, reqByPlanId, itemMap, availableByItem);
        overview.setPlans(buildUnifiedPlanCenterRows(planCards, requisitions, itemMap));
        overview.setShortages(buildShortageRows(plans, itemMap));
        // 与订单计划中心「待排产 / 待审核」列表一致：仅统计尚未生成生产计划的订单
        overview.setPendingReview(countWaitingOrders(overview.getPlans()));
        overview.setOutboundStats(buildOutboundStats(requisitions));
        List<PmcOutboundRowVO> outboundRows = buildOutboundRows(plans, reqByPlanId, itemMap, availableByItem);
        overview.setOutboundList(outboundRows);
        overview.setKpis(buildKpis(overview.getPlans(), outboundRows));
        overview.setShortageTop(buildShortageTop(itemMap, availableByItem, plans));
        overview.setPlanFunnel(buildPlanFunnel(overview.getPlans(), outboundRows));
        overview.setRiskSummary(buildRiskSummary(overview.getPlans(), overview.getShortageTop()));
        overview.setOutboundExceptions(buildOutboundExceptions(outboundRows, itemMap, availableByItem));
        overview.setReadyTrend(buildReadyTrend(plans, availableByItem));
        overview.setDataSources(List.of(
                "pmc_production_plan",
                "pmc_requisition_order",
                "out_order",
                "out_picking_task",
                "out_review_task",
                "out_review_exception",
                "inv_inventory",
                "inv_alert_record",
                "inv_transaction"
        ));
        overview.setAnalysisReports(buildAnalysisReports(overview.getPlans(), overview.getShortageTop()));
        overview.setTrendAlert(buildTrendAlert(overview.getShortageTop()));
        overview.setShortageAlert(buildShortageAlert(overview.getShortageTop()));
        applyIntegrationInbox(overview);
        return overview;
    }

    private void applyIntegrationInbox(PmcWorkbenchOverview overview) {
        Map<String, Object> sync = integrationService.latestOrderSyncSummary();
        overview.setLastIntegrationSyncAt((String) sync.get("lastIntegrationSyncAt"));
        Object count = sync.get("lastIntegrationSyncCount");
        overview.setLastIntegrationSyncCount(count instanceof Number n ? n.intValue() : 0);
        int pending = overview.getPendingReview() == null ? 0 : overview.getPendingReview();
        overview.setInboxHint(pending > 0
                ? "你有 " + pending + " 条新订单 / 计划待审核。"
                : "暂无待审核订单。");
    }

    /** 订单计划中心待处理条数（hasPlan=false） */
    private int countWaitingOrders(List<PmcPlanCardVO> plans) {
        if (plans == null || plans.isEmpty()) {
            return 0;
        }
        return (int) plans.stream().filter(p -> !Boolean.TRUE.equals(p.getHasPlan())).count();
    }

    private List<PmcPlanCardVO> buildPlanCards(List<PmcProductionPlan> plans,
                                               Map<Long, PmcRequisitionOrder> reqByPlanId,
                                               Map<Long, MdItem> itemMap,
                                               Map<Long, BigDecimal> availableByItem) {
        List<PmcPlanCardVO> cards = new ArrayList<>();
        for (PmcProductionPlan plan : plans) {
            PmcRequisitionOrder req = reqByPlanId.get(plan.getPlanId());
            OrdCustomerOrder order = resolveOrderForPlan(plan, req);
            OrdCustomerOrderLine orderLine = resolvePrimaryOrderLine(order);
            MdItem product = resolvePlanProduct(plan, orderLine, itemMap);
            List<PmcProductionPlanLine> lines = planLineMapper.selectList(
                    new LambdaQueryWrapper<PmcProductionPlanLine>().eq(PmcProductionPlanLine::getPlanId, plan.getPlanId()));
            KitMetrics metrics = plan.getKittingRate() != null
                    ? new KitMetrics(plan.getKittingRate(), countShortageFromSnapshotOrLive(plan, lines, availableByItem))
                    : calcKitMetrics(lines, availableByItem);

            PmcPlanCardVO card = new PmcPlanCardVO();
            card.setPlanId(plan.getPlanId());
            card.setHasPlan(true);
            card.setId(PlanNoFormatter.display(plan.getPlanNo(), plan.getPlanId()));
            if (order != null) {
                card.setOrderId(order.getOrderId());
            } else if (plan.getSourceOrderId() != null) {
                card.setOrderId(plan.getSourceOrderId());
            }
            card.setOrder(order != null ? order.getOrderNo() : (plan.getMesPlanNo() != null ? "MES 同步" : "—"));
            card.setSource(plan.getSourceSystemId() != null && plan.getSourceSystemId() == 2L ? "MES" : "客户订单");
            card.setProduct(product != null ? product.getItemName() : "—");
            card.setQty(resolvePlanQty(orderLine, order, product));
            card.setStart(plan.getPlannedStartDate() != null ? plan.getPlannedStartDate().toString() : "—");
            card.setFinish(plan.getPlannedEndDate() != null ? plan.getPlannedEndDate().toString() : "—");
            card.setReady(metrics.readyPercent());
            card.setShortage(metrics.shortageCount());
            card.setReq(req != null ? req.getRequisitionNo() : "—");
            if (req != null) {
                card.setRequisitionId(req.getRequisitionId());
            }
            card.setPlanCompleted(req != null);
            card.setStatus(resolveDisplayStatus(plan, metrics, req));
            if (plan.getKittingCheckedAt() != null) {
                card.setKittingCheckedAt(plan.getKittingCheckedAt()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            }
            cards.add(card);
        }
        return cards;
    }

    private List<PmcPlanCardVO> buildUnifiedPlanCenterRows(List<PmcPlanCardVO> planCards,
                                                           List<PmcRequisitionOrder> requisitions,
                                                           Map<Long, MdItem> itemMap) {
        java.util.Set<Long> ordersWithPlan = new java.util.HashSet<>();
        for (PmcPlanCardVO card : planCards) {
            if (card.getOrderId() != null) {
                ordersWithPlan.add(card.getOrderId());
            }
        }
        for (PmcRequisitionOrder req : requisitions) {
            if (req.getSourceOrderId() != null) {
                ordersWithPlan.add(req.getSourceOrderId());
            }
        }
        // 直接从计划表补齐（含刚生成、尚未建领料单的计划）
        for (PmcProductionPlan plan : planMapper.selectList(
                new LambdaQueryWrapper<PmcProductionPlan>().isNotNull(PmcProductionPlan::getSourceOrderId))) {
            ordersWithPlan.add(plan.getSourceOrderId());
        }

        List<OrdCustomerOrder> incomingOrders = orderMapper.selectList(
                new LambdaQueryWrapper<OrdCustomerOrder>()
                        .in(OrdCustomerOrder::getOrderStatus, "APPROVED", "PENDING_REVIEW")
                        .orderByDesc(OrdCustomerOrder::getCreatedAt));

        List<PmcPlanCardVO> rows = new ArrayList<>();
        for (OrdCustomerOrder order : incomingOrders) {
            if (ordersWithPlan.contains(order.getOrderId())) {
                continue;
            }
            rows.add(buildOrderWaitingCard(order, itemMap));
        }
        rows.addAll(planCards);
        return rows;
    }

    private PmcPlanCardVO buildOrderWaitingCard(OrdCustomerOrder order, Map<Long, MdItem> itemMap) {
        List<OrdCustomerOrderLine> orderLines = orderLineMapper.selectList(
                new LambdaQueryWrapper<OrdCustomerOrderLine>()
                        .eq(OrdCustomerOrderLine::getOrderId, order.getOrderId()));
        OrdCustomerOrderLine firstLine = orderLines.isEmpty() ? null : orderLines.get(0);
        MdItem product = firstLine != null ? itemMap.get(firstLine.getItemId()) : null;

        PmcPlanCardVO card = new PmcPlanCardVO();
        card.setOrderId(order.getOrderId());
        card.setHasPlan(false);
        card.setId("—");
        card.setOrder(order.getOrderNo());
        card.setSource(order.getSourceSystemId() != null && order.getSourceSystemId() == 2L ? "MES" : "客户订单");
        if (isLoggingToolsOrder(order, orderLines, itemMap)) {
            card.setProduct(resolveLoggingProductName(order));
            card.setQty(formatLoggingOrderQty(order));
        } else {
            card.setProduct(product != null ? product.getItemName() : "—");
            card.setQty(resolveOrderQty(firstLine));
        }
        card.setStart("—");
        card.setFinish(order.getDeliveryDate() != null ? order.getDeliveryDate().toString() : "—");
        card.setReady(0);
        card.setShortage(0);
        card.setReq("—");
        card.setStatus("PENDING_REVIEW".equals(order.getOrderStatus()) ? "待审核" : "待排产");
        return card;
    }

    private boolean isLoggingToolsOrder(OrdCustomerOrder order,
                                        List<OrdCustomerOrderLine> lines,
                                        Map<Long, MdItem> itemMap) {
        if (lines == null || lines.isEmpty()) {
            return false;
        }
        if (order.getOrderNo() != null && order.getOrderNo().contains("LOG")) {
            return true;
        }
        return lines.stream()
                .map(OrdCustomerOrderLine::getItemId)
                .filter(Objects::nonNull)
                .map(itemMap::get)
                .filter(Objects::nonNull)
                .map(MdItem::getItemCode)
                .allMatch(code -> code != null && code.startsWith("M-LOG-"));
    }

    private String resolveLoggingProductName(OrdCustomerOrder order) {
        String erp = order.getErpOrderNo();
        if (erp != null && erp.contains(IntegrationServiceImpl.LOGGING_ERP_PRODUCT_DELIM)) {
            String[] parts = erp.split("\\|", 3);
            if (parts.length >= 2 && parts[1] != null && !parts[1].isBlank()) {
                return parts[1].trim();
            }
        }
        return "测井仪器总成";
    }

    private int resolveLoggingProductQty(OrdCustomerOrder order) {
        String erp = order.getErpOrderNo();
        if (erp != null && erp.contains(IntegrationServiceImpl.LOGGING_ERP_PRODUCT_DELIM)) {
            String[] parts = erp.split("\\|", 3);
            if (parts.length >= 3 && parts[2] != null && !parts[2].isBlank()) {
                try {
                    return Math.max(1, Integer.parseInt(parts[2].trim()));
                } catch (NumberFormatException ignored) {
                    // fall through
                }
            }
        }
        return 1;
    }

    private String formatLoggingOrderQty(OrdCustomerOrder order) {
        return resolveLoggingProductQty(order) + " 套";
    }

    private String resolveOrderQty(OrdCustomerOrderLine line) {
        if (line == null || line.getOrderedQty() == null) {
            return "—";
        }
        return stripDecimal(line.getOrderedQty()) + " 套";
    }

    private List<PmcShortageRowVO> buildShortageRows(List<PmcProductionPlan> plans,
                                                    Map<Long, MdItem> itemMap) {
        List<PmcShortageRowVO> rows = new ArrayList<>();
        for (PmcProductionPlan plan : plans) {
            OrdCustomerOrder order = resolveOrderForPlan(plan, null);
            String orderNo = order != null ? order.getOrderNo() : "—";
            Long orderId = order != null ? order.getOrderId() : plan.getSourceOrderId();
            String planNo = PlanNoFormatter.display(plan.getPlanNo(), plan.getPlanId());

            List<Map<String, Object>> snapshot = parseShortageSnapshot(plan.getShortageAnalysisJson());
            if (snapshot != null) {
                for (Map<String, Object> line : snapshot) {
                    rows.add(toShortageRow(plan, orderId, orderNo, planNo, itemMap, line, "SNAPSHOT"));
                }
                continue;
            }

            List<PmcProductionPlanLine> lines = planLineMapper.selectList(
                    new LambdaQueryWrapper<PmcProductionPlanLine>().eq(PmcProductionPlanLine::getPlanId, plan.getPlanId()));
            for (PmcProductionPlanLine line : lines) {
                KitShortageAnalysis analysis = kitAvailabilityService.analyze(line.getItemId(), line.getRequiredQty());
                if (analysis.isKitReady()) {
                    continue;
                }
                Map<String, Object> live = new HashMap<>();
                live.put("itemId", line.getItemId());
                live.put("requiredQty", line.getRequiredQty());
                live.put("availableQty", analysis.getKitAvailableQty());
                live.put("shortageQty", analysis.getShortageQty());
                live.put("shortageType", analysis.getShortageType());
                live.put("shortageTypeLabel", analysis.getShortageTypeLabel());
                rows.add(toShortageRow(plan, orderId, orderNo, planNo, itemMap, live, "LIVE"));
            }
        }
        return rows;
    }

    private PmcShortageRowVO toShortageRow(PmcProductionPlan plan,
                                           Long orderId,
                                           String orderNo,
                                           String planNo,
                                           Map<Long, MdItem> itemMap,
                                           Map<String, Object> line,
                                           String source) {
        Long itemId = AgentDataUtils.getLong(line, "itemId");
        MdItem item = itemId != null ? itemMap.get(itemId) : null;
        String type = AgentDataUtils.getString(line, "shortageType", KitShortageAnalysis.REAL_SHORTAGE);
        String typeLabel = AgentDataUtils.getString(line, "shortageTypeLabel", KitShortageAnalysis.labelOf(type));
        BigDecimal required = AgentDataUtils.getBigDecimal(line.get("requiredQty"));
        BigDecimal shortage = AgentDataUtils.getBigDecimal(line.get("shortageQty"));
        BigDecimal available = AgentDataUtils.getBigDecimal(line.get("availableQty"));
        if (required == null) {
            required = BigDecimal.ZERO;
        }
        if (shortage == null) {
            shortage = BigDecimal.ZERO;
        }
        if (available == null) {
            available = BigDecimal.ZERO;
        }
        String advice = switch (type) {
            case KitShortageAnalysis.QUALITY_PENDING -> "有货待检，催促质检放行后再领料";
            case KitShortageAnalysis.QUALITY_ABNORMAL -> "存在不合格/冻结批次，需质量处置后可用";
            case KitShortageAnalysis.LOCATION_UNAVAILABLE -> "库位或批次状态不可用，请仓管调整后重试";
            default -> shortage.compareTo(new BigDecimal("10")) > 0
                    ? "真实缺料，建议加急采购/催料"
                    : "真实缺料，可从其他库区调拨";
        };
        PmcShortageRowVO row = new PmcShortageRowVO();
        row.setPlanId(plan.getPlanId());
        row.setOrderId(orderId);
        row.setItemId(itemId);
        row.setOrderNo(orderNo);
        row.setPlanNo(planNo);
        row.setItemName(item != null ? item.getItemName() : (itemId != null ? "物料#" + itemId : "未知物料"));
        row.setShortageType(type);
        row.setShortageTypeLabel(typeLabel);
        row.setRequiredQty(stripDecimal(required));
        row.setShortageQty(stripDecimal(shortage));
        row.setAvailableQty(stripDecimal(available));
        row.setGapText("需 " + stripDecimal(required) + " / 缺 " + stripDecimal(shortage));
        row.setAdvice(advice);
        row.setSource(source);
        return row;
    }

    private List<Map<String, Object>> parseShortageSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            List<Map<String, Object>> list = JSON.readValue(json, MAP_LIST);
            return list == null ? List.of() : list;
        } catch (Exception e) {
            return null;
        }
    }

    private int countShortageFromSnapshotOrLive(PmcProductionPlan plan,
                                                List<PmcProductionPlanLine> lines,
                                                Map<Long, BigDecimal> availableByItem) {
        List<Map<String, Object>> snapshot = parseShortageSnapshot(plan.getShortageAnalysisJson());
        if (snapshot != null) {
            return snapshot.size();
        }
        return calcKitMetrics(lines, availableByItem).shortageCount();
    }

    private List<List<String>> buildOutboundStats(List<PmcRequisitionOrder> requisitions) {
        long pendingReq = requisitions.stream()
                .filter(req -> "PENDING_OUTBOUND".equals(req.getRequisitionStatus()))
                .count();
        List<OutOrder> outOrders = outOrderMapper.selectList(null);
        long created = outOrders.size();
        long picking = outOrders.stream().filter(o -> "PICKING".equals(o.getOutboundStatus())).count();
        long reviewIssue = countOpenReviewExceptions();
        long shortageHold = outOrders.stream().filter(o -> "SHORTAGE_HOLD".equals(o.getOutboundStatus())).count();
        String avgDuration = calcAvgOutboundDuration(outOrders);
        return List.of(
                List.of("今日待出库领料单", String.valueOf(pendingReq)),
                List.of("已生成出库单", String.valueOf(created)),
                List.of("拣货中", String.valueOf(picking)),
                List.of("复核异常", String.valueOf(reviewIssue)),
                List.of("影响生产计划", String.valueOf(shortageHold)),
                List.of("平均出库耗时", avgDuration)
        );
    }

    private long countOpenReviewExceptions() {
        Long count = reviewExceptionMapper.selectCount(
                new LambdaQueryWrapper<OutReviewException>()
                        .ne(OutReviewException::getExceptionStatus, "CLOSED")
                        .ne(OutReviewException::getExceptionStatus, "RESOLVED"));
        if (count != null && count > 0) {
            return count;
        }
        return outOrderMapper.selectList(null).stream()
                .filter(o -> "REVIEWING".equals(o.getOutboundStatus()))
                .count();
    }

    private String calcAvgOutboundDuration(List<OutOrder> outOrders) {
        List<OutOrder> completed = outOrders.stream()
                .filter(o -> "COMPLETED".equals(o.getOutboundStatus()) && o.getApprovedAt() != null)
                .toList();
        if (completed.isEmpty()) {
            return "—";
        }
        long totalMinutes = 0;
        int counted = 0;
        for (OutOrder order : completed) {
            PmcRequisitionOrder req = requisitionMapper.selectById(order.getRequisitionId());
            if (req == null || req.getRequestedAt() == null) {
                continue;
            }
            totalMinutes += Duration.between(req.getRequestedAt(), order.getApprovedAt()).toMinutes();
            counted++;
        }
        if (counted == 0) {
            return "—";
        }
        return (totalMinutes / counted) + " 分钟";
    }

    private List<PmcOutboundRowVO> buildOutboundRows(List<PmcProductionPlan> plans,
                                                     Map<Long, PmcRequisitionOrder> reqByPlanId,
                                                     Map<Long, MdItem> itemMap,
                                                     Map<Long, BigDecimal> availableByItem) {
        Map<Long, String> userNames = loadUserNames();
        List<PmcOutboundRowVO> rows = new ArrayList<>();
        for (PmcProductionPlan plan : plans) {
            PmcRequisitionOrder req = reqByPlanId.get(plan.getPlanId());
            if (req == null) {
                continue;
            }
            OutOrder outOrder = outOrderMapper.selectOne(
                    new LambdaQueryWrapper<OutOrder>().eq(OutOrder::getRequisitionId, req.getRequisitionId()).last("LIMIT 1"));
            MdItem product = resolvePlanProduct(plan, resolvePrimaryOrderLine(resolveOrder(req)), itemMap);
            List<PmcProductionPlanLine> lines = planLineMapper.selectList(
                    new LambdaQueryWrapper<PmcProductionPlanLine>().eq(PmcProductionPlanLine::getPlanId, plan.getPlanId()));
            KitMetrics metrics = calcKitMetrics(lines, availableByItem);

            PmcOutboundRowVO row = new PmcOutboundRowVO();
            row.setPlanId(plan.getPlanId());
            row.setRequisitionId(req.getRequisitionId());
            row.setPlan(PlanNoFormatter.display(plan.getPlanNo(), plan.getPlanId()));
            row.setReq(req.getRequisitionNo());
            row.setProduct(product != null ? product.getItemName() : "—");
            row.setReady(metrics.readyPercent());
            List<PmcTimelineStepVO> timeline = buildTimeline(plan, req, outOrder, metrics, userNames);
            row.setTimeline(timeline);
            row.setStep(resolveCurrentStepIndex(timeline));
            if (outOrder == null) {
                row.setOut("—");
                row.setStatus("待出库");
                row.setImpact(metrics.readyPercent() >= 100 ? "待确认" : "影响开工");
                row.setNote("领料单已生成，等待仓管执行出库");
            } else {
                row.setOut(outOrder.getOutboundNo());
                row.setStatus(mapOutboundStatus(outOrder.getOutboundStatus()));
                row.setImpact(mapImpact(outOrder.getOutboundStatus()));
                row.setNote(buildOutboundNote(outOrder, metrics));
            }
            rows.add(row);
        }
        return rows;
    }

    private Map<Long, String> loadUserNames() {
        return userMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysUser::getUserId, SysUser::getUserName, (a, b) -> a));
    }

    private List<PmcTimelineStepVO> buildTimeline(PmcProductionPlan plan,
                                                  PmcRequisitionOrder req,
                                                  OutOrder outOrder,
                                                  KitMetrics metrics,
                                                  Map<Long, String> userNames) {
        OutPickingTask picking = outOrder == null ? null : pickingTaskMapper.selectOne(
                new LambdaQueryWrapper<OutPickingTask>()
                        .eq(OutPickingTask::getOutboundId, outOrder.getOutboundId())
                        .last("LIMIT 1"));
        OutReviewTask review = outOrder == null ? null : reviewTaskMapper.selectOne(
                new LambdaQueryWrapper<OutReviewTask>()
                        .eq(OutReviewTask::getOutboundId, outOrder.getOutboundId())
                        .last("LIMIT 1"));

        boolean planDone = plan.getCreatedAt() != null || plan.getPlanId() != null;
        boolean kitDone = true;
        boolean reqDone = req != null;
        boolean outCreated = outOrder != null;
        boolean pickAssigned = picking != null;
        boolean pickDone = picking != null && Set.of("COMPLETED", "DONE", "FINISHED").contains(
                Objects.requireNonNullElse(picking.getTaskStatus(), ""));
        boolean reviewing = review != null || (outOrder != null && "REVIEWING".equals(outOrder.getOutboundStatus()));
        boolean reviewDone = (review != null && "PASS".equals(review.getReviewResult()))
                || (outOrder != null && "COMPLETED".equals(outOrder.getOutboundStatus()));
        boolean completed = outOrder != null && "COMPLETED".equals(outOrder.getOutboundStatus());
        boolean shortageHold = outOrder != null && "SHORTAGE_HOLD".equals(outOrder.getOutboundStatus());

        int current = 0;
        if (!planDone) {
            current = 0;
        } else if (!kitDone) {
            current = 1;
        } else if (!reqDone) {
            current = 2;
        } else if (!outCreated) {
            current = 3;
        } else if (!pickAssigned) {
            current = 4;
        } else if (!pickDone && !reviewing && !completed) {
            current = 5;
        } else if (!reviewDone && !completed) {
            current = 6;
        } else if (!completed) {
            current = 7;
        } else {
            current = 8;
        }
        if (shortageHold) {
            current = Math.min(current, 5);
        }

        String planner = userName(userNames, req != null ? req.getRequestedBy() : null, "周计划");
        String picker = userName(userNames, picking != null ? picking.getAssignedTo() : null, "仓管");
        String reviewer = userName(userNames, review != null ? review.getReviewedBy() : null, "质检");

        List<PmcTimelineStepVO> steps = new ArrayList<>();
        steps.add(step("生产计划已审核", "OrderPlanAgent", planner, formatTime(plan.getCreatedAt()), stateOf(0, current)));
        steps.add(step("齐套校验完成", "InventoryAgent", "韩库存",
                formatTime(req != null ? req.getRequestedAt() : plan.getCreatedAt()), stateOf(1, current)));
        steps.add(step("领料单已生成", "OrderPlanAgent", planner,
                formatTime(req != null ? req.getRequestedAt() : null), stateOf(2, current)));
        steps.add(step("出库单已创建", "OutboundAgent", outCreated ? "系统" : "—",
                formatTime(outOrder != null ? outOrder.getApprovedAt() : null), stateOf(3, current)));
        steps.add(step("拣货任务已派发", "OutboundAgent", pickAssigned ? picker : "—",
                formatTime(picking != null ? picking.getCreatedAt() : null), stateOf(4, current)));
        steps.add(step(shortageHold ? "拣货暂停·缺料" : "拣货完成", "InventoryAgent", pickAssigned ? picker : "—",
                pickDone ? formatTime(picking.getPlannedPickTime() != null ? picking.getPlannedPickTime() : picking.getCreatedAt()) : "—",
                stateOf(5, current)));
        steps.add(step(reviewing && !reviewDone ? "复核中" : "复核完成", "AuditAgent", reviewing || reviewDone ? reviewer : "—",
                formatTime(review != null ? review.getReviewedAt() : null), stateOf(6, current)));
        steps.add(step("出库完成", "AuditAgent", completed ? "系统" : "—",
                formatTime(completed && outOrder != null ? outOrder.getApprovedAt() : null), stateOf(7, current)));
        return steps;
    }

    private PmcTimelineStepVO step(String name, String agent, String handler, String time, String state) {
        PmcTimelineStepVO vo = new PmcTimelineStepVO();
        vo.setName(name);
        vo.setAgent(agent);
        vo.setHandler(handler);
        vo.setTime(time);
        vo.setState(state);
        return vo;
    }

    private String stateOf(int index, int current) {
        if (index < current) {
            return "done";
        }
        if (index == current) {
            return "current";
        }
        return "pending";
    }

    private int resolveCurrentStepIndex(List<PmcTimelineStepVO> timeline) {
        for (int i = 0; i < timeline.size(); i++) {
            if ("current".equals(timeline.get(i).getState())) {
                return i;
            }
        }
        long done = timeline.stream().filter(s -> "done".equals(s.getState())).count();
        return (int) Math.min(done, timeline.size() - 1);
    }

    private String userName(Map<Long, String> userNames, Long userId, String fallback) {
        if (userId == null) {
            return fallback;
        }
        return userNames.getOrDefault(userId, fallback);
    }

    private String formatTime(LocalDateTime time) {
        if (time == null) {
            return "—";
        }
        return time.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private List<List<String>> buildKpis(List<PmcPlanCardVO> plans, List<PmcOutboundRowVO> outboundRows) {
        int total = plans.size();
        long executable = plans.stream().filter(p -> "可生产".equals(p.getStatus()) || "待领料".equals(p.getStatus())).count();
        long highRisk = plans.stream().filter(p -> "缺料".equals(p.getStatus())
                || (p.getReady() != null && p.getReady() < 70)).count();
        int avgReady = total == 0 ? 0 : plans.stream()
                .mapToInt(p -> p.getReady() == null ? 0 : p.getReady()).sum() / total;
        long withReq = plans.stream().filter(p -> p.getReq() != null && !"—".equals(p.getReq())).count();
        int reqRate = total == 0 ? 0 : (int) Math.round(withReq * 100.0 / total);
        long completedOut = outboundRows.stream().filter(r -> "已完成".equals(r.getStatus())).count();
        long withOut = outboundRows.stream().filter(r -> r.getOut() != null && !"—".equals(r.getOut())).count();
        int outRate = withOut == 0 ? 0 : (int) Math.round(completedOut * 100.0 / withOut);
        // 第 4 项为较昨日趋势文案，第 5 项为对比说明
        return List.of(
                List.of("今日生产计划", String.valueOf(total), "plan", "↑ +" + Math.max(1, total / 5), "较昨日"),
                List.of("可正常执行", String.valueOf(executable), "ok", "↑ +" + Math.max(0, (int) executable / 4), "较昨日"),
                List.of("高风险计划", String.valueOf(highRisk), "danger",
                        highRisk > 0 ? "↓ -" + Math.max(1, (int) highRisk / 3) : "→ 0", "较昨日"),
                List.of("平均齐套率", avgReady + "%", "info",
                        avgReady >= 80 ? "↑ +2%" : "↓ -3%", "较昨日"),
                List.of("今日领料完成率", reqRate + "%", "cyan",
                        reqRate >= 60 ? "↑ +8%" : "↓ -5%", "较昨日"),
                List.of("出库完成率", outRate + "%", "orange",
                        outRate >= 70 ? "↑ +3%" : "↓ -4%", "较昨日")
        );
    }

    private List<Map<String, Object>> buildShortageTop(Map<Long, MdItem> itemMap,
                                                       Map<Long, BigDecimal> availableByItem,
                                                       List<PmcProductionPlan> plans) {
        Map<Long, Integer> impactPlans = new HashMap<>();
        Map<Long, Integer> shortageTimes = new HashMap<>();
        Map<Long, List<String>> planNosByItem = new HashMap<>();
        Map<Long, List<String>> reqNosByItem = new HashMap<>();
        Map<Long, PmcRequisitionOrder> reqByPlanId = requisitionMapper.selectList(null).stream()
                .filter(req -> req.getSourcePlanId() != null)
                .collect(Collectors.toMap(PmcRequisitionOrder::getSourcePlanId, req -> req, (a, b) -> a));

        for (PmcProductionPlan plan : plans) {
            List<PmcProductionPlanLine> lines = planLineMapper.selectList(
                    new LambdaQueryWrapper<PmcProductionPlanLine>().eq(PmcProductionPlanLine::getPlanId, plan.getPlanId()));
            PmcRequisitionOrder req = reqByPlanId.get(plan.getPlanId());
            String planNo = PlanNoFormatter.display(plan.getPlanNo(), plan.getPlanId());
            for (PmcProductionPlanLine line : lines) {
                KitShortageAnalysis analysis = kitAvailabilityService.analyze(line.getItemId(), line.getRequiredQty());
                if (!analysis.isKitReady()) {
                    impactPlans.merge(line.getItemId(), 1, Integer::sum);
                    shortageTimes.merge(line.getItemId(), 1, Integer::sum);
                    planNosByItem.computeIfAbsent(line.getItemId(), k -> new ArrayList<>()).add(planNo);
                    if (req != null) {
                        reqNosByItem.computeIfAbsent(line.getItemId(), k -> new ArrayList<>()).add(req.getRequisitionNo());
                    }
                }
            }
        }

        List<InvSafetyStockRule> rules = safetyStockRuleMapper.selectList(null);
        Comparator<InvSafetyStockRule> byStock = Comparator.comparing(
                rule -> availableByItem.getOrDefault(rule.getItemId(), BigDecimal.ZERO));
        return rules.stream()
                .sorted(byStock)
                .limit(10)
                .map(rule -> {
                    MdItem item = itemMap.get(rule.getItemId());
                    BigDecimal stock = availableByItem.getOrDefault(rule.getItemId(), BigDecimal.ZERO);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", item != null ? item.getItemName() : "未知物料");
                    row.put("times", shortageTimes.getOrDefault(rule.getItemId(), 0));
                    row.put("plans", impactPlans.getOrDefault(rule.getItemId(), 0));
                    row.put("stock", stock.intValue());
                    row.put("safe", rule.getMinQty().intValue());
                    int times = shortageTimes.getOrDefault(rule.getItemId(), 0);
                    int planHits = impactPlans.getOrDefault(rule.getItemId(), 0);
                    boolean belowSafe = stock.compareTo(rule.getMinQty()) < 0;
                    int impactScore = Math.min(100, times * 12 + planHits * 18 + (belowSafe ? 28 : 0)
                            + (stock.intValue() == 0 ? 20 : 0));
                    String stockTrend = stock.intValue() == 0 ? "down2"
                            : belowSafe ? "down"
                            : stock.compareTo(rule.getMinQty().multiply(BigDecimal.valueOf(1.2))) >= 0 ? "up" : "flat";
                    String eta = stock.intValue() == 0 ? "未知" : belowSafe ? "2天" : "今天";
                    KitShortageAnalysis sample = kitAvailabilityService.analyze(rule.getItemId(), rule.getMinQty());
                    String aiAdvice = resolveShortageAdvice(
                            sample.getShortageType(), belowSafe, stock.intValue());
                    row.put("advice", aiAdvice);
                    if (!sample.isKitReady() && sample.getShortageTypeLabel() != null && !sample.getShortageTypeLabel().isBlank()) {
                        row.put("shortageType", sample.getShortageType());
                        row.put("shortageTypeLabel", sample.getShortageTypeLabel());
                    }
                    row.put("level", impactScore >= 70 ? "danger" : belowSafe ? "warn" : "ok");
                    row.put("impactScore", impactScore);
                    row.put("stockTrend", stockTrend);
                    row.put("eta", eta);
                    row.put("aiAdvice", aiAdvice);
                    Map<String, Object> detail = new LinkedHashMap<>();
                    List<String> planNos = planNosByItem.getOrDefault(rule.getItemId(), List.of()).stream().distinct().limit(5).toList();
                    List<String> reqNos = reqNosByItem.getOrDefault(rule.getItemId(), List.of()).stream().distinct().limit(5).toList();
                    detail.put("plans", planNos.isEmpty() ? "暂无影响计划" : String.join("、", planNos));
                    detail.put("planList", planNos);
                    detail.put("reqs", reqNos.isEmpty() ? "暂无关联领料单" : String.join("、", reqNos));
                    detail.put("reqList", reqNos);
                    detail.put("rule", "安全库存 " + rule.getMinQty().intValue() + "，低于即预警");
                    detail.put("lastIn", resolveLastInbound(rule.getItemId()));
                    detail.put("purchase", "PO 建议 · " + (item != null ? item.getItemCode() : "物料") + " 补货");
                    detail.put("inventory", "可用 " + stock.intValue() + " / 安全 " + rule.getMinQty().intValue());
                    row.put("detail", detail);
                    return row;
                })
                .collect(Collectors.toList());
    }

    private String resolveShortageAdvice(String shortageType, boolean belowSafe, int stock) {
        if (shortageType != null) {
            if (shortageType.contains("QUALITY")) {
                return "催检";
            }
            if (shortageType.contains("LOCATION")) {
                return "调拨";
            }
        }
        if (stock == 0) {
            return "采购";
        }
        if (belowSafe) {
            return "调拨";
        }
        return "关注";
    }

    private String resolveLastInbound(Long itemId) {
        InvTransaction tx = transactionMapper.selectOne(
                new LambdaQueryWrapper<InvTransaction>()
                        .eq(InvTransaction::getItemId, itemId)
                        .and(w -> w.like(InvTransaction::getBusinessType, "IN")
                                .or().eq(InvTransaction::getBusinessType, "RECEIPT")
                                .or().eq(InvTransaction::getBusinessType, "INBOUND")
                                .or().gt(InvTransaction::getChangeQty, BigDecimal.ZERO))
                        .orderByDesc(InvTransaction::getOperatedAt)
                        .last("LIMIT 1"));
        if (tx == null || tx.getOperatedAt() == null) {
            return "暂无入库流水";
        }
        return tx.getTransactionNo() + " · " + tx.getOperatedAt().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
    }

    private List<List<Object>> buildPlanFunnel(List<PmcPlanCardVO> plans, List<PmcOutboundRowVO> outboundRows) {
        long orderCount = orderMapper.selectCount(null);
        long planCount = plans.stream()
                .filter(p -> p.getHasPlan() == null || Boolean.TRUE.equals(p.getHasPlan()))
                .filter(p -> p.getPlanId() != null || (p.getId() != null && !p.getId().isBlank()))
                .count();
        if (planCount == 0) {
            planCount = planMapper.selectCount(null);
        }
        long kitReady = plans.stream()
                .filter(p -> p.getReady() != null && p.getReady() >= 90)
                .count();
        long withReq = plans.stream()
                .filter(p -> p.getReq() != null && !"—".equals(p.getReq()) && !p.getReq().isBlank())
                .count();
        long inProd = plans.stream()
                .filter(p -> "可生产".equals(p.getStatus()) || "待领料".equals(p.getStatus()))
                .count();
        long outCompleted = outboundRows.stream()
                .filter(r -> "已完成".equals(r.getStatus()))
                .count();
        if (outCompleted == 0) {
            outCompleted = outOrderMapper.selectCount(
                    new LambdaQueryWrapper<OutOrder>().eq(OutOrder::getOutboundStatus, "COMPLETED"));
        }
        return List.of(
                List.of("订单", orderCount),
                List.of("计划", planCount),
                List.of("已齐套", kitReady),
                List.of("已领料", withReq),
                List.of("生产", inProd),
                List.of("出库", outCompleted)
        );
    }

    private List<String> buildRiskSummary(List<PmcPlanCardVO> plans, List<Map<String, Object>> shortageTop) {
        long shortagePlans = plans.stream().filter(p -> "缺料".equals(p.getStatus())).count();
        String topMaterial = shortageTop.isEmpty() ? "暂无" : String.valueOf(shortageTop.get(0).get("name"));
        List<String> riskyPlans = plans.stream()
                .filter(p -> p.getShortage() != null && p.getShortage() > 0)
                .map(PmcPlanCardVO::getId)
                .limit(2)
                .collect(Collectors.toList());
        List<String> summary = new ArrayList<>();
        summary.add(shortagePlans + " 个计划存在缺料风险");
        summary.add("主要瓶颈物料为" + topMaterial);
        if (riskyPlans.isEmpty()) {
            summary.add("当前无待优先处理的缺料计划");
        } else {
            summary.add("建议优先处理 " + String.join("、", riskyPlans));
        }
        return summary;
    }

    private List<PmcExceptionVO> buildOutboundExceptions(List<PmcOutboundRowVO> outboundRows,
                                                         Map<Long, MdItem> itemMap,
                                                         Map<Long, BigDecimal> availableByItem) {
        List<PmcExceptionVO> list = new ArrayList<>();

        List<OutReviewException> reviewExceptions = reviewExceptionMapper.selectList(
                new LambdaQueryWrapper<OutReviewException>()
                        .ne(OutReviewException::getExceptionStatus, "CLOSED")
                        .ne(OutReviewException::getExceptionStatus, "RESOLVED")
                        .orderByDesc(OutReviewException::getCreatedAt)
                        .last("LIMIT 10"));
        for (OutReviewException ex : reviewExceptions) {
            OutReviewLine line = reviewLineMapper.selectById(ex.getReviewLineId());
            OutReviewTask task = line == null ? null : reviewTaskMapper.selectById(line.getReviewTaskId());
            OutOrder outOrder = task == null ? null : outOrderMapper.selectById(task.getOutboundId());
            PmcRequisitionOrder req = outOrder == null ? null : requisitionMapper.selectById(outOrder.getRequisitionId());
            PmcOutboundRowVO matched = outboundRows.stream()
                    .filter(r -> outOrder != null && Objects.equals(r.getOut(), outOrder.getOutboundNo()))
                    .findFirst()
                    .orElse(null);

            PmcExceptionVO vo = new PmcExceptionVO();
            vo.setType(mapExceptionType(ex.getExceptionType()));
            vo.setTitle(ex.getExceptionDesc() != null ? ex.getExceptionDesc()
                    : (outOrder != null ? outOrder.getOutboundNo() + " 复核异常" : "复核异常"));
            vo.setImpact(matched != null ? matched.getPlan() + " 出库待确认" : "出库复核待处理");
            vo.setPlanId(matched != null ? matched.getPlanId() : (req != null ? req.getSourcePlanId() : null));
            vo.setRequisitionId(matched != null ? matched.getRequisitionId() : (req != null ? req.getRequisitionId() : null));
            vo.setPlanNo(matched != null ? matched.getPlan() : null);
            vo.setOutboundNo(outOrder != null ? outOrder.getOutboundNo() : null);
            vo.setSuggestions(List.of("复核拣货明细与批次号", "核对库存扣减记录", "必要时安排重新拣货"));
            list.add(vo);
        }

        for (PmcOutboundRowVO row : outboundRows) {
            if (!"缺料暂停".equals(row.getStatus()) && !"影响开工".equals(row.getImpact())) {
                continue;
            }
            if (row.getReady() != null && row.getReady() >= 100 && !"缺料暂停".equals(row.getStatus())) {
                continue;
            }
            boolean already = list.stream().anyMatch(e -> Objects.equals(e.getPlanId(), row.getPlanId()));
            if (already) {
                continue;
            }
            List<PmcProductionPlanLine> lines = planLineMapper.selectList(
                    new LambdaQueryWrapper<PmcProductionPlanLine>().eq(PmcProductionPlanLine::getPlanId, row.getPlanId()));
            String shortageItem = "物料";
            BigDecimal gap = BigDecimal.ZERO;
            for (PmcProductionPlanLine line : lines) {
                BigDecimal available = availableByItem.getOrDefault(line.getItemId(), BigDecimal.ZERO);
                if (available.compareTo(line.getRequiredQty()) < 0) {
                    MdItem item = itemMap.get(line.getItemId());
                    shortageItem = item != null ? item.getItemName() : "物料";
                    gap = line.getRequiredQty().subtract(available).max(BigDecimal.ZERO);
                    break;
                }
            }
            PmcExceptionVO vo = new PmcExceptionVO();
            vo.setType("缺料异常");
            vo.setTitle(shortageItem + "缺料 " + stripDecimal(gap) + " 件");
            vo.setImpact(row.getPlan() + " 无法按时开工");
            vo.setPlanId(row.getPlanId());
            vo.setRequisitionId(row.getRequisitionId());
            vo.setPlanNo(row.getPlan());
            vo.setOutboundNo(row.getOut());
            vo.setSuggestions(List.of("拆分领料单，先发放齐套物料", "通知采购补料", "检查其他库区是否有可调拨库存"));
            list.add(vo);
        }

        List<InvAlertRecord> alerts = alertRecordMapper.selectList(
                new LambdaQueryWrapper<InvAlertRecord>()
                        .ne(InvAlertRecord::getAlertStatus, "CLOSED")
                        .orderByDesc(InvAlertRecord::getCreatedAt)
                        .last("LIMIT 5"));
        for (InvAlertRecord alert : alerts) {
            MdItem item = itemMap.get(alert.getItemId());
            String itemName = item != null ? item.getItemName() : "物料";
            boolean already = list.stream().anyMatch(e -> e.getTitle() != null && e.getTitle().contains(itemName));
            if (already) {
                continue;
            }
            PmcExceptionVO vo = new PmcExceptionVO();
            vo.setType(mapAlertType(alert.getAlertType()));
            vo.setTitle(itemName + "库存预警（" + stripDecimal(alert.getAlertQty()) + "）");
            vo.setImpact("可能影响关联出库单发放");
            vo.setSuggestions(List.of("确认冻结/预警原因", "评估可用替代批次", "同步相关岗位优先处理"));
            list.add(vo);
        }

        List<PmcExceptionVO> limited = list.stream().limit(8).collect(Collectors.toList());
        enrichExceptionSuggestions(limited);
        return limited;
    }

    private void enrichExceptionSuggestions(List<PmcExceptionVO> exceptions) {
        if (exceptions == null || exceptions.isEmpty()) {
            return;
        }
        exceptions.parallelStream().forEach(vo -> {
            List<String> fallback = vo.getSuggestions() == null ? List.of() : vo.getSuggestions();
            vo.setSuggestions(exceptionAiSuggestionService.suggest(vo, fallback));
        });
    }

    private String mapExceptionType(String type) {
        if (type == null) {
            return "复核不通过";
        }
        return switch (type) {
            case "QTY_MISMATCH", "QUANTITY" -> "复核不通过";
            case "BATCH_MISMATCH" -> "批次不一致";
            case "SHORTAGE" -> "缺料异常";
            default -> type;
        };
    }

    private String mapAlertType(String type) {
        if (type == null) {
            return "库存预警";
        }
        if (type.contains("FREEZE") || type.contains("冻结")) {
            return "库存冻结";
        }
        if (type.contains("SAFE") || type.contains("安全")) {
            return "安全库存预警";
        }
        return "库存预警";
    }

    private List<PmcReadyTrendVO> buildReadyTrend(List<PmcProductionPlan> plans,
                                                  Map<Long, BigDecimal> availableByItem) {
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("M/dd");
        LocalDate today = LocalDate.now();
        List<PmcReadyTrendVO> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();
            List<PmcProductionPlan> dayPlans = plans.stream()
                    .filter(p -> p.getCreatedAt() != null && !p.getCreatedAt().isAfter(dayEnd.minusSeconds(1)))
                    .toList();
            if (dayPlans.isEmpty()) {
                dayPlans = plans;
            }
            int readySum = 0;
            int shortCount = 0;
            int delay = 0;
            for (PmcProductionPlan plan : dayPlans) {
                List<PmcProductionPlanLine> lines = planLineMapper.selectList(
                        new LambdaQueryWrapper<PmcProductionPlanLine>().eq(PmcProductionPlanLine::getPlanId, plan.getPlanId()));
                KitMetrics metrics = calcKitMetrics(lines, availableByItem);
                readySum += metrics.readyPercent();
                if (metrics.shortageCount() > 0) {
                    shortCount++;
                }
                if (plan.getPlannedStartDate() != null
                        && plan.getPlannedStartDate().isBefore(day)
                        && !"READY".equals(plan.getPlanStatus())
                        && metrics.shortageCount() > 0) {
                    delay++;
                }
            }
            PmcReadyTrendVO vo = new PmcReadyTrendVO();
            vo.setDay(day.format(dayFmt));
            vo.setReady(dayPlans.isEmpty() ? 0 : readySum / dayPlans.size());
            vo.setShortCount(shortCount);
            vo.setDelay(delay);
            trend.add(vo);
        }
        return trend;
    }

    private List<List<String>> buildAnalysisReports(List<PmcPlanCardVO> plans, List<Map<String, Object>> shortageTop) {
        String top = shortageTop.isEmpty() ? "缺料物料" : String.valueOf(shortageTop.get(0).get("name"));
        long shortage = plans.stream().filter(p -> "缺料".equals(p.getStatus())).count();
        return List.of(
                List.of("生成周报",
                        "基于当前 " + plans.size() + " 条生产计划，生成本周履约与物料齐套周报。"),
                List.of("采购建议",
                        "基于缺料 Top（重点：" + top + "）生成采购与调拨建议清单。"),
                List.of("模拟分析",
                        "模拟采购补料后对齐套率与延期计划的影响（当前缺料计划 " + shortage + " 个）。")
        );
    }

    private String buildTrendAlert(List<Map<String, Object>> shortageTop) {
        if (shortageTop == null || shortageTop.isEmpty()) {
            return "当前无显著缺料趋势，齐套情况整体可控。";
        }
        List<String> names = shortageTop.stream()
                .filter(row -> {
                    Object stock = row.get("stock");
                    Object safe = row.get("safe");
                    if (!(stock instanceof Number) || !(safe instanceof Number)) {
                        return false;
                    }
                    return ((Number) stock).intValue() < ((Number) safe).intValue();
                })
                .map(row -> String.valueOf(row.get("name")))
                .limit(2)
                .toList();
        if (names.isEmpty()) {
            return "安全库存整体达标，请持续关注领料与出库进度。";
        }
        return String.join("、", names) + " 低于安全库存，可能影响后续计划。";
    }

    private String buildShortageAlert(List<Map<String, Object>> shortageTop) {
        if (shortageTop == null || shortageTop.isEmpty()) {
            return "暂无缺料瓶颈物料。";
        }
        return String.valueOf(shortageTop.get(0).get("name")) + " 是当前最主要瓶颈物料。";
    }

    private int countPendingReviewOrders() {
        Long count = orderMapper.selectCount(
                new LambdaQueryWrapper<OrdCustomerOrder>().eq(OrdCustomerOrder::getOrderStatus, "PENDING_REVIEW"));
        return count == null ? 0 : count.intValue();
    }

    private OrdCustomerOrderLine resolvePrimaryOrderLine(OrdCustomerOrder order) {
        if (order == null) {
            return null;
        }
        return orderLineMapper.selectOne(
                new LambdaQueryWrapper<OrdCustomerOrderLine>()
                        .eq(OrdCustomerOrderLine::getOrderId, order.getOrderId())
                        .last("LIMIT 1"));
    }

    private MdItem resolvePlanProduct(PmcProductionPlan plan,
                                      OrdCustomerOrderLine orderLine,
                                      Map<Long, MdItem> itemMap) {
        if (orderLine != null) {
            return itemMap.get(orderLine.getItemId());
        }
        Long demoProductId = PLAN_PRODUCT_ITEM.get(plan.getPlanId());
        return demoProductId != null ? itemMap.get(demoProductId) : null;
    }

    private String resolvePlanQty(OrdCustomerOrderLine orderLine, OrdCustomerOrder order, MdItem product) {
        if (orderLine != null) {
            return resolveOrderQty(orderLine);
        }
        return resolveQty(order, product);
    }

    private OrdCustomerOrder resolveOrderForPlan(PmcProductionPlan plan, PmcRequisitionOrder req) {
        if (plan != null && plan.getSourceOrderId() != null) {
            OrdCustomerOrder byPlan = orderMapper.selectById(plan.getSourceOrderId());
            if (byPlan != null) {
                return byPlan;
            }
        }
        return resolveOrder(req);
    }

    private OrdCustomerOrder resolveOrder(PmcRequisitionOrder req) {
        if (req == null || req.getSourceOrderId() == null) {
            return null;
        }
        return orderMapper.selectById(req.getSourceOrderId());
    }

    private String resolveQty(OrdCustomerOrder order, MdItem product) {
        if (order == null || product == null) {
            return "—";
        }
        OrdCustomerOrderLine line = orderLineMapper.selectOne(
                new LambdaQueryWrapper<OrdCustomerOrderLine>()
                        .eq(OrdCustomerOrderLine::getOrderId, order.getOrderId())
                        .eq(OrdCustomerOrderLine::getItemId, product.getItemId())
                        .last("LIMIT 1"));
        if (line == null) {
            return "—";
        }
        return stripDecimal(line.getOrderedQty()) + " 套";
    }

    private KitMetrics calcKitMetrics(List<PmcProductionPlanLine> lines, Map<Long, BigDecimal> availableByItem) {
        if (lines.isEmpty()) {
            return new KitMetrics(100, 0);
        }
        int shortageCount = 0;
        int satisfied = 0;
        for (PmcProductionPlanLine line : lines) {
            BigDecimal available = availableByItem.getOrDefault(line.getItemId(), BigDecimal.ZERO);
            if (available.compareTo(line.getRequiredQty()) >= 0) {
                satisfied++;
            } else {
                shortageCount++;
            }
        }
        int ready = BigDecimal.valueOf(satisfied)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(lines.size()), 0, RoundingMode.HALF_UP)
                .intValue();
        return new KitMetrics(ready, shortageCount);
    }

    private String resolveDisplayStatus(PmcProductionPlan plan, KitMetrics metrics, PmcRequisitionOrder req) {
        if ("SHORTAGE".equals(plan.getPlanStatus()) || (metrics.shortageCount() > 0 && req == null)) {
            return "缺料";
        }
        if (metrics.shortageCount() > 0) {
            return "待领料";
        }
        if ("READY".equals(plan.getPlanStatus())) {
            return "可生产";
        }
        if (req != null || "WAITING_REQ".equals(plan.getPlanStatus())) {
            return "待领料";
        }
        return mapPlanStatus(plan.getPlanStatus());
    }

    private String mapPlanStatus(String status) {
        if (status == null) {
            return "待领料";
        }
        return switch (status) {
            case "READY" -> "可生产";
            case "SHORTAGE" -> "缺料";
            case "WAITING_REQ" -> "待领料";
            default -> status;
        };
    }

    private String mapOutboundStatus(String status) {
        return switch (Objects.requireNonNullElse(status, "")) {
            case "PICKING" -> "拣货中";
            case "SHORTAGE_HOLD" -> "缺料暂停";
            case "REVIEWING" -> "待复核";
            case "COMPLETED" -> "已完成";
            default -> status;
        };
    }

    private String mapImpact(String status) {
        return switch (Objects.requireNonNullElse(status, "")) {
            case "SHORTAGE_HOLD" -> "影响开工";
            case "REVIEWING" -> "待确认";
            default -> "不影响";
        };
    }

    private String buildOutboundNote(OutOrder outOrder, KitMetrics metrics) {
        return switch (Objects.requireNonNullElse(outOrder.getOutboundStatus(), "")) {
            case "PICKING" -> "拣货进行中，预计 20 分钟内完成";
            case "SHORTAGE_HOLD" -> "缺料 " + metrics.shortageCount() + " 项，出库任务暂停";
            case "REVIEWING" -> "复核数量差异待确认，等待人工处理";
            default -> "出库流程推进中";
        };
    }

    private String stripDecimal(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private record KitMetrics(int readyPercent, int shortageCount) {
    }
}
