package com.upc.wms.service;

import com.upc.wms.dto.LoginResponse;
import com.upc.wms.entity.SysAuditLog;
import com.upc.wms.entity.SysPermission;
import com.upc.wms.entity.SysRole;
import com.upc.wms.entity.SysRolePermission;
import com.upc.wms.entity.SysUser;

import java.util.List;

/**
 * 用户、角色、权限与登录服务接口。
 */
public interface UserService {

    LoginResponse login(String userCode, String password);

    List<SysUser> listUsers();

    /** 按角色编码查询启用中的用户（如 WORKER） */
    List<SysUser> listUsersByRoleCode(String roleCode);

    SysUser getUserById(Long userId);

    SysUser addUser(SysUser user, String rawPassword);

    SysUser updateUser(SysUser user);

    void disableUser(Long userId);

    void assignRole(Long userId, Long roleId);

    List<SysRole> listRoles();

    List<SysPermission> listPermissions();

    List<SysRolePermission> listRolePermissions();

    boolean hasRole(Long userId, String roleCode);

    void recordAuditLog(SysAuditLog log);

    /** 全量替换角色的权限列表 */
    void updateRolePermissions(Long roleId, List<Long> permissionIds);

    /** 批量禁用用户 */
    void batchDisableUsers(List<Long> userIds);

    /** 按 userCode 查用户（不校验密码，供审计使用） */
    SysUser findUserByCode(String userCode);

    /** 物理删除用户及关联角色 */
    void deleteUser(Long userId);

    /** 批量物理删除用户 */
    void batchDeleteUsers(List<Long> userIds);
}
