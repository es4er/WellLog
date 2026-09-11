package com.upc.wms.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InvAdjustmentLineVO {
    private Long adjustmentLineId;
    private Long adjustmentId;
    private Long inventoryId;

    private Long itemId;
    private String itemCode;
    private String itemName;

    private Long batchId;
    private String batchNo;

    private Long locationId;
    private String locationCode;

    private BigDecimal beforeQty;
    private BigDecimal afterQty;
    private BigDecimal adjustmentQty;
}
