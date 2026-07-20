package com.tuowei.erp.system.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tuowei.erp.system.log.model.OperationLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLogEntity> {
}
