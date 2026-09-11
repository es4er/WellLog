package com.upc.wms.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WorkerTransferSubmitRequest {
    private Long workerId;
    private String transferCard;
    private String containerCode;
    private String fromProcess;
    private String toProcess;
    private String workOrderNo;
    private BigDecimal qty;
    private String remark;
}
