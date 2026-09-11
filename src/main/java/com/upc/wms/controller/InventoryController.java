package com.upc.wms.controller;

import com.upc.wms.common.Result;
import com.upc.wms.dto.CreateInventoryRequest;
import com.upc.wms.dto.DownShelfRequest;
import com.upc.wms.dto.FreezeRequest;
import com.upc.wms.dto.LocationMapOperateRequest;
import com.upc.wms.entity.InvAlertRecord;
import com.upc.wms.entity.InvFreezeRecord;
import com.upc.wms.entity.InvInventory;
import com.upc.wms.entity.InvSafetyStockRule;
import com.upc.wms.entity.InvTransaction;
import com.upc.wms.service.InventoryService;
import com.upc.wms.vo.InvFreezeRecordVO;
import com.upc.wms.vo.InvInventoryDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/list")
    public Result<List<InvInventory>> list() {
        return Result.success(inventoryService.queryInventory());
    }

    @GetMapping("/list-with-detail")
    public Result<List<InvInventoryDetailVO>> listWithDetail() {
        return Result.success(inventoryService.queryInventoryWithDetail());
    }

    @GetMapping("/detail")
    public Result<InvInventory> detail(@RequestParam Long inventoryId) {
        return Result.success(inventoryService.getInventoryDetail(inventoryId));
    }

    @GetMapping("/by-item")
    public Result<List<InvInventory>> byItem(@RequestParam Long itemId) {
        return Result.success(inventoryService.queryInventoryByItem(itemId));
    }

    @GetMapping("/by-batch")
    public Result<List<InvInventory>> byBatch(@RequestParam Long batchId) {
        return Result.success(inventoryService.queryInventoryByBatch(batchId));
    }

    @GetMapping("/by-location")
    public Result<List<InvInventory>> byLocation(@RequestParam Long locationId) {
        return Result.success(inventoryService.queryInventoryByLocation(locationId));
    }

    @GetMapping("/transactions")
    public Result<List<InvTransaction>> transactions(@RequestParam Long itemId, @RequestParam Long batchId) {
        return Result.success(inventoryService.queryTransactions(itemId, batchId));
    }

    @GetMapping("/trace")
    public Result<List<InvTransaction>> trace(@RequestParam Long itemId, @RequestParam Long batchId) {
        return Result.success(inventoryService.traceBatch(itemId, batchId));
    }

    @PostMapping("/freeze")
    public Result<InvFreezeRecord> freeze(@RequestBody FreezeRequest request) {
        return Result.success(inventoryService.freezeInventory(request.getInventoryId(), request.getQty(),
                request.getReason(), request.getOperatedBy()));
    }

    @PostMapping("/unfreeze")
    public Result<InvFreezeRecord> unfreeze(@RequestBody FreezeRequest request) {
        return Result.success(inventoryService.unfreezeInventory(request.getInventoryId(), request.getQty(),
                request.getReason(), request.getOperatedBy()));
    }

    @PostMapping("/safety-stock")
    public Result<InvSafetyStockRule> setSafetyStock(@RequestBody InvSafetyStockRule rule) {
        return Result.success(inventoryService.setSafetyStockRule(rule));
    }

    @PostMapping("/check-safety-stock")
    public Result<List<InvAlertRecord>> checkSafetyStock() {
        return Result.success(inventoryService.checkSafetyStock());
    }

    @GetMapping("/alerts")
    public Result<List<InvAlertRecord>> alerts() {
        return Result.success(inventoryService.listInventoryAlerts());
    }

    @PostMapping("/alert/close")
    public Result<Void> closeAlert(@RequestParam Long alertId) {
        inventoryService.closeAlert(alertId);
        return Result.success();
    }

    @PostMapping("/create")
    public Result<InvInventory> create(@RequestBody CreateInventoryRequest request) {
        return Result.success(inventoryService.createInventory(request));
    }

    @PostMapping("/import")
    public Result<Integer> importInventory(@RequestBody List<CreateInventoryRequest> rows) {
        return Result.success(inventoryService.importInventory(rows));
    }

    @PostMapping("/putaway")
    public Result<InvInventory> putaway(@RequestBody LocationMapOperateRequest request) {
        return Result.success(inventoryService.putaway(request));
    }

    @PostMapping("/down-shelf")
    public Result<Void> downShelf(@RequestBody DownShelfRequest request) {
        inventoryService.downShelf(request);
        return Result.success();
    }

    @GetMapping("/freeze-records")
    public Result<List<InvFreezeRecordVO>> freezeRecords() {
        return Result.success(inventoryService.listFreezeRecords());
    }
}
