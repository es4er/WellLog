package com.upc.wms.dto;

import lombok.Data;

/**
 * PMC 缺料分析行：订单 + 计划 + 物料 + 分型 + 需/缺数量。
 */
@Data
public class PmcShortageRowVO {
    private Long planId;
    private Long orderId;
    private Long itemId;
    private String orderNo;
    private String planNo;
    private String itemName;
    /** REAL_SHORTAGE / QUALITY_PENDING / QUALITY_ABNORMAL / LOCATION_UNAVAILABLE */
    private String shortageType;
    private String shortageTypeLabel;
    private String requiredQty;
    private String shortageQty;
    private String availableQty;
    private String gapText;
    private String advice;
    /** SNAPSHOT=Agent回写；LIVE=看板实时重算 */
    private String source;
}
