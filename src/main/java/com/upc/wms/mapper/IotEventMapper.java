package com.upc.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.upc.wms.entity.IotEvent;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface IotEventMapper extends BaseMapper<IotEvent> {

    @Select("SELECT * FROM iot_event WHERE processed_flag=0 ORDER BY event_time")
    List<IotEvent> selectUnprocessed();

    @Update("UPDATE iot_event SET processed_flag=1 WHERE event_id=#{eventId}")
    int markProcessed(@Param("eventId") Long eventId);
}
