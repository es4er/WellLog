package com.upc.wms.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent 任务执行报告（DeepSeek 汇总 + 结构化字段）。
 */
@Data
public class AgentAnalysisVO {
    private Long taskId;
    private String taskNo;
    private String taskStatus;
    private String provider;
    /** Markdown 完整报告正文 */
    private String analysis;

    /** 用户任务 */
    private String userTask;
    /** 任务类型 */
    private String taskType;
    /** 参与智能体 */
    private List<String> participatingAgents = new ArrayList<>();
    /** 执行步骤摘要 */
    private List<String> executionSteps = new ArrayList<>();
    /** 数据库依据 */
    private List<String> dbEvidence = new ArrayList<>();
    /** 关键决策 */
    private List<String> keyDecisions = new ArrayList<>();
    /** 异常/风险 */
    private List<String> risks = new ArrayList<>();
    /** 最终结果 */
    private String finalResult;
    /** 后续建议 */
    private List<String> suggestions = new ArrayList<>();
    /** Orchestrator 规划快照（若有） */
    private Map<String, Object> orchestratorPlan;
}
