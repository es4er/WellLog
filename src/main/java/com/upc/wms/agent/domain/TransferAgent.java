package com.upc.wms.agent.domain;

import com.upc.wms.agent.core.Agent;
import com.upc.wms.agent.core.AgentContext;
import com.upc.wms.agent.core.AgentDataUtils;
import com.upc.wms.agent.core.AgentNames;
import com.upc.wms.agent.core.AgentResult;
import com.upc.wms.agent.core.AgentTaskType;
import com.upc.wms.dto.TransferCreateRequest;
import com.upc.wms.entity.WhTransferLine;
import com.upc.wms.entity.WhTransferOrder;
import com.upc.wms.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 移库智能体：创建移库单并确认移库。源库位扣减、目标库位增加与移库流水由
 * {@code TransferService.confirmTransfer} 在事务内通过 {@code InventoryService} 完成，随后交库存智能体核对。
 */
@Component
@RequiredArgsConstructor
public class TransferAgent implements Agent {

    private final TransferService transferService;

    @Override
    public String getName() {
        return AgentNames.TRANSFER;
    }

    @Override
    public boolean support(String taskType) {
        return AgentTaskType.INVENTORY_TRANSFER.name().equals(taskType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public AgentResult handle(AgentContext context) {
        Map<String, Object> data = context.getData();
        Long operatedBy = AgentDataUtils.getLong(data, "operatedBy", context.getCreatedBy());

        TransferCreateRequest request = AgentDataUtils.convert(data, TransferCreateRequest.class);
        if (request.getOperatedBy() == null) {
            request.setOperatedBy(operatedBy);
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            return AgentResult.failed("移库明细为空，无法创建移库单");
        }

        WhTransferOrder order = transferService.createTransfer(request);
        transferService.confirmTransfer(order.getTransferId(), operatedBy);

        Map<String, Object> detail = transferService.getTransferDetail(order.getTransferId());
        List<WhTransferLine> lines = (List<WhTransferLine>) detail.get("lines");
        List<Map<String, Object>> affected = new ArrayList<>();
        for (WhTransferLine l : lines) {
            Map<String, Object> m = new HashMap<>();
            m.put("itemId", l.getItemId());
            m.put("batchId", l.getBatchId());
            affected.add(m);
        }
        context.put("transferId", order.getTransferId());
        context.put("affectedLines", affected);

        return AgentResult.success("移库确认完成，共 " + lines.size() + " 行")
                .business(order.getTransferNo())
                .next(AgentNames.INVENTORY)
                .put("transferId", order.getTransferId())
                .put("transferNo", order.getTransferNo());
    }
}
