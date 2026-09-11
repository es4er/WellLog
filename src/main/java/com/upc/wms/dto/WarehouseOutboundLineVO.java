package com.upc.wms.dto;

import lombok.Data;

@Data
public class WarehouseOutboundLineVO {
    private String name;
    private int required;
    private int actual;
    private String batch;
    private String location;
    private String status;
}
