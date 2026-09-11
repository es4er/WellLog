package com.upc.wms.dto;

import lombok.Data;

import java.util.List;

@Data
public class WorkerScanResultVO {
    private Boolean success;
    private List<String> messages;
    private WorkerScanRecordVO record;
}
