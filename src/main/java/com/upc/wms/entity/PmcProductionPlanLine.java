package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("pmc_production_plan_line")
public class PmcProductionPlanLine {
    @TableId(type = IdType.AUTO)
    private Long planLineId;
    private Long planId;
    private Long itemId;
    private BigDecimal requiredQty;
    private LocalDate dueDate;
    private String lineStatus;
}
