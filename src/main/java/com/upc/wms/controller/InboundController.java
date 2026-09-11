package com.upc.wms.controller;

import com.upc.wms.common.Result;
import com.upc.wms.dto.InboundConfirmRequest;
import com.upc.wms.entity.InInboundOrder;
import com.upc.wms.service.InboundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/inbound")
@RequiredArgsConstructor
public class InboundController {

    private final InboundService inboundService;

    @PostMapping("/create")
    public Result<InInboundOrder> create(@RequestParam Long receiptId,
                                         @RequestParam Long warehouseId,
                                         @RequestParam(required = false) Long operatedBy) {
        return Result.success(inboundService.createInboundOrder(receiptId, warehouseId, operatedBy));
    }

    @GetMapping("/pending")
    public Result<List<InInboundOrder>> pending() {
        return Result.success(inboundService.listPendingInbound());
    }

    @GetMapping("/detail")
    public Result<Map<String, Object>> detail(@RequestParam Long inboundId) {
        return Result.success(inboundService.getInboundDetail(inboundId));
    }

    @PostMapping("/confirm")
    public Result<InInboundOrder> confirm(@RequestBody InboundConfirmRequest request) {
        return Result.success(inboundService.confirmInbound(request));
    }
}
