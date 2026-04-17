package com.student.service.drug.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.student.entity.drug.Drug;
import com.student.entity.drug.DrugAlias;
import com.student.entity.drug.DrugInteraction;
import com.student.mapper.DrugAliasMapper;
import com.student.mapper.DrugInteractionMapper;
import com.student.mapper.DrugMapper;
import com.student.service.drug.DrugQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 药物查询服务实现类
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DrugQueryServiceImpl implements DrugQueryService {

    private final DrugMapper drugMapper;
    private final DrugAliasMapper drugAliasMapper;
    private final DrugInteractionMapper drugInteractionMapper;

    @Override
    public List<Drug> searchDrugsByName(String drugName) {
        if (!StringUtils.hasText(drugName)) {
            return new ArrayList<>();
        }

        log.debug("搜索药品: {}", drugName);

        // 使用LinkedHashSet保持插入顺序（先匹配到的优先级更高）
        Set<Long> drugIds = new LinkedHashSet<>();
        Map<Long, Drug> drugMap = new HashMap<>();

        // 1. 直接匹配通用名
        LambdaQueryWrapper<Drug> drugQuery = Wrappers.lambdaQuery(Drug.class)
                .like(Drug::getGenericName, drugName)
                .eq(Drug::getDeleted, 0);
        List<Drug> directMatches = drugMapper.selectList(drugQuery);
        directMatches.forEach(drug -> {
            drugIds.add(drug.getId());
            drugMap.put(drug.getId(), drug);
        });

        // 2. 匹配商品名
        drugQuery = Wrappers.lambdaQuery(Drug.class)
                .like(Drug::getBrandName, drugName)
                .eq(Drug::getDeleted, 0);
        List<Drug> brandMatches = drugMapper.selectList(drugQuery);
        brandMatches.forEach(drug -> {
            if (!drugIds.contains(drug.getId())) {
                drugIds.add(drug.getId());
                drugMap.put(drug.getId(), drug);
            }
        });

        // 3. 通过别名表匹配
        LambdaQueryWrapper<DrugAlias> aliasQuery = Wrappers.lambdaQuery(DrugAlias.class)
                .like(DrugAlias::getAliasName, drugName)
                .eq(DrugAlias::getDeleted, 0);
        List<DrugAlias> aliases = drugAliasMapper.selectList(aliasQuery);
        Set<Long> aliasDrugIds = new HashSet<>();
        aliases.forEach(alias -> aliasDrugIds.add(alias.getDrugId()));

        // 4. 批量查询别名匹配的药品（排除已查询的）
        if (!aliasDrugIds.isEmpty()) {
            // 移除已查询的药品ID
            aliasDrugIds.removeAll(drugIds);
            if (!aliasDrugIds.isEmpty()) {
                // 批量查询药品详情
                List<Drug> aliasDrugs = drugMapper.selectBatchIds(aliasDrugIds);
                aliasDrugs.stream()
                        .filter(drug -> drug.getDeleted() == 0)
                        .forEach(drug -> {
                            drugIds.add(drug.getId());
                            drugMap.put(drug.getId(), drug);
                        });
            }
        }

        // 5. 按匹配顺序构建结果列表
        List<Drug> result = new ArrayList<>();
        for (Long drugId : drugIds) {
            Drug drug = drugMap.get(drugId);
            if (drug != null) {
                result.add(drug);
            }
        }

        log.debug("找到 {} 个药品", result.size());
        return result;
    }

    @Override
    @Cacheable(value = "drug", key = "#drugId", unless = "#result == null")
    public Drug getDrugById(Long drugId) {
        if (drugId == null) {
            return null;
        }

        log.debug("查询药品详情: {}", drugId);
        Drug drug = drugMapper.selectById(drugId);
        if (drug == null || drug.getDeleted() == 1) {
            log.warn("药品不存在或已删除: {}", drugId);
            return null;
        }

        return drug;
    }

    @Override
    @Cacheable(value = "drug_interaction", key = "T(String).format('%d-%d', T(java.lang.Math).min(#drugAId, #drugBId), T(java.lang.Math).max(#drugAId, #drugBId))", unless = "#result == null")
    public DrugInteraction checkDrugInteraction(Long drugAId, Long drugBId) {
        if (drugAId == null || drugBId == null) {
            return null;
        }

        log.debug("检查药物相互作用: {} 和 {}", drugAId, drugBId);

        // 确保 drugAId < drugBId 以保持一致性
        Long smallerId = Math.min(drugAId, drugBId);
        Long largerId = Math.max(drugAId, drugBId);

        LambdaQueryWrapper<DrugInteraction> query = Wrappers.lambdaQuery(DrugInteraction.class)
                .eq(DrugInteraction::getDrugAId, smallerId)
                .eq(DrugInteraction::getDrugBId, largerId)
                .eq(DrugInteraction::getDeleted, 0);

        DrugInteraction interaction = drugInteractionMapper.selectOne(query);
        if (interaction == null) {
            log.debug("未找到药物相互作用: {} 和 {}", drugAId, drugBId);
        } else {
            log.debug("找到药物相互作用: {}", interaction.getInteractionType());
        }

        return interaction;
    }

    @Override
    public List<DrugInteraction> checkMultipleDrugInteractions(List<Long> drugIds) {
        if (drugIds == null || drugIds.size() < 2) {
            return new ArrayList<>();
        }

        log.debug("检查多种药物相互作用: {}", drugIds);

        List<DrugInteraction> interactions = new ArrayList<>();

        // 检查所有两两组合
        for (int i = 0; i < drugIds.size(); i++) {
            for (int j = i + 1; j < drugIds.size(); j++) {
                Long drugAId = drugIds.get(i);
                Long drugBId = drugIds.get(j);

                DrugInteraction interaction = checkDrugInteraction(drugAId, drugBId);
                if (interaction != null) {
                    interactions.add(interaction);
                }
            }
        }

        log.debug("找到 {} 个相互作用", interactions.size());
        return interactions;
    }

    @Override
    public List<String> generateDrugReminders(Long drugId, String userHealthProfile) {
        // TODO: 基于用户健康档案生成用药提醒
        // 需要解析健康档案JSON，检查过敏史、慢性病等
        // 暂时返回空列表
        log.debug("生成用药提醒: drugId={}, healthProfile={}", drugId, userHealthProfile);
        return new ArrayList<>();
    }

    @Override
    public List<Drug> suggestDrugsBySymptom(String symptom) {
        // TODO: 基于症状推荐药品
        // 需要建立症状-药品关联知识库
        // 暂时返回空列表
        log.debug("根据症状推荐药品: {}", symptom);
        return new ArrayList<>();
    }

    @Override
    public List<String> getDrugClasses() {
        log.debug("获取药品分类列表");

        LambdaQueryWrapper<Drug> query = Wrappers.lambdaQuery(Drug.class)
                .eq(Drug::getDeleted, 0)
                .isNotNull(Drug::getDrugClass);

        List<Drug> drugs = drugMapper.selectList(query);
        return drugs.stream()
                .map(Drug::getDrugClass)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<Drug> getDrugsByClass(String drugClass) {
        if (!StringUtils.hasText(drugClass)) {
            return new ArrayList<>();
        }

        log.debug("根据分类查询药品: {}", drugClass);

        LambdaQueryWrapper<Drug> query = Wrappers.lambdaQuery(Drug.class)
                .eq(Drug::getDrugClass, drugClass)
                .eq(Drug::getDeleted, 0);

        return drugMapper.selectList(query);
    }
}