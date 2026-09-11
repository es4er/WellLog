package com.upc.wms.dto;

import lombok.Data;

@Data
public class WarehousePickingScanRequest {
    private Long pickingTaskId;
    private Long pickingLineId;
    /** 仓管员操作人 ID */
    private Long operatorId;
    private String barcodeValue;
    /** 本次确认数量，默认 1 */
    private Integer qty;
}
