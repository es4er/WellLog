package com.upc.wms.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 质检员工作台聚合数据。
 */
@Data
public class QualityWorkbenchVO {
    private List<QualityTaskVO> tasks = new ArrayList<>();
    private List<QualityIssueVO> issues = new ArrayList<>();
    private int pendingInspection;
    private int inspectingCount;
    private int todayCompleted;
    private double passRatePercent;
    private int openIssues;
    private int strictBatches;
    private List<Object[]> dashboardKpis = new ArrayList<>();
    private List<Object[]> kpis = new ArrayList<>();
    private List<Object> inspectionStats = new ArrayList<>();
    private List<Object> strictBatchList = new ArrayList<>();
    private List<Object> qualityFunnel = new ArrayList<>();
    private List<QualityAnalyticsVO.PassRatePoint> passRateTrend = new ArrayList<>();
    private List<Object> riskSummary = new ArrayList<>();
}
