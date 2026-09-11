package com.upc.wms.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FreezeRequest {
    private Long inventoryId;
    private BigDecimal qty;
    private String reason;
    private Long operatedBy;
}
