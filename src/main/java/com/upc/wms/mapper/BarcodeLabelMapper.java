package com.upc.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.upc.wms.entity.BarcodeLabel;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BarcodeLabelMapper extends BaseMapper<BarcodeLabel> {

    @Select("SELECT * FROM barcode_label WHERE barcode_value=#{barcodeValue}")
    BarcodeLabel selectByValue(@Param("barcodeValue") String barcodeValue);
}
