package com.student.service.drug.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.student.entity.drug.Drug;
import com.student.entity.drug.DrugAlias;
import com.student.entity.drug.DrugInteraction;
import com.student.mapper.DrugAliasMapper;
import com.student.mapper.DrugInteractionMapper;
import com.student.mapper.DrugMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * 药物查询服务单元测试
 *
 * @author 系统
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
class DrugQueryServiceImplTest {

    @Mock
    private DrugMapper drugMapper;

    @Mock
    private DrugAliasMapper drugAliasMapper;

    @Mock
    private DrugInteractionMapper drugInteractionMapper;

    @InjectMocks
    private DrugQueryServiceImpl drugQueryService;

    private Drug testDrug1;
    private Drug testDrug2;
    private DrugAlias testAlias;
    private DrugInteraction testInteraction;

    @BeforeEach
    void setUp() {
        // 创建测试药品1: 阿司匹林
        testDrug1 = Drug.builder()
                .id(1L)
                .genericName("阿司匹林")
                .brandName("拜阿司匹灵")
                .drugClass("非甾体抗炎药")
                .dosageForm("肠溶片")
                .specification("100mg*30片")
                .manufacturer("拜耳医药保健有限公司")
                .indications("[\"缓解轻度或中度疼痛\",\"用于感冒、流感等发热疾病的退热\"]")
                .contraindications("[\"对阿司匹林或其他非甾体抗炎药过敏者禁用\"]")
                .adverseReactions("[\"胃肠道反应\",\"过敏反应\"]")
                .dosage("成人常用量：一次0.3～0.6g，一日3次")
                .precautions("应与食物同服或用水冲服")
                .storage("密封，在干燥处保存")
                .approvalNumber("国药准字H20065051")
                .hasInteractions(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .deleted(0)
                .build();

        // 创建测试药品2: 青霉素
        testDrug2 = Drug.builder()
                .id(2L)
                .genericName("青霉素")
                .brandName("青霉素钠")
                .drugClass("β-内酰胺类抗生素")
                .dosageForm("注射用粉针")
                .specification("80万单位*10支")
                .manufacturer("华北制药股份有限公司")
                .indications("[\"敏感菌所致感染\"]")
                .contraindications("[\"对青霉素类药物过敏者禁用\"]")
                .adverseReactions("[\"过敏反应\"]")
                .dosage("肌内注射：成人一日80万～200万单位")
                .precautions("用药前必须做青霉素皮肤试验")
                .storage("密闭，在干燥处保存")
                .approvalNumber("国药准字H13020665")
                .hasInteractions(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .deleted(0)
                .build();

        // 创建测试别名
        testAlias = DrugAlias.builder()
                .id(1L)
                .drugId(1L)
                .aliasType(DrugAlias.AliasType.BRAND_NAME)
                .aliasName("拜阿司匹灵")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .deleted(0)
                .build();

        // 创建测试相互作用
        testInteraction = DrugInteraction.builder()
                .id(1L)
                .drugAId(1L)
                .drugBId(2L)
                .interactionType(DrugInteraction.InteractionType.SEVERE)
                .severity(DrugInteraction.Severity.HIGH)
                .description("阿司匹林可增强华法林的抗凝作用，显著增加出血风险")
                .mechanism("阿司匹林抑制血小板聚集，与华法林协同增强抗凝效果")
                .recommendation("避免联合使用")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .deleted(0)
                .build();
    }

    @Test
    void testSearchDrugsByName_GenericNameMatch() {
        // 准备
        String searchName = "阿司匹林";
        List<Drug> drugList = new ArrayList<>();
        drugList.add(testDrug1);

        // 模拟调用顺序：第一次通用名查询返回结果，第二次商品名查询返回空列表
        when(drugMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(drugList)          // 第一次调用：通用名查询
                .thenReturn(new ArrayList<>()); // 第二次调用：商品名查询

        // 模拟别名查询返回空列表
        when(drugAliasMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(new ArrayList<>());

        // 执行
        List<Drug> result = drugQueryService.searchDrugsByName(searchName);

        // 验证
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("阿司匹林", result.get(0).getGenericName());
    }

    @Test
    void testSearchDrugsByName_BrandNameMatch() {
        // 准备
        String searchName = "拜阿司匹灵";
        List<Drug> drugList = new ArrayList<>();
        drugList.add(testDrug1);

        // 模拟调用顺序：第一次通用名查询返回空列表，第二次商品名查询返回结果
        when(drugMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(new ArrayList<>())  // 第一次调用：通用名查询
                .thenReturn(drugList);          // 第二次调用：商品名查询

        // 模拟别名查询返回空列表
        when(drugAliasMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(new ArrayList<>());

        // 执行
        List<Drug> result = drugQueryService.searchDrugsByName(searchName);

        // 验证
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("拜阿司匹灵", result.get(0).getBrandName());
    }

    @Test
    void testSearchDrugsByName_AliasMatch() {
        // 准备
        String searchName = "乙酰水杨酸"; // 阿司匹林的别名
        List<DrugAlias> aliasList = new ArrayList<>();
        aliasList.add(testAlias);

        // 模拟通用名和商品名查询返回空列表
        when(drugMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(new ArrayList<>())  // 通用名查询
                .thenReturn(new ArrayList<>()); // 商品名查询

        // 模拟别名查询返回结果
        when(drugAliasMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(aliasList);
        // 模拟批量查询药品 - 注意：别名匹配的药品ID为1L
        List<Drug> batchResult = new ArrayList<>();
        batchResult.add(testDrug1);
        when(drugMapper.selectBatchIds(any(Collection.class))).thenReturn(batchResult);

        // 执行
        List<Drug> result = drugQueryService.searchDrugsByName(searchName);

        // 验证
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    @Test
    void testSearchDrugsByName_EmptyName() {
        // 执行
        List<Drug> result = drugQueryService.searchDrugsByName("");

        // 验证
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetDrugById_Success() {
        // 准备
        Long drugId = 1L;
        when(drugMapper.selectById(drugId)).thenReturn(testDrug1);

        // 执行
        Drug result = drugQueryService.getDrugById(drugId);

        // 验证
        assertNotNull(result);
        assertEquals(drugId, result.getId());
        assertEquals("阿司匹林", result.getGenericName());
    }

    @Test
    void testGetDrugById_NotFound() {
        // 准备
        Long drugId = 999L;
        when(drugMapper.selectById(drugId)).thenReturn(null);

        // 执行
        Drug result = drugQueryService.getDrugById(drugId);

        // 验证
        assertNull(result);
    }

    @Test
    void testGetDrugById_Deleted() {
        // 准备
        Long drugId = 1L;
        Drug deletedDrug = Drug.builder()
                .id(1L)
                .genericName("阿司匹林")
                .deleted(1)
                .build();
        when(drugMapper.selectById(drugId)).thenReturn(deletedDrug);

        // 执行
        Drug result = drugQueryService.getDrugById(drugId);

        // 验证
        assertNull(result);
    }

    @Test
    void testCheckDrugInteraction_Success() {
        // 准备
        Long drugAId = 1L;
        Long drugBId = 2L;
        when(drugInteractionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testInteraction);

        // 执行
        DrugInteraction result = drugQueryService.checkDrugInteraction(drugAId, drugBId);

        // 验证
        assertNotNull(result);
        assertEquals(DrugInteraction.InteractionType.SEVERE, result.getInteractionType());
        assertEquals(DrugInteraction.Severity.HIGH, result.getSeverity());
    }

    @Test
    void testCheckDrugInteraction_NotFound() {
        // 准备
        Long drugAId = 1L;
        Long drugBId = 3L;
        when(drugInteractionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // 执行
        DrugInteraction result = drugQueryService.checkDrugInteraction(drugAId, drugBId);

        // 验证
        assertNull(result);
    }

    @Test
    void testCheckMultipleDrugInteractions() {
        // 准备
        List<Long> drugIds = Arrays.asList(1L, 2L, 3L);
        // 模拟三次调用：第一次(1,2)返回相互作用，第二次(1,3)返回null，第三次(2,3)返回null
        when(drugInteractionMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(testInteraction)  // 第一次调用：(1,2)
                .thenReturn(null)             // 第二次调用：(1,3)
                .thenReturn(null);            // 第三次调用：(2,3)

        // 执行
        List<DrugInteraction> result = drugQueryService.checkMultipleDrugInteractions(drugIds);

        // 验证
        assertNotNull(result);
        // 应该检查3种组合：(1,2), (1,3), (2,3)
        // 只有(1,2)有相互作用
        assertEquals(1, result.size());
    }

    @Test
    void testGetDrugClasses() {
        // 准备
        List<Drug> drugList = new ArrayList<>();
        drugList.add(testDrug1);
        drugList.add(testDrug2);

        when(drugMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(drugList);

        // 执行
        List<String> result = drugQueryService.getDrugClasses();

        // 验证
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains("非甾体抗炎药"));
        assertTrue(result.contains("β-内酰胺类抗生素"));
    }

    @Test
    void testGetDrugsByClass() {
        // 准备
        String drugClass = "非甾体抗炎药";
        List<Drug> drugList = new ArrayList<>();
        drugList.add(testDrug1);

        when(drugMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(drugList);

        // 执行
        List<Drug> result = drugQueryService.getDrugsByClass(drugClass);

        // 验证
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("阿司匹林", result.get(0).getGenericName());
    }

    @Test
    void testGenerateDrugReminders() {
        // 准备
        Long drugId = 1L;
        String healthProfile = "{\"allergies\": [\"青霉素\"], \"chronic_diseases\": [\"高血压\"]}";

        // 执行
        List<String> result = drugQueryService.generateDrugReminders(drugId, healthProfile);

        // 验证
        assertNotNull(result);
        // 目前是空实现，返回空列表
        assertTrue(result.isEmpty());
    }

    @Test
    void testSuggestDrugsBySymptom() {
        // 准备
        String symptom = "头痛";

        // 执行
        List<Drug> result = drugQueryService.suggestDrugsBySymptom(symptom);

        // 验证
        assertNotNull(result);
        // 目前是空实现，返回空列表
        assertTrue(result.isEmpty());
    }
}