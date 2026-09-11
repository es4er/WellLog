package com.upc.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.upc.wms.entity.MdItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface MdItemMapper extends BaseMapper<MdItem> {

    @Select("SELECT * FROM md_item WHERE item_code=#{itemCode}")
    MdItem selectByCode(@Param("itemCode") String itemCode);
}
