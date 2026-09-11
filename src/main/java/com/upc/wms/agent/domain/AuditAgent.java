package com.upc.wms.agent.domain;

import com.upc.wms.agent.core.Agent;
import com.upc.wms.agent.core.AgentContext;
import com.upc.wms.agent.core.AgentDataUtils;
import com.upc.wms.agent.core.AgentNames;
import com.upc.wms.agent.core.AgentResult;
import com.upc.wms.entity.SysAuditLog;
import com.upc.wms.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计智能体：记录整个智能体任务的关键业务操作，作为流程的收尾节点(nextAgent 为空)。
 */
@Component
@RequiredArgsConstructor
public class AuditAgent implements Agent {

    private final UserService userService;

    @Override
    public String getName() {
        return AgentNames.AUDIT;
    }

    @Override
    public boolean support(String taskType) {
        // 审计智能体可为任意流程收尾
        return true;
    }

    @Override
    public AgentResult handle(AgentContext context) {
        SysAuditLog log = new SysAuditLog();
        log.setUserId(context.getCreatedBy());
        log.setOperationType(context.getTaskType());
        log.setObjectType("AGENT_TASK");
        log.setObjectId(context.getTaskId());
        log.setAfterJson(AgentDataUtils.toJson(context.getData()));
        log.setOperationResult("SUCCESS");
        log.setOperatedAt(LocalDateTime.now());
        try {
            userService.recordAuditLog(log);
        } catch (Exception ignored) {
            // 审计失败不应阻断主流程
        }

        return AgentResult.success("已记录多智能体协作审计日志，任务流程完成")
                .stepLabel("审计智能体")
                .business(context.getBusinessNo())
                .processing("将任务上下文归档至 sys_audit_log，作为流程收尾")
                .evidence("sys_audit_log", "agent_task#" + context.getTaskId(),
                        "taskType=" + context.getTaskType());
    }
}
