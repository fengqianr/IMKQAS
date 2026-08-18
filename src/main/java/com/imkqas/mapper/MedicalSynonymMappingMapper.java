package com.imkqas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.imkqas.entity.synonym.MedicalSynonymMapping;
import org.apache.ibatis.annotations.Delete;
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

    @Select("SELECT * FROM medical_synonym_mapping WHERE colloquial_term = #{term} AND status = 'APPROVED' LIMIT 1")
    MedicalSynonymMapping findByColloquialTerm(@Param("term") String term);

    @Select("SELECT * FROM medical_synonym_mapping WHERE status = 'APPROVED'")
    List<MedicalSynonymMapping> findAllApproved();

    @Select("SELECT * FROM medical_synonym_mapping WHERE standard_term = #{standardTerm}")
    List<MedicalSynonymMapping> findByStandardTerm(@Param("standardTerm") String standardTerm);

    @Delete("DELETE FROM medical_synonym_mapping WHERE standard_term = #{standardTerm}")
    int deleteByStandardTerm(@Param("standardTerm") String standardTerm);

    @Select("SELECT COUNT(*) FROM medical_synonym_mapping WHERE colloquial_term = #{term} AND status = 'APPROVED'")
    int existsByColloquialTerm(@Param("term") String term);
}
