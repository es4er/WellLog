package com.upc.wms.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 盘点实盘录入请求。
 */
@Data
public class StocktakeCountRequest {
    private Long stocktakeId;
    private List<Line> lines;

    @Data
    public static class Line {
        private Long stocktakeLineId;
        private BigDecimal countedQty;
    }
}
