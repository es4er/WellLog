package com.upc.wms.agent.domain;

import com.upc.wms.agent.core.Agent;
import com.upc.wms.agent.core.AgentContext;
import com.upc.wms.agent.core.AgentDataUtils;
import com.upc.wms.agent.core.AgentNames;
import com.upc.wms.agent.core.AgentResult;
import com.upc.wms.agent.core.AgentTaskType;
import com.upc.wms.dto.InboundConfirmRequest;
import com.upc.wms.entity.InInboundOrder;
import com.upc.wms.entity.WhLocation;
import com.upc.wms.service.InboundService;
import com.upc.wms.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 入库智能体：为质检合格物料创建入库单、推荐可用库位并确认上架。
 * <p>
 * 库存增加与库存流水由 {@code InboundService.confirmInbound} 在同一事务内通过
 * {@code InventoryService} 完成，随后由库存智能体核对结果。
 */
@Component
@RequiredArgsConstructor
public class InboundAgent implements Agent {

    private final InboundService inboundService;
    private final WarehouseService warehouseService;

    @Override
    public String getName() {
        return AgentNames.INBOUND;
    }

    @Override
    public boolean support(String taskType) {
        return AgentTaskType.RECEIPT_INSPECTION_INBOUND.name().equals(taskType);
    }

    @Override
    public AgentResult handle(AgentContext context) {
        Map<String, Object> data = context.getData();
        Long receiptId = AgentDataUtils.getLong(data, "receiptId");
        Long warehouseId = AgentDataUtils.getLong(data, "warehouseId");
        Long operatedBy = AgentDataUtils.getLong(data, "operatedBy", context.getCreatedBy());
        List<Map<String, Object>> qualifiedLines = AgentDataUtils.getMapList(data, "qualifiedLines");

        if (qualifiedLines.isEmpty()) {
            return AgentResult.success("无合格物料需要入库，跳过入库")
                    .next(AgentNames.INVENTORY);
        }

        List<WhLocation> available = warehouseService.listAvailableLocations(warehouseId);
        if (available == null || available.isEmpty()) {
            context.put("inboundManual", true);
            return AgentResult.manualRequired("仓库无可用库位，入库任务需人工分配库位");
        }

        InInboundOrder inbound = inboundService.createInboundOrder(receiptId, warehouseId, operatedBy);

        InboundConfirmRequest request = new InboundConfirmRequest();
        request.setInboundId(inbound.getInboundId());
        request.setWarehouseId(warehouseId);
        request.setOperatedBy(operatedBy);

        List<InboundConfirmRequest.Line> lines = new ArrayList<>();
        List<Map<String, Object>> recommended = new ArrayList<>();
        int idx = 0;
        for (Map<String, Object> ql : qualifiedLines) {
            WhLocation loc = available.get(idx % available.size());
            idx++;

            InboundConfirmRequest.Line line = new InboundConfirmRequest.Line();
            line.setInspectionLineId(AgentDataUtils.getLong(ql, "inspectionLineId"));
            line.setItemId(AgentDataUtils.getLong(ql, "itemId"));
            line.setBatchId(AgentDataUtils.getLong(ql, "batchId"));
            line.setLocationId(loc.getLocationId());
            line.setInboundQty(AgentDataUtils.getBigDecimal(ql.get("qualifiedQty")));
            lines.add(line);

            Map<String, Object> rec = new java.util.HashMap<>();
            rec.put("itemId", line.getItemId());
            rec.put("batchId", line.getBatchId());
            rec.put("locationId", loc.getLocationId());
            rec.put("locationCode", loc.getLocationCode());
            recommended.add(rec);
        }
        request.setLines(lines);

        inboundService.confirmInbound(request);

        context.put("inboundId", inbound.getInboundId());
        context.put("inboundNo", inbound.getInboundNo());

        return AgentResult.success("入库上架完成，已推荐库位并确认，共 " + lines.size() + " 行")
                .business(inbound.getInboundNo())
                .next(AgentNames.INVENTORY)
                .put("inboundId", inbound.getInboundId())
                .put("inboundNo", inbound.getInboundNo())
                .put("recommendedLocations", recommended);
    }
}
