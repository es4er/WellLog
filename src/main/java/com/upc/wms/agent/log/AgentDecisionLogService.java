package com.upc.wms.agent.log;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 智能体决策日志服务：记录智能体在流程中做出的关键判断(如质检合格与否、库位推荐、库存是否充足等)，
 * 以 DECISION 类型写入执行日志，供前端可视化与论文过程追踪使用。
 */
@Service
@RequiredArgsConstructor
public class AgentDecisionLogService {

    private final AgentTaskLogService agentTaskLogService;

    public void decision(Long taskId, Long stepId, String agentName, String content) {
        agentTaskLogService.log(taskId, stepId, agentName, "DECISION", content);
    }

    public void serviceCall(Long taskId, Long stepId, String agentName, String content) {
        agentTaskLogService.log(taskId, stepId, agentName, "SERVICE_CALL", content);
    }
}
