package com.upc.wms.controller;

import com.upc.wms.common.BusinessException;
import com.upc.wms.common.Result;
import com.upc.wms.dto.PageResult;
import com.upc.wms.entity.SysAuditLog;
import com.upc.wms.service.AuditService;
import com.upc.wms.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;
    private final UserService userService;

    @GetMapping("/login/list")
    public Result<PageResult<SysAuditLog>> listLoginAudit(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String risk,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(auditService.listLoginAudit(page, size, result, risk));
    }

    @GetMapping("/permission/list")
    public Result<PageResult<SysAuditLog>> listPermissionAudit(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String result,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(auditService.listPermissionAudit(page, size, operationType, result));
    }

    private void requireAdmin(HttpServletRequest request) {
        Object value = request.getAttribute("userId");
        Long userId = value instanceof Long ? (Long) value : null;
        if (!userService.hasRole(userId, "ADMIN")) {
            throw new BusinessException("Admin role required");
        }
    }
}
