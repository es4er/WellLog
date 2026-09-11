package com.upc.wms.dto;

import lombok.Data;

/**
 * PMC 出库协同通知展示对象（供 PMC 已发送列表与仓管收件箱共用）。
 */
@Data
public class PmcCoordinationNoticeVO {

    private Long noticeId;
    private String noticeType;
    private String noticeTypeLabel;
    private String title;
    private String content;
    private Long planId;
    private String planNo;
    private Long requisitionId;
    private String outboundNo;
    private String exceptionTitle;
    private String targetRole;
    private String status;
    private String statusLabel;
    private Long createdBy;
    private String createdByName;
    private String createdAt;
    private String readAt;
    private String handledAt;
    private String handledBy;
}
