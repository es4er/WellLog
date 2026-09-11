package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("inv_adjustment_line")
public class InvAdjustmentLine {
    @TableId(type = IdType.AUTO)
    private Long adjustmentLineId;
    private Long adjustmentId;
    private Long inventoryId;
    private BigDecimal beforeQty;
    private BigDecimal afterQty;
    private BigDecimal adjustmentQty;
}
