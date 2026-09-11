CREATE TABLE IF NOT EXISTS sys_user (
  user_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_code VARCHAR(64) NOT NULL UNIQUE,
  user_name VARCHAR(128) NOT NULL,
  password_hash VARCHAR(128) NOT NULL,
  phone VARCHAR(32),
  email VARCHAR(128),
  dept_name VARCHAR(128),
  user_status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  last_login_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_flag TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_role (
  role_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_code VARCHAR(64) NOT NULL UNIQUE,
  role_name VARCHAR(128) NOT NULL,
  role_status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_permission (
  permission_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  permission_code VARCHAR(128) NOT NULL UNIQUE,
  permission_name VARCHAR(128) NOT NULL,
  resource_type VARCHAR(32),
  resource_path VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS sys_user_role (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS sys_role_permission (
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS sys_audit_log (
  audit_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT,
  operation_type VARCHAR(64),
  operation_desc VARCHAR(255),
  business_type VARCHAR(64),
  business_id VARCHAR(64),
  request_uri VARCHAR(255),
  request_method VARCHAR(16),
  request_params TEXT,
  response_result TEXT,
  ip_address VARCHAR(64),
  operated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO sys_role (role_code, role_name, role_status)
VALUES
  ('ADMIN', '系统管理员', 'ENABLED'),
  ('PMC', 'PMC计划员', 'ENABLED'),
  ('WAREHOUSE', '仓管员', 'ENABLED'),
  ('QUALITY', '质检员', 'ENABLED'),
  ('INVENTORY', '库存管理员', 'ENABLED'),
  ('WORKER', '生产工人', 'ENABLED')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), role_status = VALUES(role_status);

-- 为权限表增加分组字段（用于前端按模块分组展示）
ALTER TABLE sys_permission ADD COLUMN IF NOT EXISTS permission_group VARCHAR(64);

-- 扩展细粒度权限（按真实业务模块分组，对齐菜单路由与操作按钮）
INSERT INTO sys_permission (permission_code, permission_name, resource_type, resource_path, permission_group)
VALUES
  -- 系统管理（管理员专有）
  ('admin:user:view',    '查看用户列表',   'MENU',   '/admin/users',            '系统管理'),
  ('admin:user:edit',    '编辑/禁用用户',  'BUTTON', '/user/add /user/update',   '系统管理'),
  ('admin:role:view',    '查看角色配置',   'MENU',   '/admin/roles',             '系统管理'),
  ('admin:audit:view',   '查看审计日志',   'MENU',   '/admin/permission-audit',  '系统管理'),
  ('admin:system:config','系统配置管理',   'BUTTON', '/admin/config',            '系统管理'),
  ('admin:monitor:view', '查看接口与Agent监控', 'MENU', '/admin/api-monitor',    '系统管理'),
  ('admin:backup:manage','系统备份管理',   'BUTTON', '/admin/backup',            '系统管理'),
  -- PMC 计划
  ('pmc:order:view',     '查看客户订单',   'MENU',   '/plan-center',             'PMC 计划'),
  ('pmc:order:audit',    '审核订单',       'BUTTON', '/pmc/order/audit',         'PMC 计划'),
  ('pmc:plan:create',    '生成生产计划',   'BUTTON', '/pmc/plan/create',         'PMC 计划'),
  ('pmc:kitting:verify', '齐套校验',       'BUTTON', '/pmc/kitting/verify',      'PMC 计划'),
  ('pmc:plan:export',    '导出计划数据',   'BUTTON', '/pmc/plan/export',         'PMC 计划'),
  -- 仓库管理
  ('warehouse:inbound:view',    '查看收货上架',   'MENU',   '/warehouse-receiving',    '仓库管理'),
  ('warehouse:outbound:create', '生成出库单',     'BUTTON', '/warehouse/outbound/create','仓库管理'),
  ('warehouse:outbound:view',   '查看出库单',     'MENU',   '/outbound-orders',         '仓库管理'),
  ('warehouse:picking:assign',  '分配拣货任务',   'BUTTON', '/warehouse/picking/assign', '仓库管理'),
  ('warehouse:picking:view',    '查看拣货任务',   'MENU',   '/warehouse-picking',       '仓库管理'),
  ('warehouse:exception:handle','处理仓库异常',   'BUTTON', '/warehouse/exceptions',    '仓库管理'),
  ('warehouse:inventory:view',  '查看库存数据',   'DATA',   '/warehouse/inventory',     '仓库管理'),
  -- 质量管理
  ('quality:task:view',         '查看待检任务',   'MENU',   '/quality/tasks',           '质量管理'),
  ('quality:inspection:execute','执行检测',       'BUTTON', '/quality/execute',         '质量管理'),
  ('quality:issue:manage',      '管理质量异常',   'BUTTON', '/quality/issues',          '质量管理'),
  ('quality:standard:edit',     '维护检验标准',   'BUTTON', '/quality/standard',        '质量管理'),
  ('quality:analytics:view',    '查看质量分析',   'MENU',   '/quality/analytics',       '质量管理'),
  -- 库存控制
  ('inventory:stock:view',      '库存余额查询',   'MENU',   '/inventory/stock',         '库存控制'),
  ('inventory:count:execute',   '执行盘点',       'BUTTON', '/inventory/count',         '库存控制'),
  ('inventory:adjust:create',   '生成调整单',     'BUTTON', '/inventory/adjust',        '库存控制'),
  ('inventory:location:view',   '查看库位地图',   'MENU',   '/inventory/location',      '库存控制'),
  -- 生产执行
  ('worker:task:view',          '查看今日任务',   'MENU',   '/worker-today',            '生产执行'),
  ('worker:scan:confirm',       '扫码确认',       'BUTTON', '/scan-confirm',            '生产执行'),
  ('worker:replenish:apply',    '提交补料申请',   'BUTTON', '/replenishment',           '生产执行'),
  ('worker:exception:report',   '现场异常反馈',   'BUTTON', '/exception-feedback',      '生产执行'),
  ('worker:completed:view',     '查看完成记录',   'MENU',   '/worker-completed',        '生产执行'),
  -- 数据分析
  ('analytics:dashboard:view',  '查看数据分析',   'MENU',   '/analytics',               '数据分析'),
  ('analytics:report:export',   '导出分析报告',   'BUTTON', '/analytics/export',        '数据分析')
ON DUPLICATE KEY UPDATE
  permission_name = VALUES(permission_name),
  resource_type   = VALUES(resource_type),
  resource_path   = VALUES(resource_path),
  permission_group = VALUES(permission_group);

INSERT INTO sys_user (user_code, user_name, password_hash, dept_name, user_status)
VALUES
  ('admin', '系统管理员', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', '信息部', 'ENABLED'),
  ('pmc', '周计划', '4954b249abf7f0a5c6caa27f5bf5fabc0c8130bde4170444d78fcf910e4f26bf', '计划部', 'ENABLED'),
  ('warehouse', '沈仓管', 'da51aea1b9e0897c3b78a31240cbb5f0bdb92645e2537e2ddfe1aa75daa4d74a', '仓储部', 'ENABLED'),
  ('quality', '程质检', 'e97af628deabddcc642d00c9b0fa3c488e54fe9bbe557975e5f45e5c9f04ea82', '质量部', 'ENABLED'),
  ('inventory', '韩库存', '170b00da0d752f0eef5fa3608ea2e6c0bd751a9bf539dc101ebe9425f5003c53', '仓储部', 'ENABLED'),
  ('worker', '赵工', '312bba6ac1c4274943d7d3c1f346e8e27310c731e407ce5592d82f0d101fbff1', '生产部', 'ENABLED')
ON DUPLICATE KEY UPDATE
  user_name = VALUES(user_name),
  password_hash = VALUES(password_hash),
  dept_name = VALUES(dept_name),
  user_status = VALUES(user_status);

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.user_id, r.role_id FROM sys_user u JOIN sys_role r ON r.role_code = 'ADMIN' WHERE u.user_code = 'admin';
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.user_id, r.role_id FROM sys_user u JOIN sys_role r ON r.role_code = 'PMC' WHERE u.user_code = 'pmc';
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.user_id, r.role_id FROM sys_user u JOIN sys_role r ON r.role_code = 'WAREHOUSE' WHERE u.user_code = 'warehouse';
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.user_id, r.role_id FROM sys_user u JOIN sys_role r ON r.role_code = 'QUALITY' WHERE u.user_code = 'quality';
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.user_id, r.role_id FROM sys_user u JOIN sys_role r ON r.role_code = 'INVENTORY' WHERE u.user_code = 'inventory';
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.user_id, r.role_id FROM sys_user u JOIN sys_role r ON r.role_code = 'WORKER' WHERE u.user_code = 'worker';

-- 系统管理员：拥有全部权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM sys_role r JOIN sys_permission p WHERE r.role_code = 'ADMIN';

-- PMC计划员：计划与数据权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM sys_role r JOIN sys_permission p
 ON p.permission_code IN (
   'pmc:order:view','pmc:order:audit','pmc:plan:create','pmc:kitting:verify','pmc:plan:export',
   'analytics:dashboard:view','analytics:report:export'
 ) WHERE r.role_code = 'PMC';

-- 仓管员：仓库操作与库存查看权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM sys_role r JOIN sys_permission p
 ON p.permission_code IN (
   'warehouse:inbound:view','warehouse:outbound:create','warehouse:outbound:view',
   'warehouse:picking:assign','warehouse:picking:view','warehouse:exception:handle',
   'warehouse:inventory:view','analytics:dashboard:view'
 ) WHERE r.role_code = 'WAREHOUSE';

-- 质检员：质量全流程权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM sys_role r JOIN sys_permission p
 ON p.permission_code IN (
   'quality:task:view','quality:inspection:execute','quality:issue:manage',
   'quality:standard:edit','quality:analytics:view','analytics:dashboard:view'
 ) WHERE r.role_code = 'QUALITY';

-- 库存管理员：库存控制权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM sys_role r JOIN sys_permission p
 ON p.permission_code IN (
   'inventory:stock:view','inventory:count:execute','inventory:adjust:create',
   'inventory:location:view','warehouse:inventory:view','analytics:dashboard:view'
 ) WHERE r.role_code = 'INVENTORY';

-- 生产工人：领料执行权限（最少）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM sys_role r JOIN sys_permission p
 ON p.permission_code IN (
   'worker:task:view','worker:scan:confirm','worker:replenish:apply',
   'worker:exception:report','worker:completed:view'
 ) WHERE r.role_code = 'WORKER';
