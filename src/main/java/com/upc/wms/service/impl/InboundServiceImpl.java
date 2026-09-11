package com.upc.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.upc.wms.common.BusinessException;
import com.upc.wms.common.NoGenerator;
import com.upc.wms.dto.InboundConfirmRequest;
import com.upc.wms.entity.InInboundLine;
import com.upc.wms.entity.InInboundOrder;
import com.upc.wms.mapper.InInboundLineMapper;
import com.upc.wms.mapper.InInboundOrderMapper;
import com.upc.wms.mapper.RecReceiptOrderMapper;
import com.upc.wms.service.InboundService;
import com.upc.wms.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 入库服务实现。
 * WMS 入库链路的最后一环：质检合格 → 创建入库单 → 确认入库 → 库存增加。
 * <p>
 * 完整的入库三段式流程：
 * <ol>
 *   <li>收货（ReceiptService.createReceipt）→ 待质检</li>
 *   <li>质检（QualityService.submitInspection）→ 判定合格</li>
 *   <li>入库（本服务 confirmInbound）→ 增加库存 + 库存流水</li>
 * </ol>
 * 确认入库时会对每条明细行：
 * - 创建入库明细行记录
 * - 调用 InventoryService.increaseInventory 增加实际库存
 */
@Service
@RequiredArgsConstructor
public class InboundServiceImpl implements InboundService {

    /** 入库单 Mapper */
    private final InInboundOrderMapper inInboundOrderMapper;
    /** 入库明细行 Mapper */
    private final InInboundLineMapper inInboundLineMapper;
    /** 收货单 Mapper：入库完成后将收货单状态改为 STORED */
    private final RecReceiptOrderMapper recReceiptOrderMapper;
    /** 库存服务：实际执行库存增加 */
    private final InventoryService inventoryService;

    /**
     * 创建入库单（DRAFT 草稿态）。
     * 入库类型默认为 PURCHASE（采购入库）。
     * 此时还未实际增加库存，只是创建了一个入库凭证。
     */
    @Override
    @Transactional
    public InInboundOrder createInboundOrder(Long receiptId, Long warehouseId, Long operatedBy) {
        InInboundOrder order = new InInboundOrder();
        order.setInboundNo(NoGenerator.next("IN"));       // IN 前缀入库单号
        order.setReceiptId(receiptId);                     // 关联收货单（溯源到收货批次）
        order.setInboundType("PURCHASE");                  // 入库类型：采购入库
        order.setWarehouseId(warehouseId);                 // 目标仓库
        order.setInboundBy(operatedBy);                    // 入库执行人
        order.setInboundStatus("DRAFT");                   // 草稿状态：待确认
        inInboundOrderMapper.insert(order);
        return order;
    }

    /**
     * 查询所有待入库的单据。
     */
    @Override
    public List<InInboundOrder> listPendingInbound() {
        return inInboundOrderMapper.selectPendingInbound();
    }

    @Override
    public Map<String, Object> getInboundDetail(Long inboundId) {
        InInboundOrder order = inInboundOrderMapper.selectById(inboundId);
        if (order == null) {
            throw new BusinessException("入库单不存在");
        }
        List<InInboundLine> lines = inInboundLineMapper.selectList(
                new LambdaQueryWrapper<InInboundLine>().eq(InInboundLine::getInboundId, inboundId));
        Map<String, Object> detail = new HashMap<>();
        detail.put("order", order);
        detail.put("lines", lines);
        return detail;
    }

    /**
     * 确认入库——库存实际增加的核心操作。
     * 步骤：
     * 1. 校验入库单存在且未完成（幂等保护）
     * 2. 遍历每条入库明细：
     *    a. 创建入库明细行记录（记录入库到哪个库位、哪个批次、数量）
     *    b. 调用 increaseInventory 实际增加库存（如果该物料+批次+库位已存在则累加，否则新建库存记录）
     * 3. 更新入库单状态为 COMPLETED
     * 4. 如果有关联收货单，将收货单状态更新为 STORED
     *
     * 事务保证：入库明细+库存增加+状态更新在一个事务中，要么全部成功要么全部回滚。
     *
     * @param request 入库确认请求（含入库单ID、库位、明细行列表）
     * @return 更新后的入库单
     */
    @Override
    @Transactional
    public InInboundOrder confirmInbound(InboundConfirmRequest request) {
        InInboundOrder order = inInboundOrderMapper.selectById(request.getInboundId());
        if (order == null) {
            throw new BusinessException("入库单不存在");
        }
        // 幂等保护：已完成入库的单据不允许再次入库
        if ("COMPLETED".equals(order.getInboundStatus())) {
            throw new BusinessException("入库单已完成，不能重复入库");
        }
        // 仓库优先取请求中的值，未传则用入库单上的仓库
        Long warehouseId = request.getWarehouseId() != null ? request.getWarehouseId() : order.getWarehouseId();
        // 逐行入库
        if (request.getLines() != null) {
            for (InboundConfirmRequest.Line l : request.getLines()) {
                // a. 创建入库明细行
                InInboundLine line = new InInboundLine();
                line.setInboundId(order.getInboundId());
                line.setInspectionLineId(l.getInspectionLineId()); // 关联质检行（可追溯质检结果）
                line.setItemId(l.getItemId());
                line.setBatchId(l.getBatchId());
                line.setLocationId(l.getLocationId());             // 上架到的具体库位
                line.setInboundQty(l.getInboundQty());
                inInboundLineMapper.insert(line);

                // b. 实际增加库存（如果该物料+批次+库位已存在则累加，否则新建库存记录）
                inventoryService.increaseInventory(warehouseId, l.getLocationId(), l.getItemId(), l.getBatchId(),
                        l.getInboundQty(), "INBOUND_ORDER", order.getInboundId(), request.getOperatedBy());
            }
        }
        // 更新入库单状态
        order.setInboundStatus("COMPLETED");
        order.setInboundAt(LocalDateTime.now()); // 入库完成时间
        inInboundOrderMapper.updateById(order);
        // 关联收货单的状态推进
        if (order.getReceiptId() != null) {
            recReceiptOrderMapper.updateStatus(order.getReceiptId(), "STORED"); // 收货→存入
        }
        return order;
    }
}
