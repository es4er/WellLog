-- PMC 计划员演示数据（与前端工作台展示对齐）
-- 适用: MySQL 8.x / wms_db

SET NAMES utf8mb4;

-- ========== 基础主数据 ==========
INSERT INTO int_system (system_id, system_code, system_name, enabled_flag) VALUES
(1, 'ERP', 'ERP系统', 1),
(2, 'MES', 'MES系统', 1)
ON DUPLICATE KEY UPDATE system_name = VALUES(system_name);

INSERT INTO md_uom (uom_id, uom_code, uom_name, precision_scale) VALUES
(1, 'SET', '套', 3),
(2, 'PCS', '件', 3)
ON DUPLICATE KEY UPDATE uom_name = VALUES(uom_name);

INSERT INTO md_item_category (category_id, parent_id, category_code, category_name) VALUES
(1, NULL, 'CAT-PROD', '成品'),
(2, NULL, 'CAT-MAT', '原材料')
ON DUPLICATE KEY UPDATE category_name = VALUES(category_name);

INSERT INTO md_customer (customer_id, customer_code, customer_name, status) VALUES
(1, 'CUST-001', '中海油测井服务公司', 'ENABLED')
ON DUPLICATE KEY UPDATE customer_name = VALUES(customer_name);

INSERT INTO wh_warehouse (warehouse_id, warehouse_code, warehouse_name, warehouse_type, status) VALUES
(1, 'WH-MAIN', '主仓库', 'MAIN', 'ENABLED')
ON DUPLICATE KEY UPDATE warehouse_name = VALUES(warehouse_name);

INSERT INTO wh_zone (zone_id, warehouse_id, zone_code, zone_name, zone_type, status) VALUES
(1, 1, 'ZONE-A', 'A区标准库', 'NORMAL', 'ENABLED')
ON DUPLICATE KEY UPDATE zone_name = VALUES(zone_name);

INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status) VALUES
(1, 1, 'A-03-02', 'A区03排02位', 'AVAILABLE')
ON DUPLICATE KEY UPDATE location_name = VALUES(location_name);

-- 成品 + 原材料
INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, item_type, status) VALUES
(1, 'P-PROBE', '测井探头组件', 1, 1, 'PRODUCT', 'ENABLED'),
(2, 'P-CONN', '井下连接器模块', 1, 1, 'PRODUCT', 'ENABLED'),
(3, 'P-SEAL', '高温密封组件', 1, 1, 'PRODUCT', 'ENABLED'),
(4, 'P-EXPROOF', '防爆接插件总成', 1, 1, 'PRODUCT', 'ENABLED'),
(5, 'M-SEAL-RING', '高温密封圈', 2, 2, 'MATERIAL', 'ENABLED'),
(6, 'M-EXPROOF', '防爆接插件', 2, 2, 'MATERIAL', 'ENABLED'),
(7, 'M-HV-CONN', '耐高压连接器', 2, 2, 'MATERIAL', 'ENABLED'),
(8, 'M-PROBE-SHELL', '测井探头外壳', 2, 2, 'MATERIAL', 'ENABLED')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name);

INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES
(1, 5, 'B20260705011', 'QUALIFIED'),
(2, 6, 'B20260706004', 'QUALIFIED'),
(3, 8, 'B20260707002', 'QUALIFIED')
ON DUPLICATE KEY UPDATE batch_no = VALUES(batch_no);

-- 库存（部分物料低于安全库存以触发缺料场景）
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version) VALUES
(1, 1, 1, 5, 1, 120.000, 120.000, 0.000, 0.000, 'AVAILABLE', 0),
(2, 1, 1, 6, 2, 36.000, 36.000, 0.000, 0.000, 'AVAILABLE', 0),
(3, 1, 1, 7, 1, 200.000, 200.000, 0.000, 0.000, 'AVAILABLE', 0),
(4, 1, 1, 8, 3, 80.000, 80.000, 0.000, 0.000, 'AVAILABLE', 0),
(5, 1, 1, 1, 1, 50.000, 50.000, 0.000, 0.000, 'AVAILABLE', 0),
(6, 1, 1, 2, 1, 30.000, 30.000, 0.000, 0.000, 'AVAILABLE', 0)
ON DUPLICATE KEY UPDATE available_qty = VALUES(available_qty);

INSERT INTO inv_safety_stock_rule (rule_id, warehouse_id, item_id, min_qty, max_qty, reorder_qty, enabled_flag) VALUES
(1, 1, 5, 300.000, 500.000, 200.000, 1),
(2, 1, 6, 100.000, 200.000, 80.000, 1),
(3, 1, 8, 120.000, 200.000, 60.000, 1)
ON DUPLICATE KEY UPDATE min_qty = VALUES(min_qty);

-- ========== 客户订单（6 条待审核 + 3 条已审核） ==========
INSERT INTO ord_customer_order (order_id, order_no, customer_id, source_system_id, order_date, delivery_date, order_status, created_at) VALUES
(1, 'ORD20260705001', 1, 1, '2026-07-05', '2026-07-18', 'APPROVED', '2026-07-05 08:00:00'),
(2, 'ORD20260706002', 1, 1, '2026-07-06', '2026-07-15', 'APPROVED', '2026-07-06 09:00:00'),
(3, 'ORD20260706005', 1, 1, '2026-07-06', '2026-07-19', 'APPROVED', '2026-07-06 10:00:00'),
(4, 'ORD20260707001', 1, 1, '2026-07-07', '2026-07-20', 'PENDING_REVIEW', '2026-07-07 08:30:00'),
(5, 'ORD20260707002', 1, 1, '2026-07-07', '2026-07-21', 'PENDING_REVIEW', '2026-07-07 09:10:00'),
(6, 'ORD20260707003', 1, 1, '2026-07-07', '2026-07-22', 'PENDING_REVIEW', '2026-07-07 10:20:00'),
(7, 'ORD20260708001', 1, 1, '2026-07-08', '2026-07-23', 'PENDING_REVIEW', '2026-07-08 08:00:00'),
(8, 'ORD20260708002', 1, 1, '2026-07-08', '2026-07-24', 'PENDING_REVIEW', '2026-07-08 09:00:00'),
(9, 'ORD20260708003', 1, 1, '2026-07-08', '2026-07-25', 'PENDING_REVIEW', '2026-07-08 10:00:00')
ON DUPLICATE KEY UPDATE order_status = VALUES(order_status);

INSERT INTO ord_customer_order_line (order_line_id, order_id, item_id, ordered_qty, line_status) VALUES
(1, 1, 1, 20.000, 'OPEN'),
(2, 2, 2, 12.000, 'OPEN'),
(3, 3, 4, 18.000, 'OPEN'),
(4, 4, 1, 15.000, 'OPEN'),
(5, 5, 2, 10.000, 'OPEN'),
(6, 6, 3, 25.000, 'OPEN'),
(7, 7, 4, 8.000, 'OPEN'),
(8, 8, 1, 12.000, 'OPEN'),
(9, 9, 2, 16.000, 'OPEN')
ON DUPLICATE KEY UPDATE ordered_qty = VALUES(ordered_qty);

-- ========== 生产计划 ==========
INSERT INTO pmc_production_plan (plan_id, plan_no, source_system_id, source_order_id, mes_plan_no, plan_status, planned_start_date, planned_end_date, created_by, created_at) VALUES
(1, 'PP202607080003', 1, 1, NULL, 'WAITING_REQ', '2026-07-09', '2026-07-18', 1, '2026-07-08 08:40:00'),
(2, 'PP202607080004', 1, 2, NULL, 'READY', '2026-07-08', '2026-07-15', 1, '2026-07-08 08:45:00'),
(3, 'PP202607080005', 2, 3, 'MES-PLAN-0705', 'SHORTAGE', '2026-07-10', '2026-07-20', 1, '2026-07-08 09:00:00'),
(4, 'PP202607080006', 1, 4, NULL, 'WAITING_REQ', '2026-07-11', '2026-07-19', 1, '2026-07-08 09:15:00')
ON DUPLICATE KEY UPDATE plan_status = VALUES(plan_status), source_order_id = VALUES(source_order_id);

-- 计划行：BOM 物料需求
INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES
(1, 1, 5, 40.000, '2026-07-09', 'OPEN'),
(2, 1, 6, 40.000, '2026-07-09', 'OPEN'),
(3, 1, 8, 20.000, '2026-07-09', 'OPEN'),
(4, 2, 7, 36.000, '2026-07-08', 'OPEN'),
(5, 2, 6, 24.000, '2026-07-08', 'OPEN'),
(6, 3, 5, 150.000, '2026-07-10', 'OPEN'),
(7, 3, 6, 50.000, '2026-07-10', 'OPEN'),
(8, 4, 6, 36.000, '2026-07-11', 'OPEN'),
(9, 4, 7, 36.000, '2026-07-11', 'OPEN')
ON DUPLICATE KEY UPDATE required_qty = VALUES(required_qty);

-- ========== 领料单 ==========
INSERT INTO pmc_requisition_order (requisition_id, requisition_no, source_order_id, source_plan_id, requisition_dept, requested_by, requested_at, requisition_status) VALUES
(1, 'REQ20260708018', 1, 1, '生产一部', 1, '2026-07-08 08:52:00', 'PENDING_OUTBOUND'),
(2, 'REQ20260708019', 2, 2, '生产一部', 1, '2026-07-08 09:05:00', 'PENDING_OUTBOUND'),
(3, 'REQ20260708020', NULL, 3, '生产二部', 1, '2026-07-08 09:20:00', 'PENDING_OUTBOUND'),
(4, 'REQ20260708025', 4, 4, '生产一部', 1, '2026-07-08 10:00:00', 'PENDING_OUTBOUND')
ON DUPLICATE KEY UPDATE requisition_status = VALUES(requisition_status);

INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES
(1, 1, 5, 40.000, 0.000, 'OPEN'),
(2, 1, 6, 40.000, 0.000, 'OPEN'),
(3, 1, 8, 20.000, 0.000, 'OPEN'),
(4, 2, 7, 36.000, 0.000, 'OPEN'),
(5, 2, 6, 24.000, 0.000, 'OPEN'),
(6, 3, 5, 60.000, 0.000, 'OPEN'),
(7, 3, 6, 30.000, 0.000, 'OPEN'),
(8, 4, 6, 30.000, 0.000, 'OPEN'),
(9, 4, 7, 15.000, 0.000, 'OPEN')
ON DUPLICATE KEY UPDATE required_qty = VALUES(required_qty);

-- ========== 出库单 / 拣货 / 复核 ==========
INSERT INTO out_order (outbound_id, outbound_no, requisition_id, outbound_type, warehouse_id, outbound_status, approved_at) VALUES
(1, 'OUT20260708022', 1, 'PRODUCTION_ISSUE', 1, 'PICKING', '2026-07-08 09:05:00'),
(2, 'OUT20260708023', 2, 'PRODUCTION_ISSUE', 1, 'SHORTAGE_HOLD', '2026-07-08 09:10:00'),
(3, 'OUT20260708024', 3, 'PRODUCTION_ISSUE', 1, 'REVIEWING', '2026-07-08 09:15:00')
ON DUPLICATE KEY UPDATE outbound_status = VALUES(outbound_status);

INSERT INTO out_picking_task (picking_task_id, picking_task_no, outbound_id, assigned_to, task_status, created_at) VALUES
(1, 'PICK20260708022', 1, 1, 'IN_PROGRESS', '2026-07-08 09:18:00'),
(2, 'PICK20260708023', 2, 1, 'PAUSED', '2026-07-08 09:20:00'),
(3, 'PICK20260708024', 3, 1, 'COMPLETED', '2026-07-08 09:25:00')
ON DUPLICATE KEY UPDATE task_status = VALUES(task_status);

INSERT INTO out_review_task (review_task_id, review_task_no, outbound_id, reviewed_by, review_result) VALUES
(1, 'REV20260708024', 3, 1, 'PENDING')
ON DUPLICATE KEY UPDATE review_result = VALUES(review_result);
