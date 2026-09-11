package com.upc.wms.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.upc.wms.common.BusinessException;
import com.upc.wms.dto.LoginResponse;
import com.upc.wms.entity.SysAuditLog;
import com.upc.wms.entity.SysPermission;
import com.upc.wms.entity.SysRole;
import com.upc.wms.entity.SysRolePermission;
import com.upc.wms.entity.SysUser;
import com.upc.wms.entity.SysUserRole;
import com.upc.wms.mapper.SysAuditLogMapper;
import com.upc.wms.mapper.SysPermissionMapper;
import com.upc.wms.mapper.SysRolePermissionMapper;
import com.upc.wms.mapper.SysRoleMapper;
import com.upc.wms.mapper.SysUserMapper;
import com.upc.wms.mapper.SysUserRoleMapper;
import com.upc.wms.service.UserService;
import com.upc.wms.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户认证与权限管理服务实现。
 * WMS 系统的核心安全模块，负责：
 * <ul>
 *   <li>用户登录认证：SHA-256 密码验证 + JWT Token 签发</li>
 *   <li>用户管理：增删改查、批量禁用/删除</li>
 *   <li>角色分配：一个用户一个角色（assignRole 会先删后加，保证唯一）</li>
 *   <li>权限授权：角色→权限的 N:N 关联管理</li>
 *   <li>审计日志：记录关键操作到 sys_audit_log</li>
 * </ul>
 * 权限模型：用户 →（用户角色关联表）→ 角色 →（角色权限关联表）→ 权限。
 * 两级间接关联：用户通过角色获得权限。
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    /** 用户 Mapper */
    private final SysUserMapper sysUserMapper;
    /** 角色 Mapper */
    private final SysRoleMapper sysRoleMapper;
    /** 权限 Mapper */
    private final SysPermissionMapper sysPermissionMapper;
    /** 用户-角色关联 Mapper */
    private final SysUserRoleMapper sysUserRoleMapper;
    /** 角色-权限关联 Mapper */
    private final SysRolePermissionMapper sysRolePermissionMapper;
    /** 审计日志 Mapper */
    private final SysAuditLogMapper sysAuditLogMapper;
    /** JWT 工具类：签发和验证 Token */
    private final JwtUtil jwtUtil;

    /**
     * 用户登录认证核心流程。
     * 步骤：
     * 1. 按用户编码查出用户对象
     * 2. 校验用户状态（是否ENABLED）
     * 3. SHA-256 对比密码哈希（数据库中存的是哈希，不存明文）
     * 4. 更新最后登录时间
     * 5. 签发 JWT Token（含 userId + userName）
     * 6. 查出用户角色和权限，一并返回给前端用于路由守卫
     *
     * @param userCode 用户登录编码
     * @param password 明文密码
     * @return LoginResponse 含 Token、用户信息、角色列表、权限列表
     */
    @Override
    public LoginResponse login(String userCode, String password) {
        // 第一步：按编码查用户
        SysUser user = sysUserMapper.selectByUserCode(userCode);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 第二步：检查用户是否已被禁用
        if (!"ENABLED".equals(user.getUserStatus())) {
            throw new BusinessException("用户已被禁用");
        }
        // 第三步：SHA-256 哈希对比密码
        if (!DigestUtil.sha256Hex(password).equals(user.getPasswordHash())) {
            throw new BusinessException("密码错误");
        }
        // 第四步：更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
        // 第五步：签发 JWT Token（前端后续请求在 Header 中携带此 Token）
        String token = jwtUtil.generateToken(user.getUserId(), user.getUserName());
        // 第六步：查出角色和权限
        List<SysRole> roles = listRolesByUserId(user.getUserId());  // 该用户拥有的角色
        List<SysPermission> permissions = listPermissionsByRoles(roles); // 通过角色间接获取权限
        // 组装返回对象
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUser(maskPassword(user));              // 清除密码哈希后再返回
        response.setRoles(roles);
        response.setPermissions(permissions);
        response.setRoleCodes(roles.stream().map(SysRole::getRoleCode).collect(Collectors.toList()));
        response.setPermissionCodes(permissions.stream().map(SysPermission::getPermissionCode).collect(Collectors.toList()));
        response.setPrimaryRoleCode(response.getRoleCodes().isEmpty() ? null : response.getRoleCodes().get(0)); // 主角色=第一个角色
        return response;
    }

    /**
     * 查询全部用户列表，返回时附带角色信息并清除密码哈希。
     * 流式处理：先 attachRoles（挂角色），再 maskPassword（脱敏密码）。
     */
    @Override
    public List<SysUser> listUsers() {
        return sysUserMapper.selectList(null).stream()
                .map(this::attachRoles)   // 为每个用户附加角色ID和角色名列表
                .map(this::maskPassword)  // 清除密码哈希（安全起见不返回前端）
                .collect(Collectors.toList());
    }

    @Override
    public List<SysUser> listUsersByRoleCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return Collections.emptyList();
        }
        SysRole role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode.trim().toUpperCase()));
        if (role == null) {
            return Collections.emptyList();
        }
        List<Long> userIds = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getRoleId, role.getRoleId()))
                .stream()
                .map(SysUserRole::getUserId)
                .distinct()
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return Collections.emptyList();
        }
        return sysUserMapper.selectBatchIds(userIds).stream()
                .filter(user -> user != null
                        && (user.getDeletedFlag() == null || user.getDeletedFlag() == 0)
                        && "ENABLED".equals(user.getUserStatus()))
                .map(this::attachRoles)
                .map(this::maskPassword)
                .collect(Collectors.toList());
    }

    @Override
    public SysUser getUserById(Long userId) {
        return maskPassword(attachRoles(sysUserMapper.selectById(userId)));
    }

    /**
     * 新增用户。
     * 如果未传原始密码，默认密码为 "123456"（SHA-256 后存入数据库）。
     * 默认用户状态为 ENABLED。
     */
    @Override
    public SysUser addUser(SysUser user, String rawPassword) {
        // 对明文密码做 SHA-256 哈希后存储（数据库不存明文）
        user.setPasswordHash(DigestUtil.sha256Hex(rawPassword == null ? "123456" : rawPassword));
        if (user.getUserStatus() == null) {
            user.setUserStatus("ENABLED"); // 默认直接启用
        }
        sysUserMapper.insert(user);
        return maskPassword(user); // 返回时清除密码哈希
    }

    /**
     * 更新用户信息。
     * 注意：updateById 之前先清空 passwordHash，防止空值覆盖数据库中的现有密码。
     * 同时也防止通过更新接口间接修改密码。
     */
    @Override
    public SysUser updateUser(SysUser user) {
        user.setPasswordHash(null); // 清空密码字段，更新时不修改密码
        sysUserMapper.updateById(user);
        return maskPassword(sysUserMapper.selectById(user.getUserId()));
    }

    /**
     * 禁用用户（逻辑删除）。
     * 不物理删除用户及其关联，数据库中的记录保留用于审计。
     */
    @Override
    public void disableUser(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setUserStatus("DISABLED"); // 改为禁用状态
        sysUserMapper.updateById(user);
    }

    /**
     * 为用户分配角色。
     * 业务约束：一个用户只能拥有一个角色（一人一角色模型）。
     * 实现方式：先删除该用户所有角色关联，再插入新的关联。
     * 如果已存在该角色关联，直接返回不做任何操作。
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     */
    @Override
    @Transactional
    public void assignRole(Long userId, Long roleId) {
        if (userId == null || roleId == null) {
            throw new BusinessException("用户和角色不能为空");
        }
        // 校验用户和角色都存在
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        // 检查是否已经是该角色（幂等：相同角色不重复分配）
        long exists = sysUserRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getRoleId, roleId));
        if (exists > 0) {
            return; // 已经是该角色，无需操作
        }
        // 先删除旧的用户-角色关联（保证一个用户一个角色）
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        // 再插入新的关联
        SysUserRole ur = new SysUserRole();
        ur.setUserId(userId);
        ur.setRoleId(roleId);
        sysUserRoleMapper.insert(ur);
    }

    @Override
    public List<SysRole> listRoles() {
        return sysRoleMapper.selectList(null);
    }

    @Override
    public List<SysPermission> listPermissions() {
        return sysPermissionMapper.selectList(null);
    }

    @Override
    public List<SysRolePermission> listRolePermissions() {
        return sysRolePermissionMapper.selectList(null);
    }

    /**
     * 检查用户是否具有指定角色编码。
     * 用于前端按钮/路由的权限判断。
     * @return true 如果用户拥有该角色
     */
    @Override
    public boolean hasRole(Long userId, String roleCode) {
        if (userId == null || roleCode == null) {
            return false;
        }
        return listRolesByUserId(userId).stream()
                .anyMatch(role -> roleCode.equalsIgnoreCase(role.getRoleCode()));
    }

    /**
     * 记录审计日志。
     * 所有关键操作（登录、赋权、修改用户等）都通过此方法记录到 sys_audit_log。
     */
    @Override
    public void recordAuditLog(SysAuditLog log) {
        if (log.getOperatedAt() == null) {
            log.setOperatedAt(LocalDateTime.now()); // 操作时间默认为当前时间
        }
        sysAuditLogMapper.insert(log);
    }

    /**
     * 批量禁用用户。
     * 遍历用户列表，逐个查出后改为 DISABLED 状态。
     */
    @Override
    @Transactional
    public void batchDisableUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        for (Long userId : userIds) {
            SysUser user = sysUserMapper.selectById(userId);
            if (user != null) {
                user.setUserStatus("DISABLED");
                sysUserMapper.updateById(user);
            }
        }
    }

    /**
     * 按用户编码查找用户（返回时已清除密码哈希）。
     */
    @Override
    public SysUser findUserByCode(String userCode) {
        if (userCode == null || userCode.isBlank()) {
            return null;
        }
        SysUser user = sysUserMapper.selectByUserCode(userCode);
        return user == null ? null : maskPassword(user);
    }

    /**
     * 物理删除用户。
     * 先删除角色关联，再删除用户本身。
     * 两步操作在同一事务中，保证数据一致性。
     */
    @Override
    @Transactional
    public void deleteUser(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 删除角色关联
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        // 物理删除用户
        sysUserMapper.deleteById(userId);
    }

    /**
     * 批量物理删除用户。
     * 对每个用户：删除角色关联 → 删除用户。
     */
    @Override
    @Transactional
    public void batchDeleteUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        for (Long userId : userIds) {
            // 删除角色关联
            sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getUserId, userId));
            // 物理删除用户
            sysUserMapper.deleteById(userId);
        }
    }

    /**
     * 更新角色的权限列表。
     * 全量替换策略：先删除旧的所有角色-权限关联，再批量插入新的关联。
     * 这是权限管理中最核心的操作之一。
     * 事务保证：删除和插入在同一事务中，要么全成功要么全回滚。
     *
     * @param roleId 角色ID
     * @param permissionIds 新的权限ID列表（覆盖旧列表）
     */
    @Override
    @Transactional
    public void updateRolePermissions(Long roleId, List<Long> permissionIds) {
        if (roleId == null) {
            throw new BusinessException("角色ID不能为空");
        }
        // 校验角色存在
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        // 第一步：删除该角色所有旧的权限关联（全量替换策略）
        sysRolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, roleId));
        // 第二步：批量插入新的权限关联
        if (permissionIds != null && !permissionIds.isEmpty()) {
            for (Long permId : permissionIds) {
                SysRolePermission rp = new SysRolePermission();
                rp.setRoleId(roleId);
                rp.setPermissionId(permId);
                sysRolePermissionMapper.insert(rp);
            }
        }
    }

    /**
     * 根据用户ID查询用户拥有的角色列表。
     * 两步查询：sys_user_role 关联表 → sys_role 角色表。
     * 自动过滤已禁用的角色（只有 ENABLED 的角色才生效）。
     */
    private List<SysRole> listRolesByUserId(Long userId) {
        // 第一步：从用户-角色关联表中查出该用户的所有 roleId
        List<Long> roleIds = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId))
                .stream()
                .map(SysUserRole::getRoleId)
                .distinct() // 去重
                .collect(Collectors.toList());
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        // 第二步：批量查出角色详情，同时过滤掉非启用状态的角色
        return sysRoleMapper.selectBatchIds(roleIds).stream()
                .filter(role -> role != null && (role.getRoleStatus() == null || "ENABLED".equals(role.getRoleStatus())))
                .collect(Collectors.toList());
    }

    /**
     * 根据角色列表查出所有权限。
     * 三级关联链：角色集 → sys_role_permission 表 → sys_permission 表。
     * 多个角色的权限会去重合并（两个角色可能有相同权限）。
     */
    private List<SysPermission> listPermissionsByRoles(List<SysRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        // 收集所有 roleId
        Set<Long> roleIds = roles.stream().map(SysRole::getRoleId).collect(Collectors.toSet());
        // 从角色-权限关联表中查出所有 permissionId
        List<Long> permissionIds = sysRolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                        .in(SysRolePermission::getRoleId, roleIds))
                .stream()
                .map(SysRolePermission::getPermissionId)
                .distinct() // 去重：多个角色可能有相同权限
                .collect(Collectors.toList());
        if (permissionIds.isEmpty()) {
            return Collections.emptyList();
        }
        // 批量查出权限详情
        return sysPermissionMapper.selectBatchIds(permissionIds);
    }

    /**
     * 密码脱敏：将密码哈希设为 null，防止泄露到前端。
     * 注意：这会直接修改传入对象的字段，调用前需确认不需要保留哈希。
     */
    private SysUser maskPassword(SysUser user) {
        if (user != null) {
            user.setPasswordHash(null); // 永远不把密码哈希返回给前端
        }
        return user;
    }

    /**
     * 为用户附加角色信息（roleIds 和 roleNames 列表）。
     * 用户表本身不存角色信息，需要通过关联表查询并填充到 DTO 字段。
     */
    private SysUser attachRoles(SysUser user) {
        if (user == null || user.getUserId() == null) {
            return user;
        }
        List<SysRole> roles = listRolesByUserId(user.getUserId());
        user.setRoleIds(roles.stream().map(SysRole::getRoleId).collect(Collectors.toList()));
        user.setRoleNames(roles.stream().map(SysRole::getRoleName).collect(Collectors.toList()));
        return user;
    }
}
