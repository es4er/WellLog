package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("ord_customer_order")
public class OrdCustomerOrder {
    @TableId(type = IdType.AUTO)
    private Long orderId;
    private String orderNo;
    private Long customerId;
    private Long sourceSystemId;
    private String erpOrderNo;
    private LocalDate orderDate;
    private LocalDate deliveryDate;
    private String orderStatus;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
}
