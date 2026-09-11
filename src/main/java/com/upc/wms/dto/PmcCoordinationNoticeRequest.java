package com.upc.wms.dto;

import lombok.Data;

/**
 * PMC 出库协同通知创建请求。
 */
@Data
public class PmcCoordinationNoticeRequest {

    /** URGE_OUTBOUND / REPLENISH_ADVICE / REVIEW_FOLLOW */
    private String noticeType;

    private String title;

    private String content;

    private Long planId;

    private String planNo;

    private Long requisitionId;

    private String outboundNo;

    private String exceptionTitle;

    /** 目标岗位角色，缺省 WAREHOUSE */
    private String targetRole;

    private Long createdBy;
}
