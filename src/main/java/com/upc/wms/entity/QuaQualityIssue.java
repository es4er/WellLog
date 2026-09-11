package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("qua_quality_issue")
public class QuaQualityIssue {
    @TableId(type = IdType.AUTO)
    private Long issueId;
    private String issueNo;
    private Long inspectionLineId;
    private Long itemId;
    private Long batchId;
    private String issueType;
    private String issueDesc;
    private BigDecimal unqualifiedQty;
    private String handlingSuggestion;
    private String issueStatus;
    private LocalDateTime createdAt;
}
