package com.upc.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.upc.wms.entity.RecReceiptOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface RecReceiptOrderMapper extends BaseMapper<RecReceiptOrder> {

    @Select("SELECT * FROM rec_receipt_order WHERE receipt_status='PENDING_INSPECTION' ORDER BY arrived_at DESC")
    List<RecReceiptOrder> selectPendingInspectionList();

    @Update("UPDATE rec_receipt_order SET receipt_status=#{status} WHERE receipt_id=#{receiptId}")
    int updateStatus(@Param("receiptId") Long receiptId, @Param("status") String status);
}
