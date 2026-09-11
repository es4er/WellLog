package com.upc.wms.dto;

import lombok.Data;

@Data
public class WorkerExceptionSubmitRequest {
    private Long pickingTaskId;
    private Long pickingLineId;
    private Long workerId;
    private String type;
    private String note;
    private String location;
}
