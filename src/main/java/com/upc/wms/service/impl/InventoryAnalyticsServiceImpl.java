package com.upc.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.upc.wms.dto.InvAnalyticsVO;
import com.upc.wms.entity.*;
import com.upc.wms.mapper.*;
import com.upc.wms.service.InventoryAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryAnalyticsServiceImpl implements InventoryAnalyticsService {

    private final InvInventoryMapper invInventoryMapper;
    private final InvTransactionMapper invTransactionMapper;
    private final InvStocktakeOrderMapper invStocktakeOrderMapper;
    private final InvStocktakeLineMapper invStocktakeLineMapper;
    private final InvStocktakeDifferenceMapper invStocktakeDifferenceMapper;
    private final InvAdjustmentOrderMapper invAdjustmentOrderMapper;
    private final InvSafetyStockRuleMapper invSafetyStockRuleMapper;
    private final InvAlertRecordMapper invAlertRecordMapper;
    private final MdItemMapper mdItemMapper;
    private final MdBatchMapper mdBatchMapper;
    private final WhLocationMapper whLocationMapper;
    private final WhWarehouseMapper whWarehouseMapper;
    private final WhZoneMapper whZoneMapper;

    @Override
    public InvAnalyticsVO getDashboard(Long warehouseId) {
        InvAnalyticsVO vo = new InvAnalyticsVO();

        vo.setKpi(buildKpi(warehouseId));
        vo.setTurnoverTrend(buildTurnoverTrend(warehouseId));
        vo.setSlowMoving(buildSlowMoving(warehouseId));
        vo.setZoneAccuracy(buildZoneAccuracy(warehouseId));
        vo.setSafetyCompliance(buildSafetyCompliance(warehouseId));
        vo.setAdjustmentTrend(buildAdjustmentTrend(warehouseId));
        vo.setInsights(buildInsights(vo));
        vo.setFilters(buildFilters());

        return vo;
    }

    private LambdaQueryWrapper<InvInventory> inventoryFilter(Long warehouseId) {
        LambdaQueryWrapper<InvInventory> w = new LambdaQueryWrapper<>();
        if (warehouseId != null) {
            w.eq(InvInventory::getWarehouseId, warehouseId);
        }
        return w;
    }

    private LambdaQueryWrapper<InvTransaction> txnFilter(
            Long warehouseId, String businessType,
            LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<InvTransaction> w = new LambdaQueryWrapper<>();
        if (businessType != null) {
            w.eq(InvTransaction::getBusinessType, businessType);
        }
        if (start != null) {
            w.ge(InvTransaction::getOperatedAt, start);
        }
        if (end != null) {
            w.lt(InvTransaction::getOperatedAt, end);
        }
        return w;
    }

    private InvAnalyticsVO.SummaryKpi buildKpi(Long warehouseId) {
        InvAnalyticsVO.SummaryKpi kpi = new InvAnalyticsVO.SummaryKpi();

        List<InvInventory> allInv = invInventoryMapper.selectList(inventoryFilter(warehouseId));
        List<MdItem> allItems = mdItemMapper.selectList(null);

        kpi.setItemCount(allInv.stream().map(InvInventory::getItemId).distinct().count());

        BigDecimal totalOnhand = allInv.stream().map(i -> i.getOnhandQty() != null ? i.getOnhandQty() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalFrozen = allInv.stream().map(i -> i.getFrozenQty() != null ? i.getFrozenQty() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAvailable = allInv.stream().map(i -> i.getAvailableQty() != null ? i.getAvailableQty() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalValue = invInventoryMapper.selectInventoryTotalValue(warehouseId);
        kpi.setTotalValue(totalValue == null ? BigDecimal.ZERO : totalValue);
        kpi.setTotalValueAvailable(totalValue != null && totalValue.compareTo(BigDecimal.ZERO) > 0);

        if (totalOnhand.compareTo(BigDecimal.ZERO) > 0) {
            kpi.setFrozenRatio(totalFrozen.multiply(BigDecimal.valueOf(100))
                    .divide(totalOnhand, 1, RoundingMode.HALF_UP));
        } else {
            kpi.setFrozenRatio(BigDecimal.ZERO);
        }

        LocalDate weekAgo = LocalDate.now().minusDays(7);
        LocalDateTime weekAgoStart = weekAgo.atStartOfDay();
        List<InvTransaction> outbound7d = invTransactionMapper.selectList(
                new LambdaQueryWrapper<InvTransaction>()
                        .eq(InvTransaction::getBusinessType, "OUTBOUND")
                        .ge(InvTransaction::getOperatedAt, weekAgoStart));
        BigDecimal outboundSum = outbound7d.stream()
                .map(t -> t.getChangeQty() != null ? t.getChangeQty().abs() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalAvailable.compareTo(BigDecimal.ZERO) > 0) {
            kpi.setTurnoverRate7d(outboundSum.divide(totalAvailable, 2, RoundingMode.HALF_UP));
        } else {
            kpi.setTurnoverRate7d(BigDecimal.ZERO);
        }

        long safetyRuleCount = invSafetyStockRuleMapper.selectCount(
                new LambdaQueryWrapper<InvSafetyStockRule>().eq(InvSafetyStockRule::getEnabledFlag, 1));
        if (safetyRuleCount > 0) {
            long compliant = invSafetyStockRuleMapper.selectList(
                    new LambdaQueryWrapper<InvSafetyStockRule>().eq(InvSafetyStockRule::getEnabledFlag, 1))
                    .stream().filter(rule -> {
                        List<InvInventory> invs = invInventoryMapper.selectList(
                                new LambdaQueryWrapper<InvInventory>()
                                        .eq(InvInventory::getWarehouseId, rule.getWarehouseId())
                                        .eq(InvInventory::getItemId, rule.getItemId()));
                        BigDecimal total = invs.stream().map(InvInventory::getAvailableQty)
                                .filter(Objects::nonNull)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        return total.compareTo(rule.getMinQty() != null ? rule.getMinQty() : BigDecimal.ZERO) >= 0;
                    }).count();
            kpi.setSafetyComplianceRate(BigDecimal.valueOf(compliant)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(safetyRuleCount), 1, RoundingMode.HALF_UP));
        } else {
            kpi.setSafetyComplianceRate(BigDecimal.valueOf(100));
        }

        kpi.setStocktakeDifferenceCount(invStocktakeDifferenceMapper.selectCount(
                new LambdaQueryWrapper<InvStocktakeDifference>()
                        .eq(InvStocktakeDifference::getConfirmStatus, "PENDING")));

        kpi.setPendingAdjustmentCount(invAdjustmentOrderMapper.selectCount(
                new LambdaQueryWrapper<InvAdjustmentOrder>()
                        .eq(InvAdjustmentOrder::getAdjustmentStatus, "PENDING_APPROVAL")));

        return kpi;
    }

    private List<InvAnalyticsVO.TurnoverPoint> buildTurnoverTrend(Long warehouseId) {
        List<InvAnalyticsVO.TurnoverPoint> points = new ArrayList<>();
        List<InvInventory> allInv = invInventoryMapper.selectList(inventoryFilter(warehouseId));
        BigDecimal avgAvailable = allInv.stream()
                .map(i -> i.getAvailableQty() != null ? i.getAvailableQty() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("M/dd");
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            LocalDateTime dayStart = day.atStartOfDay();
            LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();

            LambdaQueryWrapper<InvTransaction> txnQ = new LambdaQueryWrapper<InvTransaction>()
                    .eq(InvTransaction::getBusinessType, "OUTBOUND")
                    .ge(InvTransaction::getOperatedAt, dayStart)
                    .lt(InvTransaction::getOperatedAt, dayEnd);
            if (warehouseId != null) {
                txnQ.eq(InvTransaction::getWarehouseId, warehouseId);
            }
            List<InvTransaction> dayOutbound = invTransactionMapper.selectList(txnQ);
            BigDecimal daySum = dayOutbound.stream()
                    .map(t -> t.getChangeQty() != null ? t.getChangeQty().abs() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            InvAnalyticsVO.TurnoverPoint pt = new InvAnalyticsVO.TurnoverPoint();
            pt.setDay(day.format(fmt));
            pt.setRate(avgAvailable.compareTo(BigDecimal.ZERO) > 0
                    ? daySum.divide(avgAvailable, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            pt.setMove(daySum.intValue());
            points.add(pt);
        }
        return points;
    }

    private List<InvAnalyticsVO.SlowMovingItem> buildSlowMoving(Long warehouseId) {
        List<InvAnalyticsVO.SlowMovingItem> result = new ArrayList<>();
        List<InvInventory> allInv = invInventoryMapper.selectList(inventoryFilter(warehouseId));

        Map<Long, MdItem> itemMap = mdItemMapper.selectList(null).stream()
                .collect(Collectors.toMap(MdItem::getItemId, i -> i));
        Map<Long, MdBatch> batchMap = mdBatchMapper.selectList(null).stream()
                .collect(Collectors.toMap(MdBatch::getBatchId, b -> b));
        Map<Long, WhLocation> locMap = whLocationMapper.selectList(null).stream()
                .collect(Collectors.toMap(WhLocation::getLocationId, l -> l));
        Map<Long, WhWarehouse> whMap = whWarehouseMapper.selectList(null).stream()
                .collect(Collectors.toMap(WhWarehouse::getWarehouseId, w -> w));

        for (InvInventory inv : allInv) {
            boolean isFrozen = inv.getFrozenQty() != null && inv.getFrozenQty().compareTo(BigDecimal.ZERO) > 0;
            boolean zeroStock = inv.getOnhandQty() == null || inv.getOnhandQty().compareTo(BigDecimal.ZERO) == 0;
            int stallDays = inv.getLastTxnAt() != null
                    ? (int) java.time.temporal.ChronoUnit.DAYS.between(inv.getLastTxnAt().toLocalDate(), LocalDate.now())
                    : 999;

            MdItem item = itemMap.get(inv.getItemId());
            WhLocation loc = locMap.get(inv.getLocationId());
            WhWarehouse wh = whMap.get(inv.getWarehouseId());

            String anomalyType = null;
            String suggestion = null;

            if (zeroStock) {
                anomalyType = "零库存";
                suggestion = "核实是否停用、缺货或已完成出库";
            } else if (isFrozen) {
                anomalyType = "已冻结";
                suggestion = "核实冻结原因，处理后解冻";
            } else if (stallDays >= 90) {
                anomalyType = "长期慢动";
                suggestion = "评估调拨、促销或库存处置";
            } else if (stallDays >= 30) {
                anomalyType = "慢动物料";
                suggestion = "关注库存周转情况";
            }

            if (anomalyType == null) continue;

            InvAnalyticsVO.SlowMovingItem sm = new InvAnalyticsVO.SlowMovingItem();
            sm.setItemCode(item != null ? item.getItemCode() : "");
            sm.setItemName(item != null ? item.getItemName() : "");
            sm.setBatchNo(batchMap.get(inv.getBatchId()) != null ? batchMap.get(inv.getBatchId()).getBatchNo() : "");
            sm.setQuantity(inv.getOnhandQty() != null ? inv.getOnhandQty() : BigDecimal.ZERO);
            sm.setLastOutDate(inv.getLastTxnAt() != null ? inv.getLastTxnAt().toLocalDate().toString() : "—");
            sm.setStallDays(stallDays);
            sm.setLocationArea(loc != null && loc.getLocationCode() != null && loc.getLocationCode().length() > 0
                    ? loc.getLocationCode().substring(0, 1) : (wh != null ? wh.getWarehouseCode() : "—"));
            sm.setFrozen(isFrozen);
            sm.setAnomalyType(anomalyType);
            sm.setSuggestion(suggestion);
            result.add(sm);
        }
        result.sort((a, b) -> Integer.compare(b.getStallDays(), a.getStallDays()));
        return result;
    }

    private List<InvAnalyticsVO.ZoneAccuracy> buildZoneAccuracy(Long warehouseId) {
        List<InvAnalyticsVO.ZoneAccuracy> result = new ArrayList<>();
        List<WhZone> zones = whZoneMapper.selectList(null);
        Map<Long, WhZone> zoneMap = zones.stream().collect(Collectors.toMap(WhZone::getZoneId, z -> z));

        Map<Long, List<WhLocation>> locByZone = whLocationMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(WhLocation::getZoneId));

        List<String> completedStatuses = List.of("COMPLETED", "DIFFERENCE_CONFIRMED", "ADJUSTMENT_CREATED");
        LambdaQueryWrapper<InvStocktakeOrder> orderQ = new LambdaQueryWrapper<InvStocktakeOrder>()
                .in(InvStocktakeOrder::getStocktakeStatus, completedStatuses);
        if (warehouseId != null) {
            orderQ.eq(InvStocktakeOrder::getWarehouseId, warehouseId);
        }
        List<InvStocktakeOrder> completedOrders = invStocktakeOrderMapper.selectList(orderQ);
        Set<Long> completedStocktakeIds = completedOrders.stream()
                .map(InvStocktakeOrder::getStocktakeId).collect(Collectors.toSet());

        if (completedStocktakeIds.isEmpty()) {
            InvAnalyticsVO.ZoneAccuracy za = new InvAnalyticsVO.ZoneAccuracy();
            za.setZone("ALL");
            za.setName("全部库区");
            za.setTotal(0);
            za.setAccurate(0);
            za.setPct(BigDecimal.ZERO);
            result.add(za);
            return result;
        }

        List<InvStocktakeLine> countedLines = invStocktakeLineMapper.selectList(
                new LambdaQueryWrapper<InvStocktakeLine>()
                        .in(InvStocktakeLine::getStocktakeId, completedStocktakeIds)
                        .isNotNull(InvStocktakeLine::getCountedQty));

        Map<Long, Long> locToZone = whLocationMapper.selectList(null).stream()
                .collect(Collectors.toMap(WhLocation::getLocationId, WhLocation::getZoneId));

        Map<Long, List<InvStocktakeLine>> linesByZone = new HashMap<>();
        for (InvStocktakeLine line : countedLines) {
            Long zoneId = locToZone.get(line.getLocationId());
            if (zoneId == null) continue;
            linesByZone.computeIfAbsent(zoneId, k -> new ArrayList<>()).add(line);
        }

        for (Map.Entry<Long, List<InvStocktakeLine>> entry : linesByZone.entrySet()) {
            WhZone zone = zoneMap.get(entry.getKey());
            if (zone == null) continue;

            List<InvStocktakeLine> lines = entry.getValue();
            int total = lines.size();
            long accurate = lines.stream()
                    .filter(l -> l.getDifferenceQty() != null
                            && l.getDifferenceQty().compareTo(BigDecimal.ZERO) == 0)
                    .count();

            InvAnalyticsVO.ZoneAccuracy za = new InvAnalyticsVO.ZoneAccuracy();
            za.setZone(zone.getZoneCode());
            za.setName(zone.getZoneName());
            za.setTotal(total);
            za.setAccurate((int) accurate);
            za.setPct(total > 0
                    ? BigDecimal.valueOf(accurate)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            result.add(za);
        }

        if (result.isEmpty()) {
            InvAnalyticsVO.ZoneAccuracy za = new InvAnalyticsVO.ZoneAccuracy();
            za.setZone("ALL");
            za.setName("全部库区");
            za.setTotal(0);
            za.setAccurate(0);
            za.setPct(BigDecimal.ZERO);
            result.add(za);
        }
        return result;
    }

    private List<InvAnalyticsVO.SafetyCompliance> buildSafetyCompliance(Long warehouseId) {
        List<InvAnalyticsVO.SafetyCompliance> result = new ArrayList<>();
        LambdaQueryWrapper<InvSafetyStockRule> ruleQ = new LambdaQueryWrapper<InvSafetyStockRule>()
                .eq(InvSafetyStockRule::getEnabledFlag, 1);
        if (warehouseId != null) {
            ruleQ.eq(InvSafetyStockRule::getWarehouseId, warehouseId);
        }
        List<InvSafetyStockRule> rules = invSafetyStockRuleMapper.selectList(ruleQ);
        Map<Long, WhWarehouse> whMap = whWarehouseMapper.selectList(null).stream()
                .collect(Collectors.toMap(WhWarehouse::getWarehouseId, w -> w));

        Map<Long, List<InvSafetyStockRule>> byWarehouse = rules.stream()
                .collect(Collectors.groupingBy(InvSafetyStockRule::getWarehouseId));

        for (Map.Entry<Long, List<InvSafetyStockRule>> entry : byWarehouse.entrySet()) {
            WhWarehouse wh = whMap.get(entry.getKey());
            int total = entry.getValue().size();
            int compliant = (int) entry.getValue().stream().filter(rule -> {
                List<InvInventory> invs = invInventoryMapper.selectList(
                        new LambdaQueryWrapper<InvInventory>()
                                .eq(InvInventory::getWarehouseId, rule.getWarehouseId())
                                .eq(InvInventory::getItemId, rule.getItemId()));
                BigDecimal avail = invs.stream().map(InvInventory::getAvailableQty)
                        .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
                return avail.compareTo(rule.getMinQty() != null ? rule.getMinQty() : BigDecimal.ZERO) >= 0;
            }).count();

            InvAnalyticsVO.SafetyCompliance sc = new InvAnalyticsVO.SafetyCompliance();
            sc.setZone(wh != null ? wh.getWarehouseCode() : "WH" + entry.getKey());
            sc.setName(wh != null ? wh.getWarehouseName() : "仓库" + entry.getKey());
            sc.setTotal(total);
            sc.setCompliant(compliant);
            sc.setPct(total > 0 ? BigDecimal.valueOf(compliant)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(100));
            result.add(sc);
        }

        if (result.isEmpty()) {
            InvAnalyticsVO.SafetyCompliance sc = new InvAnalyticsVO.SafetyCompliance();
            sc.setZone("ALL");
            sc.setName("全部");
            sc.setTotal(1);
            sc.setCompliant(1);
            sc.setPct(BigDecimal.valueOf(100));
            result.add(sc);
        }
        return result;
    }

    private List<InvAnalyticsVO.AdjustmentTrend> buildAdjustmentTrend(Long warehouseId) {
        List<InvAnalyticsVO.AdjustmentTrend> result = new ArrayList<>();
        List<InvAdjustmentOrder> orders = invAdjustmentOrderMapper.selectList(null);
        if (warehouseId != null) {
            Set<Long> whStocktakeIds = invStocktakeOrderMapper.selectList(
                    new LambdaQueryWrapper<InvStocktakeOrder>()
                            .eq(InvStocktakeOrder::getWarehouseId, warehouseId))
                    .stream().map(InvStocktakeOrder::getStocktakeId).collect(Collectors.toSet());
            orders = orders.stream()
                    .filter(o -> o.getStocktakeId() != null && whStocktakeIds.contains(o.getStocktakeId()))
                    .toList();
        }

        Map<String, Long> byMonth = orders.stream()
                .filter(o -> o.getApprovedAt() != null)
                .collect(Collectors.groupingBy(
                        o -> o.getApprovedAt().format(DateTimeFormatter.ofPattern("M'月'")),
                        TreeMap::new,
                        Collectors.counting()));

        for (Map.Entry<String, Long> e : byMonth.entrySet()) {
            InvAnalyticsVO.AdjustmentTrend at = new InvAnalyticsVO.AdjustmentTrend();
            at.setMonth(e.getKey());
            at.setCount(e.getValue().intValue());
            result.add(at);
        }

        if (result.isEmpty()) {
            for (int i = 2; i <= 7; i++) {
                InvAnalyticsVO.AdjustmentTrend at = new InvAnalyticsVO.AdjustmentTrend();
                at.setMonth(i + "月");
                at.setCount(0);
                result.add(at);
            }
        }
        return result;
    }

    private List<String> buildInsights(InvAnalyticsVO vo) {
        List<String> insights = new ArrayList<>();
        InvAnalyticsVO.SummaryKpi kpi = vo.getKpi();

        for (InvAnalyticsVO.ZoneAccuracy za : vo.getZoneAccuracy()) {
            if (za.getPct().compareTo(BigDecimal.valueOf(90)) < 0) {
                insights.add(String.format(
                        "%s %s 库位账实准确率仅 %s%%，显著低于其他库区，建议对该区全面复盘。",
                        za.getZone(), za.getName(), za.getPct()));
            }
        }

        if (kpi.getSafetyComplianceRate().compareTo(BigDecimal.valueOf(95)) < 0) {
            insights.add(String.format(
                    "安全库存达标率 %s%%，不达标项集中在高周转物料和低周转备件，建议调整安全库存系数。",
                    kpi.getSafetyComplianceRate()));
        }

        List<InvAnalyticsVO.SlowMovingItem> zeroStock = vo.getSlowMoving().stream()
                .filter(s -> "零库存".equals(s.getAnomalyType())).toList();
        for (InvAnalyticsVO.SlowMovingItem s : zeroStock) {
            if (s.getStallDays() >= 30) {
                insights.add(String.format(
                        "%s 已无库存且长期无出库记录（%s天），建议做报废或盘亏处理。", s.getItemName(), s.getStallDays()));
            } else {
                insights.add(String.format(
                        "%s 已无库存（停滞%s天），建议核实是否停用、缺货或已完成出库。", s.getItemName(), s.getStallDays()));
            }
        }

        if (kpi.getPendingAdjustmentCount() > 0) {
            insights.add(String.format(
                    "待处理调整单 %s 单未审核，建议提醒仓库主管优先处理。",
                    kpi.getPendingAdjustmentCount()));
        }

        List<InvAnalyticsVO.TurnoverPoint> trend = vo.getTurnoverTrend();
        if (!trend.isEmpty()) {
            InvAnalyticsVO.TurnoverPoint last = trend.get(trend.size() - 1);
            insights.add(String.format(
                    "%s 库存周转率 %s，接近合理区间，月底可能需关注备货。",
                    last.getDay(), last.getRate()));
        }

        if (insights.isEmpty()) {
            insights.add("当前库存各项指标均在正常范围内，无需特别处理。");
        }
        return insights;
    }

    private List<InvAnalyticsVO.FilterOption> buildFilters() {
        List<InvAnalyticsVO.FilterOption> filters = new ArrayList<>();

        List<WhWarehouse> whList = whWarehouseMapper.selectList(null);
        InvAnalyticsVO.FilterOption warehouseFilter = new InvAnalyticsVO.FilterOption();
        warehouseFilter.setField("warehouseId");
        warehouseFilter.setLabel("仓库");
        List<InvAnalyticsVO.FilterOption.OptionItem> whOptions = new ArrayList<>();
        InvAnalyticsVO.FilterOption.OptionItem allWh = new InvAnalyticsVO.FilterOption.OptionItem();
        allWh.setValue("");
        allWh.setLabel("全部");
        whOptions.add(allWh);
        for (WhWarehouse w : whList) {
            InvAnalyticsVO.FilterOption.OptionItem opt = new InvAnalyticsVO.FilterOption.OptionItem();
            opt.setValue(String.valueOf(w.getWarehouseId()));
            opt.setLabel(w.getWarehouseName());
            whOptions.add(opt);
        }
        warehouseFilter.setOptions(whOptions);
        filters.add(warehouseFilter);

        return filters;
    }
}
