package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rec_receipt_order")
public class RecReceiptOrder {
    @TableId(type = IdType.AUTO)
    private Long receiptId;
    private String receiptNo;
    private Long supplierId;
    private Long warehouseId;
    private Long sourceSystemId;
    private String erpPoNo;
    private LocalDateTime arrivedAt;
    private Long receivedBy;
    private String receiptStatus;
    private String remark;
}
