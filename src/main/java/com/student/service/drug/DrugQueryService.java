package com.student.service.drug;

import com.student.entity.drug.Drug;
import com.student.entity.drug.DrugInteraction;

import java.util.List;

/**
 * 药物查询服务接口
 * 提供药品信息查询、药物相互作用检查、用药提醒等功能
 *
 * @author 系统
 * @version 1.0
 */
public interface DrugQueryService {

    /**
     * 根据药品名称查询药品信息
     * @param drugName 药品名称（通用名、商品名、别名等）
     * @return 药品信息列表
     */
    List<Drug> searchDrugsByName(String drugName);

    /**
     * 根据药品ID查询药品详细信息
     * @param drugId 药品ID
     * @return 药品信息，如果不存在返回null
     */
    Drug getDrugById(Long drugId);

    /**
     * 检查两种药品之间的相互作用
     * @param drugAId 药品A ID
     * @param drugBId 药品B ID
     * @return 相互作用信息，如果不存在相互作用返回null
     */
    DrugInteraction checkDrugInteraction(Long drugAId, Long drugBId);

    /**
     * 检查多种药品之间的相互作用
     * @param drugIds 药品ID列表
     * @return 相互作用信息列表
     */
    List<DrugInteraction> checkMultipleDrugInteractions(List<Long> drugIds);

    /**
     * 根据用户健康档案生成用药提醒
     * @param drugId 药品ID
     * @param userHealthProfile 用户健康档案JSON字符串
     * @return 用药提醒信息列表
     */
    List<String> generateDrugReminders(Long drugId, String userHealthProfile);

    /**
     * 根据症状查询推荐药品
     * @param symptom 症状描述
     * @return 推荐药品列表
     */
    List<Drug> suggestDrugsBySymptom(String symptom);

    /**
     * 获取药品分类列表
     * @return 药品分类列表
     */
    List<String> getDrugClasses();

    /**
     * 根据分类查询药品
     * @param drugClass 药品分类
     * @return 该分类下的药品列表
     */
    List<Drug> getDrugsByClass(String drugClass);
}