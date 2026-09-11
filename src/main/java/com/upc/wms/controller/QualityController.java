package com.upc.wms.controller;

import com.upc.wms.common.Result;
import com.upc.wms.dto.InspectionSubmitRequest;
import com.upc.wms.dto.InspectionSubmitResultVO;
import com.upc.wms.dto.QuaInspectStandardSaveRequest;
import com.upc.wms.dto.QuaInspectStandardVO;
import com.upc.wms.dto.QualityAnalyticsVO;
import com.upc.wms.dto.QualityIssueAnalysisVO;
import com.upc.wms.dto.QualityIssueVO;
import com.upc.wms.dto.QualityTaskVO;
import com.upc.wms.dto.QualityWorkbenchVO;
import com.upc.wms.dto.VisualInspectionCompareVO;
import com.upc.wms.entity.MdItem;
import com.upc.wms.entity.QuaInspectItem;
import com.upc.wms.entity.QuaInspectionOrder;
import com.upc.wms.entity.QuaQualityIssue;
import com.upc.wms.entity.QuaReturnLine;
import com.upc.wms.entity.QuaReturnOrder;
import com.upc.wms.service.QualityMasterDataService;
import com.upc.wms.service.QualityService;
import com.upc.wms.service.VisualInspectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/quality")
@RequiredArgsConstructor
public class QualityController {

    private final QualityService qualityService;
    private final QualityMasterDataService qualityMasterDataService;
    private final VisualInspectionService visualInspectionService;

    @GetMapping("/workbench")
    public Result<QualityWorkbenchVO> workbench() {
        return Result.success(qualityService.getWorkbench());
    }

    @GetMapping("/analytics")
    public Result<QualityAnalyticsVO> analytics() {
        return Result.success(qualityService.getAnalytics());
    }

    @GetMapping("/task/list")
    public Result<List<QualityTaskVO>> taskList(@RequestParam(required = false) String status,
                                                @RequestParam(required = false) String batchNo,
                                                @RequestParam(required = false) String keyword) {
        return Result.success(qualityService.listTasks(status, batchNo, keyword));
    }

    @GetMapping("/task/detail")
    public Result<Map<String, Object>> taskDetail(@RequestParam Long receiptId) {
        return Result.success(qualityService.getTaskDetailByReceiptId(receiptId));
    }

    @PostMapping("/inspection/create")
    public Result<QuaInspectionOrder> createInspection(@RequestParam Long receiptId) {
        return Result.success(qualityService.createInspection(receiptId));
    }

    @PostMapping("/inspection/submit")
    public Result<QuaInspectionOrder> submitInspection(@RequestBody InspectionSubmitRequest request) {
        return Result.success(qualityService.submitInspection(request));
    }

    @PostMapping("/result/save")
    public Result<InspectionSubmitResultVO> saveResult(@RequestBody InspectionSubmitRequest request) {
        return Result.success(qualityService.saveInspectionResult(request));
    }

    @PostMapping("/image-compare")
    public Result<VisualInspectionCompareVO> compareImages(
            @RequestParam("referenceImage") MultipartFile referenceImage,
            @RequestParam("inspectionImage") MultipartFile inspectionImage,
            @RequestParam(required = false) Integer minArea,
            @RequestParam(required = false) Integer threshold
    ) {
        return Result.success(visualInspectionService.compareImages(
                referenceImage, inspectionImage, minArea, threshold
        ));
    }

    @GetMapping("/inspection/detail")
    public Result<Map<String, Object>> inspectionDetail(@RequestParam Long inspectionId) {
        return Result.success(qualityService.getInspectionDetail(inspectionId));
    }

    @GetMapping("/issue/list")
    public Result<List<QualityIssueVO>> listIssues() {
        return Result.success(qualityService.listIssueViews());
    }

    @GetMapping("/issue/detail")
    public Result<Map<String, Object>> issueDetail(@RequestParam Long issueId) {
        return Result.success(qualityService.getIssueDetail(issueId));
    }

    @GetMapping("/agent/analyze")
    public Result<QualityIssueAnalysisVO> analyzeIssue(@RequestParam Long issueId) {
        return Result.success(qualityService.analyzeIssue(issueId));
    }

    @PostMapping("/issue/freeze")
    public Result<Map<String, Object>> freezeIssue(@RequestParam Long issueId,
                                                   @RequestParam(required = false) Long operatedBy) {
        return Result.success(qualityService.freezeIssueInventory(issueId, operatedBy != null ? operatedBy : 1L));
    }

    @PostMapping("/issue/return")
    public Result<Map<String, Object>> returnIssue(@RequestParam Long issueId,
                                                   @RequestParam(required = false) Long operatedBy) {
        return Result.success(qualityService.returnIssue(issueId, operatedBy != null ? operatedBy : 1L));
    }

    @PostMapping("/issue/repair")
    public Result<Map<String, Object>> repairIssue(@RequestParam Long issueId) {
        return Result.success(qualityService.repairIssue(issueId));
    }

    @PostMapping("/issue/isolate")
    public Result<Map<String, Object>> isolateIssue(@RequestParam Long issueId,
                                                    @RequestParam(required = false) Long operatedBy) {
        return Result.success(qualityService.isolateIssue(issueId, operatedBy != null ? operatedBy : 1L));
    }

    @PostMapping("/issue/close")
    public Result<Void> closeIssue(@RequestParam Long issueId) {
        qualityService.closeQualityIssue(issueId);
        return Result.success();
    }

    @PostMapping("/issue/create")
    public Result<QuaQualityIssue> createIssue(@RequestBody Map<String, Object> payload) {
        return Result.success(qualityService.createIssueManual(payload));
    }

    @GetMapping("/trace/query")
    public Result<List<Map<String, Object>>> traceQuery(@RequestParam(required = false) String batchNo,
                                                        @RequestParam(required = false) String itemCode,
                                                        @RequestParam(required = false) String inspectionNo) {
        return Result.success(qualityService.queryTrace(batchNo, itemCode, inspectionNo));
    }

    @PostMapping("/return/create")
    public Result<QuaReturnOrder> createReturn(@RequestParam Long supplierId,
                                               @RequestParam(required = false) Long issueId,
                                               @RequestBody List<QuaReturnLine> lines) {
        return Result.success(qualityService.createReturnOrder(supplierId, issueId, lines));
    }

    @GetMapping("/return/detail")
    public Result<Map<String, Object>> returnDetail(@RequestParam Long returnId) {
        return Result.success(qualityService.getReturnDetail(returnId));
    }

    // ---------- 检验项 / 检验标准主数据 ----------

    @GetMapping("/inspect-items")
    public Result<List<QuaInspectItem>> listInspectItems(@RequestParam(required = false) String keyword,
                                                         @RequestParam(required = false) String itemType,
                                                         @RequestParam(required = false) String status) {
        return Result.success(qualityMasterDataService.listInspectItems(keyword, itemType, status));
    }

    @GetMapping("/inspect-items/{id}")
    public Result<QuaInspectItem> getInspectItem(@PathVariable Long id) {
        return Result.success(qualityMasterDataService.getInspectItem(id));
    }

    @PostMapping("/inspect-items/save")
    public Result<QuaInspectItem> saveInspectItem(@RequestBody QuaInspectItem item) {
        return Result.success(qualityMasterDataService.saveInspectItem(item));
    }

    @PostMapping("/inspect-items/{id}/disable")
    public Result<Void> disableInspectItem(@PathVariable Long id) {
        qualityMasterDataService.disableInspectItem(id);
        return Result.success();
    }

    @GetMapping("/standards")
    public Result<List<QuaInspectStandardVO>> listStandards(@RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) String status,
                                                            @RequestParam(required = false) Long mdItemId) {
        return Result.success(qualityMasterDataService.listStandards(keyword, status, mdItemId));
    }

    @GetMapping("/standards/{id}")
    public Result<QuaInspectStandardVO> getStandard(@PathVariable Long id) {
        return Result.success(qualityMasterDataService.getStandardDetail(id));
    }

    @PostMapping("/standards/save")
    public Result<QuaInspectStandardVO> saveStandard(@RequestBody QuaInspectStandardSaveRequest request) {
        return Result.success(qualityMasterDataService.saveStandard(request));
    }

    @PostMapping("/standards/{id}/disable")
    public Result<Void> disableStandard(@PathVariable Long id) {
        qualityMasterDataService.disableStandard(id);
        return Result.success();
    }

    @GetMapping("/master/items")
    public Result<List<MdItem>> listMasterItems(@RequestParam(required = false) String keyword) {
        return Result.success(qualityMasterDataService.listSelectableItems(keyword));
    }
}
