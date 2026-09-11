package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("int_integration_message")
public class IntIntegrationMessage {
    @TableId(type = IdType.AUTO)
    private Long messageId;
    private Long systemId;
    private String direction;
    private String messageType;
    private String businessKey;
    private String payloadJson;
    private String processStatus;
    private Integer retryCount;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
}
