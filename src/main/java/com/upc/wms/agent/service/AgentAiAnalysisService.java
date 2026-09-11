package com.upc.wms.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.wms.agent.core.AgentNames;
import com.upc.wms.agent.core.WmsAgentOrchestrator;
import com.upc.wms.agent.vo.AgentExecutionLogVO;
import com.upc.wms.agent.vo.AgentTaskStepVO;
import com.upc.wms.agent.vo.AgentTaskVO;
import com.upc.wms.common.BusinessException;
import com.upc.wms.config.AgentProperties;
import com.upc.wms.config.DeepSeekProperties;
import com.upc.wms.dto.AgentAnalysisVO;
import com.upc.wms.llm.DeepSeekChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务完成后的执行报告：基于真实步骤/日志生成结构化报告。
 * DeepSeek 用于原因分析、风险分析与报告组织；不编造业务单据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAiAnalysisService {

    private static final String SYSTEM_PROMPT = """
            你是测井装备 WMS 多智能体协作的报告生成助手。
            根据已完成的 Agent 步骤与日志，生成完整执行报告。
            要求：
            1. 只基于提供的事实，不要编造未出现的单据号、数量或齐套率。
            2. 不要替用户发起新的写库业务操作。
            3. 必须使用以下 Markdown 标题结构（按顺序）：
               ## 执行摘要 · 核心判断
               ## 用户任务与任务类型
               ## 参与智能体与执行步骤
               ## 数据库依据
               ## 关键决策
               ## 风险点
               ## 最终结果
               ## 建议措施
            4. 使用简体中文，简洁专业。
            """;

    private final WmsAgentOrchestrator orchestrator;
    private final DeepSeekChatService deepSeekChatService;
    private final DeepSeekProperties deepSeekProperties;
    private final AgentProperties agentProperties;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<Long, AgentAnalysisVO> cache = new ConcurrentHashMap<>();

    public AgentAnalysisVO analyzeTask(Long taskId) {
        AgentAnalysisVO cached = cache.get(taskId);
        if (cached != null && StringUtils.hasText(cached.getAnalysis())) {
            return cached;
        }

        AgentTaskVO task = orchestrator.getTaskDetail(taskId);
        if (task == null || task.getTaskId() == null) {
            throw new BusinessException("Agent 任务不存在: " + taskId);
        }

        List<AgentTaskStepVO> steps = orchestrator.getTaskSteps(taskId);
        List<AgentExecutionLogVO> logs = orchestrator.getTaskLogs(taskId);

        AgentAnalysisVO vo = buildStructuredSkeleton(task, steps, logs);

        String analysis;
        if (Boolean.TRUE.equals(deepSeekProperties.getEnabled())) {
            try {
                String userPrompt = buildUserPrompt(task, steps, logs, vo);
                analysis = deepSeekChatService.chat(SYSTEM_PROMPT, userPrompt);
            } catch (Exception e) {
                log.warn("DeepSeek 报告生成失败，使用模板报告: {}", e.getMessage());
                analysis = buildTemplateReport(vo);
            }
        } else {
            analysis = buildTemplateReport(vo);
        }

        vo.setAnalysis(analysis);
        vo.setProvider(agentProperties.getDefaultModelProvider());
        cache.put(taskId, vo);
        return vo;
    }

    public void evictCache(Long taskId) {
        cache.remove(taskId);
    }

    private AgentAnalysisVO buildStructuredSkeleton(AgentTaskVO task,
                                                    List<AgentTaskStepVO> steps,
                                                    List<AgentExecutionLogVO> logs) {
        AgentAnalysisVO vo = new AgentAnalysisVO();
        vo.setTaskId(task.getTaskId());
        vo.setTaskNo(task.getTaskNo());
        vo.setTaskStatus(task.getStatus());
        vo.setTaskType(task.getTaskType());
        vo.setUserTask(StringUtils.hasText(task.getTaskName()) ? task.getTaskName() : task.getTaskNo());

        Set<String> agents = new LinkedHashSet<>();
        List<String> stepSummaries = new ArrayList<>();
        List<String> evidence = new ArrayList<>();
        List<String> decisions = new ArrayList<>();
        List<String> risks = new ArrayList<>();

        agents.add(AgentNames.label(AgentNames.ORCHESTRATOR));
        for (AgentTaskStepVO step : steps) {
            String label = StringUtils.hasText(step.getAgentLabel()) ? step.getAgentLabel() : step.getAgentName();
            agents.add(label);
            stepSummaries.add(step.getStepNo() + ". " + nullToDash(step.getStepName())
                    + " [" + step.getStatus() + "]"
                    + (StringUtils.hasText(step.getErrorMessage()) ? " — " + step.getErrorMessage() : ""));

            parseStepOutput(step, evidence, decisions, risks);
        }

        for (AgentExecutionLogVO logItem : logs) {
            if ("DECISION".equals(logItem.getLogType()) && StringUtils.hasText(logItem.getContent())) {
                decisions.add(nullToDash(logItem.getAgentLabel()) + ": " + logItem.getContent());
            }
            if ("WARN".equals(logItem.getLogType()) || "ERROR".equals(logItem.getLogType())) {
                risks.add(nullToDash(logItem.getAgentLabel()) + ": " + logItem.getContent());
            }
            if (logItem.getContent() != null && logItem.getContent().contains("规划执行链")) {
                // 从规划日志提取
            }
        }

        vo.setParticipatingAgents(new ArrayList<>(agents));
        vo.setExecutionSteps(stepSummaries);
        vo.setDbEvidence(dedupe(evidence));
        vo.setKeyDecisions(dedupe(decisions));
        vo.setRisks(dedupe(risks));

        if ("SUCCESS".equals(task.getStatus())) {
            vo.setFinalResult("任务成功完成，业务单据已由领域智能体基于数据库真实数据落库。");
            vo.setSuggestions(List.of("可在对应业务看板核对生成的计划/领料/出库单据", "关注缺料与安全库存预警"));
        } else if ("MANUAL_REQUIRED".equals(task.getStatus())) {
            vo.setFinalResult("任务暂停，需人工确认后继续：" + nullToDash(task.getErrorMessage()));
            vo.setSuggestions(List.of("复核缺料/质检/库存异常原因", "确认后在业务页继续推进或重试 Agent 任务"));
        } else if ("FAILED".equals(task.getStatus())) {
            vo.setFinalResult("任务失败：" + nullToDash(task.getErrorMessage()));
            vo.setSuggestions(List.of("检查输入参数与业务单据状态", "查看步骤日志定位失败智能体后重试"));
        } else {
            vo.setFinalResult("任务状态: " + task.getStatus());
            vo.setSuggestions(List.of("等待任务完成后再查看完整报告"));
        }

        // 尝试从首步 input 解析 orchestrator plan
        if (!steps.isEmpty() && StringUtils.hasText(steps.get(0).getInputData())) {
            try {
                JsonNode root = objectMapper.readTree(steps.get(0).getInputData());
                JsonNode plan = root.get("_orchestratorPlan");
                if (plan != null && plan.isObject()) {
                    Map<String, Object> planMap = objectMapper.convertValue(plan, Map.class);
                    vo.setOrchestratorPlan(planMap);
                    Object goal = planMap.get("userGoal");
                    if (goal != null && StringUtils.hasText(String.valueOf(goal))) {
                        vo.setUserTask(String.valueOf(goal));
                    }
                }
            } catch (Exception ignored) {
                // ignore
            }
        }

        return vo;
    }

    private void parseStepOutput(AgentTaskStepVO step, List<String> evidence,
                                 List<String> decisions, List<String> risks) {
        if (!StringUtils.hasText(step.getOutputData())) {
            return;
        }
        try {
            JsonNode out = objectMapper.readTree(step.getOutputData());
            if (out.has("conclusion")) {
                decisions.add(nullToDash(step.getAgentLabel()) + ": " + out.get("conclusion").asText());
            }
            if (out.has("dbEvidence") && out.get("dbEvidence").isArray()) {
                for (JsonNode e : out.get("dbEvidence")) {
                    evidence.add(e.asText());
                }
            }
            if (out.has("kittingRate")) {
                evidence.add("齐套率=" + out.get("kittingRate").asText() + "%");
            }
            if (out.has("requisitionId")) {
                evidence.add("领料单ID=" + out.get("requisitionId").asText());
            }
            if (out.has("outboundNo")) {
                evidence.add("出库单=" + out.get("outboundNo").asText());
            }
            if (out.has("planNo") || out.has("planId")) {
                evidence.add("计划=" + (out.has("planNo") ? out.get("planNo").asText() : out.get("planId").asText()));
            }
            if (StringUtils.hasText(step.getErrorMessage())) {
                risks.add(step.getErrorMessage());
            }
        } catch (Exception ignored) {
            // ignore parse errors
        }
    }

    private String buildTemplateReport(AgentAnalysisVO vo) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 执行摘要 · 核心判断\n");
        sb.append(nullToDash(vo.getFinalResult())).append("\n\n");
        sb.append("## 用户任务与任务类型\n");
        sb.append("- 用户任务: ").append(nullToDash(vo.getUserTask())).append('\n');
        sb.append("- 任务类型: ").append(nullToDash(vo.getTaskType())).append('\n');
        sb.append("- 任务编号: ").append(nullToDash(vo.getTaskNo())).append("\n\n");
        sb.append("## 参与智能体与执行步骤\n");
        sb.append("参与: ").append(String.join(" → ", vo.getParticipatingAgents())).append('\n');
        for (String step : vo.getExecutionSteps()) {
            sb.append("- ").append(step).append('\n');
        }
        sb.append('\n');
        sb.append("## 数据库依据\n");
        if (vo.getDbEvidence().isEmpty()) {
            sb.append("- （步骤输出中未解析到显式依据，请查看步骤 outputData）\n");
        } else {
            for (String e : vo.getDbEvidence()) {
                sb.append("- ").append(e).append('\n');
            }
        }
        sb.append('\n');
        sb.append("## 关键决策\n");
        for (String d : vo.getKeyDecisions()) {
            sb.append("- ").append(d).append('\n');
        }
        if (vo.getKeyDecisions().isEmpty()) {
            sb.append("- （无）\n");
        }
        sb.append('\n');
        sb.append("## 风险点\n");
        if (vo.getRisks().isEmpty()) {
            sb.append("- 未发现显著风险\n");
        } else {
            for (String r : vo.getRisks()) {
                sb.append("- ").append(r).append('\n');
            }
        }
        sb.append('\n');
        sb.append("## 最终结果\n");
        sb.append(nullToDash(vo.getFinalResult())).append("\n\n");
        sb.append("## 建议措施\n");
        for (String s : vo.getSuggestions()) {
            sb.append("- ").append(s).append('\n');
        }
        return sb.toString();
    }

    private String buildUserPrompt(AgentTaskVO task, List<AgentTaskStepVO> steps,
                                   List<AgentExecutionLogVO> logs, AgentAnalysisVO skeleton) {
        StringBuilder sb = new StringBuilder();
        sb.append("请基于以下多智能体协作事实生成完整执行报告。\n\n");
        sb.append("【任务信息】\n");
        sb.append("- 用户任务: ").append(nullToDash(skeleton.getUserTask())).append('\n');
        sb.append("- 任务编号: ").append(task.getTaskNo()).append('\n');
        sb.append("- 任务类型: ").append(task.getTaskType()).append('\n');
        sb.append("- 业务单号: ").append(nullToDash(task.getBusinessNo())).append('\n');
        sb.append("- 任务状态: ").append(task.getStatus()).append('\n');
        if (StringUtils.hasText(task.getErrorMessage())) {
            sb.append("- 失败/暂停原因: ").append(task.getErrorMessage()).append('\n');
        }
        if (skeleton.getOrchestratorPlan() != null) {
            sb.append("- Orchestrator规划: ").append(skeleton.getOrchestratorPlan()).append('\n');
        }

        sb.append("\n【参与智能体】\n");
        sb.append(String.join(" → ", skeleton.getParticipatingAgents())).append('\n');

        sb.append("\n【执行步骤】\n");
        for (AgentTaskStepVO step : steps) {
            sb.append(step.getStepNo()).append(". ")
                    .append(nullToDash(step.getAgentLabel())).append(" / ")
                    .append(nullToDash(step.getStepName()))
                    .append(" -> ").append(step.getStatus());
            if (StringUtils.hasText(step.getErrorMessage())) {
                sb.append(" (").append(step.getErrorMessage()).append(')');
            }
            sb.append('\n');
            if (StringUtils.hasText(step.getOutputData())) {
                sb.append("   输出: ").append(truncate(step.getOutputData(), 800)).append('\n');
            }
            if (step.getNextAgent() != null) {
                sb.append("   下一步: ").append(step.getNextAgent()).append('\n');
            }
        }

        sb.append("\n【关键日志】\n");
        for (AgentExecutionLogVO logItem : logs) {
            sb.append("- [").append(nullToDash(logItem.getLogType())).append("] ")
                    .append(nullToDash(logItem.getAgentLabel())).append(": ")
                    .append(logItem.getContent()).append('\n');
        }

        sb.append("\n【已提取的数据库依据】\n");
        for (String e : skeleton.getDbEvidence()) {
            sb.append("- ").append(e).append('\n');
        }

        if ("MANUAL_REQUIRED".equals(task.getStatus())) {
            sb.append("\n请重点分析：为何需要人工介入？如何在不严重影响生产的前提下推进？\n");
        } else if ("SUCCESS".equals(task.getStatus())) {
            sb.append("\n请重点分析：本次协作是否顺畅？是否还有潜在缺料、质量或排程风险？\n");
        }
        return sb.toString();
    }

    private List<String> dedupe(List<String> source) {
        return new ArrayList<>(new LinkedHashSet<>(source));
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private String nullToDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }
}
