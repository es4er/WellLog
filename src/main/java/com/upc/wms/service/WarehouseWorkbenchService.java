package com.upc.wms.service;

import com.upc.wms.dto.WarehouseExceptionVO;
import com.upc.wms.dto.WarehousePickingCompleteRequest;
import com.upc.wms.dto.WarehousePickingScanRequest;
import com.upc.wms.dto.WarehousePickingTaskVO;
import com.upc.wms.dto.WarehouseWorkbenchVO;
import com.upc.wms.dto.WorkerOperatorVO;
import com.upc.wms.dto.WorkerScanResultVO;

import java.util.List;

public interface WarehouseWorkbenchService {
    WarehouseWorkbenchVO getOverview();

    /**
     * 人工标记仓管异常已处理（目前支持出库生成异常 OEX*）。
     */
    WarehouseExceptionVO resolveException(String exceptionId, String result);

    /** 仓管员开始 PDA 拣货 */
    WarehousePickingTaskVO startWarehousePicking(Long pickingTaskId, Long operatorId);

    /** 仓管员 PDA 扫码确认单行拣货 */
    WorkerScanResultVO submitWarehouseScan(WarehousePickingScanRequest request);

    /** 仓管员完成拣货，物料入备料区并通知生产工人 */
    WarehousePickingTaskVO completeWarehousePicking(WarehousePickingCompleteRequest request);

    List<WorkerOperatorVO> listProductionWorkers();
}
