-- 生产工人演示数据（由 scripts/seed-worker-demo.mjs 生成）
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 扩展拣货任务展示字段（兼容无 IF NOT EXISTS 的 MySQL）
SET @db := DATABASE();


SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='out_picking_task' AND COLUMN_NAME='priority');
SET @sql := IF(@exists=0, 'ALTER TABLE out_picking_task ADD COLUMN priority VARCHAR(10) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='out_picking_task' AND COLUMN_NAME='product_name');
SET @sql := IF(@exists=0, 'ALTER TABLE out_picking_task ADD COLUMN product_name VARCHAR(150) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='out_picking_task' AND COLUMN_NAME='plan_qty');
SET @sql := IF(@exists=0, 'ALTER TABLE out_picking_task ADD COLUMN plan_qty DECIMAL(18,3) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='out_picking_task' AND COLUMN_NAME='unit');
SET @sql := IF(@exists=0, 'ALTER TABLE out_picking_task ADD COLUMN unit VARCHAR(16) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='out_picking_task' AND COLUMN_NAME='handler_name');
SET @sql := IF(@exists=0, 'ALTER TABLE out_picking_task ADD COLUMN handler_name VARCHAR(64) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='out_picking_task' AND COLUMN_NAME='cancelled');
SET @sql := IF(@exists=0, 'ALTER TABLE out_picking_task ADD COLUMN cancelled TINYINT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='out_picking_task' AND COLUMN_NAME='work_order_no');
SET @sql := IF(@exists=0, 'ALTER TABLE out_picking_task ADD COLUMN work_order_no VARCHAR(64) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='out_picking_task' AND COLUMN_NAME='requisition_no');
SET @sql := IF(@exists=0, 'ALTER TABLE out_picking_task ADD COLUMN requisition_no VARCHAR(64) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


CREATE TABLE IF NOT EXISTS worker_replenish_request (
  replenish_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  picking_task_id BIGINT NULL,
  picking_line_id BIGINT NULL,
  worker_id BIGINT NOT NULL,
  work_order_no VARCHAR(64) NULL,
  requisition_no VARCHAR(64) NULL,
  product_name VARCHAR(128) NULL,
  item_id BIGINT NULL,
  material_code VARCHAR(64) NULL,
  material_name VARCHAR(128) NULL,
  spec_model VARCHAR(128) NULL,
  request_qty DECIMAL(18,3) NOT NULL,
  reason VARCHAR(64) NOT NULL,
  note VARCHAR(512) NULL,
  request_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW',
  submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  handler_name VARCHAR(64) NULL,
  INDEX idx_wr_worker (worker_id),
  INDEX idx_wr_task (picking_task_id),
  INDEX idx_wr_status (request_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


DELETE FROM worker_replenish_request WHERE replenish_id BETWEEN 1001 AND 1999 OR worker_id = 1;
DELETE FROM worker_handover WHERE picking_task_id BETWEEN 101 AND 199;
DELETE FROM worker_scan_record WHERE picking_task_id BETWEEN 101 AND 199 OR worker_id = 1;
DELETE FROM worker_exception WHERE picking_task_id BETWEEN 101 AND 199 OR worker_id = 1;
DELETE FROM out_picking_line WHERE picking_task_id BETWEEN 101 AND 199;
DELETE FROM out_picking_task WHERE picking_task_id BETWEEN 101 AND 199;
UPDATE out_picking_task SET assigned_to = NULL WHERE assigned_to = 1 AND picking_task_id < 101;
DELETE FROM out_order_line WHERE outbound_line_id BETWEEN 1001 AND 1999;
DELETE FROM out_order WHERE outbound_id BETWEEN 101 AND 199;
DELETE FROM pmc_requisition_line WHERE requisition_line_id BETWEEN 1001 AND 1999;
DELETE FROM pmc_requisition_order WHERE requisition_id BETWEEN 101 AND 199;
DELETE FROM pmc_production_plan_line WHERE plan_line_id BETWEEN 1001 AND 1999;
DELETE FROM pmc_production_plan WHERE plan_id BETWEEN 101 AND 199;
DELETE FROM inv_inventory WHERE inventory_id BETWEEN 201 AND 999;
DELETE FROM md_batch WHERE batch_id BETWEEN 201 AND 999;
DELETE FROM md_item WHERE item_id BETWEEN 201 AND 999;
DELETE FROM wh_location WHERE location_id BETWEEN 101 AND 999;

-- 库位（已存在编码则复用，避免 UNIQUE 冲突导致 location_id 悬空）
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 101, 1, 'A-03-02', 'A-03-02', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'A-03-02');
SET @loc_101 := (SELECT location_id FROM wh_location WHERE location_code = 'A-03-02' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 102, 1, 'A-03-08', 'A-03-08', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'A-03-08');
SET @loc_102 := (SELECT location_id FROM wh_location WHERE location_code = 'A-03-08' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 103, 1, 'B-01-03', 'B-01-03', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'B-01-03');
SET @loc_103 := (SELECT location_id FROM wh_location WHERE location_code = 'B-01-03' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 104, 1, 'C-02-01', 'C-02-01', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'C-02-01');
SET @loc_104 := (SELECT location_id FROM wh_location WHERE location_code = 'C-02-01' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 105, 1, 'C-02-05', 'C-02-05', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'C-02-05');
SET @loc_105 := (SELECT location_id FROM wh_location WHERE location_code = 'C-02-05' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 106, 1, 'D-01-02', 'D-01-02', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'D-01-02');
SET @loc_106 := (SELECT location_id FROM wh_location WHERE location_code = 'D-01-02' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 107, 1, 'D-01-04', 'D-01-04', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'D-01-04');
SET @loc_107 := (SELECT location_id FROM wh_location WHERE location_code = 'D-01-04' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 108, 1, 'E-02-01', 'E-02-01', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'E-02-01');
SET @loc_108 := (SELECT location_id FROM wh_location WHERE location_code = 'E-02-01' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 109, 1, 'E-02-03', 'E-02-03', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'E-02-03');
SET @loc_109 := (SELECT location_id FROM wh_location WHERE location_code = 'E-02-03' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 110, 1, 'F-01-01', 'F-01-01', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'F-01-01');
SET @loc_110 := (SELECT location_id FROM wh_location WHERE location_code = 'F-01-01' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 111, 1, 'G-03-02', 'G-03-02', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'G-03-02');
SET @loc_111 := (SELECT location_id FROM wh_location WHERE location_code = 'G-03-02' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 112, 1, 'G-03-04', 'G-03-04', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'G-03-04');
SET @loc_112 := (SELECT location_id FROM wh_location WHERE location_code = 'G-03-04' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 113, 1, 'H-01-06', 'H-01-06', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'H-01-06');
SET @loc_113 := (SELECT location_id FROM wh_location WHERE location_code = 'H-01-06' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 114, 1, 'A-04-01', 'A-04-01', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'A-04-01');
SET @loc_114 := (SELECT location_id FROM wh_location WHERE location_code = 'A-04-01' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 115, 1, 'A-04-02', 'A-04-02', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'A-04-02');
SET @loc_115 := (SELECT location_id FROM wh_location WHERE location_code = 'A-04-02' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 116, 1, 'A-04-03', 'A-04-03', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'A-04-03');
SET @loc_116 := (SELECT location_id FROM wh_location WHERE location_code = 'A-04-03' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 117, 1, 'A-04-04', 'A-04-04', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'A-04-04');
SET @loc_117 := (SELECT location_id FROM wh_location WHERE location_code = 'A-04-04' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 118, 1, 'A-04-05', 'A-04-05', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'A-04-05');
SET @loc_118 := (SELECT location_id FROM wh_location WHERE location_code = 'A-04-05' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 119, 1, 'A-04-06', 'A-04-06', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'A-04-06');
SET @loc_119 := (SELECT location_id FROM wh_location WHERE location_code = 'A-04-06' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 120, 1, 'A-04-07', 'A-04-07', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'A-04-07');
SET @loc_120 := (SELECT location_id FROM wh_location WHERE location_code = 'A-04-07' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 121, 1, 'A-04-08', 'A-04-08', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'A-04-08');
SET @loc_121 := (SELECT location_id FROM wh_location WHERE location_code = 'A-04-08' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 122, 1, 'B-02-01', 'B-02-01', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'B-02-01');
SET @loc_122 := (SELECT location_id FROM wh_location WHERE location_code = 'B-02-01' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 123, 1, 'B-02-02', 'B-02-02', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'B-02-02');
SET @loc_123 := (SELECT location_id FROM wh_location WHERE location_code = 'B-02-02' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 124, 1, 'C-03-01', 'C-03-01', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'C-03-01');
SET @loc_124 := (SELECT location_id FROM wh_location WHERE location_code = 'C-03-01' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 125, 1, 'Z-99-01', 'Z-99-01', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'Z-99-01');
SET @loc_125 := (SELECT location_id FROM wh_location WHERE location_code = 'Z-99-01' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 126, 1, 'B-03-01', 'B-03-01', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'B-03-01');
SET @loc_126 := (SELECT location_id FROM wh_location WHERE location_code = 'B-03-01' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 127, 1, 'B-03-02', 'B-03-02', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'B-03-02');
SET @loc_127 := (SELECT location_id FROM wh_location WHERE location_code = 'B-03-02' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 128, 1, 'B-03-03', 'B-03-03', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'B-03-03');
SET @loc_128 := (SELECT location_id FROM wh_location WHERE location_code = 'B-03-03' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 129, 1, 'C-04-01', 'C-04-01', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'C-04-01');
SET @loc_129 := (SELECT location_id FROM wh_location WHERE location_code = 'C-04-01' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 130, 1, 'C-04-02', 'C-04-02', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'C-04-02');
SET @loc_130 := (SELECT location_id FROM wh_location WHERE location_code = 'C-04-02' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 131, 1, 'C-04-03', 'C-04-03', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'C-04-03');
SET @loc_131 := (SELECT location_id FROM wh_location WHERE location_code = 'C-04-03' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 132, 1, 'C-04-04', 'C-04-04', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'C-04-04');
SET @loc_132 := (SELECT location_id FROM wh_location WHERE location_code = 'C-04-04' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 133, 1, 'D-02-01', 'D-02-01', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'D-02-01');
SET @loc_133 := (SELECT location_id FROM wh_location WHERE location_code = 'D-02-01' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 134, 1, 'D-02-02', 'D-02-02', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'D-02-02');
SET @loc_134 := (SELECT location_id FROM wh_location WHERE location_code = 'D-02-02' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 135, 1, 'D-02-03', 'D-02-03', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'D-02-03');
SET @loc_135 := (SELECT location_id FROM wh_location WHERE location_code = 'D-02-03' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 136, 1, 'E-01-01', 'E-01-01', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'E-01-01');
SET @loc_136 := (SELECT location_id FROM wh_location WHERE location_code = 'E-01-01' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 137, 1, 'E-01-02', 'E-01-02', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'E-01-02');
SET @loc_137 := (SELECT location_id FROM wh_location WHERE location_code = 'E-01-02' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 138, 1, 'F-02-01', 'F-02-01', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'F-02-01');
SET @loc_138 := (SELECT location_id FROM wh_location WHERE location_code = 'F-02-01' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 139, 1, 'F-02-02', 'F-02-02', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'F-02-02');
SET @loc_139 := (SELECT location_id FROM wh_location WHERE location_code = 'F-02-02' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 140, 1, 'F-02-03', 'F-02-03', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'F-02-03');
SET @loc_140 := (SELECT location_id FROM wh_location WHERE location_code = 'F-02-03' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 141, 1, 'G-01-01', 'G-01-01', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'G-01-01');
SET @loc_141 := (SELECT location_id FROM wh_location WHERE location_code = 'G-01-01' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 142, 1, 'G-01-02', 'G-01-02', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'G-01-02');
SET @loc_142 := (SELECT location_id FROM wh_location WHERE location_code = 'G-01-02' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 143, 1, 'G-01-03', 'G-01-03', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'G-01-03');
SET @loc_143 := (SELECT location_id FROM wh_location WHERE location_code = 'G-01-03' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 144, 1, 'H-02-01', 'H-02-01', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'H-02-01');
SET @loc_144 := (SELECT location_id FROM wh_location WHERE location_code = 'H-02-01' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 145, 1, 'H-02-02', 'H-02-02', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'H-02-02');
SET @loc_145 := (SELECT location_id FROM wh_location WHERE location_code = 'H-02-02' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 146, 1, 'H-02-03', 'H-02-03', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'H-02-03');
SET @loc_146 := (SELECT location_id FROM wh_location WHERE location_code = 'H-02-03' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 147, 1, 'D-03-01', 'D-03-01', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'D-03-01');
SET @loc_147 := (SELECT location_id FROM wh_location WHERE location_code = 'D-03-01' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 148, 1, 'D-03-02', 'D-03-02', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'D-03-02');
SET @loc_148 := (SELECT location_id FROM wh_location WHERE location_code = 'D-03-02' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 149, 1, 'I-01-01', 'I-01-01', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'I-01-01');
SET @loc_149 := (SELECT location_id FROM wh_location WHERE location_code = 'I-01-01' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 150, 1, 'I-01-02', 'I-01-02', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'I-01-02');
SET @loc_150 := (SELECT location_id FROM wh_location WHERE location_code = 'I-01-02' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 151, 1, 'I-01-03', 'I-01-03', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'I-01-03');
SET @loc_151 := (SELECT location_id FROM wh_location WHERE location_code = 'I-01-03' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 152, 1, 'E-03-01', 'E-03-01', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'E-03-01');
SET @loc_152 := (SELECT location_id FROM wh_location WHERE location_code = 'E-03-01' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 153, 1, 'E-03-02', 'E-03-02', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'E-03-02');
SET @loc_153 := (SELECT location_id FROM wh_location WHERE location_code = 'E-03-02' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 154, 1, 'F-03-01', 'F-03-01', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'F-03-01');
SET @loc_154 := (SELECT location_id FROM wh_location WHERE location_code = 'F-03-01' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 155, 1, 'F-03-02', 'F-03-02', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'F-03-02');
SET @loc_155 := (SELECT location_id FROM wh_location WHERE location_code = 'F-03-02' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 156, 1, 'F-03-03', 'F-03-03', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'F-03-03');
SET @loc_156 := (SELECT location_id FROM wh_location WHERE location_code = 'F-03-03' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 157, 1, 'E-02-05', 'E-02-05', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'E-02-05');
SET @loc_157 := (SELECT location_id FROM wh_location WHERE location_code = 'E-02-05' LIMIT 1);
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT 158, 1, 'E-02-06', 'E-02-06', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = 'E-02-06');
SET @loc_158 := (SELECT location_id FROM wh_location WHERE location_code = 'E-02-06' LIMIT 1);
-- 物料
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (201, 'P-W-201', '测井探头组件', 1, 1, '', 'PRODUCT', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (202, 'SEAL-HIGH', '高温密封圈', 2, 2, 'Φ20×2', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (203, 'EXPROOF', '防爆接插件', 2, 2, 'EX-4P', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (204, 'SHELL', '测井探头外壳', 2, 2, 'TP-100', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (205, 'P-W-205', '井下通信模块', 1, 1, '', 'PRODUCT', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (206, 'COMM-BOARD', '通信主板', 2, 2, 'CM-V2', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (207, 'WATER-JOINT', '防水接头', 2, 2, 'WJ-M12', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (208, 'P-W-208', '定向钻具组件', 1, 1, '', 'PRODUCT', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (209, 'DRILL-JOINT', '钻具接头', 2, 2, 'DJ-38', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (210, 'WEAR-SLEEVE', '耐磨套筒', 2, 2, 'WS-38', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (211, 'P-W-211', '压力传感器总成', 1, 1, '', 'PRODUCT', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (212, 'PRESS-CORE', '压力传感芯体', 2, 2, 'PC-200', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (213, 'SS-SHELL', '不锈钢外壳', 2, 2, 'SS-Ø80', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (214, 'P-W-214', '数据采集卡', 1, 1, '', 'PRODUCT', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (215, 'ADC-BOARD', 'ADC 采集板', 2, 2, 'ADC-16', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (216, 'P-W-216', '液压控制阀组', 1, 1, '', 'PRODUCT', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (217, 'VALVE-BODY', '控制阀体', 2, 2, 'VB-25', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (218, 'SEAL-KIT', '密封组件', 2, 2, 'SK-25', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (219, 'P-W-219', '电缆接头组件', 1, 1, '', 'PRODUCT', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (220, 'CABLE-JOINT', '电缆接头', 2, 2, 'CJ-3P', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (221, 'P-W-221', '高温密封组件', 1, 1, '', 'PRODUCT', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (222, 'CERAMIC-SEAL', '陶瓷密封环', 2, 2, 'Φ32×3', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (223, 'GRAPHITE', '石墨垫片', 2, 2, 'G-32', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (224, 'BOLT-M8', '紧固螺栓', 2, 2, 'M8×25', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (225, 'O-RING', 'O 型密封圈', 2, 2, 'Φ20×2', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (226, 'WASHER', '金属垫圈', 2, 2, 'M8', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (227, 'GREASE', '高温润滑脂', 2, 2, 'HT-200', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (228, 'M5008', '连接器', 2, 2, 'CJ-3P', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (229, 'SEALANT', '密封胶', 2, 2, 'SG-50', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (230, 'P-W-230', '传感器壳体', 1, 1, '', 'PRODUCT', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (231, 'SHELL-BODY', '壳体本体', 2, 2, 'SB-Ø90', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (232, 'VIEW-WIN', '透明视窗', 2, 2, 'VW-Ø40', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (233, 'P-W-233', '信号调理模块', 1, 1, '', 'PRODUCT', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (234, 'COND-BOARD', '调理板', 2, 2, 'CB-V1', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (235, 'P-W-235', '临时试制件', 1, 1, '', 'PRODUCT', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (236, 'TRIAL-BRACKET', '试制支架', 2, 2, 'TB-01', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (237, 'P-W-237', '泥浆脉冲发生器', 1, 1, '', 'PRODUCT', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (238, 'PULSE-CORE', '脉冲阀芯', 2, 2, 'PC-M12', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (239, 'DRIVE-COIL', '驱动线圈', 2, 2, 'DC-24V', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (240, 'END-CAP', '密封端盖', 2, 2, 'EC-Ø60', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (241, 'P-W-241', '井下电源模块', 1, 1, '', 'PRODUCT', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (242, 'BAT-PACK', '锂电池组', 2, 2, 'BP-36V', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (243, 'PM-BOARD', '电源管理板', 2, 2, 'PM-V3', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (244, 'INSUL-SLEEVE', '绝缘护套', 2, 2, 'IS-10', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (245, 'TERM-BLOCK', '接线端子', 2, 2, 'TB-4P', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (246, 'P-W-246', '旋转导向短节', 1, 1, '', 'PRODUCT', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (247, 'GUIDE-SHAFT', '导向轴', 2, 2, 'GS-Ø45', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (248, 'BEARING-KIT', '轴承组件', 2, 2, 'BK-6205', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (249, 'HYD-CYL', '液压油缸', 2, 2, 'HC-25', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (250, 'P-W-250', '伽马探管', 1, 1, '', 'PRODUCT', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (251, 'SCINT-CRYSTAL', '闪烁晶体', 2, 2, 'SC-NaI', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (252, 'PMT-TUBE', '光电倍增管', 2, 2, 'PMT-R928', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (253, 'P-W-253', '随钻测斜仪', 1, 1, '', 'PRODUCT', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (254, 'ACCEL', '加速度计', 2, 2, 'ACC-3A', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (255, 'MAGNETO', '磁力计', 2, 2, 'MAG-3M', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (256, 'SIG-BOARD', '信号处理板', 2, 2, 'SB-V2', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (257, 'P-W-257', '井口防喷器密封件', 1, 1, '', 'PRODUCT', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (258, 'RUBBER-RAM', '橡胶闸板', 2, 2, 'RR-7-1/16', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (259, 'METAL-FRAME', '金属骨架', 2, 2, 'MF-7', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (260, 'SCREW-M10', '紧固螺钉', 2, 2, 'M10×40', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (261, 'P-W-261', '泥浆密度传感器', 1, 1, '', 'PRODUCT', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (262, 'DENS-PROBE', '密度探头', 2, 2, 'DP-200', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (263, 'TEMP-COMP', '温度补偿片', 2, 2, 'TC-PT100', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (264, 'SIG-CABLE', '信号线缆', 2, 2, 'SC-5M', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (265, 'P-W-265', '井下马达定子', 1, 1, '', 'PRODUCT', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (266, 'STATOR-LINER', '定子橡胶衬套', 2, 2, 'SL-6-3/4', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (267, 'STEEL-HOUSING', '钢体外壳', 2, 2, 'SH-6-3/4', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (268, 'P-W-268', '振动筛筛网组件', 1, 1, '', 'PRODUCT', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (269, 'MAIN-SCREEN', '主筛网', 2, 2, 'MS-200', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (270, 'FRAME-BAR', '边框压条', 2, 2, 'FB-AL', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (271, 'CLIP-FIX', '固定卡扣', 2, 2, 'CF-12', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (272, 'P-W-272', '液位变送器', 1, 1, '', 'PRODUCT', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (273, 'LT-BODY', '变送器本体', 2, 2, 'LT-4-20', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (274, 'FLANGE', '法兰接头', 2, 2, 'FL-DN50', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (275, 'P-W-275', '电磁流量计', 1, 1, '', 'PRODUCT', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (276, 'FLOW-BODY', '流量计表体', 2, 2, 'FB-DN80', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (277, 'ELECTRODE', '电极组件', 2, 2, 'EL-SS', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (278, 'GROUND-RING', '接地环', 2, 2, 'GR-DN80', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (279, 'P-W-279', '压力变送器总成', 1, 1, '', 'PRODUCT', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (280, 'PRESS-CORE-2', '压力芯体', 2, 2, 'PC-350', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (281, 'DISP-HEAD', '显示表头', 2, 2, 'DH-LCD', 'MATERIAL', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);
-- 批次 + 库存
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (201, 202, 'B20260705011', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 201, 1, wl.location_id, 202, 201, 120.000, 120.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'A-03-02' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (202, 203, 'B20260706004', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 202, 1, wl.location_id, 203, 202, 36.000, 36.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'A-03-08' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (203, 204, 'B20260707002', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 203, 1, wl.location_id, 204, 203, 45.000, 45.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'B-01-03' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (204, 206, 'B20260706008', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 204, 1, wl.location_id, 206, 204, 28.000, 28.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'C-02-01' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (205, 207, 'B20260706009', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 205, 1, wl.location_id, 207, 205, 60.000, 60.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'C-02-05' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (206, 209, 'B20260707001', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 206, 1, wl.location_id, 209, 206, 16.000, 16.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'D-01-02' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (207, 210, 'B20260707003', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 207, 1, wl.location_id, 210, 207, 20.000, 20.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'D-01-04' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (208, 212, 'B20260705020', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 208, 1, wl.location_id, 212, 208, 30.000, 30.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'E-02-01' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (209, 213, 'B20260705021', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 209, 1, wl.location_id, 213, 209, 22.000, 22.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'E-02-03' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (210, 215, 'B20260704015', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 210, 1, wl.location_id, 215, 210, 12.000, 12.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'F-01-01' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (211, 217, 'B20260705030', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 211, 1, wl.location_id, 217, 211, 10.000, 10.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'G-03-02' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (212, 218, 'B20260705031', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 212, 1, wl.location_id, 218, 212, 48.000, 48.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'G-03-04' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (213, 220, 'B20260704010', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 213, 1, wl.location_id, 220, 213, 48.000, 48.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'H-01-06' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (214, 222, 'B20260707010', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 214, 1, wl.location_id, 222, 214, 18.000, 18.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'A-04-01' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (215, 223, 'B20260707011', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 215, 1, wl.location_id, 223, 215, 24.000, 24.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'A-04-02' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (216, 224, 'B20260707012', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 216, 1, wl.location_id, 224, 216, 200.000, 200.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'A-04-03' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (217, 225, 'B20260707013', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 217, 1, wl.location_id, 225, 217, 80.000, 80.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'A-04-04' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (218, 226, 'B20260707014', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 218, 1, wl.location_id, 226, 218, 160.000, 160.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'A-04-05' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (219, 227, 'B20260707015', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 219, 1, wl.location_id, 227, 219, 30.000, 30.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'A-04-06' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (220, 228, 'B20260707016', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 220, 1, wl.location_id, 228, 220, 14.000, 14.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'A-04-07' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (221, 229, 'B20260707017', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 221, 1, wl.location_id, 229, 221, 40.000, 40.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'A-04-08' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (222, 231, 'B20260707020', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 222, 1, wl.location_id, 231, 222, 20.000, 20.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'B-02-01' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (223, 232, 'B20260707021', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 223, 1, wl.location_id, 232, 223, 15.000, 15.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'B-02-02' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (224, 234, 'B20260704020', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 224, 1, wl.location_id, 234, 224, 10.000, 10.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'C-03-01' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (225, 236, 'B20260703001', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 225, 1, wl.location_id, 236, 225, 3.000, 3.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'Z-99-01' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (226, 238, 'B20260708001', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 226, 1, wl.location_id, 238, 226, 12.000, 12.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'B-03-01' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (227, 239, 'B20260708002', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 227, 1, wl.location_id, 239, 227, 10.000, 10.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'B-03-02' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (228, 240, 'B20260708003', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 228, 1, wl.location_id, 240, 228, 20.000, 20.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'B-03-03' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (229, 242, 'B20260708010', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 229, 1, wl.location_id, 242, 229, 30.000, 30.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'C-04-01' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (230, 243, 'B20260708011', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 230, 1, wl.location_id, 243, 230, 18.000, 18.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'C-04-02' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (231, 244, 'B20260708012', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 231, 1, wl.location_id, 244, 231, 60.000, 60.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'C-04-03' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (232, 245, 'B20260708013', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 232, 1, wl.location_id, 245, 232, 80.000, 80.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'C-04-04' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (233, 247, 'B20260708020', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 233, 1, wl.location_id, 247, 233, 6.000, 6.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'D-02-01' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (234, 248, 'B20260708021', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 234, 1, wl.location_id, 248, 234, 16.000, 16.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'D-02-02' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (235, 249, 'B20260708022', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 235, 1, wl.location_id, 249, 235, 5.000, 5.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'D-02-03' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (236, 251, 'B20260708030', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 236, 1, wl.location_id, 251, 236, 10.000, 10.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'E-01-01' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (237, 252, 'B20260708031', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 237, 1, wl.location_id, 252, 237, 8.000, 8.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'E-01-02' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (238, 254, 'B20260708040', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 238, 1, wl.location_id, 254, 238, 20.000, 20.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'F-02-01' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (239, 255, 'B20260708041', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 239, 1, wl.location_id, 255, 239, 15.000, 15.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'F-02-02' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (240, 256, 'B20260708042', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 240, 1, wl.location_id, 256, 240, 12.000, 12.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'F-02-03' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (241, 258, 'B20260707040', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 241, 1, wl.location_id, 258, 241, 14.000, 14.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'G-01-01' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (242, 259, 'B20260707041', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 242, 1, wl.location_id, 259, 242, 18.000, 18.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'G-01-02' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (243, 260, 'B20260707042', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 243, 1, wl.location_id, 260, 243, 200.000, 200.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'G-01-03' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (244, 262, 'B20260707050', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 244, 1, wl.location_id, 262, 244, 22.000, 22.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'H-02-01' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (245, 263, 'B20260707051', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 245, 1, wl.location_id, 263, 245, 30.000, 30.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'H-02-02' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (246, 264, 'B20260707052', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 246, 1, wl.location_id, 264, 246, 40.000, 40.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'H-02-03' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (247, 266, 'B20260707060', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 247, 1, wl.location_id, 266, 247, 5.000, 5.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'D-03-01' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (248, 267, 'B20260707061', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 248, 1, wl.location_id, 267, 248, 6.000, 6.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'D-03-02' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (249, 269, 'B20260707070', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 249, 1, wl.location_id, 269, 249, 35.000, 35.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'I-01-01' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (250, 270, 'B20260707071', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 250, 1, wl.location_id, 270, 250, 80.000, 80.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'I-01-02' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (251, 271, 'B20260707072', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 251, 1, wl.location_id, 271, 251, 200.000, 200.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'I-01-03' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (252, 273, 'B20260704030', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 252, 1, wl.location_id, 273, 252, 15.000, 15.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'E-03-01' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (253, 274, 'B20260704031', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 253, 1, wl.location_id, 274, 253, 20.000, 20.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'E-03-02' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (254, 276, 'B20260704040', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 254, 1, wl.location_id, 276, 254, 8.000, 8.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'F-03-01' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (255, 277, 'B20260704041', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 255, 1, wl.location_id, 277, 255, 20.000, 20.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'F-03-02' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (256, 278, 'B20260704042', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 256, 1, wl.location_id, 278, 256, 12.000, 12.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'F-03-03' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (257, 280, 'B20260704050', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 257, 1, wl.location_id, 280, 257, 16.000, 16.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'E-02-05' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (258, 281, 'B20260704051', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT 258, 1, wl.location_id, 281, 258, 12.000, 12.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = 'E-02-06' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);
-- 计划 / 领料 / 出库 / 拣货
INSERT INTO pmc_production_plan (plan_id, plan_no, source_system_id, source_order_id, mes_plan_no, plan_status, planned_start_date, planned_end_date, created_by, created_at) VALUES (101, 'PP-W-101', 1, 1, 'WO20260708009', 'READY', '2026-07-09', '2026-07-09', 1, '2026-07-09 09:30:00') ON DUPLICATE KEY UPDATE mes_plan_no=VALUES(mes_plan_no), planned_start_date=VALUES(planned_start_date);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1001, 101, 202, 40.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1002, 101, 203, 40.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1003, 101, 204, 20.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_order (requisition_id, requisition_no, source_order_id, source_plan_id, requisition_dept, requested_by, requested_at, requisition_status) VALUES (101, 'REQ-W-101', 1, 101, '生产一部', 1, '2026-07-09 09:30:00', 'PENDING_OUTBOUND') ON DUPLICATE KEY UPDATE source_plan_id=VALUES(source_plan_id);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1001, 101, 202, 40.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1002, 101, 203, 40.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1003, 101, 204, 20.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO out_order (outbound_id, outbound_no, requisition_id, outbound_type, warehouse_id, outbound_status, approved_at) VALUES (101, 'OUT-W-101', 101, 'PRODUCTION_ISSUE', 1, 'PICKING', '2026-07-09 09:30:00') ON DUPLICATE KEY UPDATE outbound_status=VALUES(outbound_status);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1001, 101, 1001, 202, 40.000, 40.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1002, 101, 1002, 203, 40.000, 36.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1003, 101, 1003, 204, 20.000, 20.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_picking_task (picking_task_id, picking_task_no, outbound_id, assigned_to, task_status, planned_pick_time, created_at, priority, product_name, plan_qty, unit, handler_name, cancelled, work_order_no, requisition_no) VALUES (101, 'PICK-W-101', 101, 1, 'PAUSED', '2026-07-09 09:30:00', '2026-07-09 09:30:00', '高', '测井探头组件', 20.000, '套', '赵工', 0, 'WO20260708009', 'REQ20260708018') ON DUPLICATE KEY UPDATE task_status=VALUES(task_status), assigned_to=1, product_name=VALUES(product_name), plan_qty=VALUES(plan_qty), priority=VALUES(priority), handler_name=VALUES(handler_name), cancelled=VALUES(cancelled), work_order_no=VALUES(work_order_no), requisition_no=VALUES(requisition_no);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1001, 101, 1001, 201, 202, 201, wl.location_id, 40.000, 40.000, 40.000
FROM wh_location wl WHERE wl.location_code = 'A-03-02' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1002, 101, 1002, 202, 203, 202, wl.location_id, 40.000, 36.000, 36.000
FROM wh_location wl WHERE wl.location_code = 'A-03-08' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1003, 101, 1003, 203, 204, 203, wl.location_id, 20.000, 20.000, 20.000
FROM wh_location wl WHERE wl.location_code = 'B-01-03' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO pmc_production_plan (plan_id, plan_no, source_system_id, source_order_id, mes_plan_no, plan_status, planned_start_date, planned_end_date, created_by, created_at) VALUES (102, 'PP-W-102', 1, 1, 'WO20260708012', 'READY', '2026-07-09', '2026-07-09', 1, '2026-07-09 10:00:00') ON DUPLICATE KEY UPDATE mes_plan_no=VALUES(mes_plan_no), planned_start_date=VALUES(planned_start_date);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1004, 102, 206, 15.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1005, 102, 207, 30.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_order (requisition_id, requisition_no, source_order_id, source_plan_id, requisition_dept, requested_by, requested_at, requisition_status) VALUES (102, 'REQ-W-102', 1, 102, '生产一部', 1, '2026-07-09 10:00:00', 'PENDING_OUTBOUND') ON DUPLICATE KEY UPDATE source_plan_id=VALUES(source_plan_id);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1004, 102, 206, 15.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1005, 102, 207, 30.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO out_order (outbound_id, outbound_no, requisition_id, outbound_type, warehouse_id, outbound_status, approved_at) VALUES (102, 'OUT-W-102', 102, 'PRODUCTION_ISSUE', 1, 'PICKING', '2026-07-09 10:00:00') ON DUPLICATE KEY UPDATE outbound_status=VALUES(outbound_status);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1004, 102, 1004, 206, 15.000, 0.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1005, 102, 1005, 207, 30.000, 0.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_picking_task (picking_task_id, picking_task_no, outbound_id, assigned_to, task_status, planned_pick_time, created_at, priority, product_name, plan_qty, unit, handler_name, cancelled, work_order_no, requisition_no) VALUES (102, 'PICK-W-102', 102, 1, 'ASSIGNED', '2026-07-09 10:00:00', '2026-07-09 10:00:00', '中', '井下通信模块', 15.000, '套', '赵工', 0, 'WO20260708012', 'REQ20260708021') ON DUPLICATE KEY UPDATE task_status=VALUES(task_status), assigned_to=1, product_name=VALUES(product_name), plan_qty=VALUES(plan_qty), priority=VALUES(priority), handler_name=VALUES(handler_name), cancelled=VALUES(cancelled), work_order_no=VALUES(work_order_no), requisition_no=VALUES(requisition_no);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1004, 102, 1004, 204, 206, 204, wl.location_id, 15.000, 0.000, 0.000
FROM wh_location wl WHERE wl.location_code = 'C-02-01' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1005, 102, 1005, 205, 207, 205, wl.location_id, 30.000, 0.000, 0.000
FROM wh_location wl WHERE wl.location_code = 'C-02-05' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO pmc_production_plan (plan_id, plan_no, source_system_id, source_order_id, mes_plan_no, plan_status, planned_start_date, planned_end_date, created_by, created_at) VALUES (103, 'PP-W-103', 1, 1, 'WO20260708007', 'READY', '2026-07-09', '2026-07-09', 1, '2026-07-09 08:15:00') ON DUPLICATE KEY UPDATE mes_plan_no=VALUES(mes_plan_no), planned_start_date=VALUES(planned_start_date);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1006, 103, 209, 8.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1007, 103, 210, 8.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_order (requisition_id, requisition_no, source_order_id, source_plan_id, requisition_dept, requested_by, requested_at, requisition_status) VALUES (103, 'REQ-W-103', 1, 103, '生产一部', 1, '2026-07-09 08:15:00', 'PENDING_OUTBOUND') ON DUPLICATE KEY UPDATE source_plan_id=VALUES(source_plan_id);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1006, 103, 209, 8.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1007, 103, 210, 8.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO out_order (outbound_id, outbound_no, requisition_id, outbound_type, warehouse_id, outbound_status, approved_at) VALUES (103, 'OUT-W-103', 103, 'PRODUCTION_ISSUE', 1, 'COMPLETED', '2026-07-09 08:15:00') ON DUPLICATE KEY UPDATE outbound_status=VALUES(outbound_status);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1006, 103, 1006, 209, 8.000, 8.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1007, 103, 1007, 210, 8.000, 8.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_picking_task (picking_task_id, picking_task_no, outbound_id, assigned_to, task_status, planned_pick_time, created_at, priority, product_name, plan_qty, unit, handler_name, cancelled, work_order_no, requisition_no) VALUES (103, 'PICK-W-103', 103, 1, 'COMPLETED', '2026-07-09 08:15:00', '2026-07-09 08:15:00', '低', '定向钻具组件', 8.000, '套', '赵工', 0, 'WO20260708007', 'REQ20260708012') ON DUPLICATE KEY UPDATE task_status=VALUES(task_status), assigned_to=1, product_name=VALUES(product_name), plan_qty=VALUES(plan_qty), priority=VALUES(priority), handler_name=VALUES(handler_name), cancelled=VALUES(cancelled), work_order_no=VALUES(work_order_no), requisition_no=VALUES(requisition_no);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1006, 103, 1006, 206, 209, 206, wl.location_id, 8.000, 8.000, 8.000
FROM wh_location wl WHERE wl.location_code = 'D-01-02' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1007, 103, 1007, 207, 210, 207, wl.location_id, 8.000, 8.000, 8.000
FROM wh_location wl WHERE wl.location_code = 'D-01-04' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO worker_handover (picking_task_id, worker_id, work_order_no, requisition_no, warehouse_handler, handover_time, remark) VALUES (103, 1, 'WO20260708007', 'REQ20260708012', '赵工', '2026-07-09 10:25:00', '演示交接') ON DUPLICATE KEY UPDATE handover_time=VALUES(handover_time);
INSERT INTO pmc_production_plan (plan_id, plan_no, source_system_id, source_order_id, mes_plan_no, plan_status, planned_start_date, planned_end_date, created_by, created_at) VALUES (104, 'PP-W-104', 1, 1, 'WO20260708005', 'READY', '2026-07-09', '2026-07-09', 1, '2026-07-09 08:30:00') ON DUPLICATE KEY UPDATE mes_plan_no=VALUES(mes_plan_no), planned_start_date=VALUES(planned_start_date);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1008, 104, 212, 10.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1009, 104, 213, 10.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_order (requisition_id, requisition_no, source_order_id, source_plan_id, requisition_dept, requested_by, requested_at, requisition_status) VALUES (104, 'REQ-W-104', 1, 104, '生产一部', 1, '2026-07-09 08:30:00', 'PENDING_OUTBOUND') ON DUPLICATE KEY UPDATE source_plan_id=VALUES(source_plan_id);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1008, 104, 212, 10.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1009, 104, 213, 10.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO out_order (outbound_id, outbound_no, requisition_id, outbound_type, warehouse_id, outbound_status, approved_at) VALUES (104, 'OUT-W-104', 104, 'PRODUCTION_ISSUE', 1, 'COMPLETED', '2026-07-09 08:30:00') ON DUPLICATE KEY UPDATE outbound_status=VALUES(outbound_status);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1008, 104, 1008, 212, 10.000, 10.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1009, 104, 1009, 213, 10.000, 10.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_picking_task (picking_task_id, picking_task_no, outbound_id, assigned_to, task_status, planned_pick_time, created_at, priority, product_name, plan_qty, unit, handler_name, cancelled, work_order_no, requisition_no) VALUES (104, 'PICK-W-104', 104, 1, 'COMPLETED', '2026-07-09 08:30:00', '2026-07-09 08:30:00', '中', '压力传感器总成', 10.000, '套', '赵工', 0, 'WO20260708005', 'REQ20260708014') ON DUPLICATE KEY UPDATE task_status=VALUES(task_status), assigned_to=1, product_name=VALUES(product_name), plan_qty=VALUES(plan_qty), priority=VALUES(priority), handler_name=VALUES(handler_name), cancelled=VALUES(cancelled), work_order_no=VALUES(work_order_no), requisition_no=VALUES(requisition_no);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1008, 104, 1008, 208, 212, 208, wl.location_id, 10.000, 10.000, 10.000
FROM wh_location wl WHERE wl.location_code = 'E-02-01' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1009, 104, 1009, 209, 213, 209, wl.location_id, 10.000, 10.000, 10.000
FROM wh_location wl WHERE wl.location_code = 'E-02-03' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO worker_handover (picking_task_id, worker_id, work_order_no, requisition_no, warehouse_handler, handover_time, remark) VALUES (104, 1, 'WO20260708005', 'REQ20260708014', '赵工', '2026-07-09 09:05:00', '演示交接') ON DUPLICATE KEY UPDATE handover_time=VALUES(handover_time);
INSERT INTO pmc_production_plan (plan_id, plan_no, source_system_id, source_order_id, mes_plan_no, plan_status, planned_start_date, planned_end_date, created_by, created_at) VALUES (105, 'PP-W-105', 1, 1, 'WO20260708003', 'READY', '2026-07-09', '2026-07-09', 1, '2026-07-09 08:00:00') ON DUPLICATE KEY UPDATE mes_plan_no=VALUES(mes_plan_no), planned_start_date=VALUES(planned_start_date);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1010, 105, 215, 8.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_order (requisition_id, requisition_no, source_order_id, source_plan_id, requisition_dept, requested_by, requested_at, requisition_status) VALUES (105, 'REQ-W-105', 1, 105, '生产一部', 1, '2026-07-09 08:00:00', 'PENDING_OUTBOUND') ON DUPLICATE KEY UPDATE source_plan_id=VALUES(source_plan_id);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1010, 105, 215, 8.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO out_order (outbound_id, outbound_no, requisition_id, outbound_type, warehouse_id, outbound_status, approved_at) VALUES (105, 'OUT-W-105', 105, 'PRODUCTION_ISSUE', 1, 'COMPLETED', '2026-07-09 08:00:00') ON DUPLICATE KEY UPDATE outbound_status=VALUES(outbound_status);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1010, 105, 1010, 215, 8.000, 8.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_picking_task (picking_task_id, picking_task_no, outbound_id, assigned_to, task_status, planned_pick_time, created_at, priority, product_name, plan_qty, unit, handler_name, cancelled, work_order_no, requisition_no) VALUES (105, 'PICK-W-105', 105, 1, 'COMPLETED', '2026-07-09 08:00:00', '2026-07-09 08:00:00', '低', '数据采集卡', 8.000, '套', '李仓', 0, 'WO20260708003', 'REQ20260708011') ON DUPLICATE KEY UPDATE task_status=VALUES(task_status), assigned_to=1, product_name=VALUES(product_name), plan_qty=VALUES(plan_qty), priority=VALUES(priority), handler_name=VALUES(handler_name), cancelled=VALUES(cancelled), work_order_no=VALUES(work_order_no), requisition_no=VALUES(requisition_no);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1010, 105, 1010, 210, 215, 210, wl.location_id, 8.000, 8.000, 8.000
FROM wh_location wl WHERE wl.location_code = 'F-01-01' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO worker_handover (picking_task_id, worker_id, work_order_no, requisition_no, warehouse_handler, handover_time, remark) VALUES (105, 1, 'WO20260708003', 'REQ20260708011', '李仓', '2026-07-09 08:42:00', '演示交接') ON DUPLICATE KEY UPDATE handover_time=VALUES(handover_time);
INSERT INTO pmc_production_plan (plan_id, plan_no, source_system_id, source_order_id, mes_plan_no, plan_status, planned_start_date, planned_end_date, created_by, created_at) VALUES (106, 'PP-W-106', 1, 1, 'WO20260708006', 'READY', '2026-07-08', '2026-07-08', 1, '2026-07-08 16:30:00') ON DUPLICATE KEY UPDATE mes_plan_no=VALUES(mes_plan_no), planned_start_date=VALUES(planned_start_date);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1011, 106, 217, 12.000, '2026-07-08', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1012, 106, 218, 24.000, '2026-07-08', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_order (requisition_id, requisition_no, source_order_id, source_plan_id, requisition_dept, requested_by, requested_at, requisition_status) VALUES (106, 'REQ-W-106', 1, 106, '生产一部', 1, '2026-07-08 16:30:00', 'PENDING_OUTBOUND') ON DUPLICATE KEY UPDATE source_plan_id=VALUES(source_plan_id);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1011, 106, 217, 12.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1012, 106, 218, 24.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO out_order (outbound_id, outbound_no, requisition_id, outbound_type, warehouse_id, outbound_status, approved_at) VALUES (106, 'OUT-W-106', 106, 'PRODUCTION_ISSUE', 1, 'PICKING', '2026-07-08 16:30:00') ON DUPLICATE KEY UPDATE outbound_status=VALUES(outbound_status);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1011, 106, 1011, 217, 12.000, 10.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1012, 106, 1012, 218, 24.000, 24.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_picking_task (picking_task_id, picking_task_no, outbound_id, assigned_to, task_status, planned_pick_time, created_at, priority, product_name, plan_qty, unit, handler_name, cancelled, work_order_no, requisition_no) VALUES (106, 'PICK-W-106', 106, 1, 'PAUSED', '2026-07-08 16:30:00', '2026-07-08 16:30:00', '高', '液压控制阀组', 12.000, '套', '李仓', 0, 'WO20260708006', 'REQ20260708015') ON DUPLICATE KEY UPDATE task_status=VALUES(task_status), assigned_to=1, product_name=VALUES(product_name), plan_qty=VALUES(plan_qty), priority=VALUES(priority), handler_name=VALUES(handler_name), cancelled=VALUES(cancelled), work_order_no=VALUES(work_order_no), requisition_no=VALUES(requisition_no);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1011, 106, 1011, 211, 217, 211, wl.location_id, 12.000, 10.000, 10.000
FROM wh_location wl WHERE wl.location_code = 'G-03-02' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1012, 106, 1012, 212, 218, 212, wl.location_id, 24.000, 24.000, 24.000
FROM wh_location wl WHERE wl.location_code = 'G-03-04' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO pmc_production_plan (plan_id, plan_no, source_system_id, source_order_id, mes_plan_no, plan_status, planned_start_date, planned_end_date, created_by, created_at) VALUES (107, 'PP-W-107', 1, 1, 'WO20260708001', 'READY', '2026-07-08', '2026-07-08', 1, '2026-07-08 07:45:00') ON DUPLICATE KEY UPDATE mes_plan_no=VALUES(mes_plan_no), planned_start_date=VALUES(planned_start_date);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1013, 107, 220, 50.000, '2026-07-08', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_order (requisition_id, requisition_no, source_order_id, source_plan_id, requisition_dept, requested_by, requested_at, requisition_status) VALUES (107, 'REQ-W-107', 1, 107, '生产一部', 1, '2026-07-08 07:45:00', 'PENDING_OUTBOUND') ON DUPLICATE KEY UPDATE source_plan_id=VALUES(source_plan_id);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1013, 107, 220, 50.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO out_order (outbound_id, outbound_no, requisition_id, outbound_type, warehouse_id, outbound_status, approved_at) VALUES (107, 'OUT-W-107', 107, 'PRODUCTION_ISSUE', 1, 'PICKING', '2026-07-08 07:45:00') ON DUPLICATE KEY UPDATE outbound_status=VALUES(outbound_status);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1013, 107, 1013, 220, 50.000, 48.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_picking_task (picking_task_id, picking_task_no, outbound_id, assigned_to, task_status, planned_pick_time, created_at, priority, product_name, plan_qty, unit, handler_name, cancelled, work_order_no, requisition_no) VALUES (107, 'PICK-W-107', 107, 1, 'PAUSED', '2026-07-08 07:45:00', '2026-07-08 07:45:00', '中', '电缆接头组件', 25.000, '套', '李仓', 0, 'WO20260708001', 'REQ20260708008') ON DUPLICATE KEY UPDATE task_status=VALUES(task_status), assigned_to=1, product_name=VALUES(product_name), plan_qty=VALUES(plan_qty), priority=VALUES(priority), handler_name=VALUES(handler_name), cancelled=VALUES(cancelled), work_order_no=VALUES(work_order_no), requisition_no=VALUES(requisition_no);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1013, 107, 1013, 213, 220, 213, wl.location_id, 50.000, 48.000, 48.000
FROM wh_location wl WHERE wl.location_code = 'H-01-06' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO pmc_production_plan (plan_id, plan_no, source_system_id, source_order_id, mes_plan_no, plan_status, planned_start_date, planned_end_date, created_by, created_at) VALUES (108, 'PP-W-108', 1, 1, 'WO20260708010', 'READY', '2026-07-09', '2026-07-09', 1, '2026-07-09 10:30:00') ON DUPLICATE KEY UPDATE mes_plan_no=VALUES(mes_plan_no), planned_start_date=VALUES(planned_start_date);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1014, 108, 222, 6.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1015, 108, 223, 6.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1016, 108, 224, 24.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1017, 108, 225, 12.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1018, 108, 226, 12.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1019, 108, 227, 6.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1020, 108, 228, 6.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1021, 108, 229, 6.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_order (requisition_id, requisition_no, source_order_id, source_plan_id, requisition_dept, requested_by, requested_at, requisition_status) VALUES (108, 'REQ-W-108', 1, 108, '生产一部', 1, '2026-07-09 10:30:00', 'PENDING_OUTBOUND') ON DUPLICATE KEY UPDATE source_plan_id=VALUES(source_plan_id);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1014, 108, 222, 6.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1015, 108, 223, 6.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1016, 108, 224, 24.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1017, 108, 225, 12.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1018, 108, 226, 12.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1019, 108, 227, 6.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1020, 108, 228, 6.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1021, 108, 229, 6.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO out_order (outbound_id, outbound_no, requisition_id, outbound_type, warehouse_id, outbound_status, approved_at) VALUES (108, 'OUT-W-108', 108, 'PRODUCTION_ISSUE', 1, 'PICKING', '2026-07-09 10:30:00') ON DUPLICATE KEY UPDATE outbound_status=VALUES(outbound_status);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1014, 108, 1014, 222, 6.000, 0.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1015, 108, 1015, 223, 6.000, 0.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1016, 108, 1016, 224, 24.000, 0.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1017, 108, 1017, 225, 12.000, 0.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1018, 108, 1018, 226, 12.000, 0.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1019, 108, 1019, 227, 6.000, 0.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1020, 108, 1020, 228, 6.000, 0.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1021, 108, 1021, 229, 6.000, 0.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_picking_task (picking_task_id, picking_task_no, outbound_id, assigned_to, task_status, planned_pick_time, created_at, priority, product_name, plan_qty, unit, handler_name, cancelled, work_order_no, requisition_no) VALUES (108, 'PICK-W-108', 108, 1, 'ASSIGNED', '2026-07-09 10:30:00', '2026-07-09 10:30:00', '高', '高温密封组件', 6.000, '套', '赵工', 0, 'WO20260708010', 'REQ20260708023') ON DUPLICATE KEY UPDATE task_status=VALUES(task_status), assigned_to=1, product_name=VALUES(product_name), plan_qty=VALUES(plan_qty), priority=VALUES(priority), handler_name=VALUES(handler_name), cancelled=VALUES(cancelled), work_order_no=VALUES(work_order_no), requisition_no=VALUES(requisition_no);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1014, 108, 1014, 214, 222, 214, wl.location_id, 6.000, 0.000, 0.000
FROM wh_location wl WHERE wl.location_code = 'A-04-01' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1015, 108, 1015, 215, 223, 215, wl.location_id, 6.000, 0.000, 0.000
FROM wh_location wl WHERE wl.location_code = 'A-04-02' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1016, 108, 1016, 216, 224, 216, wl.location_id, 24.000, 0.000, 0.000
FROM wh_location wl WHERE wl.location_code = 'A-04-03' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1017, 108, 1017, 217, 225, 217, wl.location_id, 12.000, 0.000, 0.000
FROM wh_location wl WHERE wl.location_code = 'A-04-04' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1018, 108, 1018, 218, 226, 218, wl.location_id, 12.000, 0.000, 0.000
FROM wh_location wl WHERE wl.location_code = 'A-04-05' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1019, 108, 1019, 219, 227, 219, wl.location_id, 6.000, 0.000, 0.000
FROM wh_location wl WHERE wl.location_code = 'A-04-06' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1020, 108, 1020, 220, 228, 220, wl.location_id, 6.000, 0.000, 0.000
FROM wh_location wl WHERE wl.location_code = 'A-04-07' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1021, 108, 1021, 221, 229, 221, wl.location_id, 6.000, 0.000, 0.000
FROM wh_location wl WHERE wl.location_code = 'A-04-08' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO pmc_production_plan (plan_id, plan_no, source_system_id, source_order_id, mes_plan_no, plan_status, planned_start_date, planned_end_date, created_by, created_at) VALUES (109, 'PP-W-109', 1, 1, 'WO20260708011', 'READY', '2026-07-09', '2026-07-09', 1, '2026-07-09 11:00:00') ON DUPLICATE KEY UPDATE mes_plan_no=VALUES(mes_plan_no), planned_start_date=VALUES(planned_start_date);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1022, 109, 231, 10.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1023, 109, 232, 10.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_order (requisition_id, requisition_no, source_order_id, source_plan_id, requisition_dept, requested_by, requested_at, requisition_status) VALUES (109, 'REQ-W-109', 1, 109, '生产一部', 1, '2026-07-09 11:00:00', 'PENDING_OUTBOUND') ON DUPLICATE KEY UPDATE source_plan_id=VALUES(source_plan_id);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1022, 109, 231, 10.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1023, 109, 232, 10.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO out_order (outbound_id, outbound_no, requisition_id, outbound_type, warehouse_id, outbound_status, approved_at) VALUES (109, 'OUT-W-109', 109, 'PRODUCTION_ISSUE', 1, 'PICKING', '2026-07-09 11:00:00') ON DUPLICATE KEY UPDATE outbound_status=VALUES(outbound_status);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1022, 109, 1022, 231, 10.000, 10.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1023, 109, 1023, 232, 10.000, 10.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_picking_task (picking_task_id, picking_task_no, outbound_id, assigned_to, task_status, planned_pick_time, created_at, priority, product_name, plan_qty, unit, handler_name, cancelled, work_order_no, requisition_no) VALUES (109, 'PICK-W-109', 109, 1, 'IN_PROGRESS', '2026-07-09 11:00:00', '2026-07-09 11:00:00', '中', '传感器壳体', 10.000, '套', '赵工', 0, 'WO20260708011', 'REQ20260708022') ON DUPLICATE KEY UPDATE task_status=VALUES(task_status), assigned_to=1, product_name=VALUES(product_name), plan_qty=VALUES(plan_qty), priority=VALUES(priority), handler_name=VALUES(handler_name), cancelled=VALUES(cancelled), work_order_no=VALUES(work_order_no), requisition_no=VALUES(requisition_no);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1022, 109, 1022, 222, 231, 222, wl.location_id, 10.000, 10.000, 10.000
FROM wh_location wl WHERE wl.location_code = 'B-02-01' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1023, 109, 1023, 223, 232, 223, wl.location_id, 10.000, 10.000, 10.000
FROM wh_location wl WHERE wl.location_code = 'B-02-02' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO pmc_production_plan (plan_id, plan_no, source_system_id, source_order_id, mes_plan_no, plan_status, planned_start_date, planned_end_date, created_by, created_at) VALUES (110, 'PP-W-110', 1, 1, 'WO20260708004', 'READY', '2026-07-09', '2026-07-09', 1, '2026-07-09 07:30:00') ON DUPLICATE KEY UPDATE mes_plan_no=VALUES(mes_plan_no), planned_start_date=VALUES(planned_start_date);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1024, 110, 234, 5.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_order (requisition_id, requisition_no, source_order_id, source_plan_id, requisition_dept, requested_by, requested_at, requisition_status) VALUES (110, 'REQ-W-110', 1, 110, '生产一部', 1, '2026-07-09 07:30:00', 'PENDING_OUTBOUND') ON DUPLICATE KEY UPDATE source_plan_id=VALUES(source_plan_id);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1024, 110, 234, 5.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO out_order (outbound_id, outbound_no, requisition_id, outbound_type, warehouse_id, outbound_status, approved_at) VALUES (110, 'OUT-W-110', 110, 'PRODUCTION_ISSUE', 1, 'COMPLETED', '2026-07-09 07:30:00') ON DUPLICATE KEY UPDATE outbound_status=VALUES(outbound_status);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1024, 110, 1024, 234, 5.000, 5.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_picking_task (picking_task_id, picking_task_no, outbound_id, assigned_to, task_status, planned_pick_time, created_at, priority, product_name, plan_qty, unit, handler_name, cancelled, work_order_no, requisition_no) VALUES (110, 'PICK-W-110', 110, 1, 'COMPLETED', '2026-07-09 07:30:00', '2026-07-09 07:30:00', '低', '信号调理模块', 5.000, '套', '李仓', 0, 'WO20260708004', 'REQ20260708010') ON DUPLICATE KEY UPDATE task_status=VALUES(task_status), assigned_to=1, product_name=VALUES(product_name), plan_qty=VALUES(plan_qty), priority=VALUES(priority), handler_name=VALUES(handler_name), cancelled=VALUES(cancelled), work_order_no=VALUES(work_order_no), requisition_no=VALUES(requisition_no);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1024, 110, 1024, 224, 234, 224, wl.location_id, 5.000, 5.000, 5.000
FROM wh_location wl WHERE wl.location_code = 'C-03-01' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO worker_handover (picking_task_id, worker_id, work_order_no, requisition_no, warehouse_handler, handover_time, remark) VALUES (110, 1, 'WO20260708004', 'REQ20260708010', '李仓', '2026-07-09 11:10:00', '演示交接') ON DUPLICATE KEY UPDATE handover_time=VALUES(handover_time);
INSERT INTO pmc_production_plan (plan_id, plan_no, source_system_id, source_order_id, mes_plan_no, plan_status, planned_start_date, planned_end_date, created_by, created_at) VALUES (111, 'PP-W-111', 1, 1, 'WO20260707028', 'READY', '2026-07-08', '2026-07-08', 1, '2026-07-08 14:00:00') ON DUPLICATE KEY UPDATE mes_plan_no=VALUES(mes_plan_no), planned_start_date=VALUES(planned_start_date);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1025, 111, 236, 3.000, '2026-07-08', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_order (requisition_id, requisition_no, source_order_id, source_plan_id, requisition_dept, requested_by, requested_at, requisition_status) VALUES (111, 'REQ-W-111', 1, 111, '生产一部', 1, '2026-07-08 14:00:00', 'PENDING_OUTBOUND') ON DUPLICATE KEY UPDATE source_plan_id=VALUES(source_plan_id);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1025, 111, 236, 3.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO out_order (outbound_id, outbound_no, requisition_id, outbound_type, warehouse_id, outbound_status, approved_at) VALUES (111, 'OUT-W-111', 111, 'PRODUCTION_ISSUE', 1, 'PICKING', '2026-07-08 14:00:00') ON DUPLICATE KEY UPDATE outbound_status=VALUES(outbound_status);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1025, 111, 1025, 236, 3.000, 0.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_picking_task (picking_task_id, picking_task_no, outbound_id, assigned_to, task_status, planned_pick_time, created_at, priority, product_name, plan_qty, unit, handler_name, cancelled, work_order_no, requisition_no) VALUES (111, 'PICK-W-111', 111, 1, 'CANCELLED', '2026-07-08 14:00:00', '2026-07-08 14:00:00', '低', '临时试制件', 3.000, '套', '赵工', 1, 'WO20260707028', 'REQ20260707035') ON DUPLICATE KEY UPDATE task_status=VALUES(task_status), assigned_to=1, product_name=VALUES(product_name), plan_qty=VALUES(plan_qty), priority=VALUES(priority), handler_name=VALUES(handler_name), cancelled=VALUES(cancelled), work_order_no=VALUES(work_order_no), requisition_no=VALUES(requisition_no);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1025, 111, 1025, 225, 236, 225, wl.location_id, 3.000, 0.000, 0.000
FROM wh_location wl WHERE wl.location_code = 'Z-99-01' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO pmc_production_plan (plan_id, plan_no, source_system_id, source_order_id, mes_plan_no, plan_status, planned_start_date, planned_end_date, created_by, created_at) VALUES (112, 'PP-W-112', 1, 1, 'WO20260709001', 'READY', '2026-07-09', '2026-07-09', 1, '2026-07-09 11:30:00') ON DUPLICATE KEY UPDATE mes_plan_no=VALUES(mes_plan_no), planned_start_date=VALUES(planned_start_date);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1026, 112, 238, 4.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1027, 112, 239, 4.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1028, 112, 240, 8.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_order (requisition_id, requisition_no, source_order_id, source_plan_id, requisition_dept, requested_by, requested_at, requisition_status) VALUES (112, 'REQ-W-112', 1, 112, '生产一部', 1, '2026-07-09 11:30:00', 'PENDING_OUTBOUND') ON DUPLICATE KEY UPDATE source_plan_id=VALUES(source_plan_id);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1026, 112, 238, 4.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1027, 112, 239, 4.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1028, 112, 240, 8.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO out_order (outbound_id, outbound_no, requisition_id, outbound_type, warehouse_id, outbound_status, approved_at) VALUES (112, 'OUT-W-112', 112, 'PRODUCTION_ISSUE', 1, 'PICKING', '2026-07-09 11:30:00') ON DUPLICATE KEY UPDATE outbound_status=VALUES(outbound_status);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1026, 112, 1026, 238, 4.000, 0.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1027, 112, 1027, 239, 4.000, 0.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1028, 112, 1028, 240, 8.000, 0.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_picking_task (picking_task_id, picking_task_no, outbound_id, assigned_to, task_status, planned_pick_time, created_at, priority, product_name, plan_qty, unit, handler_name, cancelled, work_order_no, requisition_no) VALUES (112, 'PICK-W-112', 112, 1, 'ASSIGNED', '2026-07-09 11:30:00', '2026-07-09 11:30:00', '高', '泥浆脉冲发生器', 4.000, '套', '赵工', 0, 'WO20260709001', 'REQ20260709001') ON DUPLICATE KEY UPDATE task_status=VALUES(task_status), assigned_to=1, product_name=VALUES(product_name), plan_qty=VALUES(plan_qty), priority=VALUES(priority), handler_name=VALUES(handler_name), cancelled=VALUES(cancelled), work_order_no=VALUES(work_order_no), requisition_no=VALUES(requisition_no);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1026, 112, 1026, 226, 238, 226, wl.location_id, 4.000, 0.000, 0.000
FROM wh_location wl WHERE wl.location_code = 'B-03-01' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1027, 112, 1027, 227, 239, 227, wl.location_id, 4.000, 0.000, 0.000
FROM wh_location wl WHERE wl.location_code = 'B-03-02' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1028, 112, 1028, 228, 240, 228, wl.location_id, 8.000, 0.000, 0.000
FROM wh_location wl WHERE wl.location_code = 'B-03-03' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO pmc_production_plan (plan_id, plan_no, source_system_id, source_order_id, mes_plan_no, plan_status, planned_start_date, planned_end_date, created_by, created_at) VALUES (113, 'PP-W-113', 1, 1, 'WO20260709002', 'READY', '2026-07-09', '2026-07-09', 1, '2026-07-09 13:00:00') ON DUPLICATE KEY UPDATE mes_plan_no=VALUES(mes_plan_no), planned_start_date=VALUES(planned_start_date);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1029, 113, 242, 12.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1030, 113, 243, 12.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1031, 113, 244, 24.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1032, 113, 245, 24.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_order (requisition_id, requisition_no, source_order_id, source_plan_id, requisition_dept, requested_by, requested_at, requisition_status) VALUES (113, 'REQ-W-113', 1, 113, '生产一部', 1, '2026-07-09 13:00:00', 'PENDING_OUTBOUND') ON DUPLICATE KEY UPDATE source_plan_id=VALUES(source_plan_id);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1029, 113, 242, 12.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1030, 113, 243, 12.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1031, 113, 244, 24.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1032, 113, 245, 24.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO out_order (outbound_id, outbound_no, requisition_id, outbound_type, warehouse_id, outbound_status, approved_at) VALUES (113, 'OUT-W-113', 113, 'PRODUCTION_ISSUE', 1, 'PICKING', '2026-07-09 13:00:00') ON DUPLICATE KEY UPDATE outbound_status=VALUES(outbound_status);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1029, 113, 1029, 242, 12.000, 0.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1030, 113, 1030, 243, 12.000, 0.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1031, 113, 1031, 244, 24.000, 0.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1032, 113, 1032, 245, 24.000, 0.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_picking_task (picking_task_id, picking_task_no, outbound_id, assigned_to, task_status, planned_pick_time, created_at, priority, product_name, plan_qty, unit, handler_name, cancelled, work_order_no, requisition_no) VALUES (113, 'PICK-W-113', 113, 1, 'ASSIGNED', '2026-07-09 13:00:00', '2026-07-09 13:00:00', '中', '井下电源模块', 12.000, '套', '赵工', 0, 'WO20260709002', 'REQ20260709002') ON DUPLICATE KEY UPDATE task_status=VALUES(task_status), assigned_to=1, product_name=VALUES(product_name), plan_qty=VALUES(plan_qty), priority=VALUES(priority), handler_name=VALUES(handler_name), cancelled=VALUES(cancelled), work_order_no=VALUES(work_order_no), requisition_no=VALUES(requisition_no);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1029, 113, 1029, 229, 242, 229, wl.location_id, 12.000, 0.000, 0.000
FROM wh_location wl WHERE wl.location_code = 'C-04-01' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1030, 113, 1030, 230, 243, 230, wl.location_id, 12.000, 0.000, 0.000
FROM wh_location wl WHERE wl.location_code = 'C-04-02' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1031, 113, 1031, 231, 244, 231, wl.location_id, 24.000, 0.000, 0.000
FROM wh_location wl WHERE wl.location_code = 'C-04-03' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1032, 113, 1032, 232, 245, 232, wl.location_id, 24.000, 0.000, 0.000
FROM wh_location wl WHERE wl.location_code = 'C-04-04' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO pmc_production_plan (plan_id, plan_no, source_system_id, source_order_id, mes_plan_no, plan_status, planned_start_date, planned_end_date, created_by, created_at) VALUES (114, 'PP-W-114', 1, 1, 'WO20260709003', 'READY', '2026-07-09', '2026-07-09', 1, '2026-07-09 14:00:00') ON DUPLICATE KEY UPDATE mes_plan_no=VALUES(mes_plan_no), planned_start_date=VALUES(planned_start_date);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1033, 114, 247, 2.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1034, 114, 248, 4.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1035, 114, 249, 2.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_order (requisition_id, requisition_no, source_order_id, source_plan_id, requisition_dept, requested_by, requested_at, requisition_status) VALUES (114, 'REQ-W-114', 1, 114, '生产一部', 1, '2026-07-09 14:00:00', 'PENDING_OUTBOUND') ON DUPLICATE KEY UPDATE source_plan_id=VALUES(source_plan_id);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1033, 114, 247, 2.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1034, 114, 248, 4.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1035, 114, 249, 2.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO out_order (outbound_id, outbound_no, requisition_id, outbound_type, warehouse_id, outbound_status, approved_at) VALUES (114, 'OUT-W-114', 114, 'PRODUCTION_ISSUE', 1, 'PICKING', '2026-07-09 14:00:00') ON DUPLICATE KEY UPDATE outbound_status=VALUES(outbound_status);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1033, 114, 1033, 247, 2.000, 0.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1034, 114, 1034, 248, 4.000, 0.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1035, 114, 1035, 249, 2.000, 0.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_picking_task (picking_task_id, picking_task_no, outbound_id, assigned_to, task_status, planned_pick_time, created_at, priority, product_name, plan_qty, unit, handler_name, cancelled, work_order_no, requisition_no) VALUES (114, 'PICK-W-114', 114, 1, 'ASSIGNED', '2026-07-09 14:00:00', '2026-07-09 14:00:00', '高', '旋转导向短节', 2.000, '套', '赵工', 0, 'WO20260709003', 'REQ20260709003') ON DUPLICATE KEY UPDATE task_status=VALUES(task_status), assigned_to=1, product_name=VALUES(product_name), plan_qty=VALUES(plan_qty), priority=VALUES(priority), handler_name=VALUES(handler_name), cancelled=VALUES(cancelled), work_order_no=VALUES(work_order_no), requisition_no=VALUES(requisition_no);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1033, 114, 1033, 233, 247, 233, wl.location_id, 2.000, 0.000, 0.000
FROM wh_location wl WHERE wl.location_code = 'D-02-01' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1034, 114, 1034, 234, 248, 234, wl.location_id, 4.000, 0.000, 0.000
FROM wh_location wl WHERE wl.location_code = 'D-02-02' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1035, 114, 1035, 235, 249, 235, wl.location_id, 2.000, 0.000, 0.000
FROM wh_location wl WHERE wl.location_code = 'D-02-03' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO pmc_production_plan (plan_id, plan_no, source_system_id, source_order_id, mes_plan_no, plan_status, planned_start_date, planned_end_date, created_by, created_at) VALUES (115, 'PP-W-115', 1, 1, 'WO20260709004', 'READY', '2026-07-09', '2026-07-09', 1, '2026-07-09 15:00:00') ON DUPLICATE KEY UPDATE mes_plan_no=VALUES(mes_plan_no), planned_start_date=VALUES(planned_start_date);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1036, 115, 251, 6.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1037, 115, 252, 6.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_order (requisition_id, requisition_no, source_order_id, source_plan_id, requisition_dept, requested_by, requested_at, requisition_status) VALUES (115, 'REQ-W-115', 1, 115, '生产一部', 1, '2026-07-09 15:00:00', 'PENDING_OUTBOUND') ON DUPLICATE KEY UPDATE source_plan_id=VALUES(source_plan_id);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1036, 115, 251, 6.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1037, 115, 252, 6.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO out_order (outbound_id, outbound_no, requisition_id, outbound_type, warehouse_id, outbound_status, approved_at) VALUES (115, 'OUT-W-115', 115, 'PRODUCTION_ISSUE', 1, 'PICKING', '2026-07-09 15:00:00') ON DUPLICATE KEY UPDATE outbound_status=VALUES(outbound_status);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1036, 115, 1036, 251, 6.000, 0.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1037, 115, 1037, 252, 6.000, 0.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_picking_task (picking_task_id, picking_task_no, outbound_id, assigned_to, task_status, planned_pick_time, created_at, priority, product_name, plan_qty, unit, handler_name, cancelled, work_order_no, requisition_no) VALUES (115, 'PICK-W-115', 115, 1, 'ASSIGNED', '2026-07-09 15:00:00', '2026-07-09 15:00:00', '中', '伽马探管', 6.000, '套', '赵工', 0, 'WO20260709004', 'REQ20260709004') ON DUPLICATE KEY UPDATE task_status=VALUES(task_status), assigned_to=1, product_name=VALUES(product_name), plan_qty=VALUES(plan_qty), priority=VALUES(priority), handler_name=VALUES(handler_name), cancelled=VALUES(cancelled), work_order_no=VALUES(work_order_no), requisition_no=VALUES(requisition_no);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1036, 115, 1036, 236, 251, 236, wl.location_id, 6.000, 0.000, 0.000
FROM wh_location wl WHERE wl.location_code = 'E-01-01' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1037, 115, 1037, 237, 252, 237, wl.location_id, 6.000, 0.000, 0.000
FROM wh_location wl WHERE wl.location_code = 'E-01-02' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO pmc_production_plan (plan_id, plan_no, source_system_id, source_order_id, mes_plan_no, plan_status, planned_start_date, planned_end_date, created_by, created_at) VALUES (116, 'PP-W-116', 1, 1, 'WO20260709005', 'READY', '2026-07-09', '2026-07-09', 1, '2026-07-09 15:30:00') ON DUPLICATE KEY UPDATE mes_plan_no=VALUES(mes_plan_no), planned_start_date=VALUES(planned_start_date);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1038, 116, 254, 8.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1039, 116, 255, 8.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1040, 116, 256, 8.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_order (requisition_id, requisition_no, source_order_id, source_plan_id, requisition_dept, requested_by, requested_at, requisition_status) VALUES (116, 'REQ-W-116', 1, 116, '生产一部', 1, '2026-07-09 15:30:00', 'PENDING_OUTBOUND') ON DUPLICATE KEY UPDATE source_plan_id=VALUES(source_plan_id);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1038, 116, 254, 8.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1039, 116, 255, 8.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1040, 116, 256, 8.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO out_order (outbound_id, outbound_no, requisition_id, outbound_type, warehouse_id, outbound_status, approved_at) VALUES (116, 'OUT-W-116', 116, 'PRODUCTION_ISSUE', 1, 'PICKING', '2026-07-09 15:30:00') ON DUPLICATE KEY UPDATE outbound_status=VALUES(outbound_status);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1038, 116, 1038, 254, 8.000, 0.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1039, 116, 1039, 255, 8.000, 0.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1040, 116, 1040, 256, 8.000, 0.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_picking_task (picking_task_id, picking_task_no, outbound_id, assigned_to, task_status, planned_pick_time, created_at, priority, product_name, plan_qty, unit, handler_name, cancelled, work_order_no, requisition_no) VALUES (116, 'PICK-W-116', 116, 1, 'ASSIGNED', '2026-07-09 15:30:00', '2026-07-09 15:30:00', '低', '随钻测斜仪', 8.000, '套', '赵工', 0, 'WO20260709005', 'REQ20260709005') ON DUPLICATE KEY UPDATE task_status=VALUES(task_status), assigned_to=1, product_name=VALUES(product_name), plan_qty=VALUES(plan_qty), priority=VALUES(priority), handler_name=VALUES(handler_name), cancelled=VALUES(cancelled), work_order_no=VALUES(work_order_no), requisition_no=VALUES(requisition_no);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1038, 116, 1038, 238, 254, 238, wl.location_id, 8.000, 0.000, 0.000
FROM wh_location wl WHERE wl.location_code = 'F-02-01' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1039, 116, 1039, 239, 255, 239, wl.location_id, 8.000, 0.000, 0.000
FROM wh_location wl WHERE wl.location_code = 'F-02-02' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1040, 116, 1040, 240, 256, 240, wl.location_id, 8.000, 0.000, 0.000
FROM wh_location wl WHERE wl.location_code = 'F-02-03' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO pmc_production_plan (plan_id, plan_no, source_system_id, source_order_id, mes_plan_no, plan_status, planned_start_date, planned_end_date, created_by, created_at) VALUES (117, 'PP-W-117', 1, 1, 'WO20260708015', 'READY', '2026-07-09', '2026-07-09', 1, '2026-07-09 09:00:00') ON DUPLICATE KEY UPDATE mes_plan_no=VALUES(mes_plan_no), planned_start_date=VALUES(planned_start_date);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1041, 117, 258, 10.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1042, 117, 259, 10.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1043, 117, 260, 40.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_order (requisition_id, requisition_no, source_order_id, source_plan_id, requisition_dept, requested_by, requested_at, requisition_status) VALUES (117, 'REQ-W-117', 1, 117, '生产一部', 1, '2026-07-09 09:00:00', 'PENDING_OUTBOUND') ON DUPLICATE KEY UPDATE source_plan_id=VALUES(source_plan_id);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1041, 117, 258, 10.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1042, 117, 259, 10.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1043, 117, 260, 40.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO out_order (outbound_id, outbound_no, requisition_id, outbound_type, warehouse_id, outbound_status, approved_at) VALUES (117, 'OUT-W-117', 117, 'PRODUCTION_ISSUE', 1, 'PICKING', '2026-07-09 09:00:00') ON DUPLICATE KEY UPDATE outbound_status=VALUES(outbound_status);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1041, 117, 1041, 258, 10.000, 6.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1042, 117, 1042, 259, 10.000, 10.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1043, 117, 1043, 260, 40.000, 20.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_picking_task (picking_task_id, picking_task_no, outbound_id, assigned_to, task_status, planned_pick_time, created_at, priority, product_name, plan_qty, unit, handler_name, cancelled, work_order_no, requisition_no) VALUES (117, 'PICK-W-117', 117, 1, 'IN_PROGRESS', '2026-07-09 09:00:00', '2026-07-09 09:00:00', '高', '井口防喷器密封件', 10.000, '套', '赵工', 0, 'WO20260708015', 'REQ20260708028') ON DUPLICATE KEY UPDATE task_status=VALUES(task_status), assigned_to=1, product_name=VALUES(product_name), plan_qty=VALUES(plan_qty), priority=VALUES(priority), handler_name=VALUES(handler_name), cancelled=VALUES(cancelled), work_order_no=VALUES(work_order_no), requisition_no=VALUES(requisition_no);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1041, 117, 1041, 241, 258, 241, wl.location_id, 10.000, 6.000, 6.000
FROM wh_location wl WHERE wl.location_code = 'G-01-01' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1042, 117, 1042, 242, 259, 242, wl.location_id, 10.000, 10.000, 10.000
FROM wh_location wl WHERE wl.location_code = 'G-01-02' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1043, 117, 1043, 243, 260, 243, wl.location_id, 40.000, 20.000, 20.000
FROM wh_location wl WHERE wl.location_code = 'G-01-03' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO pmc_production_plan (plan_id, plan_no, source_system_id, source_order_id, mes_plan_no, plan_status, planned_start_date, planned_end_date, created_by, created_at) VALUES (118, 'PP-W-118', 1, 1, 'WO20260708016', 'READY', '2026-07-09', '2026-07-09', 1, '2026-07-09 10:45:00') ON DUPLICATE KEY UPDATE mes_plan_no=VALUES(mes_plan_no), planned_start_date=VALUES(planned_start_date);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1044, 118, 262, 15.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1045, 118, 263, 15.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1046, 118, 264, 15.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_order (requisition_id, requisition_no, source_order_id, source_plan_id, requisition_dept, requested_by, requested_at, requisition_status) VALUES (118, 'REQ-W-118', 1, 118, '生产一部', 1, '2026-07-09 10:45:00', 'PENDING_OUTBOUND') ON DUPLICATE KEY UPDATE source_plan_id=VALUES(source_plan_id);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1044, 118, 262, 15.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1045, 118, 263, 15.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1046, 118, 264, 15.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO out_order (outbound_id, outbound_no, requisition_id, outbound_type, warehouse_id, outbound_status, approved_at) VALUES (118, 'OUT-W-118', 118, 'PRODUCTION_ISSUE', 1, 'PICKING', '2026-07-09 10:45:00') ON DUPLICATE KEY UPDATE outbound_status=VALUES(outbound_status);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1044, 118, 1044, 262, 15.000, 15.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1045, 118, 1045, 263, 15.000, 8.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1046, 118, 1046, 264, 15.000, 0.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_picking_task (picking_task_id, picking_task_no, outbound_id, assigned_to, task_status, planned_pick_time, created_at, priority, product_name, plan_qty, unit, handler_name, cancelled, work_order_no, requisition_no) VALUES (118, 'PICK-W-118', 118, 1, 'IN_PROGRESS', '2026-07-09 10:45:00', '2026-07-09 10:45:00', '中', '泥浆密度传感器', 15.000, '套', '赵工', 0, 'WO20260708016', 'REQ20260708029') ON DUPLICATE KEY UPDATE task_status=VALUES(task_status), assigned_to=1, product_name=VALUES(product_name), plan_qty=VALUES(plan_qty), priority=VALUES(priority), handler_name=VALUES(handler_name), cancelled=VALUES(cancelled), work_order_no=VALUES(work_order_no), requisition_no=VALUES(requisition_no);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1044, 118, 1044, 244, 262, 244, wl.location_id, 15.000, 15.000, 15.000
FROM wh_location wl WHERE wl.location_code = 'H-02-01' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1045, 118, 1045, 245, 263, 245, wl.location_id, 15.000, 8.000, 8.000
FROM wh_location wl WHERE wl.location_code = 'H-02-02' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1046, 118, 1046, 246, 264, 246, wl.location_id, 15.000, 0.000, 0.000
FROM wh_location wl WHERE wl.location_code = 'H-02-03' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO pmc_production_plan (plan_id, plan_no, source_system_id, source_order_id, mes_plan_no, plan_status, planned_start_date, planned_end_date, created_by, created_at) VALUES (119, 'PP-W-119', 1, 1, 'WO20260708017', 'READY', '2026-07-09', '2026-07-09', 1, '2026-07-09 12:00:00') ON DUPLICATE KEY UPDATE mes_plan_no=VALUES(mes_plan_no), planned_start_date=VALUES(planned_start_date);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1047, 119, 266, 3.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1048, 119, 267, 3.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_order (requisition_id, requisition_no, source_order_id, source_plan_id, requisition_dept, requested_by, requested_at, requisition_status) VALUES (119, 'REQ-W-119', 1, 119, '生产一部', 1, '2026-07-09 12:00:00', 'PENDING_OUTBOUND') ON DUPLICATE KEY UPDATE source_plan_id=VALUES(source_plan_id);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1047, 119, 266, 3.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1048, 119, 267, 3.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO out_order (outbound_id, outbound_no, requisition_id, outbound_type, warehouse_id, outbound_status, approved_at) VALUES (119, 'OUT-W-119', 119, 'PRODUCTION_ISSUE', 1, 'PICKING', '2026-07-09 12:00:00') ON DUPLICATE KEY UPDATE outbound_status=VALUES(outbound_status);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1047, 119, 1047, 266, 3.000, 2.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1048, 119, 1048, 267, 3.000, 3.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_picking_task (picking_task_id, picking_task_no, outbound_id, assigned_to, task_status, planned_pick_time, created_at, priority, product_name, plan_qty, unit, handler_name, cancelled, work_order_no, requisition_no) VALUES (119, 'PICK-W-119', 119, 1, 'IN_PROGRESS', '2026-07-09 12:00:00', '2026-07-09 12:00:00', '中', '井下马达定子', 3.000, '套', '赵工', 0, 'WO20260708017', 'REQ20260708030') ON DUPLICATE KEY UPDATE task_status=VALUES(task_status), assigned_to=1, product_name=VALUES(product_name), plan_qty=VALUES(plan_qty), priority=VALUES(priority), handler_name=VALUES(handler_name), cancelled=VALUES(cancelled), work_order_no=VALUES(work_order_no), requisition_no=VALUES(requisition_no);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1047, 119, 1047, 247, 266, 247, wl.location_id, 3.000, 2.000, 2.000
FROM wh_location wl WHERE wl.location_code = 'D-03-01' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1048, 119, 1048, 248, 267, 248, wl.location_id, 3.000, 3.000, 3.000
FROM wh_location wl WHERE wl.location_code = 'D-03-02' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO pmc_production_plan (plan_id, plan_no, source_system_id, source_order_id, mes_plan_no, plan_status, planned_start_date, planned_end_date, created_by, created_at) VALUES (120, 'PP-W-120', 1, 1, 'WO20260708018', 'READY', '2026-07-09', '2026-07-09', 1, '2026-07-09 13:30:00') ON DUPLICATE KEY UPDATE mes_plan_no=VALUES(mes_plan_no), planned_start_date=VALUES(planned_start_date);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1049, 120, 269, 20.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1050, 120, 270, 40.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1051, 120, 271, 80.000, '2026-07-09', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_order (requisition_id, requisition_no, source_order_id, source_plan_id, requisition_dept, requested_by, requested_at, requisition_status) VALUES (120, 'REQ-W-120', 1, 120, '生产一部', 1, '2026-07-09 13:30:00', 'PENDING_OUTBOUND') ON DUPLICATE KEY UPDATE source_plan_id=VALUES(source_plan_id);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1049, 120, 269, 20.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1050, 120, 270, 40.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1051, 120, 271, 80.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO out_order (outbound_id, outbound_no, requisition_id, outbound_type, warehouse_id, outbound_status, approved_at) VALUES (120, 'OUT-W-120', 120, 'PRODUCTION_ISSUE', 1, 'PICKING', '2026-07-09 13:30:00') ON DUPLICATE KEY UPDATE outbound_status=VALUES(outbound_status);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1049, 120, 1049, 269, 20.000, 12.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1050, 120, 1050, 270, 40.000, 40.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1051, 120, 1051, 271, 80.000, 40.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_picking_task (picking_task_id, picking_task_no, outbound_id, assigned_to, task_status, planned_pick_time, created_at, priority, product_name, plan_qty, unit, handler_name, cancelled, work_order_no, requisition_no) VALUES (120, 'PICK-W-120', 120, 1, 'IN_PROGRESS', '2026-07-09 13:30:00', '2026-07-09 13:30:00', '低', '振动筛筛网组件', 20.000, '套', '赵工', 0, 'WO20260708018', 'REQ20260708031') ON DUPLICATE KEY UPDATE task_status=VALUES(task_status), assigned_to=1, product_name=VALUES(product_name), plan_qty=VALUES(plan_qty), priority=VALUES(priority), handler_name=VALUES(handler_name), cancelled=VALUES(cancelled), work_order_no=VALUES(work_order_no), requisition_no=VALUES(requisition_no);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1049, 120, 1049, 249, 269, 249, wl.location_id, 20.000, 12.000, 12.000
FROM wh_location wl WHERE wl.location_code = 'I-01-01' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1050, 120, 1050, 250, 270, 250, wl.location_id, 40.000, 40.000, 40.000
FROM wh_location wl WHERE wl.location_code = 'I-01-02' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1051, 120, 1051, 251, 271, 251, wl.location_id, 80.000, 40.000, 40.000
FROM wh_location wl WHERE wl.location_code = 'I-01-03' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO pmc_production_plan (plan_id, plan_no, source_system_id, source_order_id, mes_plan_no, plan_status, planned_start_date, planned_end_date, created_by, created_at) VALUES (121, 'PP-W-121', 1, 1, 'WO20260707025', 'READY', '2026-07-08', '2026-07-08', 1, '2026-07-08 15:00:00') ON DUPLICATE KEY UPDATE mes_plan_no=VALUES(mes_plan_no), planned_start_date=VALUES(planned_start_date);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1052, 121, 273, 10.000, '2026-07-08', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1053, 121, 274, 10.000, '2026-07-08', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_order (requisition_id, requisition_no, source_order_id, source_plan_id, requisition_dept, requested_by, requested_at, requisition_status) VALUES (121, 'REQ-W-121', 1, 121, '生产一部', 1, '2026-07-08 15:00:00', 'PENDING_OUTBOUND') ON DUPLICATE KEY UPDATE source_plan_id=VALUES(source_plan_id);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1052, 121, 273, 10.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1053, 121, 274, 10.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO out_order (outbound_id, outbound_no, requisition_id, outbound_type, warehouse_id, outbound_status, approved_at) VALUES (121, 'OUT-W-121', 121, 'PRODUCTION_ISSUE', 1, 'COMPLETED', '2026-07-08 15:00:00') ON DUPLICATE KEY UPDATE outbound_status=VALUES(outbound_status);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1052, 121, 1052, 273, 10.000, 10.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1053, 121, 1053, 274, 10.000, 10.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_picking_task (picking_task_id, picking_task_no, outbound_id, assigned_to, task_status, planned_pick_time, created_at, priority, product_name, plan_qty, unit, handler_name, cancelled, work_order_no, requisition_no) VALUES (121, 'PICK-W-121', 121, 1, 'COMPLETED', '2026-07-08 15:00:00', '2026-07-08 15:00:00', '中', '液位变送器', 10.000, '套', '李仓', 0, 'WO20260707025', 'REQ20260707030') ON DUPLICATE KEY UPDATE task_status=VALUES(task_status), assigned_to=1, product_name=VALUES(product_name), plan_qty=VALUES(plan_qty), priority=VALUES(priority), handler_name=VALUES(handler_name), cancelled=VALUES(cancelled), work_order_no=VALUES(work_order_no), requisition_no=VALUES(requisition_no);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1052, 121, 1052, 252, 273, 252, wl.location_id, 10.000, 10.000, 10.000
FROM wh_location wl WHERE wl.location_code = 'E-03-01' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1053, 121, 1053, 253, 274, 253, wl.location_id, 10.000, 10.000, 10.000
FROM wh_location wl WHERE wl.location_code = 'E-03-02' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO worker_handover (picking_task_id, worker_id, work_order_no, requisition_no, warehouse_handler, handover_time, remark) VALUES (121, 1, 'WO20260707025', 'REQ20260707030', '李仓', '2026-07-08 16:20:00', '演示交接') ON DUPLICATE KEY UPDATE handover_time=VALUES(handover_time);
INSERT INTO pmc_production_plan (plan_id, plan_no, source_system_id, source_order_id, mes_plan_no, plan_status, planned_start_date, planned_end_date, created_by, created_at) VALUES (122, 'PP-W-122', 1, 1, 'WO20260707026', 'READY', '2026-07-08', '2026-07-08', 1, '2026-07-08 11:00:00') ON DUPLICATE KEY UPDATE mes_plan_no=VALUES(mes_plan_no), planned_start_date=VALUES(planned_start_date);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1054, 122, 276, 5.000, '2026-07-08', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1055, 122, 277, 10.000, '2026-07-08', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1056, 122, 278, 5.000, '2026-07-08', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_order (requisition_id, requisition_no, source_order_id, source_plan_id, requisition_dept, requested_by, requested_at, requisition_status) VALUES (122, 'REQ-W-122', 1, 122, '生产一部', 1, '2026-07-08 11:00:00', 'PENDING_OUTBOUND') ON DUPLICATE KEY UPDATE source_plan_id=VALUES(source_plan_id);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1054, 122, 276, 5.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1055, 122, 277, 10.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1056, 122, 278, 5.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO out_order (outbound_id, outbound_no, requisition_id, outbound_type, warehouse_id, outbound_status, approved_at) VALUES (122, 'OUT-W-122', 122, 'PRODUCTION_ISSUE', 1, 'COMPLETED', '2026-07-08 11:00:00') ON DUPLICATE KEY UPDATE outbound_status=VALUES(outbound_status);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1054, 122, 1054, 276, 5.000, 5.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1055, 122, 1055, 277, 10.000, 10.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1056, 122, 1056, 278, 5.000, 5.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_picking_task (picking_task_id, picking_task_no, outbound_id, assigned_to, task_status, planned_pick_time, created_at, priority, product_name, plan_qty, unit, handler_name, cancelled, work_order_no, requisition_no) VALUES (122, 'PICK-W-122', 122, 1, 'COMPLETED', '2026-07-08 11:00:00', '2026-07-08 11:00:00', '低', '电磁流量计', 5.000, '套', '李仓', 0, 'WO20260707026', 'REQ20260707031') ON DUPLICATE KEY UPDATE task_status=VALUES(task_status), assigned_to=1, product_name=VALUES(product_name), plan_qty=VALUES(plan_qty), priority=VALUES(priority), handler_name=VALUES(handler_name), cancelled=VALUES(cancelled), work_order_no=VALUES(work_order_no), requisition_no=VALUES(requisition_no);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1054, 122, 1054, 254, 276, 254, wl.location_id, 5.000, 5.000, 5.000
FROM wh_location wl WHERE wl.location_code = 'F-03-01' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1055, 122, 1055, 255, 277, 255, wl.location_id, 10.000, 10.000, 10.000
FROM wh_location wl WHERE wl.location_code = 'F-03-02' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1056, 122, 1056, 256, 278, 256, wl.location_id, 5.000, 5.000, 5.000
FROM wh_location wl WHERE wl.location_code = 'F-03-03' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO worker_handover (picking_task_id, worker_id, work_order_no, requisition_no, warehouse_handler, handover_time, remark) VALUES (122, 1, 'WO20260707026', 'REQ20260707031', '李仓', '2026-07-08 12:15:00', '演示交接') ON DUPLICATE KEY UPDATE handover_time=VALUES(handover_time);
INSERT INTO pmc_production_plan (plan_id, plan_no, source_system_id, source_order_id, mes_plan_no, plan_status, planned_start_date, planned_end_date, created_by, created_at) VALUES (123, 'PP-W-123', 1, 1, 'WO20260707027', 'READY', '2026-07-08', '2026-07-08', 1, '2026-07-08 09:30:00') ON DUPLICATE KEY UPDATE mes_plan_no=VALUES(mes_plan_no), planned_start_date=VALUES(planned_start_date);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1057, 123, 280, 8.000, '2026-07-08', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (1058, 123, 281, 8.000, '2026-07-08', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_order (requisition_id, requisition_no, source_order_id, source_plan_id, requisition_dept, requested_by, requested_at, requisition_status) VALUES (123, 'REQ-W-123', 1, 123, '生产一部', 1, '2026-07-08 09:30:00', 'PENDING_OUTBOUND') ON DUPLICATE KEY UPDATE source_plan_id=VALUES(source_plan_id);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1057, 123, 280, 8.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (1058, 123, 281, 8.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);
INSERT INTO out_order (outbound_id, outbound_no, requisition_id, outbound_type, warehouse_id, outbound_status, approved_at) VALUES (123, 'OUT-W-123', 123, 'PRODUCTION_ISSUE', 1, 'COMPLETED', '2026-07-08 09:30:00') ON DUPLICATE KEY UPDATE outbound_status=VALUES(outbound_status);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1057, 123, 1057, 280, 8.000, 8.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (1058, 123, 1058, 281, 8.000, 8.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);
INSERT INTO out_picking_task (picking_task_id, picking_task_no, outbound_id, assigned_to, task_status, planned_pick_time, created_at, priority, product_name, plan_qty, unit, handler_name, cancelled, work_order_no, requisition_no) VALUES (123, 'PICK-W-123', 123, 1, 'COMPLETED', '2026-07-08 09:30:00', '2026-07-08 09:30:00', '高', '压力变送器总成', 8.000, '套', '赵工', 0, 'WO20260707027', 'REQ20260707032') ON DUPLICATE KEY UPDATE task_status=VALUES(task_status), assigned_to=1, product_name=VALUES(product_name), plan_qty=VALUES(plan_qty), priority=VALUES(priority), handler_name=VALUES(handler_name), cancelled=VALUES(cancelled), work_order_no=VALUES(work_order_no), requisition_no=VALUES(requisition_no);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1057, 123, 1057, 257, 280, 257, wl.location_id, 8.000, 8.000, 8.000
FROM wh_location wl WHERE wl.location_code = 'E-02-05' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT 1058, 123, 1058, 258, 281, 258, wl.location_id, 8.000, 8.000, 8.000
FROM wh_location wl WHERE wl.location_code = 'E-02-06' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);
INSERT INTO worker_handover (picking_task_id, worker_id, work_order_no, requisition_no, warehouse_handler, handover_time, remark) VALUES (123, 1, 'WO20260707027', 'REQ20260707032', '赵工', '2026-07-08 10:40:00', '演示交接') ON DUPLICATE KEY UPDATE handover_time=VALUES(handover_time);
-- 异常
INSERT INTO worker_exception (exception_id, picking_task_id, picking_line_id, worker_id, work_order_no, requisition_no, material_name, required_qty, actual_qty, shortage_qty, exception_type, exception_note, location_code, exception_status, submitted_at) VALUES
(1001, 101, 1002, 1, 'WO20260708009', 'REQ20260708018', '防爆接插件', 40.000, 36.000, 4.000, '数量不足', '现场只收到 36 件', 'A-03-08', 'PENDING_WAREHOUSE', '2026-07-09 09:42:00'),
(1002, 106, 1011, 1, 'WO20260708006', 'REQ20260708015', '控制阀体', 12.000, 10.000, 2.000, '数量不足', '昨日领料缺 2 件，等待补拣', 'G-03-02', 'PENDING_WAREHOUSE', '2026-07-08 17:05:00'),
(1003, 101, 1002, 1, 'WO20260707020', 'REQ20260707022', '连接法兰', 6.000, 6.000, 0.000, '找不到物料', '已补拣完成', 'A-01-01', 'CLOSED', '2026-07-07 15:30:00')
ON DUPLICATE KEY UPDATE exception_status=VALUES(exception_status);
-- 扫码记录
INSERT INTO worker_scan_record (scan_id, picking_task_id, picking_line_id, worker_id, barcode_value, item_id, batch_id, material_name, batch_no, scan_result, result_message, scanned_at) VALUES
(1001, 101, 1002, 1, 'BC0001', 203, 202, '防爆接插件', 'B20260706004', 'SUCCESS', '成功', '2026-07-09 09:31:20'),
(1002, 101, 1002, 1, 'BC0002', 203, 202, '防爆接插件', 'B20260706004', 'SUCCESS', '成功', '2026-07-09 09:31:45'),
(1003, 101, 1002, 1, 'BC9999', 202, 201, '高温密封圈', 'B20260705011', 'FAILED', '非当前物料', '2026-07-09 09:32:10'),
(1004, 103, 1006, 1, 'BC0101', 209, 206, '钻具接头', 'B20260707001', 'SUCCESS', '成功', '2026-07-09 10:18:05'),
(1005, 103, 1007, 1, 'BC0102', 210, 207, '耐磨套筒', 'B20260707003', 'SUCCESS', '成功', '2026-07-09 10:22:30')
ON DUPLICATE KEY UPDATE scan_result=VALUES(scan_result);
-- 补料申请
INSERT INTO worker_replenish_request (replenish_id, picking_task_id, picking_line_id, worker_id, work_order_no, product_name, item_id, material_code, material_name, spec_model, request_qty, reason, note, request_status, submitted_at, handler_name) VALUES (1001, 101, 1002, 1, 'WO20260708009', '测井探头组件', 203, 'EXPROOF', '防爆接插件', 'EX-4P', 4.000, '装配过程发现数量不足', '现场缺 4 件，需仓管补发', 'PENDING_REVIEW', '2026-07-09 09:45:00', '赵工') ON DUPLICATE KEY UPDATE request_status=VALUES(request_status), request_qty=VALUES(request_qty);
INSERT INTO worker_replenish_request (replenish_id, picking_task_id, picking_line_id, worker_id, work_order_no, product_name, item_id, material_code, material_name, spec_model, request_qty, reason, note, request_status, submitted_at, handler_name) VALUES (1002, 106, 1011, 1, 'WO20260708006', '液压控制阀组', 217, 'VALVE-BODY', '控制阀体', 'VB-25', 2.000, '物料损坏需补发', '阀体磕碰，申请补发 2 件', 'REPLENISHING', '2026-07-08 17:20:00', '李仓') ON DUPLICATE KEY UPDATE request_status=VALUES(request_status), request_qty=VALUES(request_qty);
INSERT INTO worker_replenish_request (replenish_id, picking_task_id, picking_line_id, worker_id, work_order_no, product_name, item_id, material_code, material_name, spec_model, request_qty, reason, note, request_status, submitted_at, handler_name) VALUES (1003, 107, 1013, 1, 'WO20260708001', '电缆接头组件', 220, 'CABLE-JOINT', '电缆接头', 'CJ-3P', 2.000, '装配过程发现数量不足', '', 'COMPLETED', '2026-07-08 11:05:00', '赵工') ON DUPLICATE KEY UPDATE request_status=VALUES(request_status), request_qty=VALUES(request_qty);
INSERT INTO worker_replenish_request (replenish_id, picking_task_id, picking_line_id, worker_id, work_order_no, product_name, item_id, material_code, material_name, spec_model, request_qty, reason, note, request_status, submitted_at, handler_name) VALUES (1004, 108, 1020, 1, 'WO20260708010', '高温密封组件', 228, 'M5008', '连接器', 'CJ-3P', 1.000, '工艺变更追加用量', '工艺追加 1 件连接器', 'COMPLETED', '2026-07-08 16:40:00', '赵工') ON DUPLICATE KEY UPDATE request_status=VALUES(request_status), request_qty=VALUES(request_qty);
INSERT INTO worker_replenish_request (replenish_id, picking_task_id, picking_line_id, worker_id, work_order_no, product_name, item_id, material_code, material_name, spec_model, request_qty, reason, note, request_status, submitted_at, handler_name) VALUES (1005, 117, 1041, 1, 'WO20260708015', '井口防喷器密封件', 258, 'RUBBER-RAM', '橡胶闸板', 'RR-7-1/16', 4.000, '装配过程发现数量不足', '闸板磨损，需补发', 'PENDING_REVIEW', '2026-07-09 10:12:00', '赵工') ON DUPLICATE KEY UPDATE request_status=VALUES(request_status), request_qty=VALUES(request_qty);
INSERT INTO worker_replenish_request (replenish_id, picking_task_id, picking_line_id, worker_id, work_order_no, product_name, item_id, material_code, material_name, spec_model, request_qty, reason, note, request_status, submitted_at, handler_name) VALUES (1006, 118, 1045, 1, 'WO20260708016', '泥浆密度传感器', 263, 'TEMP-COMP', '温度补偿片', 'TC-PT100', 7.000, '装配过程发现数量不足', '扫码后发现短缺', 'REPLENISHING', '2026-07-09 10:50:00', '赵工') ON DUPLICATE KEY UPDATE request_status=VALUES(request_status), request_qty=VALUES(request_qty);
INSERT INTO worker_replenish_request (replenish_id, picking_task_id, picking_line_id, worker_id, work_order_no, product_name, item_id, material_code, material_name, spec_model, request_qty, reason, note, request_status, submitted_at, handler_name) VALUES (1007, 112, 1026, 1, 'WO20260709001', '泥浆脉冲发生器', 238, 'PULSE-CORE', '脉冲阀芯', 'PC-M12', 1.000, '物料损坏需补发', '阀芯表面划伤', 'PENDING_REVIEW', '2026-07-09 11:35:00', '赵工') ON DUPLICATE KEY UPDATE request_status=VALUES(request_status), request_qty=VALUES(request_qty);
INSERT INTO worker_replenish_request (replenish_id, picking_task_id, picking_line_id, worker_id, work_order_no, product_name, item_id, material_code, material_name, spec_model, request_qty, reason, note, request_status, submitted_at, handler_name) VALUES (1008, 113, 1029, 1, 'WO20260709002', '井下电源模块', 242, 'BAT-PACK', '锂电池组', 'BP-36V', 2.000, '工艺变更追加用量', '备用电池追加', 'REPLENISHING', '2026-07-09 13:18:00', '李仓') ON DUPLICATE KEY UPDATE request_status=VALUES(request_status), request_qty=VALUES(request_qty);
INSERT INTO worker_replenish_request (replenish_id, picking_task_id, picking_line_id, worker_id, work_order_no, product_name, item_id, material_code, material_name, spec_model, request_qty, reason, note, request_status, submitted_at, handler_name) VALUES (1009, 102, 1005, 1, 'WO20260708012', '井下通信模块', 207, 'WATER-JOINT', '防水接头', 'WJ-M12', 3.000, '装配过程发现数量不足', '', 'COMPLETED', '2026-07-08 14:22:00', '赵工') ON DUPLICATE KEY UPDATE request_status=VALUES(request_status), request_qty=VALUES(request_qty);
INSERT INTO worker_replenish_request (replenish_id, picking_task_id, picking_line_id, worker_id, work_order_no, product_name, item_id, material_code, material_name, spec_model, request_qty, reason, note, request_status, submitted_at, handler_name) VALUES (1010, 119, 1047, 1, 'WO20260708017', '井下马达定子', 266, 'STATOR-LINER', '定子橡胶衬套', 'SL-6-3/4', 1.000, '物料损坏需补发', '衬套开裂', 'PENDING_REVIEW', '2026-07-09 12:08:00', '赵工') ON DUPLICATE KEY UPDATE request_status=VALUES(request_status), request_qty=VALUES(request_qty);
INSERT INTO worker_replenish_request (replenish_id, picking_task_id, picking_line_id, worker_id, work_order_no, product_name, item_id, material_code, material_name, spec_model, request_qty, reason, note, request_status, submitted_at, handler_name) VALUES (1011, 114, 1034, 1, 'WO20260709003', '旋转导向短节', 248, 'BEARING-KIT', '轴承组件', 'BK-6205', 2.000, '装配过程发现数量不足', '轴承库存不足', 'REPLENISHING', '2026-07-09 14:05:00', '李仓') ON DUPLICATE KEY UPDATE request_status=VALUES(request_status), request_qty=VALUES(request_qty);
INSERT INTO worker_replenish_request (replenish_id, picking_task_id, picking_line_id, worker_id, work_order_no, product_name, item_id, material_code, material_name, spec_model, request_qty, reason, note, request_status, submitted_at, handler_name) VALUES (1012, 104, 1009, 1, 'WO20260708005', '压力传感器总成', 213, 'SS-SHELL', '不锈钢外壳', 'SS-Ø80', 1.000, '其他', '外壳变形更换', 'COMPLETED', '2026-07-08 09:40:00', '赵工') ON DUPLICATE KEY UPDATE request_status=VALUES(request_status), request_qty=VALUES(request_qty);
INSERT INTO worker_replenish_request (replenish_id, picking_task_id, picking_line_id, worker_id, work_order_no, product_name, item_id, material_code, material_name, spec_model, request_qty, reason, note, request_status, submitted_at, handler_name) VALUES (1013, 115, 1037, 1, 'WO20260709004', '伽马探管', 252, 'PMT-TUBE', '光电倍增管', 'PMT-R928', 1.000, '物料损坏需补发', '管脚弯曲', 'PENDING_REVIEW', '2026-07-09 15:10:00', '赵工') ON DUPLICATE KEY UPDATE request_status=VALUES(request_status), request_qty=VALUES(request_qty);
INSERT INTO worker_replenish_request (replenish_id, picking_task_id, picking_line_id, worker_id, work_order_no, product_name, item_id, material_code, material_name, spec_model, request_qty, reason, note, request_status, submitted_at, handler_name) VALUES (1014, 120, 1049, 1, 'WO20260708018', '振动筛筛网组件', 269, 'MAIN-SCREEN', '主筛网', 'MS-200', 8.000, '装配过程发现数量不足', '筛网破损需整批补发', 'REPLENISHING', '2026-07-09 13:42:00', '李仓') ON DUPLICATE KEY UPDATE request_status=VALUES(request_status), request_qty=VALUES(request_qty);
INSERT INTO worker_replenish_request (replenish_id, picking_task_id, picking_line_id, worker_id, work_order_no, product_name, item_id, material_code, material_name, spec_model, request_qty, reason, note, request_status, submitted_at, handler_name) VALUES (1015, 121, 1053, 1, 'WO20260707025', '液位变送器', 274, 'FLANGE', '法兰接头', 'FL-DN50', 2.000, '工艺变更追加用量', '', 'COMPLETED', '2026-07-08 15:30:00', '赵工') ON DUPLICATE KEY UPDATE request_status=VALUES(request_status), request_qty=VALUES(request_qty);
INSERT INTO worker_replenish_request (replenish_id, picking_task_id, picking_line_id, worker_id, work_order_no, product_name, item_id, material_code, material_name, spec_model, request_qty, reason, note, request_status, submitted_at, handler_name) VALUES (1016, 116, 1038, 1, 'WO20260709005', '随钻测斜仪', 254, 'ACCEL', '加速度计', 'ACC-3A', 2.000, '装配过程发现数量不足', '校准后发现缺件', 'PENDING_REVIEW', '2026-07-09 15:45:00', '赵工') ON DUPLICATE KEY UPDATE request_status=VALUES(request_status), request_qty=VALUES(request_qty);
SET FOREIGN_KEY_CHECKS = 1;
SELECT 'worker demo seeded' AS result, (SELECT COUNT(*) FROM out_picking_task WHERE assigned_to=1) AS tasks, (SELECT COUNT(*) FROM worker_replenish_request WHERE worker_id=1) AS replenish;