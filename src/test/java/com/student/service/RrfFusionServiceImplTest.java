package com.student.service;

import com.student.config.RagConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RRF融合服务单元测试
 * 测试RrfFusionServiceImpl的融合算法
 *
 * @author 系统
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RrfFusionServiceImplTest {

    @Mock
    private RagConfig ragConfig;

    @Mock
    private RagConfig.RetrievalConfig retrievalConfig;

    @InjectMocks
    private RrfFusionServiceImpl rrfFusionService;

    @BeforeEach
    void setUp() {
        // 模拟配置
        when(ragConfig.getRetrieval()).thenReturn(retrievalConfig);
        when(retrievalConfig.getRrfK()).thenReturn(60);

        // 手动调用@PostConstruct初始化方法
        rrfFusionService.init();
    }

    @Test
    void testFuse_EmptyInput() {
        // 准备
        List<List<MultiRetrievalService.RetrievalResult>> emptyList = new ArrayList<>();

        // 执行
        List<MultiRetrievalService.RetrievalResult> result = rrfFusionService.fuse(emptyList, null, 10);

        // 验证
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFuse_SingleList() {
        // 准备
        List<MultiRetrievalService.RetrievalResult> singleList = Arrays.asList(
                createRetrievalResult(1L, 1L, 0.9, "内容1", MultiRetrievalService.RetrievalSource.VECTOR, 0.9, 0.0),
                createRetrievalResult(2L, 1L, 0.8, "内容2", MultiRetrievalService.RetrievalSource.VECTOR, 0.8, 0.0),
                createRetrievalResult(3L, 1L, 0.7, "内容3", MultiRetrievalService.RetrievalSource.VECTOR, 0.7, 0.0)
        );

        List<List<MultiRetrievalService.RetrievalResult>> resultsList = Arrays.asList(singleList);

        // 执行
        List<MultiRetrievalService.RetrievalResult> fused = rrfFusionService.fuse(resultsList, null, 5);

        // 验证
        assertNotNull(fused);
        assertEquals(3, fused.size());
        // 单列表融合应该保持原始顺序
        assertEquals(1L, fused.get(0).getChunkId());
        assertEquals(2L, fused.get(1).getChunkId());
        assertEquals(3L, fused.get(2).getChunkId());
    }

    @Test
    void testFuse_TwoLists() {
        // 准备：两个列表，有重叠结果
        List<MultiRetrievalService.RetrievalResult> list1 = Arrays.asList(
                createRetrievalResult(1L, 1L, 0.9, "内容1", MultiRetrievalService.RetrievalSource.VECTOR, 0.9, 0.0),
                createRetrievalResult(2L, 1L, 0.8, "内容2", MultiRetrievalService.RetrievalSource.VECTOR, 0.8, 0.0),
                createRetrievalResult(3L, 1L, 0.7, "内容3", MultiRetrievalService.RetrievalSource.VECTOR, 0.7, 0.0)
        );

        List<MultiRetrievalService.RetrievalResult> list2 = Arrays.asList(
                createRetrievalResult(2L, 1L, 0.95, "内容2", MultiRetrievalService.RetrievalSource.KEYWORD, 0.0, 0.95),
                createRetrievalResult(3L, 1L, 0.85, "内容3", MultiRetrievalService.RetrievalSource.KEYWORD, 0.0, 0.85),
                createRetrievalResult(4L, 1L, 0.75, "内容4", MultiRetrievalService.RetrievalSource.KEYWORD, 0.0, 0.75)
        );

        List<List<MultiRetrievalService.RetrievalResult>> resultsList = Arrays.asList(list1, list2);

        // 执行：等权重融合
        List<MultiRetrievalService.RetrievalResult> fused = rrfFusionService.fuse(resultsList, null, 5);

        // 验证
        assertNotNull(fused);
        // 应该有4个唯一结果（1,2,3,4）
        assertEquals(4, fused.size());

        // 在RRF融合中，同时出现在两个列表的结果应该排名更高
        // 结果2和3出现在两个列表中，应该排名靠前
        // 验证结果按RRF分数降序排列
        // 由于RRF算法，同时出现在两个列表的结果分数更高
    }

    @Test
    void testFuse_WithWeights() {
        // 准备
        List<MultiRetrievalService.RetrievalResult> list1 = Arrays.asList(
                createRetrievalResult(1L, 1L, 0.9, "内容1", MultiRetrievalService.RetrievalSource.VECTOR, 0.9, 0.0)
        );

        List<MultiRetrievalService.RetrievalResult> list2 = Arrays.asList(
                createRetrievalResult(2L, 1L, 0.8, "内容2", MultiRetrievalService.RetrievalSource.KEYWORD, 0.0, 0.8)
        );

        List<List<MultiRetrievalService.RetrievalResult>> resultsList = Arrays.asList(list1, list2);
        List<Double> weights = Arrays.asList(0.7, 0.3); // 列表1权重0.7，列表2权重0.3

        // 执行
        List<MultiRetrievalService.RetrievalResult> fused = rrfFusionService.fuse(resultsList, weights, 5);

        // 验证
        assertNotNull(fused);
        assertEquals(2, fused.size());
        // 权重应该影响结果排序
    }

    @Test
    void testFuseVectorAndKeyword() {
        // 准备
        List<MultiRetrievalService.RetrievalResult> vectorResults = Arrays.asList(
                createRetrievalResult(1L, 1L, 0.9, "向量结果1", MultiRetrievalService.RetrievalSource.VECTOR, 0.9, 0.0),
                createRetrievalResult(2L, 1L, 0.8, "向量结果2", MultiRetrievalService.RetrievalSource.VECTOR, 0.8, 0.0)
        );

        List<MultiRetrievalService.RetrievalResult> keywordResults = Arrays.asList(
                createRetrievalResult(2L, 1L, 0.85, "关键词结果2", MultiRetrievalService.RetrievalSource.KEYWORD, 0.0, 0.85),
                createRetrievalResult(3L, 1L, 0.75, "关键词结果3", MultiRetrievalService.RetrievalSource.KEYWORD, 0.0, 0.75)
        );

        // 执行
        List<MultiRetrievalService.RetrievalResult> fused = rrfFusionService.fuseVectorAndKeyword(
                vectorResults, keywordResults, 0.6, 0.4, 5
        );

        // 验证
        assertNotNull(fused);
        // 应该有3个唯一结果（1,2,3）
        assertEquals(3, fused.size());
        // 结果2同时出现在两个列表中，应该排名更高
    }

    @Test
    void testGetDefaultRrfK() {
        // 执行
        int defaultK = rrfFusionService.getDefaultRrfK();

        // 验证
        assertEquals(60, defaultK);
    }

    @Test
    void testSetRrfK() {
        // 执行
        rrfFusionService.setRrfK(100);

        // 验证：通过再次获取验证
        int newK = rrfFusionService.getDefaultRrfK();
        assertEquals(100, newK);
    }

    @Test
    void testGetStats() {
        // 先执行一些融合操作以生成统计信息
        List<MultiRetrievalService.RetrievalResult> list = Arrays.asList(
                createRetrievalResult(1L, 1L, 0.9, "内容", MultiRetrievalService.RetrievalSource.VECTOR, 0.9, 0.0)
        );
        List<List<MultiRetrievalService.RetrievalResult>> resultsList = Arrays.asList(list);

        rrfFusionService.fuse(resultsList, null, 5);

        // 执行
        RrfFusionService.FusionStats stats = rrfFusionService.getStats();

        // 验证
        assertNotNull(stats);
        assertEquals(1, stats.getTotalFusions());
        assertTrue(stats.getAverageInputResults() > 0);
        assertTrue(stats.getAverageOutputResults() > 0);
        assertTrue(stats.getAverageProcessingTimeMs() >= 0);
    }

    /**
     * 创建检索结果对象（辅助方法）
     */
    private MultiRetrievalService.RetrievalResult createRetrievalResult(
            Long chunkId, Long documentId, Double score, String content,
            MultiRetrievalService.RetrievalSource source, Double vectorScore, Double keywordScore) {
        return new MultiRetrievalService.RetrievalResult(
                chunkId, documentId, score, content, source, vectorScore, keywordScore
        );
    }
}