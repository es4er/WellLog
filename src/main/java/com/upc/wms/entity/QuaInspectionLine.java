package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("qua_inspection_line")
public class QuaInspectionLine {
    @TableId(type = IdType.AUTO)
    private Long inspectionLineId;
    private Long inspectionId;
    private Long receiptLineId;
    private Long itemId;
    private Long batchId;
    private BigDecimal inspectedQty;
    private BigDecimal qualifiedQty;
    private BigDecimal unqualifiedQty;
    private String lineResult;
}
