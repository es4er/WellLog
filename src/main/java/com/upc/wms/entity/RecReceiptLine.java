package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("rec_receipt_line")
public class RecReceiptLine {
    @TableId(type = IdType.AUTO)
    private Long receiptLineId;
    private Long receiptId;
    private Long itemId;
    private Long batchId;
    private BigDecimal receivedQty;
    private BigDecimal inspectedQty;
    private String lineStatus;
}
