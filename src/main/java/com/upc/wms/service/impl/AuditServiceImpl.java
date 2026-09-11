package com.upc.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.wms.dto.PageResult;
import com.upc.wms.entity.SysAuditLog;
import com.upc.wms.entity.SysUser;
import com.upc.wms.mapper.SysAuditLogMapper;
import com.upc.wms.mapper.SysUserMapper;
import com.upc.wms.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 审计日志服务实现。
 * 负责查询和展示系统的审计日志，按业务类型分为登录审计和权限审计两大类。
 * <ul>
 *   <li>登录审计：记录每次登录行为及风险评估（objectType=LOGIN）</li>
 *   <li>权限审计：记录用户/角色/权限的变更操作（objectType=USER/ROLE/PERMISSION等）</li>
 * </ul>
 * 审计日志存储在 sys_audit_log 表，采用 JSON 列存储变更前后的快照（beforeJson/afterJson）。
 */
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private static final ObjectMapper JSON = new ObjectMapper(); // JSON解析器，用于处理 beforeJson/afterJson

    /** 审计日志 Mapper */
    private final SysAuditLogMapper sysAuditLogMapper;
    /** 用户 Mapper：填充审计日志中 userId 对应的用户名和编码 */
    private final SysUserMapper sysUserMapper;

    /**
     * 分页查询登录审计日志。
     * 登录审计的特征：objectType = "LOGIN"。
     * 支持按登录结果（SUCCESS/FAILURE）和风险等级过滤。
     *
     * @param page 页码（从1开始）
     * @param size 每页条数
     * @param result 登录结果过滤（可选）
     * @param risk 风险等级过滤（可选，如 ABNORMAL）
     * @return 分页结果，含总数、总页数、当前页、记录列表
     */
    @Override
    public PageResult<SysAuditLog> listLoginAudit(int page, int size, String result, String risk) {
        LambdaQueryWrapper<SysAuditLog> wrapper = new LambdaQueryWrapper<>();
        // object_type 存储业务分类，LOGIN 表示登录审计
        wrapper.eq(SysAuditLog::getObjectType, "LOGIN");

        if (StringUtils.hasText(result)) {
            wrapper.eq(SysAuditLog::getOperationResult, result.toUpperCase());
        }
        if (StringUtils.hasText(risk)) {
            // before_json 为 JSON 字符串（如 "ABNORMAL"），用 JSON_UNQUOTE 比较纯文本
            // MySQL JSON_UNQUOTE 函数：提取 JSON 字符串中的纯文本值
            wrapper.apply("JSON_UNQUOTE(before_json) = {0}", risk.toUpperCase());
        }

        wrapper.orderByDesc(SysAuditLog::getOperatedAt); // 最新在前

        Page<SysAuditLog> mpPage = new Page<>(page, size);
        Page<SysAuditLog> resultPage = sysAuditLogMapper.selectPage(mpPage, wrapper);

        // 后处理：将 JSON 文本还原为前端可读文本 + 填充用户信息
        normalizeJsonTextFields(resultPage.getRecords());
        fillUserInfo(resultPage.getRecords());

        return PageResult.of(
                resultPage.getTotal(),
                resultPage.getPages(),
                resultPage.getCurrent(),
                resultPage.getRecords()
        );
    }

    /**
     * 分页查询权限审计日志。
     * 权限审计涵盖：用户管理、角色管理、角色分配、权限分配。
     * objectType 范围：USER, ROLE, USER_ROLE, ROLE_PERMISSION, PERMISSION。
     */
    @Override
    public PageResult<SysAuditLog> listPermissionAudit(int page, int size, String operationType, String result) {
        LambdaQueryWrapper<SysAuditLog> wrapper = new LambdaQueryWrapper<>();
        // object_type 存储业务分类，权限审计包含用户/角色/权限相关操作
        wrapper.in(SysAuditLog::getObjectType, "USER", "ROLE", "USER_ROLE", "ROLE_PERMISSION", "PERMISSION");

        if (StringUtils.hasText(operationType)) {
            wrapper.eq(SysAuditLog::getOperationType, operationType); // 操作类型：INSERT/UPDATE/DELETE
        }
        if (StringUtils.hasText(result)) {
            wrapper.eq(SysAuditLog::getOperationResult, result.toUpperCase()); // 操作结果：SUCCESS/FAILURE
        }

        wrapper.orderByDesc(SysAuditLog::getOperatedAt);

        Page<SysAuditLog> mpPage = new Page<>(page, size);
        Page<SysAuditLog> resultPage = sysAuditLogMapper.selectPage(mpPage, wrapper);

        normalizeJsonTextFields(resultPage.getRecords());
        fillUserInfo(resultPage.getRecords());

        return PageResult.of(
                resultPage.getTotal(),
                resultPage.getPages(),
                resultPage.getCurrent(),
                resultPage.getRecords()
        );
    }

    /**
     * 批量填充审计日志中的用户信息。
     * 审计日志只存 userId，展示时需要附带 userName 和 userCode。
     * 采用批量查询 + Map 映射，避免 N+1 查询问题。
     */
    private void fillUserInfo(List<SysAuditLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }
        // 收集所有不重复的 userId
        Set<Long> userIds = logs.stream()
                .map(SysAuditLog::getUserId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return;
        }
        // 批量查用户，构建 userId → SysUser 映射
        Map<Long, SysUser> userMap = sysUserMapper.selectBatchIds(userIds).stream()
                .filter(u -> u != null)
                .collect(Collectors.toMap(SysUser::getUserId, u -> u, (a, b) -> a));
        // 回填每条日志的用户信息
        for (SysAuditLog log : logs) {
            SysUser user = userMap.get(log.getUserId());
            if (user != null) {
                log.setUserName(user.getUserName());
                log.setUserCode(user.getUserCode());
            }
        }
    }

    /**
     * 将 JSON 列中的「纯字符串」文档还原为前端可读文本。
     * 例如 DB 中 "ABNORMAL" （JSON 字符串类型） → 返回 ABNORMAL（纯文本）；
     * 对象/数组 JSON 保持原样。
     *
     * 背景：sys_audit_log 表的 before_json/after_json 列设计为 JSON 类型，
     * 但历史数据中可能存了纯字符串值（如风险等级 "ABNORMAL"），
     * 这些值被 MySQL 当成 JSON 字符串，需要用 JSON reader 解包。
     */
    private void normalizeJsonTextFields(List<SysAuditLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }
        for (SysAuditLog log : logs) {
            log.setBeforeJson(unwrapJsonText(log.getBeforeJson()));
            log.setAfterJson(unwrapJsonText(log.getAfterJson()));
        }
    }

    /**
     * 解包 JSON 文本：如果是纯字符串JSON，去掉外层引号；如果是对象/数组，保持原样。
     * 例如： 输入 "\"ABNORMAL\"" → 输出 "ABNORMAL"
     *        输入 "{\"risk\":\"HIGH\"}" → 尝试提取 risk/desc/message 字段值
     *        输入 "HIGH" → 直接返回（非JSON格式的文本）
     */
    private String unwrapJsonText(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String trimmed = raw.trim();
        // 已是普通文本（历史脏数据或非 JSON）
        if (!trimmed.startsWith("\"") && !trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return raw;
        }
        try {
            JsonNode node = JSON.readTree(trimmed);
            // 纯字符串节点：取文本值
            if (node != null && node.isTextual()) {
                return node.asText();
            }
            // 对象节点：尝试提取常用字段值（risk → desc → message 优先级）
            if (node != null && node.isObject()) {
                if (node.hasNonNull("risk")) {
                    return node.get("risk").asText();
                }
                if (node.hasNonNull("desc")) {
                    return node.get("desc").asText();
                }
                if (node.hasNonNull("message")) {
                    return node.get("message").asText();
                }
            }
        } catch (Exception ignored) {
            // 解析失败则原样返回，不阻塞业务流程
        }
        return raw;
    }
}
