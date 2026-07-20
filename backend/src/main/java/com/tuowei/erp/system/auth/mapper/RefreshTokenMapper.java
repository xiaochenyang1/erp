package com.tuowei.erp.system.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tuowei.erp.system.auth.model.RefreshTokenEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshTokenEntity> {
}
