package com.upc.wms.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WorkerCompletionVO {
    private Long id;
    private String workOrderNo;
    private String materialName;
    private String materialCode;
    private String batchNo;
    private BigDecimal qty;
    private String locationCode;
    private String status;
    private String submittedAt;
}
