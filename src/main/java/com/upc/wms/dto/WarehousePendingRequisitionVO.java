package com.upc.wms.dto;

import lombok.Data;

import java.util.List;

@Data
public class WarehousePendingRequisitionVO {
    private String id;
    private Long requisitionId;
    private String plan;
    private Long planId;
    private String workOrder;
    private int itemCount;
    private String demandDate;
    private String status;
    private String warehouse;
    private List<WarehouseMaterialVO> materials;
}
