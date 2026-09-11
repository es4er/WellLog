package com.upc.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.upc.wms.common.BusinessException;
import com.upc.wms.common.NoGenerator;
import com.upc.wms.dto.TransferCreateRequest;
import com.upc.wms.entity.InvInventory;
import com.upc.wms.entity.WhTransferLine;
import com.upc.wms.entity.WhTransferOrder;
import com.upc.wms.mapper.InvInventoryMapper;
import com.upc.wms.mapper.WhTransferLineMapper;
import com.upc.wms.mapper.WhTransferOrderMapper;
import com.upc.wms.service.InventoryService;
import com.upc.wms.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 移库服务实现。
 * 管理库位间的库存转移（同一仓库内不同库位之间移动）。
 * <p>
 * 业务流程：
 * <ol>
 *   <li>创建移库单（DRAFT 草稿态）：记录移库原因、操作人</li>
 *   <li>确认移库（confirmTransfer）：调用 InventoryService.moveInventory 实际执行库存移动</li>
 *   <li>moveInventory 内部：先扣减源库位库存，再增加目标库位库存，写两条库存流水</li>
 * </ol>
 * 移库是 WMS 中常见的日常操作：合箱、整理、补货、出库前的备货等。
 */
@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    /** 移库单 Mapper */
    private final WhTransferOrderMapper whTransferOrderMapper;
    /** 移库明细行 Mapper */
    private final WhTransferLineMapper whTransferLineMapper;
    /** 库存 Mapper：用于查询源库存是否存在 */
    private final InvInventoryMapper invInventoryMapper;
    /** 库存服务：执行实际的库存移动（扣减+增加+写流水） */
    private final InventoryService inventoryService;

    /**
     * 创建移库单（DRAFT 草稿态）。
     * 创建时校验源库存是否存在（不存在则直接报错），
     * 从源库存自动获取物料ID、批次ID、源库位ID。
     *
     * @param request 移库请求（含仓库ID、移库原因、明细行列表）
     * @return 创建好的移库单
     */
    @Override
    @Transactional
    public WhTransferOrder createTransfer(TransferCreateRequest request) {
        // 第一步：创建移库单头
        WhTransferOrder order = new WhTransferOrder();
        order.setTransferNo(NoGenerator.next("MV"));           // MV = Move（移库编号前缀）
        order.setWarehouseId(request.getWarehouseId());        // 仓库
        order.setTransferReason(request.getTransferReason());  // 移库原因（备货/整理/补货等）
        order.setTransferStatus("DRAFT");                      // 初始状态：草稿
        order.setOperatedBy(request.getOperatedBy());          // 创建人
        whTransferOrderMapper.insert(order);

        // 第二步：为每条移库明细创建行记录
        if (request.getLines() != null) {
            for (TransferCreateRequest.Line l : request.getLines()) {
                // 校验源库存必须存在
                InvInventory source = invInventoryMapper.selectById(l.getInventoryId());
                if (source == null) {
                    throw new BusinessException("源库存不存在: " + l.getInventoryId());
                }
                WhTransferLine line = new WhTransferLine();
                line.setTransferId(order.getTransferId());       // 关联移库单头
                line.setInventoryId(l.getInventoryId());         // 要移动的库存记录ID
                line.setFromLocationId(source.getLocationId());  // 源库位（从库存记录自动获取）
                line.setToLocationId(l.getToLocationId());       // 目标库位（用户指定）
                line.setItemId(source.getItemId());              // 物料ID（从源库存自动获取）
                line.setBatchId(source.getBatchId());            // 批次ID（从源库存自动获取）
                line.setTransferQty(l.getTransferQty());         // 移库数量
                whTransferLineMapper.insert(line);
            }
        }
        return order;
    }

    /**
     * 查询移库单详情（含头信息和所有明细行）。
     */
    @Override
    public Map<String, Object> getTransferDetail(Long transferId) {
        WhTransferOrder order = whTransferOrderMapper.selectById(transferId);
        if (order == null) {
            throw new BusinessException("移库单不存在");
        }
        List<WhTransferLine> lines = whTransferLineMapper.selectList(
                new LambdaQueryWrapper<WhTransferLine>().eq(WhTransferLine::getTransferId, transferId));
        Map<String, Object> detail = new HashMap<>();
        detail.put("order", order);
        detail.put("lines", lines);
        return detail;
    }

    /**
     * 确认移库——这是移库的核心执行操作。
     * 步骤：
     * 1. 校验移库单存在且未完成（幂等保护，防止重复确认）
     * 2. 遍历每条移库明细，调用 InventoryService.moveInventory 执行实际移动
     * 3. 更新移库单状态为 COMPLETED
     *
     * moveInventory 内部会处理：
     * - 源库位库存扣减（如果可用量不足则会抛异常）
     * - 目标库位库存增加（不存在则新建库存记录）
     * - 写两条库存流水（一条扣减、一条增加，用于审计追溯）
     *
     * @param transferId 移库单ID
     * @param operatedBy 操作人ID
     * @return 更新后的移库单
     */
    @Override
    @Transactional
    public WhTransferOrder confirmTransfer(Long transferId, Long operatedBy) {
        // 校验移库单存在
        WhTransferOrder order = whTransferOrderMapper.selectById(transferId);
        if (order == null) {
            throw new BusinessException("移库单不存在");
        }
        // 幂等保护：已完成的移库单不允许再次确认
        if ("COMPLETED".equals(order.getTransferStatus())) {
            throw new BusinessException("移库单已完成");
        }
        // 查出所有移库明细行
        List<WhTransferLine> lines = whTransferLineMapper.selectList(
                new LambdaQueryWrapper<WhTransferLine>().eq(WhTransferLine::getTransferId, transferId));
        // 逐行执行库存移动
        for (WhTransferLine line : lines) {
            inventoryService.moveInventory(line.getInventoryId(), line.getToLocationId(),
                    line.getTransferQty(), transferId, operatedBy);
        }
        // 全部移动成功，更新移库单状态
        order.setTransferStatus("COMPLETED");
        order.setOperatedBy(operatedBy);
        order.setOperatedAt(LocalDateTime.now());
        whTransferOrderMapper.updateById(order);
        return order;
    }
}
