package com.upc.wms.agent.domain;

import com.upc.wms.agent.core.Agent;
import com.upc.wms.agent.core.AgentContext;
import com.upc.wms.agent.core.AgentDataUtils;
import com.upc.wms.agent.core.AgentNames;
import com.upc.wms.agent.core.AgentResult;
import com.upc.wms.agent.core.AgentTaskType;
import com.upc.wms.dto.StocktakeCountRequest;
import com.upc.wms.entity.InvAdjustmentOrder;
import com.upc.wms.entity.InvStocktakeDifference;
import com.upc.wms.entity.InvStocktakeOrder;
import com.upc.wms.service.StocktakeService;
import com.upc.wms.vo.StocktakeDetailVO;
import com.upc.wms.vo.StocktakeLineVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 盘点智能体：创建盘点单并生成盘点明细；若已提供实盘数量，则依次生成差异、确认、创建并审核调整单，
 * 由调整单在 InventoryService 内修正库存，最后交库存智能体核对；否则任务转为待人工录入实盘。
 */
@Component
@RequiredArgsConstructor
public class StocktakeAgent implements Agent {

    private final StocktakeService stocktakeService;

    @Override
    public String getName() {
        return AgentNames.STOCKTAKE;
    }

    @Override
    public boolean support(String taskType) {
        return AgentTaskType.STOCKTAKE_ADJUSTMENT.name().equals(taskType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public AgentResult handle(AgentContext context) {
        Map<String, Object> data = context.getData();
        Long warehouseId = AgentDataUtils.getLong(data, "warehouseId");
        String stocktakeType = AgentDataUtils.getString(data, "stocktakeType");
        String scope = AgentDataUtils.getString(data, "stocktakeScope");
        Long createdBy = AgentDataUtils.getLong(data, "createdBy", context.getCreatedBy());
        Long approvedBy = AgentDataUtils.getLong(data, "approvedBy", createdBy);
        String reason = AgentDataUtils.getString(data, "adjustmentReason");

        InvStocktakeOrder stocktake = stocktakeService.createStocktake(warehouseId, stocktakeType, scope, createdBy);
        context.put("stocktakeId", stocktake.getStocktakeId());

        StocktakeDetailVO detail = stocktakeService.getStocktakeDetail(stocktake.getStocktakeId());
        List<StocktakeLineVO> stLines = detail.getLines();

        // 实盘数量：支持按 stocktakeLineId 或 inventoryId 提供
        List<Map<String, Object>> counts = AgentDataUtils.getMapList(data, "counts");
        if (counts.isEmpty()) {
            return AgentResult.manualRequired("盘点单已生成，等待录入实盘数量后继续")
                    .business(stocktake.getStocktakeNo())
                    .put("stocktakeId", stocktake.getStocktakeId())
                    .put("lineCount", stLines.size());
        }

        Map<Long, Long> invToLine = new HashMap<>();
        for (StocktakeLineVO l : stLines) {
            invToLine.put(l.getInventoryId(), l.getStocktakeLineId());
        }

        StocktakeCountRequest countRequest = new StocktakeCountRequest();
        countRequest.setStocktakeId(stocktake.getStocktakeId());
        List<StocktakeCountRequest.Line> countLines = new ArrayList<>();
        for (Map<String, Object> c : counts) {
            Long lineId = AgentDataUtils.getLong(c, "stocktakeLineId");
            if (lineId == null) {
                lineId = invToLine.get(AgentDataUtils.getLong(c, "inventoryId"));
            }
            if (lineId == null) {
                continue;
            }
            StocktakeCountRequest.Line cl = new StocktakeCountRequest.Line();
            cl.setStocktakeLineId(lineId);
            cl.setCountedQty(AgentDataUtils.getBigDecimal(c.get("countedQty")));
            countLines.add(cl);
        }
        countRequest.setLines(countLines);
        stocktakeService.submitCountResult(countRequest);

        List<InvStocktakeDifference> differences = stocktakeService.generateDifference(stocktake.getStocktakeId());
        stocktakeService.confirmAllDifferences(stocktake.getStocktakeId());

        InvAdjustmentOrder adjustment = stocktakeService.createAdjustment(stocktake.getStocktakeId(), reason);
        stocktakeService.approveAdjustment(adjustment.getAdjustmentId(), approvedBy);

        // 供库存智能体核对的受影响物料/批次
        List<Map<String, Object>> affected = new ArrayList<>();
        for (StocktakeLineVO l : stLines) {
            BigDecimal diff = l.getDifferenceQty();
            if (diff != null && diff.compareTo(BigDecimal.ZERO) != 0) {
                Map<String, Object> m = new HashMap<>();
                m.put("itemId", l.getItemId());
                m.put("batchId", l.getBatchId());
                affected.add(m);
            }
        }
        context.put("affectedLines", affected);

        return AgentResult.success("盘点差异已生成并完成库存调整，差异 " + differences.size() + " 条")
                .business(stocktake.getStocktakeNo())
                .next(AgentNames.INVENTORY)
                .put("stocktakeId", stocktake.getStocktakeId())
                .put("adjustmentId", adjustment.getAdjustmentId())
                .put("differenceCount", differences.size());
    }
}
