package com.upc.wms.controller;

import com.upc.wms.common.Result;
import com.upc.wms.dto.TransferCreateRequest;
import com.upc.wms.entity.WhTransferOrder;
import com.upc.wms.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/transfer")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping("/create")
    public Result<WhTransferOrder> create(@RequestBody TransferCreateRequest request) {
        return Result.success(transferService.createTransfer(request));
    }

    @GetMapping("/detail")
    public Result<Map<String, Object>> detail(@RequestParam Long transferId) {
        return Result.success(transferService.getTransferDetail(transferId));
    }

    @PostMapping("/confirm")
    public Result<WhTransferOrder> confirm(@RequestParam Long transferId, @RequestParam(required = false) Long operatedBy) {
        return Result.success(transferService.confirmTransfer(transferId, operatedBy));
    }
}
