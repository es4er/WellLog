package com.upc.wms.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InvFreezeRecordVO {
    private Long freezeId;
    private String freezeNo;
    private Long inventoryId;
    private String freezeType;
    private BigDecimal freezeQty;
    private String reason;
    private Long operatedBy;
    private LocalDateTime operatedAt;

    private String itemCode;
    private String itemName;
    private String batchNo;
    private String locationCode;
}
