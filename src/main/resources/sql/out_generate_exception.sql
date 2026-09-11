-- 仓管：出库单生成失败（缺料等）异常记录
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS out_generate_exception (
    exception_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_task_id BIGINT NULL,
    requisition_id BIGINT NOT NULL,
    requisition_no VARCHAR(64) NULL,
    plan_id BIGINT NULL,
    plan_no VARCHAR(64) NULL,
    item_id BIGINT NULL,
    material_name VARCHAR(128) NULL,
    shortage_qty DECIMAL(18,3) NULL,
    exception_type VARCHAR(32) NOT NULL DEFAULT 'SHORTAGE',
    exception_desc VARCHAR(512) NULL,
    exception_status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    resolve_result VARCHAR(256) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at DATETIME NULL,
    INDEX idx_out_gen_ex_req (requisition_id),
    INDEX idx_out_gen_ex_status (exception_status),
    INDEX idx_out_gen_ex_task (agent_task_id)
);
