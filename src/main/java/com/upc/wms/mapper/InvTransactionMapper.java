package com.upc.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.upc.wms.entity.InvTransaction;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface InvTransactionMapper extends BaseMapper<InvTransaction> {

    @Select("SELECT * FROM inv_transaction WHERE item_id=#{itemId} AND batch_id=#{batchId} ORDER BY operated_at DESC")
    List<InvTransaction> selectByItemBatch(@Param("itemId") Long itemId, @Param("batchId") Long batchId);

    @Select("SELECT * FROM inv_transaction WHERE source_doc_type=#{docType} AND source_doc_id=#{docId} ORDER BY operated_at")
    List<InvTransaction> selectBySourceDoc(@Param("docType") String docType, @Param("docId") Long docId);
}
