package com.upc.wms.dto;

import lombok.Data;

@Data
public class WarehousePickingLineVO {
    private String id;
    private Long pickingLineId;
    private String name;
    private int required;
    private int picked;
    private String batch;
    private String location;
    private String scanStatus;
}
