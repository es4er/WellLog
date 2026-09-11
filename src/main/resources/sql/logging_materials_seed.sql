-- 测井工具 10 种物料 + 库位 + 批次 + 库存（每种可用库存 >= 200）
-- 适用: MySQL 8.x / wms_db
-- 可重复执行（按 item_code / location_code / batch_no 幂等）

SET NAMES utf8mb4;

-- 依赖主仓库与 A 区
INSERT INTO wh_warehouse (warehouse_id, warehouse_code, warehouse_name, warehouse_type, status) VALUES
(1, 'WH-MAIN', '主仓库', 'MAIN', 'ENABLED')
ON DUPLICATE KEY UPDATE warehouse_name = VALUES(warehouse_name);

INSERT INTO wh_zone (zone_id, warehouse_id, zone_code, zone_name, zone_type, status) VALUES
(1, 1, 'ZONE-A', 'A区标准库', 'NORMAL', 'ENABLED')
ON DUPLICATE KEY UPDATE zone_name = VALUES(zone_name);

INSERT INTO md_uom (uom_id, uom_code, uom_name, precision_scale) VALUES
(2, 'PCS', '件', 3)
ON DUPLICATE KEY UPDATE uom_name = VALUES(uom_name);

INSERT INTO md_item_category (category_id, parent_id, category_code, category_name) VALUES
(2, NULL, 'CAT-MAT', '原材料')
ON DUPLICATE KEY UPDATE category_name = VALUES(category_name);

-- ========== 10 个专用库位 ==========
INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status) VALUES
(1001, 1, 'T-01-01', '测井物料01位', 'AVAILABLE'),
(1002, 1, 'T-01-02', '测井物料02位', 'AVAILABLE'),
(1003, 1, 'T-01-03', '测井物料03位', 'AVAILABLE'),
(1004, 1, 'T-01-04', '测井物料04位', 'AVAILABLE'),
(1005, 1, 'T-01-05', '测井物料05位', 'AVAILABLE'),
(1006, 1, 'T-01-06', '测井物料06位', 'AVAILABLE'),
(1007, 1, 'T-01-07', '测井物料07位', 'AVAILABLE'),
(1008, 1, 'T-01-08', '测井物料08位', 'AVAILABLE'),
(1009, 1, 'T-01-09', '测井物料09位', 'AVAILABLE'),
(1010, 1, 'T-01-10', '测井物料10位', 'AVAILABLE')
ON DUPLICATE KEY UPDATE location_name = VALUES(location_name), location_status = 'AVAILABLE';

-- ========== 10 种测井物料 ==========
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES
(1001, 'M-LOG-FLANGE',      '法兰连接盘', 2, 2, '测井工具法兰盘', 'MATERIAL', 'ENABLED'),
(1002, 'M-LOG-PROBE-SHELL', '探头外壳',   2, 2, '测井探头外壳',   'MATERIAL', 'ENABLED'),
(1003, 'M-LOG-PRESS-CYL',   '压力筒',     2, 2, '测井压力筒',     'MATERIAL', 'ENABLED'),
(1004, 'M-LOG-CONN-JOINT',  '连接接头',   2, 2, '测井连接接头',   'MATERIAL', 'ENABLED'),
(1005, 'M-LOG-THREAD-JOINT','螺纹接头',   2, 2, '测井螺纹接头',   'MATERIAL', 'ENABLED'),
(1006, 'M-LOG-CENTRALIZER', '中心定位器', 2, 2, '测井中心定位器', 'MATERIAL', 'ENABLED'),
(1007, 'M-LOG-STAB-BLADE',  '稳定器叶片', 2, 2, '测井稳定器叶片', 'MATERIAL', 'ENABLED'),
(1008, 'M-LOG-SLIP-SEAT',   '卡瓦座',     2, 2, '测井卡瓦座',     'MATERIAL', 'ENABLED'),
(1009, 'M-LOG-TOP-SUB',     '上接头',     2, 2, '测井上接头',     'MATERIAL', 'ENABLED'),
(1010, 'M-LOG-BOP-SUB',     '防喷接头',   2, 2, '测井防喷接头',   'MATERIAL', 'ENABLED')
ON DUPLICATE KEY UPDATE
  item_name = VALUES(item_name),
  spec_model = VALUES(spec_model),
  category_id = VALUES(category_id),
  uom_id = VALUES(uom_id),
  item_type = VALUES(item_type),
  status = VALUES(status);

-- ========== 批次（质检合格） ==========
INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES
(1001, 1001, 'BLOG20260714001', 'QUALIFIED'),
(1002, 1002, 'BLOG20260714002', 'QUALIFIED'),
(1003, 1003, 'BLOG20260714003', 'QUALIFIED'),
(1004, 1004, 'BLOG20260714004', 'QUALIFIED'),
(1005, 1005, 'BLOG20260714005', 'QUALIFIED'),
(1006, 1006, 'BLOG20260714006', 'QUALIFIED'),
(1007, 1007, 'BLOG20260714007', 'QUALIFIED'),
(1008, 1008, 'BLOG20260714008', 'QUALIFIED'),
(1009, 1009, 'BLOG20260714009', 'QUALIFIED'),
(1010, 1010, 'BLOG20260714010', 'QUALIFIED')
ON DUPLICATE KEY UPDATE batch_no = VALUES(batch_no), quality_status = 'QUALIFIED';

-- ========== 库存：每种 250 件（>= 200）分配到独立库位 ==========
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version) VALUES
(1001, 1, 1001, 1001, 1001, 250.000, 250.000, 0.000, 0.000, 'AVAILABLE', 0),
(1002, 1, 1002, 1002, 1002, 250.000, 250.000, 0.000, 0.000, 'AVAILABLE', 0),
(1003, 1, 1003, 1003, 1003, 250.000, 250.000, 0.000, 0.000, 'AVAILABLE', 0),
(1004, 1, 1004, 1004, 1004, 250.000, 250.000, 0.000, 0.000, 'AVAILABLE', 0),
(1005, 1, 1005, 1005, 1005, 250.000, 250.000, 0.000, 0.000, 'AVAILABLE', 0),
(1006, 1, 1006, 1006, 1006, 250.000, 250.000, 0.000, 0.000, 'AVAILABLE', 0),
(1007, 1, 1007, 1007, 1007, 250.000, 250.000, 0.000, 0.000, 'AVAILABLE', 0),
(1008, 1, 1008, 1008, 1008, 250.000, 250.000, 0.000, 0.000, 'AVAILABLE', 0),
(1009, 1, 1009, 1009, 1009, 250.000, 250.000, 0.000, 0.000, 'AVAILABLE', 0),
(1010, 1, 1010, 1010, 1010, 250.000, 250.000, 0.000, 0.000, 'AVAILABLE', 0)
ON DUPLICATE KEY UPDATE
  location_id = VALUES(location_id),
  batch_id = VALUES(batch_id),
  onhand_qty = GREATEST(onhand_qty, VALUES(onhand_qty)),
  available_qty = GREATEST(available_qty, VALUES(available_qty)),
  inventory_status = 'AVAILABLE';

-- ========== 安全库存规则（最低 200，便于补货/库存控制识别） ==========
INSERT INTO inv_safety_stock_rule (rule_id, warehouse_id, item_id, min_qty, max_qty, reorder_qty, enabled_flag) VALUES
(1001, 1, 1001, 200.000, 400.000, 100.000, 1),
(1002, 1, 1002, 200.000, 400.000, 100.000, 1),
(1003, 1, 1003, 200.000, 400.000, 100.000, 1),
(1004, 1, 1004, 200.000, 400.000, 100.000, 1),
(1005, 1, 1005, 200.000, 400.000, 100.000, 1),
(1006, 1, 1006, 200.000, 400.000, 100.000, 1),
(1007, 1, 1007, 200.000, 400.000, 100.000, 1),
(1008, 1, 1008, 200.000, 400.000, 100.000, 1),
(1009, 1, 1009, 200.000, 400.000, 100.000, 1),
(1010, 1, 1010, 200.000, 400.000, 100.000, 1)
ON DUPLICATE KEY UPDATE min_qty = VALUES(min_qty), max_qty = VALUES(max_qty), reorder_qty = VALUES(reorder_qty), enabled_flag = 1;
