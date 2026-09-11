package com.upc.wms.dto;

import lombok.Data;

@Data
public class WarehouseMaterialVO {
    private String name;
    private String code;
    private int qty;
    private String unit;
}
