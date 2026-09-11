package com.upc.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.upc.wms.common.BusinessException;
import com.upc.wms.common.NoGenerator;
import com.upc.wms.dto.StocktakeCountRequest;
import com.upc.wms.entity.*;
import com.upc.wms.mapper.*;
import com.upc.wms.service.InventoryService;
import com.upc.wms.service.StocktakeService;
import com.upc.wms.vo.InvAdjustmentLineVO;
import com.upc.wms.vo.StocktakeDetailVO;
import com.upc.wms.vo.StocktakeLineVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 盘点服务实现。
 * 管理库存盘点全流程：创建盘点单 → 下发 → 实盘录入 → 差异生成 → 差异确认 → 库存调整。
 * <p>
 * 盘点单状态流转：
 * DRAFT（草稿）→ PENDING_CHECK（待实盘）→ COMPLETED（无差异）/ DIFFERENCE_PENDING（有差异）
 * → DIFFERENCE_CONFIRMED（差异已确认）→ 生成调整单 → ADJUSTMENT_CREATED → 调整单审批 → COMPLETED。
 * <p>
 * 差异类型：
 * - GAIN（盘盈）：实盘数 > 账面数
 * - LOSS（盘亏）：实盘数 < 账面数
 */
@Service
@RequiredArgsConstructor
public class StocktakeServiceImpl implements StocktakeService {

    /** 盘点单 Mapper */
    private final InvStocktakeOrderMapper invStocktakeOrderMapper;
    /** 盘点明细行 Mapper */
    private final InvStocktakeLineMapper invStocktakeLineMapper;
    /** 盘点差异 Mapper */
    private final InvStocktakeDifferenceMapper invStocktakeDifferenceMapper;
    /** 库存调整单 Mapper */
    private final InvAdjustmentOrderMapper invAdjustmentOrderMapper;
    /** 库存调整明细 Mapper */
    private final InvAdjustmentLineMapper invAdjustmentLineMapper;
    /** 库存 Mapper */
    private final InvInventoryMapper invInventoryMapper;
    /** 库区 Mapper */
    private final WhZoneMapper whZoneMapper;
    /** 库位 Mapper */
    private final WhLocationMapper whLocationMapper;
    /** 库存服务：执行实际的库存调整 */
    private final InventoryService inventoryService;

    /**
     * 创建盘点单（DRAFT 草稿态）。
     * 盘点类型默认为 CYCLE（循环盘点），也可以指定为 FULL（全盘）等。
     * scope 指定盘点范围（库区编码或 "ALL"）。
     */
    @Override
    @Transactional
    public InvStocktakeOrder createStocktake(Long warehouseId, String stocktakeType, String scope, Long createdBy) {
        InvStocktakeOrder order = new InvStocktakeOrder();
        order.setStocktakeNo(NoGenerator.next("ST"));    // ST 前缀盘点单号
        order.setWarehouseId(warehouseId);
        order.setStocktakeScope(scope);                   // 盘点范围（库区编码/ALL）
        order.setStocktakeType(stocktakeType != null ? stocktakeType : "CYCLE"); // 默认循环盘点
        order.setStocktakeStatus("DRAFT");                // 草稿状态
        order.setCreatedBy(createdBy);
        order.setCreatedAt(LocalDateTime.now());
        invStocktakeOrderMapper.insert(order);
        return order;
    }

    /**
     * 生成盘点明细行。
     * 根据盘点单的 scope 范围和 warehouseId，从库存表中查出所有符合条件的库存记录，
     * 逐条生成盘点明细行。账面数量取库存表的 onhandQty（在库数量）。
     *
     * scope 处理逻辑：
     * - "ALL" 或 null → 盘点该仓库全部库存
     * - 库区编码（如 "ZONE-A"）→ 先查库区 → 查库区下所有库位 → 只盘点这些库位的库存
     */
    @Override
    @Transactional
    public void generateStocktakeLines(Long stocktakeId) {
        InvStocktakeOrder order = invStocktakeOrderMapper.selectById(stocktakeId);
        if (order == null) {
            throw new BusinessException("盘点单不存在");
        }

        // 构建查询条件：指定仓库
        LambdaQueryWrapper<InvInventory> wrapper = new LambdaQueryWrapper<InvInventory>()
                .eq(InvInventory::getWarehouseId, order.getWarehouseId());

        // 如果指定了盘点范围（非 ALL），限定库位
        String scope = order.getStocktakeScope();
        if (scope != null && !"ALL".equals(scope)) {
            // 按库区编码 + 仓库ID 查出库区
            WhZone zone = whZoneMapper.selectOne(new LambdaQueryWrapper<WhZone>()
                    .eq(WhZone::getZoneCode, scope)
                    .eq(WhZone::getWarehouseId, order.getWarehouseId()));
            if (zone != null) {
                // 查出该库区下的所有库位ID
                List<Long> locationIds = whLocationMapper.selectList(new LambdaQueryWrapper<WhLocation>()
                        .eq(WhLocation::getZoneId, zone.getZoneId()))
                        .stream().map(WhLocation::getLocationId).toList();
                if (!locationIds.isEmpty()) {
                    wrapper.in(InvInventory::getLocationId, locationIds); // 限定库位
                } else {
                    return; // 库区下无库位，无需生成明细
                }
            } else {
                return; // 库区不存在，无需生成明细
            }
        }

        // 逐条库存记录生成盘点明细行
        List<InvInventory> invList = invInventoryMapper.selectList(wrapper);
        for (InvInventory inv : invList) {
            InvStocktakeLine line = new InvStocktakeLine();
            line.setStocktakeId(stocktakeId);
            line.setInventoryId(inv.getInventoryId());
            line.setItemId(inv.getItemId());
            line.setBatchId(inv.getBatchId());
            line.setLocationId(inv.getLocationId());
            line.setBookQty(inv.getOnhandQty());      // 账面数量 = 当前在库数量
            line.setLineStatus("PENDING");             // 待实盘
            invStocktakeLineMapper.insert(line);
        }
    }

    /**
     * 下发盘点任务。
     * 将盘点单从 DRAFT 推进到 PENDING_CHECK 状态，并生成盘点明细行，
     * 同时指定执行人。
     */
    @Override
    @Transactional
    public void dispatchStocktake(Long stocktakeId, Long executorId) {
        InvStocktakeOrder order = invStocktakeOrderMapper.selectById(stocktakeId);
        if (order == null) {
            throw new BusinessException("盘点单不存在");
        }
        // 状态机校验：只有草稿可以下发
        if (!"DRAFT".equals(order.getStocktakeStatus())) {
            throw new BusinessException("只有草稿状态的盘点单可以下发");
        }
        // 生成盘点明细行（根据仓库和范围查询库存）
        generateStocktakeLines(stocktakeId);
        // 推进状态并指定执行人
        order.setStocktakeStatus("PENDING_CHECK");
        order.setExecutor(executorId);
        order.setExecutorAt(LocalDateTime.now());
        invStocktakeOrderMapper.updateById(order);
    }

    /**
     * 提交实盘结果。
     * 步骤：
     * 1. 保存每条实盘数量（countedQty），自动计算差异量 = 实盘数 - 账面数
     * 2. 自动生成盘点差异记录（只记录有差异的行）
     * 3. 根据差异结果更新盘点单状态：无差异→COMPLETED，有差异→DIFFERENCE_PENDING
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitCountResult(StocktakeCountRequest request) {
        Long stocktakeId = request.getStocktakeId();
        if (stocktakeId == null) {
            throw new BusinessException("盘点单ID不能为空");
        }

        InvStocktakeOrder order = invStocktakeOrderMapper.selectById(stocktakeId);
        if (order == null) {
            throw new BusinessException("盘点单不存在");
        }

        // 1. 保存每条实盘结果：更新实盘数量+计算差异量
        if (request.getLines() != null) {
            for (StocktakeCountRequest.Line l : request.getLines()) {
                InvStocktakeLine line = invStocktakeLineMapper.selectById(l.getStocktakeLineId());
                if (line == null) {
                    throw new BusinessException("盘点明细不存在：" + l.getStocktakeLineId());
                }
                line.setCountedQty(l.getCountedQty());                               // 实盘数量
                line.setDifferenceQty(l.getCountedQty().subtract(line.getBookQty())); // 差异量 = 实盘 - 账面
                line.setLineStatus("COUNTED");                                        // 已实盘
                invStocktakeLineMapper.updateById(line);
            }
        }

        // 2. 自动生成盘点差异记录（差异量≠0的行）
        List<InvStocktakeDifference> differences = generateDifference(stocktakeId);

        // 3. 根据差异结果更新盘点单状态
        if (differences == null || differences.isEmpty()) {
            order.setStocktakeStatus("COMPLETED");         // 账实相符，直接完成
        } else {
            order.setStocktakeStatus("DIFFERENCE_PENDING"); // 有差异，需要确认
        }
        invStocktakeOrderMapper.updateById(order);
    }

    /**
     * 生成盘点差异记录。
     * 先删除旧的差异记录（避免重复生成），再遍历所有已实盘的明细行，
     * 对差异量≠0的行生成差异记录。
     *
     * 差异类型：
     * - GAIN（盘盈）：countedQty > bookQty（实际比账面多）
     * - LOSS（盘亏）：countedQty < bookQty（实际比账面少）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<InvStocktakeDifference> generateDifference(Long stocktakeId) {
        List<InvStocktakeLine> lines = invStocktakeLineMapper.selectList(
                new LambdaQueryWrapper<InvStocktakeLine>().eq(InvStocktakeLine::getStocktakeId, stocktakeId));

        if (lines == null || lines.isEmpty()) {
            throw new BusinessException("盘点单没有明细");
        }

        // 删除该盘点单原有差异，避免重复生成（先删后建策略）
        List<Long> lineIds = lines.stream()
                .map(InvStocktakeLine::getStocktakeLineId)
                .toList();
        invStocktakeDifferenceMapper.delete(
                new LambdaQueryWrapper<InvStocktakeDifference>()
                        .in(InvStocktakeDifference::getStocktakeLineId, lineIds));

        List<InvStocktakeDifference> result = new ArrayList<>();

        // 遍历每条明细行，生成差异记录
        for (InvStocktakeLine line : lines) {
            // 未录入实盘数量的跳过
            if (line.getBookQty() == null || line.getCountedQty() == null) {
                continue;
            }

            BigDecimal differenceQty = line.getCountedQty().subtract(line.getBookQty()); // 差异量
            line.setDifferenceQty(differenceQty);
            invStocktakeLineMapper.updateById(line);

            // 差异量为0（账实相符），不生成差异记录
            if (differenceQty.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            InvStocktakeDifference difference = new InvStocktakeDifference();
            difference.setStocktakeLineId(line.getStocktakeLineId());
            // 差异类型：正数→盘盈(GAIN)，负数→盘亏(LOSS)
            difference.setDifferenceType(differenceQty.compareTo(BigDecimal.ZERO) > 0 ? "GAIN" : "LOSS");
            difference.setDifferenceQty(differenceQty);
            difference.setConfirmStatus("PENDING"); // 待确认
            invStocktakeDifferenceMapper.insert(difference);
            result.add(difference);
        }

        return result;
    }

    @Override
    public List<InvStocktakeDifference> listDifferences(Long stocktakeId) {
        List<InvStocktakeLine> lines = invStocktakeLineMapper.selectList(
                new LambdaQueryWrapper<InvStocktakeLine>().eq(InvStocktakeLine::getStocktakeId, stocktakeId));
        List<Long> lineIds = lines.stream().map(InvStocktakeLine::getStocktakeLineId).toList();
        if (lineIds.isEmpty()) {
            return List.of();
        }
        return invStocktakeDifferenceMapper.selectList(new LambdaQueryWrapper<InvStocktakeDifference>()
                .in(InvStocktakeDifference::getStocktakeLineId, lineIds));
    }

    @Override
    public List<com.upc.wms.vo.StocktakeListVO> list(Long warehouseId, String status, String keyword) {
        if ("ALL".equalsIgnoreCase(status)) {
            status = null; // "ALL" 等同于不筛选状态
        }
        return invStocktakeOrderMapper.selectByFilter(warehouseId, status, keyword);
    }

    @Override
    public StocktakeDetailVO getStocktakeDetail(Long stocktakeId) {
        if (stocktakeId == null) {
            throw new BusinessException("盘点单ID不能为空");
        }
        InvStocktakeOrder order = invStocktakeOrderMapper.selectById(stocktakeId);
        if (order == null) {
            throw new BusinessException("盘点单不存在");
        }
        StocktakeDetailVO detail = new StocktakeDetailVO();
        detail.setOrder(order);
        detail.setLines(invStocktakeLineMapper.selectDetailLines(stocktakeId));
        return detail;
    }

    /**
     * 确认所有盘点差异。
     * 状态校验：只有 DIFFERENCE_PENDING 状态的盘点单可以确认差异。
     * 确认后的盘点单进入 DIFFERENCE_CONFIRMED 状态，可进一步生成调整单。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmAllDifferences(Long stocktakeId) {
        InvStocktakeOrder order = invStocktakeOrderMapper.selectById(stocktakeId);
        if (order == null) {
            throw new BusinessException("盘点单不存在");
        }

        // 只有"差异待处理"状态才能确认
        if (!"DIFFERENCE_PENDING".equals(order.getStocktakeStatus())) {
            throw new BusinessException("当前状态不能确认差异：" + order.getStocktakeStatus());
        }

        // 再次确认确实有可确认的差异行（防止空确认）
        Long differenceCount = invStocktakeLineMapper.selectCount(
                new LambdaQueryWrapper<InvStocktakeLine>()
                        .eq(InvStocktakeLine::getStocktakeId, stocktakeId)
                        .isNotNull(InvStocktakeLine::getCountedQty)
                        .apply("COALESCE(difference_qty, counted_qty - book_qty, 0) <> 0")); // 差异量≠0
        if (differenceCount == null || differenceCount == 0) {
            throw new BusinessException(400, "当前盘点单没有可确认的差异");
        }

        order.setStocktakeStatus("DIFFERENCE_CONFIRMED"); // 差异已确认
        order.setReviewedAt(LocalDateTime.now());
        int updated = invStocktakeOrderMapper.updateById(order);
        if (updated != 1) {
            throw new BusinessException("盘点状态更新失败");
        }
    }

    /**
     * 从盘点差异生成库存调整单。
     * 前提条件：
     * 1. 盘点单状态必须为 DIFFERENCE_CONFIRMED（差异已确认）
     * 2. 该盘点单之前没有生成过调整单（一个盘点单只生成一次调整单）
     *
     * 生成的调整单状态为 PENDING_APPROVAL（待审批），
     * 审批通过后调用 approveAdjustment 实际调整库存。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvAdjustmentOrder createAdjustment(Long stocktakeId, String reason) {
        if (stocktakeId == null) {
            throw new BusinessException("盘点单ID不能为空");
        }

        InvStocktakeOrder stocktakeOrder = invStocktakeOrderMapper.selectById(stocktakeId);
        if (stocktakeOrder == null) {
            throw new BusinessException("盘点单不存在");
        }

        // 校验1：只有差异已确认的盘点单才能生成调整单
        if (!"DIFFERENCE_CONFIRMED".equals(stocktakeOrder.getStocktakeStatus())) {
            throw new BusinessException("只有差异已确认的盘点单才能生成调整单，当前状态：" + stocktakeOrder.getStocktakeStatus());
        }

        // 校验2：一个盘点单只能生成一次调整单
        Long existingCount = invAdjustmentOrderMapper.selectCount(
                new LambdaQueryWrapper<InvAdjustmentOrder>()
                        .eq(InvAdjustmentOrder::getStocktakeId, stocktakeId));
        if (existingCount != null && existingCount > 0) {
            throw new BusinessException("当前盘点单已经生成过调整单");
        }

        // 查出所有差异行（差异量≠0）
        List<InvStocktakeLine> differenceLines = invStocktakeLineMapper.selectList(
                new LambdaQueryWrapper<InvStocktakeLine>()
                        .eq(InvStocktakeLine::getStocktakeId, stocktakeId)
                        .isNotNull(InvStocktakeLine::getCountedQty)
                        .apply("COALESCE(difference_qty, counted_qty - book_qty, 0) <> 0"));

        if (differenceLines == null || differenceLines.isEmpty()) {
            throw new BusinessException("当前盘点单没有可调整的差异");
        }

        // 创建调整单头
        InvAdjustmentOrder adjustmentOrder = new InvAdjustmentOrder();
        adjustmentOrder.setAdjustmentNo(NoGenerator.next("ADJ")); // ADJ 前缀调整单号
        adjustmentOrder.setStocktakeId(stocktakeId);               // 关联盘点单
        adjustmentOrder.setAdjustmentReason(reason != null && !reason.isBlank() ? reason : "盘点差异调整");
        adjustmentOrder.setAdjustmentStatus("PENDING_APPROVAL");   // 待审批
        adjustmentOrder.setCreatedBy(1L);
        adjustmentOrder.setCreatedAt(LocalDateTime.now());
        invAdjustmentOrderMapper.insert(adjustmentOrder);

        // 为每条差异生成调整明细行：记录调整前后的数量
        for (InvStocktakeLine line : differenceLines) {
            BigDecimal beforeQty = line.getBookQty() == null ? BigDecimal.ZERO : line.getBookQty();     // 账面数（调整前）
            BigDecimal afterQty = line.getCountedQty() == null ? BigDecimal.ZERO : line.getCountedQty(); // 实盘数（调整后）

            InvAdjustmentLine al = new InvAdjustmentLine();
            al.setAdjustmentId(adjustmentOrder.getAdjustmentId());
            al.setInventoryId(line.getInventoryId());
            al.setBeforeQty(beforeQty);                                // 调整前数量
            al.setAfterQty(afterQty);                                  // 调整后数量
            al.setAdjustmentQty(afterQty.subtract(beforeQty));         // 调整量 = 调整后 - 调整前
            invAdjustmentLineMapper.insert(al);
        }

        // 推进盘点单状态
        stocktakeOrder.setStocktakeStatus("ADJUSTMENT_CREATED"); // 调整单已生成
        invStocktakeOrderMapper.updateById(stocktakeOrder);

        return adjustmentOrder;
    }

    @Override
    public List<InvAdjustmentOrder> listAdjustmentOrders(String status) {
        LambdaQueryWrapper<InvAdjustmentOrder> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            wrapper.eq(InvAdjustmentOrder::getAdjustmentStatus, status);
        }
        wrapper.orderByDesc(InvAdjustmentOrder::getCreatedAt);
        return invAdjustmentOrderMapper.selectList(wrapper);
    }

    @Override
    public Map<String, Object> getAdjustmentDetail(Long adjustmentId) {
        if (adjustmentId == null) {
            throw new BusinessException("调整单ID不能为空");
        }
        InvAdjustmentOrder order = invAdjustmentOrderMapper.selectById(adjustmentId);
        if (order == null) {
            throw new BusinessException("调整单不存在");
        }
        List<InvAdjustmentLineVO> lines = invAdjustmentLineMapper.selectDetailLines(adjustmentId);
        Map<String, Object> result = new HashMap<>();
        result.put("order", order);
        result.put("lines", lines);
        return result;
    }

    /**
     * 审批通过调整单并实际执行库存调整。
     * 遍历所有调整明细行，调用 InventoryService.adjustInventory 将库存改为调整后的数量。
     * 同时将盘点单状态推进到 COMPLETED（最终完成）。
     */
    @Override
    @Transactional
    public void approveAdjustment(Long adjustmentId, Long approvedBy) {
        InvAdjustmentOrder adjustment = invAdjustmentOrderMapper.selectById(adjustmentId);
        if (adjustment == null) {
            throw new BusinessException("调整单不存在");
        }
        // 逐行执行库存调整
        List<InvAdjustmentLine> lines = invAdjustmentLineMapper.selectList(
                new LambdaQueryWrapper<InvAdjustmentLine>().eq(InvAdjustmentLine::getAdjustmentId, adjustmentId));
        for (InvAdjustmentLine al : lines) {
            // 将库存数量直接调整为 afterQty（系统自动计算差异量并写流水）
            inventoryService.adjustInventory(al.getInventoryId(), al.getAfterQty(),
                    "ADJUSTMENT_ORDER", adjustmentId, approvedBy);
        }
        // 调整单状态推进
        adjustment.setAdjustmentStatus("COMPLETED");
        adjustment.setApprovedBy(approvedBy);
        adjustment.setApprovedAt(LocalDateTime.now());
        invAdjustmentOrderMapper.updateById(adjustment);

        // 关联的盘点单也推进到完成
        if (adjustment.getStocktakeId() != null) {
            InvStocktakeOrder order = invStocktakeOrderMapper.selectById(adjustment.getStocktakeId());
            if (order != null) {
                order.setStocktakeStatus("COMPLETED"); // 盘点最终完成
                order.setReviewer(approvedBy);
                order.setReviewedAt(LocalDateTime.now());
                invStocktakeOrderMapper.updateById(order);
            }
        }
    }

    /**
     * 驳回调整单。
     * 不做库存变更，只是将调整单状态改为 REJECTED。
     * 驳回后可以人工修改差异或重新盘点。
     */
    @Override
    @Transactional
    public void rejectAdjustment(Long adjustmentId) {
        InvAdjustmentOrder adjustment = invAdjustmentOrderMapper.selectById(adjustmentId);
        if (adjustment == null) {
            throw new BusinessException("调整单不存在");
        }
        adjustment.setAdjustmentStatus("REJECTED"); // 已驳回
        invAdjustmentOrderMapper.updateById(adjustment);
    }

    /**
     * 手工创建库存调整单（非盘点流程产生的调整）。
     * 用于日常库存纠错、报废等场景——不经过盘点流程，直接调整库存。
     */
    @Override
    @Transactional
    public List<InvAdjustmentLine> createAdjustmentManually(String reason, List<Map<String, Object>> lines, Long createdBy) {
        InvAdjustmentOrder adjustment = new InvAdjustmentOrder();
        adjustment.setAdjustmentNo(NoGenerator.next("ADJ"));
        adjustment.setAdjustmentReason(reason);
        adjustment.setAdjustmentStatus("DRAFT"); // 手工调整单默认草稿，需进一步审批
        adjustment.setCreatedBy(createdBy);
        adjustment.setCreatedAt(LocalDateTime.now());
        invAdjustmentOrderMapper.insert(adjustment);

        List<InvAdjustmentLine> result = new ArrayList<>();
        if (lines != null) {
            for (Map<String, Object> lineData : lines) {
                InvAdjustmentLine al = new InvAdjustmentLine();
                al.setAdjustmentId(adjustment.getAdjustmentId());
                Long inventoryId = lineData.get("inventoryId") != null ? Long.valueOf(lineData.get("inventoryId").toString()) : null;
                al.setInventoryId(inventoryId);
                al.setBeforeQty(lineData.get("beforeQty") != null ? new BigDecimal(lineData.get("beforeQty").toString()) : BigDecimal.ZERO);
                al.setAfterQty(lineData.get("afterQty") != null ? new BigDecimal(lineData.get("afterQty").toString()) : BigDecimal.ZERO);
                al.setAdjustmentQty(lineData.get("adjustmentQty") != null ? new BigDecimal(lineData.get("adjustmentQty").toString()) : BigDecimal.ZERO);
                invAdjustmentLineMapper.insert(al);
                result.add(al);
            }
        }
        return result;
    }
}
