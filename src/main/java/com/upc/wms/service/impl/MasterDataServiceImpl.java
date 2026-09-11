package com.upc.wms.service.impl;

import com.upc.wms.entity.MdBatch;
import com.upc.wms.entity.MdCustomer;
import com.upc.wms.entity.MdItem;
import com.upc.wms.entity.MdItemCategory;
import com.upc.wms.entity.MdSupplier;
import com.upc.wms.entity.MdUom;
import com.upc.wms.mapper.MdBatchMapper;
import com.upc.wms.mapper.MdCustomerMapper;
import com.upc.wms.mapper.MdItemCategoryMapper;
import com.upc.wms.mapper.MdItemMapper;
import com.upc.wms.mapper.MdSupplierMapper;
import com.upc.wms.mapper.MdUomMapper;
import com.upc.wms.service.MasterDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 主数据管理服务实现。
 * 负责物料（Item）、批次（Batch）、客户（Customer）、供应商（Supplier）、
 * 计量单位（UOM）、物料分类（Category）等基础数据的增删改查。
 * 这些是 WMS 系统的核心主数据，所有业务单据都依赖这些基础数据。
 */
@Service
@RequiredArgsConstructor
public class MasterDataServiceImpl implements MasterDataService {

    /** 物料 Mapper：负责物料的数据库操作 */
    private final MdItemMapper mdItemMapper;
    /** 批次 Mapper：负责批次的数据库操作（收货时生成、质检时更新状态） */
    private final MdBatchMapper mdBatchMapper;
    /** 客户 Mapper：负责客户的数据库操作 */
    private final MdCustomerMapper mdCustomerMapper;
    /** 供应商 Mapper：负责供应商的数据库操作 */
    private final MdSupplierMapper mdSupplierMapper;
    /** 计量单位 Mapper：如 件/套/kg/m 等 */
    private final MdUomMapper mdUomMapper;
    /** 物料分类 Mapper：物料的分组归类 */
    private final MdItemCategoryMapper mdItemCategoryMapper;

    // ======================== 物料（Item）管理 ========================

    /**
     * 查询全部物料列表。
     * selectList(null) 会返回 md_item 表的所有记录。
     * @return 所有物料对象列表
     */
    @Override
    public List<MdItem> listItems() {
        return mdItemMapper.selectList(null);
    }

    /**
     * 按主键ID查询单个物料。
     * @param itemId 物料主键ID
     * @return 物料对象，不存在返回null
     */
    @Override
    public MdItem getItemById(Long itemId) {
        return mdItemMapper.selectById(itemId);
    }

    /**
     * 按物料编码查询物料（编码是业务唯一标识）。
     * @param itemCode 物料编码
     * @return 物料对象，不存在返回null
     */
    @Override
    public MdItem getItemByCode(String itemCode) {
        return mdItemMapper.selectByCode(itemCode);
    }

    /**
     * 新增物料。
     * 如果前端未传状态，默认设为 ENABLED（启用）。
     * @param item 物料对象（前端传入的JSON反序列化）
     * @return 插入后的物料对象（含数据库生成的 itemId）
     */
    @Override
    public MdItem addItem(MdItem item) {
        if (item.getStatus() == null) {
            item.setStatus("ENABLED"); // 默认启用状态
        }
        mdItemMapper.insert(item); // MyBatis-Plus 自动回填主键
        return item;
    }

    /**
     * 更新物料信息。
     * 注意：updateById 是全部字段更新，不是部分更新。
     * @param item 包含更新后字段值的物料对象
     * @return 更新后的物料对象
     */
    @Override
    public MdItem updateItem(MdItem item) {
        mdItemMapper.updateById(item);
        return item;
    }

    /**
     * 禁用物料（逻辑删除，不物理删除）。
     * 先用 selectById 查出来，确认存在后改状态为 DISABLED。
     * @param itemId 物料主键ID
     */
    @Override
    public void disableItem(Long itemId) {
        MdItem item = mdItemMapper.selectById(itemId); // 先查出原记录
        if (item != null) {
            item.setStatus("DISABLED"); // 改为禁用状态而非物理删除，保留数据可追溯
            mdItemMapper.updateById(item);
        }
    }

    // ======================== 客户（Customer）管理 ========================

    @Override
    public List<MdCustomer> listCustomers() {
        return mdCustomerMapper.selectList(null);
    }

    @Override
    public MdCustomer addCustomer(MdCustomer customer) {
        mdCustomerMapper.insert(customer);
        return customer;
    }

    // ======================== 供应商（Supplier）管理 ========================

    @Override
    public List<MdSupplier> listSuppliers() {
        return mdSupplierMapper.selectList(null);
    }

    @Override
    public MdSupplier addSupplier(MdSupplier supplier) {
        mdSupplierMapper.insert(supplier);
        return supplier;
    }

    // ======================== 计量单位（UOM）管理 ========================

    @Override
    public List<MdUom> listUoms() {
        return mdUomMapper.selectList(null);
    }

    // ======================== 物料分类（Category）管理 ========================

    @Override
    public List<MdItemCategory> listCategories() {
        return mdItemCategoryMapper.selectList(null);
    }

    // ======================== 批次（Batch）管理 ========================

    /**
     * 按物料查询该物料下所有批次。
     * 批次是库存管理的核心维度之一（物料+批次+库位决定唯一库存）。
     * @param itemId 物料主键ID
     * @return 该物料下的批次列表
     */
    @Override
    public List<MdBatch> listBatchesByItem(Long itemId) {
        return mdBatchMapper.selectByItem(itemId);
    }

    /**
     * 新增批次。
     * 如果未指定质量状态，默认为 PENDING_INSPECTION（待检），
     * 这是收货后的正常初始状态——需要质检放行后才能用于生产和出库。
     * @param batch 批次对象
     * @return 插入后的批次对象（含数据库生成的 batchId）
     */
    @Override
    public MdBatch addBatch(MdBatch batch) {
        if (batch.getQualityStatus() == null) {
            batch.setQualityStatus("PENDING_INSPECTION"); // 新批次默认待检
        }
        mdBatchMapper.insert(batch);
        return batch;
    }

    /**
     * 更新批次质量状态。
     * 典型流转：PENDING_INSPECTION → QUALIFIED / UNQUALIFIED。
     * 只有 QUALIFIED 的批次才能被齐套计算纳入"可用库存"。
     * @param batchId 批次主键ID
     * @param qualityStatus 新的质量状态
     */
    @Override
    public void updateBatchQualityStatus(Long batchId, String qualityStatus) {
        MdBatch batch = mdBatchMapper.selectById(batchId); // 先查出原记录
        if (batch != null) {
            batch.setQualityStatus(qualityStatus);
            mdBatchMapper.updateById(batch);
        }
    }

    /**
     * 按追溯码查询批次。
     * 追溯码是收货时生成的唯一编码（TR前缀），用于全链路追溯：
     * 可从最终产品追溯到原材料批次、供应商、收货时间等。
     * @param traceCode 追溯码
     * @return 匹配的批次列表
     */
    @Override
    public List<MdBatch> traceByCode(String traceCode) {
        return mdBatchMapper.selectByTraceCode(traceCode);
    }
}
