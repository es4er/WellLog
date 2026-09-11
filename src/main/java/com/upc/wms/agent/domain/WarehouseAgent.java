package com.upc.wms.agent.domain;

import com.upc.wms.agent.core.Agent;
import com.upc.wms.agent.core.AgentContext;
import com.upc.wms.agent.core.AgentDataUtils;
import com.upc.wms.agent.core.AgentNames;
import com.upc.wms.agent.core.AgentResult;
import com.upc.wms.entity.WhLocation;
import com.upc.wms.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 仓库库位智能体：负责仓库与库位相关能力，可为入库/移库智能体提供可用库位推荐。作为辅助智能体。
 */
@Component
@RequiredArgsConstructor
public class WarehouseAgent implements Agent {

    private final WarehouseService warehouseService;

    @Override
    public String getName() {
        return AgentNames.WAREHOUSE;
    }

    @Override
    public boolean support(String taskType) {
        return false;
    }

    @Override
    public AgentResult handle(AgentContext context) {
        Long warehouseId = AgentDataUtils.getLong(context.getData(), "warehouseId");
        if (warehouseId != null) {
            List<WhLocation> available = warehouseService.listAvailableLocations(warehouseId);
            return AgentResult.success("可用库位查询完成")
                    .put("availableLocationCount", available == null ? 0 : available.size());
        }
        return AgentResult.success("仓库库位智能体已就绪");
    }
}
