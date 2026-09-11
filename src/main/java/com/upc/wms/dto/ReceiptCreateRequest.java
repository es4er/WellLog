package com.upc.wms.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 收货登记请求：包含收货单头与明细，明细携带批次信息用于自动建批次。
 */
@Data
public class ReceiptCreateRequest {
    private Long supplierId;
    private Long warehouseId;
    private Long sourceSystemId;
    private String erpPoNo;
    private LocalDateTime arrivedAt;
    private Long receivedBy;
    private String remark;
    private List<Line> lines;

    @Data
    public static class Line {
        private Long itemId;
        private String batchNo;
        private LocalDate manufactureDate;
        private LocalDate expireDate;
        private BigDecimal receivedQty;
    }
}
