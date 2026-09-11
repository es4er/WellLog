package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("inv_safety_stock_rule")
public class InvSafetyStockRule {
    @TableId(type = IdType.AUTO)
    private Long ruleId;
    private Long warehouseId;
    private Long itemId;
    private BigDecimal minQty;
    private BigDecimal maxQty;
    private BigDecimal reorderQty;
    private Integer enabledFlag;
}
