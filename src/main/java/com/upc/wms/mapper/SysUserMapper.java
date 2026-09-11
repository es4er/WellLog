package com.upc.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.upc.wms.entity.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT * FROM sys_user WHERE user_code=#{userCode} AND deleted_flag=0")
    SysUser selectByUserCode(@Param("userCode") String userCode);
}
