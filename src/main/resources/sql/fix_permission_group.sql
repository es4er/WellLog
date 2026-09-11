-- 紧急修复：为 sys_permission 表添加 permission_group 列
ALTER TABLE sys_permission ADD COLUMN permission_group VARCHAR(64) DEFAULT NULL;

-- 更新已有权限的分组
UPDATE sys_permission SET permission_group = '系统管理' WHERE permission_code LIKE 'admin:%';
UPDATE sys_permission SET permission_group = 'PMC 计划' WHERE permission_code LIKE 'pmc:%';
UPDATE sys_permission SET permission_group = '仓库管理' WHERE permission_code LIKE 'warehouse:%';
UPDATE sys_permission SET permission_group = '质量管理' WHERE permission_code LIKE 'quality:%';
UPDATE sys_permission SET permission_group = '库存控制' WHERE permission_code LIKE 'inventory:%';
UPDATE sys_permission SET permission_group = '生产执行' WHERE permission_code LIKE 'worker:%';
UPDATE sys_permission SET permission_group = '数据分析' WHERE permission_code LIKE 'analytics:%';
