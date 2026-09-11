package com.upc.wms.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class InvAnalyticsVO {
    private SummaryKpi kpi;
    private List<TurnoverPoint> turnoverTrend;
    private List<SlowMovingItem> slowMoving;
    private List<ZoneAccuracy> zoneAccuracy;
    private List<SafetyCompliance> safetyCompliance;
    private List<AdjustmentTrend> adjustmentTrend;
    private List<String> insights;
    private List<FilterOption> filters;

    @Data
    public static class SummaryKpi {
        private long itemCount;
        private BigDecimal totalValue;
        private boolean totalValueAvailable;
        private BigDecimal turnoverRate7d;
        private BigDecimal frozenRatio;
        private BigDecimal safetyComplianceRate;
        private long stocktakeDifferenceCount;
        private long pendingAdjustmentCount;
    }

    @Data
    public static class TurnoverPoint {
        private String day;
        private BigDecimal rate;
        private int move;
    }

    @Data
    public static class SlowMovingItem {
        private String itemCode;
        private String itemName;
        private String batchNo;
        private BigDecimal quantity;
        private String lastOutDate;
        private int stallDays;
        private String locationArea;
        private boolean frozen;
        private String anomalyType;
        private String suggestion;
    }

    @Data
    public static class ZoneAccuracy {
        private String zone;
        private String name;
        private int total;
        private int accurate;
        private BigDecimal pct;
    }

    @Data
    public static class SafetyCompliance {
        private String zone;
        private String name;
        private int total;
        private int compliant;
        private BigDecimal pct;
    }

    @Data
    public static class AdjustmentTrend {
        private String month;
        private int count;
    }

    @Data
    public static class FilterOption {
        private String field;
        private String label;
        private List<OptionItem> options;

        @Data
        public static class OptionItem {
            private String value;
            private String label;
        }
    }
}
