package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("inv_transaction")
public class InvTransaction {
    @TableId(type = IdType.AUTO)
    private Long transactionId;
    private String transactionNo;
    private Long inventoryId;
    private Long warehouseId;
    private Long locationId;
    private Long itemId;
    private Long batchId;
    private String businessType;
    private String sourceDocType;
    private Long sourceDocId;
    private BigDecimal beforeQty;
    private BigDecimal changeQty;
    private BigDecimal afterQty;
    private Long operatedBy;
    private LocalDateTime operatedAt;
}
