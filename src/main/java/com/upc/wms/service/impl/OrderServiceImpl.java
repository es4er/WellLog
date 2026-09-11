package com.upc.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.upc.wms.common.BusinessException;
import com.upc.wms.common.NoGenerator;
import com.upc.wms.entity.MdItem;
import com.upc.wms.entity.OrdCustomerOrder;
import com.upc.wms.entity.OrdCustomerOrderLine;
import com.upc.wms.mapper.MdItemMapper;
import com.upc.wms.mapper.OrdCustomerOrderLineMapper;
import com.upc.wms.mapper.OrdCustomerOrderMapper;
import com.upc.wms.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 客户订单服务实现。
 * 管理从 ERP/MES 同步过来的客户订单（含订单头和明细行）。
 * <p>
 * 订单状态流转：
 * PENDING_REVIEW（待审核）→ APPROVED（已审核）→ 可转生产计划 → 领料 → 出库
 * 或 PENDING_REVIEW → CANCELLED（取消）。
 * <p>
 * 这是 WMS 出库链路的起点：订单 → 生产计划 → 领料单 → 出库单 → 拣货 → 复核 → 交接。
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    /** 客户订单头 Mapper */
    private final OrdCustomerOrderMapper ordCustomerOrderMapper;
    /** 客户订单明细行 Mapper */
    private final OrdCustomerOrderLineMapper ordCustomerOrderLineMapper;
    /** 物料主数据 Mapper */
    private final MdItemMapper mdItemMapper;

    @Override
    public List<OrdCustomerOrder> listOrders() {
        return ordCustomerOrderMapper.selectList(null);
    }

    @Override
    public Map<String, Object> getOrderDetail(Long orderId) {
        OrdCustomerOrder order = ordCustomerOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        List<OrdCustomerOrderLine> lines = ordCustomerOrderLineMapper.selectList(
                new LambdaQueryWrapper<OrdCustomerOrderLine>().eq(OrdCustomerOrderLine::getOrderId, orderId));
        List<Long> itemIds = lines.stream()
                .map(OrdCustomerOrderLine::getItemId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, MdItem> itemMap = itemIds.isEmpty()
                ? Map.of()
                : mdItemMapper.selectBatchIds(itemIds).stream()
                        .collect(Collectors.toMap(MdItem::getItemId, item -> item, (a, b) -> a));

        List<Map<String, Object>> lineViews = new ArrayList<>();
        for (OrdCustomerOrderLine line : lines) {
            MdItem item = line.getItemId() != null ? itemMap.get(line.getItemId()) : null;
            Map<String, Object> row = new HashMap<>();
            row.put("orderLineId", line.getOrderLineId());
            row.put("orderId", line.getOrderId());
            row.put("itemId", line.getItemId());
            row.put("itemCode", item != null ? item.getItemCode() : null);
            row.put("itemName", item != null ? item.getItemName() : null);
            row.put("orderedQty", line.getOrderedQty());
            row.put("requiredDate", line.getRequiredDate());
            row.put("lineStatus", line.getLineStatus());
            lineViews.add(row);
        }

        Map<String, Object> detail = new HashMap<>();
        detail.put("order", order);
        detail.put("lines", lineViews);
        return detail;
    }

    /**
     * 创建客户订单（头+明细行一起创建）。
     * 订单号以 SO 前缀自动生成（Sales Order）。
     * 默认状态为 PENDING_REVIEW（待审核），需要 PMC 计划员审核后才能转生产计划。
     *
     * @param order 订单头（前端传入或集成接口同步）
     * @param lines 订单明细行列表
     * @return 创建后的订单（含数据库生成的 orderId）
     */
    @Override
    @Transactional
    public OrdCustomerOrder createOrder(OrdCustomerOrder order, List<OrdCustomerOrderLine> lines) {
        if (order.getOrderNo() == null) {
            order.setOrderNo(NoGenerator.next("SO")); // 自动生成销售订单号
        }
        if (order.getOrderStatus() == null) {
            order.setOrderStatus("PENDING_REVIEW");    // 新订单默认待审核
        }
        ordCustomerOrderMapper.insert(order); // insert后 order.getOrderId() 被自动回填
        // 批量插入明细行
        if (lines != null) {
            for (OrdCustomerOrderLine line : lines) {
                line.setOrderId(order.getOrderId()); // 关联订单头
                if (line.getLineStatus() == null) {
                    line.setLineStatus("OPEN");        // 明细行状态：开启
                }
                ordCustomerOrderLineMapper.insert(line);
            }
        }
        return order;
    }

    /**
     * 按订单号精确查找。
     * 用于集成同步时的去重判断：先查是否存在同号订单。
     * 使用 LIMIT 1 提高查询效率。
     *
     * @param orderNo 订单号
     * @return 匹配的订单，不存在返回 null
     */
    @Override
    public OrdCustomerOrder findByOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return null;
        }
        return ordCustomerOrderMapper.selectOne(
                new LambdaQueryWrapper<OrdCustomerOrder>()
                        .eq(OrdCustomerOrder::getOrderNo, orderNo)
                        .last("LIMIT 1")); // 只查第一条，提高效率
    }

    /**
     * 审核通过订单。
     * 状态约束：只有 PENDING_REVIEW 状态才能审核通过。
     * 审核后记录审核人和审核时间，用于审计追溯。
     *
     * @param orderId 订单ID
     * @param approvedBy 审核人ID
     */
    @Override
    public void approveOrder(Long orderId, Long approvedBy) {
        OrdCustomerOrder order = ordCustomerOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        // 状态机校验：只有待审核的订单才能通过审核
        if (!"PENDING_REVIEW".equals(order.getOrderStatus())) {
            throw new BusinessException("订单当前状态不可审核: " + order.getOrderStatus());
        }
        order.setOrderStatus("APPROVED");
        order.setApprovedBy(approvedBy);              // 审核人
        order.setApprovedAt(LocalDateTime.now());     // 审核时间
        ordCustomerOrderMapper.updateById(order);
    }

    /**
     * 取消订单（业务取消，非物理删除）。
     * 已审核的订单也可以取消（但通常业务上需要审批流）。
     */
    @Override
    public void cancelOrder(Long orderId) {
        OrdCustomerOrder order = ordCustomerOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        order.setOrderStatus("CANCELLED"); // 状态改为已取消
        ordCustomerOrderMapper.updateById(order);
    }
}
