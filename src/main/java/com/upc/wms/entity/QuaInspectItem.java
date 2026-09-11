package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("qua_inspect_item")
public class QuaInspectItem {
    @TableId(type = IdType.AUTO)
    private Long inspectItemId;
    private String itemCode;
    private String itemName;
    /** APPEARANCE / DIMENSION / PERFORMANCE / OTHER */
    private String itemType;
    private String unit;
    private String defaultStandard;
    private Integer criticalFlag;
    private String status;
    private Integer sortNo;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
