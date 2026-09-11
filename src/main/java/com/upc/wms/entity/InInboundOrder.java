package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("in_inbound_order")
public class InInboundOrder {
    @TableId(type = IdType.AUTO)
    private Long inboundId;
    private String inboundNo;
    private Long receiptId;
    private String inboundType;
    private Long warehouseId;
    private Long inboundBy;
    private LocalDateTime inboundAt;
    private String inboundStatus;
}
