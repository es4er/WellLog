package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("iot_event")
public class IotEvent {
    @TableId(type = IdType.AUTO)
    private Long eventId;
    private String deviceCode;
    private String eventType;
    private Long locationId;
    private String payloadJson;
    private LocalDateTime eventTime;
    private Integer processedFlag;
}
