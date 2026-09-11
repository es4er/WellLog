package com.upc.wms.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 出库复核提交请求。
 */
@Data
public class ReviewSubmitRequest {
    private Long reviewTaskId;
    private Long reviewedBy;
    private List<Line> lines;

    @Data
    public static class Line {
        private Long pickingLineId;
        private BigDecimal reviewQty;
        /** PASS / EXCEPTION */
        private String reviewResult;
        private String exceptionType;
        private String exceptionDesc;
    }
}
