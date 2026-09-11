package com.upc.wms.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BomComponent {
    private Long bomId;
    private Long productItemId;
    private Long componentItemId;
    private BigDecimal qtyPer;
    private Integer lineNo;
    private String bomVersion;
}
