package com.tuowei.erp.production.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductionOrderMapper extends BaseMapper<ProductionOrderEntity> {
}
