package com.upc.wms.agent.domain;

import com.upc.wms.agent.core.Agent;
import com.upc.wms.agent.core.AgentContext;
import com.upc.wms.agent.core.AgentDataUtils;
import com.upc.wms.agent.core.AgentNames;
import com.upc.wms.agent.core.AgentResult;
import com.upc.wms.agent.core.AgentTaskType;
import com.upc.wms.dto.WorkerExceptionVO;
import com.upc.wms.dto.WorkerScanResultVO;
import com.upc.wms.service.WorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 工人异常反馈智能体：登记现场异常，通知出库侧补拣，并交库存/审计留痕。
 */
@Component
@RequiredArgsConstructor
public class WorkerFeedbackAgent implements Agent {

    private final WorkerService workerService;

    @Override
    public String getName() {
        return AgentNames.WORKER_FEEDBACK;
    }

    @Override
    public boolean support(String taskType) {
        return AgentTaskType.WORKER_EXCEPTION_FEEDBACK.name().equals(taskType);
    }

    @Override
    public AgentResult handle(AgentContext context) {
        Map<String, Object> data = context.getData();
        WorkerExceptionVO ex = workerService.processExceptionFromAgent(data);
        context.put("exceptionId", ex.getExceptionId());
        context.put("workOrder", ex.getWorkOrder());
        return AgentResult.success("工人异常已登记，已通知仓管员补拣: " + ex.getMaterial())
                .business(ex.getWorkOrder())
                .next(AgentNames.OUTBOUND)
                .put("exceptionId", ex.getExceptionId())
                .put("pickingTaskId", ex.getPickingTaskId())
                .put("shortage", ex.getShortage());
    }
}
