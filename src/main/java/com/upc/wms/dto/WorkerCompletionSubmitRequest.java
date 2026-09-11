package com.upc.wms.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WorkerCompletionSubmitRequest {
    private Long workerId;
    private String workOrderNo;
    private String barcodeValue;
    private BigDecimal qty;
    private String remark;
}
