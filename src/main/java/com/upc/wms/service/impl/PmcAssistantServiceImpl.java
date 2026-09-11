package com.upc.wms.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.wms.config.DeepSeekProperties;
import com.upc.wms.dto.PmcAssistantChatRequest;
import com.upc.wms.dto.PmcAssistantChatResponse;
import com.upc.wms.dto.PmcExceptionVO;
import com.upc.wms.dto.PmcPlanCardVO;
import com.upc.wms.dto.PmcWorkbenchOverview;
import com.upc.wms.llm.DeepSeekChatService;
import com.upc.wms.service.PmcAssistantService;
import com.upc.wms.service.PmcWorkbenchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * PMC 助手：先判意图再分流。
 * <ul>
 *   <li>动作意图 → 返回 action + 确认文案，由前端确认后启动 Agent</li>
 *   <li>问答意图 → 查工作台数据，优先 DeepSeek 组织回答，失败则模板回退</li>
 *   <li>信息不足 → clarify 追问</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PmcAssistantServiceImpl implements PmcAssistantService {

    private static final Pattern PLAN_NO = Pattern.compile("(PP\\d{8,}|PLAN-\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDER_NO = Pattern.compile("(SO[-_]?\\d+|ORD[-_]?\\d+|CO[-_]?\\d+)", Pattern.CASE_INSENSITIVE);

    private static final String INTENT_SYSTEM = """
            你是测井装备 WMS 的 PMC 计划助手意图分类器。
            只输出一个 JSON 对象，不要其它文字：
            {"intent":"answer|action|clarify","action":"ORDER_PLAN_REQUISITION|null","focus":"planNo或orderNo或null","reason":"简短原因"}
            规则：
            1. 用户明确要求生成/制定/转生产计划、排产、生成领料单、执行齐套后领料 → intent=action, action=ORDER_PLAN_REQUISITION
            2. 用户询问齐套、缺料、库存、建议、分析、状态、进度、有哪些计划 → intent=answer
            3. 动作意图但完全无法定位任何订单/计划，且上下文也没有可用单据 → intent=clarify
            4. 闲聊或无关问题 → intent=answer
            """;

    private static final String ANSWER_SYSTEM = """
            你是测井装备 WMS 的 PMC 计划协同助手。
            根据给定的工作台事实回答用户问题。
            要求：
            1. 只基于事实，不要编造单据号、数量或齐套率。
            2. 回答简洁，2～6 句，可分点。
            3. 若涉及缺料，给出可执行建议（拆分领料/催补料/调拨），但不要声称已执行。
            4. 使用中文。
            """;

    private final PmcWorkbenchService pmcWorkbenchService;
    private final DeepSeekChatService deepSeekChatService;
    private final DeepSeekProperties deepSeekProperties;
    private final ObjectMapper objectMapper;

    @Override
    public PmcAssistantChatResponse chat(PmcAssistantChatRequest request) {
        String message = request == null || !StringUtils.hasText(request.getMessage())
                ? ""
                : request.getMessage().trim();
        if (!StringUtils.hasText(message)) {
            return clarify("请先输入你的问题，例如：检查某计划是否齐套，或为某订单生成生产计划。");
        }

        PmcWorkbenchOverview overview = pmcWorkbenchService.getOverview();
        List<PmcPlanCardVO> plans = overview.getPlans() == null ? List.of() : overview.getPlans();

        IntentDecision decision = resolveIntent(message, plans);
        if ("clarify".equals(decision.intent)) {
            return clarify(decision.reply);
        }
        if ("action".equals(decision.intent)) {
            return buildActionResponse(message, decision.target, plans);
        }
        return buildAnswerResponse(message, decision.target, overview, plans);
    }

    // ---------------------------------------------------------- 意图

    private IntentDecision resolveIntent(String message, List<PmcPlanCardVO> plans) {
        IntentDecision rule = resolveIntentByRules(message, plans);
        if (!Boolean.TRUE.equals(deepSeekProperties.getEnabled())) {
            return rule;
        }
        try {
            String context = buildCompactContext(plans);
            String userPrompt = "用户问题：\n" + message + "\n\n当前工作台摘要：\n" + context;
            String raw = deepSeekChatService.chat(INTENT_SYSTEM, userPrompt);
            IntentDecision llm = parseIntentJson(raw);
            if (llm != null && StringUtils.hasText(llm.intent)) {
                // LLM 判为动作时，用规则补全目标单据；判为问答时尊重 LLM
                if ("action".equals(llm.intent)) {
                    PmcPlanCardVO target = resolveTarget(message, llm.focus, plans);
                    if (target == null) {
                        return clarifyDecision("需要先明确订单号或生产计划号，才能启动生成计划/领料流程。可先问我当前有哪些待处理单据。");
                    }
                    return actionDecision(target);
                }
                if ("clarify".equals(llm.intent) && rule.intent.equals("action") && rule.target != null) {
                    // 规则已能定位单据时，不因 LLM 保守而阻断动作
                    return rule;
                }
                if ("answer".equals(llm.intent) || "clarify".equals(llm.intent)) {
                    PmcPlanCardVO target = resolveTarget(message, llm.focus, plans);
                    IntentDecision d = new IntentDecision();
                    d.intent = llm.intent;
                    d.target = target;
                    d.reply = "clarify".equals(llm.intent)
                            ? "还需要更多信息：请补充生产计划号（如 PP…）或订单号，我再帮你处理。"
                            : null;
                    return d;
                }
            }
        } catch (Exception e) {
            log.warn("PMC 意图识别 LLM 失败，回退规则: {}", e.getMessage());
        }
        return rule;
    }

    private IntentDecision resolveIntentByRules(String message, List<PmcPlanCardVO> plans) {
        String text = message.toLowerCase(Locale.ROOT);

        boolean actionLike = containsAny(text,
                "生成生产计划", "制定生产计划", "转生产计划", "创建生产计划", "排产",
                "生成领料", "做领料单", "下达领料", "执行领料", "启动计划", "开始排产",
                "订单转", "转计划");
        boolean queryLike = containsAny(text,
                "齐套", "缺料", "缺哪些", "缺多少", "库存", "分析", "建议", "检查",
                "是否满足", "可先领", "采购", "调拨", "进度", "状态", "有哪些", "多少",
                "为什么", "怎么", "如何", "查询", "看看", "汇总", "统计");
        boolean greeting = containsAny(text, "你好", "您好", "在吗", "谢谢", "帮助", "你能做什么", "你会什么");

        PmcPlanCardVO mentioned = resolveTarget(message, null, plans);

        if (actionLike && !queryLike) {
            PmcPlanCardVO target = mentioned != null ? mentioned : pickDefaultActionTarget(plans);
            if (target == null) {
                return clarifyDecision("当前没有可执行的订单/计划。请先在订单计划中心同步数据，或告诉我具体订单号。");
            }
            return actionDecision(target);
        }
        if (actionLike && queryLike) {
            // 「检查…并生成领料建议」偏问答；「生成…并检查」偏动作——以生成动词优先但若含「建议/分析」则问答
            if (containsAny(text, "建议", "分析", "缺哪些", "缺多少", "是否满足")) {
                IntentDecision d = new IntentDecision();
                d.intent = "answer";
                d.target = mentioned != null ? mentioned : pickDefaultQueryTarget(plans);
                return d;
            }
            PmcPlanCardVO target = mentioned != null ? mentioned : pickDefaultActionTarget(plans);
            if (target == null) {
                return clarifyDecision("请补充要执行的订单号或生产计划号。");
            }
            return actionDecision(target);
        }
        if (greeting && message.length() < 20) {
            IntentDecision d = new IntentDecision();
            d.intent = "answer";
            return d;
        }

        IntentDecision d = new IntentDecision();
        d.intent = "answer";
        d.target = mentioned != null ? mentioned : pickDefaultQueryTarget(plans);
        return d;
    }

    // ---------------------------------------------------------- 动作

    private PmcAssistantChatResponse buildActionResponse(String message, PmcPlanCardVO target,
                                                         List<PmcPlanCardVO> plans) {
        PmcPlanCardVO plan = target != null ? target : pickDefaultActionTarget(plans);
        if (plan == null) {
            return clarify("暂无可用订单/计划，请先在订单计划中心同步数据后再试。");
        }

        Map<String, Object> params = new HashMap<>();
        boolean pendingOrder = !Boolean.TRUE.equals(plan.getHasPlan());
        if (pendingOrder) {
            params.put("orderId", plan.getOrderId());
            params.put("planNo", plan.getOrder());
            params.put("taskName", "订单转生产计划-" + nullToDash(plan.getOrder()));
            params.put("autoApproveOrder", "待审核".equals(plan.getStatus()));
        } else {
            params.put("planId", plan.getPlanId());
            params.put("planNo", plan.getId());
            params.put("orderId", plan.getOrderId());
            params.put("taskName", "PMC计划排产-" + nullToDash(plan.getId()));
        }

        String subject = pendingOrder
                ? "订单 " + nullToDash(plan.getOrder())
                : "生产计划 " + nullToDash(plan.getId());
        String confirm = pendingOrder
                ? "将为" + subject + "制定生产计划，完成齐套校验后按结果生成领料单。是否继续？"
                : "将对" + subject + "执行齐套校验，并按结果生成领料单。是否继续？";

        PmcAssistantChatResponse resp = new PmcAssistantChatResponse();
        resp.setIntent("action");
        resp.setAction("ORDER_PLAN_REQUISITION");
        resp.setParams(params);
        resp.setConfirmMessage(confirm);
        resp.setReply("已识别为执行动作：" + confirm + "\n确认后将启动 Agent 流水线；若只是想查询齐套/缺料，请改用「检查/分析」类问题。");
        return resp;
    }

    // ---------------------------------------------------------- 问答

    private PmcAssistantChatResponse buildAnswerResponse(String message, PmcPlanCardVO target,
                                                         PmcWorkbenchOverview overview,
                                                         List<PmcPlanCardVO> plans) {
        String fallback = buildTemplateAnswer(message, target, overview, plans);
        String reply = fallback;
        if (Boolean.TRUE.equals(deepSeekProperties.getEnabled())) {
            try {
                String facts = buildFactsForAnswer(target, overview, plans);
                String userPrompt = "用户问题：\n" + message + "\n\n工作台事实：\n" + facts;
                String llmReply = deepSeekChatService.chat(ANSWER_SYSTEM, userPrompt);
                if (StringUtils.hasText(llmReply)) {
                    reply = llmReply.trim();
                }
            } catch (Exception e) {
                log.warn("PMC 问答 LLM 失败，使用模板: {}", e.getMessage());
            }
        }

        PmcAssistantChatResponse resp = new PmcAssistantChatResponse();
        resp.setIntent("answer");
        resp.setAction(null);
        resp.setParams(null);
        resp.setReply(reply);
        return resp;
    }

    private String buildTemplateAnswer(String message, PmcPlanCardVO target,
                                       PmcWorkbenchOverview overview, List<PmcPlanCardVO> plans) {
        String text = message.toLowerCase(Locale.ROOT);
        if (containsAny(text, "你好", "您好", "在吗", "帮助", "你能做什么", "你会什么")) {
            return """
                    你好，我是 PMC 计划协同助手。我可以：
                    1. 查询计划齐套率、缺料明细与领料建议（直接回答，不改数据）
                    2. 在你确认后，启动「订单→生产计划→齐套→领料」Agent 流水线
                    请直接问，例如：「检查 PP… 是否齐套」或「为订单 SO… 生成生产计划」。""";
        }

        if (plans.isEmpty()) {
            return "当前工作台暂无订单/计划数据。请先到「订单计划」同步 ERP/MES 订单后再问我。";
        }

        PmcPlanCardVO focus = target != null ? target : pickDefaultQueryTarget(plans);
        StringBuilder sb = new StringBuilder();

        if (focus != null) {
            boolean hasPlan = Boolean.TRUE.equals(focus.getHasPlan());
            sb.append(hasPlan ? "生产计划 " : "订单 ")
                    .append(hasPlan ? nullToDash(focus.getId()) : nullToDash(focus.getOrder()))
                    .append("：状态 ").append(nullToDash(focus.getStatus()))
                    .append("，产品 ").append(nullToDash(focus.getProduct()))
                    .append("，数量 ").append(nullToDash(focus.getQty())).append("。\n");
            if (hasPlan) {
                int ready = focus.getReady() == null ? 0 : focus.getReady();
                int shortage = focus.getShortage() == null ? 0 : focus.getShortage();
                sb.append("齐套率约 ").append(ready).append("%");
                if (shortage > 0) {
                    sb.append("，缺料项 ").append(shortage).append(" 类");
                }
                sb.append("。领料单：").append(nullToDash(focus.getReq())).append("。\n");
                if (ready < 90) {
                    sb.append("建议：优先拆分领料发放齐套物料，并对短缺项催补料/调拨；齐套率低于 90% 时系统不会生成正式领料单。\n");
                } else if (ready < 100) {
                    sb.append("建议：可生成部分领料单先开工，同时跟进短缺物料到货。\n");
                } else {
                    sb.append("物料已齐套，可确认后启动领料流程。\n");
                }
            } else {
                sb.append("该订单尚未生成生产计划。若要排产，请明确说「为订单 ")
                        .append(nullToDash(focus.getOrder()))
                        .append(" 生成生产计划」。\n");
            }
        }

        if (overview.getShortageTop() != null && !overview.getShortageTop().isEmpty()
                && containsAny(text, "缺料", "短缺", "采购", "调拨", "汇总", "统计")) {
            sb.append("\n缺料 TOP：");
            overview.getShortageTop().stream().limit(3).forEach(row -> {
                Object name = row.get("name") != null ? row.get("name") : row.get("itemName");
                Object qty = row.get("shortage") != null ? row.get("shortage") : row.get("qty");
                sb.append(nullToDash(String.valueOf(name))).append("(").append(nullToDash(String.valueOf(qty))).append(") ");
            });
            sb.append("\n");
        }

        if (StringUtils.hasText(overview.getShortageAlert()) && containsAny(text, "缺料", "风险", "预警")) {
            sb.append("预警：").append(overview.getShortageAlert()).append("\n");
        }

        List<PmcExceptionVO> exs = overview.getOutboundExceptions();
        if (exs != null && !exs.isEmpty() && containsAny(text, "异常", "出库", "协同")) {
            sb.append("出库协同异常 ").append(exs.size()).append(" 条，例如：")
                    .append(nullToDash(exs.get(0).getTitle())).append("。\n");
        }

        long pending = plans.stream().filter(p -> !Boolean.TRUE.equals(p.getHasPlan())).count();
        long waiting = plans.stream().filter(p -> "待领料".equals(p.getStatus())).count();
        long shortagePlans = plans.stream().filter(p -> "缺料".equals(p.getStatus())).count();
        if (containsAny(text, "有哪些", "多少", "汇总", "统计", "进度") || focus == null) {
            sb.append("工作台概况：共 ").append(plans.size()).append(" 条，待排产订单 ")
                    .append(pending).append("，待领料 ").append(waiting)
                    .append("，缺料 ").append(shortagePlans).append("。");
        }

        String result = sb.toString().trim();
        return StringUtils.hasText(result) ? result
                : "已收到你的问题。你可以问齐套/缺料情况，或确认后让我启动生成生产计划流程。";
    }

    // ---------------------------------------------------------- 目标解析

    private PmcPlanCardVO resolveTarget(String message, String focus, List<PmcPlanCardVO> plans) {
        if (plans == null || plans.isEmpty()) {
            return null;
        }
        List<String> keys = new ArrayList<>();
        if (StringUtils.hasText(focus)) {
            keys.add(focus.trim());
        }
        Matcher pm = PLAN_NO.matcher(message);
        while (pm.find()) {
            keys.add(pm.group(1));
        }
        Matcher om = ORDER_NO.matcher(message);
        while (om.find()) {
            keys.add(om.group(1));
        }
        for (String key : keys) {
            PmcPlanCardVO hit = findByKey(plans, key);
            if (hit != null) {
                return hit;
            }
        }
        // 模糊：消息中包含计划号/订单号片段
        String upper = message.toUpperCase(Locale.ROOT);
        for (PmcPlanCardVO plan : plans) {
            if (StringUtils.hasText(plan.getId()) && upper.contains(plan.getId().toUpperCase(Locale.ROOT))) {
                return plan;
            }
            if (StringUtils.hasText(plan.getOrder()) && upper.contains(plan.getOrder().toUpperCase(Locale.ROOT))) {
                return plan;
            }
        }
        return null;
    }

    private PmcPlanCardVO findByKey(List<PmcPlanCardVO> plans, String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        String k = key.trim();
        for (PmcPlanCardVO plan : plans) {
            if (k.equalsIgnoreCase(plan.getId()) || k.equalsIgnoreCase(plan.getOrder())) {
                return plan;
            }
            if (plan.getPlanId() != null && k.equalsIgnoreCase("PLAN-" + plan.getPlanId())) {
                return plan;
            }
        }
        return null;
    }

    private PmcPlanCardVO pickDefaultActionTarget(List<PmcPlanCardVO> plans) {
        return plans.stream().filter(p -> !Boolean.TRUE.equals(p.getHasPlan())).findFirst()
                .orElseGet(() -> plans.stream().filter(p -> "待领料".equals(p.getStatus())).findFirst()
                        .orElseGet(() -> plans.stream().filter(p -> Boolean.TRUE.equals(p.getHasPlan())).findFirst()
                                .orElse(null)));
    }

    private PmcPlanCardVO pickDefaultQueryTarget(List<PmcPlanCardVO> plans) {
        return plans.stream().filter(p -> "缺料".equals(p.getStatus())).findFirst()
                .orElseGet(() -> plans.stream().filter(p -> "待领料".equals(p.getStatus())).findFirst()
                        .orElseGet(() -> plans.stream().filter(p -> Boolean.TRUE.equals(p.getHasPlan())).findFirst()
                                .orElseGet(() -> plans.isEmpty() ? null : plans.get(0))));
    }

    // ---------------------------------------------------------- 上下文 / 解析

    private String buildCompactContext(List<PmcPlanCardVO> plans) {
        return plans.stream().limit(12).map(p -> {
            boolean hasPlan = Boolean.TRUE.equals(p.getHasPlan());
            return (hasPlan ? "计划" : "订单") + "=" + (hasPlan ? p.getId() : p.getOrder())
                    + ",status=" + p.getStatus()
                    + ",ready=" + p.getReady()
                    + ",shortage=" + p.getShortage()
                    + ",req=" + p.getReq();
        }).collect(Collectors.joining("\n"));
    }

    private String buildFactsForAnswer(PmcPlanCardVO target, PmcWorkbenchOverview overview,
                                       List<PmcPlanCardVO> plans) {
        StringBuilder sb = new StringBuilder();
        sb.append("待审核/待排产：").append(overview.getPendingReview()).append("\n");
        if (StringUtils.hasText(overview.getInboxHint())) {
            sb.append("提示：").append(overview.getInboxHint()).append("\n");
        }
        if (StringUtils.hasText(overview.getShortageAlert())) {
            sb.append("缺料预警：").append(overview.getShortageAlert()).append("\n");
        }
        if (target != null) {
            sb.append("焦点单据：").append(objectToJsonSafe(target)).append("\n");
        }
        sb.append("计划列表（最多 10 条）：\n").append(buildCompactContext(plans.stream().limit(10).toList()));
        if (overview.getShortageTop() != null && !overview.getShortageTop().isEmpty()) {
            sb.append("\n缺料TOP：").append(objectToJsonSafe(overview.getShortageTop().stream().limit(5).toList()));
        }
        if (overview.getRiskSummary() != null && !overview.getRiskSummary().isEmpty()) {
            sb.append("\n风险摘要：").append(String.join("；", overview.getRiskSummary()));
        }
        return sb.toString();
    }

    private IntentDecision parseIntentJson(String raw) {
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
            IntentDecision d = new IntentDecision();
            d.intent = textOrNull(node, "intent");
            d.focus = textOrNull(node, "focus");
            if ("null".equalsIgnoreCase(d.focus)) {
                d.focus = null;
            }
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText("").trim();
        return StringUtils.hasText(s) ? s : null;
    }

    private String objectToJsonSafe(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private PmcAssistantChatResponse clarify(String reply) {
        PmcAssistantChatResponse resp = new PmcAssistantChatResponse();
        resp.setIntent("clarify");
        resp.setReply(reply);
        return resp;
    }

    private IntentDecision clarifyDecision(String reply) {
        IntentDecision d = new IntentDecision();
        d.intent = "clarify";
        d.reply = reply;
        return d;
    }

    private IntentDecision actionDecision(PmcPlanCardVO target) {
        IntentDecision d = new IntentDecision();
        d.intent = "action";
        d.target = target;
        return d;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String k : keywords) {
            if (text.contains(k.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String nullToDash(String value) {
        return StringUtils.hasText(value) ? value : "—";
    }

    private static class IntentDecision {
        private String intent;
        private String focus;
        private String reply;
        private PmcPlanCardVO target;
    }
}
