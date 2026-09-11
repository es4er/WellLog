package com.upc.wms.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LocationMapOperateRequest {
    private Long warehouseId;
    private Long locationId;
    private String itemCode;
    private String batchNo;
    private BigDecimal qty;
}
