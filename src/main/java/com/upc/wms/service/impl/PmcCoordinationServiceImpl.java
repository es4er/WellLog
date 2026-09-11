package com.upc.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.upc.wms.common.BusinessException;
import com.upc.wms.dto.PmcCoordinationNoticeRequest;
import com.upc.wms.dto.PmcCoordinationNoticeVO;
import com.upc.wms.entity.PmcCoordinationNotice;
import com.upc.wms.entity.SysUser;
import com.upc.wms.mapper.PmcCoordinationNoticeMapper;
import com.upc.wms.mapper.SysUserMapper;
import com.upc.wms.service.PmcCoordinationService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PmcCoordinationServiceImpl implements PmcCoordinationService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final PmcCoordinationNoticeMapper noticeMapper;
    private final SysUserMapper userMapper;

    @PostConstruct
    public void init() {
        try {
            noticeMapper.ensureTable();
        } catch (Exception e) {
            log.warn("初始化 pmc_coordination_notice 表失败：{}", e.getMessage());
        }
    }

    @Override
    public PmcCoordinationNoticeVO createNotice(PmcCoordinationNoticeRequest request) {
        if (request == null || !StringUtils.hasText(request.getNoticeType())) {
            throw new BusinessException("协同通知类型不能为空");
        }
        PmcCoordinationNotice notice = new PmcCoordinationNotice();
        notice.setNoticeType(request.getNoticeType());
        notice.setTitle(StringUtils.hasText(request.getTitle())
                ? request.getTitle()
                : defaultTitle(request.getNoticeType()));
        notice.setContent(request.getContent());
        notice.setPlanId(request.getPlanId());
        notice.setPlanNo(request.getPlanNo());
        notice.setRequisitionId(request.getRequisitionId());
        notice.setOutboundNo(request.getOutboundNo());
        notice.setExceptionTitle(request.getExceptionTitle());
        notice.setTargetRole(StringUtils.hasText(request.getTargetRole()) ? request.getTargetRole() : "WAREHOUSE");
        notice.setStatus("OPEN");
        notice.setCreatedBy(request.getCreatedBy());
        notice.setCreatedByName(resolveUserName(request.getCreatedBy(), "PMC计划员"));
        notice.setCreatedAt(LocalDateTime.now());
        noticeMapper.insert(notice);
        return toVO(notice);
    }

    @Override
    public List<PmcCoordinationNoticeVO> listByTarget(String targetRole, String status) {
        LambdaQueryWrapper<PmcCoordinationNotice> wrapper = new LambdaQueryWrapper<PmcCoordinationNotice>()
                .eq(PmcCoordinationNotice::getTargetRole, StringUtils.hasText(targetRole) ? targetRole : "WAREHOUSE")
                .orderByDesc(PmcCoordinationNotice::getCreatedAt)
                .last("LIMIT 50");
        if (StringUtils.hasText(status)) {
            wrapper.eq(PmcCoordinationNotice::getStatus, status);
        }
        return noticeMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Override
    public List<PmcCoordinationNoticeVO> listCreatedBy(Long createdBy) {
        LambdaQueryWrapper<PmcCoordinationNotice> wrapper = new LambdaQueryWrapper<PmcCoordinationNotice>()
                .orderByDesc(PmcCoordinationNotice::getCreatedAt)
                .last("LIMIT 50");
        if (createdBy != null) {
            wrapper.eq(PmcCoordinationNotice::getCreatedBy, createdBy);
        }
        return noticeMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Override
    public PmcCoordinationNoticeVO markRead(Long noticeId) {
        PmcCoordinationNotice notice = requireNotice(noticeId);
        if ("OPEN".equals(notice.getStatus())) {
            notice.setStatus("READ");
            notice.setReadAt(LocalDateTime.now());
            noticeMapper.updateById(notice);
        }
        return toVO(notice);
    }

    @Override
    public PmcCoordinationNoticeVO markHandled(Long noticeId, String handledBy) {
        PmcCoordinationNotice notice = requireNotice(noticeId);
        notice.setStatus("DONE");
        notice.setHandledAt(LocalDateTime.now());
        notice.setHandledBy(StringUtils.hasText(handledBy) ? handledBy : "仓管员");
        if (notice.getReadAt() == null) {
            notice.setReadAt(LocalDateTime.now());
        }
        noticeMapper.updateById(notice);
        return toVO(notice);
    }

    private PmcCoordinationNotice requireNotice(Long noticeId) {
        PmcCoordinationNotice notice = noticeId == null ? null : noticeMapper.selectById(noticeId);
        if (notice == null) {
            throw new BusinessException("协同通知不存在");
        }
        return notice;
    }

    private String resolveUserName(Long userId, String fallback) {
        if (userId == null) {
            return fallback;
        }
        SysUser user = userMapper.selectById(userId);
        return user != null && StringUtils.hasText(user.getUserName()) ? user.getUserName() : fallback;
    }

    private String defaultTitle(String noticeType) {
        return switch (noticeType) {
            case "URGE_OUTBOUND" -> "催办出库";
            case "REPLENISH_ADVICE" -> "补料/调拨建议";
            case "REVIEW_FOLLOW" -> "复核跟进";
            default -> "出库协同通知";
        };
    }

    private String noticeTypeLabel(String noticeType) {
        if (noticeType == null) {
            return "协同通知";
        }
        return switch (noticeType) {
            case "URGE_OUTBOUND" -> "催办出库";
            case "REPLENISH_ADVICE" -> "补料/调拨";
            case "REVIEW_FOLLOW" -> "复核跟进";
            default -> noticeType;
        };
    }

    private String statusLabel(String status) {
        if (status == null) {
            return "待处理";
        }
        return switch (status) {
            case "OPEN" -> "待处理";
            case "READ" -> "已读";
            case "DONE" -> "已处理";
            default -> status;
        };
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? null : time.format(TIME_FMT);
    }

    private PmcCoordinationNoticeVO toVO(PmcCoordinationNotice notice) {
        PmcCoordinationNoticeVO vo = new PmcCoordinationNoticeVO();
        vo.setNoticeId(notice.getNoticeId());
        vo.setNoticeType(notice.getNoticeType());
        vo.setNoticeTypeLabel(noticeTypeLabel(notice.getNoticeType()));
        vo.setTitle(notice.getTitle());
        vo.setContent(notice.getContent());
        vo.setPlanId(notice.getPlanId());
        vo.setPlanNo(notice.getPlanNo());
        vo.setRequisitionId(notice.getRequisitionId());
        vo.setOutboundNo(notice.getOutboundNo());
        vo.setExceptionTitle(notice.getExceptionTitle());
        vo.setTargetRole(notice.getTargetRole());
        vo.setStatus(notice.getStatus());
        vo.setStatusLabel(statusLabel(notice.getStatus()));
        vo.setCreatedBy(notice.getCreatedBy());
        vo.setCreatedByName(notice.getCreatedByName());
        vo.setCreatedAt(formatTime(notice.getCreatedAt()));
        vo.setReadAt(formatTime(notice.getReadAt()));
        vo.setHandledAt(formatTime(notice.getHandledAt()));
        vo.setHandledBy(notice.getHandledBy());
        return vo;
    }
}
