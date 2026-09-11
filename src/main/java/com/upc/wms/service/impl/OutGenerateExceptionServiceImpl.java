package com.upc.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.upc.wms.agent.core.AgentDataUtils;
import com.upc.wms.agent.core.AgentStatus;
import com.upc.wms.agent.core.AgentTaskType;
import com.upc.wms.common.BusinessException;
import com.upc.wms.entity.MdItem;
import com.upc.wms.entity.OutGenerateException;
import com.upc.wms.entity.PmcRequisitionOrder;
import com.upc.wms.mapper.MdItemMapper;
import com.upc.wms.mapper.OutGenerateExceptionMapper;
import com.upc.wms.mapper.PmcRequisitionOrderMapper;
import com.upc.wms.service.OutGenerateExceptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OutGenerateExceptionServiceImpl implements OutGenerateExceptionService {

    private final OutGenerateExceptionMapper exceptionMapper;
    private final PmcRequisitionOrderMapper requisitionMapper;
    private final MdItemMapper itemMapper;

    @Override
    @Transactional
    public OutGenerateException recordFromAgent(Long taskId, String taskType, String status,
                                                String message, Map<String, Object> contextData,
                                                Map<String, Object> resultData) {
        try {
            return doRecordFromAgent(taskId, taskType, status, message, contextData, resultData);
        } catch (Exception e) {
            // 表未建或写入失败时不影响 Agent 主流程
            return null;
        }
    }

    private OutGenerateException doRecordFromAgent(Long taskId, String taskType, String status,
                                                   String message, Map<String, Object> contextData,
                                                   Map<String, Object> resultData) {
        if (!AgentTaskType.REQUISITION_OUTBOUND.name().equals(taskType)
                && !AgentTaskType.ORDER_REQUISITION_OUTBOUND.name().equals(taskType)) {
            return null;
        }
        if (!AgentStatus.MANUAL_REQUIRED.name().equals(status)
                && !AgentStatus.FAILED.name().equals(status)) {
            return null;
        }

        Map<String, Object> data = contextData != null ? contextData : Map.of();
        Map<String, Object> result = resultData != null ? resultData : Map.of();

        Long requisitionId = AgentDataUtils.getLong(data, "requisitionId");
        if (requisitionId == null) {
            requisitionId = AgentDataUtils.getLong(result, "requisitionId");
        }
        if (requisitionId == null) {
            return null;
        }

        String requisitionNo = AgentDataUtils.getString(data, "requisitionNo");
        if (!StringUtils.hasText(requisitionNo)) {
            requisitionNo = AgentDataUtils.getString(result, "requisitionNo");
        }
        if (!StringUtils.hasText(requisitionNo)) {
            PmcRequisitionOrder req = requisitionMapper.selectById(requisitionId);
            if (req != null) {
                requisitionNo = req.getRequisitionNo();
            }
        }
        if (!StringUtils.hasText(requisitionNo)) {
            requisitionNo = "REQ-" + requisitionId;
        }

        Long planId = AgentDataUtils.getLong(data, "planId");
        if (planId == null) {
            planId = AgentDataUtils.getLong(result, "planId");
        }
        String planNo = AgentDataUtils.getString(data, "planNo");
        if (!StringUtils.hasText(planNo)) {
            planNo = AgentDataUtils.getString(result, "planNo");
        }

        Long itemId = AgentDataUtils.getLong(result, "shortItemId");
        if (itemId == null) {
            itemId = firstShortageItemId(result);
        }
        BigDecimal shortageQty = AgentDataUtils.getBigDecimal(result.get("shortageQty"));
        if (shortageQty == null) {
            shortageQty = firstShortageQty(result, itemId);
        }

        String materialName = resolveMaterialName(itemId);
        String exceptionType = AgentStatus.MANUAL_REQUIRED.name().equals(status) ? "SHORTAGE" : "GENERATE_FAILED";
        String desc = StringUtils.hasText(message) ? message : "出库单生成失败，需人工处理";

        OutGenerateException existing = exceptionMapper.selectOne(
                new LambdaQueryWrapper<OutGenerateException>()
                        .eq(OutGenerateException::getRequisitionId, requisitionId)
                        .eq(OutGenerateException::getExceptionStatus, "OPEN")
                        .orderByDesc(OutGenerateException::getExceptionId)
                        .last("LIMIT 1"));

        if (existing != null) {
            existing.setAgentTaskId(taskId);
            existing.setRequisitionNo(requisitionNo);
            existing.setPlanId(planId);
            existing.setPlanNo(planNo);
            existing.setItemId(itemId);
            existing.setMaterialName(materialName);
            existing.setShortageQty(shortageQty);
            existing.setExceptionType(exceptionType);
            existing.setExceptionDesc(desc);
            exceptionMapper.updateById(existing);
            return existing;
        }

        OutGenerateException ex = new OutGenerateException();
        ex.setAgentTaskId(taskId);
        ex.setRequisitionId(requisitionId);
        ex.setRequisitionNo(requisitionNo);
        ex.setPlanId(planId);
        ex.setPlanNo(planNo);
        ex.setItemId(itemId);
        ex.setMaterialName(materialName);
        ex.setShortageQty(shortageQty);
        ex.setExceptionType(exceptionType);
        ex.setExceptionDesc(desc);
        ex.setExceptionStatus("OPEN");
        ex.setCreatedAt(LocalDateTime.now());
        exceptionMapper.insert(ex);
        return ex;
    }

    @Override
    @Transactional
    public OutGenerateException resolve(Long exceptionId, String result) {
        OutGenerateException ex = exceptionMapper.selectById(exceptionId);
        if (ex == null) {
            throw new BusinessException("出库生成异常不存在");
        }
        if ("RESOLVED".equals(ex.getExceptionStatus()) || "CLOSED".equals(ex.getExceptionStatus())) {
            return ex;
        }
        ex.setExceptionStatus("RESOLVED");
        ex.setResolveResult(StringUtils.hasText(result) ? result : "已标记处理完成，可重新生成出库单");
        ex.setResolvedAt(LocalDateTime.now());
        exceptionMapper.updateById(ex);
        return ex;
    }

    @Override
    @Transactional
    public void autoResolveByRequisition(Long requisitionId, String outboundNo) {
        if (requisitionId == null) {
            return;
        }
        try {
            List<OutGenerateException> openList = exceptionMapper.selectList(
                    new LambdaQueryWrapper<OutGenerateException>()
                            .eq(OutGenerateException::getRequisitionId, requisitionId)
                            .eq(OutGenerateException::getExceptionStatus, "OPEN"));
            if (openList.isEmpty()) {
                return;
            }
            String result = StringUtils.hasText(outboundNo)
                    ? "出库单 " + outboundNo + " 已生成，异常自动关闭"
                    : "出库单已生成，异常自动关闭";
            LocalDateTime now = LocalDateTime.now();
            for (OutGenerateException ex : openList) {
                ex.setExceptionStatus("RESOLVED");
                ex.setResolveResult(result);
                ex.setResolvedAt(now);
                exceptionMapper.updateById(ex);
            }
        } catch (Exception ignored) {
            // 表未建或查询失败时不影响出库单生成主流程
        }
    }

    @Override
    public List<OutGenerateException> listAll() {
        try {
            return exceptionMapper.selectList(
                    new LambdaQueryWrapper<OutGenerateException>()
                            .orderByDesc(OutGenerateException::getCreatedAt));
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public List<OutGenerateException> listOpen() {
        try {
            return exceptionMapper.selectList(
                    new LambdaQueryWrapper<OutGenerateException>()
                            .eq(OutGenerateException::getExceptionStatus, "OPEN")
                            .orderByDesc(OutGenerateException::getCreatedAt));
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public OutGenerateException getById(Long exceptionId) {
        return exceptionMapper.selectById(exceptionId);
    }

    @SuppressWarnings("unchecked")
    private Long firstShortageItemId(Map<String, Object> result) {
        Object lines = result.get("shortageLines");
        if (!(lines instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        Object first = list.get(0);
        if (first instanceof Map<?, ?> map) {
            return AgentDataUtils.getLong((Map<String, Object>) map, "itemId");
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private BigDecimal firstShortageQty(Map<String, Object> result, Long itemId) {
        Object lines = result.get("shortageLines");
        if (!(lines instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        for (Object row : list) {
            if (!(row instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> m = (Map<String, Object>) map;
            Long id = AgentDataUtils.getLong(m, "itemId");
            if (itemId == null || itemId.equals(id)) {
                return AgentDataUtils.getBigDecimal(m.get("shortageQty"));
            }
        }
        return null;
    }

    private String resolveMaterialName(Long itemId) {
        if (itemId == null) {
            return "";
        }
        MdItem item = itemMapper.selectById(itemId);
        if (item == null) {
            return "物料#" + itemId;
        }
        return item.getItemName() != null ? item.getItemName() : "物料#" + itemId;
    }
}
