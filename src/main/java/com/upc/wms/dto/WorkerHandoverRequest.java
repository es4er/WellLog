package com.upc.wms.dto;

import lombok.Data;

@Data
public class WorkerHandoverRequest {
    private Long pickingTaskId;
    private Long workerId;
    private String remark;
}
