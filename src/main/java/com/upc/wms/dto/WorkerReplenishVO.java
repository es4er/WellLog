package com.upc.wms.dto;

import lombok.Data;

@Data
public class WorkerReplenishVO {
    private String id;
    private Long replenishId;
    private Long pickingTaskId;
    private Long pickingLineId;
    private String workOrder;
    private String product;
    private String material;
    private String materialCode;
    private String spec;
    private Integer qty;
    private String reason;
    private String note;
    private String status;
    private String submittedAt;
    private String handler;
}
