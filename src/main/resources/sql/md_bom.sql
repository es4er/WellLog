-- BOM 主数据 + PMC 排产所需原材料库存补齐
-- 适用: MySQL 8.x / wms_db
-- 用法: mysql -uroot -p wms_db < md_bom.sql

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS md_bom_header (
    bom_id           BIGINT       NOT NULL AUTO_INCREMENT,
    product_item_id  BIGINT       NOT NULL,
    bom_version      VARCHAR(32)  NOT NULL DEFAULT 'V1',
    status           VARCHAR(20)  NOT NULL DEFAULT 'ENABLED',
    remark           VARCHAR(255) NULL,
    created_at       DATETIME     NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (bom_id),
    UNIQUE KEY uk_bom_product_ver (product_item_id, bom_version),
    KEY idx_bom_product_status (product_item_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS md_bom_line (
    bom_line_id        BIGINT         NOT NULL AUTO_INCREMENT,
    bom_id             BIGINT         NOT NULL,
    component_item_id  BIGINT         NOT NULL,
    qty_per            DECIMAL(18,3)  NOT NULL DEFAULT 1.000,
    line_no            INT            NOT NULL DEFAULT 1,
    remark             VARCHAR(255)   NULL,
    PRIMARY KEY (bom_line_id),
    KEY idx_bom_line_header (bom_id),
    KEY idx_bom_line_component (component_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== 经典成品 1-4（与原硬编码一致）==========
INSERT INTO md_bom_header (bom_id, product_item_id, bom_version, status, remark) VALUES
(1, 1, 'V1', 'ENABLED', '测井探头组件'),
(2, 2, 'V1', 'ENABLED', '井下连接器模块'),
(3, 3, 'V1', 'ENABLED', '高温密封组件'),
(4, 4, 'V1', 'ENABLED', '防爆接插件总成')
ON DUPLICATE KEY UPDATE status = VALUES(status), remark = VALUES(remark);

INSERT INTO md_bom_line (bom_line_id, bom_id, component_item_id, qty_per, line_no) VALUES
(1, 1, 5, 2.000, 1),
(2, 1, 6, 2.000, 2),
(3, 1, 8, 1.000, 3),
(4, 2, 7, 3.000, 1),
(5, 2, 6, 2.000, 2),
(6, 3, 5, 6.000, 1),
(7, 3, 6, 2.000, 2),
(8, 4, 6, 2.000, 1),
(9, 4, 7, 2.000, 2)
ON DUPLICATE KEY UPDATE qty_per = VALUES(qty_per), component_item_id = VALUES(component_item_id);

-- ========== 仓管成品 201+ ==========
INSERT INTO md_bom_header (bom_id, product_item_id, bom_version, status, remark) VALUES
(201, 201, 'V1', 'ENABLED', '测井探头组件'),
(205, 205, 'V1', 'ENABLED', '井下通信模块'),
(208, 208, 'V1', 'ENABLED', '定向钻具组件'),
(211, 211, 'V1', 'ENABLED', '压力传感器总成'),
(214, 214, 'V1', 'ENABLED', '数据采集卡'),
(216, 216, 'V1', 'ENABLED', '液压控制阀组'),
(219, 219, 'V1', 'ENABLED', '电缆接头组件'),
(221, 221, 'V1', 'ENABLED', '高温密封组件'),
(230, 230, 'V1', 'ENABLED', '传感器壳体'),
(233, 233, 'V1', 'ENABLED', '信号调理模块'),
(235, 235, 'V1', 'ENABLED', '临时试制件'),
(237, 237, 'V1', 'ENABLED', '泥浆脉冲发生器'),
(241, 241, 'V1', 'ENABLED', '井下电源模块'),
(246, 246, 'V1', 'ENABLED', '旋转导向短节'),
(250, 250, 'V1', 'ENABLED', '伽马探管'),
(253, 253, 'V1', 'ENABLED', '随钻测斜仪'),
(257, 257, 'V1', 'ENABLED', '井口防喷器密封件'),
(261, 261, 'V1', 'ENABLED', '泥浆密度传感器'),
(265, 265, 'V1', 'ENABLED', '井下马达定子'),
(268, 268, 'V1', 'ENABLED', '振动筛筛网组件'),
(272, 272, 'V1', 'ENABLED', '液位变送器'),
(275, 275, 'V1', 'ENABLED', '电磁流量计'),
(279, 279, 'V1', 'ENABLED', '压力变送器总成')
ON DUPLICATE KEY UPDATE status = VALUES(status), remark = VALUES(remark);

INSERT INTO md_bom_line (bom_line_id, bom_id, component_item_id, qty_per, line_no) VALUES
-- 201: 202/203/204
(2011, 201, 202, 2.000, 1),
(2012, 201, 203, 2.000, 2),
(2013, 201, 204, 1.000, 3),
-- 205
(2051, 205, 206, 1.000, 1),
(2052, 205, 207, 2.000, 2),
-- 208
(2081, 208, 209, 1.000, 1),
(2082, 208, 210, 1.000, 2),
-- 211
(2111, 211, 212, 1.000, 1),
(2112, 211, 213, 1.000, 2),
-- 214
(2141, 214, 215, 1.000, 1),
-- 216
(2161, 216, 217, 1.000, 1),
(2162, 216, 218, 1.000, 2),
-- 219
(2191, 219, 220, 1.000, 1),
-- 221（主料 + 少量紧固件）
(2211, 221, 222, 1.000, 1),
(2212, 221, 223, 2.000, 2),
(2213, 221, 224, 4.000, 3),
(2214, 221, 225, 2.000, 4),
-- 230
(2301, 230, 231, 1.000, 1),
(2302, 230, 232, 1.000, 2),
-- 233
(2331, 233, 234, 1.000, 1),
-- 235
(2351, 235, 236, 1.000, 1),
-- 237
(2371, 237, 238, 1.000, 1),
(2372, 237, 239, 1.000, 2),
(2373, 237, 240, 1.000, 3),
-- 241
(2411, 241, 242, 1.000, 1),
(2412, 241, 243, 1.000, 2),
(2413, 241, 244, 2.000, 3),
(2414, 241, 245, 2.000, 4),
-- 246
(2461, 246, 247, 1.000, 1),
(2462, 246, 248, 1.000, 2),
(2463, 246, 249, 1.000, 3),
-- 250
(2501, 250, 251, 1.000, 1),
(2502, 250, 252, 1.000, 2),
-- 253
(2531, 253, 254, 1.000, 1),
(2532, 253, 255, 1.000, 2),
(2533, 253, 256, 1.000, 3),
-- 257
(2571, 257, 258, 1.000, 1),
(2572, 257, 259, 1.000, 2),
(2573, 257, 260, 4.000, 3),
-- 261
(2611, 261, 262, 1.000, 1),
(2612, 261, 263, 1.000, 2),
(2613, 261, 264, 1.000, 3),
-- 265
(2651, 265, 266, 1.000, 1),
(2652, 265, 267, 1.000, 2),
-- 268
(2681, 268, 269, 1.000, 1),
(2682, 268, 270, 2.000, 2),
(2683, 268, 271, 4.000, 3),
-- 272
(2721, 272, 273, 1.000, 1),
(2722, 272, 274, 1.000, 2),
-- 275
(2751, 275, 276, 1.000, 1),
(2752, 275, 277, 2.000, 2),
(2753, 275, 278, 1.000, 3),
-- 279
(2791, 279, 280, 1.000, 1),
(2792, 279, 281, 1.000, 2)
ON DUPLICATE KEY UPDATE qty_per = VALUES(qty_per), component_item_id = VALUES(component_item_id);

-- ========== 库存补齐 ==========
-- item 6 保持低库存/0 以演示真实缺料；其余关键组件补合格可用量
-- 批次 301+ / 库存 301+

INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES
(301, 6, 'BOM-B6-PARTIAL', 'QUALIFIED'),
(302, 202, 'BOM-B202', 'QUALIFIED'),
(303, 215, 'BOM-B215', 'QUALIFIED'),
(304, 217, 'BOM-B217', 'QUALIFIED'),
(305, 218, 'BOM-B218', 'QUALIFIED'),
(306, 220, 'BOM-B220', 'QUALIFIED'),
(307, 251, 'BOM-B251', 'QUALIFIED'),
(308, 252, 'BOM-B252', 'QUALIFIED'),
(309, 254, 'BOM-B254', 'QUALIFIED'),
(310, 255, 'BOM-B255', 'QUALIFIED'),
(311, 256, 'BOM-B256', 'QUALIFIED'),
(312, 258, 'BOM-B258', 'QUALIFIED'),
(313, 259, 'BOM-B259', 'QUALIFIED'),
(314, 260, 'BOM-B260', 'QUALIFIED'),
(315, 262, 'BOM-B262', 'QUALIFIED'),
(316, 263, 'BOM-B263', 'QUALIFIED'),
(317, 264, 'BOM-B264', 'QUALIFIED'),
(318, 266, 'BOM-B266', 'QUALIFIED'),
(319, 267, 'BOM-B267', 'QUALIFIED'),
(320, 269, 'BOM-B269', 'QUALIFIED'),
(321, 270, 'BOM-B270', 'QUALIFIED'),
(322, 271, 'BOM-B271', 'QUALIFIED'),
(323, 273, 'BOM-B273', 'QUALIFIED'),
(324, 274, 'BOM-B274', 'QUALIFIED'),
(325, 276, 'BOM-B276', 'QUALIFIED'),
(326, 277, 'BOM-B277', 'QUALIFIED'),
(327, 278, 'BOM-B278', 'QUALIFIED'),
(328, 280, 'BOM-B280', 'QUALIFIED'),
(329, 281, 'BOM-B281', 'QUALIFIED')
ON DUPLICATE KEY UPDATE quality_status = 'QUALIFIED';

-- item 6：仅补少量，经典成品仍易缺料；201+ 成功路径不依赖它
INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id,
                           onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
VALUES
(301, 1, 1, 6, 301, 8.000, 8.000, 0.000, 0.000, 'AVAILABLE', 0),
(302, 1, 1, 202, 302, 200.000, 200.000, 0.000, 0.000, 'AVAILABLE', 0),
(303, 1, 1, 215, 303, 120.000, 120.000, 0.000, 0.000, 'AVAILABLE', 0),
(304, 1, 1, 217, 304, 80.000, 80.000, 0.000, 0.000, 'AVAILABLE', 0),
(305, 1, 1, 218, 305, 80.000, 80.000, 0.000, 0.000, 'AVAILABLE', 0),
(306, 1, 1, 220, 306, 100.000, 100.000, 0.000, 0.000, 'AVAILABLE', 0),
(307, 1, 1, 251, 307, 50.000, 50.000, 0.000, 0.000, 'AVAILABLE', 0),
(308, 1, 1, 252, 308, 50.000, 50.000, 0.000, 0.000, 'AVAILABLE', 0),
(309, 1, 1, 254, 309, 40.000, 40.000, 0.000, 0.000, 'AVAILABLE', 0),
(310, 1, 1, 255, 310, 40.000, 40.000, 0.000, 0.000, 'AVAILABLE', 0),
(311, 1, 1, 256, 311, 40.000, 40.000, 0.000, 0.000, 'AVAILABLE', 0),
(312, 1, 1, 258, 312, 30.000, 30.000, 0.000, 0.000, 'AVAILABLE', 0),
(313, 1, 1, 259, 313, 30.000, 30.000, 0.000, 0.000, 'AVAILABLE', 0),
(314, 1, 1, 260, 314, 100.000, 100.000, 0.000, 0.000, 'AVAILABLE', 0),
(315, 1, 1, 262, 315, 40.000, 40.000, 0.000, 0.000, 'AVAILABLE', 0),
(316, 1, 1, 263, 316, 40.000, 40.000, 0.000, 0.000, 'AVAILABLE', 0),
(317, 1, 1, 264, 317, 40.000, 40.000, 0.000, 0.000, 'AVAILABLE', 0),
(318, 1, 1, 266, 318, 30.000, 30.000, 0.000, 0.000, 'AVAILABLE', 0),
(319, 1, 1, 267, 319, 30.000, 30.000, 0.000, 0.000, 'AVAILABLE', 0),
(320, 1, 1, 269, 320, 40.000, 40.000, 0.000, 0.000, 'AVAILABLE', 0),
(321, 1, 1, 270, 321, 80.000, 80.000, 0.000, 0.000, 'AVAILABLE', 0),
(322, 1, 1, 271, 322, 120.000, 120.000, 0.000, 0.000, 'AVAILABLE', 0),
(323, 1, 1, 273, 323, 40.000, 40.000, 0.000, 0.000, 'AVAILABLE', 0),
(324, 1, 1, 274, 324, 40.000, 40.000, 0.000, 0.000, 'AVAILABLE', 0),
(325, 1, 1, 276, 325, 30.000, 30.000, 0.000, 0.000, 'AVAILABLE', 0),
(326, 1, 1, 277, 326, 60.000, 60.000, 0.000, 0.000, 'AVAILABLE', 0),
(327, 1, 1, 278, 327, 30.000, 30.000, 0.000, 0.000, 'AVAILABLE', 0),
(328, 1, 1, 280, 328, 40.000, 40.000, 0.000, 0.000, 'AVAILABLE', 0),
(329, 1, 1, 281, 329, 40.000, 40.000, 0.000, 0.000, 'AVAILABLE', 0)
ON DUPLICATE KEY UPDATE
  available_qty = VALUES(available_qty),
  onhand_qty = VALUES(onhand_qty),
  inventory_status = 'AVAILABLE';
