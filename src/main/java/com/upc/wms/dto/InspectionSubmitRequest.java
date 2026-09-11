package com.upc.wms.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 质检提交请求：针对某收货单提交质检结论与逐行合格/不合格数量。
 */
@Data
public class InspectionSubmitRequest {
    private Long receiptId;
    private Long inspectedBy;
    private List<Line> lines;

    @Data
    public static class Line {
        private Long receiptLineId;
        private Long itemId;
        private Long batchId;
        private BigDecimal inspectedQty;
        private BigDecimal qualifiedQty;
        private BigDecimal unqualifiedQty;
        /** QUALIFIED / UNQUALIFIED */
        private String lineResult;
        private String issueType;
        private String issueDesc;
    }
}
