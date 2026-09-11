package com.upc.wms.controller;

import com.upc.wms.common.Result;
import com.upc.wms.dto.PickingSubmitRequest;
import com.upc.wms.dto.ReviewSubmitRequest;
import com.upc.wms.entity.OutOrder;
import com.upc.wms.entity.OutPickingTask;
import com.upc.wms.entity.OutReviewTask;
import com.upc.wms.service.OutboundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/outbound")
@RequiredArgsConstructor
public class OutboundController {

    private final OutboundService outboundService;

    @PostMapping("/create")
    public Result<OutOrder> create(@RequestParam Long requisitionId, @RequestParam Long warehouseId) {
        return Result.success(outboundService.createOutboundOrder(requisitionId, warehouseId));
    }

    @GetMapping("/pending")
    public Result<List<OutOrder>> pending() {
        return Result.success(outboundService.listPendingOutbound());
    }

    @GetMapping("/detail")
    public Result<Map<String, Object>> detail(@RequestParam Long outboundId) {
        return Result.success(outboundService.getOutboundDetail(outboundId));
    }

    @PostMapping("/picking/create")
    public Result<OutPickingTask> createPicking(@RequestParam Long outboundId,
                                                @RequestParam(required = false) Long assignedTo) {
        return Result.success(outboundService.createPickingTask(outboundId, assignedTo));
    }

    @PostMapping("/picking/assign")
    public Result<OutPickingTask> assignPicking(@RequestParam Long pickingTaskId,
                                                @RequestParam Long assignedTo) {
        return Result.success(outboundService.assignPickingTask(pickingTaskId, assignedTo));
    }

    @PostMapping("/picking/submit")
    public Result<Void> submitPicking(@RequestBody PickingSubmitRequest request) {
        outboundService.submitPicking(request);
        return Result.success();
    }

    @PostMapping("/review/create")
    public Result<OutReviewTask> createReview(@RequestParam Long outboundId,
                                              @RequestParam(required = false) Long reviewedBy) {
        return Result.success(outboundService.createReviewTask(outboundId, reviewedBy));
    }

    @PostMapping("/review/submit")
    public Result<OutReviewTask> submitReview(@RequestBody ReviewSubmitRequest request) {
        return Result.success(outboundService.submitReview(request));
    }

    @PostMapping("/confirm")
    public Result<OutOrder> confirm(@RequestParam Long outboundId, @RequestParam(required = false) Long operatedBy) {
        return Result.success(outboundService.confirmOutbound(outboundId, operatedBy));
    }
}
