-- 生产工人作业台表结构 + 演示数据
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS worker_scan_record (
    scan_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    picking_task_id BIGINT NOT NULL,
    picking_line_id BIGINT NULL,
    worker_id BIGINT NOT NULL,
    barcode_value VARCHAR(128) NOT NULL,
    item_id BIGINT NULL,
    batch_id BIGINT NULL,
    material_name VARCHAR(128) NULL,
    batch_no VARCHAR(64) NULL,
    scan_result VARCHAR(32) NOT NULL,
    result_message VARCHAR(256) NULL,
    scanned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_worker_scan_task (picking_task_id),
    INDEX idx_worker_scan_worker (worker_id)
);

CREATE TABLE IF NOT EXISTS worker_exception (
    exception_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    picking_task_id BIGINT NOT NULL,
    picking_line_id BIGINT NULL,
    worker_id BIGINT NOT NULL,
    work_order_no VARCHAR(64) NULL,
    requisition_no VARCHAR(64) NULL,
    material_name VARCHAR(128) NULL,
    required_qty DECIMAL(18,3) NULL,
    actual_qty DECIMAL(18,3) NULL,
    shortage_qty DECIMAL(18,3) NULL,
    exception_type VARCHAR(32) NOT NULL,
    exception_note VARCHAR(512) NULL,
    location_code VARCHAR(64) NULL,
    exception_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_WAREHOUSE',
    submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_worker_ex_task (picking_task_id),
    INDEX idx_worker_ex_worker (worker_id)
);

CREATE TABLE IF NOT EXISTS worker_handover (
    handover_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    picking_task_id BIGINT NOT NULL,
    worker_id BIGINT NOT NULL,
    work_order_no VARCHAR(64) NULL,
    requisition_no VARCHAR(64) NULL,
    warehouse_handler VARCHAR(64) NULL,
    handover_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark VARCHAR(256) NULL,
    UNIQUE KEY uk_handover_task (picking_task_id)
);

-- 拣货行增加工人已扫数量（若列已存在则跳过）
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'out_picking_line' AND COLUMN_NAME = 'worker_scanned_qty'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE out_picking_line ADD COLUMN worker_scanned_qty DECIMAL(18,3) NOT NULL DEFAULT 0 AFTER actual_pick_qty',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 拣货任务增加计划领料时间
SET @col_exists2 = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'out_picking_task' AND COLUMN_NAME = 'planned_pick_time'
);
SET @ddl2 = IF(@col_exists2 = 0,
    'ALTER TABLE out_picking_task ADD COLUMN planned_pick_time DATETIME NULL AFTER task_status',
    'SELECT 1');
PREPARE stmt2 FROM @ddl2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- MES 工单号
UPDATE pmc_production_plan SET mes_plan_no = 'WO20260708009' WHERE plan_id = 1;
UPDATE pmc_production_plan SET mes_plan_no = 'WO20260708012' WHERE plan_id = 2;
UPDATE pmc_production_plan SET mes_plan_no = 'WO20260708006' WHERE plan_id = 3;
UPDATE pmc_production_plan SET mes_plan_no = 'WO20260708010' WHERE plan_id = 4;

UPDATE out_picking_task SET planned_pick_time = '2026-07-09 09:30:00' WHERE picking_task_id = 1;
UPDATE out_picking_task SET planned_pick_time = '2026-07-08 16:30:00' WHERE picking_task_id = 2;
UPDATE out_picking_task SET planned_pick_time = '2026-07-09 10:00:00' WHERE picking_task_id = 3;

-- 拣货明细
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty) VALUES
(1, 1, 1, 1, 5, 1, 1, 40.000, 0.000, 40.000),
(2, 1, 2, 2, 6, 2, 1, 40.000, 0.000, 36.000),
(3, 1, 3, 4, 8, 3, 1, 20.000, 0.000, 20.000),
(4, 2, 4, 3, 7, 1, 1, 36.000, 0.000, 10.000),
(5, 2, 5, 2, 6, 2, 1, 24.000, 0.000, 24.000)
ON DUPLICATE KEY UPDATE worker_scanned_qty = VALUES(worker_scanned_qty);

-- 条码绑定
INSERT INTO barcode_label (barcode_id, barcode_value, code_type, bind_type, bind_id, status, created_at) VALUES
(1, 'B20260706004-M-EXPROOF-0001', 'ITEM_BATCH', 'INVENTORY', 2, 'ENABLED', NOW()),
(2, 'B20260705011-M-SEAL-0001', 'ITEM_BATCH', 'INVENTORY', 1, 'ENABLED', NOW()),
(3, 'B20260707002-M-SHELL-0001', 'ITEM_BATCH', 'INVENTORY', 4, 'ENABLED', NOW()),
(4, 'BC0001', 'ITEM_BATCH', 'INVENTORY', 2, 'ENABLED', NOW()),
(5, 'BC0002', 'ITEM_BATCH', 'INVENTORY', 2, 'ENABLED', NOW())
ON DUPLICATE KEY UPDATE status = VALUES(status);

-- 演示异常
INSERT INTO worker_exception (exception_id, picking_task_id, picking_line_id, worker_id, work_order_no, requisition_no, material_name, required_qty, actual_qty, shortage_qty, exception_type, exception_note, location_code, exception_status, submitted_at) VALUES
(1, 1, 2, 1, 'WO20260708009', 'REQ20260708018', '防爆接插件', 40.000, 36.000, 4.000, '缺件', '现场只收到 36 件', 'A-03-12', 'PENDING_WAREHOUSE', '2026-07-09 09:42:00'),
(2, 2, 4, 1, 'WO20260708006', 'REQ20260708019', '耐高压连接器', 36.000, 10.000, 26.000, '缺件', '昨日领料未完成', 'A-03-02', 'PENDING_WAREHOUSE', '2026-07-08 17:05:00')
ON DUPLICATE KEY UPDATE exception_status = VALUES(exception_status);

CREATE TABLE IF NOT EXISTS worker_notification (
    notification_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    worker_id BIGINT NOT NULL,
    notify_type VARCHAR(32) NOT NULL,
    title VARCHAR(128) NOT NULL,
    content VARCHAR(512) NULL,
    related_doc VARCHAR(64) NULL,
    picking_task_id BIGINT NULL,
    read_flag TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_worker_notify_worker (worker_id),
    INDEX idx_worker_notify_read (worker_id, read_flag)
);

CREATE TABLE IF NOT EXISTS worker_production_completion (
    completion_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    worker_id BIGINT NOT NULL,
    work_order_no VARCHAR(64) NOT NULL,
    item_id BIGINT NULL,
    batch_id BIGINT NULL,
    inventory_id BIGINT NULL,
    material_name VARCHAR(128) NULL,
    material_code VARCHAR(64) NULL,
    batch_no VARCHAR(64) NULL,
    qty DECIMAL(18,3) NOT NULL,
    barcode_value VARCHAR(128) NULL,
    warehouse_id BIGINT NULL,
    location_id BIGINT NULL,
    location_code VARCHAR(64) NULL,
    completion_status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
    remark VARCHAR(256) NULL,
    submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_worker_completion_worker (worker_id)
);

CREATE TABLE IF NOT EXISTS worker_process_transfer (
    transfer_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    worker_id BIGINT NOT NULL,
    transfer_card VARCHAR(64) NULL,
    container_code VARCHAR(64) NULL,
    from_process VARCHAR(64) NOT NULL,
    to_process VARCHAR(64) NOT NULL,
    work_order_no VARCHAR(64) NULL,
    item_id BIGINT NULL,
    material_name VARCHAR(128) NULL,
    qty DECIMAL(18,3) NOT NULL DEFAULT 1,
    transfer_status VARCHAR(32) NOT NULL DEFAULT 'TRANSFERRED',
    remark VARCHAR(256) NULL,
    submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_worker_transfer_worker (worker_id)
);
