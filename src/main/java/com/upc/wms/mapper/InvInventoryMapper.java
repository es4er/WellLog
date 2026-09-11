package com.upc.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.upc.wms.entity.InvInventory;
import com.upc.wms.vo.InvInventoryDetailVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

public interface InvInventoryMapper extends BaseMapper<InvInventory> {

    /**
     * 按仓库筛选计算库存总价值（SUM(onhand_qty * standard_cost)）。
     */
    BigDecimal selectInventoryTotalValue(@Param("warehouseId") Long warehouseId);

    /**
     * 库存列表（联表物料/批次/库位展示字段）。
     */
    List<InvInventoryDetailVO> selectListWithDetail();

    /**
     * 按仓库+库位+物料+批次定位唯一库存行。
     */
    @Select("SELECT * FROM inv_inventory WHERE warehouse_id=#{warehouseId} AND location_id=#{locationId} " +
            "AND item_id=#{itemId} AND batch_id=#{batchId}")
    InvInventory selectByItemBatchLocation(@Param("warehouseId") Long warehouseId,
                                           @Param("locationId") Long locationId,
                                           @Param("itemId") Long itemId,
                                           @Param("batchId") Long batchId);

    /**
     * 按仓库+库位+物料+批次查询全部库存行（兼容历史脏数据存在多条）。
     */
    @Select("SELECT * FROM inv_inventory WHERE warehouse_id=#{warehouseId} AND location_id=#{locationId} " +
            "AND item_id=#{itemId} AND batch_id=#{batchId} ORDER BY inventory_id ASC")
    List<InvInventory> listByItemBatchLocation(@Param("warehouseId") Long warehouseId,
                                               @Param("locationId") Long locationId,
                                               @Param("itemId") Long itemId,
                                               @Param("batchId") Long batchId);

    /**
     * 增加库存（入库、移库入、调整增）。
     */
    @Update("UPDATE inv_inventory SET onhand_qty=onhand_qty+#{qty}, available_qty=available_qty+#{qty}, " +
            "version=version+1, last_txn_at=NOW() WHERE inventory_id=#{inventoryId}")
    int increaseStock(@Param("inventoryId") Long inventoryId, @Param("qty") BigDecimal qty);

    /**
     * 扣减库存：条件更新保证可用数量充足且状态可用，返回影响行数判断是否成功。
     */
    @Update("UPDATE inv_inventory SET onhand_qty=onhand_qty-#{qty}, available_qty=available_qty-#{qty}, " +
            "version=version+1, last_txn_at=NOW() WHERE inventory_id=#{inventoryId} " +
            "AND available_qty>=#{qty} AND inventory_status='AVAILABLE'")
    int deductStock(@Param("inventoryId") Long inventoryId, @Param("qty") BigDecimal qty);

    /**
     * 冻结：可用数量减少，冻结数量增加。
     */
    @Update("UPDATE inv_inventory SET available_qty=available_qty-#{qty}, frozen_qty=frozen_qty+#{qty}, " +
            "version=version+1, last_txn_at=NOW() WHERE inventory_id=#{inventoryId} AND available_qty>=#{qty}")
    int freezeStock(@Param("inventoryId") Long inventoryId, @Param("qty") BigDecimal qty);

    /**
     * 解冻：冻结数量减少，可用数量增加。
     */
    @Update("UPDATE inv_inventory SET available_qty=available_qty+#{qty}, frozen_qty=frozen_qty-#{qty}, " +
            "version=version+1, last_txn_at=NOW() WHERE inventory_id=#{inventoryId} AND frozen_qty>=#{qty}")
    int unfreezeStock(@Param("inventoryId") Long inventoryId, @Param("qty") BigDecimal qty);
}
