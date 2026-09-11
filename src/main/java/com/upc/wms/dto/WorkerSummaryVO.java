package com.upc.wms.dto;

import lombok.Data;

@Data
public class WorkerSummaryVO {
    private int pending;
    private int pendingScan;
    private int shortage;
    private int completed;
}
