package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("md_uom")
public class MdUom {
    @TableId(type = IdType.AUTO)
    private Long uomId;
    private String uomCode;
    private String uomName;
    private Integer precisionScale;
}
