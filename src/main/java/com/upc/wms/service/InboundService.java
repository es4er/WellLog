package com.upc.wms.service;

import com.upc.wms.dto.InboundConfirmRequest;
import com.upc.wms.entity.InInboundOrder;

import java.util.List;
import java.util.Map;

/**
 * 入库上架服务接口。
 */
public interface InboundService {

    InInboundOrder createInboundOrder(Long receiptId, Long warehouseId, Long operatedBy);

    List<InInboundOrder> listPendingInbound();

    Map<String, Object> getInboundDetail(Long inboundId);

    InInboundOrder confirmInbound(InboundConfirmRequest request);
}
