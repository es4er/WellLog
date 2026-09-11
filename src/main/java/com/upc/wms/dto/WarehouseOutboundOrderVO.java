package com.upc.wms.dto;

import lombok.Data;

import java.util.List;

@Data
public class WarehouseOutboundOrderVO {
    private String id;
    private Long outboundId;
    private String requisition;
    private Long requisitionId;
    private String workOrder;
    private int itemCount;
    private String status;
    private String createdAt;
    private String warehouse;
    private String pickingTaskId;
    private Long pickingTaskDbId;
    private List<WarehouseOutboundLineVO> lines;
    private List<String> generationLog;
}
