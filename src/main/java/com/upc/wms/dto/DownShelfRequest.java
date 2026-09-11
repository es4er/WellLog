package com.upc.wms.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DownShelfRequest {
    private Long inventoryId;
    private BigDecimal quantity;
    private String reason;
    private Long operatedBy;
}
