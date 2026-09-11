package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("wh_location")
public class WhLocation {
    @TableId(type = IdType.AUTO)
    private Long locationId;
    private Long zoneId;
    private String locationCode;
    private String locationName;
    private BigDecimal capacityQty;
    private BigDecimal capacityWeight;
    private BigDecimal capacityVolume;
    private String locationStatus;
    private String rfidReaderCode;
    private String electronicLabelCode;
}
