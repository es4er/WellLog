package com.upc.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.upc.wms.agent.capability.AgentCapabilityCatalog;
import com.upc.wms.agent.core.WmsAgentOrchestrator;
import com.upc.wms.agent.vo.AgentStatusVO;
import com.upc.wms.entity.IntIntegrationMessage;
import com.upc.wms.entity.InvTransaction;
import com.upc.wms.entity.SysAuditLog;
import com.upc.wms.entity.SysRole;
import com.upc.wms.entity.SysUser;
import com.upc.wms.entity.SysUserRole;
import com.upc.wms.mapper.IntIntegrationMessageMapper;
import com.upc.wms.mapper.InvTransactionMapper;
import com.upc.wms.mapper.SysAuditLogMapper;
import com.upc.wms.mapper.SysRoleMapper;
import com.upc.wms.mapper.SysUserMapper;
import com.upc.wms.mapper.SysUserRoleMapper;
import com.upc.wms.service.AdminWorkbenchAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminWorkbenchAuditServiceImpl implements AdminWorkbenchAuditService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> INVENTORY_MUTATION_ROLES = Set.of("ADMIN", "WAREHOUSE", "INVENTORY");
    private static final Set<String> FRONTLINE_INVENTORY_ROLES = Set.of("WAREHOUSE", "INVENTORY");

    private final InvTransactionMapper invTransactionMapper;
    private final SysAuditLogMapper sysAuditLogMapper;
    private final IntIntegrationMessageMapper integrationMessageMapper;
    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final WmsAgentOrchestrator orchestrator;
    private final AgentCapabilityCatalog capabilityCatalog;

    @Override
    public String buildFacts(String message) {
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
        LocalDate day = LocalDate.now();
        StringBuilder sb = new StringBuilder();
        sb.append("查询日=").append(day).append("\n");

        if (isHelp(text)) {
            sb.append(helpText());
            return sb.toString();
        }

        boolean wantPrivilege = containsAny(text, "越权", "权限", "临时权限", "巡检", "证据", "风险");
        boolean wantIntegrity = containsAny(text, "审计", "完整性", "库存变更", "流水", "证据链");
        boolean wantIntegration = containsAny(text, "接口", "集成", "erp", "mes", "失败", "重试");
        boolean wantAgent = containsAny(text, "智能体", "agent", "状态", "能力", "有哪些");

        // 默认提示同时覆盖越权 + 库存变更审计
        if (!wantPrivilege && !wantIntegrity && !wantIntegration && !wantAgent) {
            wantPrivilege = true;
            wantIntegrity = true;
        }

        if (wantPrivilege || wantIntegrity) {
            sb.append(buildInventoryRiskFacts(day));
            sb.append(buildMultiRoleFacts());
        }
        if (wantIntegrity) {
            sb.append(buildAuditIntegrityFacts(day));
        }
        if (wantIntegration) {
            sb.append(buildIntegrationFacts());
        }
        if (wantAgent) {
            sb.append(buildAgentFacts());
        }
        return sb.toString().trim();
    }

    @Override
    public String buildFallbackAnswer(String message) {
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (isHelp(text)) {
            return helpText();
        }

        LocalDate day = LocalDate.now();
        boolean wantPrivilege = containsAny(text, "越权", "权限", "临时权限", "巡检", "证据", "风险");
        boolean wantIntegrity = containsAny(text, "审计", "完整性", "库存变更", "流水", "证据链");
        boolean wantIntegration = containsAny(text, "接口", "集成", "erp", "mes", "失败", "重试");
        boolean wantAgent = containsAny(text, "智能体", "agent", "状态", "能力", "有哪些");
        if (!wantPrivilege && !wantIntegrity && !wantIntegration && !wantAgent) {
            wantPrivilege = true;
            wantIntegrity = true;
        }

        StringBuilder sb = new StringBuilder();
        if (wantPrivilege || wantIntegrity) {
            RiskReport report = analyzeInventoryRisks(day);
            sb.append("【").append(day).append(" 库存变更越权/职责边界巡检】\n");
            sb.append("库存流水 ").append(report.transactions.size()).append(" 条，风险点 ")
                    .append(report.risks.size()).append(" 条。\n");
            if (report.risks.isEmpty()) {
                sb.append("未发现无权限角色直接写库存的越权记录。\n");
            } else {
                sb.append("风险证据链：\n");
                int i = 1;
                for (String risk : report.risks) {
                    sb.append(i++).append(". ").append(risk).append("\n");
                    if (i > 8) {
                        sb.append("…其余 ").append(report.risks.size() - 8).append(" 条略\n");
                        break;
                    }
                }
            }
            String multi = buildMultiRoleFacts();
            if (StringUtils.hasText(multi)) {
                sb.append(multi);
            }
            if (wantIntegrity) {
                sb.append(buildAuditIntegrityAnswer(day, report));
            }
        }
        if (wantIntegration) {
            sb.append(buildIntegrationAnswer());
        }
        if (wantAgent) {
            sb.append(buildAgentAnswer());
        }
        return sb.toString().trim();
    }

    private String helpText() {
        return """
                你好，我是系统管理助手，回答均基于库表真实数据：
                1. 库存变更越权/职责边界：对照 inv_transaction 操作人角色
                2. 审计完整性：对照当日流水与 sys_audit_log
                3. ERP/MES 接口：对照 int_integration_message 失败与重试
                4. 智能体状态：对照 Orchestrator 注册状态与能力目录
                可直接问：「检查今天库存变更是否越权并生成证据链」或「近 24 小时接口失败」。""";
    }

    private String buildInventoryRiskFacts(LocalDate day) {
        RiskReport report = analyzeInventoryRisks(day);
        StringBuilder sb = new StringBuilder();
        sb.append("【库存变更巡检】流水数=").append(report.transactions.size())
                .append("，风险数=").append(report.risks.size()).append("\n");
        for (String risk : report.risks) {
            sb.append("- ").append(risk).append("\n");
        }
        if (report.risks.isEmpty() && !report.transactions.isEmpty()) {
            sb.append("- 当日流水操作人均具备库存变更相关角色，无「无权限写库」类越权\n");
        }
        if (report.transactions.isEmpty()) {
            sb.append("- 当日无 inv_transaction 记录\n");
        }
        return sb.toString();
    }

    private String buildMultiRoleFacts() {
        Map<Long, List<String>> rolesByUser = loadUserRoleCodes();
        Map<Long, SysUser> users = loadUsers(rolesByUser.keySet());
        List<String> multi = new ArrayList<>();
        for (Map.Entry<Long, List<String>> e : rolesByUser.entrySet()) {
            if (e.getValue().size() > 1) {
                SysUser u = users.get(e.getKey());
                String code = u == null ? String.valueOf(e.getKey()) : u.getUserCode();
                multi.add(code + "=" + String.join("+", e.getValue()));
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【多角色/提权残留扫描】系统无独立临时权限表；以 sys_user_role 多角色绑定近似。\n");
        if (multi.isEmpty()) {
            sb.append("- 当前无多角色绑定用户\n");
        } else {
            for (String row : multi) {
                sb.append("- ").append(row).append("（建议回收多余角色）\n");
            }
        }
        return sb.toString();
    }

    private String buildAuditIntegrityFacts(LocalDate day) {
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.atTime(LocalTime.MAX);
        List<InvTransaction> txns = listTransactions(start, end);
        List<SysAuditLog> audits = listAudits(start, end);
        int linked = 0;
        List<String> missing = new ArrayList<>();
        for (InvTransaction txn : txns) {
            if (hasNearbyAudit(txn, audits)) {
                linked++;
            } else {
                missing.add(txn.getTransactionNo() + "/" + nullToDash(txn.getBusinessType())
                        + "/item=" + txn.getItemId());
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【审计完整性】当日流水=").append(txns.size())
                .append("，有邻近审计=").append(linked)
                .append("，审计日志条数=").append(audits.size()).append("\n");
        for (String m : missing.stream().limit(8).toList()) {
            sb.append("- 缺邻近审计: ").append(m).append("\n");
        }
        return sb.toString();
    }

    private String buildAuditIntegrityAnswer(LocalDate day, RiskReport report) {
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.atTime(LocalTime.MAX);
        List<SysAuditLog> audits = listAudits(start, end);
        int linked = 0;
        List<String> missing = new ArrayList<>();
        for (InvTransaction txn : report.transactions) {
            if (hasNearbyAudit(txn, audits)) {
                linked++;
            } else {
                missing.add(txn.getTransactionNo());
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【审计完整性】当日 Agent/业务审计日志 ").append(audits.size()).append(" 条；")
                .append("流水可关联邻近审计 ").append(linked).append("/")
                .append(report.transactions.size()).append("。\n");
        if (!missing.isEmpty()) {
            sb.append("缺审计链流水：").append(String.join("、", missing.stream().limit(5).toList()));
            if (missing.size() > 5) {
                sb.append(" 等").append(missing.size()).append("条");
            }
            sb.append("。\n");
        } else if (!report.transactions.isEmpty()) {
            sb.append("当日库存流水均可在 ±30 分钟内找到同操作人审计记录。\n");
        }
        return sb.toString();
    }

    private String buildIntegrationFacts() {
        List<IntIntegrationMessage> all = integrationMessageMapper.selectList(
                new LambdaQueryWrapper<IntIntegrationMessage>()
                        .orderByDesc(IntIntegrationMessage::getCreatedAt)
                        .last("LIMIT 50"));
        long failed = all.stream().filter(m -> statusIs(m, "FAILED", "FAIL", "ERROR")).count();
        long pending = all.stream().filter(m -> statusIs(m, "PENDING")).count();
        long success = all.stream().filter(m -> statusIs(m, "SUCCESS")).count();
        long retried = all.stream().filter(m -> m.getRetryCount() != null && m.getRetryCount() > 0).count();
        StringBuilder sb = new StringBuilder();
        sb.append("【集成消息】样本最近 ").append(all.size()).append(" 条：SUCCESS=")
                .append(success).append(" FAILED=").append(failed)
                .append(" PENDING=").append(pending).append(" 曾重试=").append(retried).append("\n");
        all.stream().filter(m -> statusIs(m, "FAILED", "FAIL", "ERROR")
                        || (m.getRetryCount() != null && m.getRetryCount() > 0))
                .limit(5)
                .forEach(m -> sb.append("- #").append(m.getMessageId()).append(" ")
                        .append(nullToDash(m.getMessageType())).append("/")
                        .append(nullToDash(m.getBusinessKey())).append(" status=")
                        .append(nullToDash(m.getProcessStatus())).append(" retry=")
                        .append(m.getRetryCount() == null ? 0 : m.getRetryCount())
                        .append(" err=").append(nullToDash(m.getErrorMessage())).append("\n"));
        return sb.toString();
    }

    private String buildIntegrationAnswer() {
        String facts = buildIntegrationFacts();
        return facts + "处理失败报文请到系统集成页指定 messageId，或明确说「处理集成消息#ID」。\n";
    }

    private String buildAgentFacts() {
        List<AgentStatusVO> statuses = orchestrator.getStatusList();
        StringBuilder sb = new StringBuilder();
        sb.append("【智能体】注册状态 ").append(statuses.size())
                .append(" 条，能力目录 ").append(capabilityCatalog.listAll().size()).append(" 项\n");
        for (AgentStatusVO s : statuses.stream().limit(12).toList()) {
            sb.append("- ").append(nullToDash(s.getAgentLabel())).append("/")
                    .append(nullToDash(s.getAgentName())).append(" status=")
                    .append(nullToDash(s.getStatus()))
                    .append(" ok=").append(nvl(s.getSuccessCount()))
                    .append(" fail=").append(nvl(s.getFailedCount())).append("\n");
        }
        return sb.toString();
    }

    private String buildAgentAnswer() {
        return buildAgentFacts();
    }

    private RiskReport analyzeInventoryRisks(LocalDate day) {
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.atTime(LocalTime.MAX);
        List<InvTransaction> txns = listTransactions(start, end);
        Map<Long, List<String>> rolesByUser = loadUserRoleCodes();
        Map<Long, SysUser> users = loadUsers(
                txns.stream().map(InvTransaction::getOperatedBy).filter(Objects::nonNull).collect(Collectors.toSet()));

        List<String> risks = new ArrayList<>();
        for (InvTransaction txn : txns) {
            Long uid = txn.getOperatedBy();
            SysUser user = uid == null ? null : users.get(uid);
            List<String> roles = uid == null ? List.of() : rolesByUser.getOrDefault(uid, List.of());
            String who = user == null ? ("userId=" + uid) : (user.getUserCode() + "/" + user.getUserName());
            String base = txn.getTransactionNo() + " | " + nullToDash(txn.getBusinessType())
                    + " | item=" + txn.getItemId()
                    + " | qty=" + txn.getChangeQty()
                    + " | at=" + (txn.getOperatedAt() == null ? "—" : TS.format(txn.getOperatedAt()))
                    + " | by=" + who + "(" + (roles.isEmpty() ? "无角色" : String.join(",", roles)) + ")";

            if (uid == null) {
                risks.add(base + " → 风险=操作人缺失，无法鉴权");
                continue;
            }
            boolean hasMutationRole = roles.stream().anyMatch(INVENTORY_MUTATION_ROLES::contains);
            if (!hasMutationRole) {
                risks.add(base + " → 风险=越权写库存（操作人角色不含 ADMIN/WAREHOUSE/INVENTORY）");
                continue;
            }
            boolean frontline = roles.stream().anyMatch(FRONTLINE_INVENTORY_ROLES::contains);
            boolean onlyAdmin = roles.size() == 1 && roles.contains("ADMIN");
            if (onlyAdmin || (!frontline && roles.contains("ADMIN"))) {
                risks.add(base + " → 风险=管理员账号直接变更库存（职责越界，建议仓管/库存岗位执行）");
            }
        }
        RiskReport report = new RiskReport();
        report.transactions = txns;
        report.risks = risks;
        return report;
    }

    private boolean hasNearbyAudit(InvTransaction txn, List<SysAuditLog> audits) {
        if (txn.getOperatedAt() == null) {
            return false;
        }
        LocalDateTime from = txn.getOperatedAt().minusMinutes(30);
        LocalDateTime to = txn.getOperatedAt().plusMinutes(30);
        String txnNo = txn.getTransactionNo();
        Long uid = txn.getOperatedBy();
        for (SysAuditLog a : audits) {
            if (a.getOperatedAt() == null) {
                continue;
            }
            if (a.getOperatedAt().isBefore(from) || a.getOperatedAt().isAfter(to)) {
                continue;
            }
            if (uid != null && a.getUserId() != null && uid.equals(a.getUserId())) {
                return true;
            }
            String after = a.getAfterJson();
            if (StringUtils.hasText(txnNo) && StringUtils.hasText(after) && after.contains(txnNo)) {
                return true;
            }
            if (txn.getSourceDocId() != null && Objects.equals(a.getObjectId(), txn.getSourceDocId())) {
                return true;
            }
        }
        return false;
    }

    private List<InvTransaction> listTransactions(LocalDateTime start, LocalDateTime end) {
        return invTransactionMapper.selectList(new LambdaQueryWrapper<InvTransaction>()
                .ge(InvTransaction::getOperatedAt, start)
                .le(InvTransaction::getOperatedAt, end)
                .orderByDesc(InvTransaction::getOperatedAt));
    }

    private List<SysAuditLog> listAudits(LocalDateTime start, LocalDateTime end) {
        return sysAuditLogMapper.selectList(new LambdaQueryWrapper<SysAuditLog>()
                .ge(SysAuditLog::getOperatedAt, start)
                .le(SysAuditLog::getOperatedAt, end)
                .orderByDesc(SysAuditLog::getOperatedAt));
    }

    private Map<Long, List<String>> loadUserRoleCodes() {
        List<SysUserRole> links = sysUserRoleMapper.selectList(null);
        if (links.isEmpty()) {
            return Map.of();
        }
        Set<Long> roleIds = links.stream().map(SysUserRole::getRoleId).collect(Collectors.toSet());
        Map<Long, String> roleCodeById = sysRoleMapper.selectBatchIds(roleIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(SysRole::getRoleId, SysRole::getRoleCode, (a, b) -> a));
        Map<Long, List<String>> result = new LinkedHashMap<>();
        for (SysUserRole link : links) {
            String code = roleCodeById.get(link.getRoleId());
            if (!StringUtils.hasText(code)) {
                continue;
            }
            result.computeIfAbsent(link.getUserId(), k -> new ArrayList<>()).add(code);
        }
        return result;
    }

    private Map<Long, SysUser> loadUsers(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, SysUser> map = new HashMap<>();
        for (SysUser u : sysUserMapper.selectBatchIds(userIds)) {
            if (u != null) {
                map.put(u.getUserId(), u);
            }
        }
        return map;
    }

    private boolean statusIs(IntIntegrationMessage m, String... statuses) {
        if (m == null || !StringUtils.hasText(m.getProcessStatus())) {
            return false;
        }
        String s = m.getProcessStatus().trim().toUpperCase(Locale.ROOT);
        for (String expect : statuses) {
            if (s.equals(expect)) {
                return true;
            }
        }
        return false;
    }

    private boolean isHelp(String text) {
        return containsAny(text, "你好", "您好", "帮助", "你能做什么", "你会什么");
    }

    private boolean containsAny(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(k.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String nullToDash(String v) {
        return StringUtils.hasText(v) ? v : "—";
    }

    private int nvl(Integer v) {
        return v == null ? 0 : v;
    }

    private static class RiskReport {
        private List<InvTransaction> transactions = List.of();
        private List<String> risks = List.of();
    }
}
