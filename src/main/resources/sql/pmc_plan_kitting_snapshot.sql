-- 生产计划齐套/缺料快照（InventoryAgent 校验后回写）
-- 若列已存在会报错，可忽略后继续
ALTER TABLE pmc_production_plan
  ADD COLUMN kitting_rate INT NULL COMMENT '最近一次齐套率%' AFTER created_at,
  ADD COLUMN kitting_checked_at DATETIME NULL COMMENT '最近齐套校验时间' AFTER kitting_rate,
  ADD COLUMN shortage_analysis_json TEXT NULL COMMENT '缺料分型快照 JSON' AFTER kitting_checked_at,
  ADD COLUMN kitting_lines_json TEXT NULL COMMENT '全量齐套行快照 JSON' AFTER shortage_analysis_json;
