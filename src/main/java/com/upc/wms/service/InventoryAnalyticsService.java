package com.upc.wms.service;

import com.upc.wms.dto.InvAnalyticsVO;

public interface InventoryAnalyticsService {
    InvAnalyticsVO getDashboard(Long warehouseId);
}
