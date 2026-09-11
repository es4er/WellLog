package com.upc.wms.dto;

import lombok.Data;

@Data
public class WorkerExceptionVO {
    private String id;
    private Long exceptionId;
    private Long pickingTaskId;
    private String pickingLineId;
    private String workOrder;
    private String requisition;
    private String material;
    private int required;
    private int actual;
    private int shortage;
    private String type;
    private String note;
    private String status;
    private String submittedAt;
    private String handler;
    private String taskId;
    private String itemId;
}
