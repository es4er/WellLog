package com.upc.wms.dto;

import lombok.Data;

@Data
public class WorkerTaskItemVO {
    private String id;
    private Long pickingLineId;
    private String material;
    private String materialCode;
    private String spec;
    private int required;
    private int scanned;
    private String batch;
    private String location;
    private Integer available;
    private Long batchId;
    private Long itemId;
    private String status;
    private Integer shortage;
}
