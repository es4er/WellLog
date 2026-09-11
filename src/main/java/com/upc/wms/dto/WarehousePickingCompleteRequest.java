package com.upc.wms.dto;

import lombok.Data;

@Data
public class WarehousePickingCompleteRequest {
    private Long pickingTaskId;
    private Long operatorId;
    /** 备料区通知的生产工人 ID */
    private Long targetWorkerId;
    /** 备料区目标库位 ID（可选，不传则按推荐规则自动选择） */
    private Long prepLocationId;
}
