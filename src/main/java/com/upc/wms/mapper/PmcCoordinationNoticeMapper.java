package com.upc.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.upc.wms.entity.PmcCoordinationNotice;
import org.apache.ibatis.annotations.Update;

public interface PmcCoordinationNoticeMapper extends BaseMapper<PmcCoordinationNotice> {

    /**
     * 启动时自建表，避免依赖手工执行 SQL 脚本。
     */
    @Update("""
            CREATE TABLE IF NOT EXISTS pmc_coordination_notice (
                notice_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                notice_type VARCHAR(32) NOT NULL,
                title VARCHAR(200) NOT NULL,
                content VARCHAR(1000) NULL,
                plan_id BIGINT NULL,
                plan_no VARCHAR(64) NULL,
                requisition_id BIGINT NULL,
                outbound_no VARCHAR(64) NULL,
                exception_title VARCHAR(200) NULL,
                target_role VARCHAR(32) NOT NULL DEFAULT 'WAREHOUSE',
                status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
                created_by BIGINT NULL,
                created_by_name VARCHAR(64) NULL,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                read_at DATETIME NULL,
                handled_at DATETIME NULL,
                handled_by VARCHAR(64) NULL,
                INDEX idx_target_status (target_role, status),
                INDEX idx_plan (plan_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """)
    void ensureTable();
}
