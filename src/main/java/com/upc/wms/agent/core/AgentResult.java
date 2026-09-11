package com.upc.wms.agent.core;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能体执行结果：返回执行状态、下一步智能体与产出数据，供总控智能体推进流程。
 * <p>
 * 结构化字段（processingContent / conclusion / dbEvidence）会写入步骤 output，供前端链式展示。
 */
@Data
public class AgentResult {

    private Boolean success;
    /** 见 {@link AgentStatus} */
    private String status;
    private String message;
    private String nextAgent;
    private String businessNo;
    /** 可选：覆盖步骤展示名称（同一智能体多次执行时区分阶段） */
    private String stepLabel;
    /** 本步处理内容说明 */
    private String processingContent;
    /** 本步结论 */
    private String conclusion;
    /** 数据库依据（表名、单据号、关键字段等） */
    private List<String> dbEvidence = new ArrayList<>();
    private Map<String, Object> resultData = new HashMap<>();

    public static AgentResult success(String message) {
        AgentResult r = new AgentResult();
        r.setSuccess(true);
        r.setStatus(AgentStatus.SUCCESS.name());
        r.setMessage(message);
        r.setConclusion(message);
        return r;
    }

    public static AgentResult failed(String message) {
        AgentResult r = new AgentResult();
        r.setSuccess(false);
        r.setStatus(AgentStatus.FAILED.name());
        r.setMessage(message);
        r.setConclusion(message);
        return r;
    }

    public static AgentResult manualRequired(String message) {
        AgentResult r = new AgentResult();
        r.setSuccess(false);
        r.setStatus(AgentStatus.MANUAL_REQUIRED.name());
        r.setMessage(message);
        r.setConclusion(message);
        return r;
    }

    public AgentResult next(String nextAgent) {
        this.nextAgent = nextAgent;
        return this;
    }

    public AgentResult business(String businessNo) {
        this.businessNo = businessNo;
        return this;
    }

    public AgentResult stepLabel(String stepLabel) {
        this.stepLabel = stepLabel;
        return this;
    }

    public AgentResult processing(String processingContent) {
        this.processingContent = processingContent;
        return this;
    }

    public AgentResult conclude(String conclusion) {
        this.conclusion = conclusion;
        return this;
    }

    public AgentResult evidence(String... evidences) {
        if (this.dbEvidence == null) {
            this.dbEvidence = new ArrayList<>();
        }
        if (evidences != null) {
            for (String e : evidences) {
                if (e != null && !e.isBlank()) {
                    this.dbEvidence.add(e);
                }
            }
        }
        return this;
    }

    public AgentResult put(String key, Object value) {
        if (this.resultData == null) {
            this.resultData = new HashMap<>();
        }
        this.resultData.put(key, value);
        return this;
    }

    /**
     * 组装写入 agent_task_step.output_data 的结构化 Map。
     */
    public Map<String, Object> toStructuredOutput() {
        Map<String, Object> out = new LinkedHashMap<>();
        if (resultData != null && !resultData.isEmpty()) {
            out.putAll(resultData);
        }
        if (processingContent != null && !processingContent.isBlank()) {
            out.put("processingContent", processingContent);
        }
        String concl = conclusion != null && !conclusion.isBlank() ? conclusion : message;
        if (concl != null && !concl.isBlank()) {
            out.put("conclusion", concl);
        }
        if (dbEvidence != null && !dbEvidence.isEmpty()) {
            out.put("dbEvidence", new ArrayList<>(dbEvidence));
        }
        if (nextAgent != null) {
            out.put("nextFlow", nextAgent);
        } else {
            out.put("nextFlow", "END");
        }
        out.put("status", status);
        return out;
    }
}
