2026-04-04-IMKQAS-design

**版本**: 1.0  
**日期**: 2026-04-04  
**作者**: Claude Code  
**状态**: 草案 (待用户审查)

---

## 1. 项目概述

### 1.1 项目背景
基于RAG（Retrieval-Augmented Generation）架构的医疗知识智能问答系统，旨在为不同用户群体提供专业、准确、安全的医疗知识查询服务。

### 1.2 目标用户
| 用户群体 | 核心需求 | 使用场景 |
|---------|---------|---------|
| 医学生 | 学习辅助、备考复习 | 快速查询教材知识点 |
| 基层医护 | 临床决策支持 | 检索诊疗指南、用药方案 |
| 普通患者 | 健康咨询 | 了解疾病知识、用药注意事项、就医建议 |
| 健康管理师 | 专业查询 | 营养学、康复训练等健康管理知识 |

### 1.3 核心价值
- **准确性**: 基于权威医学文献，避免大模型幻觉
- **安全性**: 医疗安全兜底机制，紧急症状提醒就医
- **专业性**: 医疗术语规范化，科室导诊分流
- **可视化**: 检索过程可视化展示技术深度
- **易用性**: 对话式交互，流式输出体验

## 2. 系统架构

### 2.1 六层架构设计
```
┌─────────────────────────────────────────────────────────┐
│                   展示层 (Presentation)                   │
│   Vue3 + Element Plus + Markdown渲染 + ECharts响应式前端  │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│                   网关层 (Gateway)                       │
│         Spring Cloud Gateway (统一入口、限流、鉴权)        │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│                   应用层 (Application)                   │
│        SpringBoot + Langchain4j (核心业务逻辑)            │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│                 RAG引擎层 (RAG Engine)                   │
│   多路召回 + RRF融合 + Cross-Encoder重排序 (系统亮点)      │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│                模型服务层 (Model Service)                │
│ Embedding(bge-large-zh) + Reranker(bge-reranker-v2-m3) + LLM │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│                   存储层 (Storage)                       │
│       MySQL + Milvus + Redis + MinIO (结构化+向量+缓存+文件)│
└─────────────────────────────────────────────────────────┘
```

### 2.2 技术栈选型
| 层级 | 技术组件 | 版本/说明 |
|------|---------|----------|
| 展示层 | Vue3 | 3.4+ |
| 展示层 | Element Plus | 2.4+ |
| 展示层 | ECharts | 5.4+ |
| 网关层 | Spring Cloud Gateway | 4.1+ (可选) |
| 应用层 | Spring Boot | 3.2+ |
| 应用层 | Langchain4j | 0.29+ |
| RAG引擎 | 自定义实现 | 多路召回+RRF+重排序 |
| 向量模型 | BGE-large-zh | 中文医疗领域优化 |
| 重排序 | BGE-reranker-v2-m3 | 医疗语义匹配 |
| LLM | 专用医疗大模型 | 查询改写+答案生成 |
| 数据库 | MySQL | 8.0+ |
| 向量库 | Milvus | 2.3+ |
| 缓存 | Redis | 7.2+ |
| 文件存储 | MinIO | 最新版 |

## 3. 模块设计

### 3.1 智能问答模块 (核心亮点 ⭐)

#### 3.1.1 多轮对话问答
| 功能点 | 描述 | 技术实现 | 优先级 |
|--------|------|----------|--------|
| 流式输出 | 答案逐字流式返回，类似 ChatGPT 的打字机效果 | SSE 推送 | 高 |
| 来源溯源 | 每条回答末尾标注引用的医学文献章节和页码 | Metadata 标注 | 最高 |
| 多轮上下文 | 支持追问和指代消解 | Redis 上下文 + LLM 指代消解 | 高 |
| 医疗安全兜底 | 检索置信度低时明确提示"建议就医" | 阈值判断 + 关键词检测 | 最高 |
| 答案自评 | LLM 生成回答后自动评估是否完全基于检索内容 | 二次 LLM 调用 | 中 |

#### 3.1.2 科室导诊分流
用户描述症状后，系统基于检索到的医学知识自动判断可能涉及的科室，给出就诊建议。
- **实现方式**: 在 Prompt 中添加科室判断指令
- **演示价值**: 答辩加分项，展示医疗专业性

#### 3.1.3 药物查询与用药提醒
- **药品说明书检索**: 适应症、用法用量、禁忌症、不良反应
- **药物相互作用**: "头孢和酒能一起吗"等高频问题语义缓存
- **用药提醒**: 基于用户健康档案的个性化提醒

#### 3.1.4 检索过程可视化 (答辩演示专用)
在对话界面提供"查看检索过程"按钮，展示完整 RAG 链路：
1. Query 改写结果
2. 向量检索 top‑20 列表
3. BM25 检索 top‑20 列表
4. RRF 融合后的排序
5. Cross‑Encoder 重排序后的 top‑5
6. 最终 Prompt 模板

### 3.2 知识库管理模块 (标准 CRUD)
| 功能 | 描述 | 技术实现 | 开发难度 |
|------|------|----------|----------|
| 文档上传 | 支持 PDF/Word/TXT 格式医学文档 | 文件上传 + 异步任务 | 简单 |
| 智能切分 | 文本提取 → 结构化切分 → 向量化 → Milvus 入库 | 定时任务 / MQ | 中等 |
| 知识库列表 | 展示所有已入库文档 | 分页查询 | 简单 |
| 分类管理 | 按医学分类标签管理 | 标签 CRUD | 简单 |
| 文档预览 | 在线预览原始文档 | 文件预览组件 | 简单 |
| 切片浏览 | 查看文档 chunk 及 metadata | 分页查询 | 简单 |

### 3.3 用户管理模块 (标准 CRUD)
| 功能 | 描述 | 技术实现 |
|------|------|----------|
| 注册/登录 | 手机号 + 验证码登录，JWT Token 鉴权 | Spring Security + JWT |
| 角色权限 | 医护人员(知识库+问答)、普通用户(问答) | RBAC 权限模型 |
| 个人中心 | 修改头像、昵称、联系方式 | 基础 CRUD |
| 健康档案 | 年龄/性别/过敏史/慢病史，问答时自动注入 Prompt | 表单 + JSON 存储 |
| 用户列表 | 管理员查看所有用户 | 分页 + 条件查询 |

### 3.4 对话管理模块 (标准 CRUD)
| 功能 | 描述 |
|------|------|
| 会话列表 | 用户可创建多个独立会话 |
| 历史记录 | 查看完整对话历史，支持关键词搜索 |
| 会话导出 | 导出为 PDF 或 Markdown 格式 |
| 收藏/点赞 | 收藏有价值的回答 |
| 反馈机制 | "有用/无用"评价，优化检索策略 |

### 3.5 数据统计与可视化模块
后台首页数据大屏，使用 ECharts 可视化：
| 图表展示内容 | 实现方式 |
|-------------|----------|
| 问答统计: 今日/本周/本月问答总量、日趋势折线图 | SQL 聚合 + ECharts 折线图 |
| 热门问题: 高频问题 TOP 10 排行榜，词云图展示 | SQL GROUP BY + 词云组件 |
| 科室分布: 问答涉及的科室分布饼图 | 分类统计 + ECharts 饼图 |
| 知识库覆盖: 各分类知识库文档数量、切片数量统计 | SQL COUNT + 柱状图 |
| 用户活跃度: 日活用户数、人均问答次数、会话时长分布 | SQL 聚合 + 多维度图表 |
| 检索质量: 平均检索耗时、重排序命中率、兜底触发率 | 日志埋点 + 统计面板 |

## 4. 详细设计

### 4.1 RAG 引擎详细流程
```
用户提问 → Query改写LLM → 预处理 + 意图识别 → 多路并行检索
    ↓
向量检索 (Milvus) + 关键词检索 (BM25) + 混合检索
    ↓
RRF (Reciprocal Rank Fusion) 融合排序
    ↓
Cross-Encoder (bge-reranker-v2-m3) 重排序 top-5
    ↓
Prompt 模板构建 (注入检索结果 + 用户健康档案)
    ↓
LLM 生成答案 + 医疗安全检查
    ↓
流式输出 + 来源标注
```

### 4.2 核心服务类设计

#### 4.2.1 QaService (问答服务)
```java
@Service
public class QaService {
    // 流式答案生成
    public Flux<AnswerChunk> streamAnswer(String query, Long userId) {}
    
    // 科室导诊分流
    public DepartmentTriageResult triageDepartment(String symptoms) {}
    
    // 药物查询
    public DrugInfo queryDrug(String drugName) {}
}
```

#### 4.2.2 MedicalQueryRewriter (医疗查询改写)
```java
@Service  
public class MedicalQueryRewriter {
    // 口语化→专业化改写，例如："肚子疼吃什么药" → "腹痛的常见病因及对症用药方案"
    public String rewriteQuery(String originalQuery) {}
}
```

#### 4.2.3 MultiRetrievalService (多路检索)
```java
@Service
public class MultiRetrievalService {
    // 向量检索
    public List<DocumentChunk> vectorRetrieval(String query, int topK) {}
    
    // 关键词检索 (BM25)
    public List<DocumentChunk> keywordRetrieval(String query, int topK) {}
    
    // 混合检索
    public List<DocumentChunk> hybridRetrieval(String query, int topK) {}
}
```

#### 4.2.4 RrfFusionService (RRF融合)
```java
@Service
public class RrfFusionService {
    // Reciprocal Rank Fusion 算法
    public List<DocumentChunk> fuseResults(
        List<List<DocumentChunk>> retrievalResults, 
        Map<String, Double> weights) {}
}
```

#### 4.2.5 CrossEncoderRerankService (重排序)
```java
@Service
public class CrossEncoderRerankService {
    // 使用 bge-reranker-v2-m3 进行医疗语义重排序
    public List<DocumentChunk> rerank(String query, List<DocumentChunk> candidates) {}
}
```

### 4.3 数据库设计

#### 4.3.1 MySQL 表结构
```sql
-- 用户表
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) UNIQUE,
    phone VARCHAR(20) UNIQUE,
    role ENUM('USER', 'ADMIN') DEFAULT 'USER',
    health_profile JSON,  -- 健康档案: {age, gender, allergies, chronic_diseases}
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 文档表
CREATE TABLE documents (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255),
    category VARCHAR(50),  -- 内科/外科/儿科/妇科等
    file_path VARCHAR(500),
    chunk_count INT,
    status ENUM('UPLOADED', 'PROCESSING', 'COMPLETED', 'FAILED'),
    uploaded_by BIGINT,
    FOREIGN KEY (uploaded_by) REFERENCES users(id)
);

-- 文档分块表
CREATE TABLE document_chunks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT,
    chunk_index INT,
    content TEXT,
    metadata JSON,  -- {page, section, start_char, end_char}
    vector_id VARCHAR(100),  -- Milvus 向量 ID
    FOREIGN KEY (document_id) REFERENCES documents(id)
);

-- 对话表
CREATE TABLE conversations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
    title VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 消息表
CREATE TABLE messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT,
    role ENUM('USER', 'ASSISTANT'),
    content TEXT,
    source_references JSON,  -- 引用的文档chunk IDs
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id)
);
```

#### 4.3.2 Milvus 向量集合
```python
# medical_documents 集合配置
{
    "collection_name": "medical_documents",
    "dimension": 1024,  # BGE-large-zh 维度
    "metric_type": "IP",  # 内积相似度
    "fields": [
        {"name": "id", "type": "INT64", "is_primary": True},
        {"name": "chunk_id", "type": "INT64"},
        {"name": "document_id", "type": "INT64"},
        {"name": "content", "type": "VARCHAR", "max_length": 65535},
        {"name": "embedding", "type": "FLOAT_VECTOR", "dim": 1024},
        {"name": "metadata", "type": "JSON"}  # {category, page, section}
    ]
}
```

### 4.4 缓存设计 (Redis)

#### 4.4.1 缓存策略
```java
@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        // 配置连接池、序列化等
    }
}
```

#### 4.4.2 缓存分类
| 缓存类型 | Key 格式 | TTL | 用途 |
|---------|---------|-----|------|
| 查询结果 | `qa:result:{query_hash}` | 1小时 | 高频问题答案缓存 |
| 用户会话 | `session:{userId}:{conversationId}` | 24小时 | 对话上下文 |
| 医疗术语 | `medical:term:{term}` | 7天 | 医学术语标准化 |
| 药品交互 | `drug:interaction:{drug1}:{drug2}` | 30天 | 药物相互作用 |
| 限流计数 | `rate:limit:{userId}:{api}` | 按秒刷新 | 限流控制 |

### 4.5 限流策略
| 角色 | 默认限流 (请求/秒) | 突发容量 |
|------|-------------------|----------|
| 普通用户 | 10 | 20 |
| 管理员 | 50 | 100 |

特殊接口限流（无论角色）：
- 文档上传接口：2 请求/秒
- 批量操作接口：5 请求/秒

## 5. API 接口设计

### 5.1 RESTful API 概览
| 模块 | 端点 | 方法 | 描述 | 权限 |
|------|------|------|------|------|
| 问答 | `/api/qa/stream` | POST | 流式问答 | 所有用户 |
| 问答 | `/api/qa/triage` | POST | 科室导诊 | 所有用户 |
| 问答 | `/api/qa/drug` | GET | 药物查询 | 所有用户 |
| 知识库 | `/api/kb/documents` | POST | 上传文档 | ADMIN |
| 知识库 | `/api/kb/documents/{id}` | GET | 获取文档 | ADMIN |
| 用户 | `/api/auth/login` | POST | 登录 | 公开 |
| 用户 | `/api/auth/register` | POST | 注册 | 公开 |
| 对话 | `/api/conversations` | GET | 获取会话列表 | 所有用户 |
| 统计 | `/api/stats/dashboard` | GET | 数据大屏 | ADMIN |

### 5.2 流式问答接口示例
```http
POST /api/qa/stream
Content-Type: application/json
Authorization: Bearer {token}

{
  "query": "高血压患者应该注意什么？",
  "conversationId": "可选，用于多轮上下文"
}

响应 (SSE stream):
data: {"type": "chunk", "content": "高血压患者", "finished": false}
data: {"type": "chunk", "content": "应该注意", "finished": false}
data: {"type": "answer", "content": "完整答案...", "sources": [{"docId": 1, "page": 42}], "finished": true}
```

## 6. 部署方案

### 6.1 环境要求
| 组件 | 版本 | 配置要求 |
|------|------|----------|
| JDK | 21+ | 4核8G |
| MySQL | 8.0+ | 2核4G，100G存储 |
| Redis | 7.2+ | 2核4G |
| Milvus | 2.3+ | 4核8G，GPU可选 |
| MinIO | 最新 | 2核4G，存储依文档量定 |
| Nginx | 1.24+ | 2核4G |

### 6.2 部署架构
```
用户请求 → Nginx (负载均衡) → Spring Cloud Gateway → 微服务集群
                              ↓
            MySQL ↔ Redis ↔ Milvus ↔ MinIO
```

### 6.3 Docker Compose 配置
```yaml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_PASSWORD}
    volumes:
      - mysql_data:/var/lib/mysql
  
  redis:
    image: redis:7.2-alpine
  
  milvus:
    image: milvusdb/milvus:v2.3.0
    ports:
      - "19530:19530"
  
  minio:
    image: minio/minio
    command: server /data --console-address ":9001"
  
  backend:
    build: ./backend
    depends_on:
      - mysql
      - redis
      - milvus
      - minio
    environment:
      SPRING_PROFILES_ACTIVE: prod
```

## 7. 开发计划

### 7.1 阶段划分
| 阶段 | 时间 | 交付物 | 优先级 |
|------|------|--------|--------|
| 阶段1: 基础框架 | 2周 | SpringBoot项目、数据库、基础API | 高 |
| 阶段2: RAG核心 | 3周 | 检索、融合、重排序、LLM集成 | 最高 |
| 阶段3: 业务模块 | 2周 | 知识库管理、用户管理、对话管理 | 高 |
| 阶段4: 前端界面 | 2周 | Vue3前端、流式对话界面 | 中 |
| 阶段5: 高级功能 | 1周 | 可视化、科室导诊、药物查询 | 中 |
| 阶段6: 测试部署 | 1周 | 测试、部署、文档 | 低 |

### 7.2 里程碑
1. **M1**: 完成基础框架，实现简单问答
2. **M2**: 完成RAG核心链路，检索准确率>85%
3. **M3**: 完成前后端联调，上线测试环境
4. **M4**: 完成所有功能，性能优化，上线生产

## 8. 风险评估与缓解

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| 医疗准确性风险 | 高 | 中 | 严格来源标注、安全兜底、人工审核流程 |
| LLM API 成本 | 中 | 高 | 查询缓存、答案缓存、限制使用频率 |
| 向量检索性能 | 中 | 中 | Milvus索引优化、结果缓存、降级策略 |
| 数据隐私安全 | 高 | 低 | 数据加密、访问控制、匿名化处理 |
| 并发压力 | 中 | 中 | 限流策略、服务降级、弹性扩容 |

---

## 附录

### A. 术语表
- **RAG**: Retrieval-Augmented Generation，检索增强生成
- **RRF**: Reciprocal Rank Fusion，互逆排名融合
- **BM25**: 最佳匹配25，经典关键词检索算法
- **Cross-Encoder**: 交叉编码器，用于语义重排序
- **SSE**: Server-Sent Events，服务器推送事件

### B. 参考文献
1. 医疗诊疗指南（最新版）
2. 药品说明书数据库
3. 医学教材与专业文献
4. RAG 相关研究论文

### C. 修订记录
| 版本 | 日期 | 作者 | 修改说明 |
|------|------|------|----------|
| 1.0 | 2026-04-04 | Claude Code | 初稿创建 |

---

**审批**:  
□ 用户审查通过  
□ 技术评审通过  
□ 产品确认  
□ 项目启动批准