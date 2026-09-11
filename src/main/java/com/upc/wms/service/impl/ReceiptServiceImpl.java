package com.upc.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.upc.wms.common.BusinessException;
import com.upc.wms.common.NoGenerator;
import com.upc.wms.dto.ReceiptCreateRequest;
import com.upc.wms.entity.MdBatch;
import com.upc.wms.entity.RecReceiptLine;
import com.upc.wms.entity.RecReceiptOrder;
import com.upc.wms.mapper.MdBatchMapper;
import com.upc.wms.mapper.RecReceiptLineMapper;
import com.upc.wms.mapper.RecReceiptOrderMapper;
import com.upc.wms.service.ReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 收货服务实现。
 * WMS 入库链路的第一环：供应商到货 → 创建收货单 → 生成批次 → 待质检。
 * <p>
 * 核心业务流程：
 * <ol>
 *   <li>创建收货单（RecReceiptOrder）：记录供应商、仓库、ERP采购单号等</li>
 *   <li>为每条收货明细生成批次（MdBatch）：批次号=用户传入或自动生成</li>
 *   <li>生成收货明细行（RecReceiptLine）：关联批次、收货数量</li>
 *   <li>收货完成后状态 = PENDING_INSPECTION（待质检），质检通过后才能入库</li>
 * </ol>
 * 收货 → 质检 → 入库 是 WMS 的三段式入库流程。
 */
@Service
@RequiredArgsConstructor
public class ReceiptServiceImpl implements ReceiptService {

    /** 收货单 Mapper */
    private final RecReceiptOrderMapper recReceiptOrderMapper;
    /** 收货明细行 Mapper */
    private final RecReceiptLineMapper recReceiptLineMapper;
    /** 批次 Mapper：收货时创建批次 */
    private final MdBatchMapper mdBatchMapper;

    /**
     * 创建收货单并生成批次和明细行。
     * 这是收货业务的核心入口，在一个事务中完成三个操作：
     * 1. 创建收货单头（订单号 RC 前缀）
     * 2. 遍历每条收货明细 → 创建批次 → 创建收货明细行
     * 3. 整个收货单状态设为 PENDING_INSPECTION（待质检）
     *
     * 事务保证：收货单头+批次+明细行三者要么全部创建成功，要么全部回滚。
     *
     * @param request 收货请求（含供应商ID、仓库ID、ERP采购单号、收货明细列表）
     * @return 创建好的收货单（含数据库生成的 receiptId）
     */
    @Override
    @Transactional
    public RecReceiptOrder createReceipt(ReceiptCreateRequest request) {
        // 第一步：创建收货单头
        RecReceiptOrder order = new RecReceiptOrder();
        order.setReceiptNo(NoGenerator.next("RC"));           // RC前缀的收货单号
        order.setSupplierId(request.getSupplierId());          // 供应商ID（哪个供应商送的货）
        order.setWarehouseId(request.getWarehouseId());        // 收货仓库
        order.setSourceSystemId(request.getSourceSystemId());  // 来源系统（ERP/MES/手工）
        order.setErpPoNo(request.getErpPoNo());                // ERP采购单号（用于对账追溯）
        order.setArrivedAt(request.getArrivedAt());            // 到货时间
        order.setReceivedBy(request.getReceivedBy());          // 收货人
        order.setReceiptStatus("PENDING_INSPECTION");          // 收货后直接进入待质检状态
        order.setRemark(request.getRemark());
        recReceiptOrderMapper.insert(order); // insert后 order.getReceiptId() 被 MyBatis-Plus 自动回填

        // 第二步：遍历每条收货明细，创建批次和明细行
        if (request.getLines() != null) {
            for (ReceiptCreateRequest.Line l : request.getLines()) {
                // 2a：创建批次记录
                MdBatch batch = new MdBatch();
                batch.setItemId(l.getItemId());               // 物料ID
                // 批次号：用户可自定义，未提供时自动生成 B 前缀批次号
                batch.setBatchNo(l.getBatchNo() != null ? l.getBatchNo() : NoGenerator.next("B"));
                batch.setSupplierId(request.getSupplierId());  // 批次关联供应商（用于质量追溯）
                batch.setManufactureDate(l.getManufactureDate()); // 生产日期
                batch.setExpireDate(l.getExpireDate());         // 有效期
                batch.setQualityStatus("PENDING_INSPECTION");   // 批次初始状态：待质检
                batch.setTraceCode(NoGenerator.next("TR"));     // 生成全局唯一追溯码
                mdBatchMapper.insert(batch); // insert后 batch.getBatchId() 被自动回填

                // 2b：创建收货明细行（关联收货单+批次）
                RecReceiptLine line = new RecReceiptLine();
                line.setReceiptId(order.getReceiptId());      // 关联收货单头
                line.setItemId(l.getItemId());                 // 物料ID
                line.setBatchId(batch.getBatchId());           // 关联刚创建的批次
                line.setReceivedQty(l.getReceivedQty());       // 收货数量
                line.setInspectedQty(BigDecimal.ZERO);         // 已检数量初始为0
                line.setLineStatus("PENDING_INSPECTION");      // 明细行状态与头一致
                recReceiptLineMapper.insert(line);
            }
        }
        return order;
    }

    /**
     * 查询收货单详情（含头信息和所有明细行）。
     * 业务校验：收货单不存在时抛出异常。
     */
    @Override
    public Map<String, Object> getReceiptDetail(Long receiptId) {
        syncReceiptStatusFromLines(receiptId);
        RecReceiptOrder order = recReceiptOrderMapper.selectById(receiptId);
        if (order == null) {
            throw new BusinessException("收货单不存在");
        }
        // 按收货单ID查出所有明细行
        List<RecReceiptLine> lines = recReceiptLineMapper.selectList(
                new LambdaQueryWrapper<RecReceiptLine>().eq(RecReceiptLine::getReceiptId, receiptId));
        Map<String, Object> detail = new HashMap<>();
        detail.put("order", order);   // 收货单头
        detail.put("lines", lines);   // 收货明细行列表
        return detail;
    }

    @Override
    public List<RecReceiptOrder> listReceipts() {
        return recReceiptOrderMapper.selectList(null);
    }

    /**
     * 查询所有待质检的收货单。
     * 质检人员在工作台看到的就是这些单据。
     */
    @Override
    public List<RecReceiptOrder> listPendingInspectionReceipts() {
        return recReceiptOrderMapper.selectPendingInspectionList();
    }

    /**
     * 更新收货单状态（单字段更新，高效）。
     * 状态流转：PENDING_INSPECTION → INSPECTING → INSPECTED → STORED。
     */
    @Override
    public void updateReceiptStatus(Long receiptId, String status) {
        recReceiptOrderMapper.updateStatus(receiptId, status); // 直接SQL更新，避免先查后改
    }

    @Override
    public void syncReceiptStatusFromLines(Long receiptId) {
        if (receiptId == null) {
            return;
        }
        RecReceiptOrder order = recReceiptOrderMapper.selectById(receiptId);
        if (order == null) {
            return;
        }
        String current = order.getReceiptStatus();
        if (current != null && Set.of("STORED", "AVAILABLE", "COMPLETED").contains(current)) {
            return;
        }

        List<RecReceiptLine> lines = recReceiptLineMapper.selectList(
                new LambdaQueryWrapper<RecReceiptLine>().eq(RecReceiptLine::getReceiptId, receiptId));
        if (lines.isEmpty()) {
            return;
        }

        boolean anyInspecting = false;
        boolean anyPendingInspection = false;
        boolean anyInspected = false;
        boolean anyUnqualified = false;
        boolean allStored = true;

        for (RecReceiptLine line : lines) {
            String ls = line.getLineStatus() != null ? line.getLineStatus() : "PENDING_INSPECTION";
            if ("STORED".equals(ls) || "AVAILABLE".equals(ls)) {
                continue;
            }
            allStored = false;
            switch (ls) {
                case "PENDING_INSPECTION" -> anyPendingInspection = true;
                case "INSPECTING" -> anyInspecting = true;
                case "INSPECTED", "QUALIFIED", "INSPECTION_PASSED", "QUALITY_PASS" -> anyInspected = true;
                case "UNQUALIFIED", "INSPECTION_FAILED", "QUALITY_FAIL", "QUARANTINED" -> anyUnqualified = true;
                default -> { }
            }
        }

        String next;
        if (allStored) {
            next = "STORED";
        } else if (anyInspecting || (anyInspected && anyPendingInspection)) {
            next = "INSPECTING";
        } else if (anyPendingInspection && !anyInspected && !anyUnqualified) {
            next = "PENDING_INSPECTION";
        } else if (anyUnqualified && !anyInspected && !anyPendingInspection && !anyInspecting) {
            next = "INSPECTION_FAILED";
        } else if (anyUnqualified && anyInspected) {
            next = "QUALITY_FAIL";
        } else if (anyInspected) {
            next = "INSPECTED";
        } else {
            next = current != null ? current : "PENDING_INSPECTION";
        }

        if (!next.equals(current)) {
            recReceiptOrderMapper.updateStatus(receiptId, next);
        }
    }
}
