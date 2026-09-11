package com.upc.wms.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ReceiptSubmitInspectionResultVO {
    private Long receiptId;
    private String receiptNo;
    private Long inspectionId;
    private String inspectionNo;
    private int submittedLineCount;
    private String message;
    private List<SubmittedLineSummary> lines = new ArrayList<>();

    @Data
    public static class SubmittedLineSummary {
        private Long receiptLineId;
        private Long itemId;
        private String itemName;
        private String itemCode;
        private String batchNo;
        private String receivedQty;
    }
}
