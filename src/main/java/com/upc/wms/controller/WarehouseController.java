package com.upc.wms.controller;

import com.upc.wms.common.Result;
import com.upc.wms.dto.PrepLocationOptionVO;
import com.upc.wms.dto.PrepLocationRecommendVO;
import com.upc.wms.dto.WarehouseExceptionVO;
import com.upc.wms.dto.WarehousePickingCompleteRequest;
import com.upc.wms.dto.WarehousePickingScanRequest;
import com.upc.wms.dto.WarehousePickingTaskVO;
import com.upc.wms.dto.WarehouseWorkbenchVO;
import com.upc.wms.dto.WorkerOperatorVO;
import com.upc.wms.dto.WorkerScanResultVO;
import com.upc.wms.entity.WhLocation;
import com.upc.wms.entity.WhWarehouse;
import com.upc.wms.entity.WhZone;
import com.upc.wms.service.PrepAreaService;
import com.upc.wms.service.WarehouseService;
import com.upc.wms.service.WarehouseWorkbenchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/warehouse")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;
    private final WarehouseWorkbenchService warehouseWorkbenchService;
    private final PrepAreaService prepAreaService;

    @GetMapping("/workbench/overview")
    public Result<WarehouseWorkbenchVO> workbenchOverview() {
        return Result.success(warehouseWorkbenchService.getOverview());
    }

    /**
     * 标记仓管异常已处理。出库生成缺料异常处理后，可回出库单页重新「生成出库单」。
     */
    @PostMapping("/exception/{exceptionId}/resolve")
    public Result<WarehouseExceptionVO> resolveException(@PathVariable String exceptionId,
                                                         @RequestBody(required = false) Map<String, String> body) {
        String result = body != null ? body.get("result") : null;
        return Result.success(warehouseWorkbenchService.resolveException(exceptionId, result));
    }

    /** 仓管员开始 PDA 拣货 */
    @PostMapping("/picking/start")
    public Result<WarehousePickingTaskVO> startPicking(@RequestParam Long pickingTaskId,
                                                       @RequestParam(required = false) Long operatorId) {
        return Result.success(warehouseWorkbenchService.startWarehousePicking(pickingTaskId, operatorId));
    }

    /** 仓管员 PDA 扫码拣货 */
    @PostMapping("/picking/scan")
    public Result<WorkerScanResultVO> submitPickingScan(@RequestBody WarehousePickingScanRequest request) {
        return Result.success(warehouseWorkbenchService.submitWarehouseScan(request));
    }

    /** 仓管员完成拣货，物料入备料区 */
    @PostMapping("/picking/complete")
    public Result<WarehousePickingTaskVO> completePicking(@RequestBody WarehousePickingCompleteRequest request) {
        return Result.success(warehouseWorkbenchService.completeWarehousePicking(request));
    }

    /** 可通知的生产工人列表（WORKER 角色） */
    @GetMapping("/workers")
    public Result<List<WorkerOperatorVO>> listWorkers() {
        return Result.success(warehouseWorkbenchService.listProductionWorkers());
    }

    /** 备料区库位列表（含占用概况） */
    @GetMapping("/prep/locations")
    public Result<List<PrepLocationOptionVO>> listPrepLocations(@RequestParam(required = false) Long warehouseId) {
        return Result.success(prepAreaService.listPrepLocations(warehouseId != null ? warehouseId : 1L));
    }

    /** 为拣货任务推荐备料库位 */
    @GetMapping("/prep/recommend")
    public Result<PrepLocationRecommendVO> recommendPrepLocation(@RequestParam Long pickingTaskId) {
        return Result.success(prepAreaService.recommendForPickingTask(pickingTaskId));
    }

    @GetMapping("/list")
    public Result<List<WhWarehouse>> list() {
        return Result.success(warehouseService.listWarehouses());
    }

    @PostMapping("/add")
    public Result<WhWarehouse> add(@RequestBody WhWarehouse warehouse) {
        return Result.success(warehouseService.addWarehouse(warehouse));
    }

    @GetMapping("/zone/list")
    public Result<List<WhZone>> listZones(@RequestParam(required = false) Long warehouseId) {
        return Result.success(warehouseService.listZones(warehouseId));
    }

    @PostMapping("/zone/add")
    public Result<WhZone> addZone(@RequestBody WhZone zone) {
        return Result.success(warehouseService.addZone(zone));
    }

    @GetMapping("/location/list")
    public Result<List<WhLocation>> listLocations(@RequestParam(required = false) Long zoneId) {
        return Result.success(warehouseService.listLocations(zoneId));
    }

    @GetMapping("/location/{id}")
    public Result<WhLocation> locationDetail(@PathVariable("id") Long id) {
        return Result.success(warehouseService.getLocationDetail(id));
    }

    @GetMapping("/location/available")
    public Result<List<WhLocation>> availableLocations(@RequestParam Long warehouseId) {
        return Result.success(warehouseService.listAvailableLocations(warehouseId));
    }

    @PostMapping("/location/add")
    public Result<WhLocation> addLocation(@RequestBody WhLocation location) {
        return Result.success(warehouseService.addLocation(location));
    }

    @PutMapping("/location/status")
    public Result<Void> updateLocationStatus(@RequestParam Long locationId, @RequestParam String status) {
        warehouseService.updateLocationStatus(locationId, status);
        return Result.success();
    }
}
