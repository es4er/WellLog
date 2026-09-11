package com.upc.wms.service;

/**
 * 系统管理员工作台审计：基于 inv_transaction / sys_audit_log / 集成消息 / 角色绑定 生成真实事实与风险证据链。
 */
public interface AdminWorkbenchAuditService {

    /**
     * 按用户问题拼装可喂给 LLM 的事实块（不含编造）。
     */
    String buildFacts(String message);

    /**
     * 无 LLM 时的完整中文答复（含证据链摘要）。
     */
    String buildFallbackAnswer(String message);
}
