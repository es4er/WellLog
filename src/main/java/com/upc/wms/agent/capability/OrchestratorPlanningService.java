package com.upc.wms.agent.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.wms.agent.core.AgentNames;
import com.upc.wms.agent.core.AgentTaskType;
import com.upc.wms.config.DeepSeekProperties;
import com.upc.wms.llm.DeepSeekChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 总控规划服务：接入 DeepSeek 做任务理解、模块判断与执行链规划；
 * LLM 失败或未启用时回退到规则型标准链路。
 * <p>
 * 注意：规划只决定「理解与编排」，库存/订单等核心业务判断仍由领域智能体基于数据库执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorPlanningService {

    private static final String SYSTEM_PROMPT = """
            你是测井装备 WMS 的总控智能体（Orchestrator）规划器。
            根据用户任务与可用智能体能力，输出唯一 JSON（不要其它文字）：
            {
              "taskType":"AgentTaskType枚举名",
              "userGoal":"一句话用户目标",
              "modules":["业务模块1","业务模块2"],
              "plannedChain":["Agent类名1","Agent类名2"],
              "firstAgent":"首个领域Agent类名",
              "reasoning":"规划理由（2～4句）",
              "risks":["可选风险提示"]
            }
            规则：
            1. taskType 必须是给定枚举之一；若请求已给出合法 taskType，优先沿用。
            2. plannedChain 只能使用能力目录中的 Agent 类名，且应符合该任务类型的标准业务顺序。
            3. REQUISITION_OUTBOUND 必须 OutboundAgent 首节点接收领料单，再 InventoryAgent 校验/推荐，不可颠倒。
            4. 不要编造单据号；不要让 Orchestrator 自己完成业务写库。
            5. 辅助层 Agent（UserAgent/MasterDataAgent/WarehouseAgent）默认不进入主链，除非用户明确只要查询。
            """;

    private final DeepSeekChatService deepSeekChatService;
    private final DeepSeekProperties deepSeekProperties;
    private final AgentCapabilityCatalog capabilityCatalog;
    private final ObjectMapper objectMapper;

    public OrchestratorPlan plan(String taskType, String taskName, String businessNo, Map<String, Object> data) {
        OrchestratorPlan rulePlan = buildRulePlan(taskType);
        String promptText = extractPrompt(data, taskName, businessNo);

        if (!Boolean.TRUE.equals(deepSeekProperties.getEnabled())) {
            rulePlan.setUserGoal(promptText);
            return rulePlan;
        }

        try {
            String userPrompt = buildUserPrompt(taskType, taskName, businessNo, promptText, data);
            String raw = deepSeekChatService.chat(SYSTEM_PROMPT, userPrompt);
            OrchestratorPlan llmPlan = parsePlan(raw, taskType);
            if (llmPlan != null && StringUtils.hasText(llmPlan.getFirstAgent())) {
                // 任务类型以请求为准；执行链与首节点强制使用规则标准链（LLM 仅补全目标/模块/理由）
                if (StringUtils.hasText(taskType)) {
                    llmPlan.setTaskType(taskType);
                    llmPlan.setFirstAgent(rulePlan.getFirstAgent());
                    llmPlan.setPlannedChain(rulePlan.getPlannedChain());
                    if (llmPlan.getModules() == null || llmPlan.getModules().isEmpty()) {
                        llmPlan.setModules(rulePlan.getModules());
                    }
                }
                llmPlan.setFromLlm(true);
                if (!StringUtils.hasText(llmPlan.getUserGoal())) {
                    llmPlan.setUserGoal(promptText);
                }
                return llmPlan;
            }
        } catch (Exception e) {
            log.warn("Orchestrator DeepSeek 规划失败，回退规则链: {}", e.getMessage());
        }

        rulePlan.setUserGoal(promptText);
        return rulePlan;
    }

    public OrchestratorPlan buildRulePlan(String taskType) {
        AgentTaskType type = AgentTaskType.valueOf(taskType);
        return switch (type) {
            case RECEIPT_INSPECTION_INBOUND -> OrchestratorPlan.ruleBased(
                    taskType, AgentNames.RECEIVING,
                    List.of(AgentNames.RECEIVING, AgentNames.QUALITY, AgentNames.INBOUND,
                            AgentNames.INVENTORY, AgentNames.AUDIT),
                    List.of("收货", "质检", "入库", "库存", "审计"),
                    "标准收货质检入库链：收货登记→质检判定→入库上架→库存核对→审计收尾");
            case ORDER_PLAN_REQUISITION -> OrchestratorPlan.ruleBased(
                    taskType, AgentNames.ORDER_PLAN,
                    List.of(AgentNames.ORDER_PLAN, AgentNames.INVENTORY, AgentNames.ORDER_PLAN, AgentNames.AUDIT),
                    List.of("订单计划", "库存齐套", "领料", "审计"),
                    "PMC 计划链：生成/确认生产计划→齐套校验→生成领料单→审计；出库由仓管另启任务");
            case REQUISITION_OUTBOUND -> OrchestratorPlan.ruleBased(
                    taskType, AgentNames.OUTBOUND,
                    List.of(AgentNames.OUTBOUND, AgentNames.INVENTORY, AgentNames.INVENTORY,
                            AgentNames.OUTBOUND, AgentNames.OUTBOUND, AgentNames.AUDIT),
                    List.of("出库", "库存", "拣货任务", "审计"),
                    "仓管出库链：接收领料→库存校验→批次库位推荐→出库单→出库明细→审计");
            case ORDER_REQUISITION_OUTBOUND -> OrchestratorPlan.ruleBased(
                    taskType, AgentNames.ORDER_PLAN,
                    List.of(AgentNames.ORDER_PLAN, AgentNames.INVENTORY, AgentNames.ORDER_PLAN,
                            AgentNames.OUTBOUND, AgentNames.AUDIT),
                    List.of("订单计划", "库存", "出库", "审计"),
                    "兼容全链路：计划齐套领料后继续出库（已弃用，建议拆分）");
            case STOCKTAKE_ADJUSTMENT -> OrchestratorPlan.ruleBased(
                    taskType, AgentNames.STOCKTAKE,
                    List.of(AgentNames.STOCKTAKE, AgentNames.INVENTORY, AgentNames.AUDIT),
                    List.of("盘点", "库存", "审计"),
                    "盘点调整链：盘点差异→库存调整→审计");
            case INVENTORY_TRANSFER -> OrchestratorPlan.ruleBased(
                    taskType, AgentNames.TRANSFER,
                    List.of(AgentNames.TRANSFER, AgentNames.INVENTORY, AgentNames.AUDIT),
                    List.of("移库", "库存", "审计"),
                    "移库链：移库执行→库存核对→审计");
            case INVENTORY_FREEZE, INVENTORY_UNFREEZE -> OrchestratorPlan.ruleBased(
                    taskType, AgentNames.INVENTORY,
                    List.of(AgentNames.INVENTORY, AgentNames.AUDIT),
                    List.of("库存", "审计"),
                    "库存冻结/解冻链：库存操作→审计");
            case SAFETY_STOCK_CHECK -> OrchestratorPlan.ruleBased(
                    taskType, AgentNames.INVENTORY,
                    List.of(AgentNames.INVENTORY, AgentNames.INTEGRATION, AgentNames.AUDIT),
                    List.of("安全库存", "集成推送", "审计"),
                    "安全库存链：检查预警→可选推送 ERP→审计");
            case INTEGRATION_MESSAGE_PROCESS -> OrchestratorPlan.ruleBased(
                    taskType, AgentNames.INTEGRATION,
                    List.of(AgentNames.INTEGRATION, AgentNames.AUDIT),
                    List.of("系统集成", "审计"),
                    "集成消息链：处理外部消息→审计");
            case SMART_WAREHOUSE_EVENT_PROCESS -> OrchestratorPlan.ruleBased(
                    taskType, AgentNames.SMART_WAREHOUSE,
                    List.of(AgentNames.SMART_WAREHOUSE, AgentNames.AUDIT),
                    List.of("智能仓储", "审计"),
                    "IoT 事件链：事件处理→审计");
            case WORKER_PICKING_SCAN -> OrchestratorPlan.ruleBased(
                    taskType, AgentNames.SMART_WAREHOUSE,
                    List.of(AgentNames.SMART_WAREHOUSE, AgentNames.INVENTORY, AgentNames.AUDIT),
                    List.of("扫码领料", "库存", "审计"),
                    "工人扫码链：扫码确认→库存扣减核对→审计");
            case WORKER_EXCEPTION_FEEDBACK -> OrchestratorPlan.ruleBased(
                    taskType, AgentNames.WORKER_FEEDBACK,
                    List.of(AgentNames.WORKER_FEEDBACK, AgentNames.OUTBOUND, AgentNames.INVENTORY, AgentNames.AUDIT),
                    List.of("异常反馈", "出库协同", "库存", "审计"),
                    "工人异常链：异常登记→出库协同→库存→审计");
        };
    }

    private String buildUserPrompt(String taskType, String taskName, String businessNo,
                                   String promptText, Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("【请求任务类型】").append(nullToDash(taskType)).append('\n');
        sb.append("【任务名称】").append(nullToDash(taskName)).append('\n');
        sb.append("【业务单号】").append(nullToDash(businessNo)).append('\n');
        sb.append("【用户需求】").append(nullToDash(promptText)).append('\n');
        if (data != null && !data.isEmpty()) {
            sb.append("【关键业务参数键】").append(String.join(", ", data.keySet())).append('\n');
        }
        sb.append("\n【可用 AgentTaskType】\n");
        for (AgentTaskType t : AgentTaskType.values()) {
            sb.append("- ").append(t.name()).append('\n');
        }
        sb.append("\n【智能体能力目录】\n");
        sb.append(capabilityCatalog.buildCatalogSummaryForLlm());
        sb.append("\n请输出规划 JSON。");
        return sb.toString();
    }

    private OrchestratorPlan parsePlan(String raw, String fallbackTaskType) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(text.substring(start, end + 1));
            OrchestratorPlan plan = new OrchestratorPlan();
            String type = textOrNull(node, "taskType");
            if (!StringUtils.hasText(type) || !isValidTaskType(type)) {
                type = fallbackTaskType;
            }
            plan.setTaskType(type);
            plan.setUserGoal(textOrNull(node, "userGoal"));
            plan.setReasoning(textOrNull(node, "reasoning"));
            plan.setFirstAgent(textOrNull(node, "firstAgent"));
            plan.setModules(readStringArray(node.get("modules")));
            plan.setPlannedChain(readStringArray(node.get("plannedChain")));
            plan.setRisks(readStringArray(node.get("risks")));

            // 清洗非法 Agent 名
            List<String> cleaned = new ArrayList<>();
            for (String name : plan.getPlannedChain()) {
                if (isValidAgent(name)) {
                    cleaned.add(name);
                }
            }
            plan.setPlannedChain(cleaned);
            if (!isValidAgent(plan.getFirstAgent()) && !cleaned.isEmpty()) {
                plan.setFirstAgent(cleaned.get(0));
            }
            return plan;
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> readStringArray(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return list;
        }
        for (JsonNode item : node) {
            if (item != null && !item.isNull() && StringUtils.hasText(item.asText())) {
                list.add(item.asText().trim());
            }
        }
        return list;
    }

    private boolean isValidTaskType(String type) {
        try {
            AgentTaskType.valueOf(type);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isValidAgent(String name) {
        return StringUtils.hasText(name) && capabilityCatalog.get(name) != null
                && !AgentNames.ORCHESTRATOR.equals(name);
    }

    private String extractPrompt(Map<String, Object> data, String taskName, String businessNo) {
        if (data != null) {
            Object prompt = data.get("promptText");
            if (prompt == null) {
                prompt = data.get("userGoal");
            }
            if (prompt != null && StringUtils.hasText(String.valueOf(prompt))) {
                return String.valueOf(prompt).trim();
            }
        }
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(taskName)) {
            sb.append(taskName);
        }
        if (StringUtils.hasText(businessNo)) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append(businessNo);
        }
        return sb.length() > 0 ? sb.toString() : "执行多智能体协作任务";
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText("").trim();
        return StringUtils.hasText(s) ? s : null;
    }

    private String nullToDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }
}
