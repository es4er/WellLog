package com.upc.wms.dto;

import lombok.Data;

import java.util.List;

/**
 * 仓管员将收货单明细提交质检的请求。
 */
@Data
public class ReceiptSubmitInspectionRequest {
    private Long receiptId;
    /** 待提交的收货明细行 ID；为空则提交该单全部待检行 */
    private List<Long> receiptLineIds;
    private Long submittedBy;
}
