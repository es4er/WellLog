package com.upc.wms.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class WarehousePickingTaskVO {
    private String id;
    private Long pickingTaskId;
    private String outbound;
    private Long outboundId;
    private String workOrder;
    private int itemCount;
    private String zone;
    private String status;
    /** 执行拣货的生产工人 userId */
    private Long assignedTo;
    private String assigneeName;
    private Map<String, Integer> progress;
    private List<WarehousePickingLineVO> lines;
}
