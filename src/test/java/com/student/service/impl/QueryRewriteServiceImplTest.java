package com.student.service.impl;

import com.student.service.rag.QueryRewriteService;
import com.student.service.rag.impl.QueryRewriteServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 查询改写服务单元测试
 * 测试QueryRewriteServiceImpl的各种方法
 *
 * @author 系统
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
class QueryRewriteServiceImplTest {

    private QueryRewriteServiceImpl queryRewriteService;

    @BeforeEach
    void setUp() {
        queryRewriteService = new QueryRewriteServiceImpl();
    }

    @Test
    void testRewrite_SimpleQuery() {
        // 准备
        String originalQuery = "高血压有什么症状";
        Long userId = 1L;
        Long conversationId = 100L;

        // 执行
        String rewritten = queryRewriteService.rewrite(originalQuery, userId, conversationId);

        // 验证
        assertNotNull(rewritten);
        assertTrue(rewritten.contains("高血压"));
        // 简化后应保留核心医学术语
        assertTrue(rewritten.contains("高血压"));
        assertTrue(rewritten.contains("症状"));
    }

    @Test
    void testRewrite_WithStopWords() {
        // 准备
        String originalQuery = "请问高血压有什么症状吗";
        Long userId = 1L;
        Long conversationId = 100L;

        // 执行
        String rewritten = queryRewriteService.rewrite(originalQuery, userId, conversationId);

        // 验证
        assertNotNull(rewritten);
        // 停用词应该被移除或简化
        assertFalse(rewritten.contains("请问"));
        assertFalse(rewritten.contains("吗"));
        assertTrue(rewritten.contains("高血压"));
    }

    @Test
    void testRewrite_WithSpellingError() {
        // 准备
        String originalQuery = "感冐怎么治疗";
        Long userId = 1L;
        Long conversationId = 100L;

        // 执行
        String rewritten = queryRewriteService.rewrite(originalQuery, userId, conversationId);

        // 验证
        assertNotNull(rewritten);
        // 拼写应该被纠正
        assertTrue(rewritten.contains("感冒"));
        assertFalse(rewritten.contains("感冐"));
    }

    @Test
    void testRewrite_Medicalize() {
        // 准备
        String originalQuery = "脑袋疼是怎么回事";
        Long userId = 1L;
        Long conversationId = 100L;

        // 执行
        String rewritten = queryRewriteService.rewrite(originalQuery, userId, conversationId);

        // 验证
        assertNotNull(rewritten);
        // 应该被医疗术语化
        assertTrue(rewritten.contains("头痛"));
        assertFalse(rewritten.contains("脑袋疼"));
    }

    @Test
    void testRewrite_Exception() {
        // 准备
        String originalQuery = null;
        Long userId = 1L;
        Long conversationId = 100L;

        // 执行
        String rewritten = queryRewriteService.rewrite(originalQuery, userId, conversationId);

        // 验证 - 应该返回原始查询（null）
        assertNull(rewritten);
    }

    @Test
    void testRewriteBatch() {
        // 准备
        List<String> queries = Arrays.asList(
                "高血压症状",
                "糖尿病治疗",
                "感冒预防"
        );
        Long userId = 1L;
        Long conversationId = 100L;

        // 执行
        List<String> rewrittenQueries = queryRewriteService.rewriteBatch(queries, userId, conversationId);

        // 验证
        assertNotNull(rewrittenQueries);
        assertEquals(queries.size(), rewrittenQueries.size());
        for (int i = 0; i < queries.size(); i++) {
            assertNotNull(rewrittenQueries.get(i));
            assertTrue(rewrittenQueries.get(i).contains(queries.get(i).substring(0, 2)));
        }
    }

    @Test
    void testSimplify_RemoveStopWords() {
        // 准备
        String query = "请问高血压有什么症状吗谢谢";

        // 执行
        String simplified = queryRewriteService.simplify(query);

        // 验证
        assertNotNull(simplified);
        // 停用词应该被移除
        assertFalse(simplified.contains("请问"));
        assertFalse(simplified.contains("吗"));
        assertFalse(simplified.contains("谢谢"));
        assertTrue(simplified.contains("高血压"));
        assertTrue(simplified.contains("症状"));
    }

    @Test
    void testSimplify_AllStopWords() {
        // 准备
        String query = "请问怎么谢谢";

        // 执行
        String simplified = queryRewriteService.simplify(query);

        // 验证 - 如果全部是停用词，应返回原始查询
        assertEquals(query, simplified);
    }

    @Test
    void testSimplify_NullQuery() {
        // 准备
        String query = null;

        // 执行
        String simplified = queryRewriteService.simplify(query);

        // 验证
        assertNull(simplified);
    }

    @Test
    void testSimplify_EmptyQuery() {
        // 准备
        String query = "";

        // 执行
        String simplified = queryRewriteService.simplify(query);

        // 验证
        assertEquals("", simplified);
    }

    @Test
    void testCorrectSpelling_KnownError() {
        // 准备
        String query = "糖原病怎么治";

        // 执行
        String corrected = queryRewriteService.correctSpelling(query);

        // 验证
        assertNotNull(corrected);
        assertEquals("糖尿病怎么治", corrected);
    }

    @Test
    void testCorrectSpelling_MultipleErrors() {
        // 准备
        String query = "感冐和高血圧";

        // 执行
        String corrected = queryRewriteService.correctSpelling(query);

        // 验证
        assertNotNull(corrected);
        assertEquals("感冒和高血压", corrected);
    }

    @Test
    void testCorrectSpelling_NoError() {
        // 准备
        String query = "高血压糖尿病";

        // 执行
        String corrected = queryRewriteService.correctSpelling(query);

        // 验证
        assertEquals(query, corrected);
    }

    @Test
    void testCorrectSpelling_NullQuery() {
        // 准备
        String query = null;

        // 执行
        String corrected = queryRewriteService.correctSpelling(query);

        // 验证
        assertNull(corrected);
    }

    @Test
    void testMedicalize_KnownTerm() {
        // 准备
        String query = "肚子疼拉肚子";

        // 执行
        String medicalized = queryRewriteService.medicalize(query);

        // 验证
        assertNotNull(medicalized);
        // 应该转换为医学术语
        assertTrue(medicalized.contains("腹痛") || medicalized.contains("腹泻"));
        assertFalse(medicalized.contains("肚子疼"));
    }

    @Test
    void testMedicalize_NoMedicalization() {
        // 准备
        String query = "高血压治疗";

        // 执行
        String medicalized = queryRewriteService.medicalize(query);

        // 验证
        assertEquals(query, medicalized); // 没有需要转换的术语
    }

    @Test
    void testMedicalize_NullQuery() {
        // 准备
        String query = null;

        // 执行
        String medicalized = queryRewriteService.medicalize(query);

        // 验证
        assertNull(medicalized);
    }

    @Test
    void testMedicalize_EmptyQuery() {
        // 准备
        String query = "";

        // 执行
        String medicalized = queryRewriteService.medicalize(query);

        // 验证
        assertEquals("", medicalized);
    }

    @Test
    void testIsAvailable() {
        // 执行
        boolean available = queryRewriteService.isAvailable();

        // 验证
        assertTrue(available); // 基于规则的服务始终可用
    }

    @Test
    void testGetStats_Initial() {
        // 执行
        QueryRewriteService.QueryRewriteStats stats = queryRewriteService.getStats();

        // 验证
        assertNotNull(stats);
        assertEquals(0, stats.getTotalQueries());
        assertEquals(0, stats.getRewrittenQueries());
        assertEquals(0, stats.getExpandedQueries());
        assertEquals(0, stats.getSimplifiedQueries());
        assertEquals(0, stats.getCorrectedQueries());
        assertEquals(0.0, stats.getAverageProcessingTime());
    }

    @Test
    void testGetStats_AfterQueries() {
        // 准备 - 执行一些查询
        queryRewriteService.rewrite("高血压症状", 1L, 100L);
        queryRewriteService.rewrite("糖尿病治疗", 2L, 200L);

        // 执行
        QueryRewriteService.QueryRewriteStats stats = queryRewriteService.getStats();

        // 验证
        assertNotNull(stats);
        assertEquals(2, stats.getTotalQueries());
        assertTrue(stats.getRewrittenQueries() >= 0);
        assertTrue(stats.getAverageProcessingTime() >= 0);
    }
}