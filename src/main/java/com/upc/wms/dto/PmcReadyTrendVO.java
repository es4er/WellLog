package com.upc.wms.dto;

import lombok.Data;

@Data
public class PmcReadyTrendVO {
    private String day;
    private Integer ready;
    private Integer shortCount;
    private Integer delay;
}
