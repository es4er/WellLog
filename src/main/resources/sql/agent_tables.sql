-- =============================================================
-- 测井装备 WMS 多智能体协作 - 数据库表
-- 在原有业务表基础上新增，用于记录智能体任务、执行步骤与过程日志。
-- 适用: MySQL 8.x
-- =============================================================

-- 智能体主任务表
CREATE TABLE IF NOT EXISTS agent_task (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    task_no       VARCHAR(64)  NOT NULL COMMENT '智能体任务编号，如 AT202607070001',
    task_type     VARCHAR(64)  NOT NULL COMMENT '任务类型，如 RECEIPT_INSPECTION_INBOUND',
    business_no   VARCHAR(64)           COMMENT '关联业务单号',
    task_name     VARCHAR(128)          COMMENT '任务名称',
    status        VARCHAR(32)  NOT NULL COMMENT '任务状态 WAITING/RUNNING/SUCCESS/FAILED/MANUAL_REQUIRED',
    current_agent VARCHAR(64)           COMMENT '当前执行到的智能体',
    created_by    BIGINT                COMMENT '发起人',
    start_time    DATETIME              COMMENT '开始时间',
    end_time      DATETIME              COMMENT '结束时间',
    error_message VARCHAR(500)          COMMENT '失败原因',
    created_at    DATETIME              COMMENT '创建时间',
    updated_at    DATETIME              COMMENT '更新时间',
    KEY idx_agent_task_no (task_no),
    KEY idx_agent_task_business (business_no),
    KEY idx_agent_task_type (task_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体任务主表';

-- 智能体任务步骤表
CREATE TABLE IF NOT EXISTS agent_task_step (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    task_id       BIGINT       NOT NULL COMMENT '所属智能体任务',
    step_no       INT          NOT NULL COMMENT '步骤顺序',
    agent_name    VARCHAR(64)  NOT NULL COMMENT '执行该步骤的智能体名称',
    step_name     VARCHAR(128)          COMMENT '步骤名称',
    status        VARCHAR(32)  NOT NULL COMMENT '步骤状态 RUNNING/SUCCESS/FAILED/SKIPPED/MANUAL_REQUIRED',
    input_data    TEXT                  COMMENT '输入参数 JSON',
    output_data   TEXT                  COMMENT '输出结果 JSON',
    next_agent    VARCHAR(64)           COMMENT '下一步智能体',
    start_time    DATETIME              COMMENT '开始时间',
    end_time      DATETIME              COMMENT '结束时间',
    duration_ms   BIGINT                COMMENT '执行耗时(毫秒)',
    error_message VARCHAR(500)          COMMENT '失败原因',
    created_at    DATETIME              COMMENT '创建时间',
    updated_at    DATETIME              COMMENT '更新时间',
    KEY idx_agent_step_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体任务步骤表';

-- 智能体执行日志表
CREATE TABLE IF NOT EXISTS agent_execution_log (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    task_id     BIGINT       NOT NULL COMMENT '所属智能体任务',
    step_id     BIGINT                COMMENT '所属步骤',
    agent_name  VARCHAR(64)           COMMENT '智能体名称',
    log_type    VARCHAR(32)           COMMENT '日志类型 INFO/WARN/ERROR/DECISION/SERVICE_CALL',
    content     VARCHAR(1000)         COMMENT '日志内容',
    created_at  DATETIME              COMMENT '创建时间',
    KEY idx_agent_log_task (task_id),
    KEY idx_agent_log_step (step_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体执行日志表';

-- 智能体状态快照表(用于前端展示每个 Agent 当前状态)
CREATE TABLE IF NOT EXISTS agent_status_snapshot (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    agent_name       VARCHAR(64)  NOT NULL COMMENT '智能体名称',
    agent_label      VARCHAR(64)           COMMENT '智能体中文名称',
    status           VARCHAR(32)  NOT NULL COMMENT '状态 IDLE/RUNNING/SUCCESS/FAILED/WAITING',
    current_task_id  BIGINT                COMMENT '当前任务',
    total_task_count INT DEFAULT 0         COMMENT '累计任务数',
    success_count    INT DEFAULT 0         COMMENT '成功数',
    failed_count     INT DEFAULT 0         COMMENT '失败数',
    avg_duration_ms  BIGINT DEFAULT 0      COMMENT '平均耗时(毫秒)',
    last_active_time DATETIME              COMMENT '最近活跃时间',
    updated_at       DATETIME              COMMENT '更新时间',
    UNIQUE KEY uk_agent_status_name (agent_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体状态快照表';
