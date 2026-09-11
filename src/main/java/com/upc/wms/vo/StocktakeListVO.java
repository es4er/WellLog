package com.upc.wms.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class StocktakeListVO {
    private Long stocktakeId;
    private String stocktakeNo;
    private Long warehouseId;
    private String stocktakeScope;
    private String stocktakeType;
    private String stocktakeStatus;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Integer differenceCount;
    private Long differenceItemCount;
    private BigDecimal differenceQtyTotal;
    private Long adjustmentId;
    private String adjustmentNo;
    private String adjustmentStatus;
}
