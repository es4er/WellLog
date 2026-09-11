package com.upc.wms.dto;

import lombok.Data;

@Data
public class WorkerReplenishSubmitRequest {
    private Long workerId;
    private Long pickingTaskId;
    private Long pickingLineId;
    private Integer qty;
    private String reason;
    private String note;
}
