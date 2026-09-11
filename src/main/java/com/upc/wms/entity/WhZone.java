package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("wh_zone")
public class WhZone {
    @TableId(type = IdType.AUTO)
    private Long zoneId;
    private Long warehouseId;
    private String zoneCode;
    private String zoneName;
    private String zoneType;
    private String status;
    private Integer rackCount;
    private Integer shelfCount;
}
