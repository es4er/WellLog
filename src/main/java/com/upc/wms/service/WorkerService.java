package com.upc.wms.service;

import com.upc.wms.dto.*;
import com.upc.wms.entity.OutPickingTask;

import java.util.List;
import java.util.Map;

public interface WorkerService {

    WorkerWorkbenchVO loadWorkbench(Long workerId);

    WorkerScanResultVO submitScan(WorkerScanRequest request);

    WorkerExceptionVO submitException(WorkerExceptionSubmitRequest request);

    WorkerTaskVO confirmHandover(WorkerHandoverRequest request);

    BarcodeScanDetailVO resolveBarcode(String barcodeValue);

    WorkerScanResultVO processScanFromAgent(Map<String, Object> data);

    WorkerExceptionVO processExceptionFromAgent(Map<String, Object> data);

    List<WorkerReplenishVO> listReplenish(Long workerId);

    WorkerReplenishVO submitReplenish(WorkerReplenishSubmitRequest request);

    /** 仓管员开始 PDA 拣货 */
    void startWarehousePicking(Long pickingTaskId, Long operatorId);

    /** 仓管员 PDA 扫码拣货（按库位找料、扫条码、核批次数量） */
    WorkerScanResultVO submitWarehouseScan(WarehousePickingScanRequest request);

    /** 仓管员完成拣货，物料入备料区并指定生产工人领取 */
    OutPickingTask completeWarehousePicking(Long pickingTaskId, Long operatorId, Long targetWorkerId, Long prepLocationId);

    List<WorkerOperatorVO> listProductionWorkers();

    List<WorkerNotificationVO> listNotifications(Long workerId);

    void markNotificationRead(Long notificationId, Long workerId);

    WorkerCompletionVO submitCompletion(WorkerCompletionSubmitRequest request);

    List<WorkerCompletionVO> listCompletions(Long workerId);

    WorkerTransferVO submitTransfer(WorkerTransferSubmitRequest request);

    List<WorkerTransferVO> listTransfers(Long workerId);
}
