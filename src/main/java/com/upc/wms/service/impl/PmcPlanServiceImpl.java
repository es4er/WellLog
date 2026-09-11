package com.upc.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.upc.wms.common.BusinessException;
import com.upc.wms.common.NoGenerator;
import com.upc.wms.common.PlanNoFormatter;
import com.upc.wms.entity.MdItem;
import com.upc.wms.entity.OrdCustomerOrder;
import com.upc.wms.entity.OrdCustomerOrderLine;
import com.upc.wms.entity.PmcProductionPlan;
import com.upc.wms.entity.PmcProductionPlanLine;
import com.upc.wms.entity.PmcRequisitionLine;
import com.upc.wms.entity.PmcRequisitionOrder;
import com.upc.wms.mapper.MdItemMapper;
import com.upc.wms.mapper.OrdCustomerOrderLineMapper;
import com.upc.wms.mapper.OrdCustomerOrderMapper;
import com.upc.wms.mapper.PmcProductionPlanLineMapper;
import com.upc.wms.mapper.PmcProductionPlanMapper;
import com.upc.wms.mapper.PmcRequisitionLineMapper;
import com.upc.wms.mapper.PmcRequisitionOrderMapper;
import com.upc.wms.service.BomService;
import com.upc.wms.service.PmcPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PmcPlanServiceImpl implements PmcPlanService {

    private final PmcProductionPlanMapper pmcProductionPlanMapper;
    private final PmcProductionPlanLineMapper pmcProductionPlanLineMapper;
    private final PmcRequisitionOrderMapper pmcRequisitionOrderMapper;
    private final PmcRequisitionLineMapper pmcRequisitionLineMapper;
    private final OrdCustomerOrderLineMapper ordCustomerOrderLineMapper;
    private final OrdCustomerOrderMapper ordCustomerOrderMapper;
    private final MdItemMapper mdItemMapper;
    private final BomService bomService;

    @Override
    public List<PmcProductionPlan> listPlans() {
        return pmcProductionPlanMapper.selectList(null);
    }

    @Override
    public Map<String, Object> getPlanDetail(Long planId) {
        PmcProductionPlan plan = pmcProductionPlanMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException("生产计划不存在");
        }
        List<PmcProductionPlanLine> lines = pmcProductionPlanLineMapper.selectList(
                new LambdaQueryWrapper<PmcProductionPlanLine>().eq(PmcProductionPlanLine::getPlanId, planId));
        Map<String, Object> detail = new HashMap<>();
        detail.put("plan", plan);
        detail.put("lines", lines);
        return detail;
    }

    @Override
    @Transactional
    public PmcProductionPlan createPlan(PmcProductionPlan plan, List<PmcProductionPlanLine> lines) {
        if (plan.getPlanNo() == null) {
            plan.setPlanNo(generatePlanNo());
        }
        if (plan.getPlanStatus() == null) {
            plan.setPlanStatus("DRAFT");
        }
        pmcProductionPlanMapper.insert(plan);
        if (lines != null) {
            for (PmcProductionPlanLine line : lines) {
                line.setPlanId(plan.getPlanId());
                if (line.getLineStatus() == null) {
                    line.setLineStatus("OPEN");
                }
                pmcProductionPlanLineMapper.insert(line);
            }
        }
        return plan;
    }

    @Override
    @Transactional
    public PmcRequisitionOrder createRequisitionByOrder(Long orderId, Long requestedBy, String dept) {
        PmcRequisitionOrder req = new PmcRequisitionOrder();
        req.setRequisitionNo(NoGenerator.next("RQ"));
        req.setSourceOrderId(orderId);
        req.setRequisitionDept(dept);
        req.setRequestedBy(requestedBy);
        req.setRequestedAt(LocalDateTime.now());
        req.setRequisitionStatus("PENDING_OUTBOUND");
        pmcRequisitionOrderMapper.insert(req);

        List<OrdCustomerOrderLine> orderLines = ordCustomerOrderLineMapper.selectList(
                new LambdaQueryWrapper<OrdCustomerOrderLine>().eq(OrdCustomerOrderLine::getOrderId, orderId));
        for (OrdCustomerOrderLine ol : orderLines) {
            PmcRequisitionLine rl = new PmcRequisitionLine();
            rl.setRequisitionId(req.getRequisitionId());
            rl.setItemId(ol.getItemId());
            rl.setRequiredQty(ol.getOrderedQty());
            rl.setLineStatus("OPEN");
            pmcRequisitionLineMapper.insert(rl);
        }
        return req;
    }

    @Override
    @Transactional
    public PmcRequisitionOrder createRequisitionByPlan(Long planId, Long requestedBy, String dept) {
        return createRequisitionByPlan(planId, null, requestedBy, dept);
    }

    @Override
    @Transactional
    public PmcRequisitionOrder createRequisitionByPlan(Long planId, Long orderId, Long requestedBy, String dept) {
        PmcRequisitionOrder req = new PmcRequisitionOrder();
        req.setRequisitionNo(NoGenerator.next("RQ"));
        req.setSourcePlanId(planId);
        req.setSourceOrderId(orderId);
        req.setRequisitionDept(dept);
        req.setRequestedBy(requestedBy);
        req.setRequestedAt(LocalDateTime.now());
        req.setRequisitionStatus("PENDING_OUTBOUND");
        pmcRequisitionOrderMapper.insert(req);

        List<PmcProductionPlanLine> planLines = pmcProductionPlanLineMapper.selectList(
                new LambdaQueryWrapper<PmcProductionPlanLine>().eq(PmcProductionPlanLine::getPlanId, planId));
        for (PmcProductionPlanLine pl : planLines) {
            PmcRequisitionLine rl = new PmcRequisitionLine();
            rl.setRequisitionId(req.getRequisitionId());
            rl.setItemId(pl.getItemId());
            rl.setRequiredQty(pl.getRequiredQty());
            rl.setLineStatus("OPEN");
            pmcRequisitionLineMapper.insert(rl);
        }
        return req;
    }

    @Override
    @Transactional
    public PmcProductionPlan createPlanFromOrder(Long orderId, Long createdBy) {
        OrdCustomerOrder order = ordCustomerOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在: " + orderId);
        }
        if (orderHasPlan(orderId)) {
            throw new BusinessException("订单 " + order.getOrderNo() + " 已存在生产计划");
        }

        List<OrdCustomerOrderLine> orderLines = ordCustomerOrderLineMapper.selectList(
                new LambdaQueryWrapper<OrdCustomerOrderLine>().eq(OrdCustomerOrderLine::getOrderId, orderId));
        if (orderLines.isEmpty()) {
            throw new BusinessException("订单无明细行，无法生成生产计划");
        }

        Map<Long, BigDecimal> materialNeed = new LinkedHashMap<>();
        if (isLoggingToolsOrder(order, orderLines)) {
            BigDecimal setQty = BigDecimal.valueOf(resolveLoggingProductQty(order));
            for (OrdCustomerOrderLine ol : orderLines) {
                BigDecimal lineQty = ol.getOrderedQty() == null ? BigDecimal.ONE : ol.getOrderedQty();
                materialNeed.merge(ol.getItemId(), lineQty.multiply(setQty), BigDecimal::add);
            }
        } else {
            for (OrdCustomerOrderLine ol : orderLines) {
                Long productItemId = ol.getItemId();
                if (bomService.listActiveComponents(productItemId).isEmpty()) {
                    throw new BusinessException("成品物料 " + productItemId + " 未配置 BOM，请先在 md_bom 主数据中维护");
                }
                BigDecimal productQty = ol.getOrderedQty() == null ? BigDecimal.ONE : ol.getOrderedQty();
                Map<Long, BigDecimal> expanded = bomService.expandMaterialNeed(productItemId, productQty);
                for (Map.Entry<Long, BigDecimal> e : expanded.entrySet()) {
                    materialNeed.merge(e.getKey(), e.getValue(), BigDecimal::add);
                }
            }
        }

        PmcProductionPlan plan = new PmcProductionPlan();
        plan.setSourceSystemId(order.getSourceSystemId());
        plan.setSourceOrderId(orderId);
        plan.setPlanStatus("WAITING_REQ");
        LocalDate start = order.getOrderDate() != null ? order.getOrderDate().plusDays(1) : LocalDate.now().plusDays(1);
        plan.setPlannedStartDate(start);
        plan.setPlannedEndDate(order.getDeliveryDate() != null ? order.getDeliveryDate() : start.plusDays(7));
        plan.setCreatedBy(createdBy);
        plan.setCreatedAt(LocalDateTime.now());

        List<PmcProductionPlanLine> lines = new ArrayList<>();
        LocalDate due = plan.getPlannedStartDate();
        for (Map.Entry<Long, BigDecimal> e : materialNeed.entrySet()) {
            PmcProductionPlanLine line = new PmcProductionPlanLine();
            line.setItemId(e.getKey());
            line.setRequiredQty(e.getValue());
            line.setDueDate(due);
            line.setLineStatus("OPEN");
            lines.add(line);
        }
        return createPlan(plan, lines);
    }

    private String generatePlanNo() {
        String prefix = "PP" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        List<PmcProductionPlan> todayPlans = pmcProductionPlanMapper.selectList(
                new LambdaQueryWrapper<PmcProductionPlan>().likeRight(PmcProductionPlan::getPlanNo, prefix));
        List<String> existing = todayPlans.stream().map(PmcProductionPlan::getPlanNo).toList();
        return PlanNoFormatter.next(existing);
    }

    @Override
    @Transactional
    public PmcRequisitionOrder createRequisitionAfterKitting(Long planId, Long orderId, Long requestedBy,
                                                             String dept, int kittingRate,
                                                             List<Map<String, Object>> kittingLines) {
        if (kittingRate < 90) {
            return null;
        }

        PmcRequisitionOrder req = new PmcRequisitionOrder();
        req.setRequisitionNo(NoGenerator.next("RQ"));
        req.setSourcePlanId(planId);
        req.setSourceOrderId(orderId);
        req.setRequisitionDept(dept);
        req.setRequestedBy(requestedBy);
        req.setRequestedAt(LocalDateTime.now());
        req.setRequisitionStatus(kittingRate >= 100 ? "PENDING_OUTBOUND" : "PARTIAL_PENDING");
        pmcRequisitionOrderMapper.insert(req);

        List<Map<String, Object>> lines = kittingLines == null || kittingLines.isEmpty()
                ? loadPlanLinesAsMaps(planId)
                : kittingLines;

        for (Map<String, Object> line : lines) {
            boolean kitReady = isKitReadyLine(line, kittingRate);
            if (!kitReady) {
                continue;
            }
            PmcRequisitionLine rl = new PmcRequisitionLine();
            rl.setRequisitionId(req.getRequisitionId());
            rl.setItemId(toLong(line.get("itemId")));
            rl.setRequiredQty(toBigDecimal(line.get("requiredQty")));
            rl.setLineStatus("OPEN");
            pmcRequisitionLineMapper.insert(rl);
        }
        return req;
    }

    @Override
    @Transactional
    public void saveKittingSnapshot(Long planId, int kittingRate,
                                    List<Map<String, Object>> kittingLines,
                                    List<Map<String, Object>> shortageAnalysis) {
        if (planId == null) {
            return;
        }
        PmcProductionPlan plan = pmcProductionPlanMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException("生产计划不存在: " + planId);
        }
        plan.setKittingRate(kittingRate);
        plan.setKittingCheckedAt(LocalDateTime.now());
        plan.setKittingLinesJson(toJsonSafe(kittingLines));
        plan.setShortageAnalysisJson(toJsonSafe(shortageAnalysis));
        if (kittingRate >= 100) {
            plan.setPlanStatus("READY");
        } else if (kittingRate < 90) {
            plan.setPlanStatus("SHORTAGE");
        } else {
            plan.setPlanStatus("WAITING_REQ");
        }
        pmcProductionPlanMapper.updateById(plan);
    }

    private static String toJsonSafe(Object value) {
        try {
            return com.upc.wms.agent.core.AgentDataUtils.toJson(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static boolean isKitReadyLine(Map<String, Object> line, int kittingRate) {
        if (kittingRate >= 100) {
            return true;
        }
        Object ready = line.get("kitReady");
        if (ready instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(ready));
    }

    private List<Map<String, Object>> loadPlanLinesAsMaps(Long planId) {
        List<PmcProductionPlanLine> planLines = pmcProductionPlanLineMapper.selectList(
                new LambdaQueryWrapper<PmcProductionPlanLine>().eq(PmcProductionPlanLine::getPlanId, planId));
        List<Map<String, Object>> maps = new ArrayList<>();
        for (PmcProductionPlanLine pl : planLines) {
            Map<String, Object> m = new HashMap<>();
            m.put("itemId", pl.getItemId());
            m.put("requiredQty", pl.getRequiredQty());
            m.put("kitReady", true);
            maps.add(m);
        }
        return maps;
    }

    private static Long toLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        try {
            return new BigDecimal(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    @Override
    public Long findPlanIdByOrderId(Long orderId) {
        if (orderId == null) {
            return null;
        }
        PmcProductionPlan plan = pmcProductionPlanMapper.selectOne(
                new LambdaQueryWrapper<PmcProductionPlan>()
                        .eq(PmcProductionPlan::getSourceOrderId, orderId)
                        .orderByDesc(PmcProductionPlan::getPlanId)
                        .last("LIMIT 1"));
        if (plan != null) {
            return plan.getPlanId();
        }
        // 兼容历史数据：仅有领料单关联订单、计划未写 source_order_id
        PmcRequisitionOrder req = pmcRequisitionOrderMapper.selectOne(
                new LambdaQueryWrapper<PmcRequisitionOrder>()
                        .eq(PmcRequisitionOrder::getSourceOrderId, orderId)
                        .orderByDesc(PmcRequisitionOrder::getRequisitionId)
                        .last("LIMIT 1"));
        return req != null ? req.getSourcePlanId() : null;
    }

    @Override
    public boolean orderHasPlan(Long orderId) {
        return findPlanIdByOrderId(orderId) != null;
    }

    @Override
    public Map<String, Object> getRequisitionDetail(Long requisitionId) {
        PmcRequisitionOrder req = pmcRequisitionOrderMapper.selectById(requisitionId);
        if (req == null) {
            throw new BusinessException("领料单不存在");
        }
        List<PmcRequisitionLine> lines = pmcRequisitionLineMapper.selectList(
                new LambdaQueryWrapper<PmcRequisitionLine>().eq(PmcRequisitionLine::getRequisitionId, requisitionId));
        Map<String, Object> detail = new HashMap<>();
        detail.put("requisition", req);
        detail.put("lines", lines);
        return detail;
    }

    @Override
    public List<PmcRequisitionOrder> listRequisitions() {
        return pmcRequisitionOrderMapper.selectList(null);
    }

    /** 测井 ERP 订单：明细行即物料清单，不走 BOM 展开 */
    private boolean isLoggingToolsOrder(OrdCustomerOrder order, List<OrdCustomerOrderLine> orderLines) {
        if (order.getOrderNo() != null && order.getOrderNo().contains("LOG")) {
            return true;
        }
        if (orderLines == null || orderLines.isEmpty()) {
            return false;
        }
        List<Long> itemIds = orderLines.stream()
                .map(OrdCustomerOrderLine::getItemId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (itemIds.isEmpty()) {
            return false;
        }
        Map<Long, MdItem> itemMap = mdItemMapper.selectBatchIds(itemIds).stream()
                .collect(Collectors.toMap(MdItem::getItemId, item -> item, (a, b) -> a));
        return itemIds.stream()
                .map(itemMap::get)
                .filter(Objects::nonNull)
                .allMatch(item -> item.getItemCode() != null && item.getItemCode().startsWith("M-LOG-"));
    }

    /** ERP 单号第三段为成品套数：ERP-{day}-{ts}|{产品名}|{套数} */
    private int resolveLoggingProductQty(OrdCustomerOrder order) {
        String erp = order.getErpOrderNo();
        if (erp != null && erp.contains("|")) {
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
}
