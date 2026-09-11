package com.upc.wms.dto;

import lombok.Data;

@Data
public class QualityTaskVO {
    private Long receiptId;
    private Long inspectionId;
    /** 质检编号展示 */
    private String id;
    private String receiptNo;
    private String product;
    private String batchNo;
    private String qty;
    /** 待检测 / 检测中 / 完成（加严用 strict 标记，不单独占状态） */
    private String status;
    private boolean strict;
    private boolean hasInspection;
    private Long supplierId;
    private String supplierName;
    private Long warehouseId;
}
