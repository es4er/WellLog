package com.upc.wms.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PmcWorkbenchOverview {
    private List<PmcPlanCardVO> plans;
    private List<PmcShortageRowVO> shortages;
    private Integer pendingReview;
    private List<List<String>> outboundStats;
    private List<PmcOutboundRowVO> outboundList;
    private List<List<String>> kpis;
    private List<Map<String, Object>> shortageTop;
    private List<List<Object>> planFunnel;
    private List<String> riskSummary;
    private List<PmcExceptionVO> outboundExceptions;
    private List<PmcReadyTrendVO> readyTrend;
    private List<String> dataSources;
    private List<List<String>> analysisReports;
    private String trendAlert;
    private String shortageAlert;
    /** 最近一次 ERP/MES 同步时间 */
    private String lastIntegrationSyncAt;
    /** 最近一次同步写入的订单数 */
    private Integer lastIntegrationSyncCount;
    /** 主页提示文案（基于真实同步/待审数据） */
    private String inboxHint;
}
