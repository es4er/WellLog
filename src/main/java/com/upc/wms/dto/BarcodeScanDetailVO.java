package com.upc.wms.dto;

import lombok.Data;

@Data
public class BarcodeScanDetailVO {
    private String barcodeValue;
    private Long itemId;
    private String materialCode;
    private String materialName;
    private Long batchId;
    private String batchNo;
    private String qualityStatus;
    private Long inventoryId;
}
