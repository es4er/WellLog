package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("inv_stocktake_order")
public class InvStocktakeOrder {
    @TableId(type = IdType.AUTO)
    private Long stocktakeId;
    private String stocktakeNo;
    private Long warehouseId;
    private String stocktakeScope;
    private String stocktakeType;
    private String stocktakeStatus;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long executor;
    private LocalDateTime executorAt;
    private Long reviewer;
    private LocalDateTime reviewedAt;
    private String notes;

    @TableField(exist = false)
    private List<InvStocktakeLine> stocktakeLines;

    @TableField(exist = false)
    private Integer differenceCount;

    @TableField(exist = false)
    private Long adjustmentId;

    @TableField(exist = false)
    private String adjustmentNo;

    @TableField(exist = false)
    private String adjustmentStatus;
}
