package com.upc.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.upc.wms.entity.QuaInspectionOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface QuaInspectionOrderMapper extends BaseMapper<QuaInspectionOrder> {

    @Select("SELECT * FROM qua_inspection_order WHERE receipt_id=#{receiptId}")
    List<QuaInspectionOrder> selectByReceiptId(@Param("receiptId") Long receiptId);

    @Update("UPDATE qua_inspection_order SET inspection_result=#{result} WHERE inspection_id=#{inspectionId}")
    int updateResult(@Param("inspectionId") Long inspectionId, @Param("result") String result);

    @Update("UPDATE qua_inspection_order SET inspection_status=#{status} WHERE inspection_id=#{inspectionId}")
    int updateStatus(@Param("inspectionId") Long inspectionId, @Param("status") String status);
}
