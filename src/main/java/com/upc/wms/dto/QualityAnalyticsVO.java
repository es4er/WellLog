package com.upc.wms.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QualityAnalyticsVO {
    private List<Object[]> kpis = new ArrayList<>();
    private List<PassRatePoint> passRateTrend = new ArrayList<>();
    private List<IssueTypeStat> issueTypeStats = new ArrayList<>();
    private List<ItemRank> itemRanking = new ArrayList<>();
    private List<SupplierStat> supplierStats = new ArrayList<>();

    @Data
    public static class PassRatePoint {
        private String day;
        private double passRate;
        private int inspectedCount;
        private int qualifiedCount;
    }

    @Data
    public static class IssueTypeStat {
        private String type;
        private String label;
        private int count;
    }

    @Data
    public static class ItemRank {
        private String itemName;
        private int issueCount;
        private String riskLevel;
    }

    @Data
    public static class SupplierStat {
        private String supplierName;
        private int batchCount;
        private double passRate;
        private int issueCount;
    }
}
