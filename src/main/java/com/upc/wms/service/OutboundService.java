package com.upc.wms.service;

import com.upc.wms.dto.PickingSubmitRequest;
import com.upc.wms.dto.ReviewSubmitRequest;
import com.upc.wms.entity.OutOrder;
import com.upc.wms.entity.OutPickingLine;
import com.upc.wms.entity.OutPickingTask;
import com.upc.wms.entity.OutReviewTask;

import java.util.List;
import java.util.Map;

/**
 * 出库拣货与复核服务接口。
 */
public interface OutboundService {

    OutOrder createOutboundOrder(Long requisitionId, Long warehouseId);

    List<OutOrder> listPendingOutbound();

    Map<String, Object> getOutboundDetail(Long outboundId);

    OutPickingTask createPickingTask(Long outboundId, Long assignedTo);

    /** 仓管员将拣货任务分配给生产工人 */
    OutPickingTask assignPickingTask(Long pickingTaskId, Long assignedTo);

    OutPickingLine addPickingLine(OutPickingLine line);

    void submitPicking(PickingSubmitRequest request);

    OutReviewTask createReviewTask(Long outboundId, Long reviewedBy);

    OutReviewTask submitReview(ReviewSubmitRequest request);

    OutOrder confirmOutbound(Long outboundId, Long operatedBy);

    /**
     * 仓管 PDA 完成拣货入备料区：从原库位移库至备料区，并回写出库明细已拣数量。
     */
    void transferToPrepAreaForPickingTask(Long pickingTaskId, Long prepLocationId, Long operatedBy);

    /**
     * 生产工人确认领取：从备料区扣减库存，并回写出库明细已发数量。
     */
    void deductPrepAreaForHandover(Long pickingTaskId, Long operatedBy);
}
