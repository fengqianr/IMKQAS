# 阶段2（RAG核心）执行计划

## 项目背景
IMKQAS（医疗知识问答系统）是一个基于Spring Boot + MyBatis Plus的Java Web项目，采用RAG（检索增强生成）架构为医学生、基层医护、普通患者和健康管理师提供专业、准确、安全的医疗知识查询服务。

根据设计文档（`docs/2026-04-04-IMKQAS-design.md`），项目分为6个阶段：
1. **阶段1**: 基础框架（2周） - SpringBoot项目、数据库、基础API
2. **阶段2**: RAG核心（3周） - 检索、融合、重排序、LLM集成 **(当前阶段)**
3. **阶段3**: 业务模块（2周） - 知识库管理、用户管理、对话管理
4. **阶段4**: 前端界面（2周） - Vue3前端、流式对话界面
5. **阶段5**: 高级功能（1周） - 可视化、科室导诊、药物查询
6. **阶段6**: 测试部署（1周） - 测试、部署、文档

## 当前状态分析

### 已完成的基础设施（阶段1部分完成）
1. **项目框架**: Spring Boot 3.2.5 + MyBatis Plus 3.5.5 + MySQL 8.3.0
2. **实体类**: User, Document, DocumentChunk, Conversation, Message（完整MyBatis Plus注解）
3. **数据访问层**: Mapper接口（继承BaseMapper<T>）
4. **业务服务层**: Service接口与实现
5. **控制层**: Controller基础CRUD
6. **配置类**: MyBatisPlusConfig、MyMetaObjectHandler、JwtAuthenticationFilter
7. **向量存储**: MilvusConfig + MilvusService（完整CRUD操作）
8. **文件存储**: MinioConfig + MinioService（文件上传下载）
9. **缓存**: Redis配置
10. **认证**: JWT工具类 + 认证过滤器

### 缺失的阶段1组件（可能影响阶段2）
1. 统一异常处理（GlobalExceptionHandler）
2. 完整Spring Security配置（SecurityConfig）
3. 数据库迁移工具（Flyway/Liquibase）
4. API文档（SpringDoc OpenAPI集成）
5. 单元测试框架完善
6. 多环境配置

### RAG核心组件现状
1. **向量数据库**: MilvusService已实现（插入、搜索、删除、统计）
2. **文件存储**: MinioService已实现（上传、下载、管理）
3. **缺失的RAG核心**:
   - 嵌入模型服务（BGE-large-zh集成）
   - 多路检索服务（向量检索、关键词检索、混合检索）
   - RRF融合服务
   - 重排序服务（BGE-reranker-v2-m3）
   - LLM集成（医疗大模型API调用）
   - 问答服务（QaService）和流式输出
   - 医疗查询改写服务
   - 科室导诊分流
   - 药物查询功能

## 阶段2目标
在3周（15个工作日）内实现完整的RAG核心功能，建立可工作的医疗知识问答系统基础，支持：
1. **文档向量化**: 集成BGE-large-zh嵌入模型，将医学文档转化为向量
2. **多路检索**: 实现向量检索、关键词检索（BM25）、混合检索
3. **结果融合**: 实现RRF（Reciprocal Rank Fusion）融合算法
4. **语义重排序**: 集成BGE-reranker-v2-m3进行医疗语义重排序
5. **LLM集成**: 集成医疗大模型生成专业、准确的回答
6. **流式输出**: 实现SSE流式输出，类似ChatGPT打字机效果
7. **医疗特化**: 实现科室导诊分流、药物查询、医疗安全兜底
8. **性能优化**: 检索缓存、限流策略、错误处理

## 前提条件与依赖
### 阶段1必须首先完成
根据用户确认，**需要先完成阶段1剩余任务，再开始阶段2**。阶段1基础框架必须完全就绪，包括：
1. **统一异常处理**（GlobalExceptionHandler）
2. **完整Spring Security配置**（SecurityConfig + 权限控制）
3. **数据库迁移工具**（Flyway/Liquibase集成）
4. **API文档**（SpringDoc OpenAPI完整集成）
5. **多环境配置**（dev/test/prod）
6. **单元测试框架完善**
7. **部署配置**（Dockerfile, docker-compose.yml）

### 外部服务依赖
1. **Milvus向量数据库**（已配置）
2. **MinIO对象存储**（已配置）
3. **Redis缓存**（已配置）
4. **MySQL数据库**（已配置）

### 模型服务依赖
1. **嵌入模型**: BGE-large-zh（1024维）
   - 部署方式：混合部署（本地部署推荐，远程API备选）
2. **重排序模型**: BGE-reranker-v2-m3
   - 部署方式：混合部署（本地部署推荐，远程API备选）
3. **医疗大语言模型**: 蚂蚁·安诊儿（AntAngelMed）
   - 部署方式：API服务（蚂蚁集团提供的医疗大模型服务）
   - 需要申请API访问权限和密钥

## 3周详细工作计划（15个工作日）

### 第1周：基础集成与嵌入模型（5天）

#### 第1天：依赖添加与项目配置
- **任务**: 添加RAG核心依赖，配置模型服务连接
- **详细描述**:
  - 在pom.xml中添加Langchain4j依赖（0.29+）用于LLM集成
  - 添加HTTP客户端依赖（OkHttp/RestTemplate）用于模型API调用
  - 添加本地嵌入模型库（可选：sentence-transformers、OnnxRuntime）
  - 配置application.yml，添加模型服务端点、API密钥等配置
  - 创建RAG配置类（RagConfig），集中管理RAG相关参数
- **预计耗时**: 1天
- **交付物**:
  - 更新的pom.xml依赖配置
  - RAG配置文件（application-rag.yml）
  - RagConfig配置类
- **验收标准**:
  - 项目能正常编译，无依赖冲突
  - 配置参数能从application.yml读取
  - 模型服务连接配置完整

#### 第2天：嵌入模型服务实现
- **任务**: 集成BGE-large-zh嵌入模型，实现文本向量化
- **详细描述**:
  - 创建EmbeddingService接口和实现类
  - 支持两种模式：本地模型推理（ONNX）和远程API调用
  - 实现文本批处理向量化，优化性能
  - 添加向量维度验证（1024维）
  - 实现嵌入模型缓存（Redis），避免重复计算
- **预计耗时**: 1天
- **前置依赖**: 第1天任务完成
- **交付物**:
  - EmbeddingService接口和实现类
  - 本地模型加载器（如使用ONNX Runtime）
  - 远程API调用客户端
  - 嵌入缓存管理器
- **验收标准**:
  - 能将中文文本转化为1024维向量
  - 支持批量处理（至少10个文本/批次）
  - 响应时间<500ms（本地）或<2s（远程）

#### 第3天：文档处理流水线
- **任务**: 实现文档解析、分块、向量化完整流水线
- **详细描述**:
  - 创建DocumentProcessorService，协调文档处理流程
  - 实现文本提取器：支持PDF、Word、TXT格式
  - 实现智能分块器：按医学结构（章节、段落）分块，保留上下文
  - 集成嵌入模型服务，将分块文本向量化
  - 批量存储到Milvus向量数据库
  - 更新Document和DocumentChunk状态
- **预计耗时**: 1天
- **前置依赖**: 第2天任务完成
- **交付物**:
  - DocumentProcessorService文档处理服务
  - 文本提取器（PDFBox、Apache POI集成）
  - 智能分块器（基于医学文档结构）
  - 批量向量化任务
- **验收标准**:
  - 能处理PDF/Word/TXT格式医学文档
  - 分块保留医学结构信息（章节、页码）
  - 向量化后正确存储到Milvus

#### 第4天：关键词检索服务（BM25）
- **任务**: 实现BM25关键词检索，建立全文检索引擎
- **详细描述**:
  - 创建KeywordRetrievalService，实现BM25算法
  - 集成Lucene或Elasticsearch（或纯Java实现）
  - 建立文档分块全文索引（content字段）
  - 支持医学同义词扩展（医学术语词典）
  - 实现多字段检索（内容、标题、分类）
- **预计耗时**: 1天
- **前置依赖**: 第3天任务完成
- **交付物**:
  - KeywordRetrievalService关键词检索服务
  - 全文索引构建器
  - 医学术语同义词扩展器
  - BM25评分计算器
- **验收标准**:
  - 能对文档分块进行关键词检索
  - 支持医学术语同义词扩展
  - 检索结果按BM25评分排序

#### 第5天：多路检索服务集成
- **任务**: 整合向量检索和关键词检索，实现混合检索
- **详细描述**:
  - 创建MultiRetrievalService，协调多路检索
  - 实现向量检索：调用MilvusService
  - 实现关键词检索：调用KeywordRetrievalService
  - 实现混合检索：并行执行，合并结果
  - 添加检索超时控制和错误处理
- **预计耗时**: 1天
- **前置依赖**: 第4天任务完成
- **交付物**:
  - MultiRetrievalService多路检索服务
  - 向量检索适配器
  - 关键词检索适配器
  - 混合检索协调器
- **验收标准**:
  - 支持向量、关键词、混合三种检索模式
  - 检索结果包含相关性分数
  - 错误处理和降级机制

### 第2周：结果融合与重排序（5天）

#### 第6天：RRF融合算法实现
- **任务**: 实现RRF（Reciprocal Rank Fusion）结果融合算法
- **详细描述**:
  - 创建RrfFusionService，实现RRF算法
  - 支持多路检索结果融合（向量、关键词等）
  - 可配置权重参数（向量检索权重、关键词检索权重）
  - 实现结果去重和归一化处理
  - 添加融合算法性能监控
- **预计耗时**: 1天
- **前置依赖**: 第5天任务完成
- **交付物**:
  - RrfFusionService RRF融合服务
  - 可配置的权重参数
  - 结果去重和归一化器
  - 融合性能监控器
- **验收标准**:
  - 能正确融合多路检索结果
  - 融合结果排序合理
  - 权重参数可动态配置

#### 第7天：重排序模型集成
- **任务**: 集成BGE-reranker-v2-m3进行医疗语义重排序
- **详细描述**:
  - 创建CrossEncoderRerankService重排序服务
  - 集成BGE-reranker-v2-m3模型（本地或API）
  - 实现query-document对相关性评分
  - 对融合后的top-k结果进行重排序
  - 添加重排序缓存，优化性能
- **预计耗时**: 1天
- **前置依赖**: 第6天任务完成
- **交付物**:
  - CrossEncoderRerankService重排序服务
  - 重排序模型客户端（本地/远程）
  - 相关性评分计算器
  - 重排序缓存管理器
- **验收标准**:
  - 能对检索结果进行语义重排序
  - 重排序后结果更符合query意图
  - 响应时间<1s（本地）或<3s（远程）

#### 第8天：LLM集成与Prompt工程
- **任务**: 集成医疗大语言模型，设计医疗Prompt模板
- **详细描述**:
  - 创建LlmService，集成蚂蚁·安诊儿医疗大模型（AntAngelMed）
  - 支持API接口调用，适配蚂蚁·安诊儿（AntAngelMed）API规范
  - 设计医疗问答Prompt模板，包含：
    - 检索结果注入
    - 用户健康档案注入
    - 医疗安全指令（置信度低时提示就医）
    - 来源引用格式要求
  - 实现流式响应解析（SSE格式）
- **预计耗时**: 1天
- **前置依赖**: 第7天任务完成
- **交付物**:
  - LlmService大语言模型服务
  - 医疗Prompt模板管理器
  - 流式响应解析器
  - 健康档案注入器
- **验收标准**:
  - 能调用大模型生成回答
  - Prompt模板包含所有必要信息
  - 支持流式输出格式

#### 第9天：问答服务核心实现
- **任务**: 实现完整的问答服务，整合RAG全流程
- **详细描述**:
  - 创建QaService问答服务，协调RAG全流程：
    1. 查询预处理（去噪、标准化）
    2. 多路检索
    3. RRF融合
    4. 重排序
    5. Prompt构建
    6. LLM生成
    7. 后处理（来源标注、安全检查）
  - 实现流式问答方法（SSE）
  - 添加问答缓存（Redis），避免重复计算
  - 实现医疗安全兜底机制
- **预计耗时**: 1天
- **前置依赖**: 第8天任务完成
- **交付物**:
  - QaService问答服务（核心类）
  - 流式问答响应生成器
  - 问答缓存管理器
  - 医疗安全兜底检查器
- **验收标准**:
  - 完整的RAG流程能串通运行
  - 支持流式输出
  - 医疗安全兜底机制生效

#### 第10天：医疗查询改写与意图识别
- **任务**: 实现医疗查询改写和意图识别服务
- **详细描述**:
  - 创建MedicalQueryRewriter医疗查询改写服务
  - 使用小型LLM或规则将口语化查询改写成专业查询
  - 实现医疗意图识别：诊断咨询、用药查询、科室导诊等
  - 添加上下文理解（多轮对话指代消解）
  - 集成同义词扩展和医学术语标准化
- **预计耗时**: 1天
- **前置依赖**: 第9天任务完成
- **交付物**:
  - MedicalQueryRewriter查询改写服务
  - 医疗意图识别器
  - 上下文管理器（Redis存储）
  - 医学术语标准化器
- **验收标准**:
  - 能将"肚子疼吃什么药"改写成"腹痛的常见病因及对症用药方案"
  - 能识别查询的医疗意图
  - 支持多轮对话上下文

### 第3周：高级功能与优化（5天）

#### 第11天：科室导诊分流实现
- **任务**: 实现症状到科室的导诊分流功能
- **详细描述**:
  - 创建DepartmentTriageService科室导诊服务
  - 构建科室-症状知识库（JSON配置或数据库）
  - 使用LLM进行症状分析和科室推荐
  - 实现多科室概率分布计算
  - 添加急诊症状识别和紧急就医提醒
- **预计耗时**: 1天
- **前置依赖**: 第10天任务完成
- **交付物**:
  - DepartmentTriageService科室导诊服务
  - 科室-症状知识库
  - 症状分析器
  - 急诊识别器
- **验收标准**:
  - 能根据症状描述推荐合适科室
  - 识别急诊症状并提示紧急就医
  - 输出科室推荐置信度

#### 第12天：药物查询功能实现
- **任务**: 实现药品说明书查询和药物相互作用检查
- **详细描述**:
  - 创建DrugQueryService药物查询服务
  - 集成药品知识库（JSON/数据库）
  - 实现药品信息检索：适应症、用法用量、禁忌症、不良反应
  - 实现药物相互作用检查：基于规则或LLM推理
  - 添加用药提醒（基于用户健康档案）
- **预计耗时**: 1天
- **前置依赖**: 第11天任务完成
- **交付物**:
  - DrugQueryService药物查询服务
  - 药品知识库管理器
  - 药物相互作用检查器
  - 用药提醒生成器
- **验收标准**:
  - 能查询药品详细信息
  - 检查药物相互作用并给出警告
  - 生成个性化用药提醒

#### 第13天：API接口与控制器实现
- **任务**: 实现RAG相关REST API接口
- **详细描述**:
  - 创建QaController问答控制器
    - `POST /api/qa/stream` 流式问答（SSE）
    - `POST /api/qa/triage` 科室导诊
    - `GET /api/qa/drug` 药物查询
  - 创建RagController RAG管理控制器
    - `POST /api/rag/process-document` 文档处理
    - `GET /api/rag/stats` RAG统计信息
  - 添加API参数验证和错误处理
  - 集成SpringDoc OpenAPI注解，生成API文档
- **预计耗时**: 1天
- **前置依赖**: 第12天任务完成
- **交付物**:
  - QaController问答API控制器
  - RagController RAG管理API控制器
  - 完整的API文档（Swagger UI）
  - 参数验证和错误处理
- **验收标准**:
  - 所有API能正常访问
  - 参数验证生效
  - Swagger UI文档完整准确

#### 第14天：性能优化与缓存策略
- **任务**: 优化RAG性能，实现多级缓存
- **详细描述**:
  - 实现查询结果缓存（Redis）：高频问题答案缓存
  - 实现向量检索缓存：相似query的检索结果缓存
  - 实现LLM响应缓存：相同prompt的LLM响应缓存
  - 添加缓存过期策略和刷新机制
  - 性能监控：检索耗时、缓存命中率、LLM响应时间
- **预计耗时**: 1天
- **前置依赖**: 第13天任务完成
- **交付物**:
  - 多级缓存管理器
  - 缓存策略配置
  - 性能监控指标
  - 缓存健康检查
- **验收标准**:
  - 缓存能显著减少响应时间
  - 缓存命中率>50%
  - 性能监控指标可查看

#### 第15天：集成测试与验收准备
- **任务**: 进行端到端集成测试，准备阶段验收
- **详细描述**:
  - 编写RAG核心集成测试
  - 模拟完整问答流程，验证各组件协作
  - 性能测试：并发问答、大数据量检索
  - 医疗准确性测试：专业术语、安全兜底
  - 编写阶段2验收文档，总结交付物
- **预计耗时**: 1天
- **前置依赖**: 第14天任务完成
- **交付物**:
  - RAG集成测试套件
  - 性能测试报告
  - 医疗准确性验证报告
  - 阶段2验收文档
- **验收标准**:
  - 集成测试通过率100%
  - 平均问答响应时间<5秒
  - 医疗安全兜底机制正确触发

## 关键文件路径

### 新增服务类
1. `src/main/java/com/student/service/EmbeddingService.java` - 嵌入模型服务
2. `src/main/java/com/student/service/KeywordRetrievalService.java` - 关键词检索服务
3. `src/main/java/com/student/service/MultiRetrievalService.java` - 多路检索服务
4. `src/main/java/com/student/service/RrfFusionService.java` - RRF融合服务
5. `src/main/java/com/student/service/CrossEncoderRerankService.java` - 重排序服务
6. `src/main/java/com/student/service/LlmService.java` - 大语言模型服务
7. `src/main/java/com/student/service/QaService.java` - 问答服务（核心）
8. `src/main/java/com/student/service/MedicalQueryRewriter.java` - 查询改写服务
9. `src/main/java/com/student/service/DepartmentTriageService.java` - 科室导诊服务
10. `src/main/java/com/student/service/DrugQueryService.java` - 药物查询服务

### 新增控制器
1. `src/main/java/com/student/controller/QaController.java` - 问答API控制器
2. `src/main/java/com/student/controller/RagController.java` - RAG管理API控制器

### 配置类
1. `src/main/java/com/student/config/RagConfig.java` - RAG配置类
2. `src/main/resources/application-rag.yml` - RAG专用配置

### 测试类
1. `src/test/java/com/student/service/EmbeddingServiceTest.java`
2. `src/test/java/com/student/service/QaServiceTest.java`
3. `src/test/java/com/student/controller/QaControllerTest.java`

## 技术选型与依赖

### 新增依赖
1. **Langchain4j**: 0.29+（LLM集成框架）
2. **ONNX Runtime**: 1.16+（本地模型推理，可选）
3. **Lucene**: 9.8+（BM25关键词检索，可选）
4. **PDFBox**: 3.0+（PDF文档解析）
5. **Apache POI**: 5.2+（Word文档解析）
6. **OkHttp**: 4.12+（HTTP客户端）

### 模型服务选项
1. **嵌入模型**: BGE-large-zh（1024维）
   - 选项A: 本地部署（ONNX格式）
   - 选项B: 远程API（ModelScope、HuggingFace）
2. **重排序模型**: BGE-reranker-v2-m3
   - 选项A: 本地部署
   - 选项B: 远程API
3. **大语言模型**: 蚂蚁·安诊儿（AntAngelMed）
   - 部署方式: API服务（蚂蚁集团医疗大模型）
   - 需要申请API访问权限和密钥
   - 支持流式输出和医疗专业问答

## 风险评估与缓解措施

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|--------|------|----------|
| 模型服务性能不足 | 高 | 高 | 1. 实现多级缓存 2. 支持模型降级 3. 异步处理机制 |
| 医疗准确性风险 | 中 | 高 | 1. 严格来源标注 2. 安全兜底机制 3. 人工审核流程 |
| 依赖服务不稳定 | 中 | 中 | 1. 服务健康检查 2. 优雅降级 3. 重试机制 |
| 向量检索精度不足 | 低 | 中 | 1. 多路检索融合 2. 重排序优化 3. query改写增强 |
| 并发压力 | 中 | 中 | 1. 限流策略 2. 异步处理 3. 水平扩展准备 |
| 成本控制 | 高 | 中 | 1. 查询缓存 2. 答案缓存 3. 使用本地模型降低API调用 |

## 验收标准

### 功能验收
1. **完整RAG流程**: 从query输入到answer输出的完整流程可运行
2. **流式输出**: 支持SSE流式输出，响应时间<5秒
3. **多路检索**: 向量检索、关键词检索、混合检索均可用
4. **医疗特化**: 科室导诊、药物查询、安全兜底功能正常
5. **缓存生效**: 查询缓存、答案缓存能减少重复计算

### 性能验收
1. **响应时间**: 平均问答响应时间<5秒（缓存命中时<1秒）
2. **并发能力**: 支持至少10并发问答请求
3. **缓存命中率**: 高频问题缓存命中率>50%
4. **资源使用**: CPU/内存使用在合理范围内

### 质量验收
1. **代码质量**: 通过代码规范检查，测试覆盖率>70%
2. **API文档**: Swagger UI文档完整准确
3. **错误处理**: 统一异常处理，友好错误信息
4. **日志完整**: 关键操作有详细日志记录

## 后续步骤

1. **评审本计划**: 确认技术选型、任务分解和时间安排
2. **准备环境**: 部署模型服务（嵌入模型、重排序模型、LLM）
3. **并行开发**: 可按照计划开始第1周任务
4. **每日跟踪**: 每日进度跟踪，及时调整计划
5. **阶段验收**: 第15天进行阶段2验收，进入阶段3