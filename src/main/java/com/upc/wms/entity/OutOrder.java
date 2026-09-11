package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("out_order")
public class OutOrder {
    @TableId(type = IdType.AUTO)
    private Long outboundId;
    private String outboundNo;
    private Long requisitionId;
    private String outboundType;
    private Long warehouseId;
    private String outboundStatus;
    private Long approvedBy;
    private LocalDateTime approvedAt;
}
