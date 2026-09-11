package com.upc.wms.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.wms.agent.core.AgentTaskType;
import com.upc.wms.config.DeepSeekProperties;
import com.upc.wms.dto.PmcAssistantChatRequest;
import com.upc.wms.dto.PmcAssistantChatResponse;
import com.upc.wms.dto.QualityTaskVO;
import com.upc.wms.dto.QualityWorkbenchVO;
import com.upc.wms.dto.WarehousePendingRequisitionVO;
import com.upc.wms.dto.WarehouseWorkbenchVO;
import com.upc.wms.dto.WorkbenchAssistantChatRequest;
import com.upc.wms.dto.WorkerSummaryVO;
import com.upc.wms.dto.WorkerTaskVO;
import com.upc.wms.dto.WorkerWorkbenchVO;
import com.upc.wms.entity.InvAlertRecord;
import com.upc.wms.llm.DeepSeekChatService;
import com.upc.wms.service.AdminWorkbenchAuditService;
import com.upc.wms.service.InventoryService;
import com.upc.wms.service.PmcAssistantService;
import com.upc.wms.service.QualityService;
import com.upc.wms.service.WarehouseWorkbenchService;
import com.upc.wms.service.WorkbenchAssistantService;
import com.upc.wms.service.WorkerService;
import com.upc.wms.service.assistant.WorkbenchTopicAcl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全角色工作台助手：与 PMC 助手同构——DeepSeek 判意图 + 真实工作台数据问答；
 * 动作意图返回确认参数，由前端确认后启动 Orchestrator（DeepSeek 规划 + 领域 Agent 真链）。
 * <p>
 * 入口处按角色做话题 ACL：越权询问直接返回 intent=forbidden，不查敏感数据、不启动 Agent。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkbenchAssistantServiceImpl implements WorkbenchAssistantService {

    private static final LocalDate WORKER_DEMO_TODAY = LocalDate.of(2026, 7, 9);
    private static final DateTimeFormatter WORKER_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String INTENT_SYSTEM = """
            你是测井装备 WMS 工作台助手意图分类器。
            只输出一个 JSON 对象，不要其它文字：
            {"intent":"answer|action|clarify","action":"任务类型枚举或null","reason":"简短原因"}
            规则：
            1. 用户明确要求执行/生成/启动/扫码确认/反馈异常/处理集成消息等写库动作 → intent=action
            2. 用户询问状态、清单、分析、建议、检查、巡检、证据链、有哪些 → intent=answer（即使含「检查/生成报告」措辞）
            3. 动作意图但关键单据完全无法定位 → intent=clarify
            4. 闲聊 → intent=answer
            """;

    private final PmcAssistantService pmcAssistantService;
    private final WarehouseWorkbenchService warehouseWorkbenchService;
    private final QualityService qualityService;
    private final InventoryService inventoryService;
    private final WorkerService workerService;
    private final AdminWorkbenchAuditService adminWorkbenchAuditService;
    private final DeepSeekChatService deepSeekChatService;
    private final DeepSeekProperties deepSeekProperties;
    private final ObjectMapper objectMapper;
    private final WorkbenchTopicAcl topicAcl;

    @Override
    public PmcAssistantChatResponse chat(WorkbenchAssistantChatRequest request) {
        String role = request == null || !StringUtils.hasText(request.getRole())
                ? "pmc"
                : request.getRole().trim().toLowerCase(Locale.ROOT);
        String message = request == null || !StringUtils.hasText(request.getMessage())
                ? ""
                : request.getMessage().trim();

        if (!StringUtils.hasText(message)) {
            return clarify("请先输入问题或指令。例如询问当前待办，或明确说「生成出库单 / 扫码确认 / 安全库存检查」。");
        }

        // 话题越权拦截（规则优先，不依赖 LLM）
        String deny = topicAcl.denyReason(role, message);
        if (deny != null) {
            log.info("工作台助手越权拦截 role={}, hitMsgLen={}", role, message.length());
            return forbidden(deny);
        }

        if ("pmc".equals(role)) {
            PmcAssistantChatRequest pmcReq = new PmcAssistantChatRequest();
            pmcReq.setMessage(message);
            return pmcAssistantService.chat(pmcReq);
        }

        return switch (role) {
            case "warehouse" -> chatWarehouse(message);
            case "worker" -> chatWorker(message, request.getWorkerId());
            case "quality" -> chatQuality(message);
            case "inventory" -> chatInventory(message);
            case "admin" -> chatAdmin(message);
            default -> clarify("暂不支持角色「" + role + "」的工作台助手，请切换到 PMC / 仓管 / 质检 / 库存 / 工人 / 管理员。");
        };
    }

    // ---------------------------------------------------------- warehouse

    private PmcAssistantChatResponse chatWarehouse(String message) {
        WarehouseWorkbenchVO wb = warehouseWorkbenchService.getOverview();
        List<WarehousePendingRequisitionVO> pending =
                wb.getPendingRequisitions() == null ? List.of() : wb.getPendingRequisitions();

        IntentDecision decision = resolveIntent(message, "warehouse",
                "REQUISITION_OUTBOUND",
                List.of("生成出库", "做出库单", "出库执行", "库存校验", "批次推荐", "启动出库"),
                List.of("待办", "待生成", "异常", "拣货", "有哪些", "多少", "状态", "进度", "分析", "建议"));

        if ("clarify".equals(decision.intent)) {
            return clarify(decision.reply != null ? decision.reply
                    : "请补充领料单号，或先确认是否有待生成出库的领料单。");
        }
        if ("action".equals(decision.intent)) {
            if (pending.isEmpty()) {
                return clarify("当前没有待生成出库的领料单。请先由 PMC 完成齐套领料，或到出库单页选择单据。");
            }
            WarehousePendingRequisitionVO target = pickPending(pending, message);
            Map<String, Object> params = new HashMap<>();
            params.put("requisitionId", target.getRequisitionId());
            params.put("planId", target.getPlanId());
            params.put("planNo", target.getPlan());
            params.put("businessNo", target.getId());
            params.put("taskName", "仓管生成出库单-" + nullToDash(target.getId()));
            params.put("taskType", AgentTaskType.REQUISITION_OUTBOUND.name());

            String confirm = "将对领料单 " + nullToDash(target.getId())
                    + " 启动出库 Agent 链（接收领料→库存校验→批次库位推荐→出库单→明细→审计）。是否继续？";
            return action(AgentTaskType.REQUISITION_OUTBOUND.name(), params, confirm,
                    "已识别为执行动作：" + confirm);
        }

        String fallback = buildWarehouseAnswer(message, wb, pending);
        return answer(message, fallback, "仓管工作台事实：\n" + fallback);
    }

    private String buildWarehouseAnswer(String message, WarehouseWorkbenchVO wb,
                                        List<WarehousePendingRequisitionVO> pending) {
        String text = message.toLowerCase(Locale.ROOT);
        if (containsAny(text, "你好", "帮助", "你能做什么", "你会什么")) {
            return """
                    你好，我是仓管出库协同助手（接入 15 位智能体 Orchestrator）。
                    1. 可查询待生成出库、拣货任务与异常（直接回答）
                    2. 确认后可启动 REQUISITION_OUTBOUND 真链：Outbound→Inventory→Audit
                    请直接问，例如：「有哪些待出库领料单」或「根据领料单生成出库单」。""";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("待生成出库领料单 ").append(pending.size()).append(" 条");
        if (!pending.isEmpty()) {
            sb.append("，例如 ").append(nullToDash(pending.get(0).getId()));
        }
        sb.append("。\n");
        int outbound = wb.getOutboundOrders() == null ? 0 : wb.getOutboundOrders().size();
        int picking = wb.getPickingTasks() == null ? 0 : wb.getPickingTasks().size();
        int ex = wb.getExceptions() == null ? 0 : wb.getExceptions().size();
        sb.append("出库单 ").append(outbound).append("，拣货任务 ").append(picking)
                .append("，异常 ").append(ex).append("。\n");
        if (ex > 0 && containsAny(text, "异常", "风险")) {
            sb.append("建议优先处理异常栏中的库存不足/复核异常，再继续生成出库。");
        } else if (!pending.isEmpty()) {
            sb.append("若要执行出库，请说「生成出库单」并确认后启动 Agent。");
        }
        return sb.toString().trim();
    }

    private WarehousePendingRequisitionVO pickPending(List<WarehousePendingRequisitionVO> pending, String message) {
        String upper = message.toUpperCase(Locale.ROOT);
        for (WarehousePendingRequisitionVO row : pending) {
            if (row.getId() != null && upper.contains(row.getId().toUpperCase(Locale.ROOT))) {
                return row;
            }
            if (row.getPlan() != null && upper.contains(row.getPlan().toUpperCase(Locale.ROOT))) {
                return row;
            }
        }
        return pending.get(0);
    }

    // ---------------------------------------------------------- worker

    private PmcAssistantChatResponse chatWorker(String message, Long workerId) {
        Long wid = workerId == null ? 1L : workerId;
        WorkerWorkbenchVO wb = workerService.loadWorkbench(wid);
        List<WorkerTaskVO> tasks = wb.getTasks() == null ? List.of() : wb.getTasks();

        boolean exceptionLike = containsAny(message.toLowerCase(Locale.ROOT),
                "异常", "缺件", "缺料反馈", "补拣", "反馈");
        boolean scanLike = containsAny(message.toLowerCase(Locale.ROOT),
                "扫码", "确认交接", "领料确认", "核对");

        IntentDecision decision = resolveIntent(message, "worker",
                exceptionLike ? "WORKER_EXCEPTION_FEEDBACK" : "WORKER_PICKING_SCAN",
                List.of("扫码", "确认", "交接", "异常", "缺件", "反馈", "补拣"),
                List.of("任务", "有哪些", "今天", "进度", "查看", "清单", "状态"));

        if ("action".equals(decision.intent) || (scanLike || exceptionLike)) {
            if (tasks.isEmpty() && ("action".equals(decision.intent) || scanLike || exceptionLike)) {
                if ("answer".equals(decision.intent) && !scanLike && !exceptionLike) {
                    // fall through to answer
                } else if (scanLike || exceptionLike || "action".equals(decision.intent)) {
                    return clarify("暂无拣货任务。请先由仓管分配拣货任务后再扫码或反馈异常。");
                }
            }
        }

        if (("action".equals(decision.intent) || scanLike || exceptionLike) && !tasks.isEmpty()) {
            WorkerTaskVO task = tasks.get(0);
            Map<String, Object> params = new HashMap<>();
            params.put("pickingTaskId", task.getPickingTaskId());
            params.put("workOrder", task.getWorkOrder());
            params.put("workerId", wid);
            params.put("operatedBy", wid);

            if (exceptionLike) {
                params.put("taskType", AgentTaskType.WORKER_EXCEPTION_FEEDBACK.name());
                params.put("exceptionType", "SHORTAGE");
                params.put("exceptionNote", message);
                params.put("taskName", "工人异常反馈-" + nullToDash(task.getWorkOrder()));
                String confirm = "将登记工单 " + nullToDash(task.getWorkOrder())
                        + " 的现场异常并通知仓管协同（WorkerFeedback→Outbound→Inventory→Audit）。是否继续？";
                return action(AgentTaskType.WORKER_EXCEPTION_FEEDBACK.name(), params, confirm,
                        "已识别为执行动作：" + confirm);
            }

            params.put("taskType", AgentTaskType.WORKER_PICKING_SCAN.name());
            params.put("taskName", "工人扫码确认-" + nullToDash(task.getWorkOrder()));
            if (task.getItems() != null && !task.getItems().isEmpty()) {
                var item = task.getItems().get(0);
                params.put("pickingLineId", item.getPickingLineId());
                if (item.getBatch() != null) {
                    params.put("barcodeValue", item.getBatch());
                }
            }
            String confirm = "将对工单 " + nullToDash(task.getWorkOrder())
                    + " 启动扫码核对 Agent 链（SmartWarehouse→Inventory→Audit）。是否继续？";
            return action(AgentTaskType.WORKER_PICKING_SCAN.name(), params, confirm,
                    "已识别为执行动作：" + confirm);
        }

        String fallback = buildWorkerAnswer(message, wb, tasks);
        return answer(message, fallback, "工人工作台事实：\n" + fallback);
    }

    private String buildWorkerAnswer(String message, WorkerWorkbenchVO wb, List<WorkerTaskVO> tasks) {
        if (containsAny(message.toLowerCase(Locale.ROOT), "你好", "帮助", "你能做什么")) {
            return """
                    你好，我是生产工人领料助手（接入 Orchestrator 真链）。
                    1. 可查询今日领料/拣货任务
                    2. 确认后可启动扫码核对或异常反馈 Agent
                    请直接说：「查看我今天的领料任务」或「扫码确认」。""";
        }

        WorkerSummaryVO summary = wb.getSummary();
        int completed = summary != null ? summary.getCompleted() : countHandedOver(tasks);
        int pending = summary != null ? summary.getPending() : countPendingTasks(tasks);

        List<WorkerTaskVO> pendingTasks = tasks.stream()
                .filter(t -> !"已交接".equals(t.getStatus()))
                .filter(t -> !Boolean.TRUE.equals(t.getCancelled()))
                .toList();

        boolean askToday = containsAny(message.toLowerCase(Locale.ROOT), "今天", "今日");
        String todayStr = WORKER_DEMO_TODAY.format(WORKER_DATE_FMT);

        StringBuilder sb = new StringBuilder();
        if (askToday) {
            List<WorkerTaskVO> todayScope = tasks.stream()
                    .filter(t -> !Boolean.TRUE.equals(t.getCancelled()))
                    .filter(t -> todayStr.equals(t.getPlanDate()) || isWorkerOverdue(t, todayStr))
                    .toList();
            long todayCompleted = todayScope.stream().filter(t -> "已交接".equals(t.getStatus())).count();
            long todayPending = todayScope.size() - todayCompleted;
            pendingTasks = pendingTasks.stream()
                    .filter(t -> todayStr.equals(t.getPlanDate()) || isWorkerOverdue(t, todayStr))
                    .toList();
            sb.append("今日备料区领料任务 ").append(todayScope.size()).append(" 条：")
                    .append("已完成 ").append(todayCompleted).append(" 条，待处理 ").append(todayPending).append(" 条。\n");
        } else {
            sb.append("备料区领料任务共 ").append(tasks.size()).append(" 条：")
                    .append("已完成 ").append(completed).append(" 条，待处理 ").append(pending).append(" 条。\n");
        }

        if (!pendingTasks.isEmpty()) {
            sb.append("待处理工单：");
            pendingTasks.stream().limit(5).forEach(t ->
                    sb.append(nullToDash(t.getWorkOrder())).append("（")
                            .append(nullToDash(t.getStatus())).append("） "));
            if (pendingTasks.size() > 5) {
                sb.append("等 ").append(pendingTasks.size()).append(" 条。");
            }
            sb.append("\n");
        } else if (completed > 0) {
            sb.append("当前无待处理任务，").append(completed).append(" 条已完成。\n");
        }

        int openEx = wb.getExceptions() == null ? 0 : (int) wb.getExceptions().stream()
                .filter(ex -> !"已关闭".equals(ex.getStatus()))
                .count();
        if (openEx > 0) {
            sb.append("未关闭异常 ").append(openEx).append(" 条。");
        }
        return sb.toString().trim();
    }

    private int countHandedOver(List<WorkerTaskVO> tasks) {
        return (int) tasks.stream().filter(t -> "已交接".equals(t.getStatus())).count();
    }

    private int countPendingTasks(List<WorkerTaskVO> tasks) {
        return (int) tasks.stream()
                .filter(t -> !"已交接".equals(t.getStatus()))
                .filter(t -> !Boolean.TRUE.equals(t.getCancelled()))
                .count();
    }

    private boolean isWorkerOverdue(WorkerTaskVO task, String todayStr) {
        if (task.getPlanDate() == null || "已交接".equals(task.getStatus())) {
            return false;
        }
        return task.getPlanDate().compareTo(todayStr) < 0;
    }

    // ---------------------------------------------------------- quality

    private PmcAssistantChatResponse chatQuality(String message) {
        QualityWorkbenchVO wb = qualityService.getWorkbench();
        List<QualityTaskVO> tasks = wb.getTasks() == null ? List.of() : wb.getTasks();

        IntentDecision decision = resolveIntent(message, "quality",
                "RECEIPT_INSPECTION_INBOUND",
                List.of("执行质检", "收货质检", "入库", "启动质检", "判定"),
                List.of("待检", "异常", "清单", "有哪些", "分析", "建议", "合格率", "状态"));

        if ("action".equals(decision.intent)) {
            // 质检写库动作通常在检测执行页完成；工作台动作引导走收货质检入库全链需收货明细
            return clarify("质检判定请在「待检任务 / 检测执行」页对具体批次录入结果。"
                    + "若要启动「收货→质检→入库」全链，请提供收货明细参数或从收货上架流程发起。"
                    + "当前待检 " + wb.getPendingInspection() + " 条，开放异常 " + wb.getOpenIssues() + " 条。");
        }

        String fallback = buildQualityAnswer(message, wb, tasks);
        return answer(message, fallback, "质检工作台事实：\n" + fallback);
    }

    private String buildQualityAnswer(String message, QualityWorkbenchVO wb, List<QualityTaskVO> tasks) {
        if (containsAny(message.toLowerCase(Locale.ROOT), "你好", "帮助", "你能做什么")) {
            return """
                    你好，我是质检协同助手。
                    1. 可查询待检任务、合格率与质量异常（基于真实工作台数据）
                    2. 具体批次判定请在检测执行页完成；结论与证据可进入 Agent 报告追溯
                    当前系统已接入 Orchestrator 与 15 位智能体能力契约。""";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("待检 ").append(wb.getPendingInspection())
                .append("，检验中 ").append(wb.getInspectingCount())
                .append("，今日完成 ").append(wb.getTodayCompleted())
                .append("，合格率 ").append(wb.getPassRatePercent()).append("%")
                .append("，开放异常 ").append(wb.getOpenIssues()).append("。\n");
        if (!tasks.isEmpty() && containsAny(message.toLowerCase(Locale.ROOT), "待检", "清单", "有哪些", "批次")) {
            sb.append("待检示例：");
            tasks.stream().limit(3).forEach(t ->
                    sb.append(nullToDash(t.getBatchNo())).append("(")
                            .append(nullToDash(t.getStatus())).append(") "));
        }
        return sb.toString().trim();
    }

    // ---------------------------------------------------------- inventory

    private PmcAssistantChatResponse chatInventory(String message) {
        List<InvAlertRecord> alerts = inventoryService.checkSafetyStock();
        IntentDecision decision = resolveIntent(message, "inventory",
                "SAFETY_STOCK_CHECK",
                List.of("安全库存", "检查预警", "启动检查", "扫描低于", "冻结", "解冻", "盘点"),
                List.of("有哪些", "预警", "分析", "建议", "状态", "多少", "排查"));

        String text = message.toLowerCase(Locale.ROOT);
        if ("action".equals(decision.intent) || containsAny(text, "安全库存", "扫描低于", "启动检查")) {
            if (containsAny(text, "冻结")) {
                return clarify("库存冻结需要明确 inventoryId 与数量。请到库存控制页选择库存行后再执行，或补充参数。");
            }
            if (containsAny(text, "盘点")) {
                Map<String, Object> params = new HashMap<>();
                params.put("taskType", AgentTaskType.STOCKTAKE_ADJUSTMENT.name());
                params.put("taskName", "盘点调整任务");
                String confirm = "将启动盘点调整 Agent 链（Stocktake→Inventory→Audit）。若尚无盘点单可能进入人工确认。是否继续？";
                return action(AgentTaskType.STOCKTAKE_ADJUSTMENT.name(), params, confirm,
                        "已识别为执行动作：" + confirm);
            }
            Map<String, Object> params = new HashMap<>();
            params.put("taskType", AgentTaskType.SAFETY_STOCK_CHECK.name());
            params.put("taskName", "安全库存检查");
            params.put("pushToErp", false);
            String confirm = "将启动安全库存检查 Agent 链（Inventory→Audit），基于 inv_inventory / inv_alert_record 真实数据。是否继续？";
            return action(AgentTaskType.SAFETY_STOCK_CHECK.name(), params, confirm,
                    "已识别为执行动作：" + confirm);
        }

        String fallback = buildInventoryAnswer(message, alerts);
        return answer(message, fallback, "库存工作台事实：\n" + fallback);
    }

    private String buildInventoryAnswer(String message, List<InvAlertRecord> alerts) {
        if (containsAny(message.toLowerCase(Locale.ROOT), "你好", "帮助", "你能做什么")) {
            return """
                    你好，我是库存控制助手（接入 Orchestrator）。
                    1. 可查询安全库存预警
                    2. 确认后可启动 SAFETY_STOCK_CHECK / 盘点调整等真链
                    核心库存判断以数据库与系统规则为准，DeepSeek 只做理解与报告。""";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("安全库存预警 ").append(alerts == null ? 0 : alerts.size()).append(" 条。\n");
        if (alerts != null && !alerts.isEmpty()) {
            sb.append("示例：");
            alerts.stream().limit(3).forEach(a ->
                    sb.append("物料").append(a.getItemId()).append("(")
                            .append(nullToDash(a.getAlertType())).append(") "));
            sb.append("\n");
        }
        sb.append("若要执行检查，请说「扫描安全库存」并确认。");
        return sb.toString().trim();
    }

    // ---------------------------------------------------------- admin

    private PmcAssistantChatResponse chatAdmin(String message) {
        String text = message.toLowerCase(Locale.ROOT);
        // 审计/巡检/证据链属于问答，禁止被 LLM 误判成集成写库动作
        boolean auditQuery = containsAny(text,
                "越权", "审计", "权限", "临时权限", "巡检", "证据", "风险",
                "库存变更", "完整性", "接口失败", "智能体", "能力", "有哪些", "分析", "检查");

        IntentDecision decision = resolveIntent(message, "admin",
                "INTEGRATION_MESSAGE_PROCESS",
                List.of("处理集成", "同步消息", "启动集成", "处理报文", "重试消息"),
                List.of("越权", "审计", "权限", "接口", "状态", "智能体", "有哪些", "巡检", "分析",
                        "证据", "风险", "失败", "检查", "完整性"));
        if (auditQuery) {
            decision.intent = "answer";
        }

        if ("action".equals(decision.intent)) {
            Long messageId = extractLongId(message);
            if (messageId == null) {
                return clarify("要处理集成消息，请提供 messageId（例如「处理集成消息#3」），"
                        + "或到「接口监控 / 系统集成」页选择具体报文。"
                        + "工作台可直接查询：越权巡检、审计完整性、接口失败、智能体状态。");
            }
            Map<String, Object> params = new HashMap<>();
            params.put("messageId", messageId);
            params.put("businessNo", "INT-" + messageId);
            params.put("taskName", "处理集成消息#" + messageId);
            params.put("taskType", AgentTaskType.INTEGRATION_MESSAGE_PROCESS.name());
            String confirm = "将对集成消息 #" + messageId
                    + " 启动 INTEGRATION_MESSAGE_PROCESS（Integration→Audit）。是否继续？";
            return action(AgentTaskType.INTEGRATION_MESSAGE_PROCESS.name(), params, confirm,
                    "已识别为执行动作：" + confirm);
        }

        String fallback = adminWorkbenchAuditService.buildFallbackAnswer(message);
        String facts = adminWorkbenchAuditService.buildFacts(message);
        return answer(message, fallback, "管理员审计事实（来自数据库）：\n" + facts);
    }

    private Long extractLongId(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:#|消息|message\\s*id[=:：]?\\s*)(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(message);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    // ---------------------------------------------------------- intent / llm

    private IntentDecision resolveIntent(String message, String role, String defaultAction,
                                         List<String> actionKeys, List<String> answerKeys) {
        IntentDecision rule = resolveByRules(message, actionKeys, answerKeys);
        if (!Boolean.TRUE.equals(deepSeekProperties.getEnabled())) {
            return rule;
        }
        try {
            String userPrompt = "角色=" + role + "\n默认动作类型=" + defaultAction
                    + "\n用户问题：\n" + message
                    + "\n可用任务类型：\n"
                    + String.join("\n", java.util.Arrays.stream(AgentTaskType.values())
                    .map(Enum::name).toList());
            String raw = deepSeekChatService.chat(INTENT_SYSTEM, userPrompt);
            IntentDecision llm = parseIntent(raw);
            if (llm != null && StringUtils.hasText(llm.intent)) {
                if ("action".equals(llm.intent) && !StringUtils.hasText(llm.action)) {
                    llm.action = defaultAction;
                }
                if ("clarify".equals(llm.intent) && "action".equals(rule.intent)) {
                    return rule;
                }
                return llm;
            }
        } catch (Exception e) {
            log.warn("工作台助手意图识别失败，回退规则: {}", e.getMessage());
        }
        return rule;
    }

    private IntentDecision resolveByRules(String message, List<String> actionKeys, List<String> answerKeys) {
        String text = message.toLowerCase(Locale.ROOT);
        boolean actionLike = actionKeys.stream().anyMatch(k -> text.contains(k.toLowerCase(Locale.ROOT)));
        boolean answerLike = answerKeys.stream().anyMatch(k -> text.contains(k.toLowerCase(Locale.ROOT)));
        IntentDecision d = new IntentDecision();
        if (actionLike && !answerLike) {
            d.intent = "action";
        } else if (actionLike && containsAny(text, "建议", "分析", "是否", "能不能")) {
            d.intent = "answer";
        } else if (actionLike) {
            d.intent = "action";
        } else {
            d.intent = "answer";
        }
        return d;
    }

    private IntentDecision parseIntent(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(raw.substring(start, end + 1));
            IntentDecision d = new IntentDecision();
            d.intent = textOrNull(node, "intent");
            d.action = textOrNull(node, "action");
            if ("null".equalsIgnoreCase(d.action)) {
                d.action = null;
            }
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    private PmcAssistantChatResponse answer(String message, String fallback, String facts) {
        String reply = fallback;
        if (Boolean.TRUE.equals(deepSeekProperties.getEnabled())) {
            try {
                String system = """
                        你是测井装备 WMS 工作台协同助手。只基于给定事实回答，不要编造单据号。
                        简洁中文，2～6 句。不要声称已执行写库动作。
                        若用户问题与给定事实域明显不匹配（例如问角色授权/权限审计但只有出库或计划数据），
                        必须明确说明「当前岗位无权回答该问题」，禁止用无关业务数据凑答案。
                        """;
                String llm = deepSeekChatService.chat(system, "用户问题：\n" + message + "\n\n" + facts);
                if (StringUtils.hasText(llm)) {
                    reply = llm.trim();
                }
            } catch (Exception e) {
                log.warn("工作台问答 LLM 失败，使用模板: {}", e.getMessage());
            }
        }
        PmcAssistantChatResponse resp = new PmcAssistantChatResponse();
        resp.setIntent("answer");
        resp.setReply(reply);
        return resp;
    }

    private PmcAssistantChatResponse action(String action, Map<String, Object> params,
                                            String confirm, String reply) {
        PmcAssistantChatResponse resp = new PmcAssistantChatResponse();
        resp.setIntent("action");
        resp.setAction(action);
        resp.setParams(params);
        resp.setConfirmMessage(confirm);
        resp.setReply(reply);
        return resp;
    }

    private PmcAssistantChatResponse clarify(String reply) {
        PmcAssistantChatResponse resp = new PmcAssistantChatResponse();
        resp.setIntent("clarify");
        resp.setReply(reply);
        return resp;
    }

    private PmcAssistantChatResponse forbidden(String reply) {
        PmcAssistantChatResponse resp = new PmcAssistantChatResponse();
        resp.setIntent("forbidden");
        resp.setAction(null);
        resp.setParams(null);
        resp.setConfirmMessage(null);
        resp.setReply(reply);
        return resp;
    }

    private boolean containsAny(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(k.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
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
        return StringUtils.hasText(value) ? value : "—";
    }

    private static class IntentDecision {
        private String intent;
        private String action;
        private String reply;
    }
}
