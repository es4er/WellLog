package com.upc.wms.service;

import com.upc.wms.dto.ReceiptCreateRequest;
import com.upc.wms.entity.RecReceiptOrder;

import java.util.List;
import java.util.Map;

/**
 * 收货登记服务接口。
 */
public interface ReceiptService {

    RecReceiptOrder createReceipt(ReceiptCreateRequest request);

    Map<String, Object> getReceiptDetail(Long receiptId);

    List<RecReceiptOrder> listReceipts();

    List<RecReceiptOrder> listPendingInspectionReceipts();

    void updateReceiptStatus(Long receiptId, String status);

    /** 根据收货明细行状态回写收货单头状态，修复头行不一致 */
    void syncReceiptStatusFromLines(Long receiptId);
}
