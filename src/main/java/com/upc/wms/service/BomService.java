package com.upc.wms.service;

import com.upc.wms.dto.BomComponent;

import java.util.List;
import java.util.Map;

public interface BomService {

    /**
     * 查询成品启用中的 BOM 组件（每 1 套成品所需数量）。
     * 无启用 BOM 时返回空列表。
     */
    List<BomComponent> listActiveComponents(Long productItemId);

    /**
     * 按成品汇总 BOM 组件用量（同组件合并）。
     */
    Map<Long, java.math.BigDecimal> expandMaterialNeed(Long productItemId, java.math.BigDecimal productQty);

    /**
     * 成品 BOM 详情（含表头 + 行），无 BOM 返回 null。
     */
    Map<String, Object> getBomByProduct(Long productItemId);
}
