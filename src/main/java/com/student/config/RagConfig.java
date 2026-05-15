package com.student.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * RAG配置类
 * 集中管理检索增强生成相关配置参数
 *
 * @author 系统
 * @version 1.0
 */
@Configuration
@ConfigurationProperties(prefix = "imkqas.rag")
@Data
@Validated
@Slf4j
public class RagConfig {


    // ========== 重排序模型配置 ==========

    /**
     * 重排序模型配置
     */
    @Data
    public static class RerankerConfig {
        /** 模型名称 */
        @NotBlank
        private String model = "gte-rerank-v2";

        /** 部署方式: local(本地ONNX), api(远程API) */
        @NotBlank
        private String deployment = "api";

        /** 本地模型路径 (ONNX格式) */
        private String modelPath = "models/bge-reranker-v2-m3.onnx";

        /** 远程API端点 */
        private String apiEndpoint = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";

        /** API密钥 (环境变量) */
        private String apiKey = "sk-ca1040e1914248cbb02dcb7ca8c7120a";

        /** 重排序top-k */
        @Min(1)
        @Max(50)
        private int topK = 5;

        /** 是否启用缓存 */
        private boolean cacheEnabled = true;

        /** 缓存TTL (秒) */
        @Min(60)
        private int cacheTtl = 1800;

        /** 超时时间 (毫秒) */
        @Min(1000)
        private int timeout = 3000;
    }

    // ========== 嵌入模型配置 ==========

    /**
     * 嵌入模型配置
     */
    @Data
    public static class EmbeddingConfig {
        /** 模型名称 */
        @NotBlank
        private String model = "text-embedding-v3";

        /** 输出维度 */
        @Min(1)
        @Max(4096)
        private int dimension = 1024;

        /** API端点 */
        private String apiEndpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings";

        /** API密钥 (从环境变量读取) */
        private String apiKey = "sk-ca1040e1914248cbb02dcb7ca8c7120a";

        /** 是否启用缓存 */
        private boolean cacheEnabled = true;

        /** 超时时间 (毫秒) */
        @Min(1000)
        private int timeout = 10000;

        /** 缓存TTL (秒) */
        @Min(60)
        private int cacheTtl = 1800;

        /** 部署方式: api(远程API), local(本地模型) */
        @NotBlank
        private String deployment = "api";
    }

    // ========== 检索配置 ==========

    /**
     * 检索配置
     */
    @Data
    public static class RetrievalConfig {
        /**
         * 检索模式: vector(向量), keyword(关键词), hybrid(混合)
         */
        public enum Mode {
            VECTOR, KEYWORD, HYBRID
        }

        /** 检索模式 */
        @NotNull
        private Mode mode = Mode.HYBRID;

        /** 各检索方式权重配置 */
        @Data
        public static class WeightsConfig {
            /** 向量检索权重 */
            @DecimalMin("0.0")
            @DecimalMax("1.0")
            private double vector = 0.6;

            /** 关键词检索权重 */
            @DecimalMin("0.0")
            @DecimalMax("1.0")
            private double keyword = 0.4;
        }

        /** 权重配置 */
        @NotNull
        private WeightsConfig weights = new WeightsConfig();

        /** 初始检索top-k */
        @Min(1)
        @Max(100)
        private int initialTopK = 20;

        /** 重排序top-k */
        @Min(1)
        @Max(50)
        private int rerankTopK = 5;

        /** RRF融合参数k */
        @Min(1)
        @Max(1000)
        private int rrfK = 60;

        /** 检索超时时间 (毫秒) */
        @Min(1000)
        @Max(30000)
        private int timeout = 10000;
    }

    // ========== LLM配置 ==========

    /**
     * 大语言模型配置 (阿里云百炼 DashScope)
     */
    @Data
    public static class LlmConfig {
        /** API基础URL */
        @NotBlank
        private String baseUrl = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";

        /** API密钥 (从环境变量读取) */
        private String apiKey = "sk-ca1040e1914248cbb02dcb7ca8c7120a";

        /** 模型名称: qwen-turbo / qwen-plus / qwen-max */
        @NotBlank
        private String model = "qwen-plus";

        /** 嵌入模型名称 (用于统一配置) */
        private String embeddingModel = "text-embedding-v3";

        /** 超时时间 (秒) */
        @Min(1)  // 临时改为1以调试
        @Max(300)
        private int timeout = 60;

        // 保留向后兼容的字段（可选）
        /** 温度参数 */
        @DecimalMin("0.0")
        @DecimalMax("2.0")
        private double temperature = 0.1;

        /** 最大token数 */
        @Min(100)
        @Max(10000)
        private int maxTokens = 2000;
    }

    // ========== 文档处理配置 ==========

    /**
     * 文档处理配置
     */
    @Data
    public static class DocumentConfig {
        /** 支持的文件类型 */
        @NotBlank
        private String supportedFormats = "pdf,docx,doc,txt";

        /** 分块配置 */
        @Data
        public static class ChunkConfig {
            /** 分块大小 (字符数) */
            @Min(100)
            @Max(2000)
            private int size = 500;

            /** 重叠字符数 */
            @Min(0)
            @Max(500)
            private int overlap = 50;

            /**
             * 分块策略: fixed(固定大小), semantic(语义分割)
             */
            @NotBlank
            private String strategy = "semantic";
        }

        /** 文本提取配置 */
        @Data
        public static class ExtractionConfig {
            /** PDF提取配置 */
            @Data
            public static class PdfConfig {
                /** 提取策略: pdfbox */
                @NotBlank
                private String strategy = "pdfbox";

                /** 是否提取图片 */
                private boolean extractImages = false;
            }

            /** DOCX提取配置 */
            @Data
            public static class DocxConfig {
                /** 提取策略: poi */
                @NotBlank
                private String strategy = "poi";
            }

            private PdfConfig pdf = new PdfConfig();
            private DocxConfig docx = new DocxConfig();
        }

        private ChunkConfig chunk = new ChunkConfig();
        private ExtractionConfig extraction = new ExtractionConfig();
    }

    // ========== 缓存配置 ==========

    /**
     * 缓存配置
     */
    @Data
    public static class CacheConfig {
        /** 查询结果缓存配置 */
        @Data
        public static class QueryCacheConfig {
            private boolean enabled = true;
            @Min(60)
            private int ttl = 3600;
            @Min(10)
            private int maxSize = 1000;
        }

        /** 嵌入向量缓存配置 */
        @Data
        public static class EmbeddingCacheConfig {
            private boolean enabled = true;
            @Min(300)
            private int ttl = 86400;
            @Min(100)
            private int maxSize = 10000;
        }

        /** LLM响应缓存配置 */
        @Data
        public static class LlmCacheConfig {
            private boolean enabled = true;
            @Min(60)
            private int ttl = 1800;
            @Min(10)
            private int maxSize = 500;
        }

        private QueryCacheConfig query = new QueryCacheConfig();
        private EmbeddingCacheConfig embedding = new EmbeddingCacheConfig();
        private LlmCacheConfig llm = new LlmCacheConfig();
    }

    // ========== 性能监控配置 ==========

    /**
     * 性能监控配置
     */
    @Data
    public static class MonitoringConfig {
        /** 是否启用监控 */
        private boolean enabled = true;

        /** 指标采集间隔 (秒) */
        @Min(10)
        private int interval = 60;

        /** 慢查询阈值 (毫秒) */
        @Min(100)
        private int slowQueryThreshold = 5000;

        /** 错误率阈值 (%) */
        @DecimalMin("0.1")
        @DecimalMax("100.0")
        private double errorRateThreshold = 5.0;
    }

    // ========== 主配置字段 ==========

    /** 重排序模型配置 */
    private RerankerConfig reranker = new RerankerConfig();

    /** 嵌入模型配置 */
    private EmbeddingConfig embedding = new EmbeddingConfig();

    /** 检索配置 */
    private RetrievalConfig retrieval = new RetrievalConfig();

    /** 大语言模型配置 */
    private LlmConfig llm = new LlmConfig();

    /** 文档处理配置 */
    private DocumentConfig document = new DocumentConfig();

    /** 缓存配置 */
    private CacheConfig cache = new CacheConfig();

    /** 性能监控配置 */
    private MonitoringConfig monitoring = new MonitoringConfig();

    // ========== 质量过滤配置 ==========

    /** 质量过滤配置 */
    @Data
    public static class QualityFilterConfig {
        /** 最小片段长度（字符数，低于此值丢弃） */
        private int minFragmentLength = 20;
        /** 黑名单域名 */
        private List<String> blacklistDomains = List.of(
                "zhidao.baidu.com", "baike.baidu.com",
                "zhihu.com", "tieba.baidu.com",
                "haodf.com", "xywy.com"
        );
        /** 黑名单来源的长度阈值 */
        private int blacklistMinLength = 100;
        /** 是否启用矛盾检测 */
        private boolean contradictionDetectionEnabled = true;
        /** 矛盾检测权威性阈值 */
        private double contradictionAuthorityThreshold = 0.7;
    }

    /** 质量过滤配置 */
    private QualityFilterConfig qualityFilter = new QualityFilterConfig();

    // ========== 多因子重排序配置 ==========

    /** 多因子重排序配置 */
    @Data
    public static class MultiFactorRerankConfig {
        /** 维度权重 */
        @Data
        public static class FactorWeights {
            private double authority = 0.4;
            private double timeliness = 0.2;
            private double semantic = 0.4;
        }
        private FactorWeights weights = new FactorWeights();
        /** 时效性衰减率 λ */
        private double decayRate = 0.3;
        /** 各知识类型半衰期（年） */
        @Data
        public static class HalfLives {
            private double treatment = 3.0;
            private double diagnosis = 6.0;
            private double device = 5.0;
            private double basicScience = 18.0;
            private double publicHealth = 4.0;
            private double classicResearch = 100.0;
            private double unknown = 5.0;
        }
        private HalfLives halfLives = new HalfLives();
    }

    /** 多因子重排序配置 */
    private MultiFactorRerankConfig multiFactorRerank = new MultiFactorRerankConfig();

    // ========== 语义缓存链配置 ==========

    /** 语义缓存链配置 */
    @Data
    public static class SemanticCacheConfig {
        /** 是否启用语义缓存 */
        private boolean enabled = true;
        /** Redis缓存TTL（秒），作为最终兜底 */
        @Min(300)
        private int ttl = 7200; // 2小时
        /** 分布式锁等待超时（秒） */
        @Min(1)
        private int lockWaitTimeout = 5;
        /** 分布式锁TTL（秒），防止死锁 */
        @Min(1)
        private int lockTtl = 30;
        /** 术语映射版本定时检查间隔（秒） */
        @Min(300)
        private int versionCheckInterval = 3600; // 1小时
        /** 知识库当前版本（可通过API递增） */
        private int knowledgeVersion = 1;
    }

    /** 语义缓存链配置 */
    private SemanticCacheConfig semanticCache = new SemanticCacheConfig();

    /**
     * 配置初始化方法
     */
    @PostConstruct
    public void init() {
        log.info("RAG配置初始化完成");

        log.info("重排序模型: {}, 部署方式: {}",
                reranker.getModel(), reranker.getDeployment());
        log.info("检索模式: {}, 权重[向量: {}, 关键词: {}]",
                retrieval.getMode(), retrieval.getWeights().getVector(), retrieval.getWeights().getKeyword());
        log.info("嵌入模型: {}, 维度: {}",
                embedding.getModel(), embedding.getDimension());
        log.info("LLM模型: {}, 温度: {}, 最大token: {}",
                llm.getModel(), llm.getTemperature(), llm.getMaxTokens());
        log.info("质量过滤: 最小片段长度={}, 黑名单域名数={}, 矛盾检测={}",
                qualityFilter.getMinFragmentLength(), qualityFilter.getBlacklistDomains().size(),
                qualityFilter.isContradictionDetectionEnabled() ? "启用" : "禁用");
        log.info("多因子重排序: 权威权重={}, 时效权重={}, 语义权重={}",
                multiFactorRerank.getWeights().getAuthority(),
                multiFactorRerank.getWeights().getTimeliness(),
                multiFactorRerank.getWeights().getSemantic());
        log.info("语义缓存链: 启用={}, TTL={}s, 版本={}",
                semanticCache.isEnabled(), semanticCache.getTtl(), semanticCache.getKnowledgeVersion());
    }
}