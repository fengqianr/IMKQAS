package com.student.mapper.his;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.student.entity.his.FhirPatientCache;
import org.apache.ibatis.annotations.Mapper;

/**
 * FHIR患者缓存Mapper
 *
 * @author 系统
 * @version 1.0
 */
@Mapper
public interface FhirPatientCacheMapper extends BaseMapper<FhirPatientCache> {
}
