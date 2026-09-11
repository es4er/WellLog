package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("inv_alert_record")
public class InvAlertRecord {
    @TableId(type = IdType.AUTO)
    private Long alertId;
    private Long ruleId;
    private Long warehouseId;
    private Long itemId;
    private String alertType;
    private BigDecimal alertQty;
    private String alertStatus;
    private LocalDateTime createdAt;
}
