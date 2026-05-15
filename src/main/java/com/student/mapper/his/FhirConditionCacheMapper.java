package com.student.mapper.his;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.student.entity.his.FhirConditionCache;
import org.apache.ibatis.annotations.Mapper;

/**
 * FHIR病情缓存Mapper
 *
 * @author 系统
 * @version 1.0
 */
@Mapper
public interface FhirConditionCacheMapper extends BaseMapper<FhirConditionCache> {
}
