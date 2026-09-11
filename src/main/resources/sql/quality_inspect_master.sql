-- 质检主数据：检验项库 + 检验标准（按物料）
CREATE TABLE IF NOT EXISTS qua_inspect_item (
  inspect_item_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  item_code VARCHAR(64) NOT NULL UNIQUE,
  item_name VARCHAR(128) NOT NULL,
  item_type VARCHAR(32) NOT NULL COMMENT 'APPEARANCE/DIMENSION/PERFORMANCE/OTHER',
  unit VARCHAR(32) DEFAULT NULL,
  default_standard VARCHAR(512) DEFAULT NULL,
  critical_flag TINYINT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
  sort_no INT NOT NULL DEFAULT 0,
  remark VARCHAR(255) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS qua_inspect_standard (
  standard_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  standard_code VARCHAR(64) NOT NULL UNIQUE,
  standard_name VARCHAR(128) NOT NULL,
  md_item_id BIGINT DEFAULT NULL COMMENT '关联 md_item，空表示通用标准',
  version_no VARCHAR(32) NOT NULL DEFAULT 'A',
  aql_level VARCHAR(16) NOT NULL DEFAULT 'II',
  aql_value DECIMAL(10,2) NOT NULL DEFAULT 1.50,
  drawing_no VARCHAR(64) DEFAULT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
  remark VARCHAR(255) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_std_item (md_item_id),
  KEY idx_std_status (status)
);

CREATE TABLE IF NOT EXISTS qua_inspect_standard_line (
  line_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  standard_id BIGINT NOT NULL,
  inspect_item_id BIGINT NOT NULL,
  required_flag TINYINT NOT NULL DEFAULT 1,
  standard_text VARCHAR(512) DEFAULT NULL,
  nominal DECIMAL(18,4) DEFAULT NULL,
  lower_tol DECIMAL(18,4) DEFAULT NULL,
  upper_tol DECIMAL(18,4) DEFAULT NULL,
  min_value DECIMAL(18,4) DEFAULT NULL,
  max_value DECIMAL(18,4) DEFAULT NULL,
  unit VARCHAR(32) DEFAULT NULL,
  critical_flag TINYINT NOT NULL DEFAULT 0,
  sort_no INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_std_item (standard_id, inspect_item_id),
  KEY idx_line_std (standard_id)
);

-- ========== 检验项库 ==========
INSERT INTO qua_inspect_item (item_code, item_name, item_type, unit, default_standard, critical_flag, sort_no)
VALUES
  ('APPEARANCE', '外观检测', 'APPEARANCE', NULL, '表面无划痕、无锈蚀、无变形，标识清晰', 0, 10),
  ('DIMENSION', '尺寸检测', 'DIMENSION', 'mm', '关键尺寸按图纸公差执行', 1, 20),
  ('PRESSURE', '耐压测试', 'PERFORMANCE', 'V', '绝缘耐压测试无击穿', 1, 30),
  ('HARDNESS', '硬度测试', 'PERFORMANCE', 'HRC', '硬度在规定区间内', 0, 40),
  ('SEAL', '密封测试', 'PERFORMANCE', 'MPa', '水压/气密试验无渗漏', 1, 50),
  ('FLANGE-OD', '法兰外径', 'DIMENSION', 'mm', '法兰外径按图纸公差执行', 0, 110),
  ('FLANGE-THK', '法兰厚度', 'DIMENSION', 'mm', '法兰厚度按图纸公差执行', 1, 120),
  ('FLANGE-BCD', '螺栓孔中心距', 'DIMENSION', 'mm', '螺栓孔中心距按图纸公差执行', 1, 130),
  ('FLANGE-HOLE', '螺栓孔径', 'DIMENSION', 'mm', '螺栓孔径按图纸公差执行', 0, 140),
  ('FLANGE-RA', '密封面粗糙度', 'DIMENSION', 'μm', '密封面粗糙度 Ra 值符合图纸', 0, 150),
  ('FLANGE-ID', '内孔直径', 'DIMENSION', 'mm', '内孔直径按图纸公差执行', 1, 160)
ON DUPLICATE KEY UPDATE
  item_name = VALUES(item_name),
  item_type = VALUES(item_type),
  unit = VALUES(unit),
  default_standard = VALUES(default_standard),
  critical_flag = VALUES(critical_flag),
  sort_no = VALUES(sort_no);

-- 停用历史自动种子中非测井物料的标准（保留通用标准）
UPDATE qua_inspect_standard
SET status = 'DISABLED', updated_at = CURRENT_TIMESTAMP
WHERE standard_code NOT LIKE 'STD-M-LOG-%'
  AND standard_code != 'STD-GENERAL';

-- ========== 10 种测井物料专属检验标准 ==========
INSERT INTO qua_inspect_standard (standard_code, standard_name, md_item_id, version_no, aql_level, aql_value, drawing_no, status, remark)
SELECT
  CONCAT('STD-', i.item_code),
  CONCAT(
    CASE i.item_code
      WHEN 'M-LOG-FLANGE' THEN '法兰盘'
      WHEN 'M-LOG-PROBE-SHELL' THEN '探头外壳'
      WHEN 'M-LOG-PRESS-CYL' THEN '压力筒'
      WHEN 'M-LOG-CONN-JOINT' THEN '连接接头'
      WHEN 'M-LOG-THREAD-JOINT' THEN '螺纹接头'
      WHEN 'M-LOG-CENTRALIZER' THEN '中心定位器'
      WHEN 'M-LOG-STAB-BLADE' THEN '稳定器叶片'
      WHEN 'M-LOG-SLIP-SEAT' THEN '卡瓦座'
      WHEN 'M-LOG-TOP-SUB' THEN '上接头'
      WHEN 'M-LOG-BOP-SUB' THEN '防喷接头'
      ELSE i.item_name
    END,
    ' 来料检验标准'
  ),
  i.item_id,
  'A',
  'II',
  1.50,
  CONCAT('DWG-', i.item_code, '-001'),
  'ENABLED',
  '测井工具物料检验标准'
FROM md_item i
WHERE i.item_code IN (
  'M-LOG-FLANGE', 'M-LOG-PROBE-SHELL', 'M-LOG-PRESS-CYL', 'M-LOG-CONN-JOINT',
  'M-LOG-THREAD-JOINT', 'M-LOG-CENTRALIZER', 'M-LOG-STAB-BLADE', 'M-LOG-SLIP-SEAT',
  'M-LOG-TOP-SUB', 'M-LOG-BOP-SUB'
)
ON DUPLICATE KEY UPDATE
  standard_name = VALUES(standard_name),
  md_item_id = VALUES(md_item_id),
  drawing_no = VALUES(drawing_no),
  status = 'ENABLED',
  remark = VALUES(remark),
  updated_at = CURRENT_TIMESTAMP;

-- 通用兜底标准
INSERT INTO qua_inspect_standard (standard_code, standard_name, md_item_id, version_no, aql_level, aql_value, drawing_no, status, remark)
VALUES ('STD-GENERAL', '通用来料检验标准', NULL, 'A', 'II', 1.50, NULL, 'ENABLED', '无物料专属标准时使用')
ON DUPLICATE KEY UPDATE standard_name = VALUES(standard_name), status = 'ENABLED';

-- 重建测井物料标准明细行
DELETE l FROM qua_inspect_standard_line l
INNER JOIN qua_inspect_standard s ON s.standard_id = l.standard_id
WHERE s.standard_code LIKE 'STD-M-LOG-%';

-- 法兰盘：外观 + 6 项尺寸 + 密封测试
INSERT INTO qua_inspect_standard_line (
  standard_id, inspect_item_id, required_flag, standard_text,
  nominal, lower_tol, upper_tol, min_value, max_value, unit, critical_flag, sort_no
)
SELECT s.standard_id, ii.inspect_item_id, 1,
       CASE ii.item_code
         WHEN 'APPEARANCE' THEN '无砂眼、无裂纹，密封面完好，标识清晰'
         WHEN 'FLANGE-OD' THEN '法兰外径 285±1.0mm'
         WHEN 'FLANGE-THK' THEN '法兰厚度 22.0±0.1mm'
         WHEN 'FLANGE-BCD' THEN '螺栓孔中心距 240±0.5mm'
         WHEN 'FLANGE-HOLE' THEN '螺栓孔径 22 +0.3/0mm'
         WHEN 'FLANGE-RA' THEN '密封面粗糙度 Ra≤3.2μm'
         WHEN 'FLANGE-ID' THEN '内孔直径 159.3 +0.5/0mm'
         WHEN 'SEAL' THEN '1.6MPa 水压试验无渗漏'
         ELSE ii.default_standard
       END,
       CASE ii.item_code
         WHEN 'FLANGE-OD' THEN 285.0000
         WHEN 'FLANGE-THK' THEN 22.0000
         WHEN 'FLANGE-BCD' THEN 240.0000
         WHEN 'FLANGE-HOLE' THEN 22.0000
         WHEN 'FLANGE-RA' THEN 3.2000
         WHEN 'FLANGE-ID' THEN 159.3000
         ELSE NULL
       END,
       CASE ii.item_code
         WHEN 'FLANGE-OD' THEN -1.0000
         WHEN 'FLANGE-THK' THEN -0.1000
         WHEN 'FLANGE-BCD' THEN -0.5000
         WHEN 'FLANGE-HOLE' THEN 0.0000
         WHEN 'FLANGE-RA' THEN -1.6000
         WHEN 'FLANGE-ID' THEN 0.0000
         ELSE NULL
       END,
       CASE ii.item_code
         WHEN 'FLANGE-OD' THEN 1.0000
         WHEN 'FLANGE-THK' THEN 0.1000
         WHEN 'FLANGE-BCD' THEN 0.5000
         WHEN 'FLANGE-HOLE' THEN 0.3000
         WHEN 'FLANGE-RA' THEN 1.6000
         WHEN 'FLANGE-ID' THEN 0.5000
         ELSE NULL
       END,
       CASE ii.item_code WHEN 'SEAL' THEN 1.6000 ELSE NULL END,
       NULL,
       ii.unit,
       CASE ii.item_code
         WHEN 'FLANGE-THK' THEN 1
         WHEN 'FLANGE-BCD' THEN 1
         WHEN 'FLANGE-ID' THEN 1
         WHEN 'SEAL' THEN 1
         ELSE ii.critical_flag
       END,
       ii.sort_no
FROM qua_inspect_standard s
JOIN qua_inspect_item ii ON ii.item_code IN (
  'APPEARANCE', 'FLANGE-OD', 'FLANGE-THK', 'FLANGE-BCD', 'FLANGE-HOLE', 'FLANGE-RA', 'FLANGE-ID', 'SEAL'
)
WHERE s.standard_code = 'STD-M-LOG-FLANGE';

-- 其余 9 种测井物料：外观 + 尺寸 + 性能（按物料差异化）
INSERT INTO qua_inspect_standard_line (
  standard_id, inspect_item_id, required_flag, standard_text,
  nominal, lower_tol, upper_tol, min_value, max_value, unit, critical_flag, sort_no
)
SELECT s.standard_id, ii.inspect_item_id, 1,
       CASE CONCAT(s.standard_code, ':', ii.item_code)
         WHEN 'STD-M-LOG-PROBE-SHELL:APPEARANCE' THEN '壳体无裂纹、镀层完整，标识清晰'
         WHEN 'STD-M-LOG-PROBE-SHELL:DIMENSION' THEN '外壳外径及壁厚按 DWG-M-LOG-PROBE-SHELL-001 执行'
         WHEN 'STD-M-LOG-PROBE-SHELL:PRESSURE' THEN '密封性能测试无渗漏'
         WHEN 'STD-M-LOG-PRESS-CYL:APPEARANCE' THEN '筒体无锈蚀、焊缝完好，标识清晰'
         WHEN 'STD-M-LOG-PRESS-CYL:DIMENSION' THEN '内径及壁厚按图纸公差执行'
         WHEN 'STD-M-LOG-PRESS-CYL:PRESSURE' THEN '耐压 35MPa 保压 5min 无泄漏'
         WHEN 'STD-M-LOG-CONN-JOINT:APPEARANCE' THEN '螺纹完好无损伤，表面无锈蚀'
         WHEN 'STD-M-LOG-CONN-JOINT:DIMENSION' THEN '接头长度及螺纹尺寸按图纸执行'
         WHEN 'STD-M-LOG-CONN-JOINT:HARDNESS' THEN '螺纹硬度 HRC 28-34'
         WHEN 'STD-M-LOG-THREAD-JOINT:APPEARANCE' THEN '螺纹牙型完整，无磕碰'
         WHEN 'STD-M-LOG-THREAD-JOINT:DIMENSION' THEN '螺纹中径及有效长度按图纸执行'
         WHEN 'STD-M-LOG-THREAD-JOINT:HARDNESS' THEN '螺纹硬度 HRC 30-36'
         WHEN 'STD-M-LOG-CENTRALIZER:APPEARANCE' THEN '弓臂无变形，弹簧弹性良好'
         WHEN 'STD-M-LOG-CENTRALIZER:DIMENSION' THEN '外径及弓臂间距按图纸执行'
         WHEN 'STD-M-LOG-CENTRALIZER:PRESSURE' THEN '弹性复位性能合格'
         WHEN 'STD-M-LOG-STAB-BLADE:APPEARANCE' THEN '叶片无裂纹、刃口完好'
         WHEN 'STD-M-LOG-STAB-BLADE:DIMENSION' THEN '叶片长度及安装孔位按图纸执行'
         WHEN 'STD-M-LOG-STAB-BLADE:HARDNESS' THEN '刃口硬度 HRC 45-50'
         WHEN 'STD-M-LOG-SLIP-SEAT:APPEARANCE' THEN '卡瓦槽无毛刺，表面光洁'
         WHEN 'STD-M-LOG-SLIP-SEAT:DIMENSION' THEN '槽宽及锥面角度按图纸执行'
         WHEN 'STD-M-LOG-SLIP-SEAT:HARDNESS' THEN '接触面硬度 HRC 40-45'
         WHEN 'STD-M-LOG-TOP-SUB:APPEARANCE' THEN '接头螺纹完好，密封面无损伤'
         WHEN 'STD-M-LOG-TOP-SUB:DIMENSION' THEN '接头总长及螺纹尺寸按图纸执行'
         WHEN 'STD-M-LOG-TOP-SUB:SEAL' THEN '密封面水压 2.0MPa 无渗漏'
         WHEN 'STD-M-LOG-BOP-SUB:APPEARANCE' THEN '本体无裂纹，密封槽完好'
         WHEN 'STD-M-LOG-BOP-SUB:DIMENSION' THEN '防喷接头关键尺寸按图纸执行'
         WHEN 'STD-M-LOG-BOP-SUB:SEAL' THEN '密封耐压 5.0MPa 无泄漏'
         ELSE ii.default_standard
       END,
       CASE
         WHEN ii.item_code = 'DIMENSION' THEN 10.0000
         ELSE NULL
       END,
       CASE
         WHEN ii.item_code = 'DIMENSION' THEN -0.0500
         ELSE NULL
       END,
       CASE
         WHEN ii.item_code = 'DIMENSION' THEN 0.0500
         ELSE NULL
       END,
       CASE CONCAT(s.standard_code, ':', ii.item_code)
         WHEN 'STD-M-LOG-PRESS-CYL:PRESSURE' THEN 35.0000
         WHEN 'STD-M-LOG-CONN-JOINT:HARDNESS' THEN 28.0000
         WHEN 'STD-M-LOG-THREAD-JOINT:HARDNESS' THEN 30.0000
         WHEN 'STD-M-LOG-STAB-BLADE:HARDNESS' THEN 45.0000
         WHEN 'STD-M-LOG-SLIP-SEAT:HARDNESS' THEN 40.0000
         WHEN 'STD-M-LOG-TOP-SUB:SEAL' THEN 2.0000
         WHEN 'STD-M-LOG-BOP-SUB:SEAL' THEN 5.0000
         ELSE NULL
       END,
       CASE CONCAT(s.standard_code, ':', ii.item_code)
         WHEN 'STD-M-LOG-CONN-JOINT:HARDNESS' THEN 34.0000
         WHEN 'STD-M-LOG-THREAD-JOINT:HARDNESS' THEN 36.0000
         WHEN 'STD-M-LOG-STAB-BLADE:HARDNESS' THEN 50.0000
         WHEN 'STD-M-LOG-SLIP-SEAT:HARDNESS' THEN 45.0000
         ELSE NULL
       END,
       ii.unit,
       ii.critical_flag,
       ii.sort_no
FROM qua_inspect_standard s
JOIN qua_inspect_item ii ON (
  (s.standard_code = 'STD-M-LOG-PROBE-SHELL' AND ii.item_code IN ('APPEARANCE', 'DIMENSION', 'SEAL'))
  OR (s.standard_code = 'STD-M-LOG-PRESS-CYL' AND ii.item_code IN ('APPEARANCE', 'DIMENSION', 'PRESSURE'))
  OR (s.standard_code = 'STD-M-LOG-CONN-JOINT' AND ii.item_code IN ('APPEARANCE', 'DIMENSION', 'HARDNESS'))
  OR (s.standard_code = 'STD-M-LOG-THREAD-JOINT' AND ii.item_code IN ('APPEARANCE', 'DIMENSION', 'HARDNESS'))
  OR (s.standard_code = 'STD-M-LOG-CENTRALIZER' AND ii.item_code IN ('APPEARANCE', 'DIMENSION', 'PRESSURE'))
  OR (s.standard_code = 'STD-M-LOG-STAB-BLADE' AND ii.item_code IN ('APPEARANCE', 'DIMENSION', 'HARDNESS'))
  OR (s.standard_code = 'STD-M-LOG-SLIP-SEAT' AND ii.item_code IN ('APPEARANCE', 'DIMENSION', 'HARDNESS'))
  OR (s.standard_code = 'STD-M-LOG-TOP-SUB' AND ii.item_code IN ('APPEARANCE', 'DIMENSION', 'SEAL'))
  OR (s.standard_code = 'STD-M-LOG-BOP-SUB' AND ii.item_code IN ('APPEARANCE', 'DIMENSION', 'SEAL'))
)
WHERE s.standard_code IN (
  'STD-M-LOG-PROBE-SHELL', 'STD-M-LOG-PRESS-CYL', 'STD-M-LOG-CONN-JOINT', 'STD-M-LOG-THREAD-JOINT',
  'STD-M-LOG-CENTRALIZER', 'STD-M-LOG-STAB-BLADE', 'STD-M-LOG-SLIP-SEAT', 'STD-M-LOG-TOP-SUB', 'STD-M-LOG-BOP-SUB'
);

-- 通用标准：外观 + 尺寸 + 耐压
INSERT IGNORE INTO qua_inspect_standard_line (
  standard_id, inspect_item_id, required_flag, standard_text,
  nominal, lower_tol, upper_tol, min_value, max_value, unit, critical_flag, sort_no
)
SELECT s.standard_id, ii.inspect_item_id, 1,
       COALESCE(ii.default_standard, ii.item_name),
       CASE WHEN ii.item_type = 'DIMENSION' THEN 10.0000 ELSE NULL END,
       CASE WHEN ii.item_type = 'DIMENSION' THEN -0.0500 ELSE NULL END,
       CASE WHEN ii.item_type = 'DIMENSION' THEN 0.0500 ELSE NULL END,
       CASE WHEN ii.item_code = 'PRESSURE' THEN 480 ELSE NULL END,
       NULL,
       ii.unit,
       ii.critical_flag,
       ii.sort_no
FROM qua_inspect_standard s
JOIN qua_inspect_item ii ON ii.item_code IN ('APPEARANCE', 'DIMENSION', 'PRESSURE')
WHERE s.standard_code = 'STD-GENERAL';
