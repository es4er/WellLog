package com.upc.wms.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PrepLocationOptionVO {
    private Long locationId;
    private String locationCode;
    private String locationName;
    private BigDecimal onhandQty;
    /** 是否空闲（账存为 0） */
    private boolean empty;
}
