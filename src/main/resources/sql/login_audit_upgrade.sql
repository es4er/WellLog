-- ============================================================
-- 登录审计 & 权限审计升级脚本
-- 新增 user_agent（设备/浏览器标识）和 operation_result（操作结果）
-- ============================================================

ALTER TABLE sys_audit_log
    ADD COLUMN IF NOT EXISTS user_agent VARCHAR(512) AFTER ip_address;

ALTER TABLE sys_audit_log
    ADD COLUMN IF NOT EXISTS operation_result VARCHAR(32) AFTER user_agent;
