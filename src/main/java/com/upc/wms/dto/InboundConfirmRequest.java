package com.upc.wms.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 入库上架请求：为入库单逐行指定库位与上架数量。
 */
@Data
public class InboundConfirmRequest {
    private Long inboundId;
    private Long warehouseId;
    private Long operatedBy;
    private List<Line> lines;

    @Data
    public static class Line {
        private Long inspectionLineId;
        private Long itemId;
        private Long batchId;
        private Long locationId;
        private BigDecimal inboundQty;
    }
}
