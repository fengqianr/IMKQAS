# 阶段2第15天任务：准备实施计划

## 上下文
IMKQAS项目（医疗知识问答系统）正在执行阶段2（RAG核心）开发。根据阶段2执行计划，第15天任务是"集成测试与验收准备"。当前阶段2已完成第1-14天任务（93%），需要完成最后的集成测试与验收工作，为阶段3做准备。

**当前测试状态：**
- 总测试：308个，通过307个（99.7%通过率）
- 失败测试：1个（KeywordRetrievalServiceImplTest.testClearIndex_Success）
- 已有集成测试：QaServiceIntegrationTest（7个测试全部通过）、QaControllerTest、RagControllerTest等
- 缺失测试类型：性能测试、医疗准确性测试、CrossEncoderRerankService单元测试
- 缺失工具：JaCoCo代码覆盖率

**任务要求（从阶段2执行计划）：**
1. 编写RAG核心集成测试
2. 模拟完整问答流程，验证各组件协作
3. 性能测试：并发问答、大数据量检索
4. 医疗准确性测试：专业术语、安全兜底
5. 编写阶段2验收文档，总结交付物

**交付物：**
- RAG集成测试套件
- 性能测试报告
- 医疗准确性验证报告
- 阶段2验收文档

## 实施方法

### 1. 修复现有测试问题
**目标：** 确保所有现有测试通过率100%

**关键文件：**
- `src/test/java/com/student/service/KeywordRetrievalServiceImplTest.java` - 修复Lucene索引清理测试失败

**修改内容：**
- 修复testClearIndex_Success测试中IndexWriter.deleteAll()的模拟问题
- 验证修复后所有308个测试通过

### 2. 创建性能测试套件
**目标：** 验证系统性能指标（响应时间<5秒，支持至少10并发）

**技术选型：**
- **单元/集成级：** JUnit 5的`@RepeatedTest`和`@Timeout`注解 + Spring Boot Actuator `@Timed`
- **系统级：** JMeter进行HTTP接口压测

**新增文件：**
- `src/test/java/com/student/performance/BasePerformanceTest.java` - 性能测试基类
- `src/test/java/com/student/performance/RagPerformanceTest.java` - RAG核心性能测试
- `src/test/java/com/student/performance/QaControllerPerformanceTest.java` - 问答接口性能测试
- `src/test/java/com/student/performance/ConcurrentQaTest.java` - 并发测试
- `src/test/resources/jmeter/rag-performance.jmx` - JMeter测试计划（可选）

**性能指标：**
- 单次问答响应时间 < 2秒（P95）
- 并发10用户时，平均响应时间 < 3秒
- 系统吞吐量 > 5 QPS（每秒问答数）

### 3. 创建医疗准确性测试套件
**目标：** 验证医疗知识的准确性和安全性

**测试方法：**
1. 医学术语验证（使用标准医学术语词典）
2. 安全兜底测试（无法回答时的安全响应）
3. 医疗场景测试（模拟真实医疗问答）
4. 边界条件测试（极端和异常问题）

**新增文件：**
- `src/test/java/com/student/medical/MedicalAccuracyValidator.java` - 医疗准确性验证器
- `src/test/java/com/student/medical/MedicalScenarioTest.java` - 医疗场景测试
- `src/test/java/com/student/medical/SafetyFallbackTest.java` - 安全兜底测试
- `src/test/resources/medical/medical_terms.json` - 医学术语词典
- `src/test/resources/medical/test_scenarios.json` - 测试场景数据

**准确性标准：**
- 医学术语识别准确率 > 90%
- 安全兜底触发率100%
- 无医疗误导性回答

### 4. 补充缺失的单元测试
**目标：** 提高测试覆盖率，确保核心组件都有测试

**新增文件：**
- `src/test/java/com/student/service/rag/CrossEncoderRerankServiceImplTest.java` - CrossEncoderRerankService单元测试

### 5. 集成JaCoCo代码覆盖率工具
**目标：** 代码覆盖率达标（行>80%，分支>70%）

**修改文件：**
- `pom.xml` - 添加JaCoCo Maven插件配置

**配置要求：**
- 行覆盖率 > 80%
- 分支覆盖率 > 70%
- RAG核心模块覆盖率 > 85%
- 构建时检查覆盖率阈值

### 6. 编写验收文档
**目标：** 生成完整的阶段2验收材料

**新增文档：**
- `docs/phase2-acceptance-report.md` - 阶段2验收文档（总结目标、成果、测试结果、交付物）
- `docs/performance-test-report.md` - 性能测试报告
- `docs/medical-accuracy-report.md` - 医疗准确性验证报告
- `docs/code-coverage-report.md` - 代码覆盖率报告

**文档内容：**
- 项目概述和阶段2目标
- 测试执行结果汇总
- 性能指标分析
- 医疗准确性评估
- 代码覆盖率统计
- 交付物清单
- 验收结论和建议

## 验证方法

### 测试验证
1. **编译验证：** `mvn compile` - 确保无编译错误
2. **测试验证：** `mvn test` - 验证所有测试通过（308/308）
3. **覆盖率验证：** `mvn verify` - 检查JaCoCo覆盖率报告
4. **性能验证：** 运行性能测试套件，检查性能指标
5. **准确性验证：** 运行医疗准确性测试，人工审核关键场景

### 验收标准验证
1. **集成测试通过率：** 100%（308/308）
2. **性能指标：** 满足响应时间和并发要求
3. **医疗准确性：** 通过医疗准确性测试
4. **代码覆盖率：** 行>80%，分支>70%
5. **文档完整性：** 所有交付文档齐全

## 实施步骤（按优先级）

### 第1步：修复现有测试问题（预计：2小时）
1. 分析KeywordRetrievalServiceImplTest失败原因
2. 修改测试方法，正确模拟Lucene IndexWriter
3. 运行`mvn test`验证修复

### 第2步：集成JaCoCo（预计：1小时）
1. 在pom.xml中添加JaCoCo插件配置
2. 配置覆盖率阈值
3. 运行`mvn clean test`生成初始覆盖率报告
4. 分析当前覆盖率，识别低覆盖区域

### 第3步：创建性能测试套件（预计：4小时）
1. 创建BasePerformanceTest基类
2. 实现RagPerformanceTest（核心RAG流程性能）
3. 实现QaControllerPerformanceTest（HTTP接口性能）
4. 实现ConcurrentQaTest（并发测试）
5. 运行性能测试，收集基准数据

### 第4步：创建医疗准确性测试（预计：3小时）
1. 创建医学术语词典（medical_terms.json）
2. 创建医疗测试场景数据（test_scenarios.json）
3. 实现MedicalAccuracyValidator验证器
4. 实现MedicalScenarioTest和SafetyFallbackTest
5. 运行医疗准确性测试

### 第5步：补充CrossEncoderRerankService测试（预计：1小时）
1. 创建CrossEncoderRerankServiceImplTest
2. 实现重排序功能测试、批量测试、异常测试
3. 运行测试验证通过

### 第6步：编写验收文档（预计：2小时）
1. 编写phase2-acceptance-report.md（主要验收文档）
2. 基于测试结果编写performance-test-report.md
3. 基于测试结果编写medical-accuracy-report.md
4. 基于JaCoCo报告编写code-coverage-report.md
5. 更新项目进度报告（docs/project-progress-report.md）

### 第7步：最终验证（预计：1小时）
1. 运行完整测试套件：`mvn clean verify`
2. 生成所有报告
3. 验证所有验收标准
4. 更新任务状态为完成

## 关键文件路径

### 需要修改的现有文件
1. `src/test/java/com/student/service/KeywordRetrievalServiceImplTest.java` - 修复失败测试
2. `pom.xml` - 添加JaCoCo插件和性能测试依赖
3. `docs/project-progress-report.md` - 更新项目进度

### 需要创建的新文件
1. **性能测试：**
   - `src/test/java/com/student/performance/BasePerformanceTest.java`
   - `src/test/java/com/student/performance/RagPerformanceTest.java`
   - `src/test/java/com/student/performance/QaControllerPerformanceTest.java`
   - `src/test/java/com/student/performance/ConcurrentQaTest.java`
   - `src/test/resources/jmeter/rag-performance.jmx`（可选）

2. **医疗准确性测试：**
   - `src/test/java/com/student/medical/MedicalAccuracyValidator.java`
   - `src/test/java/com/student/medical/MedicalScenarioTest.java`
   - `src/test/java/com/student/medical/SafetyFallbackTest.java`
   - `src/test/resources/medical/medical_terms.json`
   - `src/test/resources/medical/test_scenarios.json`

3. **单元测试补充：**
   - `src/test/java/com/student/service/rag/CrossEncoderRerankServiceImplTest.java`

4. **验收文档：**
   - `docs/phase2-acceptance-report.md`
   - `docs/performance-test-report.md`
   - `docs/medical-accuracy-report.md`
   - `docs/code-coverage-report.md`

## 风险与缓解

### 技术风险
1. **性能测试环境差异**：在测试环境模拟生产配置，使用Docker Compose部署完整环境
2. **医疗测试数据不足**：使用公开医疗数据集子集，人工标注关键测试用例
3. **JaCoCo配置复杂性**：参考标准配置模板，分阶段启用覆盖率检查

### 时间风险
1. **测试开发超时**：优先实现核心测试，分阶段交付
2. **性能调试耗时**：设置性能基准，只对关键路径进行深度优化

### 质量风险
1. **测试覆盖不足**：设置JaCoCo覆盖率阈值，构建失败时阻止合并
2. **医疗准确性主观性**：建立明确的验收标准，人工审核关键场景

## 成功标准

### 技术标准
- 所有测试通过率100%（308/308）
- JaCoCo覆盖率：行>80%，分支>70%
- 性能指标满足要求（响应时间<5秒，支持10并发）

### 业务标准
- RAG核心功能集成测试完成
- 医疗准确性验证通过
- 系统稳定性验证完成

### 过程标准
- 测试文档完整可重现
- 所有交付物齐全
- 项目进度报告更新

## 后续建议
1. **持续集成**：将性能测试集成到CI/CD流水线
2. **监控增强**：在生产环境添加性能监控和告警
3. **测试数据管理**：建立医疗测试数据集版本管理
4. **自动化扩展**：考虑使用TestContainers进行集成测试