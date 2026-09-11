-- 修复：将 quality 用户的角色重置为 QUALITY（质检员）
-- 如果 quality 用户因误操作被分配了 PMC 角色，执行此脚本修复

-- 1. 删除 quality 用户现有的所有角色绑定
DELETE ur FROM sys_user_role ur
JOIN sys_user u ON u.user_id = ur.user_id
WHERE u.user_code = 'quality';

-- 2. 重新分配 QUALITY 角色
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.user_id, r.role_id
FROM sys_user u
JOIN sys_role r ON r.role_code = 'QUALITY'
WHERE u.user_code = 'quality';

-- 3. 验证（应显示 QUALITY 角色）
SELECT u.user_code, u.user_name, r.role_code, r.role_name
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id = u.user_id
JOIN sys_role r ON r.role_id = ur.role_id
WHERE u.user_code = 'quality';
