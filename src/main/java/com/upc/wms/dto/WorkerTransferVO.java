package com.upc.wms.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WorkerTransferVO {
    private Long id;
    private String transferCard;
    private String containerCode;
    private String fromProcess;
    private String toProcess;
    private String workOrderNo;
    private BigDecimal qty;
    private String status;
    private String submittedAt;
}
