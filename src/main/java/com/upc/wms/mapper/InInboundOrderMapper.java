package com.upc.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.upc.wms.entity.InInboundOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface InInboundOrderMapper extends BaseMapper<InInboundOrder> {

    @Select("SELECT * FROM in_inbound_order WHERE inbound_status IN ('DRAFT','PENDING') ORDER BY inbound_id DESC")
    List<InInboundOrder> selectPendingInbound();

    @Update("UPDATE in_inbound_order SET inbound_status=#{status} WHERE inbound_id=#{inboundId}")
    int updateStatus(@Param("inboundId") Long inboundId, @Param("status") String status);
}
