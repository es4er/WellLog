package com.upc.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.upc.wms.agent.core.AgentDataUtils;
import com.upc.wms.common.BusinessException;
import com.upc.wms.dto.*;
import com.upc.wms.entity.*;
import com.upc.wms.mapper.*;
import com.upc.wms.service.InventoryService;
import com.upc.wms.service.OutboundService;
import com.upc.wms.service.PrepAreaService;
import com.upc.wms.service.UserService;
import com.upc.wms.service.WorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkerServiceImpl implements WorkerService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 演示基准日，与前端 workerConstants.TODAY 对齐 */
    private static final LocalDate DEMO_TODAY = LocalDate.of(2026, 7, 9);

    private final OutPickingTaskMapper pickingTaskMapper;
    private final OutPickingLineMapper pickingLineMapper;
    private final OutOrderMapper outOrderMapper;
    private final PmcRequisitionOrderMapper requisitionMapper;
    private final PmcProductionPlanMapper planMapper;
    private final MdItemMapper itemMapper;
    private final MdBatchMapper batchMapper;
    private final BarcodeLabelMapper barcodeLabelMapper;
    private final InvInventoryMapper inventoryMapper;
    private final WhLocationMapper locationMapper;
    private final WhZoneMapper zoneMapper;
    private final WorkerScanRecordMapper scanRecordMapper;
    private final WorkerExceptionMapper exceptionMapper;
    private final WorkerHandoverMapper handoverMapper;
    private final WorkerReplenishRequestMapper replenishRequestMapper;
    private final WorkerNotificationMapper notificationMapper;
    private final WorkerProductionCompletionMapper completionMapper;
    private final WorkerProcessTransferMapper transferMapper;
    private final UserService userService;
    private final InventoryService inventoryService;
    private final OutboundService outboundService;

    @Override
    public WorkerWorkbenchVO loadWorkbench(Long workerId) {
        Long assignedTo = workerId == null ? 20L : workerId;
        List<OutPickingTask> tasks = pickingTaskMapper.selectList(
                new LambdaQueryWrapper<OutPickingTask>()
                        .eq(OutPickingTask::getAssignedTo, assignedTo)
                        .in(OutPickingTask::getTaskStatus, List.of("STAGED", "PAUSED", "COMPLETED"))
                        .orderByAsc(OutPickingTask::getPlannedPickTime));

        List<WorkerTaskVO> taskVOs = new ArrayList<>();
        for (OutPickingTask task : tasks) {
            WorkerHandover handover = handoverMapper.selectOne(
                    new LambdaQueryWrapper<WorkerHandover>().eq(WorkerHandover::getPickingTaskId, task.getPickingTaskId()));
            taskVOs.add(buildTaskVO(task, handover));
        }

        List<WorkerException> exceptions = exceptionMapper.selectList(
                new LambdaQueryWrapper<WorkerException>()
                        .eq(WorkerException::getWorkerId, assignedTo)
                        .orderByDesc(WorkerException::getSubmittedAt));

        List<WorkerExceptionVO> exceptionVOs = exceptions.stream().map(this::toExceptionVO).toList();
        List<WorkerExceptionVO> openExceptionVOs = exceptionVOs.stream()
                .filter(ex -> !"已关闭".equals(ex.getStatus()))
                .toList();

        List<WorkerScanRecord> scans = scanRecordMapper.selectList(
                new LambdaQueryWrapper<WorkerScanRecord>()
                        .eq(WorkerScanRecord::getWorkerId, assignedTo)
                        .orderByDesc(WorkerScanRecord::getScannedAt)
                        .last("LIMIT 50"));

        List<WorkerScanRecordVO> scanVOs = scans.stream().map(this::toScanVO).toList();

        WorkerSummaryVO summary = buildSummary(taskVOs, openExceptionVOs);

        WorkerWorkbenchVO vo = new WorkerWorkbenchVO();
        vo.setSummary(summary);
        vo.setTasks(taskVOs);
        vo.setExceptions(exceptionVOs);
        vo.setScanRecords(scanVOs);
        vo.setReplenishRecords(listReplenish(assignedTo));
        vo.setNotifications(listNotifications(assignedTo));
        vo.setCompletions(listCompletions(assignedTo));
        vo.setTransfers(listTransfers(assignedTo));
        return vo;
    }

    @Override
    @Transactional
    public WorkerScanResultVO submitScan(WorkerScanRequest request) {
        return doScan(request.getPickingTaskId(), request.getPickingLineId(),
                request.getWorkerId(), request.getBarcodeValue());
    }

    @Override
    @Transactional
    public WorkerExceptionVO submitException(WorkerExceptionSubmitRequest request) {
        OutPickingTask task = requireTask(request.getPickingTaskId(), request.getWorkerId());
        OutPickingLine line = requireLine(request.getPickingLineId(), task.getPickingTaskId());
        MdItem item = itemMapper.selectById(line.getItemId());
        TaskContext ctx = buildContext(task);

        int required = toInt(line.getPlanPickQty());
        int scanned = toInt(line.getWorkerScannedQty());
        int shortage = Math.max(0, required - scanned);

        WorkerException ex = new WorkerException();
        ex.setPickingTaskId(task.getPickingTaskId());
        ex.setPickingLineId(line.getPickingLineId());
        ex.setWorkerId(request.getWorkerId() == null ? task.getAssignedTo() : request.getWorkerId());
        ex.setWorkOrderNo(ctx.workOrder);
        ex.setRequisitionNo(ctx.requisitionNo);
        ex.setMaterialName(item != null ? item.getItemName() : "");
        ex.setRequiredQty(line.getPlanPickQty());
        ex.setActualQty(line.getWorkerScannedQty());
        ex.setShortageQty(BigDecimal.valueOf(shortage));
        ex.setExceptionType(request.getType());
        ex.setExceptionNote(request.getNote());
        ex.setLocationCode(request.getLocation());
        ex.setExceptionStatus("PENDING_WAREHOUSE");
        ex.setSubmittedAt(LocalDateTime.now());
        exceptionMapper.insert(ex);

        task.setTaskStatus("PAUSED");
        pickingTaskMapper.updateById(task);

        return toExceptionVO(ex);
    }

    @Override
    @Transactional
    public WorkerTaskVO confirmHandover(WorkerHandoverRequest request) {
        OutPickingTask task = requireTask(request.getPickingTaskId(), request.getWorkerId());
        if ("COMPLETED".equals(Objects.requireNonNullElse(task.getTaskStatus(), ""))) {
            throw new BusinessException("该领料任务已完成");
        }
        WorkerHandover existingHandover = handoverMapper.selectOne(new LambdaQueryWrapper<WorkerHandover>()
                .eq(WorkerHandover::getPickingTaskId, task.getPickingTaskId()));
        if (existingHandover != null) {
            throw new BusinessException("该领料任务已确认领取");
        }

        List<OutPickingLine> lines = listLines(task.getPickingTaskId());
        // 工人一键确认领料：以仓管备料数量为准，无需现场扫码
        for (OutPickingLine line : lines) {
            int plan = toInt(line.getPlanPickQty());
            int actual = toInt(line.getActualPickQty());
            int received = actual > 0 ? actual : plan;
            line.setWorkerScannedQty(BigDecimal.valueOf(received));
            pickingLineMapper.updateById(line);
        }

        Long workerId = request.getWorkerId() == null ? task.getAssignedTo() : request.getWorkerId();
        outboundService.deductPrepAreaForHandover(task.getPickingTaskId(), workerId);
        if (task.getOutboundId() != null) {
            outboundService.confirmOutbound(task.getOutboundId(), workerId);
        }

        TaskContext ctx = buildContext(task);
        WorkerHandover handover = new WorkerHandover();
        handover.setPickingTaskId(task.getPickingTaskId());
        handover.setWorkerId(request.getWorkerId() == null ? task.getAssignedTo() : request.getWorkerId());
        handover.setWorkOrderNo(ctx.workOrder);
        handover.setRequisitionNo(ctx.requisitionNo);
        handover.setWarehouseHandler("沈砚");
        handover.setHandoverTime(LocalDateTime.now());
        handover.setRemark(request.getRemark());
        handoverMapper.insert(handover);

        task.setTaskStatus("COMPLETED");
        pickingTaskMapper.updateById(task);

        return buildTaskVO(task, handover);
    }

    @Override
    public BarcodeScanDetailVO resolveBarcode(String barcodeValue) {
        BarcodeScanDetailVO detail = new BarcodeScanDetailVO();
        detail.setBarcodeValue(barcodeValue);

        try {
            BarcodeLabel label = barcodeLabelMapper.selectByValue(barcodeValue);
            if (label != null && label.getBindId() != null) {
                InvInventory inv = inventoryMapper.selectById(label.getBindId());
                if (inv != null) {
                    detail.setInventoryId(inv.getInventoryId());
                    detail.setItemId(inv.getItemId());
                    detail.setBatchId(inv.getBatchId());
                    MdItem item = itemMapper.selectById(inv.getItemId());
                    if (item != null) {
                        detail.setMaterialCode(item.getItemCode());
                        detail.setMaterialName(item.getItemName());
                    }
                    if (inv.getBatchId() != null) {
                        MdBatch batch = batchMapper.selectById(inv.getBatchId());
                        if (batch != null) {
                            detail.setBatchNo(batch.getBatchNo());
                            detail.setQualityStatus(batch.getQualityStatus());
                        }
                    }
                    return detail;
                }
            }
        } catch (Exception ignored) {
            // fallback parse
        }

        ParsedBarcode parsed = parseBarcode(barcodeValue);
        detail.setBatchNo(parsed.batchNo);
        detail.setMaterialCode(parsed.materialCode);
        if (parsed.batchNo != null) {
            MdBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<MdBatch>()
                    .eq(MdBatch::getBatchNo, parsed.batchNo).last("LIMIT 1"));
            if (batch != null) {
                detail.setBatchId(batch.getBatchId());
                detail.setItemId(batch.getItemId());
                detail.setQualityStatus(batch.getQualityStatus());
                MdItem item = itemMapper.selectById(batch.getItemId());
                if (item != null) {
                    detail.setMaterialCode(item.getItemCode());
                    detail.setMaterialName(item.getItemName());
                }
            }
        }
        return detail;
    }

    @Override
    public WorkerScanResultVO processScanFromAgent(Map<String, Object> data) {
        WorkerScanRequest req = new WorkerScanRequest();
        req.setPickingTaskId(AgentDataUtils.getLong(data, "pickingTaskId"));
        req.setPickingLineId(AgentDataUtils.getLong(data, "pickingLineId"));
        req.setWorkerId(AgentDataUtils.getLong(data, "workerId", AgentDataUtils.getLong(data, "operatedBy")));
        req.setBarcodeValue(AgentDataUtils.getString(data, "barcodeValue"));
        return submitScan(req);
    }

    @Override
    public WorkerExceptionVO processExceptionFromAgent(Map<String, Object> data) {
        WorkerExceptionSubmitRequest req = new WorkerExceptionSubmitRequest();
        req.setPickingTaskId(AgentDataUtils.getLong(data, "pickingTaskId"));
        req.setPickingLineId(AgentDataUtils.getLong(data, "pickingLineId"));
        req.setWorkerId(AgentDataUtils.getLong(data, "workerId", AgentDataUtils.getLong(data, "operatedBy")));
        req.setType(AgentDataUtils.getString(data, "exceptionType", "缺件"));
        req.setNote(AgentDataUtils.getString(data, "exceptionNote"));
        req.setLocation(AgentDataUtils.getString(data, "location"));
        return submitException(req);
    }

    @Override
    public List<WorkerReplenishVO> listReplenish(Long workerId) {
        Long assignedTo = workerId == null ? 1L : workerId;
        return replenishRequestMapper.selectList(
                        new LambdaQueryWrapper<WorkerReplenishRequest>()
                                .eq(WorkerReplenishRequest::getWorkerId, assignedTo)
                                .orderByDesc(WorkerReplenishRequest::getSubmittedAt))
                .stream()
                .map(this::toReplenishVO)
                .toList();
    }

    @Override
    @Transactional
    public WorkerReplenishVO submitReplenish(WorkerReplenishSubmitRequest request) {
        if (request.getQty() == null || request.getQty() <= 0) {
            throw new BusinessException("补料数量必须大于 0");
        }
        OutPickingTask task = requireTask(request.getPickingTaskId(), request.getWorkerId());
        OutPickingLine line = requireLine(request.getPickingLineId(), task.getPickingTaskId());
        MdItem item = itemMapper.selectById(line.getItemId());
        TaskContext ctx = buildContext(task);

        WorkerReplenishRequest row = new WorkerReplenishRequest();
        row.setPickingTaskId(task.getPickingTaskId());
        row.setPickingLineId(line.getPickingLineId());
        row.setWorkerId(request.getWorkerId() == null ? task.getAssignedTo() : request.getWorkerId());
        row.setWorkOrderNo(ctx.workOrder);
        row.setRequisitionNo(ctx.requisitionNo);
        row.setProductName(ctx.productName);
        row.setItemId(line.getItemId());
        row.setMaterialCode(item != null ? item.getItemCode() : "");
        row.setMaterialName(item != null ? item.getItemName() : "");
        row.setSpecModel(item != null ? item.getSpecModel() : "");
        row.setRequestQty(BigDecimal.valueOf(request.getQty()));
        row.setReason(request.getReason() == null ? "其他" : request.getReason());
        row.setNote(request.getNote());
        row.setRequestStatus("PENDING_REVIEW");
        row.setSubmittedAt(LocalDateTime.now());
        row.setHandlerName(task.getHandlerName() != null ? task.getHandlerName() : "赵工");
        replenishRequestMapper.insert(row);
        return toReplenishVO(row);
    }

    private WorkerScanResultVO doScan(Long pickingTaskId, Long pickingLineId, Long workerId, String barcodeValue) {
        OutPickingTask task = requireTask(pickingTaskId, workerId);
        OutPickingLine line = requireLine(pickingLineId, task.getPickingTaskId());
        MdItem expectedItem = itemMapper.selectById(line.getItemId());
        MdBatch expectedBatch = line.getBatchId() != null ? batchMapper.selectById(line.getBatchId()) : null;

        BarcodeScanDetailVO resolved = resolveBarcode(barcodeValue);
        List<String> messages = new ArrayList<>();
        boolean success = true;

        if (resolved.getMaterialName() == null && resolved.getItemId() == null) {
            ParsedBarcode parsed = parseBarcode(barcodeValue);
            if (parsed.materialCode != null && expectedItem != null) {
                resolved.setMaterialName(expectedItem.getItemName());
            }
        }

        String resolvedMaterial = resolved.getMaterialName() != null
                ? resolved.getMaterialName()
                : (expectedItem != null ? expectedItem.getItemName() : "");
        String resolvedBatch = resolved.getBatchNo() != null
                ? resolved.getBatchNo()
                : (expectedBatch != null ? expectedBatch.getBatchNo() : "");

        if (expectedItem != null && resolved.getItemId() != null && !expectedItem.getItemId().equals(resolved.getItemId())) {
            success = false;
            messages.add("× 物料不匹配");
        } else if (expectedItem != null && resolvedMaterial.equals(expectedItem.getItemName())) {
            messages.add("✓ 物料匹配");
        } else if (expectedItem == null || !resolvedMaterial.equals(expectedItem.getItemName())) {
            success = false;
            messages.add("× 物料不匹配");
        } else {
            messages.add("✓ 物料匹配");
        }

        if (expectedBatch != null && resolvedBatch != null && !expectedBatch.getBatchNo().equals(resolvedBatch)) {
            success = false;
            messages.add("× 批次不在领料范围");
        } else if (expectedBatch != null) {
            messages.add("✓ 批次匹配");
        }

        if ("UNQUALIFIED".equalsIgnoreCase(resolved.getQualityStatus()) || barcodeValue.contains("QC-FAIL")) {
            success = false;
            messages.add("× 该批次质量状态异常");
        }

        int required = toInt(line.getPlanPickQty());
        int scanned = toInt(line.getWorkerScannedQty());
        if (scanned >= required) {
            success = false;
            messages.add("× 数量超过应领数量");
        } else if (success) {
            line.setWorkerScannedQty(BigDecimal.valueOf(scanned + 1));
            pickingLineMapper.updateById(line);
            // 备料区领取阶段保持 STAGED，避免任务从工人工作台消失
            if (!"STAGED".equals(task.getTaskStatus()) && !"IN_PROGRESS".equals(task.getTaskStatus())) {
                task.setTaskStatus("IN_PROGRESS");
                pickingTaskMapper.updateById(task);
            }
            messages.add("✓ 数量 +1");
        }

        TaskContext ctx = buildContext(task);
        WorkerScanRecord record = new WorkerScanRecord();
        record.setPickingTaskId(task.getPickingTaskId());
        record.setPickingLineId(line.getPickingLineId());
        record.setWorkerId(workerId == null ? task.getAssignedTo() : workerId);
        record.setBarcodeValue(barcodeValue);
        record.setItemId(line.getItemId());
        record.setBatchId(line.getBatchId());
        record.setMaterialName(resolvedMaterial);
        record.setBatchNo(resolvedBatch);
        record.setScanResult(success ? "SUCCESS" : "FAILED");
        record.setResultMessage(messages.stream().filter(m -> m.startsWith("×")).findFirst()
                .orElse("成功").replace("× ", "").replace("✓ ", ""));
        record.setScannedAt(LocalDateTime.now());
        scanRecordMapper.insert(record);

        WorkerScanResultVO result = new WorkerScanResultVO();
        result.setSuccess(success);
        result.setMessages(messages);
        WorkerScanRecordVO scanVO = toScanVO(record);
        scanVO.setWorkOrder(ctx.workOrder);
        result.setRecord(scanVO);
        return result;
    }

    private WorkerTaskVO buildTaskVO(OutPickingTask task, WorkerHandover handover) {
        TaskContext ctx = buildContext(task);
        List<OutPickingLine> lines = listLines(task.getPickingTaskId());
        boolean prepStaging = isPrepStagingTask(task);

        List<WorkerTaskItemVO> items = lines.stream().map(line -> {
            if (prepStaging) {
                syncStagingLinePrepReference(task, line);
            }
            MdItem item = itemMapper.selectById(line.getItemId());
            MdBatch batch = line.getBatchId() != null ? batchMapper.selectById(line.getBatchId()) : null;
            WhLocation location = resolveWorkerItemLocation(task, line);
            InvInventory inventory = line.getInventoryId() != null ? inventoryMapper.selectById(line.getInventoryId()) : null;
            int required = toInt(line.getPlanPickQty());
            int prepared = toInt(line.getActualPickQty());
            if (prepared <= 0) {
                prepared = toInt(line.getWorkerScannedQty());
            }
            WorkerTaskItemVO iv = new WorkerTaskItemVO();
            iv.setId("item-" + line.getPickingLineId());
            iv.setPickingLineId(line.getPickingLineId());
            iv.setMaterial(item != null ? item.getItemName() : "物料-" + line.getItemId());
            iv.setMaterialCode(item != null ? item.getItemCode() : "");
            iv.setSpec(item != null && item.getSpecModel() != null ? item.getSpecModel() : "—");
            iv.setRequired(required);
            iv.setScanned(prepared);
            iv.setBatch(batch != null ? batch.getBatchNo() : "");
            iv.setLocation(location != null ? location.getLocationCode() : "—");
            iv.setAvailable(inventory != null ? toInt(inventory.getAvailableQty()) : required);
            iv.setBatchId(line.getBatchId());
            iv.setItemId(line.getItemId());
            iv.setShortage(Math.max(0, required - prepared));
            iv.setStatus(itemStatus(prepared, required));
            return iv;
        }).toList();

        WorkerTaskVO vo = new WorkerTaskVO();
        vo.setId("pick-" + task.getPickingTaskId());
        vo.setPickingTaskId(task.getPickingTaskId());
        vo.setWorkOrder(ctx.workOrder);
        vo.setRequisition(ctx.requisitionNo);
        vo.setProduct(ctx.productName);
        vo.setPriority(task.getPriority() != null ? task.getPriority() : "中");
        vo.setPlanQty(task.getPlanQty() != null ? toInt(task.getPlanQty()) : items.size());
        vo.setUnit(task.getUnit() != null ? task.getUnit() : "套");
        vo.setPlanDate(ctx.planDate);
        vo.setExpectedTime(ctx.expectedTime);
        vo.setHandler(task.getHandlerName() != null ? task.getHandlerName() : "赵工");
        vo.setHandoverTime(handover != null && handover.getHandoverTime() != null
                ? handover.getHandoverTime().format(TIME_FMT) : "");
        vo.setCancelled(task.getCancelled() != null && task.getCancelled() == 1);
        vo.setStatus(mapTaskStatus(task, handover, items));
        vo.setItems(items);
        return vo;
    }

    private WorkerSummaryVO buildSummary(List<WorkerTaskVO> tasks, List<WorkerExceptionVO> exceptions) {
        int pending = 0;
        int pendingScan = 0;
        int completed = 0;
        LocalDate today = DEMO_TODAY;
        for (WorkerTaskVO task : tasks) {
            if ("已交接".equals(task.getStatus())) {
                completed++;
                continue;
            }
            if (task.getCancelled() != null && task.getCancelled()) continue;
            boolean overdue = task.getPlanDate() != null && task.getPlanDate().compareTo(today.format(DATE_FMT)) < 0;
            boolean todayTask = task.getPlanDate() != null && task.getPlanDate().equals(today.format(DATE_FMT));
            if (overdue || todayTask || !"已交接".equals(task.getStatus())) {
                pending++;
            }
            for (WorkerTaskItemVO item : task.getItems()) {
                if (item.getScanned() < item.getRequired()) pendingScan++;
            }
        }
        WorkerSummaryVO summary = new WorkerSummaryVO();
        summary.setPending(pending);
        summary.setPendingScan(pendingScan);
        summary.setShortage(exceptions.size());
        summary.setCompleted(completed);
        return summary;
    }

    private String mapTaskStatus(OutPickingTask task, WorkerHandover handover, List<WorkerTaskItemVO> items) {
        if (task.getCancelled() != null && task.getCancelled() == 1) return "已取消";
        if (handover != null || "COMPLETED".equals(task.getTaskStatus())) return "已交接";
        if ("PAUSED".equals(task.getTaskStatus())) return "补料/异常";
        boolean shortage = items.stream().anyMatch(i -> i.getScanned() < i.getRequired());
        if (shortage) return "补料/异常";
        return "备料区待领";
    }

    private String itemStatus(int prepared, int required) {
        if (prepared >= required) return "已备齐";
        if (prepared > 0) return "缺 " + (required - prepared);
        return "待备料";
    }

    private WorkerExceptionVO toExceptionVO(WorkerException ex) {
        WorkerExceptionVO vo = new WorkerExceptionVO();
        vo.setId("ex-" + ex.getExceptionId());
        vo.setExceptionId(ex.getExceptionId());
        vo.setPickingTaskId(ex.getPickingTaskId());
        vo.setPickingLineId(ex.getPickingLineId() != null ? "item-" + ex.getPickingLineId() : "");
        vo.setWorkOrder(ex.getWorkOrderNo());
        vo.setRequisition(ex.getRequisitionNo());
        vo.setMaterial(ex.getMaterialName());
        vo.setRequired(toInt(ex.getRequiredQty()));
        vo.setActual(toInt(ex.getActualQty()));
        vo.setShortage(toInt(ex.getShortageQty()));
        vo.setType(ex.getExceptionType());
        vo.setNote(ex.getExceptionNote());
        vo.setStatus(mapExceptionStatus(ex.getExceptionStatus()));
        vo.setSubmittedAt(formatSubmittedAt(ex.getSubmittedAt()));
        OutPickingTask task = pickingTaskMapper.selectById(ex.getPickingTaskId());
        vo.setHandler(task != null && task.getHandlerName() != null ? task.getHandlerName() : "赵工");
        vo.setTaskId("pick-" + ex.getPickingTaskId());
        vo.setItemId(ex.getPickingLineId() != null ? "item-" + ex.getPickingLineId() : "");
        return vo;
    }

    private WorkerScanRecordVO toScanVO(WorkerScanRecord record) {
        WorkerScanRecordVO vo = new WorkerScanRecordVO();
        vo.setId("scan-" + record.getScanId());
        vo.setTime(record.getScannedAt() != null ? record.getScannedAt().format(DateTimeFormatter.ofPattern("HH:mm:ss")) : "");
        vo.setBarcode(record.getBarcodeValue());
        vo.setMaterial(record.getMaterialName());
        vo.setBatch(record.getBatchNo());
        vo.setSuccess("SUCCESS".equals(record.getScanResult()));
        vo.setResult(vo.getSuccess() ? "成功" : record.getResultMessage());
        vo.setPickingTaskId(record.getPickingTaskId());
        if (record.getPickingTaskId() != null) {
            OutPickingTask task = pickingTaskMapper.selectById(record.getPickingTaskId());
            if (task != null) {
                TaskContext ctx = buildContext(task);
                vo.setWorkOrder(ctx.workOrder);
            }
        }
        return vo;
    }

    private WorkerReplenishVO toReplenishVO(WorkerReplenishRequest row) {
        WorkerReplenishVO vo = new WorkerReplenishVO();
        vo.setId("rep-" + row.getReplenishId());
        vo.setReplenishId(row.getReplenishId());
        vo.setPickingTaskId(row.getPickingTaskId());
        vo.setPickingLineId(row.getPickingLineId());
        vo.setWorkOrder(row.getWorkOrderNo());
        vo.setProduct(row.getProductName());
        vo.setMaterial(row.getMaterialName());
        vo.setMaterialCode(row.getMaterialCode());
        vo.setSpec(row.getSpecModel() != null ? row.getSpecModel() : "—");
        vo.setQty(toInt(row.getRequestQty()));
        vo.setReason(row.getReason());
        vo.setNote(row.getNote());
        vo.setStatus(mapReplenishStatus(row.getRequestStatus()));
        vo.setSubmittedAt(formatSubmittedAt(row.getSubmittedAt()));
        vo.setHandler(row.getHandlerName() != null ? row.getHandlerName() : "赵工");
        return vo;
    }

    private String mapReplenishStatus(String status) {
        if ("COMPLETED".equals(status)) return "已完成";
        if ("REPLENISHING".equals(status)) return "补发中";
        return "待仓管复核";
    }

    private String mapExceptionStatus(String status) {
        if ("CLOSED".equals(status)) return "已关闭";
        return "待仓管员补拣";
    }

    private String formatSubmittedAt(LocalDateTime dt) {
        if (dt == null) return "";
        if (dt.toLocalDate().isBefore(DEMO_TODAY)) {
            if (dt.toLocalDate().equals(DEMO_TODAY.minusDays(1))) {
                return "昨天 " + dt.format(TIME_FMT);
            }
            return dt.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
        }
        return dt.format(TIME_FMT);
    }

    @Override
    @Transactional
    public void startWarehousePicking(Long pickingTaskId, Long operatorId) {
        OutPickingTask task = pickingTaskMapper.selectById(pickingTaskId);
        if (task == null) {
            throw new BusinessException("拣货任务不存在");
        }
        String status = Objects.requireNonNullElse(task.getTaskStatus(), "");
        if (!Set.of("PENDING", "ASSIGNED").contains(status)) {
            throw new BusinessException("当前状态不可开始拣货: " + status);
        }
        Long op = operatorId == null ? 1L : operatorId;
        task.setAssignedTo(op);
        task.setHandlerName("沈砚");
        task.setTaskStatus("IN_PROGRESS");
        pickingTaskMapper.updateById(task);
        OutOrder order = outOrderMapper.selectById(task.getOutboundId());
        if (order != null) {
            order.setOutboundStatus("PICKING");
            outOrderMapper.updateById(order);
        }
    }

    @Override
    @Transactional
    public WorkerScanResultVO submitWarehouseScan(WarehousePickingScanRequest request) {
        int qty = request.getQty() == null || request.getQty() <= 0 ? 1 : request.getQty();
        return doWarehouseScan(
                request.getPickingTaskId(),
                request.getPickingLineId(),
                request.getOperatorId(),
                request.getBarcodeValue(),
                qty);
    }

    @Override
    @Transactional
    public OutPickingTask completeWarehousePicking(Long pickingTaskId, Long operatorId, Long targetWorkerId, Long prepLocationId) {
        OutPickingTask task = pickingTaskMapper.selectById(pickingTaskId);
        if (task == null) {
            throw new BusinessException("拣货任务不存在");
        }
        if (!Set.of("IN_PROGRESS", "PICKING").contains(Objects.requireNonNullElse(task.getTaskStatus(), ""))) {
            throw new BusinessException("请先完成 PDA 扫码拣货");
        }
        List<OutPickingLine> lines = listLines(pickingTaskId);
        boolean incomplete = lines.stream()
                .anyMatch(l -> toInt(l.getWorkerScannedQty()) < toInt(l.getPlanPickQty()));
        if (incomplete) {
            throw new BusinessException("尚有未扫齐物料，请按库位找料并完成全部扫码");
        }
        Long operator = operatorId == null ? 1L : operatorId;
        for (OutPickingLine line : lines) {
            line.setActualPickQty(line.getWorkerScannedQty());
            pickingLineMapper.updateById(line);
        }
        outboundService.transferToPrepAreaForPickingTask(pickingTaskId, prepLocationId, operator);
        List<OutPickingLine> updatedLines = listLines(pickingTaskId);
        String prepLocationCode = resolvePrepLocationCode(prepLocationId, updatedLines);
        for (OutPickingLine line : updatedLines) {
            line.setWorkerScannedQty(BigDecimal.ZERO);
            pickingLineMapper.updateById(line);
        }
        Long workerId = targetWorkerId == null ? 20L : targetWorkerId;
        TaskContext ctx = buildContext(task);
        task.setTaskStatus("STAGED");
        task.setAssignedTo(workerId);
        task.setHandlerName("沈砚");
        task.setWorkOrderNo(ctx.workOrder);
        task.setRequisitionNo(ctx.requisitionNo);
        task.setProductName(ctx.productName);
        if (task.getPlannedPickTime() == null) {
            task.setPlannedPickTime(LocalDateTime.now());
        }
        pickingTaskMapper.updateById(task);
        OutOrder order = outOrderMapper.selectById(task.getOutboundId());
        if (order != null) {
            order.setOutboundStatus("PENDING_HANDOVER");
            outOrderMapper.updateById(order);
        }
        createPrepNotification(workerId, task, ctx, prepLocationCode);
        return task;
    }

    @Override
    public List<WorkerOperatorVO> listProductionWorkers() {
        return userService.listUsersByRoleCode("WORKER").stream()
                .map(user -> {
                    WorkerOperatorVO vo = new WorkerOperatorVO();
                    vo.setId(user.getUserId());
                    vo.setCode(user.getUserCode());
                    vo.setName(user.getUserName());
                    vo.setDeptName(user.getDeptName());
                    return vo;
                })
                .toList();
    }

    @Override
    public List<WorkerNotificationVO> listNotifications(Long workerId) {
        Long assignedTo = workerId == null ? 1L : workerId;
        List<WorkerNotification> rows = notificationMapper.selectList(
                new LambdaQueryWrapper<WorkerNotification>()
                        .eq(WorkerNotification::getWorkerId, assignedTo)
                        .eq(WorkerNotification::getNotifyType, "PREP_READY")
                        .orderByDesc(WorkerNotification::getCreatedAt)
                        .last("LIMIT 1"));
        return rows.stream().map(this::toNotificationVO).toList();
    }

    @Override
    @Transactional
    public void markNotificationRead(Long notificationId, Long workerId) {
        if (notificationId == null) return;
        WorkerNotification row = notificationMapper.selectById(notificationId);
        if (row == null) return;
        if (workerId != null && !workerId.equals(row.getWorkerId())) {
            throw new BusinessException("无权操作该通知");
        }
        row.setReadFlag(1);
        notificationMapper.updateById(row);
    }

    @Override
    @Transactional
    public WorkerCompletionVO submitCompletion(WorkerCompletionSubmitRequest request) {
        if (request == null || !org.springframework.util.StringUtils.hasText(request.getWorkOrderNo())) {
            throw new BusinessException("请扫描或输入工单号");
        }
        if (!org.springframework.util.StringUtils.hasText(request.getBarcodeValue())) {
            throw new BusinessException("请扫描成品物料码");
        }
        BigDecimal qty = request.getQty() == null || request.getQty().compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.ONE : request.getQty();
        Long workerId = request.getWorkerId() == null ? 1L : request.getWorkerId();

        BarcodeScanDetailVO resolved = resolveBarcode(request.getBarcodeValue().trim());
        if (resolved.getItemId() == null) {
            throw new BusinessException("无法识别物料条码，请核对后重试");
        }
        MdItem item = itemMapper.selectById(resolved.getItemId());
        MdBatch batch = resolved.getBatchId() != null ? batchMapper.selectById(resolved.getBatchId()) : null;

        Long warehouseId = 1L;
        Long locationId = null;
        String locationCode = "WIP-A01";
        if (resolved.getInventoryId() != null) {
            InvInventory inv = inventoryMapper.selectById(resolved.getInventoryId());
            if (inv != null) {
                warehouseId = inv.getWarehouseId();
                locationId = inv.getLocationId();
                WhLocation location = locationId != null ? locationMapper.selectById(locationId) : null;
                if (location != null) locationCode = location.getLocationCode();
            }
        }
        if (locationId == null) {
            WhLocation fallback = locationMapper.selectOne(new LambdaQueryWrapper<WhLocation>()
                    .eq(WhLocation::getLocationCode, "WIP-A01")
                    .last("LIMIT 1"));
            if (fallback != null) {
                locationId = fallback.getLocationId();
                locationCode = fallback.getLocationCode();
                if (fallback.getZoneId() != null) {
                    WhZone zone = zoneMapper.selectById(fallback.getZoneId());
                    if (zone != null && zone.getWarehouseId() != null) {
                        warehouseId = zone.getWarehouseId();
                    }
                }
            }
        }

        InvInventory updated = inventoryService.increaseInventory(
                warehouseId, locationId, resolved.getItemId(), resolved.getBatchId(),
                qty, "PRODUCTION_COMPLETION", null, workerId);

        WorkerProductionCompletion row = new WorkerProductionCompletion();
        row.setWorkerId(workerId);
        row.setWorkOrderNo(request.getWorkOrderNo().trim());
        row.setItemId(resolved.getItemId());
        row.setBatchId(resolved.getBatchId());
        row.setInventoryId(updated != null ? updated.getInventoryId() : null);
        row.setMaterialName(item != null ? item.getItemName() : resolved.getMaterialName());
        row.setMaterialCode(item != null ? item.getItemCode() : resolved.getMaterialCode());
        row.setBatchNo(batch != null ? batch.getBatchNo() : resolved.getBatchNo());
        row.setQty(qty);
        row.setBarcodeValue(request.getBarcodeValue().trim());
        row.setWarehouseId(warehouseId);
        row.setLocationId(locationId);
        row.setLocationCode(locationCode);
        row.setCompletionStatus("COMPLETED");
        row.setRemark(request.getRemark());
        row.setSubmittedAt(LocalDateTime.now());
        completionMapper.insert(row);
        return toCompletionVO(row);
    }

    @Override
    public List<WorkerCompletionVO> listCompletions(Long workerId) {
        Long assignedTo = workerId == null ? 1L : workerId;
        return completionMapper.selectList(new LambdaQueryWrapper<WorkerProductionCompletion>()
                        .eq(WorkerProductionCompletion::getWorkerId, assignedTo)
                        .orderByDesc(WorkerProductionCompletion::getSubmittedAt)
                        .last("LIMIT 50"))
                .stream().map(this::toCompletionVO).toList();
    }

    @Override
    @Transactional
    public WorkerTransferVO submitTransfer(WorkerTransferSubmitRequest request) {
        if (request == null) {
            throw new BusinessException("请求不能为空");
        }
        if (!org.springframework.util.StringUtils.hasText(request.getTransferCard())
                && !org.springframework.util.StringUtils.hasText(request.getContainerCode())) {
            throw new BusinessException("请扫描流转卡或容器码");
        }
        if (!org.springframework.util.StringUtils.hasText(request.getFromProcess())
                || !org.springframework.util.StringUtils.hasText(request.getToProcess())) {
            throw new BusinessException("请指定上道工序与下道工序");
        }
        BigDecimal qty = request.getQty() == null || request.getQty().compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.ONE : request.getQty();
        Long workerId = request.getWorkerId() == null ? 1L : request.getWorkerId();

        WorkerProcessTransfer row = new WorkerProcessTransfer();
        row.setWorkerId(workerId);
        row.setTransferCard(org.springframework.util.StringUtils.hasText(request.getTransferCard())
                ? request.getTransferCard().trim() : null);
        row.setContainerCode(org.springframework.util.StringUtils.hasText(request.getContainerCode())
                ? request.getContainerCode().trim() : null);
        row.setFromProcess(request.getFromProcess().trim());
        row.setToProcess(request.getToProcess().trim());
        row.setWorkOrderNo(request.getWorkOrderNo());
        row.setQty(qty);
        row.setTransferStatus("TRANSFERRED");
        row.setRemark(request.getRemark());
        row.setSubmittedAt(LocalDateTime.now());
        transferMapper.insert(row);
        return toTransferVO(row);
    }

    @Override
    public List<WorkerTransferVO> listTransfers(Long workerId) {
        Long assignedTo = workerId == null ? 1L : workerId;
        return transferMapper.selectList(new LambdaQueryWrapper<WorkerProcessTransfer>()
                        .eq(WorkerProcessTransfer::getWorkerId, assignedTo)
                        .orderByDesc(WorkerProcessTransfer::getSubmittedAt)
                        .last("LIMIT 50"))
                .stream().map(this::toTransferVO).toList();
    }

    private void createPrepNotification(Long workerId, OutPickingTask task, TaskContext ctx, String prepLocationCode) {
        List<WorkerNotification> previous = notificationMapper.selectList(
                new LambdaQueryWrapper<WorkerNotification>()
                        .eq(WorkerNotification::getWorkerId, workerId)
                        .eq(WorkerNotification::getNotifyType, "PREP_READY")
                        .eq(WorkerNotification::getReadFlag, 0));
        for (WorkerNotification old : previous) {
            old.setReadFlag(1);
            notificationMapper.updateById(old);
        }

        WorkerNotification notification = new WorkerNotification();
        notification.setWorkerId(workerId);
        notification.setNotifyType("PREP_READY");
        notification.setTitle("备料区待领取");
        notification.setContent(String.format("仓管员已将工单 %s（%s）物料放入备料区 %s，请尽快领取。",
                ctx.workOrder, ctx.productName, prepLocationCode != null ? prepLocationCode : "备料区"));
        notification.setRelatedDoc(task.getPickingTaskNo());
        notification.setPickingTaskId(task.getPickingTaskId());
        notification.setReadFlag(0);
        notification.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(notification);
    }

    private String resolvePrepLocationCode(Long prepLocationId, List<OutPickingLine> lines) {
        if (prepLocationId != null) {
            WhLocation loc = locationMapper.selectById(prepLocationId);
            if (loc != null && loc.getLocationCode() != null) {
                return loc.getLocationCode();
            }
        }
        if (lines != null) {
            for (OutPickingLine line : lines) {
                if (line.getLocationId() != null) {
                    WhLocation loc = locationMapper.selectById(line.getLocationId());
                    if (loc != null && loc.getLocationCode() != null) {
                        return loc.getLocationCode();
                    }
                }
            }
        }
        return "备料区";
    }

    private boolean isPrepStagingTask(OutPickingTask task) {
        return Set.of("STAGED", "PAUSED", "COMPLETED").contains(
                Objects.requireNonNullElse(task.getTaskStatus(), ""));
    }

    private boolean isPrepLocationCode(String code) {
        return code != null && code.startsWith(PrepAreaService.LOCATION_CODE_PREFIX);
    }

    /** 备料区待领任务：展示/修复备料格库位，避免仍指向拣货源库位 */
    private WhLocation resolveWorkerItemLocation(OutPickingTask task, OutPickingLine line) {
        if (line.getInventoryId() != null) {
            InvInventory inv = inventoryMapper.selectById(line.getInventoryId());
            if (inv != null && inv.getLocationId() != null) {
                WhLocation loc = locationMapper.selectById(inv.getLocationId());
                if (loc != null && isPrepLocationCode(loc.getLocationCode())) {
                    return loc;
                }
            }
        }
        if (isPrepStagingTask(task)) {
            InvInventory prepInv = findPrepInventoryForLine(task, line);
            if (prepInv != null && prepInv.getLocationId() != null) {
                return locationMapper.selectById(prepInv.getLocationId());
            }
        }
        if (line.getLocationId() != null) {
            return locationMapper.selectById(line.getLocationId());
        }
        return null;
    }

    /** 修复历史脏数据：拣货行 inventory/location 被旧快照覆盖时，重新指向备料区库存 */
    private void syncStagingLinePrepReference(OutPickingTask task, OutPickingLine line) {
        BigDecimal qty = line.getActualPickQty();
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (line.getInventoryId() != null) {
            InvInventory inv = inventoryMapper.selectById(line.getInventoryId());
            if (inv != null && inv.getLocationId() != null) {
                WhLocation loc = locationMapper.selectById(inv.getLocationId());
                if (loc != null && isPrepLocationCode(loc.getLocationCode())) {
                    return;
                }
            }
        }
        InvInventory prepInv = findPrepInventoryForLine(task, line);
        if (prepInv == null) {
            return;
        }
        line.setInventoryId(prepInv.getInventoryId());
        line.setLocationId(prepInv.getLocationId());
        pickingLineMapper.updateById(line);
    }

    private InvInventory findPrepInventoryForLine(OutPickingTask task, OutPickingLine line) {
        if (line.getItemId() == null) {
            return null;
        }
        OutOrder order = outOrderMapper.selectById(task.getOutboundId());
        Long warehouseId = order != null && order.getWarehouseId() != null ? order.getWarehouseId() : 1L;
        List<Long> prepLocationIds = listPrepLocationIds(warehouseId);
        if (prepLocationIds.isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<InvInventory> query = new LambdaQueryWrapper<InvInventory>()
                .eq(InvInventory::getWarehouseId, warehouseId)
                .eq(InvInventory::getItemId, line.getItemId())
                .in(InvInventory::getLocationId, prepLocationIds)
                .gt(InvInventory::getOnhandQty, BigDecimal.ZERO)
                .orderByDesc(InvInventory::getLastTxnAt);
        if (line.getBatchId() != null) {
            query.eq(InvInventory::getBatchId, line.getBatchId());
        }
        List<InvInventory> rows = inventoryMapper.selectList(query.last("LIMIT 5"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<Long> listPrepLocationIds(Long warehouseId) {
        Long whId = warehouseId != null ? warehouseId : 1L;
        WhZone zone = zoneMapper.selectOne(new LambdaQueryWrapper<WhZone>()
                .eq(WhZone::getWarehouseId, whId)
                .eq(WhZone::getZoneCode, PrepAreaService.ZONE_CODE)
                .last("LIMIT 1"));
        if (zone == null) {
            zone = zoneMapper.selectOne(new LambdaQueryWrapper<WhZone>()
                    .eq(WhZone::getWarehouseId, whId)
                    .eq(WhZone::getZoneCode, PrepAreaService.LEGACY_ZONE_CODE)
                    .last("LIMIT 1"));
        }
        if (zone == null) {
            return List.of();
        }
        return locationMapper.selectList(new LambdaQueryWrapper<WhLocation>()
                        .eq(WhLocation::getZoneId, zone.getZoneId())
                        .likeRight(WhLocation::getLocationCode, PrepAreaService.LOCATION_CODE_PREFIX))
                .stream()
                .map(WhLocation::getLocationId)
                .toList();
    }

    private WorkerNotificationVO toNotificationVO(WorkerNotification row) {
        WorkerNotificationVO vo = new WorkerNotificationVO();
        vo.setId(row.getNotificationId());
        vo.setType(row.getNotifyType());
        vo.setTitle(row.getTitle());
        vo.setContent(row.getContent());
        vo.setRelatedDoc(row.getRelatedDoc());
        vo.setPickingTaskId(row.getPickingTaskId());
        vo.setRead(row.getReadFlag() != null && row.getReadFlag() == 1);
        vo.setCreatedAt(row.getCreatedAt() != null ? row.getCreatedAt().format(DT_FMT) : "");
        return vo;
    }

    private WorkerCompletionVO toCompletionVO(WorkerProductionCompletion row) {
        WorkerCompletionVO vo = new WorkerCompletionVO();
        vo.setId(row.getCompletionId());
        vo.setWorkOrderNo(row.getWorkOrderNo());
        vo.setMaterialName(row.getMaterialName());
        vo.setMaterialCode(row.getMaterialCode());
        vo.setBatchNo(row.getBatchNo());
        vo.setQty(row.getQty());
        vo.setLocationCode(row.getLocationCode());
        vo.setStatus("已入库");
        vo.setSubmittedAt(row.getSubmittedAt() != null ? row.getSubmittedAt().format(DT_FMT) : "");
        return vo;
    }

    private WorkerTransferVO toTransferVO(WorkerProcessTransfer row) {
        WorkerTransferVO vo = new WorkerTransferVO();
        vo.setId(row.getTransferId());
        vo.setTransferCard(row.getTransferCard());
        vo.setContainerCode(row.getContainerCode());
        vo.setFromProcess(row.getFromProcess());
        vo.setToProcess(row.getToProcess());
        vo.setWorkOrderNo(row.getWorkOrderNo());
        vo.setQty(row.getQty());
        vo.setStatus("已流转");
        vo.setSubmittedAt(row.getSubmittedAt() != null ? row.getSubmittedAt().format(DT_FMT) : "");
        return vo;
    }

    private WorkerScanResultVO doWarehouseScan(Long pickingTaskId, Long pickingLineId, Long operatorId,
                                               String barcodeValue, int qty) {
        OutPickingTask task = pickingTaskMapper.selectById(pickingTaskId);
        if (task == null) {
            throw new BusinessException("拣货任务不存在");
        }
        if (!Set.of("IN_PROGRESS", "PICKING").contains(Objects.requireNonNullElse(task.getTaskStatus(), ""))) {
            throw new BusinessException("请先开始 PDA 拣货");
        }
        OutPickingLine line = requireLine(pickingLineId, task.getPickingTaskId());
        MdItem expectedItem = itemMapper.selectById(line.getItemId());
        MdBatch expectedBatch = line.getBatchId() != null ? batchMapper.selectById(line.getBatchId()) : null;

        BarcodeScanDetailVO resolved = resolveBarcode(barcodeValue);
        List<String> messages = new ArrayList<>();
        boolean success = true;

        if (resolved.getMaterialName() == null && resolved.getItemId() == null) {
            ParsedBarcode parsed = parseBarcode(barcodeValue);
            if (parsed.materialCode != null && expectedItem != null) {
                resolved.setMaterialName(expectedItem.getItemName());
            }
        }

        String resolvedMaterial = resolved.getMaterialName() != null
                ? resolved.getMaterialName()
                : (expectedItem != null ? expectedItem.getItemName() : "");
        String resolvedBatch = resolved.getBatchNo() != null
                ? resolved.getBatchNo()
                : (expectedBatch != null ? expectedBatch.getBatchNo() : "");

        if (expectedItem != null && resolved.getItemId() != null && !expectedItem.getItemId().equals(resolved.getItemId())) {
            success = false;
            messages.add("× 物料不匹配");
        } else if (expectedItem != null && resolvedMaterial.equals(expectedItem.getItemName())) {
            messages.add("✓ 物料匹配");
        } else if (expectedItem == null || !resolvedMaterial.equals(expectedItem.getItemName())) {
            success = false;
            messages.add("× 物料不匹配");
        } else {
            messages.add("✓ 物料匹配");
        }

        if (expectedBatch != null && resolvedBatch != null && !expectedBatch.getBatchNo().equals(resolvedBatch)) {
            success = false;
            messages.add("× 批次不在领料范围");
        } else if (expectedBatch != null) {
            messages.add("✓ 批次匹配");
        }

        if ("UNQUALIFIED".equalsIgnoreCase(resolved.getQualityStatus()) || barcodeValue.contains("QC-FAIL")) {
            success = false;
            messages.add("× 该批次质量状态异常");
        }

        int required = toInt(line.getPlanPickQty());
        int scanned = toInt(line.getWorkerScannedQty());
        if (scanned >= required) {
            success = false;
            messages.add("× 数量超过应拣数量");
        } else if (success) {
            int next = Math.min(required, scanned + qty);
            line.setWorkerScannedQty(BigDecimal.valueOf(next));
            pickingLineMapper.updateById(line);
            messages.add("✓ 数量 +" + (next - scanned));
        }

        Long opId = operatorId == null ? task.getAssignedTo() : operatorId;
        TaskContext ctx = buildContext(task);
        WorkerScanRecord record = new WorkerScanRecord();
        record.setPickingTaskId(task.getPickingTaskId());
        record.setPickingLineId(line.getPickingLineId());
        record.setWorkerId(opId);
        record.setBarcodeValue(barcodeValue);
        record.setItemId(line.getItemId());
        record.setBatchId(line.getBatchId());
        record.setMaterialName(resolvedMaterial);
        record.setBatchNo(resolvedBatch);
        record.setScanResult(success ? "SUCCESS" : "FAILED");
        record.setResultMessage(messages.stream().filter(m -> m.startsWith("×")).findFirst()
                .orElse("成功").replace("× ", "").replace("✓ ", ""));
        record.setScannedAt(LocalDateTime.now());
        scanRecordMapper.insert(record);

        WorkerScanResultVO result = new WorkerScanResultVO();
        result.setSuccess(success);
        result.setMessages(messages);
        WorkerScanRecordVO scanVO = toScanVO(record);
        scanVO.setWorkOrder(ctx.workOrder);
        result.setRecord(scanVO);
        return result;
    }

    private OutPickingTask requireTask(Long pickingTaskId, Long workerId) {
        if (pickingTaskId == null) throw new BusinessException("缺少拣货任务");
        OutPickingTask task = pickingTaskMapper.selectById(pickingTaskId);
        if (task == null) throw new BusinessException("拣货任务不存在");
        if (workerId != null && task.getAssignedTo() != null && !task.getAssignedTo().equals(workerId)) {
            throw new BusinessException("无权操作该拣货任务");
        }
        return task;
    }

    private OutPickingLine requireLine(Long pickingLineId, Long pickingTaskId) {
        if (pickingLineId == null) throw new BusinessException("缺少拣货明细行");
        OutPickingLine line = pickingLineMapper.selectById(pickingLineId);
        if (line == null || !pickingTaskId.equals(line.getPickingTaskId())) {
            throw new BusinessException("拣货明细不存在");
        }
        return line;
    }

    private List<OutPickingLine> listLines(Long pickingTaskId) {
        return pickingLineMapper.selectList(new LambdaQueryWrapper<OutPickingLine>()
                .eq(OutPickingLine::getPickingTaskId, pickingTaskId));
    }

    private TaskContext buildContext(OutPickingTask task) {
        TaskContext ctx = new TaskContext();
        if (task.getWorkOrderNo() != null && !task.getWorkOrderNo().isBlank()) {
            ctx.workOrder = task.getWorkOrderNo();
        }
        if (task.getRequisitionNo() != null && !task.getRequisitionNo().isBlank()) {
            ctx.requisitionNo = task.getRequisitionNo();
        }
        if (task.getProductName() != null && !task.getProductName().isBlank()) {
            ctx.productName = task.getProductName();
        }
        OutOrder order = outOrderMapper.selectById(task.getOutboundId());
        if (order != null && order.getRequisitionId() != null) {
            PmcRequisitionOrder req = requisitionMapper.selectById(order.getRequisitionId());
            if (req != null) {
                if (ctx.requisitionNo == null) ctx.requisitionNo = req.getRequisitionNo();
                if (req.getSourcePlanId() != null) {
                    PmcProductionPlan plan = planMapper.selectById(req.getSourcePlanId());
                    if (plan != null) {
                        if (ctx.workOrder == null) {
                            ctx.workOrder = plan.getMesPlanNo() != null ? plan.getMesPlanNo() : plan.getPlanNo();
                        }
                        if (plan.getPlannedStartDate() != null) {
                            ctx.planDate = plan.getPlannedStartDate().format(DATE_FMT);
                        }
                        if (ctx.productName == null) {
                            List<OutPickingLine> lines = listLines(task.getPickingTaskId());
                            if (!lines.isEmpty()) {
                                MdItem first = itemMapper.selectById(lines.get(0).getItemId());
                                ctx.productName = first != null ? first.getItemName() + " 等" : plan.getPlanNo();
                            } else {
                                ctx.productName = plan.getPlanNo();
                            }
                        }
                    }
                }
            }
        }
        if (ctx.workOrder == null) {
            if (task.getPickingTaskNo() != null && !task.getPickingTaskNo().isBlank()) {
                ctx.workOrder = task.getPickingTaskNo();
            } else {
                ctx.workOrder = "WO" + task.getPickingTaskId();
            }
        }
        if (ctx.requisitionNo == null) ctx.requisitionNo = "REQ" + task.getPickingTaskId();
        if (ctx.productName == null) ctx.productName = "生产领料";
        if (task.getPlannedPickTime() != null) {
            ctx.expectedTime = task.getPlannedPickTime().format(TIME_FMT);
            if (ctx.planDate == null) ctx.planDate = task.getPlannedPickTime().toLocalDate().format(DATE_FMT);
        } else if (task.getCreatedAt() != null) {
            ctx.expectedTime = task.getCreatedAt().format(TIME_FMT);
            ctx.planDate = task.getCreatedAt().toLocalDate().format(DATE_FMT);
        } else {
            ctx.expectedTime = "09:00";
            ctx.planDate = DEMO_TODAY.format(DATE_FMT);
        }
        return ctx;
    }

    private int toInt(BigDecimal val) {
        if (val == null) return 0;
        return val.setScale(0, RoundingMode.DOWN).intValue();
    }

    private ParsedBarcode parseBarcode(String value) {
        ParsedBarcode p = new ParsedBarcode();
        if (value == null) return p;
        String[] parts = value.trim().split("-");
        if (parts.length >= 3) {
            p.batchNo = parts[0];
            p.materialCode = parts[1];
        }
        return p;
    }

    private static class TaskContext {
        String workOrder;
        String requisitionNo;
        String productName;
        String planDate;
        String expectedTime;
    }

    private static class ParsedBarcode {
        String batchNo;
        String materialCode;
    }
}
