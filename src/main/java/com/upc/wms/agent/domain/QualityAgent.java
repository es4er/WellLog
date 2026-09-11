package com.upc.wms.agent.domain;

import com.upc.wms.agent.core.Agent;
import com.upc.wms.agent.core.AgentContext;
import com.upc.wms.agent.core.AgentDataUtils;
import com.upc.wms.agent.core.AgentNames;
import com.upc.wms.agent.core.AgentResult;
import com.upc.wms.agent.core.AgentTaskType;
import com.upc.wms.dto.InspectionSubmitRequest;
import com.upc.wms.entity.QuaInspectionLine;
import com.upc.wms.entity.QuaInspectionOrder;
import com.upc.wms.service.QualityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 质检智能体：负责收货后的质量检验。
 * <p>
 * 默认将全部收货数量判为合格；也支持前端通过 data.inspection 逐行指定合格/不合格数量。
 * 若存在合格数量则触发入库智能体；若全部不合格则生成质量问题并直接转交审计智能体。
 */
@Component
@RequiredArgsConstructor
public class QualityAgent implements Agent {

    private final QualityService qualityService;

    @Override
    public String getName() {
        return AgentNames.QUALITY;
    }

    @Override
    public boolean support(String taskType) {
        return AgentTaskType.RECEIPT_INSPECTION_INBOUND.name().equals(taskType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public AgentResult handle(AgentContext context) {
        Map<String, Object> data = context.getData();
        Long receiptId = AgentDataUtils.getLong(data, "receiptId");
        if (receiptId == null) {
            return AgentResult.failed("缺少 receiptId，无法执行质检");
        }
        List<Map<String, Object>> receiptLines = AgentDataUtils.getMapList(data, "receiptLines");

        // 前端可选覆盖：inspection = [{receiptLineId, qualifiedQty, unqualifiedQty, lineResult, issueType, issueDesc}]
        Map<Long, Map<String, Object>> overrides = new HashMap<>();
        for (Map<String, Object> o : AgentDataUtils.getMapList(data, "inspection")) {
            Long rlId = AgentDataUtils.getLong(o, "receiptLineId");
            if (rlId != null) {
                overrides.put(rlId, o);
            }
        }

        InspectionSubmitRequest request = new InspectionSubmitRequest();
        request.setReceiptId(receiptId);
        request.setInspectedBy(AgentDataUtils.getLong(data, "inspectedBy", context.getCreatedBy()));

        List<InspectionSubmitRequest.Line> lines = new ArrayList<>();
        for (Map<String, Object> rl : receiptLines) {
            Long receiptLineId = AgentDataUtils.getLong(rl, "receiptLineId");
            BigDecimal received = AgentDataUtils.getBigDecimal(rl.get("receivedQty"));
            if (received == null) {
                received = BigDecimal.ZERO;
            }

            InspectionSubmitRequest.Line line = new InspectionSubmitRequest.Line();
            line.setReceiptLineId(receiptLineId);
            line.setItemId(AgentDataUtils.getLong(rl, "itemId"));
            line.setBatchId(AgentDataUtils.getLong(rl, "batchId"));
            line.setInspectedQty(received);

            Map<String, Object> ov = overrides.get(receiptLineId);
            if (ov != null) {
                BigDecimal qualified = AgentDataUtils.getBigDecimal(ov.get("qualifiedQty"));
                BigDecimal unqualified = AgentDataUtils.getBigDecimal(ov.get("unqualifiedQty"));
                qualified = qualified == null ? BigDecimal.ZERO : qualified;
                unqualified = unqualified == null ? BigDecimal.ZERO : unqualified;
                line.setQualifiedQty(qualified);
                line.setUnqualifiedQty(unqualified);
                String lineResult = AgentDataUtils.getString(ov, "lineResult");
                line.setLineResult(lineResult != null ? lineResult
                        : (unqualified.compareTo(BigDecimal.ZERO) > 0 ? "UNQUALIFIED" : "QUALIFIED"));
                line.setIssueType(AgentDataUtils.getString(ov, "issueType"));
                line.setIssueDesc(AgentDataUtils.getString(ov, "issueDesc"));
            } else {
                line.setQualifiedQty(received);
                line.setUnqualifiedQty(BigDecimal.ZERO);
                line.setLineResult("QUALIFIED");
            }
            lines.add(line);
        }
        request.setLines(lines);

        QuaInspectionOrder inspection = qualityService.submitInspection(request);

        Map<String, Object> detail = qualityService.getInspectionDetail(inspection.getInspectionId());
        List<QuaInspectionLine> insLines = (List<QuaInspectionLine>) detail.get("lines");

        List<Map<String, Object>> qualifiedLines = new ArrayList<>();
        for (QuaInspectionLine il : insLines) {
            BigDecimal q = il.getQualifiedQty() == null ? BigDecimal.ZERO : il.getQualifiedQty();
            if (q.compareTo(BigDecimal.ZERO) > 0) {
                Map<String, Object> m = new HashMap<>();
                m.put("inspectionLineId", il.getInspectionLineId());
                m.put("itemId", il.getItemId());
                m.put("batchId", il.getBatchId());
                m.put("qualifiedQty", q);
                qualifiedLines.add(m);
            }
        }

        context.put("inspectionId", inspection.getInspectionId());
        context.put("qualifiedLines", qualifiedLines);

        if (qualifiedLines.isEmpty()) {
            context.put("inboundSkipped", true);
            return AgentResult.success("质检全部不合格，已生成质量问题，流程不进入入库")
                    .business(inspection.getInspectionNo())
                    .next(AgentNames.AUDIT)
                    .processing("质检判定全部不合格，登记质量问题并跳过入库")
                    .evidence("qua_inspection#" + inspection.getInspectionId(),
                            "inspectionNo=" + inspection.getInspectionNo(), "合格行=0")
                    .put("inspectionResult", inspection.getInspectionResult())
                    .put("qualifiedLineCount", 0);
        }

        return AgentResult.success("质检完成，合格 " + qualifiedLines.size() + " 行，进入入库流程")
                .business(inspection.getInspectionNo())
                .next(AgentNames.INBOUND)
                .processing("质检判定完成，合格行写入上下文后交入库智能体")
                .evidence("qua_inspection#" + inspection.getInspectionId(),
                        "inspectionNo=" + inspection.getInspectionNo(),
                        "合格行=" + qualifiedLines.size())
                .put("inspectionResult", inspection.getInspectionResult())
                .put("qualifiedLineCount", qualifiedLines.size());
    }
}
