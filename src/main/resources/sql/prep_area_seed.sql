-- 备料区库区与库位（生产领料暂存，库存管理员可在库位地图/库存控制查看）
-- 适用: MySQL 8.x / wms_db，可重复执行

SET NAMES utf8mb4;

-- 兼容旧「备件区」编码：统一改为备料区
UPDATE wh_zone
SET zone_code = 'ZONE-PREP', zone_name = '备料区', zone_type = 'STAGING', status = 'ENABLED'
WHERE zone_code = 'ZONE-SPARE';

INSERT INTO wh_zone (zone_id, warehouse_id, zone_code, zone_name, zone_type, status)
SELECT 90, 1, 'ZONE-PREP', '备料区', 'STAGING', 'ENABLED'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_zone WHERE zone_code = 'ZONE-PREP' AND warehouse_id = 1);

SET @prep_zone_id := (SELECT zone_id FROM wh_zone WHERE zone_code = 'ZONE-PREP' AND warehouse_id = 1 LIMIT 1);

-- 备料区库位 PREP-01-01 ~ PREP-04-08（与库位地图网格编码一致）
INSERT INTO wh_location (zone_id, location_code, location_name, location_status)
SELECT @prep_zone_id, CONCAT('PREP-', LPAD(r, 2, '0'), '-', LPAD(s, 2, '0')),
       CONCAT('备料区', r, '排', s, '位'), 'AVAILABLE'
FROM (
  SELECT r.n AS r, s.n AS s
  FROM (SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) r
  CROSS JOIN (SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
              UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8) s
) grid
WHERE @prep_zone_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM wh_location l
    WHERE l.zone_id = @prep_zone_id AND l.location_code = CONCAT('PREP-', LPAD(grid.r, 2, '0'), '-', LPAD(grid.s, 2, '0'))
  );

-- 历史备件区库位（SP-*）保留在库位表但不再作为备料格使用，避免下拉与地图混淆
UPDATE wh_location l
JOIN wh_zone z ON l.zone_id = z.zone_id
SET l.location_status = 'DISABLED'
WHERE z.zone_code = 'ZONE-PREP'
  AND l.location_code LIKE 'SP-%'
  AND l.location_status = 'AVAILABLE';
