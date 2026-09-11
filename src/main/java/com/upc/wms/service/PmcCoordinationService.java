package com.upc.wms.service;

import com.upc.wms.dto.PmcCoordinationNoticeRequest;
import com.upc.wms.dto.PmcCoordinationNoticeVO;

import java.util.List;

/**
 * PMC 出库协同通知：催出库 / 补料调拨建议 / 复核跟进的落库与流转。
 */
public interface PmcCoordinationService {

    /** PMC 发起一条协同通知 */
    PmcCoordinationNoticeVO createNotice(PmcCoordinationNoticeRequest request);

    /** 按目标岗位与状态查询通知（仓管收件箱用） */
    List<PmcCoordinationNoticeVO> listByTarget(String targetRole, String status);

    /** 查询某人发起的通知（PMC 已发送列表用） */
    List<PmcCoordinationNoticeVO> listCreatedBy(Long createdBy);

    /** 标记已读 */
    PmcCoordinationNoticeVO markRead(Long noticeId);

    /** 标记已处理 */
    PmcCoordinationNoticeVO markHandled(Long noticeId, String handledBy);
}
