package com.upc.wms.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class VisualInspectionCompareVO {

    private String taskId;
    private Boolean isAbnormal;
    private Double similarity;
    private Double abnormalScore;
    private Integer matchedFeatureCount;
    private Integer regionCount;
    private List<Region> regions = new ArrayList<>();
    private String resultImageUrl;
    private String differenceImageUrl;

    @Data
    public static class Region {
        private String regionId;
        private Integer x;
        private Integer y;
        private Integer width;
        private Integer height;
        private Double area;
        private Double score;
        private String status;
    }
}
