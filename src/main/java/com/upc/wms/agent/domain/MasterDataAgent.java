package com.upc.wms.agent.domain;

import com.upc.wms.agent.core.Agent;
import com.upc.wms.agent.core.AgentContext;
import com.upc.wms.agent.core.AgentDataUtils;
import com.upc.wms.agent.core.AgentNames;
import com.upc.wms.agent.core.AgentResult;
import com.upc.wms.entity.MdItem;
import com.upc.wms.service.MasterDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 基础资料智能体：负责物料、供应商等基础资料校验。作为辅助智能体，可为其他智能体提供物料/供应商合法性校验。
 */
@Component
@RequiredArgsConstructor
public class MasterDataAgent implements Agent {

    private final MasterDataService masterDataService;

    @Override
    public String getName() {
        return AgentNames.MASTER_DATA;
    }

    @Override
    public boolean support(String taskType) {
        return false;
    }

    @Override
    public AgentResult handle(AgentContext context) {
        Long itemId = AgentDataUtils.getLong(context.getData(), "itemId");
        if (itemId != null) {
            MdItem item = masterDataService.getItemById(itemId);
            if (item == null) {
                return AgentResult.failed("物料不存在: " + itemId);
            }
            return AgentResult.success("物料校验通过")
                    .put("itemCode", item.getItemCode())
                    .put("itemName", item.getItemName());
        }
        return AgentResult.success("基础资料智能体已就绪");
    }
}
