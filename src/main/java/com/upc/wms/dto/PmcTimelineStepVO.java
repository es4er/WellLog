package com.upc.wms.dto;

import lombok.Data;

@Data
public class PmcTimelineStepVO {
    private String name;
    private String agent;
    private String handler;
    private String time;
    /** done | current | pending */
    private String state;
}
