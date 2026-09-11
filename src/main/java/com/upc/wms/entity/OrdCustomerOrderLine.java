package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("ord_customer_order_line")
public class OrdCustomerOrderLine {
    @TableId(type = IdType.AUTO)
    private Long orderLineId;
    private Long orderId;
    private Long itemId;
    private BigDecimal orderedQty;
    private BigDecimal issuedQty;
    private LocalDate requiredDate;
    private String lineStatus;
}
