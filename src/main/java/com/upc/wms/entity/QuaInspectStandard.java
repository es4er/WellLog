package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("qua_inspect_standard")
public class QuaInspectStandard {
    @TableId(type = IdType.AUTO)
    private Long standardId;
    private String standardCode;
    private String standardName;
    private Long mdItemId;
    private String versionNo;
    private String aqlLevel;
    private BigDecimal aqlValue;
    private String drawingNo;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
