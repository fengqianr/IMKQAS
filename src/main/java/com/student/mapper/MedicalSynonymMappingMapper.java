package com.student.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.student.entity.synonym.MedicalSynonymMapping;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/**
 * 医学同义词映射 Mapper
 *
 * @author 系统
 * @version 1.0
 */
@Mapper
public interface MedicalSynonymMappingMapper extends BaseMapper<MedicalSynonymMapping> {

    @Select("SELECT * FROM medical_synonym_mapping WHERE colloquial_term = #{term} AND status = 'APPROVED' AND deleted = 0 LIMIT 1")
    MedicalSynonymMapping findByColloquialTerm(@Param("term") String term);

    @Select("SELECT * FROM medical_synonym_mapping WHERE status = 'APPROVED' AND deleted = 0")
    List<MedicalSynonymMapping> findAllApproved();
}
