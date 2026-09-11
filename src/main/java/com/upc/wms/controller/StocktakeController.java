package com.upc.wms.controller;

import com.upc.wms.common.Result;
import com.upc.wms.dto.StocktakeCountRequest;
import com.upc.wms.entity.InvAdjustmentOrder;
import com.upc.wms.entity.InvAdjustmentLine;
import com.upc.wms.entity.InvStocktakeDifference;
import com.upc.wms.entity.InvStocktakeOrder;
import com.upc.wms.service.StocktakeService;
import com.upc.wms.vo.StocktakeDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/stocktake")
@RequiredArgsConstructor
public class StocktakeController {

    private final StocktakeService stocktakeService;

    @GetMapping("/list")
    public Result<java.util.List<com.upc.wms.vo.StocktakeListVO>> list(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {

        if (warehouseId == null) {
            warehouseId = 1L;
        }

        return Result.success(
            stocktakeService.list(
                warehouseId,
                status,
                keyword
            )
        );
    }

    @GetMapping("/detail")
    public Result<StocktakeDetailVO> detail(@RequestParam Long stocktakeId) {
        return Result.success(stocktakeService.getStocktakeDetail(stocktakeId));
    }

    @PostMapping("/create")
    public Result<InvStocktakeOrder> create(@RequestParam Long warehouseId,
                                            @RequestParam(required = false) String stocktakeType,
                                            @RequestParam(required = false) String scope,
                                            @RequestParam(required = false) Long createdBy) {
        return Result.success(stocktakeService.createStocktake(warehouseId, stocktakeType, scope, createdBy));
    }

    @PostMapping("/dispatch")
    public Result<Void> dispatch(@RequestParam Long stocktakeId,
                                 @RequestParam(required = false) Long executorId) {
        stocktakeService.dispatchStocktake(stocktakeId, executorId);
        return Result.success();
    }

    @PostMapping("/submit")
    public Result<Void> submit(@RequestBody StocktakeCountRequest request) {
        stocktakeService.submitCountResult(request);
        return Result.success();
    }

    @PostMapping("/difference/generate")
    public Result<List<InvStocktakeDifference>> generateDifference(@RequestParam Long stocktakeId) {
        return Result.success(stocktakeService.generateDifference(stocktakeId));
    }

    @GetMapping("/difference/list")
    public Result<List<InvStocktakeDifference>> listDifference(@RequestParam Long stocktakeId) {
        return Result.success(stocktakeService.listDifferences(stocktakeId));
    }

    @PostMapping("/difference/confirm")
    public Result<Void> confirmDifference(@RequestParam Long stocktakeId) {
        stocktakeService.confirmAllDifferences(stocktakeId);
        return Result.success();
    }

    @PostMapping("/adjustment/create")
    public Result<InvAdjustmentOrder> createAdjustment(@RequestParam Long stocktakeId,
                                                       @RequestParam(required = false) String reason) {
        return Result.success(stocktakeService.createAdjustment(stocktakeId, reason));
    }

    @PostMapping("/adjustment/approve")
    public Result<Void> approveAdjustment(@RequestParam Long adjustmentId,
                                          @RequestParam(required = false) Long approvedBy) {
        stocktakeService.approveAdjustment(adjustmentId, approvedBy);
        return Result.success();
    }

    @PostMapping("/adjustment/reject")
    public Result<Void> rejectAdjustment(@RequestParam Long adjustmentId) {
        stocktakeService.rejectAdjustment(adjustmentId);
        return Result.success();
    }

    @PostMapping("/adjustment/manual")
    public Result<List<InvAdjustmentLine>> createManualAdjustment(@RequestBody Map<String, Object> body) {
        String reason = (String) body.getOrDefault("reason", "手动调整");
        Long createdBy = body.get("createdBy") != null ? Long.valueOf(body.get("createdBy").toString()) : null;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("lines");
        return Result.success(stocktakeService.createAdjustmentManually(reason, lines, createdBy));
    }

    @GetMapping("/adjustment/list")
    public Result<List<InvAdjustmentOrder>> adjustmentList(@RequestParam(required = false) String status) {
        return Result.success(stocktakeService.listAdjustmentOrders(status));
    }

    @GetMapping("/adjustment/detail")
    public Result<Map<String, Object>> adjustmentDetail(@RequestParam Long adjustmentId) {
        return Result.success(stocktakeService.getAdjustmentDetail(adjustmentId));
    }
}
