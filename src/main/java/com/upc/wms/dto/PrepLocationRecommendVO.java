package com.upc.wms.dto;

import lombok.Data;

import java.util.List;

@Data
public class PrepLocationRecommendVO {
    private Long locationId;
    private String locationCode;
    private String locationName;
    /** 推荐原因说明 */
    private String reason;
    private List<PrepLocationOptionVO> options;
}
