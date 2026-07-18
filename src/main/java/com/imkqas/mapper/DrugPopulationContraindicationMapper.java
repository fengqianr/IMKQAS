package com.imkqas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.imkqas.entity.contraindication.DrugPopulationContraindication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 药物-人群禁忌规则 Mapper 接口
 *
 * @author 系统
 * @version 1.0
 */
@Mapper
public interface DrugPopulationContraindicationMapper extends BaseMapper<DrugPopulationContraindication> {

    /** 查询所有激活的禁忌规则 */
    @Select("SELECT * FROM drug_population_contraindication WHERE deleted = 0 AND is_active = 1")
    List<DrugPopulationContraindication> selectActive();

    /** 根据药物名查询禁忌规则（用于别名扩展后的精确匹配） */
    @Select("SELECT * FROM drug_population_contraindication WHERE drug_name = #{drugName} AND deleted = 0 AND is_active = 1")
    List<DrugPopulationContraindication> selectByDrug(@Param("drugName") String drugName);

    /** 根据药物名和人群精确查询 */
    @Select("SELECT * FROM drug_population_contraindication WHERE drug_name = #{drugName} AND population_name = #{populationName} AND deleted = 0 AND is_active = 1")
    DrugPopulationContraindication selectByDrugAndPopulation(
            @Param("drugName") String drugName,
            @Param("populationName") String populationName);
}
