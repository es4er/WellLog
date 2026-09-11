package com.upc.wms.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class InspectionSubmitResultVO {
    private boolean hasIssue;
    private List<Long> issueIds = new ArrayList<>();
    private String message;
    private Long inspectionId;
    private String inspectionNo;
    private String inspectionResult;
    /** 收货单上仍待检的明细行数（含 INSPECTING / PENDING_INSPECTION） */
    private int remainingLineCount;
    /** 本收货单全部物料是否均已检完 */
    private boolean allComplete;
}
