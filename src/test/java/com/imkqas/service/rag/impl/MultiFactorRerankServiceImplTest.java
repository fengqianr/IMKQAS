package com.imkqas.service.rag.impl;

import com.imkqas.config.RagConfig;
import com.imkqas.service.rag.CrossEncoderRerankService;
import com.imkqas.service.rag.MultiRetrievalService;
import com.imkqas.service.rag.SynonymExpansionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 多因子重排序服务单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MultiFactorRerankServiceImplTest {

    @Mock
    private CrossEncoderRerankService crossEncoderRerankService;

    @Mock
    private RagConfig ragConfig;

    @Mock
    private RagConfig.MultiFactorRerankConfig multiFactorRerankConfig;

    @Mock
    private RagConfig.MultiFactorRerankConfig.FactorWeights factorWeights;

    @Mock
    private SynonymExpansionService synonymExpansionService;

    @InjectMocks
    private MultiFactorRerankServiceImpl rerankService;

    @BeforeEach
    void setUp() {
        when(ragConfig.getMultiFactorRerank()).thenReturn(multiFactorRerankConfig);
        when(multiFactorRerankConfig.getWeights()).thenReturn(factorWeights);
        when(factorWeights.getAuthority()).thenReturn(0.27);
        when(factorWeights.getTimeliness()).thenReturn(0.13);
        when(factorWeights.getSemantic()).thenReturn(0.32);
        when(factorWeights.getIntent()).thenReturn(0.18);
        when(factorWeights.getHierarchy()).thenReturn(0.10);
    }

    private MultiRetrievalService.RetrievalResult makeResult(Map<String, Object> metadata) {
        return new MultiRetrievalService.RetrievalResult(
                1L, 100L, 0.8, "测试内容，包含高血压相关信息",
                MultiRetrievalService.RetrievalSource.HYBRID,
                0.7, 0.6, metadata
        );
    }

    @Test
    void testHierarchyRelevance_SectionTitleExactMatch() {
        when(multiFactorRerankConfig.isHierarchyEnabled()).thenReturn(true);
        Map<String, Object> metadata = Map.of(
                "section_title", "高血压治疗指南",
                "breadcrumb", "医学 > 心脏病学"
        );
        MultiRetrievalService.RetrievalResult result = makeResult(metadata);

        double score = rerankService.calculateHierarchyRelevance("高血压治疗指南", result);
        assertEquals(1.0, score, 0.01);
    }

    @Test
    void testHierarchyRelevance_SynonymMatch() {
        when(multiFactorRerankConfig.isHierarchyEnabled()).thenReturn(true);
        Map<String, Object> metadata = Map.of(
                "section_title", "高血压治疗方案",
                "breadcrumb", "医学"
        );
        MultiRetrievalService.RetrievalResult result = makeResult(metadata);

        SynonymExpansionService.TermMapping mapping = new SynonymExpansionService.TermMapping(
                "血压高", "高血压", "C001", "LOCAL", 1.0);
        when(synonymExpansionService.lookupTerm("血压高")).thenReturn(mapping);

        double score = rerankService.calculateHierarchyRelevance("血压高", result);
        assertEquals(0.9, score, 0.01);
    }

    @Test
    void testHierarchyRelevance_BigramOverlap() {
        when(multiFactorRerankConfig.isHierarchyEnabled()).thenReturn(true);
        Map<String, Object> metadata = Map.of(
                "breadcrumb", "第3章 > 高血压诊断与治疗"
        );
        MultiRetrievalService.RetrievalResult result = makeResult(metadata);

        double score = rerankService.calculateHierarchyRelevance("高血压诊断", result);
        assertTrue(score > 0.0 && score <= 0.8,
                "bigram重叠分应在(0, 0.8]范围内，实际: " + score);
    }

    @Test
    void testHierarchyRelevance_NoMetadata() {
        when(multiFactorRerankConfig.isHierarchyEnabled()).thenReturn(true);
        MultiRetrievalService.RetrievalResult result = makeResult(Collections.emptyMap());

        double score = rerankService.calculateHierarchyRelevance("任意查询", result);
        assertEquals(0.5, score, 0.01);
    }

    @Test
    void testHierarchyRelevance_NullQuery() {
        when(multiFactorRerankConfig.isHierarchyEnabled()).thenReturn(true);
        Map<String, Object> metadata = Map.of("section_title", "高血压");
        MultiRetrievalService.RetrievalResult result = makeResult(metadata);

        double score = rerankService.calculateHierarchyRelevance(null, result);
        assertEquals(0.5, score, 0.01);
    }

    @Test
    void testCalculateFinalScore_WithHierarchyEnabled() {
        when(multiFactorRerankConfig.isHierarchyEnabled()).thenReturn(true);
        Map<String, Object> metadata = Map.of(
                "section_title", "高血压治疗指南",
                "breadcrumb", "医学 > 心脏病学 > 高血压"
        );
        MultiRetrievalService.RetrievalResult result = makeResult(metadata);

        double score = rerankService.calculateFinalScore("高血压治疗指南", result, 0.8);
        assertTrue(score >= 0.0 && score <= 1.0,
                "最终分数应在[0,1]范围内，实际: " + score);
    }

    @Test
    void testCalculateFinalScore_WithHierarchyDisabled() {
        when(multiFactorRerankConfig.isHierarchyEnabled()).thenReturn(false);
        Map<String, Object> metadata = Map.of(
                "section_title", "高血压治疗指南",
                "breadcrumb", "医学 > 心脏病学"
        );
        MultiRetrievalService.RetrievalResult result = makeResult(metadata);

        double score = rerankService.calculateFinalScore("高血压治疗指南", result, 0.8);
        assertTrue(score >= 0.0 && score <= 1.0,
                "禁用层级时最终分数应在[0,1]范围内，实际: " + score);
    }
}
