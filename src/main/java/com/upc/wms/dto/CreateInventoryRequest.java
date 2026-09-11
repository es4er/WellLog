package com.upc.wms.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateInventoryRequest {
    private Long warehouseId;
    private Long locationId;
    private Long itemId;
    private Long batchId;
    private String itemCode;
    private String batchNo;
    private BigDecimal onhandQty;
    private BigDecimal availableQty;
    private String inventoryStatus;
    private Long operatedBy;
}
