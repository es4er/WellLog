package com.upc.wms.dto;

import com.upc.wms.entity.SysPermission;
import com.upc.wms.entity.SysRole;
import com.upc.wms.entity.SysUser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 登录响应：返回 JWT 令牌与用户信息。
 * 前端后续请求需在请求头携带：Authorization: Bearer {token}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private SysUser user;
    private List<SysRole> roles;
    private List<SysPermission> permissions;
    private List<String> roleCodes;
    private List<String> permissionCodes;
    private String primaryRoleCode;
}
