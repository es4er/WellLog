package com.upc.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.upc.wms.dto.BomComponent;
import com.upc.wms.entity.MdBomHeader;
import com.upc.wms.entity.MdBomLine;
import com.upc.wms.mapper.MdBomHeaderMapper;
import com.upc.wms.mapper.MdBomLineMapper;
import com.upc.wms.service.BomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BOM（物料清单）服务实现。
 * 管理产品与原材料/零部件的组成关系，用于生产计划的物料需求展开。
 * <ul>
 *   <li>每个成品物料（productItemId）有一个激活的BOM头（MdBomHeader）</li>
 *   <li>BOM行（MdBomLine）定义了每个组件物料及其单位用量（qtyPer）</li>
 *   <li>系统通过 BOM 展开计算：生产 N 件产品需要多少件各组件物料</li>
 * </ul>
 * 这是齐套（Kit Availability）计算的基础数据来源。
 */
@Service
@RequiredArgsConstructor
public class BomServiceImpl implements BomService {

    /** BOM 头 Mapper */
    private final MdBomHeaderMapper bomHeaderMapper;
    /** BOM 行 Mapper */
    private final MdBomLineMapper bomLineMapper;

    /**
     * 获取指定成品物料的激活BOM组件列表。
     * 流程：
     * 1. 找到该产品的激活 BOM 头（status=ENABLED）
     * 2. 查出该 BOM 下所有行，按行号排序
     * 3. 转换为 BomComponent DTO 返回
     *
     * @param productItemId 成品物料ID
     * @return 组件列表，如果产品没有配置 BOM 则返回空列表
     */
    @Override
    public List<BomComponent> listActiveComponents(Long productItemId) {
        if (productItemId == null) {
            return List.of(); // 空参数直接返回空列表，避免NPE
        }
        // 第一步：找到该产品的启用BOM头（可能有多个版本，取最新）
        MdBomHeader header = findActiveHeader(productItemId);
        if (header == null) {
            return List.of(); // 该产品未配置BOM，返回空列表
        }
        // 第二步：查询该BOM下的所有行，按行号升序排列
        List<MdBomLine> lines = bomLineMapper.selectList(
                new LambdaQueryWrapper<MdBomLine>()
                        .eq(MdBomLine::getBomId, header.getBomId())
                        .orderByAsc(MdBomLine::getLineNo)   // 按行号排序（BOM结构）
                        .orderByAsc(MdBomLine::getBomLineId));
        // 第三步：转换为前端需要的 BomComponent DTO
        List<BomComponent> result = new ArrayList<>(lines.size());
        for (MdBomLine line : lines) {
            BomComponent c = new BomComponent();
            c.setBomId(header.getBomId());
            c.setProductItemId(header.getProductItemId());   // 成品物料ID
            c.setComponentItemId(line.getComponentItemId()); // 组件物料ID
            c.setQtyPer(line.getQtyPer() == null ? BigDecimal.ONE : line.getQtyPer()); // 单位用量，默认1
            c.setLineNo(line.getLineNo());
            c.setBomVersion(header.getBomVersion());         // 所用BOM版本号
            result.add(c);
        }
        return result;
    }

    /**
     * 展开物料需求：给定产品 + 数量，返回各组件的总需求量。
     * 核心逻辑：对每个 BOM 组件，need = productQty × qtyPer（产品数量 × 单位用量）。
     * 如果 BOM 有多行引用同一个组件物料，需求会合并（merge累加）。
     *
     * 使用场景：生产计划生成时，需要知道 N 件产品需要多少件各组件物料。
     *
     * @param productItemId 成品物料ID
     * @param productQty 产品生产数量
     * @return Map<组件物料ID, 需求数量>
     */
    @Override
    public Map<Long, BigDecimal> expandMaterialNeed(Long productItemId, BigDecimal productQty) {
        BigDecimal qty = productQty == null ? BigDecimal.ONE : productQty; // 默认按1件计算
        Map<Long, BigDecimal> need = new LinkedHashMap<>(); // 保持插入顺序，便于阅读
        for (BomComponent c : listActiveComponents(productItemId)) {
            if (c.getComponentItemId() == null) {
                continue; // 无效行跳过
            }
            // 需求量 = 产品数量 × 单位用量
            BigDecimal lineNeed = qty.multiply(c.getQtyPer() == null ? BigDecimal.ONE : c.getQtyPer());
            // merge：如果同一个物料在多行出现（如不同工序用量），累加需求
            need.merge(c.getComponentItemId(), lineNeed, BigDecimal::add);
        }
        return need;
    }

    /**
     * 按产品查询 BOM 详情（含 BOM 头信息和组件列表）。
     */
    @Override
    public Map<String, Object> getBomByProduct(Long productItemId) {
        MdBomHeader header = findActiveHeader(productItemId);
        if (header == null) {
            return null; // 该产品未配置BOM
        }
        List<BomComponent> components = listActiveComponents(productItemId);
        Map<String, Object> detail = new HashMap<>();
        detail.put("header", header);       // BOM 头：编码、版本、状态等
        detail.put("lines", components);    // BOM 行：组件物料及用量
        return detail;
    }

    /**
     * 查找指定产品的激活 BOM 头。
     * 查询条件：productItemId 匹配 且 status=ENABLED。
     * 如果有多个版本，取版本号最大（按字符串比较）且 ID 最大的。
     * BOM 版本号用于管理设计变更（如 A→B→C），同一时间只有一个版本启用。
     */
    private MdBomHeader findActiveHeader(Long productItemId) {
        // 查询该产品所有启用状态的BOM头
        List<MdBomHeader> headers = bomHeaderMapper.selectList(
                new LambdaQueryWrapper<MdBomHeader>()
                        .eq(MdBomHeader::getProductItemId, productItemId)
                        .eq(MdBomHeader::getStatus, "ENABLED")); // 只有启用状态的BOM才生效
        if (headers.isEmpty()) {
            return null;
        }
        // 多个启用版本时，选版本号最大 + ID最新的
        // 第一排序：版本号字典序降序（B > A）；第二排序：ID降序（新记录优先）
        return headers.stream()
                .max(Comparator.comparing(MdBomHeader::getBomVersion, Comparator.nullsFirst(String::compareTo))
                        .thenComparing(MdBomHeader::getBomId, Comparator.nullsFirst(Long::compareTo)))
                .orElse(headers.get(0));
    }
}
