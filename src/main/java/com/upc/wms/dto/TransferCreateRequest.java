package com.upc.wms.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 移库请求：源库存行移动到目标库位。
 */
@Data
public class TransferCreateRequest {
    private Long warehouseId;
    private String transferReason;
    private Long operatedBy;
    private List<Line> lines;

    @Data
    public static class Line {
        private Long inventoryId;
        private Long toLocationId;
        private BigDecimal transferQty;
    }
}
