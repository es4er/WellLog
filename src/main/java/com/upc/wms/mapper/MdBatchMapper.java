package com.upc.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.upc.wms.entity.MdBatch;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface MdBatchMapper extends BaseMapper<MdBatch> {

    @Select("SELECT * FROM md_batch WHERE item_id=#{itemId}")
    List<MdBatch> selectByItem(@Param("itemId") Long itemId);

    @Select("SELECT * FROM md_batch WHERE batch_no=#{batchNo}")
    MdBatch selectByBatchNo(@Param("batchNo") String batchNo);

    @Select("SELECT * FROM md_batch WHERE trace_code=#{traceCode}")
    List<MdBatch> selectByTraceCode(@Param("traceCode") String traceCode);
}
