-- 生产计划关联来源订单，修复「生成生产计划后退出界面看不到」问题
-- 适用: MySQL 8.x / wms_db
-- 请在业务库执行一次。若 source_order_id 已存在，跳过第 1 步即可。

SET NAMES utf8mb4;

-- 1) 增加 source_order_id
ALTER TABLE pmc_production_plan
    ADD COLUMN source_order_id BIGINT NULL COMMENT '来源客户订单ID' AFTER source_system_id;

-- 2) 从已有领料单回填历史计划的订单关联
UPDATE pmc_production_plan p
    INNER JOIN pmc_requisition_order r ON r.source_plan_id = p.plan_id
SET p.source_order_id = r.source_order_id
WHERE p.source_order_id IS NULL
  AND r.source_order_id IS NOT NULL;

-- 3) 演示数据：按 plan_id 与订单对应关系补齐（与 pmc_demo_data 对齐）
UPDATE pmc_production_plan SET source_order_id = 1 WHERE plan_id = 1 AND source_order_id IS NULL;
UPDATE pmc_production_plan SET source_order_id = 2 WHERE plan_id = 2 AND source_order_id IS NULL;
UPDATE pmc_production_plan SET source_order_id = 3 WHERE plan_id = 3 AND source_order_id IS NULL;
UPDATE pmc_production_plan SET source_order_id = 4 WHERE plan_id = 4 AND source_order_id IS NULL;

-- 4) 索引（若已存在可忽略报错）
CREATE INDEX idx_pmc_plan_source_order ON pmc_production_plan (source_order_id);
