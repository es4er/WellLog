package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("wh_transfer_line")
public class WhTransferLine {
    @TableId(type = IdType.AUTO)
    private Long transferLineId;
    private Long transferId;
    private Long inventoryId;
    private Long fromLocationId;
    private Long toLocationId;
    private Long itemId;
    private Long batchId;
    private BigDecimal transferQty;
}
