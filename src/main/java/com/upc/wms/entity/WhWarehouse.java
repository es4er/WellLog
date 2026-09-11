package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("wh_warehouse")
public class WhWarehouse {
    @TableId(type = IdType.AUTO)
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private String warehouseType;
    private String address;
    private String status;
}
