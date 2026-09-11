package com.upc.wms.agent.capability;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrator 对用户任务的理解与执行链规划结果。
 */
@Data
public class OrchestratorPlan {

    /** 识别后的任务类型（AgentTaskType 名） */
    private String taskType;
    /** 用户目标摘要 */
    private String userGoal;
    /** 涉及业务模块 */
    private List<String> modules = new ArrayList<>();
    /** 规划的智能体执行链（名称列表） */
    private List<String> plannedChain = new ArrayList<>();
    /** 规划理由 */
    private String reasoning;
    /** 是否来自 DeepSeek（false 表示规则回退） */
    private boolean fromLlm;
    /** 首个领域智能体 */
    private String firstAgent;
    /** 风险提示（规划阶段） */
    private List<String> risks = new ArrayList<>();

    public static OrchestratorPlan ruleBased(String taskType, String firstAgent, List<String> chain,
                                             List<String> modules, String reasoning) {
        OrchestratorPlan plan = new OrchestratorPlan();
        plan.setTaskType(taskType);
        plan.setFirstAgent(firstAgent);
        plan.setPlannedChain(chain);
        plan.setModules(modules);
        plan.setReasoning(reasoning);
        plan.setFromLlm(false);
        plan.setUserGoal("按任务类型 " + taskType + " 执行标准业务流程");
        return plan;
    }
}
