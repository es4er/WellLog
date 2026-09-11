package com.upc.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.upc.wms.common.BusinessException;
import com.upc.wms.dto.PrepLocationOptionVO;
import com.upc.wms.dto.PrepLocationRecommendVO;
import com.upc.wms.entity.InvInventory;
import com.upc.wms.entity.OutOrder;
import com.upc.wms.entity.OutPickingLine;
import com.upc.wms.entity.OutPickingTask;
import com.upc.wms.entity.PmcProductionPlan;
import com.upc.wms.entity.PmcRequisitionOrder;
import com.upc.wms.entity.WhLocation;
import com.upc.wms.entity.WhZone;
import com.upc.wms.mapper.InvInventoryMapper;
import com.upc.wms.mapper.OutOrderMapper;
import com.upc.wms.mapper.OutPickingLineMapper;
import com.upc.wms.mapper.OutPickingTaskMapper;
import com.upc.wms.mapper.PmcProductionPlanMapper;
import com.upc.wms.mapper.PmcRequisitionOrderMapper;
import com.upc.wms.mapper.WhLocationMapper;
import com.upc.wms.mapper.WhZoneMapper;
import com.upc.wms.service.PrepAreaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PrepAreaServiceImpl implements PrepAreaService {

    private final WhZoneMapper whZoneMapper;
    private final WhLocationMapper whLocationMapper;
    private final InvInventoryMapper invInventoryMapper;
    private final OutPickingTaskMapper outPickingTaskMapper;
    private final OutPickingLineMapper outPickingLineMapper;
    private final OutOrderMapper outOrderMapper;
    private final PmcRequisitionOrderMapper requisitionMapper;
    private final PmcProductionPlanMapper planMapper;

    @Override
    public Long resolvePrepLocationId(Long warehouseId) {
        WhZone zone = requirePrepZone(warehouseId);
        WhLocation location = whLocationMapper.selectOne(new LambdaQueryWrapper<WhLocation>()
                .eq(WhLocation::getZoneId, zone.getZoneId())
                .eq(WhLocation::getLocationCode, DEFAULT_LOCATION_CODE)
                .last("LIMIT 1"));
        if (location == null) {
            location = whLocationMapper.selectOne(new LambdaQueryWrapper<WhLocation>()
                    .eq(WhLocation::getZoneId, zone.getZoneId())
                    .orderByAsc(WhLocation::getLocationCode)
                    .last("LIMIT 1"));
        }
        if (location == null) {
            throw new BusinessException("备料区库区下无可用库位，请先维护 PREP-01-01 等库位");
        }
        return location.getLocationId();
    }

    @Override
    public List<PrepLocationOptionVO> listPrepLocations(Long warehouseId) {
        WhZone zone = requirePrepZone(warehouseId);
        List<WhLocation> locations = listPrepLocationEntities(zone.getZoneId());
        Map<Long, BigDecimal> onhandByLoc = sumOnhandByLocation(locations);
        return locations.stream()
                .map(loc -> toOption(loc, onhandByLoc.getOrDefault(loc.getLocationId(), BigDecimal.ZERO)))
                .toList();
    }

    @Override
    public PrepLocationRecommendVO recommendForPickingTask(Long pickingTaskId) {
        if (pickingTaskId == null) {
            throw new BusinessException("缺少拣货任务");
        }
        OutPickingTask task = outPickingTaskMapper.selectById(pickingTaskId);
        if (task == null) {
            throw new BusinessException("拣货任务不存在");
        }
        OutOrder order = outOrderMapper.selectById(task.getOutboundId());
        if (order == null) {
            throw new BusinessException("拣货任务未关联出库单");
        }
        Long warehouseId = order.getWarehouseId() != null ? order.getWarehouseId() : 1L;
        WhZone zone = requirePrepZone(warehouseId);
        List<WhLocation> locations = listPrepLocationEntities(zone.getZoneId());
        if (locations.isEmpty()) {
            throw new BusinessException("备料区库区下无可用库位");
        }
        Map<Long, BigDecimal> onhandByLoc = sumOnhandByLocation(locations);
        List<PrepLocationOptionVO> options = locations.stream()
                .map(loc -> toOption(loc, onhandByLoc.getOrDefault(loc.getLocationId(), BigDecimal.ZERO)))
                .toList();

        Set<Long> prepLocIds = locations.stream().map(WhLocation::getLocationId).collect(Collectors.toSet());
        String workOrderNo = resolveWorkOrderNo(task);

        WhLocation recommended = findSameWorkOrderPrepLocation(workOrderNo, pickingTaskId, prepLocIds);
        String reason;
        if (recommended != null) {
            reason = String.format("同工单 %s 已有物料在备料区 %s，本任务统一入同一格", workOrderNo, recommended.getLocationCode());
        } else {
            recommended = findEmptyPrepLocation(locations, onhandByLoc);
            if (recommended != null) {
                reason = String.format("推荐空闲库位 %s（当前无账存）", recommended.getLocationCode());
            } else {
                recommended = locations.stream()
                        .filter(loc -> DEFAULT_LOCATION_CODE.equals(loc.getLocationCode()))
                        .findFirst()
                        .orElse(locations.get(0));
                reason = String.format("默认备料格 %s", recommended.getLocationCode());
            }
        }

        PrepLocationRecommendVO vo = new PrepLocationRecommendVO();
        vo.setLocationId(recommended.getLocationId());
        vo.setLocationCode(recommended.getLocationCode());
        vo.setLocationName(recommended.getLocationName());
        vo.setReason(reason);
        vo.setOptions(options);
        return vo;
    }

    @Override
    public void validatePrepLocation(Long warehouseId, Long locationId) {
        if (locationId == null) {
            throw new BusinessException("请选择备料库位");
        }
        WhZone zone = requirePrepZone(warehouseId);
        WhLocation location = whLocationMapper.selectById(locationId);
        if (location == null || !zone.getZoneId().equals(location.getZoneId())) {
            throw new BusinessException("所选库位不属于备料区，请重新选择");
        }
        if (location.getLocationCode() == null || !location.getLocationCode().startsWith(LOCATION_CODE_PREFIX)) {
            throw new BusinessException("所选库位不是备料格（PREP-*），请重新选择");
        }
    }

    private WhZone requirePrepZone(Long warehouseId) {
        Long whId = warehouseId != null ? warehouseId : 1L;
        WhZone zone = whZoneMapper.selectOne(new LambdaQueryWrapper<WhZone>()
                .eq(WhZone::getWarehouseId, whId)
                .eq(WhZone::getZoneCode, ZONE_CODE)
                .last("LIMIT 1"));
        if (zone == null) {
            zone = whZoneMapper.selectOne(new LambdaQueryWrapper<WhZone>()
                    .eq(WhZone::getWarehouseId, whId)
                    .eq(WhZone::getZoneCode, LEGACY_ZONE_CODE)
                    .last("LIMIT 1"));
        }
        if (zone == null) {
            throw new BusinessException("未配置备料区库区（ZONE-PREP），请先执行 prep_area_seed.sql 或维护库区");
        }
        return zone;
    }

    private List<WhLocation> listPrepLocationEntities(Long zoneId) {
        return whLocationMapper.selectList(new LambdaQueryWrapper<WhLocation>()
                .eq(WhLocation::getZoneId, zoneId)
                .likeRight(WhLocation::getLocationCode, LOCATION_CODE_PREFIX)
                .orderByAsc(WhLocation::getLocationCode));
    }

    private Map<Long, BigDecimal> sumOnhandByLocation(List<WhLocation> locations) {
        if (locations.isEmpty()) {
            return Map.of();
        }
        Set<Long> locIds = locations.stream().map(WhLocation::getLocationId).collect(Collectors.toSet());
        List<InvInventory> rows = invInventoryMapper.selectList(new LambdaQueryWrapper<InvInventory>()
                .in(InvInventory::getLocationId, locIds));
        Map<Long, BigDecimal> onhandByLoc = new HashMap<>();
        for (InvInventory row : rows) {
            BigDecimal qty = row.getOnhandQty() != null ? row.getOnhandQty() : BigDecimal.ZERO;
            onhandByLoc.merge(row.getLocationId(), qty, BigDecimal::add);
        }
        return onhandByLoc;
    }

    private PrepLocationOptionVO toOption(WhLocation loc, BigDecimal onhand) {
        PrepLocationOptionVO vo = new PrepLocationOptionVO();
        vo.setLocationId(loc.getLocationId());
        vo.setLocationCode(loc.getLocationCode());
        vo.setLocationName(loc.getLocationName());
        vo.setOnhandQty(onhand);
        vo.setEmpty(onhand.compareTo(BigDecimal.ZERO) <= 0);
        return vo;
    }

    private WhLocation findSameWorkOrderPrepLocation(String workOrderNo, Long excludeTaskId, Set<Long> prepLocIds) {
        if (workOrderNo == null || workOrderNo.isBlank()) {
            return null;
        }
        List<OutPickingTask> staged = outPickingTaskMapper.selectList(new LambdaQueryWrapper<OutPickingTask>()
                .eq(OutPickingTask::getTaskStatus, "STAGED")
                .eq(OutPickingTask::getWorkOrderNo, workOrderNo)
                .ne(excludeTaskId != null, OutPickingTask::getPickingTaskId, excludeTaskId));
        Set<Long> usedPrepLocs = new HashSet<>();
        for (OutPickingTask stagedTask : staged) {
            List<OutPickingLine> lines = outPickingLineMapper.selectList(new LambdaQueryWrapper<OutPickingLine>()
                    .eq(OutPickingLine::getPickingTaskId, stagedTask.getPickingTaskId()));
            for (OutPickingLine line : lines) {
                if (line.getLocationId() != null && prepLocIds.contains(line.getLocationId())) {
                    usedPrepLocs.add(line.getLocationId());
                }
            }
        }
        if (usedPrepLocs.isEmpty()) {
            return null;
        }
        Long locId = usedPrepLocs.iterator().next();
        return whLocationMapper.selectById(locId);
    }

    private WhLocation findEmptyPrepLocation(List<WhLocation> locations, Map<Long, BigDecimal> onhandByLoc) {
        return locations.stream()
                .filter(loc -> onhandByLoc.getOrDefault(loc.getLocationId(), BigDecimal.ZERO).compareTo(BigDecimal.ZERO) <= 0)
                .min(Comparator.comparing(WhLocation::getLocationCode))
                .orElse(null);
    }

    private String resolveWorkOrderNo(OutPickingTask task) {
        if (task.getWorkOrderNo() != null && !task.getWorkOrderNo().isBlank()) {
            return task.getWorkOrderNo();
        }
        OutOrder order = outOrderMapper.selectById(task.getOutboundId());
        if (order != null && order.getRequisitionId() != null) {
            PmcRequisitionOrder req = requisitionMapper.selectById(order.getRequisitionId());
            if (req != null && req.getSourcePlanId() != null) {
                PmcProductionPlan plan = planMapper.selectById(req.getSourcePlanId());
                if (plan != null) {
                    if (plan.getMesPlanNo() != null && !plan.getMesPlanNo().isBlank()) {
                        return plan.getMesPlanNo();
                    }
                    if (plan.getPlanNo() != null && !plan.getPlanNo().isBlank()) {
                        return plan.getPlanNo();
                    }
                }
            }
        }
        if (task.getPickingTaskNo() != null && !task.getPickingTaskNo().isBlank()) {
            return task.getPickingTaskNo();
        }
        return "WO" + task.getPickingTaskId();
    }
}
