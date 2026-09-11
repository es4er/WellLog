package com.upc.wms.controller;

import com.upc.wms.common.Result;
import com.upc.wms.dto.InvAnalyticsVO;
import com.upc.wms.service.InventoryAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryAnalyticsController {

    private final InventoryAnalyticsService inventoryAnalyticsService;

    @GetMapping("/analytics/dashboard")
    public Result<InvAnalyticsVO> dashboard(
            @RequestParam(required = false) Long warehouseId) {
        return Result.success(inventoryAnalyticsService.getDashboard(warehouseId));
    }
}
