package com.upc.wms.controller;

import com.upc.wms.common.Result;
import com.upc.wms.dto.*;
import com.upc.wms.service.WorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/worker")
@RequiredArgsConstructor
public class WorkerController {

    private final WorkerService workerService;

    @GetMapping("/workbench")
    public Result<WorkerWorkbenchVO> workbench(@RequestParam(defaultValue = "1") Long workerId) {
        return Result.success(workerService.loadWorkbench(workerId));
    }

    @PostMapping("/scan")
    public Result<WorkerScanResultVO> scan(@RequestBody WorkerScanRequest request) {
        return Result.success(workerService.submitScan(request));
    }

    @PostMapping("/exception")
    public Result<WorkerExceptionVO> exception(@RequestBody WorkerExceptionSubmitRequest request) {
        return Result.success(workerService.submitException(request));
    }

    @PostMapping("/handover")
    public Result<WorkerTaskVO> handover(@RequestBody WorkerHandoverRequest request) {
        return Result.success(workerService.confirmHandover(request));
    }

    @GetMapping("/barcode/scan")
    public Result<BarcodeScanDetailVO> scanBarcode(@RequestParam String barcodeValue) {
        return Result.success(workerService.resolveBarcode(barcodeValue));
    }

    @GetMapping("/replenish")
    public Result<List<WorkerReplenishVO>> replenishList(@RequestParam(defaultValue = "1") Long workerId) {
        return Result.success(workerService.listReplenish(workerId));
    }

    @PostMapping("/replenish")
    public Result<WorkerReplenishVO> replenishSubmit(@RequestBody WorkerReplenishSubmitRequest request) {
        return Result.success(workerService.submitReplenish(request));
    }

    @GetMapping("/operators")
    public Result<List<WorkerOperatorVO>> operators() {
        return Result.success(workerService.listProductionWorkers());
    }

    @GetMapping("/notifications")
    public Result<List<WorkerNotificationVO>> notifications(@RequestParam(defaultValue = "1") Long workerId) {
        return Result.success(workerService.listNotifications(workerId));
    }

    @PostMapping("/notifications/{id}/read")
    public Result<Void> markNotificationRead(@PathVariable("id") Long id,
                                             @RequestParam(defaultValue = "1") Long workerId) {
        workerService.markNotificationRead(id, workerId);
        return Result.success();
    }

    @PostMapping("/completion")
    public Result<WorkerCompletionVO> completion(@RequestBody WorkerCompletionSubmitRequest request) {
        return Result.success(workerService.submitCompletion(request));
    }

    @GetMapping("/completion")
    public Result<List<WorkerCompletionVO>> completionList(@RequestParam(defaultValue = "1") Long workerId) {
        return Result.success(workerService.listCompletions(workerId));
    }

    @PostMapping("/transfer")
    public Result<WorkerTransferVO> transfer(@RequestBody WorkerTransferSubmitRequest request) {
        return Result.success(workerService.submitTransfer(request));
    }

    @GetMapping("/transfer")
    public Result<List<WorkerTransferVO>> transferList(@RequestParam(defaultValue = "1") Long workerId) {
        return Result.success(workerService.listTransfers(workerId));
    }
}
