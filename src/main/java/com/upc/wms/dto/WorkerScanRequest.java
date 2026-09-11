package com.upc.wms.dto;

import lombok.Data;

@Data
public class WorkerScanRequest {
    private Long pickingTaskId;
    private Long pickingLineId;
    private Long workerId;
    private String barcodeValue;
}
