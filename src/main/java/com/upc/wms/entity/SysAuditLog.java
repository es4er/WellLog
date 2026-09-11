package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_audit_log")
public class SysAuditLog {
    @TableId(type = IdType.AUTO)
    private Long auditId;
    private Long userId;
    private String operationType;
    /** 映射 DB 列 object_type（旧 DDL）*/
    private String objectType;
    private Long objectId;
    private String beforeJson;
    private String afterJson;
    private String ipAddress;
    /** 新增：User-Agent 设备标识 */
    private String userAgent;
    /** 新增：操作结果 SUCCESS / FAIL */
    private String operationResult;
    private LocalDateTime operatedAt;

    /** 非数据库字段：关联查询时的用户名 */
    @TableField(exist = false)
    private String userName;
    /** 非数据库字段：关联查询时的用户账号 */
    @TableField(exist = false)
    private String userCode;
}
