package com.upc.wms.agent.domain;

import com.upc.wms.agent.core.Agent;
import com.upc.wms.agent.core.AgentContext;
import com.upc.wms.agent.core.AgentDataUtils;
import com.upc.wms.agent.core.AgentNames;
import com.upc.wms.agent.core.AgentResult;
import com.upc.wms.agent.core.AgentTaskType;
import com.upc.wms.dto.ReceiptCreateRequest;
import com.upc.wms.entity.RecReceiptLine;
import com.upc.wms.entity.RecReceiptOrder;
import com.upc.wms.service.ReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 收货智能体：负责供应商到货后的收货登记，登记完成后将流程交给质检智能体。
 */
@Component
@RequiredArgsConstructor
public class ReceivingAgent implements Agent {

    private final ReceiptService receiptService;

    @Override
    public String getName() {
        return AgentNames.RECEIVING;
    }

    @Override
    public boolean support(String taskType) {
        return AgentTaskType.RECEIPT_INSPECTION_INBOUND.name().equals(taskType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public AgentResult handle(AgentContext context) {
        Map<String, Object> data = context.getData();

        ReceiptCreateRequest request = AgentDataUtils.convert(data, ReceiptCreateRequest.class);
        if (request.getReceivedBy() == null) {
            request.setReceivedBy(context.getCreatedBy());
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            return AgentResult.failed("收货明细为空，无法创建收货单");
        }

        RecReceiptOrder order = receiptService.createReceipt(request);

        Map<String, Object> detail = receiptService.getReceiptDetail(order.getReceiptId());
        List<RecReceiptLine> lines = (List<RecReceiptLine>) detail.get("lines");

        List<Map<String, Object>> lineInfos = new ArrayList<>();
        for (RecReceiptLine l : lines) {
            Map<String, Object> m = new HashMap<>();
            m.put("receiptLineId", l.getReceiptLineId());
            m.put("itemId", l.getItemId());
            m.put("batchId", l.getBatchId());
            m.put("receivedQty", l.getReceivedQty());
            lineInfos.add(m);
        }

        context.put("receiptId", order.getReceiptId());
        context.put("receiptNo", order.getReceiptNo());
        context.put("warehouseId", order.getWarehouseId());
        context.put("receiptLines", lineInfos);

        return AgentResult.success("收货登记完成，已进入待质检状态")
                .business(order.getReceiptNo())
                .next(AgentNames.QUALITY)
                .processing("创建收货单并写入收货明细，流转质检")
                .evidence("rcv_receipt#" + order.getReceiptId(), "receiptNo=" + order.getReceiptNo(),
                        "明细行=" + lineInfos.size())
                .put("receiptId", order.getReceiptId())
                .put("receiptNo", order.getReceiptNo())
                .put("lineCount", lineInfos.size());
    }
}
