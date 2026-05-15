package com.student.mapper.evaluation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.student.entity.evaluation.EvalQueryResult;
import org.apache.ibatis.annotations.Mapper;

/**
 * 逐查询评估结果Mapper
 *
 * @author 系统
 * @version 1.0
 */
@Mapper
public interface EvalQueryResultMapper extends BaseMapper<EvalQueryResult> {
}
