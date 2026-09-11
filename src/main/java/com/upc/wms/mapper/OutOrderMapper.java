package com.upc.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.upc.wms.entity.OutOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface OutOrderMapper extends BaseMapper<OutOrder> {

    @Select("SELECT * FROM out_order WHERE outbound_status='PENDING_PICK' ORDER BY outbound_id DESC")
    List<OutOrder> selectPendingPickList();

    @Update("UPDATE out_order SET outbound_status=#{status} WHERE outbound_id=#{outboundId}")
    int updateStatus(@Param("outboundId") Long outboundId, @Param("status") String status);
}
