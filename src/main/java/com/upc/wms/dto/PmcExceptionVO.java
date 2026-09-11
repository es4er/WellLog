package com.upc.wms.dto;

import lombok.Data;

import java.util.List;

@Data
public class PmcExceptionVO {
    private String type;
    private String title;
    private String impact;
    private List<String> suggestions;
    private Long planId;
    private Long requisitionId;
    private String planNo;
    private String outboundNo;
}
