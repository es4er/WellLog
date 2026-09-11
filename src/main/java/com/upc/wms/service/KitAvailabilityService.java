package com.upc.wms.service;

import com.upc.wms.dto.KitShortageAnalysis;
import com.upc.wms.entity.InvInventory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 客户订单线齐套可用量：已入库 + 质检合格/放行 + 未冻结 + 可用数量 &gt; 0。
 */
public interface KitAvailabilityService {

    /** 批次是否质检合格/放行，可计入齐套 */
    boolean isQualityReleased(String qualityStatus);

    /** 库存行是否计入齐套可用 */
    boolean isKitAvailable(InvInventory inv, String qualityStatus);

    List<InvInventory> listKitAvailableCandidates(Long itemId, Long warehouseId);

    BigDecimal sumKitAvailableQty(Long itemId);

    BigDecimal sumKitAvailableQty(Long itemId, Long warehouseId);

    /** 全物料齐套可用量汇总 */
    Map<Long, BigDecimal> sumKitAvailableByItem();

    KitShortageAnalysis analyze(Long itemId, BigDecimal requiredQty);
}
