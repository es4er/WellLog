package com.upc.wms.service;

import com.upc.wms.dto.TransferCreateRequest;
import com.upc.wms.entity.WhTransferOrder;

import java.util.Map;

/**
 * 库位移库服务接口。
 */
public interface TransferService {

    WhTransferOrder createTransfer(TransferCreateRequest request);

    Map<String, Object> getTransferDetail(Long transferId);

    WhTransferOrder confirmTransfer(Long transferId, Long operatedBy);
}
