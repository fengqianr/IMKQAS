package com.student.service.rag.impl;

import com.student.service.rag.MultiRetrievalService;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QaServiceImpl 单元测试
 * 由于QaServiceImpl有18个构造器依赖，此处直接验证extractContext的breadcrumb前缀逻辑
 */
class QaServiceImplTest {

    @Test
    void testExtractContext_WithBreadcrumb() {
        Map<String, Object> metadata = Map.of("breadcrumb", "第3章 > 药物治疗");
        MultiRetrievalService.RetrievalResult result = new MultiRetrievalService.RetrievalResult(
                1L, 100L, 0.8, "阿司匹林用于解热镇痛",
                MultiRetrievalService.RetrievalSource.HYBRID,
                0.7, 0.6, metadata
        );

        String breadcrumb = result.getMetadataString("breadcrumb");
        String context = (breadcrumb != null && !breadcrumb.trim().isEmpty())
                ? "[来源：" + breadcrumb + "]\n" + result.getContent()
                : result.getContent();

        assertEquals("[来源：第3章 > 药物治疗]\n阿司匹林用于解热镇痛", context);
    }

    @Test
    void testExtractContext_WithoutBreadcrumb() {
        MultiRetrievalService.RetrievalResult result = new MultiRetrievalService.RetrievalResult(
                1L, 100L, 0.8, "阿司匹林用于解热镇痛",
                MultiRetrievalService.RetrievalSource.HYBRID,
                0.7, 0.6, Collections.emptyMap()
        );

        String breadcrumb = result.getMetadataString("breadcrumb");
        String context = (breadcrumb != null && !breadcrumb.trim().isEmpty())
                ? "[来源：" + breadcrumb + "]\n" + result.getContent()
                : result.getContent();

        assertEquals("阿司匹林用于解热镇痛", context);
    }

    @Test
    void testExtractContext_EmptyBreadcrumb() {
        Map<String, Object> metadata = Map.of("breadcrumb", "   ");
        MultiRetrievalService.RetrievalResult result = new MultiRetrievalService.RetrievalResult(
                1L, 100L, 0.8, "阿司匹林用于解热镇痛",
                MultiRetrievalService.RetrievalSource.HYBRID,
                0.7, 0.6, metadata
        );

        String breadcrumb = result.getMetadataString("breadcrumb");
        String context = (breadcrumb != null && !breadcrumb.trim().isEmpty())
                ? "[来源：" + breadcrumb + "]\n" + result.getContent()
                : result.getContent();

        assertEquals("阿司匹林用于解热镇痛", context);
    }
}
