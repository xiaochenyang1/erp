package com.tuowei.erp.common.idempotency.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tuowei.erp.common.idempotency.IdempotencyRequestEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IdempotencyRequestMapper extends BaseMapper<IdempotencyRequestEntity> {
}
