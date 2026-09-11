package com.upc.wms.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class QualityIssueVO {
    private Long issueId;
    private String issueNo;
    private Long itemId;
    private String itemName;
    private Long batchId;
    private String batchNo;
    private String issueDesc;
    private String issueType;
    private String riskLevel;
    private String level;
    /** 待分析 / 分析完成 / 处理中 / 已关闭 */
    private String status;
    private String issueStatus;
    private BigDecimal unqualifiedQty;
}
