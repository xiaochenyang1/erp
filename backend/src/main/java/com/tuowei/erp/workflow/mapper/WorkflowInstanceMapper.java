package com.tuowei.erp.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tuowei.erp.workflow.model.WorkflowInstanceEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowInstanceMapper extends BaseMapper<WorkflowInstanceEntity> {
}
