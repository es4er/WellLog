package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("out_picking_line")
public class OutPickingLine {
    @TableId(type = IdType.AUTO)
    private Long pickingLineId;
    private Long pickingTaskId;
    private Long outboundLineId;
    private Long inventoryId;
    private Long itemId;
    private Long batchId;
    private Long locationId;
    private BigDecimal planPickQty;
    private BigDecimal actualPickQty;
    private BigDecimal workerScannedQty;
}
