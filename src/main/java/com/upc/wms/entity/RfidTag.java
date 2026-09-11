package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rfid_tag")
public class RfidTag {
    @TableId(type = IdType.AUTO)
    private Long rfidId;
    private String epcCode;
    private String bindType;
    private Long bindId;
    private String tagStatus;
    private LocalDateTime lastSeenAt;
}
