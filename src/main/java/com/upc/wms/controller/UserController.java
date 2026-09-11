package com.upc.wms.controller;

import com.upc.wms.agent.core.AgentDataUtils;
import com.upc.wms.common.BusinessException;
import com.upc.wms.common.Result;
import com.upc.wms.dto.LoginRequest;
import com.upc.wms.dto.LoginResponse;
import com.upc.wms.entity.SysAuditLog;
import com.upc.wms.entity.SysPermission;
import com.upc.wms.entity.SysRole;
import com.upc.wms.entity.SysRolePermission;
import com.upc.wms.entity.SysUser;
import com.upc.wms.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request,
                                        HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        SysUser lookupUser = userService.findUserByCode(request.getUserCode());

        try {
            LoginResponse response = userService.login(request.getUserCode(), request.getPassword());
            // 登录成功 — 记录审计
            writeLoginAudit(lookupUser != null ? lookupUser.getUserId() : null,
                    request.getUserCode(),
                    lookupUser != null ? lookupUser.getUserName() : request.getUserCode(),
                    "SUCCESS", "NORMAL", ip, userAgent, "登录成功");
            return Result.success(response);
        } catch (Exception e) {
            // 登录失败 — 记录审计
            boolean abnormal = isExternalIp(ip);
            writeLoginAudit(lookupUser != null ? lookupUser.getUserId() : null,
                    request.getUserCode(),
                    lookupUser != null ? lookupUser.getUserName() : request.getUserCode(),
                    "FAIL", abnormal ? "ABNORMAL" : "NORMAL",
                    ip, userAgent, e.getMessage());
            throw e;
        }
    }

    private void writeLoginAudit(Long userId, String userCode, String userName,
                                  String result, String risk,
                                  String ip, String userAgent, String message) {
        try {
            SysAuditLog log = new SysAuditLog();
            log.setUserId(userId);
            log.setUserCode(userCode);
            log.setUserName(userName);
            log.setOperationType("LOGIN");
            log.setObjectType("LOGIN");
            log.setObjectId(userId != null ? userId : 0L);
            // before_json / after_json 为 MySQL JSON 列，必须写入合法 JSON（字符串需带引号）
            log.setBeforeJson(AgentDataUtils.toJson(risk));       // "NORMAL" / "ABNORMAL"
            log.setIpAddress(ip);
            log.setUserAgent(userAgent);
            log.setOperationResult(result); // SUCCESS / FAIL
            log.setAfterJson(AgentDataUtils.toJson(message));      // 登录结果描述
            log.setOperatedAt(LocalDateTime.now());
            userService.recordAuditLog(log);
        } catch (Exception e) {
            System.err.println("[LoginAudit] 写入登录审计失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 从 HttpServletRequest 获取真实客户端 IP。
     * 优先从代理头获取，回退到 remoteAddr。
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            // 多级代理时取第一个
            int idx = ip.indexOf(',');
            return idx > 0 ? ip.substring(0, idx).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 简单判断是否为外网 IP（非内网地址）。
     */
    private boolean isExternalIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return true;
        }
        // 127.x.x.x 回环
        if (ip.startsWith("127.") || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1")) {
            return false;
        }
        // 10.x.x.x / 172.16-31.x.x / 192.168.x.x 内网
        if (ip.startsWith("10.") || ip.startsWith("192.168.")) {
            return false;
        }
        if (ip.startsWith("172.")) {
            try {
                int second = Integer.parseInt(ip.split("\\.")[1]);
                if (second >= 16 && second <= 31) {
                    return false;
                }
            } catch (Exception ignored) {
            }
        }
        return true;
    }

    @GetMapping("/list")
    public Result<List<SysUser>> list(HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(userService.listUsers());
    }

    @GetMapping("/roles")
    public Result<List<SysRole>> roles(HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(userService.listRoles());
    }

    @GetMapping("/permissions")
    public Result<List<SysPermission>> permissions(HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(userService.listPermissions());
    }

    @GetMapping("/role-permissions")
    public Result<List<SysRolePermission>> rolePermissions(HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(userService.listRolePermissions());
    }

    @PostMapping("/add")
    public Result<SysUser> add(@RequestBody SysUser user, @RequestParam(required = false) String password,
                               HttpServletRequest request) {
        requireAdmin(request);
        SysUser created = userService.addUser(user, password);
        writePermissionAudit(request, "CREATE_USER", "USER", created.getUserId(),
                "新增用户 " + created.getUserCode(), "SUCCESS");
        return Result.success(created);
    }

    @PutMapping("/update")
    public Result<SysUser> update(@RequestBody SysUser user, HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(userService.updateUser(user));
    }

    @PostMapping("/assign-role")
    public Result<Void> assignRole(@RequestParam Long userId, @RequestParam Long roleId,
                                   HttpServletRequest request) {
        requireAdmin(request);
        userService.assignRole(userId, roleId);
        writePermissionAudit(request, "ASSIGN_ROLE", "USER_ROLE", userId,
                "为用户分配角色 roleId=" + roleId, "SUCCESS");
        return Result.success();
    }

    /** 仅匹配数字 ID，避免与 /roles、/role-permissions 等静态路径冲突 */
    @GetMapping("/{id:\\d+}")
    public Result<SysUser> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(userService.getUserById(id));
    }

    @PutMapping("/{id:\\d+}/disable")
    public Result<Void> disable(@PathVariable("id") Long id, HttpServletRequest request) {
        requireAdmin(request);
        userService.disableUser(id);
        writePermissionAudit(request, "DISABLE_USER", "USER", id, "禁用用户", "SUCCESS");
        return Result.success();
    }

    /** 批量禁用用户 */
    @PostMapping("/batch-disable")
    public Result<Void> batchDisable(@RequestBody List<Long> userIds,
                                      HttpServletRequest request) {
        requireAdmin(request);
        userService.batchDisableUsers(userIds);
        for (Long id : userIds) {
            writePermissionAudit(request, "DISABLE_USER", "USER", id, "批量禁用用户", "SUCCESS");
        }
        return Result.success();
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> delete(@PathVariable("id") Long id, HttpServletRequest request) {
        requireAdmin(request);
        userService.deleteUser(id);
        writePermissionAudit(request, "DELETE_USER", "USER", id, "用户删除", "SUCCESS");
        return Result.success();
    }

    /** 批量物理删除用户 */
    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(@RequestBody List<Long> userIds,
                                     HttpServletRequest request) {
        requireAdmin(request);
        userService.batchDeleteUsers(userIds);
        for (Long id : userIds) {
            writePermissionAudit(request, "DELETE_USER", "USER", id, "批量删除用户", "SUCCESS");
        }
        return Result.success();
    }

    private void writePermissionAudit(HttpServletRequest request,
                                       String operationType, String objectType,
                                       Long objectId, String desc, String result) {
        try {
            Object uidVal = request.getAttribute("userId");
            Long operatorId = uidVal instanceof Long ? (Long) uidVal : null;
            SysUser operator = operatorId != null ? userService.getUserById(operatorId) : null;
            SysAuditLog log = new SysAuditLog();
            log.setUserId(operatorId);
            log.setUserCode(operator != null ? operator.getUserCode() : null);
            log.setUserName(operator != null ? operator.getUserName() : null);
            log.setOperationType(operationType);
            log.setObjectType(objectType);
            log.setObjectId(objectId);
            // before_json 为 MySQL JSON 列，描述文本需序列化为合法 JSON 字符串
            log.setBeforeJson(AgentDataUtils.toJson(desc));
            log.setIpAddress(getClientIp(request));
            log.setUserAgent(request.getHeader("User-Agent"));
            log.setOperationResult(result);
            log.setOperatedAt(LocalDateTime.now());
            userService.recordAuditLog(log);
        } catch (Exception ignored) {
        }
    }

    /** 全量更新角色权限（管理员操作） */
    @PostMapping("/role-permissions/update")
    public Result<Void> updateRolePermissions(@RequestParam Long roleId,
                                               @RequestBody List<Long> permissionIds,
                                               HttpServletRequest request) {
        requireAdmin(request);
        userService.updateRolePermissions(roleId, permissionIds);
        writePermissionAudit(request, "UPDATE_ROLE_PERMISSION", "ROLE_PERMISSION", roleId,
                "更新角色权限 permCount=" + (permissionIds != null ? permissionIds.size() : 0), "SUCCESS");
        return Result.success();
    }

    private void requireAdmin(HttpServletRequest request) {
        Object value = request.getAttribute("userId");
        Long userId = value instanceof Long ? (Long) value : null;
        if (!userService.hasRole(userId, "ADMIN")) {
            throw new BusinessException("Admin role required");
        }
    }
}
