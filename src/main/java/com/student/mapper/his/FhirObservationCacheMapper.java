package com.student.mapper.his;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.student.entity.his.FhirObservationCache;
import org.apache.ibatis.annotations.Mapper;

/**
 * FHIR观察缓存Mapper
 *
 * @author 系统
 * @version 1.0
 */
@Mapper
public interface FhirObservationCacheMapper extends BaseMapper<FhirObservationCache> {
}
