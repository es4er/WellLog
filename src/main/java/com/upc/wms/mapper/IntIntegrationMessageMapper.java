package com.upc.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.upc.wms.entity.IntIntegrationMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface IntIntegrationMessageMapper extends BaseMapper<IntIntegrationMessage> {

    @Select("SELECT * FROM int_integration_message WHERE process_status='PENDING' ORDER BY created_at")
    List<IntIntegrationMessage> selectPendingMessages();

    @Update("UPDATE int_integration_message SET process_status='SUCCESS', processed_at=NOW() WHERE message_id=#{messageId}")
    int markSuccess(@Param("messageId") Long messageId);

    @Update("UPDATE int_integration_message SET process_status='FAILED', error_message=#{errorMessage}, " +
            "processed_at=NOW() WHERE message_id=#{messageId}")
    int markFailed(@Param("messageId") Long messageId, @Param("errorMessage") String errorMessage);

    @Update("UPDATE int_integration_message SET retry_count=retry_count+1 WHERE message_id=#{messageId}")
    int increaseRetryCount(@Param("messageId") Long messageId);
}
