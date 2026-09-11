package com.upc.wms.dto;

import lombok.Data;

@Data
public class WorkerScanRecordVO {
    private String id;
    private String time;
    private String barcode;
    private String material;
    private String batch;
    private String result;
    private Boolean success;
    private String workOrder;
    private Long pickingTaskId;
}
