package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("pmc_production_plan")
public class PmcProductionPlan {
    @TableId(type = IdType.AUTO)
    private Long planId;
    private String planNo;
    private Long sourceSystemId;
    /** 来源客户订单，生成计划后用于列表回显与防重复排产 */
    private Long sourceOrderId;
    private String mesPlanNo;
    private String planStatus;
    private LocalDate plannedStartDate;
    private LocalDate plannedEndDate;
    private Long createdBy;
    private LocalDateTime createdAt;
    /** 最近一次齐套率 %（InventoryAgent 回写） */
    private Integer kittingRate;
    /** 最近齐套校验时间 */
    private LocalDateTime kittingCheckedAt;
    /** 缺料分型快照 JSON */
    private String shortageAnalysisJson;
    /** 全量齐套行快照 JSON */
    private String kittingLinesJson;
}
