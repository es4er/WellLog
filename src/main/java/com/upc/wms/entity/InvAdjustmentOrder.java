package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("inv_adjustment_order")
public class InvAdjustmentOrder {
    @TableId(type = IdType.AUTO)
    private Long adjustmentId;
    private String adjustmentNo;
    private Long stocktakeId;
    private String adjustmentReason;
    private String adjustmentStatus;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long approvedBy;
    private LocalDateTime approvedAt;
}
