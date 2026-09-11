package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("out_order_line")
public class OutOrderLine {
    @TableId(type = IdType.AUTO)
    private Long outboundLineId;
    private Long outboundId;
    private Long requisitionLineId;
    private Long itemId;
    private BigDecimal planQty;
    private BigDecimal pickedQty;
    private BigDecimal shippedQty;
    private String lineStatus;
}
