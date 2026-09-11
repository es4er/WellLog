package com.upc.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.upc.wms.entity.InvAdjustmentLine;
import com.upc.wms.vo.InvAdjustmentLineVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface InvAdjustmentLineMapper extends BaseMapper<InvAdjustmentLine> {

    @Select("""
        SELECT
            al.adjustment_line_id AS adjustmentLineId,
            al.adjustment_id AS adjustmentId,
            al.inventory_id AS inventoryId,

            i.item_id AS itemId,
            mi.item_code AS itemCode,
            mi.item_name AS itemName,

            i.batch_id AS batchId,
            mb.batch_no AS batchNo,

            i.location_id AS locationId,
            wl.location_code AS locationCode,

            al.before_qty AS beforeQty,
            al.after_qty AS afterQty,
            al.adjustment_qty AS adjustmentQty

        FROM inv_adjustment_line al

        LEFT JOIN inv_inventory i ON i.inventory_id = al.inventory_id
        LEFT JOIN md_item mi ON mi.item_id = i.item_id
        LEFT JOIN md_batch mb ON mb.batch_id = i.batch_id
        LEFT JOIN wh_location wl ON wl.location_id = i.location_id

        WHERE al.adjustment_id = #{adjustmentId}

        ORDER BY al.adjustment_line_id
        """)
    List<InvAdjustmentLineVO> selectDetailLines(@Param("adjustmentId") Long adjustmentId);
}
