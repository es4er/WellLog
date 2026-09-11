package com.upc.wms.dto;

import lombok.Data;

@Data
public class QualityIssueAnalysisVO {
    private String riskLevel;
    private String rootCause;
    private String impactScope;
    private String recommendation;
    private String analysisReport;
    private boolean aiPowered;
}
