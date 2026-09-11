package com.upc.wms.dto;

import lombok.Data;

import java.util.List;

@Data
public class PmcOutboundRowVO {
    private Long planId;
    private Long requisitionId;
    private String plan;
    private String req;
    private String out;
    private String product;
    private Integer ready;
    private String status;
    private String impact;
    private Integer step;
    private String note;
    private List<PmcTimelineStepVO> timeline;
}
