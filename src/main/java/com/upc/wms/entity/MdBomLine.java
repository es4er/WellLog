package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("md_bom_line")
public class MdBomLine {
    @TableId(type = IdType.AUTO)
    private Long bomLineId;
    private Long bomId;
    private Long componentItemId;
    private BigDecimal qtyPer;
    private Integer lineNo;
    private String remark;
}
