package com.upc.wms.controller;

import com.upc.wms.common.Result;
import com.upc.wms.entity.AgvTask;
import com.upc.wms.entity.BarcodeLabel;
import com.upc.wms.entity.IotEvent;
import com.upc.wms.entity.RfidTag;
import com.upc.wms.service.SmartWarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/smart")
@RequiredArgsConstructor
public class SmartWarehouseController {

    private final SmartWarehouseService smartWarehouseService;

    @PostMapping("/barcode/bind")
    public Result<BarcodeLabel> bindBarcode(@RequestBody BarcodeLabel label) {
        return Result.success(smartWarehouseService.bindBarcode(label));
    }

    @GetMapping("/barcode/scan")
    public Result<BarcodeLabel> scanBarcode(@RequestParam String barcodeValue) {
        return Result.success(smartWarehouseService.scanBarcode(barcodeValue));
    }

    @PostMapping("/rfid/bind")
    public Result<RfidTag> bindRfid(@RequestBody RfidTag tag) {
        return Result.success(smartWarehouseService.bindRfid(tag));
    }

    @PostMapping("/agv/task")
    public Result<AgvTask> createAgvTask(@RequestBody AgvTask task) {
        return Result.success(smartWarehouseService.createAgvTask(task));
    }

    @PutMapping("/agv/task/status")
    public Result<Void> updateAgvStatus(@RequestParam Long agvTaskId, @RequestParam String status) {
        smartWarehouseService.updateAgvTaskStatus(agvTaskId, status);
        return Result.success();
    }

    @PostMapping("/iot/event")
    public Result<IotEvent> saveIotEvent(@RequestBody IotEvent event) {
        return Result.success(smartWarehouseService.saveIotEvent(event));
    }

    @GetMapping("/iot/event/unprocessed")
    public Result<List<IotEvent>> unprocessedEvents() {
        return Result.success(smartWarehouseService.listUnprocessedIotEvents());
    }
}
