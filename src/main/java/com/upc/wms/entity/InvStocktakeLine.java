package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("inv_stocktake_line")
public class InvStocktakeLine {
    @TableId(type = IdType.AUTO)
    private Long stocktakeLineId;
    private Long stocktakeId;
    private Long inventoryId;
    private Long itemId;
    private Long batchId;
    private Long locationId;
    private BigDecimal bookQty;
    private BigDecimal countedQty;
    private BigDecimal differenceQty;
    private String lineStatus;
    @TableField(exist = false)
    private String remark;

    @TableField(exist = false)
    private String itemCode;
    @TableField(exist = false)
    private String itemName;
    @TableField(exist = false)
    private String batchNo;
    @TableField(exist = false)
    private String locationCode;
}
