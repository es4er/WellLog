package com.upc.wms.dto;

import lombok.Data;

import java.util.List;

@Data
public class WarehouseWorkbenchVO {
    private List<WarehousePendingRequisitionVO> pendingRequisitions;
    private List<WarehouseOutboundOrderVO> outboundOrders;
    private List<WarehousePickingTaskVO> pickingTasks;
    private List<WarehouseExceptionVO> exceptions;
    /** PMC 出库协同通知（催出库 / 补料调拨 / 复核跟进） */
    private List<PmcCoordinationNoticeVO> coordinationNotices;
}
