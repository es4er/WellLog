package com.upc.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.upc.wms.entity.InvAdjustmentOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface InvAdjustmentOrderMapper extends BaseMapper<InvAdjustmentOrder> {

    @Select("SELECT * FROM inv_adjustment_order WHERE stocktake_id = #{stocktakeId} LIMIT 1")
    InvAdjustmentOrder selectByStocktakeId(@Param("stocktakeId") Long stocktakeId);
}
