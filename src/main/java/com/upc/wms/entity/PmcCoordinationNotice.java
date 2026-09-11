package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * PMC 出库协同通知：PMC 计划员在「出库协同」看板发起的催出库 / 补料调拨建议 / 复核跟进，
 * 落库后由目标岗位（默认仓管 WAREHOUSE）在其工作台接收、查看并标记处理，形成真实闭环。
 */
@Data
@TableName("pmc_coordination_notice")
public class PmcCoordinationNotice {

    @TableId(type = IdType.AUTO)
    private Long noticeId;

    /** URGE_OUTBOUND=催出库 / REPLENISH_ADVICE=补料调拨建议 / REVIEW_FOLLOW=复核跟进 */
    private String noticeType;

    private String title;

    private String content;

    private Long planId;

    private String planNo;

    private Long requisitionId;

    private String outboundNo;

    private String exceptionTitle;

    /** 目标岗位角色，默认 WAREHOUSE */
    private String targetRole;

    /** OPEN=待处理 / READ=已读 / DONE=已处理 */
    private String status;

    private Long createdBy;

    private String createdByName;

    private LocalDateTime createdAt;

    private LocalDateTime readAt;

    private LocalDateTime handledAt;

    private String handledBy;
}
