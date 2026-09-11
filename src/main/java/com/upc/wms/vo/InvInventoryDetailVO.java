package com.upc.wms.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InvInventoryDetailVO {
    private Long inventoryId;
    private Long warehouseId;
    private Long locationId;
    private Long itemId;
    private Long batchId;

    private String itemCode;
    private String itemName;
    private String batchNo;
    private String locationCode;

    private BigDecimal onhandQty;
    private BigDecimal availableQty;
    private BigDecimal reservedQty;
    private BigDecimal frozenQty;
    private String inventoryStatus;
    private LocalDateTime lastTxnAt;
}
