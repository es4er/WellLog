package com.upc.wms.agent.core;

import com.upc.wms.agent.capability.AgentCapability;
import com.upc.wms.agent.capability.AgentCapabilityCatalog;
import com.upc.wms.agent.capability.OrchestratorPlan;
import com.upc.wms.agent.capability.OrchestratorPlanningService;
import com.upc.wms.agent.log.AgentDecisionLogService;
import com.upc.wms.agent.log.AgentTaskLogService;
import com.upc.wms.agent.vo.AgentExecutionLogVO;
import com.upc.wms.agent.vo.AgentStatusVO;
import com.upc.wms.agent.vo.AgentTaskStepVO;
import com.upc.wms.agent.vo.AgentTaskVO;
import com.upc.wms.common.BusinessException;
import com.upc.wms.config.AgentProperties;
import com.upc.wms.entity.AgentTask;
import com.upc.wms.entity.AgentTaskStep;
import com.upc.wms.service.OutGenerateExceptionService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 总控智能体（决策层）：
 * <ol>
 *   <li>接收用户任务</li>
 *   <li>DeepSeek 理解任务 / 识别类型 / 判断模块 / 规划执行链（失败则规则回退）</li>
 *   <li>按链调度领域智能体；每步基于真实 DB 执行</li>
 *   <li>记录输入/输出/结论/下一步；一步完成后再进入下一位</li>
 *   <li>直至流程完成或人工确认节点</li>
 * </ol>
 * Orchestrator 本身不直接完成库存/订单等业务写库，只负责理解、拆解、选择、编排与汇总。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WmsAgentOrchestrator {

    private static final int MAX_STEPS = 30;

    private final List<Agent> agents;
    private final AgentTaskLogService taskLogService;
    private final AgentDecisionLogService decisionLogService;
    private final OutGenerateExceptionService outGenerateExceptionService;
    private final OrchestratorPlanningService planningService;
    private final AgentCapabilityCatalog capabilityCatalog;
    private final AgentProperties agentProperties;

    private final Map<String, Agent> registry = new HashMap<>();

    @PostConstruct
    public void init() {
        for (Agent agent : agents) {
            registry.put(agent.getName(), agent);
        }
    }

    /**
     * 创建任务并异步执行，立即返回 RUNNING 状态供前端轮询。
     */
    public AgentTask startTaskAsync(String taskType, String taskName, String businessNo,
                                    Map<String, Object> data, Long createdBy) {
        validateTaskType(taskType);
        if (!Boolean.TRUE.equals(agentProperties.getEnabled())) {
            throw new BusinessException("多智能体协作模块已禁用（agent.enabled=false）");
        }

        AgentTask task = taskLogService.createTask(taskType, taskName, businessNo, createdBy);

        AgentContext context = new AgentContext();
        context.setTaskId(task.getId());
        context.setTaskNo(task.getTaskNo());
        context.setTaskType(taskType);
        context.setBusinessNo(businessNo);
        context.setCreatedBy(createdBy);
        context.setData(data == null ? new HashMap<>() : new HashMap<>(data));

        taskLogService.log(task.getId(), null, AgentNames.ORCHESTRATOR, "INFO",
                "总控智能体接收任务 " + task.getTaskNo() + "，类型 " + taskType);

        CompletableFuture.runAsync(() -> {
            try {
                OrchestratorPlan plan = planningService.plan(taskType, taskName, businessNo, context.getData());
                applyPlanToContext(context, plan);
                logPlan(task.getId(), plan);
                String firstAgent = resolveFirstAgent(plan, taskType);
                dispatchTask(context, firstAgent);
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                log.error("任务异步执行失败 taskId={}", task.getId(), e);
                failTask(task.getId(), msg);
            }
        });

        return reload(task.getId());
    }

    /**
     * 发起并执行一个智能体任务(同步)。
     */
    public AgentTask startTask(String taskType, String taskName, String businessNo,
                               Map<String, Object> data, Long createdBy) {
        validateTaskType(taskType);
        if (!Boolean.TRUE.equals(agentProperties.getEnabled())) {
            throw new BusinessException("多智能体协作模块已禁用（agent.enabled=false）");
        }

        AgentTask task = taskLogService.createTask(taskType, taskName, businessNo, createdBy);

        AgentContext context = new AgentContext();
        context.setTaskId(task.getId());
        context.setTaskNo(task.getTaskNo());
        context.setTaskType(taskType);
        context.setBusinessNo(businessNo);
        context.setCreatedBy(createdBy);
        context.setData(data == null ? new HashMap<>() : new HashMap<>(data));

        taskLogService.log(task.getId(), null, AgentNames.ORCHESTRATOR, "INFO",
                "总控智能体接收任务 " + task.getTaskNo() + "，类型 " + taskType);

        OrchestratorPlan plan = planningService.plan(taskType, taskName, businessNo, context.getData());
        applyPlanToContext(context, plan);
        logPlan(task.getId(), plan);

        String firstAgent = resolveFirstAgent(plan, taskType);
        dispatchTask(context, firstAgent);

        return reload(task.getId());
    }

    /**
     * 按 nextAgent 链推进流程。
     */
    public void dispatchTask(AgentContext context, String firstAgent) {
        Long taskId = context.getTaskId();
        String currentAgentName = firstAgent;
        int stepNo = 1;

        while (currentAgentName != null && stepNo <= MAX_STEPS) {
            Agent agent = registry.get(currentAgentName);
            if (agent == null) {
                taskLogService.finishTask(taskId, AgentStatus.FAILED.name(), "未找到智能体: " + currentAgentName);
                taskLogService.log(taskId, null, AgentNames.ORCHESTRATOR, "ERROR", "未找到智能体: " + currentAgentName);
                return;
            }

            // 职责边界：不支持该任务类型的智能体不得执行（审计除外）
            if (!agent.support(context.getTaskType()) && !AgentNames.AUDIT.equals(currentAgentName)) {
                String msg = "智能体 " + currentAgentName + " 不支持任务类型 " + context.getTaskType();
                taskLogService.finishTask(taskId, AgentStatus.FAILED.name(), msg);
                taskLogService.log(taskId, null, AgentNames.ORCHESTRATOR, "ERROR", msg);
                return;
            }

            context.setCurrentAgent(currentAgentName);
            taskLogService.updateCurrentAgent(taskId, currentAgentName);
            String stepName = AgentNames.label(currentAgentName);

            Map<String, Object> scopedInput = capabilityCatalog.filterDataScope(currentAgentName, context.getData());
            AgentTaskStep step = taskLogService.startStep(taskId, stepNo, currentAgentName, stepName, scopedInput);
            taskLogService.updateSnapshot(currentAgentName, AgentStatus.RUNNING.name(), taskId, null, null);
            taskLogService.log(taskId, step.getId(), currentAgentName, "INFO",
                    "开始执行: " + stepName + " | 数据范围键数=" + scopedInput.size());

            AgentCapability cap = capabilityCatalog.get(currentAgentName);
            if (cap != null && Boolean.TRUE.equals(agentProperties.getTraceEnabled())) {
                decisionLogService.serviceCall(taskId, step.getId(), currentAgentName,
                        "能力: " + String.join("、", cap.getCapabilities())
                                + " | 数据表: " + String.join("、", cap.getDataTables()));
            }

            AgentResult result;
            try {
                result = executeAgent(agent, context);
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                taskLogService.finishStep(step, AgentStatus.FAILED.name(), null, null, msg);
                taskLogService.log(taskId, step.getId(), currentAgentName, "ERROR", "执行异常: " + msg);
                taskLogService.updateSnapshot(currentAgentName, AgentStatus.FAILED.name(), taskId, false, step.getDurationMs());
                failTask(taskId, msg);
                recordOutboundGenerateException(context, AgentStatus.FAILED.name(),
                        AgentResult.failed(msg));
                return;
            }

            Map<String, Object> structuredOut = enrichStructuredOutput(currentAgentName, result);

            if (result.getSuccess() == null || !result.getSuccess()) {
                String status = result.getStatus() != null ? result.getStatus() : AgentStatus.FAILED.name();
                taskLogService.finishStep(step, status, structuredOut, result.getNextAgent(), result.getMessage());
                taskLogService.log(taskId, step.getId(), currentAgentName,
                        AgentStatus.MANUAL_REQUIRED.name().equals(status) ? "WARN" : "ERROR",
                        formatStepLog(result, false));
                decisionLogService.decision(taskId, step.getId(), currentAgentName,
                        "结论: " + nullToDash(result.getConclusion() != null ? result.getConclusion() : result.getMessage())
                                + " | 状态: " + status);
                taskLogService.updateSnapshot(currentAgentName, status, taskId, false, step.getDurationMs());
                if (result.getBusinessNo() != null) {
                    taskLogService.updateBusinessNo(taskId, result.getBusinessNo());
                }
                taskLogService.finishTask(taskId, status, result.getMessage());
                recordOutboundGenerateException(context, status, result);
                return;
            }

            // 成功：合并产出到上下文，供下一步使用
            mergeResultIntoContext(context, result);

            taskLogService.finishStep(step, AgentStatus.SUCCESS.name(), structuredOut,
                    result.getNextAgent(), null, result.getStepLabel());
            taskLogService.log(taskId, step.getId(), currentAgentName, "INFO", formatStepLog(result, true));
            decisionLogService.decision(taskId, step.getId(), currentAgentName,
                    "结论: " + nullToDash(result.getConclusion() != null ? result.getConclusion() : result.getMessage())
                            + " | 下一步: " + (result.getNextAgent() == null ? "无(流程结束)" : result.getNextAgent()));
            taskLogService.updateSnapshot(currentAgentName, AgentStatus.SUCCESS.name(), null, true, step.getDurationMs());

            if (result.getBusinessNo() != null) {
                context.setBusinessNo(result.getBusinessNo());
                taskLogService.updateBusinessNo(taskId, result.getBusinessNo());
            }

            context.setSourceAgent(currentAgentName);
            currentAgentName = result.getNextAgent();
            stepNo++;
        }

        if (stepNo > MAX_STEPS) {
            failTask(taskId, "流程步骤超过上限，疑似出现循环调度");
            return;
        }
        completeTask(taskId);
    }

    public AgentResult executeAgent(Agent agent, AgentContext context) {
        return agent.handle(context);
    }

    public void failTask(Long taskId, String errorMessage) {
        taskLogService.finishTask(taskId, AgentStatus.FAILED.name(), errorMessage);
        taskLogService.log(taskId, null, AgentNames.ORCHESTRATOR, "ERROR", "任务失败: " + errorMessage);
    }

    public void completeTask(Long taskId) {
        taskLogService.finishTask(taskId, AgentStatus.SUCCESS.name(), null);
        taskLogService.log(taskId, null, AgentNames.ORCHESTRATOR, "INFO", "任务执行完成，等待报告汇总");
    }

    public AgentTaskVO getTaskDetail(Long taskId) {
        return taskLogService.getTaskVO(taskId);
    }

    public List<AgentTaskStepVO> getTaskSteps(Long taskId) {
        return taskLogService.getStepVOs(taskId);
    }

    public List<AgentExecutionLogVO> getTaskLogs(Long taskId) {
        return taskLogService.getLogVOs(taskId);
    }

    public List<AgentStatusVO> getStatusList() {
        return taskLogService.getStatusVOs();
    }

    public List<AgentTask> listTasks() {
        return taskLogService.listTasks();
    }

    public List<AgentCapability> listCapabilities() {
        return capabilityCatalog.listAll();
    }

    // ---------------------------------------------------------- 内部

    private void applyPlanToContext(AgentContext context, OrchestratorPlan plan) {
        Map<String, Object> planMap = new LinkedHashMap<>();
        planMap.put("taskType", plan.getTaskType());
        planMap.put("userGoal", plan.getUserGoal());
        planMap.put("modules", plan.getModules());
        planMap.put("plannedChain", plan.getPlannedChain());
        planMap.put("firstAgent", plan.getFirstAgent());
        planMap.put("reasoning", plan.getReasoning());
        planMap.put("fromLlm", plan.isFromLlm());
        planMap.put("risks", plan.getRisks());
        context.put("_orchestratorPlan", planMap);
        if (StringUtils.hasText(plan.getUserGoal()) && context.get("promptText") == null) {
            context.put("userGoal", plan.getUserGoal());
        }
    }

    private void logPlan(Long taskId, OrchestratorPlan plan) {
        String source = plan.isFromLlm() ? "DeepSeek" : "规则回退";
        taskLogService.log(taskId, null, AgentNames.ORCHESTRATOR, "INFO",
                "任务理解(" + source + "): " + nullToDash(plan.getUserGoal()));
        taskLogService.log(taskId, null, AgentNames.ORCHESTRATOR, "DECISION",
                "规划执行链: " + String.join(" → ", plan.getPlannedChain())
                        + " | 模块: " + String.join("、", plan.getModules())
                        + " | 理由: " + nullToDash(plan.getReasoning()));
        if (plan.getRisks() != null) {
            for (String risk : plan.getRisks()) {
                if (StringUtils.hasText(risk)) {
                    taskLogService.log(taskId, null, AgentNames.ORCHESTRATOR, "WARN", "规划风险: " + risk);
                }
            }
        }
    }

    private String resolveFirstAgent(OrchestratorPlan plan, String taskType) {
        // 领料出库等业务链首节点固定，不采纳 LLM 可能颠倒的顺序
        String ruleFirst = firstAgentOf(taskType);
        if (AgentTaskType.REQUISITION_OUTBOUND.name().equals(taskType)
                || AgentTaskType.ORDER_PLAN_REQUISITION.name().equals(taskType)) {
            return ruleFirst;
        }
        if (plan != null && StringUtils.hasText(plan.getFirstAgent()) && registry.containsKey(plan.getFirstAgent())) {
            return plan.getFirstAgent();
        }
        return ruleFirst;
    }

    private void mergeResultIntoContext(AgentContext context, AgentResult result) {
        if (result.getResultData() == null || result.getResultData().isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> e : result.getResultData().entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                context.put(e.getKey(), e.getValue());
            }
        }
    }

    private Map<String, Object> enrichStructuredOutput(String agentName, AgentResult result) {
        Map<String, Object> out = result.toStructuredOutput();
        AgentCapability cap = capabilityCatalog.get(agentName);
        if (cap != null) {
            out.put("agentLabel", cap.getLabel());
            out.put("agentLayer", cap.getLayerLabel());
            Object existingEvidence = out.get("dbEvidence");
            if (!(existingEvidence instanceof List<?> list) || list.isEmpty()) {
                out.put("dbEvidence", cap.getDataTables());
            }
            if (!out.containsKey("processingContent")) {
                out.put("processingContent",
                        cap.getLabel() + "执行: " + String.join("、",
                                cap.getCapabilities().subList(0, Math.min(3, cap.getCapabilities().size()))));
            }
        }
        return out;
    }

    private String formatStepLog(AgentResult result, boolean success) {
        StringBuilder sb = new StringBuilder();
        sb.append(success ? "执行成功" : "执行未完成");
        if (StringUtils.hasText(result.getProcessingContent())) {
            sb.append(" | 处理: ").append(result.getProcessingContent());
        }
        if (StringUtils.hasText(result.getConclusion()) || StringUtils.hasText(result.getMessage())) {
            sb.append(" | 结论: ").append(nullToDash(
                    StringUtils.hasText(result.getConclusion()) ? result.getConclusion() : result.getMessage()));
        }
        if (result.getDbEvidence() != null && !result.getDbEvidence().isEmpty()) {
            sb.append(" | 依据: ").append(String.join("; ", result.getDbEvidence()));
        }
        if (result.getNextAgent() != null) {
            sb.append(" | 下一步 → ").append(AgentNames.label(result.getNextAgent()));
        }
        return sb.toString();
    }

    private void recordOutboundGenerateException(AgentContext context, String status, AgentResult result) {
        try {
            outGenerateExceptionService.recordFromAgent(
                    context.getTaskId(),
                    context.getTaskType(),
                    status,
                    result != null ? result.getMessage() : null,
                    context.getData(),
                    result != null ? result.getResultData() : null);
        } catch (Exception ignored) {
            // 异常登记失败不影响主任务状态落库
        }
    }

    private AgentTask reload(Long taskId) {
        AgentTaskVO vo = taskLogService.getTaskVO(taskId);
        AgentTask task = new AgentTask();
        task.setId(vo.getTaskId());
        task.setTaskNo(vo.getTaskNo());
        task.setTaskType(vo.getTaskType());
        task.setBusinessNo(vo.getBusinessNo());
        task.setTaskName(vo.getTaskName());
        task.setStatus(vo.getStatus());
        task.setCurrentAgent(vo.getCurrentAgent());
        task.setStartTime(vo.getStartTime());
        task.setEndTime(vo.getEndTime());
        task.setErrorMessage(vo.getErrorMessage());
        return task;
    }

    private void validateTaskType(String taskType) {
        try {
            AgentTaskType.valueOf(taskType);
        } catch (Exception e) {
            throw new BusinessException("不支持的任务类型: " + taskType);
        }
    }

    private String firstAgentOf(String taskType) {
        AgentTaskType type = AgentTaskType.valueOf(taskType);
        return switch (type) {
            case RECEIPT_INSPECTION_INBOUND -> AgentNames.RECEIVING;
            case ORDER_PLAN_REQUISITION, ORDER_REQUISITION_OUTBOUND -> AgentNames.ORDER_PLAN;
            case REQUISITION_OUTBOUND -> AgentNames.OUTBOUND;
            case STOCKTAKE_ADJUSTMENT -> AgentNames.STOCKTAKE;
            case INVENTORY_TRANSFER -> AgentNames.TRANSFER;
            case INVENTORY_FREEZE, INVENTORY_UNFREEZE, SAFETY_STOCK_CHECK -> AgentNames.INVENTORY;
            case INTEGRATION_MESSAGE_PROCESS -> AgentNames.INTEGRATION;
            case SMART_WAREHOUSE_EVENT_PROCESS, WORKER_PICKING_SCAN -> AgentNames.SMART_WAREHOUSE;
            case WORKER_EXCEPTION_FEEDBACK -> AgentNames.WORKER_FEEDBACK;
        };
    }

    private String nullToDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }
}
