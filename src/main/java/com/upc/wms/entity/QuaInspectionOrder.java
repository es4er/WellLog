package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("qua_inspection_order")
public class QuaInspectionOrder {
    @TableId(type = IdType.AUTO)
    private Long inspectionId;
    private String inspectionNo;
    private Long receiptId;
    private Long inspectedBy;
    private LocalDateTime inspectedAt;
    private String inspectionResult;
    private String inspectionStatus;
}
