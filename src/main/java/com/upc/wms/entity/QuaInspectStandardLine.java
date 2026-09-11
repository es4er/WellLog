package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("qua_inspect_standard_line")
public class QuaInspectStandardLine {
    @TableId(type = IdType.AUTO)
    private Long lineId;
    private Long standardId;
    private Long inspectItemId;
    private Integer requiredFlag;
    private String standardText;
    private BigDecimal nominal;
    private BigDecimal lowerTol;
    private BigDecimal upperTol;
    private BigDecimal minValue;
    private BigDecimal maxValue;
    private String unit;
    private Integer criticalFlag;
    private Integer sortNo;
}
