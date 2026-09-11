package com.upc.wms.controller;

import com.upc.wms.common.Result;
import com.upc.wms.dto.ReceiptCreateRequest;
import com.upc.wms.dto.ReceiptSubmitInspectionRequest;
import com.upc.wms.dto.ReceiptSubmitInspectionResultVO;
import com.upc.wms.entity.RecReceiptOrder;
import com.upc.wms.service.QualityService;
import com.upc.wms.service.ReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/receipt")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;
    private final QualityService qualityService;

    @PostMapping("/create")
    public Result<RecReceiptOrder> create(@RequestBody ReceiptCreateRequest request) {
        return Result.success(receiptService.createReceipt(request));
    }

    @GetMapping("/list")
    public Result<List<RecReceiptOrder>> list() {
        return Result.success(receiptService.listReceipts());
    }

    @GetMapping("/detail")
    public Result<Map<String, Object>> detail(@RequestParam Long receiptId) {
        return Result.success(receiptService.getReceiptDetail(receiptId));
    }

    @GetMapping("/pending-inspection")
    public Result<List<RecReceiptOrder>> pendingInspection() {
        return Result.success(receiptService.listPendingInspectionReceipts());
    }

    @PutMapping("/status")
    public Result<Void> updateStatus(@RequestParam Long receiptId, @RequestParam String status) {
        receiptService.updateReceiptStatus(receiptId, status);
        return Result.success();
    }

    /** 仓管员勾选收货明细，提交至质检员待检队列 */
    @PostMapping("/submit-inspection")
    public Result<ReceiptSubmitInspectionResultVO> submitInspection(@RequestBody ReceiptSubmitInspectionRequest request) {
        return Result.success(qualityService.submitReceiptForInspection(request));
    }
}
