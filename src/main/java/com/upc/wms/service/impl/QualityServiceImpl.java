package com.upc.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.wms.common.BusinessException;
import com.upc.wms.common.NoGenerator;
import com.upc.wms.config.DeepSeekProperties;
import com.upc.wms.dto.InspectionSubmitRequest;
import com.upc.wms.dto.InspectionSubmitResultVO;
import com.upc.wms.dto.ReceiptSubmitInspectionRequest;
import com.upc.wms.dto.ReceiptSubmitInspectionResultVO;
import com.upc.wms.dto.QuaInspectStandardVO;
import com.upc.wms.dto.QualityAnalyticsVO;
import com.upc.wms.dto.QualityIssueAnalysisVO;
import com.upc.wms.dto.QualityIssueVO;
import com.upc.wms.dto.QualityTaskVO;
import com.upc.wms.dto.QualityWorkbenchVO;
import com.upc.wms.entity.InvFreezeRecord;
import com.upc.wms.entity.InvInventory;
import com.upc.wms.entity.MdBatch;
import com.upc.wms.entity.MdItem;
import com.upc.wms.entity.MdSupplier;
import com.upc.wms.entity.QuaInspectionLine;
import com.upc.wms.entity.QuaInspectionOrder;
import com.upc.wms.entity.QuaQualityIssue;
import com.upc.wms.entity.QuaReturnLine;
import com.upc.wms.entity.QuaReturnOrder;
import com.upc.wms.entity.RecReceiptLine;
import com.upc.wms.entity.RecReceiptOrder;
import com.upc.wms.llm.DeepSeekChatService;
import com.upc.wms.mapper.MdBatchMapper;
import com.upc.wms.mapper.MdItemMapper;
import com.upc.wms.mapper.MdSupplierMapper;
import com.upc.wms.mapper.QuaInspectionLineMapper;
import com.upc.wms.mapper.QuaInspectionOrderMapper;
import com.upc.wms.mapper.QuaQualityIssueMapper;
import com.upc.wms.mapper.QuaReturnLineMapper;
import com.upc.wms.mapper.QuaReturnOrderMapper;
import com.upc.wms.mapper.RecReceiptLineMapper;
import com.upc.wms.mapper.RecReceiptOrderMapper;
import com.upc.wms.service.InventoryService;
import com.upc.wms.service.QualityMasterDataService;
import com.upc.wms.service.QualityService;
import com.upc.wms.service.ReceiptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QualityServiceImpl implements QualityService {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("MM-dd");
    private static final Set<String> OPEN_ISSUE = Set.of("OPEN", "ANALYZED", "HANDLING");
    private static final String AI_SYSTEM_PROMPT = """
            你是测井装备 WMS 质量分析助手。
            根据给定的质量异常事实，输出严格 JSON（不要 markdown）：
            {
              "riskLevel": "高|中|低",
              "rootCause": "根因一句话",
              "impactScope": "影响范围一句话",
              "recommendation": "处理建议一句话",
              "analysisReport": "80字以内分析报告"
            }
            只基于事实，不要编造单据号或数量。
            """;

    private final QuaInspectionOrderMapper quaInspectionOrderMapper;
    private final QuaInspectionLineMapper quaInspectionLineMapper;
    private final QuaQualityIssueMapper quaQualityIssueMapper;
    private final QuaReturnOrderMapper quaReturnOrderMapper;
    private final QuaReturnLineMapper quaReturnLineMapper;
    private final RecReceiptOrderMapper recReceiptOrderMapper;
    private final RecReceiptLineMapper recReceiptLineMapper;
    private final MdBatchMapper mdBatchMapper;
    private final MdItemMapper mdItemMapper;
    private final MdSupplierMapper mdSupplierMapper;
    private final InventoryService inventoryService;
    private final ReceiptService receiptService;
    private final QualityMasterDataService qualityMasterDataService;
    private final DeepSeekChatService deepSeekChatService;
    private final DeepSeekProperties deepSeekProperties;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<Long, QualityIssueAnalysisVO> analysisCache = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public ReceiptSubmitInspectionResultVO submitReceiptForInspection(ReceiptSubmitInspectionRequest request) {
        if (request.getReceiptId() == null) {
            throw new BusinessException("缺少收货单 ID");
        }
        RecReceiptOrder receipt = recReceiptOrderMapper.selectById(request.getReceiptId());
        if (receipt == null) {
            throw new BusinessException("收货单不存在");
        }
        if (!"PENDING_INSPECTION".equals(receipt.getReceiptStatus())
                && !"INSPECTING".equals(receipt.getReceiptStatus())) {
            throw new BusinessException("当前收货单状态不可提交质检");
        }

        List<RecReceiptLine> allLines = recReceiptLineMapper.selectList(
                new LambdaQueryWrapper<RecReceiptLine>().eq(RecReceiptLine::getReceiptId, request.getReceiptId()));
        if (allLines.isEmpty()) {
            throw new BusinessException("收货单没有可提交的明细行");
        }

        List<Long> requestedIds = request.getReceiptLineIds();
        List<RecReceiptLine> targetLines;
        if (requestedIds == null || requestedIds.isEmpty()) {
            targetLines = allLines.stream()
                    .filter(line -> "PENDING_INSPECTION".equals(line.getLineStatus()))
                    .toList();
        } else {
            Set<Long> idSet = requestedIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
            targetLines = allLines.stream()
                    .filter(line -> idSet.contains(line.getReceiptLineId()))
                    .toList();
        }
        if (targetLines.isEmpty()) {
            throw new BusinessException("请选择待质检的物料行");
        }
        for (RecReceiptLine line : targetLines) {
            if (!"PENDING_INSPECTION".equals(line.getLineStatus())) {
                throw new BusinessException("存在非待质检状态的明细行，无法重复提交");
            }
        }

        QuaInspectionOrder inspection = createInspection(request.getReceiptId());
        Map<Long, MdItem> itemMap = loadItemMap();
        Map<Long, MdBatch> batchMap = loadBatchMap();

        List<ReceiptSubmitInspectionResultVO.SubmittedLineSummary> summaries = new ArrayList<>();
        for (RecReceiptLine line : targetLines) {
            line.setLineStatus("INSPECTING");
            recReceiptLineMapper.updateById(line);

            MdItem item = itemMap.get(line.getItemId());
            MdBatch batch = batchMap.get(line.getBatchId());
            ReceiptSubmitInspectionResultVO.SubmittedLineSummary summary =
                    new ReceiptSubmitInspectionResultVO.SubmittedLineSummary();
            summary.setReceiptLineId(line.getReceiptLineId());
            summary.setItemId(line.getItemId());
            summary.setItemName(item != null ? item.getItemName() : "物料#" + line.getItemId());
            summary.setItemCode(item != null ? item.getItemCode() : null);
            summary.setBatchNo(batch != null ? batch.getBatchNo() : null);
            summary.setReceivedQty(line.getReceivedQty() != null
                    ? line.getReceivedQty().stripTrailingZeros().toPlainString()
                    : "0");
            summaries.add(summary);
        }

        recReceiptOrderMapper.updateStatus(request.getReceiptId(), "INSPECTING");
        receiptService.syncReceiptStatusFromLines(request.getReceiptId());

        ReceiptSubmitInspectionResultVO result = new ReceiptSubmitInspectionResultVO();
        result.setReceiptId(receipt.getReceiptId());
        result.setReceiptNo(receipt.getReceiptNo());
        result.setInspectionId(inspection.getInspectionId());
        result.setInspectionNo(inspection.getInspectionNo());
        result.setSubmittedLineCount(summaries.size());
        result.setLines(summaries);
        result.setMessage("已提交 " + summaries.size() + " 行待检物料至质检员，质检单号 "
                + inspection.getInspectionNo());
        return result;
    }

    @Override
    @Transactional
    public QuaInspectionOrder createInspection(Long receiptId) {
        RecReceiptOrder receipt = recReceiptOrderMapper.selectById(receiptId);
        if (receipt == null) {
            throw new BusinessException("收货单不存在");
        }
        List<QuaInspectionOrder> existing = quaInspectionOrderMapper.selectByReceiptId(receiptId);
        for (QuaInspectionOrder order : existing) {
            if ("DRAFT".equals(order.getInspectionStatus())) {
                return order;
            }
        }
        QuaInspectionOrder order = new QuaInspectionOrder();
        order.setInspectionNo(NoGenerator.next("QC"));
        order.setReceiptId(receiptId);
        order.setInspectionResult("PENDING");
        order.setInspectionStatus("DRAFT");
        quaInspectionOrderMapper.insert(order);
        return order;
    }

    @Override
    @Transactional
    public QuaInspectionOrder submitInspection(InspectionSubmitRequest request) {
        if (request.getReceiptId() == null) {
            throw new BusinessException("缺少 receiptId");
        }
        QuaInspectionOrder order = findOrCreateDraft(request.getReceiptId());

        if (request.getLines() != null) {
            for (InspectionSubmitRequest.Line l : request.getLines()) {
                if (l.getReceiptLineId() != null) {
                    long existing = quaInspectionLineMapper.selectCount(
                            new LambdaQueryWrapper<QuaInspectionLine>()
                                    .eq(QuaInspectionLine::getInspectionId, order.getInspectionId())
                                    .eq(QuaInspectionLine::getReceiptLineId, l.getReceiptLineId()));
                    if (existing > 0) {
                        throw new BusinessException("该物料行已提交检测结果，请勿重复提交");
                    }
                }

                QuaInspectionLine line = new QuaInspectionLine();
                line.setInspectionId(order.getInspectionId());
                line.setReceiptLineId(l.getReceiptLineId());
                line.setItemId(l.getItemId());
                line.setBatchId(l.getBatchId());
                line.setInspectedQty(l.getInspectedQty());
                line.setQualifiedQty(l.getQualifiedQty() != null ? l.getQualifiedQty() : BigDecimal.ZERO);
                line.setUnqualifiedQty(l.getUnqualifiedQty() != null ? l.getUnqualifiedQty() : BigDecimal.ZERO);
                line.setLineResult(l.getLineResult());
                quaInspectionLineMapper.insert(line);

                boolean qualified = "QUALIFIED".equals(l.getLineResult());
                updateBatchQuality(l.getBatchId(), qualified ? "QUALIFIED" : "UNQUALIFIED");
                updateReceiptLine(l.getReceiptLineId(), l.getInspectedQty(), qualified ? "INSPECTED" : "UNQUALIFIED");
                if (!qualified) {
                    createQualityIssue(line.getInspectionLineId(), l.getItemId(), l.getBatchId(),
                            l.getIssueType() != null ? l.getIssueType() : "QUALITY_DEFECT",
                            l.getIssueDesc() != null ? l.getIssueDesc() : "质检不合格",
                            line.getUnqualifiedQty());
                }
            }
        }

        int remaining = countPendingInspectionLines(request.getReceiptId());
        if (remaining == 0) {
            List<QuaInspectionLine> allInsLines = quaInspectionLineMapper.selectList(
                    new LambdaQueryWrapper<QuaInspectionLine>().eq(QuaInspectionLine::getInspectionId, order.getInspectionId()));
            boolean allQualified = allInsLines.stream().allMatch(il -> "QUALIFIED".equals(il.getLineResult()));
            order.setInspectionResult(allQualified ? "QUALIFIED" : "UNQUALIFIED");
            order.setInspectionStatus("CLOSED");
            order.setInspectedBy(request.getInspectedBy());
            order.setInspectedAt(LocalDateTime.now());
        } else {
            order.setInspectionStatus("DRAFT");
        }
        quaInspectionOrderMapper.updateById(order);
        receiptService.syncReceiptStatusFromLines(request.getReceiptId());
        analysisCache.clear();
        return order;
    }

    @Override
    @Transactional
    public InspectionSubmitResultVO saveInspectionResult(InspectionSubmitRequest request) {
        QuaInspectionOrder order = submitInspection(request);
        List<QuaInspectionLine> lines = quaInspectionLineMapper.selectList(
                new LambdaQueryWrapper<QuaInspectionLine>().eq(QuaInspectionLine::getInspectionId, order.getInspectionId()));
        List<Long> lineIds = lines.stream().map(QuaInspectionLine::getInspectionLineId).toList();
        List<Long> issueIds = new ArrayList<>();
        if (!lineIds.isEmpty()) {
            List<QuaQualityIssue> issues = quaQualityIssueMapper.selectList(
                    new LambdaQueryWrapper<QuaQualityIssue>().in(QuaQualityIssue::getInspectionLineId, lineIds));
            issueIds = issues.stream().map(QuaQualityIssue::getIssueId).toList();
        }
        InspectionSubmitResultVO vo = new InspectionSubmitResultVO();
        vo.setHasIssue(!issueIds.isEmpty());
        vo.setIssueIds(issueIds);
        vo.setInspectionId(order.getInspectionId());
        vo.setInspectionNo(order.getInspectionNo());
        vo.setInspectionResult(order.getInspectionResult());
        int remaining = countPendingInspectionLines(request.getReceiptId());
        vo.setRemainingLineCount(remaining);
        vo.setAllComplete(remaining == 0);
        if (remaining > 0) {
            vo.setMessage("当前物料检测完成，还有 " + remaining + " 行待检");
        } else if (issueIds.isEmpty()) {
            int lineCount = request.getLines() != null ? request.getLines().size() : 0;
            vo.setMessage(lineCount > 1
                    ? "检测完成，共 " + lineCount + " 种物料全部合格"
                    : "检测完成，全部合格");
        } else {
            int lineCount = request.getLines() != null ? request.getLines().size() : 0;
            vo.setMessage(lineCount > 1
                    ? "检测完成，共 " + lineCount + " 种物料已登记质量问题"
                    : "检测完成，已登记质量问题");
        }
        return vo;
    }

    @Override
    public Map<String, Object> getInspectionDetail(Long inspectionId) {
        QuaInspectionOrder order = quaInspectionOrderMapper.selectById(inspectionId);
        if (order == null) {
            throw new BusinessException("质检单不存在");
        }
        List<QuaInspectionLine> lines = quaInspectionLineMapper.selectList(
                new LambdaQueryWrapper<QuaInspectionLine>().eq(QuaInspectionLine::getInspectionId, inspectionId));
        Map<String, Object> detail = new HashMap<>();
        detail.put("order", order);
        detail.put("lines", lines);
        return detail;
    }

    @Override
    public Map<String, Object> getTaskDetailByReceiptId(Long receiptId) {
        RecReceiptOrder receipt = recReceiptOrderMapper.selectById(receiptId);
        if (receipt == null) {
            throw new BusinessException("收货单不存在");
        }
        List<RecReceiptLine> receiptLines = recReceiptLineMapper.selectList(
                new LambdaQueryWrapper<RecReceiptLine>().eq(RecReceiptLine::getReceiptId, receiptId));
        QuaInspectionOrder inspection = latestInspection(receiptId);

        Map<Long, MdItem> itemMap = loadItemMap();
        Map<Long, MdBatch> batchMap = loadBatchMap();
        MdSupplier supplier = receipt.getSupplierId() != null ? mdSupplierMapper.selectById(receipt.getSupplierId()) : null;

        List<Map<String, Object>> lineDetails = new ArrayList<>();
        for (RecReceiptLine rl : receiptLines) {
            MdItem item = itemMap.get(rl.getItemId());
            MdBatch batch = batchMap.get(rl.getBatchId());
            Map<String, Object> row = new HashMap<>();
            row.put("receiptLineId", rl.getReceiptLineId());
            row.put("itemId", rl.getItemId());
            row.put("batchId", rl.getBatchId());
            row.put("itemName", item != null ? item.getItemName() : "物料#" + rl.getItemId());
            row.put("itemCode", item != null ? item.getItemCode() : null);
            row.put("batchNo", batch != null ? batch.getBatchNo() : null);
            row.put("receivedQty", rl.getReceivedQty());
            row.put("lineStatus", rl.getLineStatus());
            QuaInspectStandardVO std = qualityMasterDataService.resolveStandardForItem(rl.getItemId());
            List<Map<String, Object>> standards = qualityMasterDataService.toExecutionStandards(std);
            if (standards.isEmpty()) {
                standards = defaultStandards(item);
            }
            row.put("standards", standards);
            if (std != null) {
                row.put("standardCode", std.getStandardCode());
                row.put("standardName", std.getStandardName());
                row.put("aqlLevel", std.getAqlLevel());
                row.put("aqlValue", std.getAqlValue());
                row.put("drawingNo", std.getDrawingNo());
            }
            lineDetails.add(row);
        }

        Map<String, Object> detail = new HashMap<>();
        detail.put("inspection", inspection);
        detail.put("receipt", receipt);
        detail.put("supplierName", supplier != null ? supplier.getSupplierName() : null);
        detail.put("supplierId", receipt.getSupplierId());
        detail.put("strict", isStrictSupplier(receipt.getSupplierId()));
        detail.put("lineDetails", lineDetails);
        return detail;
    }

    @Override
    public QualityWorkbenchVO getWorkbench() {
        List<QualityTaskVO> tasks = buildTasks(null, null, null);
        List<QualityIssueVO> issues = listIssueViews();

        int pending = (int) tasks.stream().filter(t -> t.getStatus().startsWith("待")).count();
        int inspecting = (int) tasks.stream().filter(t -> "检测中".equals(t.getStatus())).count();
        int completedToday = countTodayCompleted();
        int openIssues = (int) issues.stream().filter(i -> !"已关闭".equals(i.getStatus())).count();
        int strict = (int) tasks.stream().filter(QualityTaskVO::isStrict).count();
        double passRate = calcOverallPassRate();

        QualityWorkbenchVO vo = new QualityWorkbenchVO();
        vo.setTasks(tasks);
        vo.setIssues(issues);
        vo.setPendingInspection(pending);
        vo.setInspectingCount(inspecting);
        vo.setTodayCompleted(completedToday);
        vo.setPassRatePercent(passRate);
        vo.setOpenIssues(openIssues);
        vo.setStrictBatches(strict);
        vo.setPassRateTrend(buildPassRateTrend());

        List<Object[]> kpis = new ArrayList<>();
        kpis.add(new Object[]{"待检测任务", String.valueOf(pending), pending > 0 ? "需及时处理" : "暂无积压", pending > 0 ? "warn" : "ok"});
        kpis.add(new Object[]{"今日完成检测", String.valueOf(completedToday), "合格率 " + passRate + "%", "ok"});
        kpis.add(new Object[]{"质量合格率", passRate + "%", "累计质检", ""});
        kpis.add(new Object[]{"未关闭异常", String.valueOf(openIssues), openIssues > 0 ? "需跟进处理" : "全部关闭", openIssues > 0 ? "danger" : "ok"});
        kpis.add(new Object[]{"加严批次", String.valueOf(strict), strict > 0 ? "供应商风险触发" : "无", strict > 0 ? "warn" : ""});
        vo.setDashboardKpis(kpis);
        vo.setKpis(kpis);
        return vo;
    }

    @Override
    public QualityAnalyticsVO getAnalytics() {
        QualityWorkbenchVO wb = getWorkbench();
        QualityAnalyticsVO vo = new QualityAnalyticsVO();
        vo.setKpis(wb.getDashboardKpis());
        vo.setPassRateTrend(wb.getPassRateTrend());
        vo.setIssueTypeStats(buildIssueTypeStats());
        vo.setItemRanking(buildItemRanking());
        vo.setSupplierStats(buildSupplierStats());
        return vo;
    }

    @Override
    public List<QualityTaskVO> listTasks(String status, String batchNo, String keyword) {
        return buildTasks(status, batchNo, keyword);
    }

    @Override
    public Map<String, Object> getIssueDetail(Long issueId) {
        QuaQualityIssue issue = quaQualityIssueMapper.selectById(issueId);
        if (issue == null) {
            throw new BusinessException("质量问题不存在");
        }
        QuaInspectionLine inspectionLine = issue.getInspectionLineId() != null
                ? quaInspectionLineMapper.selectById(issue.getInspectionLineId()) : null;
        QuaInspectionOrder inspection = null;
        if (inspectionLine != null) {
            inspection = quaInspectionOrderMapper.selectById(inspectionLine.getInspectionId());
        }
        MdItem item = issue.getItemId() != null ? mdItemMapper.selectById(issue.getItemId()) : null;
        MdBatch batch = issue.getBatchId() != null ? mdBatchMapper.selectById(issue.getBatchId()) : null;
        MdSupplier supplier = null;
        if (batch != null && batch.getSupplierId() != null) {
            supplier = mdSupplierMapper.selectById(batch.getSupplierId());
        } else if (inspection != null) {
            RecReceiptOrder receipt = recReceiptOrderMapper.selectById(inspection.getReceiptId());
            if (receipt != null && receipt.getSupplierId() != null) {
                supplier = mdSupplierMapper.selectById(receipt.getSupplierId());
            }
        }
        List<InvInventory> inventories = issue.getBatchId() != null
                ? inventoryService.queryInventoryByBatch(issue.getBatchId()) : List.of();
        RecReceiptLine receiptLine = null;
        RecReceiptOrder receipt = null;
        if (inspectionLine != null && inspectionLine.getReceiptLineId() != null) {
            receiptLine = recReceiptLineMapper.selectById(inspectionLine.getReceiptLineId());
            if (receiptLine != null && receiptLine.getReceiptId() != null) {
                receipt = recReceiptOrderMapper.selectById(receiptLine.getReceiptId());
            }
        }
        boolean hasShelvedInventory = hasShelvedInventory(inventories);

        Map<String, Object> detail = new HashMap<>();
        detail.put("issue", toIssueVO(issue, item, batch));
        detail.put("inspection", inspection);
        detail.put("inspectionLine", inspectionLine);
        detail.put("item", item);
        detail.put("batch", batch);
        detail.put("supplier", supplier);
        detail.put("inventories", inventories);
        detail.put("receiptLine", receiptLine);
        detail.put("receipt", receipt);
        detail.put("hasShelvedInventory", hasShelvedInventory);
        detail.put("materialStage", hasShelvedInventory ? "SHELVED" : "PRE_PUTAWAY");
        detail.put("analysis", analysisCache.get(issueId));
        return detail;
    }

    @Override
    public QualityIssueAnalysisVO analyzeIssue(Long issueId) {
        QualityIssueAnalysisVO cached = analysisCache.get(issueId);
        if (cached != null) {
            return cached;
        }
        Map<String, Object> detail = getIssueDetail(issueId);
        QuaQualityIssue issue = quaQualityIssueMapper.selectById(issueId);
        QualityIssueAnalysisVO fallback = buildRuleAnalysis(detail, issue);

        QualityIssueAnalysisVO result = fallback;
        if (Boolean.TRUE.equals(deepSeekProperties.getEnabled())) {
            try {
                String reply = deepSeekChatService.chat(AI_SYSTEM_PROMPT, buildIssuePrompt(detail, issue));
                QualityIssueAnalysisVO ai = parseAiAnalysis(reply);
                if (ai != null) {
                    ai.setAiPowered(true);
                    result = ai;
                }
            } catch (Exception e) {
                log.warn("质量异常 AI 分析失败，使用规则回退: {}", e.getMessage());
            }
        }

        if (issue != null && "OPEN".equals(issue.getIssueStatus())) {
            issue.setIssueStatus("ANALYZED");
            issue.setHandlingSuggestion(result.getRecommendation());
            quaQualityIssueMapper.updateById(issue);
        }
        analysisCache.put(issueId, result);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> freezeIssueInventory(Long issueId, Long operatedBy) {
        QuaQualityIssue issue = requireIssue(issueId);
        List<InvInventory> inventories = inventoryService.queryInventoryByBatch(issue.getBatchId());
        BigDecimal remain = issue.getUnqualifiedQty() != null ? issue.getUnqualifiedQty() : BigDecimal.ZERO;
        List<String> freezeNos = new ArrayList<>();
        if (remain.compareTo(BigDecimal.ZERO) <= 0) {
            remain = inventories.stream()
                    .map(InvInventory::getAvailableQty)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        for (InvInventory inv : inventories) {
            if (remain.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal available = inv.getAvailableQty() != null ? inv.getAvailableQty() : BigDecimal.ZERO;
            if (available.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal qty = available.min(remain);
            InvFreezeRecord record = inventoryService.freezeInventory(
                    inv.getInventoryId(), qty, "质量异常冻结 " + issue.getIssueNo(), operatedBy);
            freezeNos.add(record.getFreezeNo());
            remain = remain.subtract(qty);
        }
        markIssueHandling(issue, "冻结库存");
        Map<String, Object> result = new HashMap<>();
        if (freezeNos.isEmpty()) {
            throw new BusinessException("该批次尚未入库或无可用库存，无需冻结库存。请使用「供应商退货」「返修」或「禁止上架并隔离」");
        }
        result.put("message", "已冻结库存：" + String.join("、", freezeNos));
        result.put("freezeNos", freezeNos);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> isolateIssue(Long issueId, Long operatedBy) {
        QuaQualityIssue issue = requireIssue(issueId);
        List<InvInventory> inventories = issue.getBatchId() != null
                ? inventoryService.queryInventoryByBatch(issue.getBatchId()) : List.of();
        if (hasShelvedInventory(inventories)) {
            throw new BusinessException("该批次已入库，请使用「库存冻结」或其他在库处置方式");
        }
        QuaInspectionLine inspectionLine = issue.getInspectionLineId() != null
                ? quaInspectionLineMapper.selectById(issue.getInspectionLineId()) : null;
        if (inspectionLine != null && inspectionLine.getReceiptLineId() != null) {
            RecReceiptLine receiptLine = recReceiptLineMapper.selectById(inspectionLine.getReceiptLineId());
            if (receiptLine != null) {
                receiptLine.setLineStatus("QUARANTINED");
                recReceiptLineMapper.updateById(receiptLine);
                receiptService.syncReceiptStatusFromLines(receiptLine.getReceiptId());
            }
        }
        markIssueHandling(issue, "隔离禁止上架");
        Map<String, Object> result = new HashMap<>();
        result.put("message", "已标记禁止上架并转入隔离待退，仓管员将不再允许该批物料入库上架");
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> returnIssue(Long issueId, Long operatedBy) {
        QuaQualityIssue issue = requireIssue(issueId);
        MdBatch batch = issue.getBatchId() != null ? mdBatchMapper.selectById(issue.getBatchId()) : null;
        Long supplierId = batch != null ? batch.getSupplierId() : null;
        if (supplierId == null) {
            QuaInspectionLine line = issue.getInspectionLineId() != null
                    ? quaInspectionLineMapper.selectById(issue.getInspectionLineId()) : null;
            if (line != null) {
                QuaInspectionOrder order = quaInspectionOrderMapper.selectById(line.getInspectionId());
                if (order != null) {
                    RecReceiptOrder receipt = recReceiptOrderMapper.selectById(order.getReceiptId());
                    if (receipt != null) supplierId = receipt.getSupplierId();
                }
            }
        }
        if (supplierId == null) {
            throw new BusinessException("无法确定供应商，不能生成退货单");
        }
        QuaReturnLine returnLine = new QuaReturnLine();
        returnLine.setItemId(issue.getItemId());
        returnLine.setBatchId(issue.getBatchId());
        returnLine.setReturnQty(issue.getUnqualifiedQty() != null ? issue.getUnqualifiedQty() : BigDecimal.ONE);
        QuaReturnOrder returnOrder = createReturnOrder(supplierId, issueId, List.of(returnLine));
        markIssueHandling(issue, "退货");
        Map<String, Object> result = new HashMap<>();
        result.put("returnId", returnOrder.getReturnId());
        result.put("returnNo", returnOrder.getReturnNo());
        result.put("message", "已生成退货单 " + returnOrder.getReturnNo());
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> repairIssue(Long issueId) {
        QuaQualityIssue issue = requireIssue(issueId);
        markIssueHandling(issue, "返修");
        Map<String, Object> result = new HashMap<>();
        result.put("message", "已登记返修任务，异常标记为处理中");
        return result;
    }

    @Override
    public QuaQualityIssue createQualityIssue(Long inspectionLineId, Long itemId, Long batchId,
                                              String issueType, String issueDesc, BigDecimal unqualifiedQty) {
        QuaQualityIssue issue = new QuaQualityIssue();
        issue.setIssueNo(NoGenerator.next("QI"));
        issue.setInspectionLineId(inspectionLineId);
        issue.setItemId(itemId);
        issue.setBatchId(batchId);
        issue.setIssueType(issueType);
        issue.setIssueDesc(issueDesc);
        issue.setUnqualifiedQty(unqualifiedQty);
        issue.setIssueStatus("OPEN");
        issue.setCreatedAt(LocalDateTime.now());
        quaQualityIssueMapper.insert(issue);
        return issue;
    }

    @Override
    @Transactional
    public QuaQualityIssue createIssueManual(Map<String, Object> payload) {
        Long itemId = toLong(payload.get("itemId"));
        Long batchId = toLong(payload.get("batchId"));
        if (itemId == null) {
            throw new BusinessException("缺少 itemId");
        }
        String issueType = payload.get("issueType") != null ? String.valueOf(payload.get("issueType")) : "QUALITY_DEFECT";
        String issueDesc = payload.get("issueDesc") != null ? String.valueOf(payload.get("issueDesc")) : "手工登记质量异常";
        BigDecimal qty = toBigDecimal(payload.get("unqualifiedQty"));
        return createQualityIssue(null, itemId, batchId, issueType, issueDesc, qty != null ? qty : BigDecimal.ONE);
    }

    @Override
    public List<QuaQualityIssue> listQualityIssues() {
        return quaQualityIssueMapper.selectList(
                new LambdaQueryWrapper<QuaQualityIssue>().orderByDesc(QuaQualityIssue::getIssueId));
    }

    @Override
    public List<QualityIssueVO> listIssueViews() {
        Map<Long, MdItem> itemMap = loadItemMap();
        Map<Long, MdBatch> batchMap = loadBatchMap();
        return listQualityIssues().stream()
                .map(issue -> toIssueVO(issue, itemMap.get(issue.getItemId()), batchMap.get(issue.getBatchId())))
                .toList();
    }

    @Override
    public void closeQualityIssue(Long issueId) {
        QuaQualityIssue issue = requireIssue(issueId);
        issue.setIssueStatus("CLOSED");
        quaQualityIssueMapper.updateById(issue);
        analysisCache.remove(issueId);
    }

    @Override
    @Transactional
    public QuaReturnOrder createReturnOrder(Long supplierId, Long issueId, List<QuaReturnLine> lines) {
        QuaReturnOrder order = new QuaReturnOrder();
        order.setReturnNo(NoGenerator.next("RT"));
        order.setSupplierId(supplierId);
        order.setIssueId(issueId);
        order.setReturnStatus("DRAFT");
        order.setCreatedAt(LocalDateTime.now());
        quaReturnOrderMapper.insert(order);
        if (lines != null) {
            for (QuaReturnLine line : lines) {
                line.setReturnId(order.getReturnId());
                quaReturnLineMapper.insert(line);
            }
        }
        return order;
    }

    @Override
    public Map<String, Object> getReturnDetail(Long returnId) {
        QuaReturnOrder order = quaReturnOrderMapper.selectById(returnId);
        if (order == null) {
            throw new BusinessException("退货单不存在");
        }
        List<QuaReturnLine> lines = quaReturnLineMapper.selectList(
                new LambdaQueryWrapper<QuaReturnLine>().eq(QuaReturnLine::getReturnId, returnId));
        Map<String, Object> detail = new HashMap<>();
        detail.put("order", order);
        detail.put("lines", lines);
        return detail;
    }

    @Override
    public List<Map<String, Object>> queryTrace(String batchNo, String itemCode, String inspectionNo) {
        List<Map<String, Object>> chain = new ArrayList<>();
        MdBatch batch = null;
        if (StringUtils.hasText(batchNo)) {
            batch = mdBatchMapper.selectList(new LambdaQueryWrapper<MdBatch>().eq(MdBatch::getBatchNo, batchNo))
                    .stream().findFirst().orElse(null);
        }
        MdItem item = null;
        if (StringUtils.hasText(itemCode)) {
            item = mdItemMapper.selectList(new LambdaQueryWrapper<MdItem>().eq(MdItem::getItemCode, itemCode))
                    .stream().findFirst().orElse(null);
        }
        if (batch == null && item != null) {
            batch = mdBatchMapper.selectByItem(item.getItemId()).stream().findFirst().orElse(null);
        }
        if (batch != null) {
            if (item == null) item = mdItemMapper.selectById(batch.getItemId());
            Map<String, Object> node = new HashMap<>();
            node.put("type", "BATCH");
            node.put("title", "批次 " + batch.getBatchNo());
            node.put("status", batch.getQualityStatus());
            node.put("traceCode", batch.getTraceCode());
            node.put("itemName", item != null ? item.getItemName() : null);
            chain.add(node);
            for (InvInventory inv : inventoryService.queryInventoryByBatch(batch.getBatchId())) {
                Map<String, Object> invNode = new HashMap<>();
                invNode.put("type", "INVENTORY");
                invNode.put("title", "库存 #" + inv.getInventoryId());
                invNode.put("onhandQty", inv.getOnhandQty());
                invNode.put("availableQty", inv.getAvailableQty());
                invNode.put("frozenQty", inv.getFrozenQty());
                chain.add(invNode);
            }
        }
        if (StringUtils.hasText(inspectionNo)) {
            QuaInspectionOrder order = quaInspectionOrderMapper.selectList(
                            new LambdaQueryWrapper<QuaInspectionOrder>().eq(QuaInspectionOrder::getInspectionNo, inspectionNo))
                    .stream().findFirst().orElse(null);
            if (order != null) {
                Map<String, Object> node = new HashMap<>();
                node.put("type", "INSPECTION");
                node.put("title", "质检单 " + order.getInspectionNo());
                node.put("result", order.getInspectionResult());
                node.put("status", order.getInspectionStatus());
                chain.add(node);
            }
        }
        return chain;
    }

    // ---------- helpers ----------

    private List<QualityTaskVO> buildTasks(String status, String batchNo, String keyword) {
        Map<Long, MdItem> itemMap = loadItemMap();
        Map<Long, MdBatch> batchMap = loadBatchMap();
        Map<Long, MdSupplier> supplierMap = loadSupplierMap();
        Map<Long, List<QuaInspectionOrder>> inspectionByReceipt = quaInspectionOrderMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(QuaInspectionOrder::getReceiptId));

        List<RecReceiptOrder> receipts = recReceiptOrderMapper.selectList(
                new LambdaQueryWrapper<RecReceiptOrder>().orderByDesc(RecReceiptOrder::getReceiptId));
        List<QualityTaskVO> tasks = new ArrayList<>();
        for (RecReceiptOrder receipt : receipts) {
            String mapped = mapReceiptTaskStatus(receipt, inspectionByReceipt.get(receipt.getReceiptId()));
            if ("忽略".equals(mapped)) continue;

            List<RecReceiptLine> lines = recReceiptLineMapper.selectList(
                    new LambdaQueryWrapper<RecReceiptLine>().eq(RecReceiptLine::getReceiptId, receipt.getReceiptId()));
            List<String> productNames = new ArrayList<>();
            List<String> batchNos = new ArrayList<>();
            for (RecReceiptLine rl : lines) {
                MdItem it = itemMap.get(rl.getItemId());
                if (it != null && it.getItemName() != null && !productNames.contains(it.getItemName())) {
                    productNames.add(it.getItemName());
                }
                MdBatch bt = batchMap.get(rl.getBatchId());
                if (bt != null && bt.getBatchNo() != null && !batchNos.contains(bt.getBatchNo())) {
                    batchNos.add(bt.getBatchNo());
                }
            }
            MdSupplier supplier = supplierMap.get(receipt.getSupplierId());
            QuaInspectionOrder latest = latestOf(inspectionByReceipt.get(receipt.getReceiptId()));

            BigDecimal qty = lines.stream()
                    .map(RecReceiptLine::getReceivedQty)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            QualityTaskVO task = new QualityTaskVO();
            task.setReceiptId(receipt.getReceiptId());
            task.setInspectionId(latest != null ? latest.getInspectionId() : null);
            task.setId(latest != null ? latest.getInspectionNo() : "待生成");
            task.setReceiptNo(receipt.getReceiptNo());
            task.setProduct(formatMultiValueSummary(productNames, lines.isEmpty() ? "—" : "物料", "种"));
            task.setBatchNo(formatMultiValueSummary(batchNos, "—", "批"));
            task.setQty(qty.stripTrailingZeros().toPlainString() + " 件");
            task.setStatus(mapped);
            task.setStrict(isStrictSupplier(receipt.getSupplierId()));
            task.setHasInspection(latest != null);
            task.setSupplierId(receipt.getSupplierId());
            task.setSupplierName(supplier != null ? supplier.getSupplierName() : null);
            task.setWarehouseId(receipt.getWarehouseId());

            if (StringUtils.hasText(status) && !status.equals(task.getStatus())) continue;
            if (StringUtils.hasText(batchNo)) {
                String bn = batchNo.trim();
                boolean batchMatch = batchNos.stream().anyMatch(b -> b.contains(bn));
                if (!batchMatch) continue;
            }
            if (StringUtils.hasText(keyword)) {
                String kw = keyword.trim().toLowerCase(Locale.ROOT);
                String hay = String.join(" ",
                        nullSafe(task.getReceiptNo()), nullSafe(task.getProduct()),
                        String.join(" ", productNames),
                        nullSafe(task.getId()), nullSafe(task.getBatchNo()),
                        String.join(" ", batchNos),
                        nullSafe(task.getSupplierName())
                ).toLowerCase(Locale.ROOT);
                if (!hay.contains(kw)) continue;
            }
            tasks.add(task);
        }
        return tasks;
    }

    private String mapReceiptTaskStatus(RecReceiptOrder receipt, List<QuaInspectionOrder> inspections) {
        String rs = receipt.getReceiptStatus();
        if ("INSPECTED".equals(rs) || "STORED".equals(rs) || "AVAILABLE".equals(rs)) {
            return "完成";
        }
        if ("INSPECTION_FAILED".equals(rs) || "QUALITY_FAIL".equals(rs)) {
            return "完成";
        }

        int pendingLines = countPendingInspectionLines(receipt.getReceiptId());
        QuaInspectionOrder latest = latestOf(inspections);
        if (pendingLines == 0) {
            if (latest != null && "CLOSED".equals(latest.getInspectionStatus())) {
                return "完成";
            }
            List<RecReceiptLine> lines = recReceiptLineMapper.selectList(
                    new LambdaQueryWrapper<RecReceiptLine>().eq(RecReceiptLine::getReceiptId, receipt.getReceiptId()));
            boolean anyFinished = lines.stream().anyMatch(l -> {
                String ls = l.getLineStatus();
                return "INSPECTED".equals(ls) || "QUALIFIED".equals(ls)
                        || "UNQUALIFIED".equals(ls) || "INSPECTION_FAILED".equals(ls)
                        || "QUALITY_FAIL".equals(ls);
            });
            if (anyFinished) {
                return "完成";
            }
        }

        if (latest != null && "CLOSED".equals(latest.getInspectionStatus()) && pendingLines == 0) {
            return "完成";
        }
        if (latest != null && "DRAFT".equals(latest.getInspectionStatus())) {
            return "检测中";
        }
        if ("INSPECTING".equals(rs)) {
            return "检测中";
        }
        if ("PENDING_INSPECTION".equals(rs)) {
            return "待检测";
        }
        return "忽略";
    }

    private QuaInspectionOrder latestOf(List<QuaInspectionOrder> list) {
        if (list == null || list.isEmpty()) return null;
        return list.stream().max(Comparator.comparing(QuaInspectionOrder::getInspectionId)).orElse(null);
    }

    private QuaInspectionOrder latestInspection(Long receiptId) {
        return latestOf(quaInspectionOrderMapper.selectByReceiptId(receiptId));
    }

    private QuaInspectionOrder findOrCreateDraft(Long receiptId) {
        List<QuaInspectionOrder> existing = quaInspectionOrderMapper.selectByReceiptId(receiptId);
        for (QuaInspectionOrder order : existing) {
            if ("DRAFT".equals(order.getInspectionStatus())) {
                return order;
            }
        }
        return createInspection(receiptId);
    }

    private void updateBatchQuality(Long batchId, String status) {
        if (batchId == null) return;
        MdBatch batch = mdBatchMapper.selectById(batchId);
        if (batch != null) {
            batch.setQualityStatus(status);
            mdBatchMapper.updateById(batch);
        }
    }

    private int countPendingInspectionLines(Long receiptId) {
        if (receiptId == null) return 0;
        Long count = recReceiptLineMapper.selectCount(
                new LambdaQueryWrapper<RecReceiptLine>()
                        .eq(RecReceiptLine::getReceiptId, receiptId)
                        .in(RecReceiptLine::getLineStatus, "INSPECTING", "PENDING_INSPECTION"));
        return count == null ? 0 : count.intValue();
    }

    private String formatMultiValueSummary(List<String> values, String emptyFallback, String unit) {
        if (values == null || values.isEmpty()) {
            return emptyFallback;
        }
        if (values.size() == 1) {
            return values.get(0);
        }
        return values.get(0) + " 等" + values.size() + unit;
    }

    private void updateReceiptLine(Long receiptLineId, BigDecimal inspectedQty, String lineStatus) {
        if (receiptLineId == null) return;
        RecReceiptLine line = recReceiptLineMapper.selectById(receiptLineId);
        if (line != null) {
            line.setInspectedQty(inspectedQty != null ? inspectedQty : line.getReceivedQty());
            line.setLineStatus(lineStatus);
            recReceiptLineMapper.updateById(line);
        }
    }

    private QualityIssueVO toIssueVO(QuaQualityIssue issue, MdItem item, MdBatch batch) {
        QualityIssueVO vo = new QualityIssueVO();
        vo.setIssueId(issue.getIssueId());
        vo.setIssueNo(issue.getIssueNo());
        vo.setItemId(issue.getItemId());
        vo.setItemName(item != null ? item.getItemName() : "物料#" + issue.getItemId());
        vo.setBatchId(issue.getBatchId());
        vo.setBatchNo(batch != null ? batch.getBatchNo() : "—");
        vo.setIssueDesc(issue.getIssueDesc());
        vo.setIssueType(mapIssueTypeLabel(issue.getIssueType()));
        String risk = guessRiskLevel(issue);
        vo.setRiskLevel(risk);
        vo.setLevel(risk);
        vo.setStatus(mapIssueStatus(issue.getIssueStatus()));
        vo.setIssueStatus(issue.getIssueStatus());
        vo.setUnqualifiedQty(issue.getUnqualifiedQty());
        return vo;
    }

    private String mapIssueStatus(String status) {
        if (status == null) return "待分析";
        return switch (status) {
            case "OPEN" -> "待分析";
            case "ANALYZED" -> "分析完成";
            case "HANDLING" -> "处理中";
            case "CLOSED" -> "已关闭";
            default -> status;
        };
    }

    private String mapIssueTypeLabel(String type) {
        if (type == null) return "质量缺陷";
        return switch (type) {
            case "APPEARANCE", "外观缺陷" -> "外观缺陷";
            case "DIMENSION", "尺寸超差" -> "尺寸超差";
            case "PERFORMANCE", "性能不合格" -> "性能不合格";
            case "PACKAGING", "包装破损" -> "包装破损";
            case "QUALITY_DEFECT" -> "质量缺陷";
            default -> type;
        };
    }

    private String guessRiskLevel(QuaQualityIssue issue) {
        if (issue.getUnqualifiedQty() != null && issue.getUnqualifiedQty().compareTo(new BigDecimal("5")) >= 0) {
            return "高";
        }
        String type = issue.getIssueType() != null ? issue.getIssueType() : "";
        if (type.contains("PERFORMANCE") || type.contains("性能") || type.contains("DIMENSION") || type.contains("尺寸")) {
            return "高";
        }
        if (type.contains("APPEARANCE") || type.contains("外观")) {
            return "中";
        }
        return "中";
    }

    private boolean isStrictSupplier(Long supplierId) {
        if (supplierId == null) return false;
        long openCount = listQualityIssues().stream()
                .filter(i -> OPEN_ISSUE.contains(i.getIssueStatus()))
                .filter(i -> {
                    MdBatch batch = i.getBatchId() != null ? mdBatchMapper.selectById(i.getBatchId()) : null;
                    return batch != null && Objects.equals(supplierId, batch.getSupplierId());
                })
                .count();
        return openCount >= 2 || Objects.equals(supplierId, 3L);
    }

    private List<Map<String, Object>> defaultStandards(MdItem item) {
        String name = item != null ? item.getItemName() : "物料";
        List<Map<String, Object>> standards = new ArrayList<>();
        standards.add(Map.of(
                "code", "APPEARANCE",
                "name", "外观检测",
                "standard", name + "：表面无划痕、无锈蚀、无变形，标识清晰",
                "type", "choice"
        ));
        Map<String, Object> dim = new LinkedHashMap<>();
        dim.put("code", "DIMENSION");
        dim.put("name", "尺寸检测");
        dim.put("standard", "关键尺寸按图纸公差执行（默认标称 10.00±0.05）");
        dim.put("type", "numeric");
        dim.put("nominal", 10.00);
        dim.put("tolerance", 0.05);
        dim.put("unit", "mm");
        standards.add(dim);
        standards.add(Map.of(
                "code", "PRESSURE",
                "name", "性能/耐压测试",
                "standard", "按物料规范执行耐压或功能测试，结果判定合格/不合格",
                "type", "performance",
                "unit", "V"
        ));
        return standards;
    }

    private int countTodayCompleted() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        return (int) quaInspectionOrderMapper.selectList(
                        new LambdaQueryWrapper<QuaInspectionOrder>()
                                .eq(QuaInspectionOrder::getInspectionStatus, "CLOSED")
                                .ge(QuaInspectionOrder::getInspectedAt, start))
                .size();
    }

    private double calcOverallPassRate() {
        List<QuaInspectionLine> lines = quaInspectionLineMapper.selectList(null);
        if (lines.isEmpty()) return 100.0;
        BigDecimal inspected = BigDecimal.ZERO;
        BigDecimal qualified = BigDecimal.ZERO;
        for (QuaInspectionLine line : lines) {
            if (line.getInspectedQty() != null) inspected = inspected.add(line.getInspectedQty());
            if (line.getQualifiedQty() != null) qualified = qualified.add(line.getQualifiedQty());
        }
        if (inspected.compareTo(BigDecimal.ZERO) <= 0) return 100.0;
        return qualified.multiply(new BigDecimal("100"))
                .divide(inspected, 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private List<QualityAnalyticsVO.PassRatePoint> buildPassRateTrend() {
        List<QualityAnalyticsVO.PassRatePoint> points = new ArrayList<>();
        LocalDate today = LocalDate.now();
        List<QuaInspectionOrder> closed = quaInspectionOrderMapper.selectList(
                new LambdaQueryWrapper<QuaInspectionOrder>()
                        .eq(QuaInspectionOrder::getInspectionStatus, "CLOSED")
                        .ge(QuaInspectionOrder::getInspectedAt, today.minusDays(6).atStartOfDay()));
        Map<Long, List<QuaInspectionLine>> linesByInspection = quaInspectionLineMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(QuaInspectionLine::getInspectionId));

        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            List<QuaInspectionOrder> dayOrders = closed.stream()
                    .filter(o -> o.getInspectedAt() != null && o.getInspectedAt().toLocalDate().equals(day))
                    .toList();
            BigDecimal inspected = BigDecimal.ZERO;
            BigDecimal qualified = BigDecimal.ZERO;
            for (QuaInspectionOrder order : dayOrders) {
                for (QuaInspectionLine line : linesByInspection.getOrDefault(order.getInspectionId(), List.of())) {
                    if (line.getInspectedQty() != null) inspected = inspected.add(line.getInspectedQty());
                    if (line.getQualifiedQty() != null) qualified = qualified.add(line.getQualifiedQty());
                }
            }
            QualityAnalyticsVO.PassRatePoint point = new QualityAnalyticsVO.PassRatePoint();
            point.setDay(day.format(DAY_FMT));
            point.setInspectedCount(inspected.intValue());
            point.setQualifiedCount(qualified.intValue());
            point.setPassRate(inspected.compareTo(BigDecimal.ZERO) <= 0 ? 100.0
                    : qualified.multiply(new BigDecimal("100")).divide(inspected, 1, RoundingMode.HALF_UP).doubleValue());
            points.add(point);
        }
        return points;
    }

    private List<QualityAnalyticsVO.IssueTypeStat> buildIssueTypeStats() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (QuaQualityIssue issue : listQualityIssues()) {
            String label = mapIssueTypeLabel(issue.getIssueType());
            counts.merge(label, 1, Integer::sum);
        }
        List<QualityAnalyticsVO.IssueTypeStat> stats = new ArrayList<>();
        counts.forEach((label, count) -> {
            QualityAnalyticsVO.IssueTypeStat s = new QualityAnalyticsVO.IssueTypeStat();
            s.setType(label);
            s.setLabel(label);
            s.setCount(count);
            stats.add(s);
        });
        return stats;
    }

    private List<QualityAnalyticsVO.ItemRank> buildItemRanking() {
        Map<Long, MdItem> itemMap = loadItemMap();
        Map<Long, Long> counts = listQualityIssues().stream()
                .filter(i -> i.getItemId() != null)
                .collect(Collectors.groupingBy(QuaQualityIssue::getItemId, Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> {
                    QualityAnalyticsVO.ItemRank rank = new QualityAnalyticsVO.ItemRank();
                    MdItem item = itemMap.get(e.getKey());
                    rank.setItemName(item != null ? item.getItemName() : "物料#" + e.getKey());
                    rank.setIssueCount(e.getValue().intValue());
                    rank.setRiskLevel(e.getValue() >= 3 ? "高" : e.getValue() == 2 ? "中" : "低");
                    return rank;
                })
                .toList();
    }

    private List<QualityAnalyticsVO.SupplierStat> buildSupplierStats() {
        Map<Long, MdSupplier> supplierMap = loadSupplierMap();
        Map<Long, MdBatch> batchMap = loadBatchMap();
        Map<Long, List<QuaQualityIssue>> bySupplier = new HashMap<>();
        for (QuaQualityIssue issue : listQualityIssues()) {
            MdBatch batch = batchMap.get(issue.getBatchId());
            if (batch == null || batch.getSupplierId() == null) continue;
            bySupplier.computeIfAbsent(batch.getSupplierId(), k -> new ArrayList<>()).add(issue);
        }
        List<QualityAnalyticsVO.SupplierStat> stats = new ArrayList<>();
        for (MdSupplier supplier : supplierMap.values()) {
            List<QuaQualityIssue> issues = bySupplier.getOrDefault(supplier.getSupplierId(), List.of());
            long batchCount = batchMap.values().stream()
                    .filter(b -> Objects.equals(b.getSupplierId(), supplier.getSupplierId()))
                    .count();
            if (batchCount == 0 && issues.isEmpty()) continue;
            QualityAnalyticsVO.SupplierStat s = new QualityAnalyticsVO.SupplierStat();
            s.setSupplierName(supplier.getSupplierName());
            s.setBatchCount((int) batchCount);
            s.setIssueCount(issues.size());
            double pass = batchCount == 0 ? 100.0
                    : Math.max(0, 100.0 - (issues.size() * 100.0 / Math.max(batchCount, 1)));
            s.setPassRate(Math.round(pass * 10) / 10.0);
            stats.add(s);
        }
        stats.sort(Comparator.comparingInt(QualityAnalyticsVO.SupplierStat::getIssueCount).reversed());
        return stats;
    }

    private QualityIssueAnalysisVO buildRuleAnalysis(Map<String, Object> detail, QuaQualityIssue issue) {
        QualityIssueAnalysisVO vo = new QualityIssueAnalysisVO();
        String risk = issue != null ? guessRiskLevel(issue) : "中";
        vo.setRiskLevel(risk);
        vo.setRootCause("来料质量波动，可能与供应商加工/包装环节有关");
        MdItem item = (MdItem) detail.get("item");
        MdSupplier supplier = (MdSupplier) detail.get("supplier");
        boolean shelved = Boolean.TRUE.equals(detail.get("hasShelvedInventory"));
        if (shelved) {
            vo.setImpactScope("当前不合格数量 "
                    + (issue != null && issue.getUnqualifiedQty() != null ? issue.getUnqualifiedQty() : "—")
                    + "，同批次在库库存需隔离并冻结");
            vo.setRecommendation("冻结库存");
            vo.setAnalysisReport("【规则分析】物料 "
                    + (item != null ? item.getItemName() : "未知")
                    + "，供应商 "
                    + (supplier != null ? supplier.getSupplierName() : "未知")
                    + "，问题类型 "
                    + (issue != null ? mapIssueTypeLabel(issue.getIssueType()) : "质量缺陷")
                    + "。该批次已入库，建议先冻结可用库存，再决定退货或返修。");
        } else {
            vo.setImpactScope("当前不合格数量 "
                    + (issue != null && issue.getUnqualifiedQty() != null ? issue.getUnqualifiedQty() : "—")
                    + "，物料尚未入库上架，应禁止上架并隔离待退");
            vo.setRecommendation("供应商退货");
            vo.setAnalysisReport("【规则分析】物料 "
                    + (item != null ? item.getItemName() : "未知")
                    + "，供应商 "
                    + (supplier != null ? supplier.getSupplierName() : "未知")
                    + "，问题类型 "
                    + (issue != null ? mapIssueTypeLabel(issue.getIssueType()) : "质量缺陷")
                    + "。该批次尚未入库，无需冻结库存，建议禁止上架并发起供应商退货或返修。");
        }
        vo.setAiPowered(false);
        return vo;
    }

    private boolean hasShelvedInventory(List<InvInventory> inventories) {
        if (inventories == null || inventories.isEmpty()) {
            return false;
        }
        for (InvInventory inv : inventories) {
            BigDecimal available = inv.getAvailableQty() != null ? inv.getAvailableQty() : BigDecimal.ZERO;
            BigDecimal onhand = inv.getOnhandQty() != null ? inv.getOnhandQty() : BigDecimal.ZERO;
            if (available.compareTo(BigDecimal.ZERO) > 0 || onhand.compareTo(BigDecimal.ZERO) > 0) {
                return true;
            }
        }
        return false;
    }

    private String buildIssuePrompt(Map<String, Object> detail, QuaQualityIssue issue) {
        MdItem item = (MdItem) detail.get("item");
        MdBatch batch = (MdBatch) detail.get("batch");
        MdSupplier supplier = (MdSupplier) detail.get("supplier");
        return """
                问题编号: %s
                物料: %s
                批次: %s
                供应商: %s
                问题类型: %s
                问题描述: %s
                不合格数量: %s
                """.formatted(
                issue.getIssueNo(),
                item != null ? item.getItemName() : issue.getItemId(),
                batch != null ? batch.getBatchNo() : issue.getBatchId(),
                supplier != null ? supplier.getSupplierName() : "未知",
                issue.getIssueType(),
                issue.getIssueDesc(),
                issue.getUnqualifiedQty()
        );
    }

    private QualityIssueAnalysisVO parseAiAnalysis(String reply) {
        if (!StringUtils.hasText(reply)) return null;
        try {
            String json = reply.trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }
            JsonNode node = objectMapper.readTree(json);
            QualityIssueAnalysisVO vo = new QualityIssueAnalysisVO();
            vo.setRiskLevel(textOr(node, "riskLevel", "中"));
            vo.setRootCause(textOr(node, "rootCause", "来料质量异常"));
            vo.setImpactScope(textOr(node, "impactScope", "当前批次需隔离"));
            vo.setRecommendation(textOr(node, "recommendation", "冻结库存"));
            vo.setAnalysisReport(textOr(node, "analysisReport", reply));
            return vo;
        } catch (Exception e) {
            return null;
        }
    }

    private String textOr(JsonNode node, String field, String fallback) {
        JsonNode v = node.get(field);
        return v != null && StringUtils.hasText(v.asText()) ? v.asText() : fallback;
    }

    private QuaQualityIssue requireIssue(Long issueId) {
        QuaQualityIssue issue = quaQualityIssueMapper.selectById(issueId);
        if (issue == null) throw new BusinessException("质量问题不存在");
        return issue;
    }

    private void markIssueHandling(QuaQualityIssue issue, String suggestion) {
        issue.setIssueStatus("HANDLING");
        issue.setHandlingSuggestion(suggestion);
        quaQualityIssueMapper.updateById(issue);
        analysisCache.remove(issue.getIssueId());
    }

    private Map<Long, MdItem> loadItemMap() {
        return mdItemMapper.selectList(null).stream()
                .collect(Collectors.toMap(MdItem::getItemId, i -> i, (a, b) -> a));
    }

    private Map<Long, MdBatch> loadBatchMap() {
        return mdBatchMapper.selectList(null).stream()
                .collect(Collectors.toMap(MdBatch::getBatchId, b -> b, (a, b) -> a));
    }

    private Map<Long, MdSupplier> loadSupplierMap() {
        return mdSupplierMapper.selectList(null).stream()
                .collect(Collectors.toMap(MdSupplier::getSupplierId, s -> s, (a, b) -> a));
    }

    private String nullSafe(String v) {
        return v == null ? "" : v;
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }
}
