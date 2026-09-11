package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("in_inbound_line")
public class InInboundLine {
    @TableId(type = IdType.AUTO)
    private Long inboundLineId;
    private Long inboundId;
    private Long inspectionLineId;
    private Long itemId;
    private Long batchId;
    private Long locationId;
    private BigDecimal inboundQty;
}
