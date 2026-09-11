package com.upc.wms.service;

import com.upc.wms.dto.PageResult;
import com.upc.wms.entity.SysAuditLog;

/**
 * 审计查询服务：登录审计 & 权限审计的分页查询。
 */
public interface AuditService {

    /**
     * 分页查询登录审计记录
     * @param page     页码（从1开始）
     * @param size     每页条数
     * @param result   筛选操作结果：SUCCESS / FAIL，传 null 或空串表示全部
     * @param risk     筛选风险等级：NORMAL / ABNORMAL，传 null 或空串表示全部
     */
    PageResult<SysAuditLog> listLoginAudit(int page, int size, String result, String risk);

    /**
     * 分页查询权限审计记录（业务类型为 PERMISSION / ROLE / USER 的操作）
     * @param page          页码（从1开始）
     * @param size          每页条数
     * @param operationType 筛选操作类型，传 null 或空串表示全部
     * @param result        筛选操作结果，传 null 或空串表示全部
     */
    PageResult<SysAuditLog> listPermissionAudit(int page, int size, String operationType, String result);
}
