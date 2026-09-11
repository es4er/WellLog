package com.upc.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.upc.wms.entity.InvStocktakeLine;
import com.upc.wms.vo.StocktakeLineVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

public interface InvStocktakeLineMapper extends BaseMapper<InvStocktakeLine> {

    @Update("UPDATE inv_stocktake_line SET counted_qty=#{countedQty}, difference_qty=#{countedQty}-book_qty, " +
            "line_status='COUNTED' WHERE stocktake_line_id=#{stocktakeLineId}")
    int updateCountedQty(@Param("stocktakeLineId") Long stocktakeLineId, @Param("countedQty") BigDecimal countedQty);

    @Select("""
        SELECT
            stocktake_line_id AS stocktakeLineId,
            stocktake_id AS stocktakeId,
            inventory_id AS inventoryId,
            item_id AS itemId,
            batch_id AS batchId,
            location_id AS locationId,
            book_qty AS bookQty,
            counted_qty AS countedQty
        FROM inv_stocktake_line
        WHERE stocktake_id = #{_parameter,jdbcType=BIGINT}
        ORDER BY stocktake_line_id
        """)
    List<InvStocktakeLine> findLinesForAdjustment(Long stocktakeId);

    List<StocktakeLineVO> selectDetailLines(@Param("stocktakeId") Long stocktakeId);
}
