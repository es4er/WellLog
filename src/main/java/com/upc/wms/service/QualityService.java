package com.upc.wms.service;

import com.upc.wms.dto.InspectionSubmitRequest;
import com.upc.wms.dto.InspectionSubmitResultVO;
import com.upc.wms.dto.ReceiptSubmitInspectionRequest;
import com.upc.wms.dto.ReceiptSubmitInspectionResultVO;
import com.upc.wms.dto.QualityAnalyticsVO;
import com.upc.wms.dto.QualityIssueAnalysisVO;
import com.upc.wms.dto.QualityIssueVO;
import com.upc.wms.dto.QualityTaskVO;
import com.upc.wms.dto.QualityWorkbenchVO;
import com.upc.wms.entity.QuaInspectionOrder;
import com.upc.wms.entity.QuaQualityIssue;
import com.upc.wms.entity.QuaReturnLine;
import com.upc.wms.entity.QuaReturnOrder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 质量检验与异常处理服务接口。
 */
public interface QualityService {

    QuaInspectionOrder createInspection(Long receiptId);

    /** 仓管员提交收货明细至质检员待检队列 */
    ReceiptSubmitInspectionResultVO submitReceiptForInspection(ReceiptSubmitInspectionRequest request);

    QuaInspectionOrder submitInspection(InspectionSubmitRequest request);

    /** 提交质检并返回前端所需的 hasIssue / issueIds 结构 */
    InspectionSubmitResultVO saveInspectionResult(InspectionSubmitRequest request);

    Map<String, Object> getInspectionDetail(Long inspectionId);

    /** 按收货单组装检测执行页详情 */
    Map<String, Object> getTaskDetailByReceiptId(Long receiptId);

    QualityWorkbenchVO getWorkbench();

    QualityAnalyticsVO getAnalytics();

    List<QualityTaskVO> listTasks(String status, String batchNo, String keyword);

    Map<String, Object> getIssueDetail(Long issueId);

    QualityIssueAnalysisVO analyzeIssue(Long issueId);

    Map<String, Object> freezeIssueInventory(Long issueId, Long operatedBy);

    Map<String, Object> returnIssue(Long issueId, Long operatedBy);

    Map<String, Object> repairIssue(Long issueId);

    Map<String, Object> isolateIssue(Long issueId, Long operatedBy);

    QuaQualityIssue createQualityIssue(Long inspectionLineId, Long itemId, Long batchId,
                                       String issueType, String issueDesc, BigDecimal unqualifiedQty);

    QuaQualityIssue createIssueManual(Map<String, Object> payload);

    List<QuaQualityIssue> listQualityIssues();

    List<QualityIssueVO> listIssueViews();

    void closeQualityIssue(Long issueId);

    QuaReturnOrder createReturnOrder(Long supplierId, Long issueId, List<QuaReturnLine> lines);

    Map<String, Object> getReturnDetail(Long returnId);

    List<Map<String, Object>> queryTrace(String batchNo, String itemCode, String inspectionNo);
}
