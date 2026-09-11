package com.upc.wms.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StocktakeLineVO {
    private Long stocktakeLineId;
    private Long stocktakeId;
    private Long inventoryId;

    private Long itemId;
    private String itemCode;
    private String itemName;

    private Long batchId;
    private String batchNo;

    private Long locationId;
    private String locationCode;

    private BigDecimal bookQty;
    private BigDecimal countedQty;
    private BigDecimal differenceQty;

    private String lineStatus;
    private String remark;
}
