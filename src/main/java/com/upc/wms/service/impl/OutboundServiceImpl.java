package com.upc.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.upc.wms.common.BusinessException;
import com.upc.wms.common.NoGenerator;
import com.upc.wms.dto.PickingSubmitRequest;
import com.upc.wms.dto.ReviewSubmitRequest;
import com.upc.wms.entity.InvInventory;
import com.upc.wms.entity.OutOrder;
import com.upc.wms.entity.OutOrderLine;
import com.upc.wms.entity.OutPickingLine;
import com.upc.wms.entity.OutPickingTask;
import com.upc.wms.entity.OutReviewException;
import com.upc.wms.entity.OutReviewLine;
import com.upc.wms.entity.OutReviewTask;
import com.upc.wms.entity.PmcRequisitionLine;
import com.upc.wms.mapper.InvInventoryMapper;
import com.upc.wms.mapper.OutOrderLineMapper;
import com.upc.wms.mapper.OutOrderMapper;
import com.upc.wms.mapper.OutPickingLineMapper;
import com.upc.wms.mapper.OutPickingTaskMapper;
import com.upc.wms.mapper.OutReviewExceptionMapper;
import com.upc.wms.mapper.OutReviewLineMapper;
import com.upc.wms.mapper.OutReviewTaskMapper;
import com.upc.wms.mapper.PmcRequisitionLineMapper;
import com.upc.wms.service.InventoryService;
import com.upc.wms.service.OutboundService;
import com.upc.wms.service.PrepAreaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OutboundServiceImpl implements OutboundService {

    private final OutOrderMapper outOrderMapper;
    private final OutOrderLineMapper outOrderLineMapper;
    private final OutPickingTaskMapper outPickingTaskMapper;
    private final OutPickingLineMapper outPickingLineMapper;
    private final OutReviewTaskMapper outReviewTaskMapper;
    private final OutReviewLineMapper outReviewLineMapper;
    private final OutReviewExceptionMapper outReviewExceptionMapper;
    private final PmcRequisitionLineMapper pmcRequisitionLineMapper;
    private final InvInventoryMapper invInventoryMapper;
    private final InventoryService inventoryService;
    private final PrepAreaService prepAreaService;

    @Override
    @Transactional
    public OutOrder createOutboundOrder(Long requisitionId, Long warehouseId) {
        OutOrder order = new OutOrder();
        order.setOutboundNo(NoGenerator.next("OUT"));
        order.setRequisitionId(requisitionId);
        order.setOutboundType("PRODUCTION_ISSUE");
        order.setWarehouseId(warehouseId);
        order.setOutboundStatus("PENDING_PICK");
        outOrderMapper.insert(order);

        List<PmcRequisitionLine> reqLines = pmcRequisitionLineMapper.selectList(
                new LambdaQueryWrapper<PmcRequisitionLine>().eq(PmcRequisitionLine::getRequisitionId, requisitionId));
        for (PmcRequisitionLine rl : reqLines) {
            OutOrderLine line = new OutOrderLine();
            line.setOutboundId(order.getOutboundId());
            line.setRequisitionLineId(rl.getRequisitionLineId());
            line.setItemId(rl.getItemId());
            line.setPlanQty(rl.getRequiredQty());
            line.setPickedQty(BigDecimal.ZERO);
            line.setShippedQty(BigDecimal.ZERO);
            line.setLineStatus("OPEN");
            outOrderLineMapper.insert(line);
        }
        return order;
    }

    @Override
    public List<OutOrder> listPendingOutbound() {
        return outOrderMapper.selectPendingPickList();
    }

    @Override
    public Map<String, Object> getOutboundDetail(Long outboundId) {
        OutOrder order = outOrderMapper.selectById(outboundId);
        if (order == null) {
            throw new BusinessException("出库单不存在");
        }
        List<OutOrderLine> lines = outOrderLineMapper.selectList(
                new LambdaQueryWrapper<OutOrderLine>().eq(OutOrderLine::getOutboundId, outboundId));
        Map<String, Object> detail = new HashMap<>();
        detail.put("order", order);
        detail.put("lines", lines);
        return detail;
    }

    @Override
    @Transactional
    public OutPickingTask createPickingTask(Long outboundId, Long assignedTo) {
        OutOrder order = outOrderMapper.selectById(outboundId);
        if (order == null) {
            throw new BusinessException("出库单不存在");
        }
        OutPickingTask task = new OutPickingTask();
        task.setPickingTaskNo(NoGenerator.next("PK"));
        task.setOutboundId(outboundId);
        task.setAssignedTo(assignedTo);
        task.setTaskStatus("PENDING");
        task.setCreatedAt(LocalDateTime.now());
        outPickingTaskMapper.insert(task);
        return task;
    }

    @Override
    @Transactional
    public OutPickingTask assignPickingTask(Long pickingTaskId, Long assignedTo) {
        OutPickingTask task = outPickingTaskMapper.selectById(pickingTaskId);
        if (task == null) {
            throw new BusinessException("拣货任务不存在");
        }
        if (assignedTo == null) {
            throw new BusinessException("请指定执行拣货的生产工人");
        }
        task.setAssignedTo(assignedTo);
        if ("PENDING".equals(task.getTaskStatus()) || task.getTaskStatus() == null) {
            task.setTaskStatus("ASSIGNED");
        }
        outPickingTaskMapper.updateById(task);
        return task;
    }

    @Override
    public OutPickingLine addPickingLine(OutPickingLine line) {
        outPickingLineMapper.insert(line);
        return line;
    }

    @Override
    @Transactional
    public void submitPicking(PickingSubmitRequest request) {
        OutPickingTask task = outPickingTaskMapper.selectById(request.getPickingTaskId());
        if (task == null) {
            throw new BusinessException("拣货任务不存在");
        }
        if (request.getLines() != null) {
            for (PickingSubmitRequest.Line l : request.getLines()) {
                OutPickingLine line = outPickingLineMapper.selectById(l.getPickingLineId());
                if (line == null) {
                    throw new BusinessException("拣货明细不存在: " + l.getPickingLineId());
                }
                line.setActualPickQty(l.getActualPickQty());
                outPickingLineMapper.updateById(line);
            }
        }
        task.setTaskStatus("PICKED");
        outPickingTaskMapper.updateById(task);
        outOrderMapper.updateStatus(task.getOutboundId(), "PENDING_REVIEW");
    }

    @Override
    @Transactional
    public OutReviewTask createReviewTask(Long outboundId, Long reviewedBy) {
        OutReviewTask task = new OutReviewTask();
        task.setReviewTaskNo(NoGenerator.next("RV"));
        task.setOutboundId(outboundId);
        task.setReviewedBy(reviewedBy);
        task.setReviewResult("PENDING");
        outReviewTaskMapper.insert(task);
        return task;
    }

    @Override
    @Transactional
    public OutReviewTask submitReview(ReviewSubmitRequest request) {
        OutReviewTask task = outReviewTaskMapper.selectById(request.getReviewTaskId());
        if (task == null) {
            throw new BusinessException("复核任务不存在");
        }
        boolean hasException = false;
        if (request.getLines() != null) {
            for (ReviewSubmitRequest.Line l : request.getLines()) {
                OutReviewLine line = new OutReviewLine();
                line.setReviewTaskId(task.getReviewTaskId());
                line.setPickingLineId(l.getPickingLineId());
                line.setReviewQty(l.getReviewQty());
                line.setReviewResult(l.getReviewResult());
                outReviewLineMapper.insert(line);

                if ("EXCEPTION".equals(l.getReviewResult())) {
                    hasException = true;
                    OutReviewException ex = new OutReviewException();
                    ex.setReviewLineId(line.getReviewLineId());
                    ex.setExceptionType(l.getExceptionType() != null ? l.getExceptionType() : "MISMATCH");
                    ex.setExceptionDesc(l.getExceptionDesc() != null ? l.getExceptionDesc() : "复核不一致");
                    ex.setExceptionStatus("OPEN");
                    ex.setCreatedAt(LocalDateTime.now());
                    outReviewExceptionMapper.insert(ex);
                }
            }
        }
        task.setReviewResult(hasException ? "EXCEPTION" : "PASS");
        task.setReviewedBy(request.getReviewedBy());
        task.setReviewedAt(LocalDateTime.now());
        outReviewTaskMapper.updateById(task);
        outOrderMapper.updateStatus(task.getOutboundId(), hasException ? "EXCEPTION" : "REVIEWED");
        return task;
    }

    @Override
    @Transactional
    public void transferToPrepAreaForPickingTask(Long pickingTaskId, Long prepLocationId, Long operatedBy) {
        if (pickingTaskId == null) {
            throw new BusinessException("缺少拣货任务");
        }
        OutPickingTask task = outPickingTaskMapper.selectById(pickingTaskId);
        if (task == null) {
            throw new BusinessException("拣货任务不存在");
        }
        if (Set.of("STAGED", "COMPLETED", "PAUSED").contains(Objects.requireNonNullElse(task.getTaskStatus(), ""))) {
            throw new BusinessException("该拣货任务已完成备料，库存已移入备料区");
        }
        Long outboundId = task.getOutboundId();
        if (outboundId == null) {
            throw new BusinessException("拣货任务未关联出库单");
        }
        OutOrder order = outOrderMapper.selectById(outboundId);
        if (order == null) {
            throw new BusinessException("出库单不存在");
        }
        Long warehouseId = order.getWarehouseId() != null ? order.getWarehouseId() : 1L;
        if (prepLocationId == null) {
            prepLocationId = prepAreaService.recommendForPickingTask(pickingTaskId).getLocationId();
        }
        prepAreaService.validatePrepLocation(warehouseId, prepLocationId);
        Long operator = operatedBy == null ? 1L : operatedBy;
        List<OutPickingLine> pickLines = outPickingLineMapper.selectList(
                new LambdaQueryWrapper<OutPickingLine>().eq(OutPickingLine::getPickingTaskId, pickingTaskId));
        if (pickLines.isEmpty()) {
            throw new BusinessException("拣货任务无明细行");
        }
        for (OutPickingLine pl : pickLines) {
            BigDecimal qty = pl.getActualPickQty() != null ? pl.getActualPickQty() : BigDecimal.ZERO;
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (pl.getInventoryId() == null) {
                throw new BusinessException("拣货行未绑定库存记录，无法移库至备料区");
            }
            InvInventory source = invInventoryMapper.selectById(pl.getInventoryId());
            if (source == null) {
                throw new BusinessException("源库存记录不存在: " + pl.getInventoryId());
            }
            inventoryService.moveInventory(pl.getInventoryId(), prepLocationId, qty, outboundId, operator);
            InvInventory prepInv = invInventoryMapper.selectByItemBatchLocation(
                    source.getWarehouseId(), prepLocationId, source.getItemId(), source.getBatchId());
            if (prepInv == null) {
                throw new BusinessException("移库后未找到备料区库存记录");
            }
            pl.setInventoryId(prepInv.getInventoryId());
            pl.setLocationId(prepLocationId);
            outPickingLineMapper.updateById(pl);

            if (pl.getOutboundLineId() != null) {
                OutOrderLine ol = outOrderLineMapper.selectById(pl.getOutboundLineId());
                if (ol != null) {
                    BigDecimal picked = ol.getPickedQty() != null ? ol.getPickedQty() : BigDecimal.ZERO;
                    ol.setPickedQty(picked.add(qty));
                    ol.setLineStatus("PICKED");
                    outOrderLineMapper.updateById(ol);
                }
            }
        }
    }

    @Override
    @Transactional
    public void deductPrepAreaForHandover(Long pickingTaskId, Long operatedBy) {
        if (pickingTaskId == null) {
            throw new BusinessException("缺少拣货任务");
        }
        OutPickingTask task = outPickingTaskMapper.selectById(pickingTaskId);
        if (task == null) {
            throw new BusinessException("拣货任务不存在");
        }
        if (!"STAGED".equals(Objects.requireNonNullElse(task.getTaskStatus(), ""))) {
            throw new BusinessException("物料尚未入备料区，无法确认领取");
        }
        Long outboundId = task.getOutboundId();
        if (outboundId == null) {
            throw new BusinessException("拣货任务未关联出库单");
        }
        Long operator = operatedBy == null ? 1L : operatedBy;
        List<OutPickingLine> pickLines = outPickingLineMapper.selectList(
                new LambdaQueryWrapper<OutPickingLine>().eq(OutPickingLine::getPickingTaskId, pickingTaskId));
        if (pickLines.isEmpty()) {
            throw new BusinessException("拣货任务无明细行");
        }
        for (OutPickingLine pl : pickLines) {
            BigDecimal qty = pl.getActualPickQty() != null ? pl.getActualPickQty() : BigDecimal.ZERO;
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (pl.getInventoryId() == null) {
                throw new BusinessException("拣货行未绑定备料区库存，无法完成领料扣账");
            }
            inventoryService.deductInventory(pl.getInventoryId(), qty, "OUTBOUND_ORDER", outboundId, operator);
            if (pl.getOutboundLineId() != null) {
                OutOrderLine ol = outOrderLineMapper.selectById(pl.getOutboundLineId());
                if (ol != null) {
                    BigDecimal shipped = ol.getShippedQty() != null ? ol.getShippedQty() : BigDecimal.ZERO;
                    ol.setShippedQty(shipped.add(qty));
                    ol.setLineStatus("SHIPPED");
                    outOrderLineMapper.updateById(ol);
                }
            }
        }
    }

    @Override
    @Transactional
    public OutOrder confirmOutbound(Long outboundId, Long operatedBy) {
        OutOrder order = outOrderMapper.selectById(outboundId);
        if (order == null) {
            throw new BusinessException("出库单不存在");
        }
        String status = Objects.requireNonNullElse(order.getOutboundStatus(), "");
        if ("SHIPPED".equals(status)) {
            return order;
        }
        // 新流程：工人确认领取时已从备料区扣账，此处仅标记出库完成
        if ("PENDING_HANDOVER".equals(status)) {
            order.setOutboundStatus("SHIPPED");
            outOrderMapper.updateById(order);
            return order;
        }
        List<OutOrderLine> orderLines = outOrderLineMapper.selectList(
                new LambdaQueryWrapper<OutOrderLine>().eq(OutOrderLine::getOutboundId, outboundId));
        for (OutOrderLine ol : orderLines) {
            List<OutPickingLine> pickLines = outPickingLineMapper.selectList(
                    new LambdaQueryWrapper<OutPickingLine>().eq(OutPickingLine::getOutboundLineId, ol.getOutboundLineId()));
            BigDecimal shipped = BigDecimal.ZERO;
            for (OutPickingLine pl : pickLines) {
                BigDecimal qty = pl.getActualPickQty() != null ? pl.getActualPickQty() : BigDecimal.ZERO;
                if (qty.compareTo(BigDecimal.ZERO) > 0 && pl.getInventoryId() != null) {
                    inventoryService.deductInventory(pl.getInventoryId(), qty, "OUTBOUND_ORDER", outboundId, operatedBy);
                    shipped = shipped.add(qty);
                }
            }
            ol.setShippedQty(shipped);
            ol.setLineStatus("SHIPPED");
            outOrderLineMapper.updateById(ol);
        }
        order.setOutboundStatus("SHIPPED");
        outOrderMapper.updateById(order);
        return order;
    }

    private void markAllOrderLinesShipped(Long outboundId) {
        List<OutOrderLine> orderLines = outOrderLineMapper.selectList(
                new LambdaQueryWrapper<OutOrderLine>().eq(OutOrderLine::getOutboundId, outboundId));
        for (OutOrderLine ol : orderLines) {
            if (!"SHIPPED".equals(ol.getLineStatus())) {
                ol.setLineStatus("SHIPPED");
                outOrderLineMapper.updateById(ol);
            }
        }
    }
}
