package com.upc.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.upc.wms.common.BusinessException;
import com.upc.wms.common.PlanNoFormatter;
import com.upc.wms.dto.*;
import com.upc.wms.entity.*;
import com.upc.wms.mapper.*;
import com.upc.wms.service.OutGenerateExceptionService;
import com.upc.wms.service.PmcCoordinationService;
import com.upc.wms.service.WarehouseWorkbenchService;
import com.upc.wms.service.WorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WarehouseWorkbenchServiceImpl implements WarehouseWorkbenchService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final PmcRequisitionOrderMapper requisitionMapper;
    private final PmcRequisitionLineMapper requisitionLineMapper;
    private final PmcProductionPlanMapper planMapper;
    private final OutOrderMapper outOrderMapper;
    private final OutOrderLineMapper outOrderLineMapper;
    private final OutPickingTaskMapper pickingTaskMapper;
    private final OutPickingLineMapper pickingLineMapper;
    private final OutReviewExceptionMapper reviewExceptionMapper;
    private final WorkerExceptionMapper workerExceptionMapper;
    private final OutGenerateExceptionService outGenerateExceptionService;
    private final MdItemMapper itemMapper;
    private final MdUomMapper uomMapper;
    private final MdBatchMapper batchMapper;
    private final WhWarehouseMapper warehouseMapper;
    private final WhLocationMapper locationMapper;
    private final WhZoneMapper zoneMapper;
    private final SysUserMapper userMapper;
    private final WorkerService workerService;
    private final PmcCoordinationService pmcCoordinationService;

    @Override
    public WarehouseWorkbenchVO getOverview() {
        Map<Long, MdItem> itemMap = itemMapper.selectList(null).stream()
                .collect(Collectors.toMap(MdItem::getItemId, i -> i, (a, b) -> a));
        Map<Long, String> uomNames = uomMapper.selectList(null).stream()
                .collect(Collectors.toMap(MdUom::getUomId, MdUom::getUomName, (a, b) -> a));
        Map<Long, String> batchNos = batchMapper.selectList(null).stream()
                .collect(Collectors.toMap(MdBatch::getBatchId, MdBatch::getBatchNo, (a, b) -> a));
        Map<Long, WhLocation> locationMap = locationMapper.selectList(null).stream()
                .collect(Collectors.toMap(WhLocation::getLocationId, l -> l, (a, b) -> a));
        Map<Long, WhZone> zoneMap = zoneMapper.selectList(null).stream()
                .collect(Collectors.toMap(WhZone::getZoneId, z -> z, (a, b) -> a));
        Map<Long, String> warehouseNames = warehouseMapper.selectList(null).stream()
                .collect(Collectors.toMap(WhWarehouse::getWarehouseId, WhWarehouse::getWarehouseName, (a, b) -> a));
        Map<Long, String> userNames = userMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysUser::getUserId, SysUser::getUserName, (a, b) -> a));

        List<OutOrder> allOrders = outOrderMapper.selectList(
                new LambdaQueryWrapper<OutOrder>().orderByDesc(OutOrder::getOutboundId));
        Set<Long> requisitionIdsWithOutbound = allOrders.stream()
                .map(OutOrder::getRequisitionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<WarehousePendingRequisitionVO> pending = buildPendingRequisitions(
                requisitionIdsWithOutbound, itemMap, uomNames, warehouseNames);
        List<WarehouseOutboundOrderVO> outboundOrders = buildOutboundOrders(
                allOrders, itemMap, batchNos, locationMap, warehouseNames);
        List<WarehousePickingTaskVO> pickingTasks = buildPickingTasks(
                allOrders, itemMap, batchNos, locationMap, zoneMap, userNames);
        List<WarehouseExceptionVO> exceptions = buildExceptions(allOrders);
        markPendingWithGenerateException(pending, exceptions);

        WarehouseWorkbenchVO vo = new WarehouseWorkbenchVO();
        vo.setPendingRequisitions(pending);
        vo.setOutboundOrders(outboundOrders);
        vo.setPickingTasks(pickingTasks);
        vo.setExceptions(exceptions);
        vo.setCoordinationNotices(pmcCoordinationService.listByTarget("WAREHOUSE", null));
        return vo;
    }

    @Override
    public WarehouseExceptionVO resolveException(String exceptionId, String result) {
        if (!StringUtils.hasText(exceptionId)) {
            throw new BusinessException("缺少异常编号");
        }
        if (exceptionId.startsWith("OEX")) {
            Long id = parseExceptionNumericId(exceptionId, "OEX");
            OutGenerateException ex = outGenerateExceptionService.resolve(id, result);
            return toGenerateExceptionVO(ex);
        }
        if (exceptionId.startsWith("WEX")) {
            throw new BusinessException("工人拣货异常请在拣货协同中处理，暂不支持在此直接关闭");
        }
        if (exceptionId.startsWith("REX")) {
            throw new BusinessException("复核异常请在复核流程中处理，暂不支持在此直接关闭");
        }
        throw new BusinessException("无法识别的异常编号: " + exceptionId);
    }

    private List<WarehousePendingRequisitionVO> buildPendingRequisitions(
            Set<Long> requisitionIdsWithOutbound,
            Map<Long, MdItem> itemMap,
            Map<Long, String> uomNames,
            Map<Long, String> warehouseNames) {
        List<PmcRequisitionOrder> reqs = requisitionMapper.selectList(
                new LambdaQueryWrapper<PmcRequisitionOrder>()
                        .in(PmcRequisitionOrder::getRequisitionStatus, "PENDING_OUTBOUND", "PARTIAL_PENDING")
                        .orderByDesc(PmcRequisitionOrder::getRequisitionId));

        List<WarehousePendingRequisitionVO> rows = new ArrayList<>();
        for (PmcRequisitionOrder req : reqs) {
            if (requisitionIdsWithOutbound.contains(req.getRequisitionId())) {
                continue;
            }
            List<PmcRequisitionLine> lines = requisitionLineMapper.selectList(
                    new LambdaQueryWrapper<PmcRequisitionLine>()
                            .eq(PmcRequisitionLine::getRequisitionId, req.getRequisitionId()));
            PlanContext planCtx = resolvePlanContext(req);

            WarehousePendingRequisitionVO row = new WarehousePendingRequisitionVO();
            row.setId(req.getRequisitionNo());
            row.setRequisitionId(req.getRequisitionId());
            row.setPlan(planCtx.planNo);
            row.setPlanId(planCtx.planId);
            row.setWorkOrder(planCtx.workOrder);
            row.setItemCount(lines.size());
            row.setDemandDate(resolveDemandDate(req, lines));
            row.setStatus("待生成出库单");
            row.setWarehouse(warehouseNames.getOrDefault(1L, "主仓库"));
            row.setMaterials(lines.stream().map(line -> toMaterial(line, itemMap, uomNames)).toList());
            rows.add(row);
        }
        return rows;
    }

    private List<WarehouseOutboundOrderVO> buildOutboundOrders(
            List<OutOrder> allOrders,
            Map<Long, MdItem> itemMap,
            Map<Long, String> batchNos,
            Map<Long, WhLocation> locationMap,
            Map<Long, String> warehouseNames) {
        List<WarehouseOutboundOrderVO> rows = new ArrayList<>();
        for (OutOrder order : allOrders) {
            PmcRequisitionOrder req = order.getRequisitionId() == null
                    ? null : requisitionMapper.selectById(order.getRequisitionId());
            PlanContext planCtx = resolvePlanContext(req);
            OutPickingTask picking = pickingTaskMapper.selectOne(
                    new LambdaQueryWrapper<OutPickingTask>()
                            .eq(OutPickingTask::getOutboundId, order.getOutboundId())
                            .orderByDesc(OutPickingTask::getPickingTaskId)
                            .last("LIMIT 1"));

            List<OutOrderLine> orderLines = outOrderLineMapper.selectList(
                    new LambdaQueryWrapper<OutOrderLine>().eq(OutOrderLine::getOutboundId, order.getOutboundId()));
            List<OutPickingLine> pickLines = picking == null ? List.of() : pickingLineMapper.selectList(
                    new LambdaQueryWrapper<OutPickingLine>()
                            .eq(OutPickingLine::getPickingTaskId, picking.getPickingTaskId()));
            Map<Long, OutPickingLine> pickByOutboundLine = pickLines.stream()
                    .filter(pl -> pl.getOutboundLineId() != null)
                    .collect(Collectors.toMap(OutPickingLine::getOutboundLineId, pl -> pl, (a, b) -> a));

            List<WarehouseOutboundLineVO> lines = new ArrayList<>();
            if (!orderLines.isEmpty()) {
                for (OutOrderLine ol : orderLines) {
                    OutPickingLine pl = pickByOutboundLine.get(ol.getOutboundLineId());
                    lines.add(toOutboundLine(ol, pl, itemMap, batchNos, locationMap));
                }
            } else if (!pickLines.isEmpty()) {
                for (OutPickingLine pl : pickLines) {
                    lines.add(toOutboundLineFromPick(pl, itemMap, batchNos, locationMap));
                }
            } else if (req != null) {
                List<PmcRequisitionLine> reqLines = requisitionLineMapper.selectList(
                        new LambdaQueryWrapper<PmcRequisitionLine>()
                                .eq(PmcRequisitionLine::getRequisitionId, req.getRequisitionId()));
                for (PmcRequisitionLine rl : reqLines) {
                    WarehouseOutboundLineVO line = new WarehouseOutboundLineVO();
                    MdItem item = itemMap.get(rl.getItemId());
                    line.setName(item != null ? item.getItemName() : "物料#" + rl.getItemId());
                    line.setRequired(toInt(rl.getRequiredQty()));
                    line.setActual(0);
                    line.setBatch("—");
                    line.setLocation("—");
                    line.setStatus(mapLineStatus(order.getOutboundStatus(), 0, toInt(rl.getRequiredQty())));
                    lines.add(line);
                }
            }

            WarehouseOutboundOrderVO row = new WarehouseOutboundOrderVO();
            row.setId(order.getOutboundNo());
            row.setOutboundId(order.getOutboundId());
            row.setRequisition(req != null ? req.getRequisitionNo() : "—");
            row.setRequisitionId(order.getRequisitionId());
            row.setWorkOrder(planCtx.workOrder);
            row.setItemCount(lines.size());
            row.setStatus(mapOutboundStatus(order.getOutboundStatus(), picking));
            row.setCreatedAt(formatDateTime(order.getApprovedAt()));
            row.setWarehouse(warehouseNames.getOrDefault(order.getWarehouseId(), "主仓库"));
            row.setPickingTaskId(picking != null ? picking.getPickingTaskNo() : null);
            row.setPickingTaskDbId(picking != null ? picking.getPickingTaskId() : null);
            row.setLines(lines);
            row.setGenerationLog(List.of(
                    "出库智能体接收领料单 " + row.getRequisition(),
                    "库存智能体完成库存校验与批次库位推荐",
                    "出库智能体生成出库单 " + order.getOutboundNo() + " 与出库明细",
                    "审计智能体记录生成过程"
            ));
            rows.add(row);
        }
        return rows;
    }

    private List<WarehousePickingTaskVO> buildPickingTasks(
            List<OutOrder> allOrders,
            Map<Long, MdItem> itemMap,
            Map<Long, String> batchNos,
            Map<Long, WhLocation> locationMap,
            Map<Long, WhZone> zoneMap,
            Map<Long, String> userNames) {
        Map<Long, OutOrder> orderById = allOrders.stream()
                .collect(Collectors.toMap(OutOrder::getOutboundId, o -> o, (a, b) -> a));
        List<OutPickingTask> tasks = pickingTaskMapper.selectList(
                new LambdaQueryWrapper<OutPickingTask>().orderByDesc(OutPickingTask::getPickingTaskId));

        List<WarehousePickingTaskVO> rows = new ArrayList<>();
        for (OutPickingTask task : tasks) {
            OutOrder order = orderById.get(task.getOutboundId());
            PmcRequisitionOrder req = order != null && order.getRequisitionId() != null
                    ? requisitionMapper.selectById(order.getRequisitionId()) : null;
            PlanContext planCtx = resolvePlanContext(req);

            List<OutPickingLine> pickLines = pickingLineMapper.selectList(
                    new LambdaQueryWrapper<OutPickingLine>()
                            .eq(OutPickingLine::getPickingTaskId, task.getPickingTaskId()));
            List<WarehousePickingLineVO> lines = new ArrayList<>();
            int done = 0;
            String zoneName = "—";
            for (OutPickingLine pl : pickLines) {
                WarehousePickingLineVO line = toPickingLine(pl, itemMap, batchNos, locationMap);
                lines.add(line);
                if ("已扫码".equals(line.getScanStatus())) {
                    done++;
                }
                if ("—".equals(zoneName) && pl.getLocationId() != null) {
                    WhLocation loc = locationMap.get(pl.getLocationId());
                    if (loc != null && loc.getZoneId() != null) {
                        WhZone zone = zoneMap.get(loc.getZoneId());
                        if (zone != null) {
                            zoneName = zone.getZoneName() != null ? zone.getZoneName() : zone.getZoneCode();
                        }
                    }
                }
            }

            WarehousePickingTaskVO row = new WarehousePickingTaskVO();
            row.setId(task.getPickingTaskNo());
            row.setPickingTaskId(task.getPickingTaskId());
            row.setOutbound(order != null ? order.getOutboundNo() : "—");
            row.setOutboundId(task.getOutboundId());
            row.setWorkOrder(planCtx.workOrder);
            row.setItemCount(lines.size());
            row.setZone(zoneName);
            row.setStatus(mapPickingStatus(task.getTaskStatus(), order));
            row.setAssignedTo(task.getAssignedTo());
            row.setAssigneeName(resolveAssigneeName(task, userNames));
            row.setProgress(Map.of("done", done, "total", Math.max(lines.size(), 1)));
            row.setLines(lines);
            rows.add(row);
        }
        return rows;
    }

    private List<WarehouseExceptionVO> buildExceptions(List<OutOrder> allOrders) {
        List<WarehouseExceptionVO> rows = new ArrayList<>();

        for (OutGenerateException ex : outGenerateExceptionService.listAll()) {
            rows.add(toGenerateExceptionVO(ex));
        }

        List<WorkerException> workerExceptions = workerExceptionMapper.selectList(
                new LambdaQueryWrapper<WorkerException>().orderByDesc(WorkerException::getSubmittedAt));
        for (WorkerException ex : workerExceptions) {
            WarehouseExceptionVO row = new WarehouseExceptionVO();
            row.setId("WEX" + ex.getExceptionId());
            row.setExceptionId(ex.getExceptionId());
            row.setSource("WORKER");
            row.setRelatedDoc(ex.getRequisitionNo() != null ? ex.getRequisitionNo() : ex.getWorkOrderNo());
            row.setRelatedType("requisition");
            row.setStage("拣货");
            row.setType(ex.getExceptionType() != null ? ex.getExceptionType() : "数量不一致");
            row.setDescription(ex.getExceptionNote() != null ? ex.getExceptionNote() : "工人反馈异常");
            row.setMaterial(ex.getMaterialName());
            row.setSuggestion("核对库位实物与推荐批次，确认后补拣或登记短缺。");
            row.setResult("");
            row.setStatus(mapWorkerExceptionStatus(ex.getExceptionStatus()));
            row.setCreatedAt(formatDateTime(ex.getSubmittedAt()));
            rows.add(row);
        }

        List<OutReviewException> reviewExceptions = reviewExceptionMapper.selectList(
                new LambdaQueryWrapper<OutReviewException>().orderByDesc(OutReviewException::getExceptionId));
        for (OutReviewException ex : reviewExceptions) {
            WarehouseExceptionVO row = new WarehouseExceptionVO();
            row.setId("REX" + ex.getExceptionId());
            row.setExceptionId(ex.getExceptionId());
            row.setSource("REVIEW");
            row.setRelatedDoc(resolveReviewRelatedDoc(ex, allOrders));
            row.setRelatedType("outbound");
            row.setStage("复核");
            row.setType(ex.getExceptionType() != null ? ex.getExceptionType() : "批次不一致");
            row.setDescription(ex.getExceptionDesc() != null ? ex.getExceptionDesc() : "复核异常");
            row.setMaterial("");
            row.setSuggestion("以实物扫码为准更新明细，或退回重新拣货。");
            row.setResult("");
            row.setStatus(mapReviewExceptionStatus(ex.getExceptionStatus()));
            row.setCreatedAt(formatDateTime(ex.getCreatedAt()));
            rows.add(row);
        }

        rows.sort((a, b) -> String.valueOf(b.getCreatedAt()).compareTo(String.valueOf(a.getCreatedAt())));
        return rows;
    }

    private WarehouseExceptionVO toGenerateExceptionVO(OutGenerateException ex) {
        WarehouseExceptionVO row = new WarehouseExceptionVO();
        row.setId("OEX" + ex.getExceptionId());
        row.setExceptionId(ex.getExceptionId());
        row.setSource("GENERATE");
        row.setRelatedDoc(ex.getRequisitionNo() != null ? ex.getRequisitionNo() : "REQ-" + ex.getRequisitionId());
        row.setRelatedType("requisition");
        row.setStage("出库生成");
        row.setType(mapGenerateExceptionType(ex.getExceptionType()));
        row.setDescription(ex.getExceptionDesc() != null ? ex.getExceptionDesc() : "出库单生成失败");
        row.setMaterial(ex.getMaterialName() != null ? ex.getMaterialName() : "");
        if (ex.getShortageQty() != null && ex.getShortageQty().compareTo(BigDecimal.ZERO) > 0) {
            String material = StringUtils.hasText(row.getMaterial()) ? row.getMaterial() : "物料";
            row.setSuggestion("请先完成 " + material + " 补货/调拨（缺 "
                    + ex.getShortageQty().stripTrailingZeros().toPlainString()
                    + "），再回出库单页重新生成出库单。");
        } else {
            row.setSuggestion("请核对库存与领料明细后，回出库单页重新点击「生成出库单」。");
        }
        row.setResult(ex.getResolveResult() != null ? ex.getResolveResult() : "");
        row.setStatus(mapGenerateExceptionStatus(ex.getExceptionStatus()));
        row.setCreatedAt(formatDateTime(ex.getCreatedAt()));
        row.setRequisitionId(ex.getRequisitionId());
        row.setPlanId(ex.getPlanId());
        row.setPlanNo(ex.getPlanNo());
        row.setAgentTaskId(ex.getAgentTaskId());
        return row;
    }

    private void markPendingWithGenerateException(List<WarehousePendingRequisitionVO> pending,
                                                  List<WarehouseExceptionVO> exceptions) {
        if (pending == null || pending.isEmpty() || exceptions == null) {
            return;
        }
        Set<Long> openReqIds = exceptions.stream()
                .filter(e -> "GENERATE".equals(e.getSource()) && "待处理".equals(e.getStatus()))
                .map(WarehouseExceptionVO::getRequisitionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (openReqIds.isEmpty()) {
            return;
        }
        for (WarehousePendingRequisitionVO row : pending) {
            if (row.getRequisitionId() != null && openReqIds.contains(row.getRequisitionId())) {
                row.setStatus("生成失败·待处理");
            }
        }
    }

    private Long parseExceptionNumericId(String exceptionId, String prefix) {
        try {
            return Long.parseLong(exceptionId.substring(prefix.length()));
        } catch (Exception e) {
            throw new BusinessException("异常编号格式错误: " + exceptionId);
        }
    }

    private String mapGenerateExceptionType(String type) {
        return switch (Objects.requireNonNullElse(type, "")) {
            case "SHORTAGE" -> "库存不足";
            case "GENERATE_FAILED" -> "生成失败";
            default -> StringUtils.hasText(type) ? type : "库存不足";
        };
    }

    private String mapGenerateExceptionStatus(String status) {
        return switch (Objects.requireNonNullElse(status, "")) {
            case "CLOSED", "RESOLVED" -> "已处理";
            default -> "待处理";
        };
    }

    private WarehouseMaterialVO toMaterial(PmcRequisitionLine line, Map<Long, MdItem> itemMap, Map<Long, String> uomNames) {
        MdItem item = itemMap.get(line.getItemId());
        WarehouseMaterialVO vo = new WarehouseMaterialVO();
        vo.setName(item != null ? item.getItemName() : "物料#" + line.getItemId());
        vo.setCode(item != null ? item.getItemCode() : "");
        vo.setQty(toInt(line.getRequiredQty()));
        String unit = "件";
        if (item != null && item.getUomId() != null) {
            unit = uomNames.getOrDefault(item.getUomId(), "件");
        }
        vo.setUnit(unit);
        return vo;
    }

    private WarehouseOutboundLineVO toOutboundLine(OutOrderLine ol, OutPickingLine pl,
                                                   Map<Long, MdItem> itemMap,
                                                   Map<Long, String> batchNos,
                                                   Map<Long, WhLocation> locationMap) {
        MdItem item = itemMap.get(ol.getItemId());
        int required = toInt(ol.getPlanQty());
        int actual = pl != null ? Math.max(toInt(pl.getActualPickQty()), toInt(pl.getWorkerScannedQty()))
                : toInt(ol.getPickedQty());
        WarehouseOutboundLineVO line = new WarehouseOutboundLineVO();
        line.setName(item != null ? item.getItemName() : "物料#" + ol.getItemId());
        line.setRequired(required);
        line.setActual(actual);
        line.setBatch(pl != null && pl.getBatchId() != null ? batchNos.getOrDefault(pl.getBatchId(), "—") : "—");
        line.setLocation(resolveLocationCode(pl != null ? pl.getLocationId() : null, locationMap));
        line.setStatus(mapLineStatus(null, actual, required));
        return line;
    }

    private WarehouseOutboundLineVO toOutboundLineFromPick(OutPickingLine pl,
                                                           Map<Long, MdItem> itemMap,
                                                           Map<Long, String> batchNos,
                                                           Map<Long, WhLocation> locationMap) {
        MdItem item = itemMap.get(pl.getItemId());
        int required = toInt(pl.getPlanPickQty());
        int actual = Math.max(toInt(pl.getActualPickQty()), toInt(pl.getWorkerScannedQty()));
        WarehouseOutboundLineVO line = new WarehouseOutboundLineVO();
        line.setName(item != null ? item.getItemName() : "物料#" + pl.getItemId());
        line.setRequired(required);
        line.setActual(actual);
        line.setBatch(pl.getBatchId() != null ? batchNos.getOrDefault(pl.getBatchId(), "—") : "—");
        line.setLocation(resolveLocationCode(pl.getLocationId(), locationMap));
        line.setStatus(mapLineStatus(null, actual, required));
        return line;
    }

    private WarehousePickingLineVO toPickingLine(OutPickingLine pl,
                                                 Map<Long, MdItem> itemMap,
                                                 Map<Long, String> batchNos,
                                                 Map<Long, WhLocation> locationMap) {
        MdItem item = itemMap.get(pl.getItemId());
        int required = toInt(pl.getPlanPickQty());
        int picked = Math.max(toInt(pl.getActualPickQty()), toInt(pl.getWorkerScannedQty()));
        WarehousePickingLineVO line = new WarehousePickingLineVO();
        line.setId("pl-" + pl.getPickingLineId());
        line.setPickingLineId(pl.getPickingLineId());
        line.setName(item != null ? item.getItemName() : "物料#" + pl.getItemId());
        line.setRequired(required);
        line.setPicked(picked);
        line.setBatch(pl.getBatchId() != null ? batchNos.getOrDefault(pl.getBatchId(), "—") : "—");
        line.setLocation(resolveLocationCode(pl.getLocationId(), locationMap));
        if (picked >= required && required > 0) {
            line.setScanStatus("已扫码");
        } else if (picked > 0) {
            line.setScanStatus("部分扫码");
        } else {
            line.setScanStatus("未扫码");
        }
        return line;
    }

    private PlanContext resolvePlanContext(PmcRequisitionOrder req) {
        PlanContext ctx = new PlanContext();
        ctx.planNo = "—";
        ctx.workOrder = "—";
        if (req == null) {
            return ctx;
        }
        Long planId = req.getSourcePlanId();
        if (planId != null) {
            PmcProductionPlan plan = planMapper.selectById(planId);
            if (plan != null) {
                ctx.planId = plan.getPlanId();
                ctx.planNo = PlanNoFormatter.display(plan.getPlanNo(), plan.getPlanId());
                ctx.workOrder = plan.getMesPlanNo() != null && !plan.getMesPlanNo().isBlank()
                        ? plan.getMesPlanNo()
                        : "WO" + plan.getPlanId();
            }
        }
        if ("—".equals(ctx.workOrder)) {
            ctx.workOrder = "WO-REQ" + req.getRequisitionId();
        }
        return ctx;
    }

    private String resolveDemandDate(PmcRequisitionOrder req, List<PmcRequisitionLine> lines) {
        for (PmcRequisitionLine line : lines) {
            if (line.getRequiredAt() != null) {
                return line.getRequiredAt().toLocalDate().format(DATE_FMT);
            }
        }
        if (req.getRequestedAt() != null) {
            return req.getRequestedAt().toLocalDate().format(DATE_FMT);
        }
        return "—";
    }

    private String resolveLocationCode(Long locationId, Map<Long, WhLocation> locationMap) {
        if (locationId == null) {
            return "—";
        }
        WhLocation loc = locationMap.get(locationId);
        return loc != null ? loc.getLocationCode() : "—";
    }

    private String mapOutboundStatus(String status, OutPickingTask picking) {
        String s = Objects.requireNonNullElse(status, "");
        return switch (s) {
            case "PENDING_PICK" -> "待拣货";
            case "PICKING" -> "拣货中";
            case "PENDING_REVIEW", "REVIEWING" -> "待复核";
            case "REVIEWED", "PENDING_HANDOVER" -> "待交接";
            case "COMPLETED" -> "已完成";
            case "SHORTAGE_HOLD", "EXCEPTION" -> "异常";
            default -> {
                if (picking != null) {
                    yield mapPickingStatus(picking.getTaskStatus(), null);
                }
                yield s.isBlank() ? "待拣货" : s;
            }
        };
    }

    private String mapPickingStatus(String taskStatus, OutOrder order) {
        String s = Objects.requireNonNullElse(taskStatus, "");
        return switch (s) {
            case "PENDING", "ASSIGNED" -> "待拣货";
            case "IN_PROGRESS", "PICKING" -> "拣货中";
            case "STAGED" -> "备料区";
            case "PICKED", "COMPLETED", "DONE", "FINISHED" -> {
                if (order != null && Set.of("PENDING_REVIEW", "REVIEWING").contains(
                        Objects.requireNonNullElse(order.getOutboundStatus(), ""))) {
                    yield "待复核";
                }
                if ("COMPLETED".equals(s) || "DONE".equals(s) || "FINISHED".equals(s)) {
                    if (order != null && "REVIEWING".equals(order.getOutboundStatus())) {
                        yield "待复核";
                    }
                    yield "待复核";
                }
                yield "待复核";
            }
            case "PAUSED" -> "异常";
            default -> {
                if (order != null) {
                    yield mapOutboundStatus(order.getOutboundStatus(), null);
                }
                yield s.isBlank() ? "待拣货" : s;
            }
        };
    }

    private String mapLineStatus(String outboundStatus, int actual, int required) {
        if (actual >= required && required > 0) {
            return "已拣货";
        }
        if (actual > 0) {
            return "拣货中";
        }
        return "待拣货";
    }

    private String mapWorkerExceptionStatus(String status) {
        return switch (Objects.requireNonNullElse(status, "")) {
            case "CLOSED", "RESOLVED" -> "已处理";
            default -> "待处理";
        };
    }

    private String mapReviewExceptionStatus(String status) {
        return switch (Objects.requireNonNullElse(status, "")) {
            case "CLOSED", "RESOLVED" -> "已处理";
            default -> "待处理";
        };
    }

    private String resolveReviewRelatedDoc(OutReviewException ex, List<OutOrder> allOrders) {
        if (allOrders == null || allOrders.isEmpty()) {
            return "—";
        }
        return allOrders.get(0).getOutboundNo() != null ? allOrders.get(0).getOutboundNo() : "—";
    }

    private String resolveAssigneeName(OutPickingTask task, Map<Long, String> userNames) {
        String status = Objects.requireNonNullElse(task.getTaskStatus(), "");
        if ("STAGED".equals(status)) {
            String worker = task.getAssignedTo() == null
                    ? "待指定"
                    : userNames.getOrDefault(task.getAssignedTo(), "工人#" + task.getAssignedTo());
            return "备料区 → " + worker;
        }
        if (Set.of("IN_PROGRESS", "PICKING").contains(status)) {
            return "沈砚（仓管 PDA 拣货）";
        }
        if (task.getAssignedTo() == null) {
            return "待仓管拣货";
        }
        return userNames.getOrDefault(task.getAssignedTo(), "工人#" + task.getAssignedTo());
    }

    @Override
    public WarehousePickingTaskVO startWarehousePicking(Long pickingTaskId, Long operatorId) {
        workerService.startWarehousePicking(pickingTaskId, operatorId);
        return requirePickingTaskVo(pickingTaskId);
    }

    @Override
    public WorkerScanResultVO submitWarehouseScan(WarehousePickingScanRequest request) {
        return workerService.submitWarehouseScan(request);
    }

    @Override
    public WarehousePickingTaskVO completeWarehousePicking(WarehousePickingCompleteRequest request) {
        workerService.completeWarehousePicking(
                request.getPickingTaskId(),
                request.getOperatorId(),
                request.getTargetWorkerId(),
                request.getPrepLocationId());
        return requirePickingTaskVo(request.getPickingTaskId());
    }

    @Override
    public List<WorkerOperatorVO> listProductionWorkers() {
        return workerService.listProductionWorkers();
    }

    private WarehousePickingTaskVO requirePickingTaskVo(Long pickingTaskId) {
        WarehouseWorkbenchVO overview = getOverview();
        return overview.getPickingTasks().stream()
                .filter(t -> pickingTaskId.equals(t.getPickingTaskId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("拣货任务不存在"));
    }

    private String formatDateTime(LocalDateTime time) {
        return time == null ? "—" : time.format(DT_FMT);
    }

    private int toInt(BigDecimal value) {
        return value == null ? 0 : value.setScale(0, java.math.RoundingMode.HALF_UP).intValue();
    }

    private static class PlanContext {
        Long planId;
        String planNo;
        String workOrder;
    }
}
