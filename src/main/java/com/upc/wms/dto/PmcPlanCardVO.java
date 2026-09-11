package com.upc.wms.dto;

import lombok.Data;

@Data
public class PmcPlanCardVO {
    private Long planId;
    private Long orderId;
    private Long requisitionId;
    /** 是否已有生产计划 */
    private Boolean hasPlan;
    /** 是否已完成排产（已有计划且已生成领料单） */
    private Boolean planCompleted;
    private String id;
    private String order;
    private String source;
    private String product;
    private String qty;
    private String start;
    private String finish;
    private Integer ready;
    private Integer shortage;
    private String req;
    private String status;
    /** 最近齐套校验时间（Agent 回写） */
    private String kittingCheckedAt;
}
