package com.upc.wms.service.assistant;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 工作台助手话题 ACL：按角色拦截越权询问/指令。
 * <p>
 * 规则优先：命中禁止关键词即拒绝；不依赖 LLM，避免模型误放行。
 */
@Component
public class WorkbenchTopicAcl {

    /** 仅系统管理员可查询/操作的话题（所有非 admin 角色统一拦截） */
    private static final List<String> ADMIN_ONLY_TOPICS = List.of(
            "角色授权", "角色权限", "权限审计", "权限管理", "用户权限",
            "越权操作巡检", "越权操作", "越权巡检",
            "临时权限回收", "临时权限",
            "系统管理员权限", "系统管理员",
            "登录审计", "角色管理", "授权情况", "授权明细"
    );

    private final Map<String, RoleTopicPolicy> policies = new LinkedHashMap<>();

    public WorkbenchTopicAcl() {
        // 生产工人：仅本人领料执行域
        policies.put("worker", new RoleTopicPolicy(
                "生产工人",
                List.of("今日领料任务", "扫码确认", "缺件/异常反馈", "工单交接进度"),
                List.of(
                        "订单计划", "排产", "生产计划", "齐套", "缺料分析", "领料单生成", "客户订单", "待审核订单",
                        "安全库存", "库存冻结", "库存解冻", "全库库存", "盘点调整", "库位地图", "移库",
                        "质检判定", "待检任务", "合格率", "质量异常", "加严抽检", "不良统计", "供应商分析",
                        "生成出库单", "出库单", "拣货分配", "复核异常",
                        "越权", "系统集成", "ERP", "MES 接口", "审计日志",
                        "成本", "售价", "价格", "其他工人", "全部工人", "管理员"
                )
        ));

        // 仓管：出库执行域，不可做 PMC 排产 / 权限审计 / 质检判定写结论
        policies.put("warehouse", new RoleTopicPolicy(
                "仓管员",
                List.of("待生成出库", "生成出库单", "拣货任务", "出库异常", "库存校验(出库相关)"),
                List.of(
                        "生成生产计划", "排产", "订单审核", "齐套校验", "客户订单待审核",
                        "质检判定", "录入检测结果", "合格率统计", "检验项配置",
                        "扫码确认(工人)", "其他工人任务", "成本", "售价"
                )
        ));

        // PMC：计划与齐套，不可代做出库执行 / 权限管理 / 质检录入
        policies.put("pmc", new RoleTopicPolicy(
                "PMC计划员",
                List.of("齐套/缺料查询", "生成生产计划", "领料单", "出库协同进度"),
                List.of(
                        "生成出库单", "分配拣货", "拣货扫码", "工人扫码",
                        "录入检测结果", "质检判定提交", "检验项配置",
                        "成本核算", "售价"
                )
        ));

        // 质检：质量域
        policies.put("quality", new RoleTopicPolicy(
                "质检员",
                List.of("待检任务", "检测建议", "质量异常", "合格率"),
                List.of(
                        "生成生产计划", "排产", "生成领料单",
                        "生成出库单", "分配拣货", "工人扫码",
                        "成本", "售价", "修改安全库存规则"
                )
        ));

        // 库存：库存控制域
        policies.put("inventory", new RoleTopicPolicy(
                "库存管理员",
                List.of("安全库存预警", "冻结/解冻", "盘点差异", "批次追溯"),
                List.of(
                        "生成生产计划", "排产", "订单审核",
                        "生成出库单", "分配拣货", "工人扫码",
                        "质检判定提交", "录入检测结果",
                        "成本", "售价"
                )
        ));

        // 管理员：偏审计与系统，不直接代做一线业务写库指令（可查询状态）
        policies.put("admin", new RoleTopicPolicy(
                "系统管理员",
                List.of("权限审计", "智能体状态", "接口失败", "审计完整性", "角色授权"),
                List.of(
                        "生成生产计划", "生成出库单", "工人扫码确认", "录入质检结果",
                        "代他人领料", "修改业务单据数量"
                )
        ));
    }

    /**
     * @return 越权说明文案；null 表示放行
     */
    public String denyReason(String role, String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String r = role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
        RoleTopicPolicy policy = policies.get(r);
        if (policy == null) {
            return null;
        }
        String text = message.toLowerCase(Locale.ROOT);

        // 管理员专属话题：非 admin 统一拒绝（优先于问候放行，避免 LLM 用错域数据硬答）
        if (!"admin".equals(r)) {
            for (String topic : ADMIN_ONLY_TOPICS) {
                if (text.contains(topic.toLowerCase(Locale.ROOT))) {
                    return buildDenyMessage(policy, topic);
                }
            }
        }

        // 问候/帮助放行（纯闲聊）
        if (containsAny(text, "你好", "您好", "在吗", "帮助", "你能做什么", "你会什么", "谢谢")) {
            return null;
        }
        for (String banned : policy.bannedTopics()) {
            if (StringUtils.hasText(banned) && text.contains(banned.toLowerCase(Locale.ROOT))) {
                return buildDenyMessage(policy, banned);
            }
        }
        return null;
    }

    public String roleLabel(String role) {
        RoleTopicPolicy policy = policies.get(role == null ? "" : role.trim().toLowerCase(Locale.ROOT));
        return policy == null ? role : policy.roleLabel();
    }

    private String buildDenyMessage(RoleTopicPolicy policy, String hit) {
        return "无权限：当前角色为「" + policy.roleLabel() + "」，无权查询或操作「"
                + hit + "」相关信息。\n"
                + "你可询问/办理：" + String.join("、", policy.allowedHints()) + "。\n"
                + "如需该信息，请切换到系统管理员账号后再试。";
    }

    private boolean containsAny(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(k.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private record RoleTopicPolicy(String roleLabel, List<String> allowedHints, List<String> bannedTopics) {
    }
}
