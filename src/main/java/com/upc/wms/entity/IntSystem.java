package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("int_system")
public class IntSystem {
    @TableId(type = IdType.AUTO)
    private Long systemId;
    private String systemCode;
    private String systemName;
    private String endpointUrl;
    private String authType;
    private Integer enabledFlag;
}
