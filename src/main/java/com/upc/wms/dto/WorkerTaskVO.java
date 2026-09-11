package com.upc.wms.dto;

import lombok.Data;

@Data
public class WorkerTaskVO {
    private String id;
    private Long pickingTaskId;
    private String workOrder;
    private String requisition;
    private String product;
    private String priority;
    private Integer planQty;
    private String unit;
    private String planDate;
    private String expectedTime;
    private String handler;
    private String handoverTime;
    private String status;
    private Boolean cancelled;
    private java.util.List<WorkerTaskItemVO> items;
}
