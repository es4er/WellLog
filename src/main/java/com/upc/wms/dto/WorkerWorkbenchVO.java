package com.upc.wms.dto;

import lombok.Data;

import java.util.List;

@Data
public class WorkerWorkbenchVO {
    private WorkerSummaryVO summary;
    private List<WorkerTaskVO> tasks;
    private List<WorkerExceptionVO> exceptions;
    private List<WorkerScanRecordVO> scanRecords;
    private List<WorkerReplenishVO> replenishRecords;
    private List<WorkerNotificationVO> notifications;
    private List<WorkerCompletionVO> completions;
    private List<WorkerTransferVO> transfers;
}
