package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("inv_stocktake_difference")
public class InvStocktakeDifference {
    @TableId(type = IdType.AUTO)
    private Long differenceId;
    private Long stocktakeLineId;
    private String differenceType;
    private BigDecimal differenceQty;
    private String reasonCode;
    private String confirmStatus;
}
