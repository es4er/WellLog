package com.upc.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.upc.wms.dto.KitShortageAnalysis;
import com.upc.wms.entity.InvInventory;
import com.upc.wms.entity.MdBatch;
import com.upc.wms.mapper.InvInventoryMapper;
import com.upc.wms.mapper.MdBatchMapper;
import com.upc.wms.service.KitAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KitAvailabilityServiceImpl implements KitAvailabilityService {

    private static final Set<String> RELEASED_QUALITY = Set.of("QUALIFIED", "RELEASED");
    private static final Set<String> PENDING_QUALITY = Set.of("PENDING_INSPECTION", "PENDING", "INSPECTING");
    private static final Set<String> ABNORMAL_QUALITY = Set.of("UNQUALIFIED", "REJECTED", "HOLD");

    private final InvInventoryMapper inventoryMapper;
    private final MdBatchMapper batchMapper;

    @Override
    public boolean isQualityReleased(String qualityStatus) {
        if (qualityStatus == null || qualityStatus.isBlank()) {
            // 无批次信息时不计入齐套，避免把未质检库存算作可用
            return false;
        }
        return RELEASED_QUALITY.contains(qualityStatus.toUpperCase());
    }

    @Override
    public boolean isKitAvailable(InvInventory inv, String qualityStatus) {
        if (inv == null) {
            return false;
        }
        if (!"AVAILABLE".equalsIgnoreCase(inv.getInventoryStatus())) {
            return false;
        }
        BigDecimal available = inv.getAvailableQty() == null ? BigDecimal.ZERO : inv.getAvailableQty();
        if (available.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        BigDecimal frozen = inv.getFrozenQty() == null ? BigDecimal.ZERO : inv.getFrozenQty();
        if (frozen.compareTo(BigDecimal.ZERO) > 0 && available.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return isQualityReleased(qualityStatus);
    }

    @Override
    public List<InvInventory> listKitAvailableCandidates(Long itemId, Long warehouseId) {
        if (itemId == null) {
            return List.of();
        }
        Map<Long, String> qualityByBatch = loadBatchQualityMap();
        return inventoryMapper.selectList(new LambdaQueryWrapper<InvInventory>().eq(InvInventory::getItemId, itemId))
                .stream()
                .filter(i -> warehouseId == null || warehouseId.equals(i.getWarehouseId()))
                .filter(i -> isKitAvailable(i, qualityByBatch.get(i.getBatchId())))
                .sorted(Comparator.comparing(InvInventory::getInventoryId))
                .toList();
    }

    @Override
    public BigDecimal sumKitAvailableQty(Long itemId) {
        return sumKitAvailableQty(itemId, null);
    }

    @Override
    public BigDecimal sumKitAvailableQty(Long itemId, Long warehouseId) {
        return listKitAvailableCandidates(itemId, warehouseId).stream()
                .map(i -> i.getAvailableQty() == null ? BigDecimal.ZERO : i.getAvailableQty())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public Map<Long, BigDecimal> sumKitAvailableByItem() {
        Map<Long, String> qualityByBatch = loadBatchQualityMap();
        Map<Long, BigDecimal> result = new HashMap<>();
        for (InvInventory inv : inventoryMapper.selectList(null)) {
            if (!isKitAvailable(inv, qualityByBatch.get(inv.getBatchId()))) {
                continue;
            }
            BigDecimal qty = inv.getAvailableQty() == null ? BigDecimal.ZERO : inv.getAvailableQty();
            result.merge(inv.getItemId(), qty, BigDecimal::add);
        }
        return result;
    }

    @Override
    public KitShortageAnalysis analyze(Long itemId, BigDecimal requiredQty) {
        KitShortageAnalysis analysis = new KitShortageAnalysis();
        analysis.setItemId(itemId);
        BigDecimal required = requiredQty == null ? BigDecimal.ZERO : requiredQty;
        analysis.setRequiredQty(required);

        if (itemId == null) {
            analysis.setKitReady(required.compareTo(BigDecimal.ZERO) <= 0);
            if (!analysis.isKitReady()) {
                analysis.setShortageType(KitShortageAnalysis.REAL_SHORTAGE);
                analysis.setShortageTypeLabel(KitShortageAnalysis.labelOf(KitShortageAnalysis.REAL_SHORTAGE));
                analysis.setShortageQty(required);
            }
            return analysis;
        }

        Map<Long, String> qualityByBatch = loadBatchQualityMap();
        List<InvInventory> invList = inventoryMapper.selectList(
                new LambdaQueryWrapper<InvInventory>().eq(InvInventory::getItemId, itemId));

        BigDecimal kitAvailable = BigDecimal.ZERO;
        BigDecimal onhand = BigDecimal.ZERO;
        BigDecimal pending = BigDecimal.ZERO;
        BigDecimal abnormal = BigDecimal.ZERO;
        BigDecimal locationUnavailable = BigDecimal.ZERO;

        for (InvInventory inv : invList) {
            BigDecimal onhandLine = inv.getOnhandQty() == null ? BigDecimal.ZERO : inv.getOnhandQty();
            BigDecimal availableLine = inv.getAvailableQty() == null ? BigDecimal.ZERO : inv.getAvailableQty();
            onhand = onhand.add(onhandLine);

            String quality = qualityByBatch.get(inv.getBatchId());
            boolean statusOk = "AVAILABLE".equalsIgnoreCase(inv.getInventoryStatus());
            boolean qtyOk = availableLine.compareTo(BigDecimal.ZERO) > 0;

            if (isKitAvailable(inv, quality)) {
                kitAvailable = kitAvailable.add(availableLine);
                continue;
            }

            if (isPendingQuality(quality) && onhandLine.compareTo(BigDecimal.ZERO) > 0) {
                pending = pending.add(onhandLine);
            } else if (isAbnormalQuality(quality) && onhandLine.compareTo(BigDecimal.ZERO) > 0) {
                abnormal = abnormal.add(onhandLine);
            } else if ((!statusOk || !qtyOk) && onhandLine.compareTo(BigDecimal.ZERO) > 0) {
                locationUnavailable = locationUnavailable.add(onhandLine);
            } else if (onhandLine.compareTo(BigDecimal.ZERO) > 0) {
                // 有货但质量未知/未放行
                pending = pending.add(onhandLine);
            }
        }

        analysis.setKitAvailableQty(kitAvailable);
        analysis.setOnhandQty(onhand);
        analysis.setPendingInspectionQty(pending);
        analysis.setQualityBlockedQty(abnormal);
        analysis.setLocationUnavailableQty(locationUnavailable);

        boolean ready = kitAvailable.compareTo(required) >= 0;
        analysis.setKitReady(ready);
        if (ready) {
            return analysis;
        }

        analysis.setShortageQty(required.subtract(kitAvailable).max(BigDecimal.ZERO));
        String type = classifyShortage(required, kitAvailable, onhand, pending, abnormal, locationUnavailable);
        analysis.setShortageType(type);
        analysis.setShortageTypeLabel(KitShortageAnalysis.labelOf(type));
        return analysis;
    }

    private String classifyShortage(BigDecimal required,
                                    BigDecimal kitAvailable,
                                    BigDecimal onhand,
                                    BigDecimal pending,
                                    BigDecimal abnormal,
                                    BigDecimal locationUnavailable) {
        // 账面总量不足 → 真实缺料
        if (onhand.compareTo(required) < 0) {
            return KitShortageAnalysis.REAL_SHORTAGE;
        }
        // 加上待检即可满足 → 质量未放行
        if (kitAvailable.add(pending).compareTo(required) >= 0 && pending.compareTo(BigDecimal.ZERO) > 0) {
            return KitShortageAnalysis.QUALITY_PENDING;
        }
        // 有不合格/冻结质量库存挡路 → 质量异常
        if (abnormal.compareTo(BigDecimal.ZERO) > 0) {
            return KitShortageAnalysis.QUALITY_ABNORMAL;
        }
        // 有货但库位/状态不可用
        if (locationUnavailable.compareTo(BigDecimal.ZERO) > 0) {
            return KitShortageAnalysis.LOCATION_UNAVAILABLE;
        }
        // 待检存在但不足以单独解释时，仍优先提示质量未放行
        if (pending.compareTo(BigDecimal.ZERO) > 0) {
            return KitShortageAnalysis.QUALITY_PENDING;
        }
        return KitShortageAnalysis.REAL_SHORTAGE;
    }

    private boolean isPendingQuality(String qualityStatus) {
        if (qualityStatus == null || qualityStatus.isBlank()) {
            return true;
        }
        return PENDING_QUALITY.contains(qualityStatus.toUpperCase());
    }

    private boolean isAbnormalQuality(String qualityStatus) {
        if (qualityStatus == null || qualityStatus.isBlank()) {
            return false;
        }
        return ABNORMAL_QUALITY.contains(qualityStatus.toUpperCase());
    }

    private Map<Long, String> loadBatchQualityMap() {
        return batchMapper.selectList(null).stream()
                .filter(Objects::nonNull)
                .filter(b -> b.getBatchId() != null)
                .collect(Collectors.toMap(MdBatch::getBatchId, MdBatch::getQualityStatus, (a, b) -> a));
    }
}
