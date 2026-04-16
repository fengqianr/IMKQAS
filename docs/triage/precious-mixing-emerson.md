# 科室导诊服务引擎组件实现计划

## 背景和问题陈述

我们正在开发科室导诊服务，用于根据患者症状描述智能推荐就诊科室。服务需要支持：
1. **混合架构**：80%规则引擎匹配 + 20% LLM语义分析
2. **急诊检测**：自动识别紧急症状并分级（CRITICAL, HIGH, MEDIUM, LOW）
3. **批量处理**：支持同时处理多个症状描述
4. **降级机制**：当LLM不可用时自动降级为纯规则引擎
5. **统计监控**：收集使用情况、成功率、响应时间等指标

已完成的组件：
- 数据模型类 (`com.student.model.triage.*`)
- DTO类 (`com.student.dto.triage.*`)  
- 配置类和YAML文件 (`DepartmentKnowledgeConfig.java`, `department-knowledge.yml`, `symptom-synonyms.yml`)

## 设计决策

### 包结构选择
基于现有架构模式，引擎组件将放置在 `com.student.service.triage` 包下：
- 与现有服务保持一致（如 `MultiRetrievalService` 在 `com.student.service`）
- 符合Spring Boot分层架构
- 便于依赖注入和管理

### 混合架构设计
基于用户选择的顺序执行+条件触发策略：
1. **顺序执行**：先执行规则引擎，如果置信度低于阈值再触发LLM分析
2. **条件触发**：当规则引擎置信度 < 规则引擎阈值时，触发LLM分析
3. **超时控制**：设置LLM调用超时，超时后使用规则引擎结果
4. **结果融合**：基于置信度权重融合规则引擎和LLM结果
5. **多层降级**：LLM不可用→纯规则引擎；超时→规则引擎结果；低置信度→建议咨询导诊台

### 组件职责划分
1. **SymptomNormalizer**：症状标准化，同义词扩展，模糊匹配
2. **RuleBasedTriageEngine**：基于配置规则的科室匹配和置信度计算
3. **EmergencySymptomDetector**：急诊症状检测和分级
4. **LlmTriageAdapter**：LLM提示词构建和响应解析
5. **HybridTriageServiceImpl**：核心协调器，管理混合执行和结果融合

## 实施计划

### 第一阶段：创建基础组件
1. **创建包结构**：`com.student.service.triage` 及其子包
2. **创建TriageService接口**：定义分流服务契约
3. **创建TriageConfig配置类**：管理引擎参数和阈值
4. **实现SymptomNormalizer**：症状标准化器
5. **实现RuleBasedTriageEngine**：规则引擎核心逻辑
6. **实现EmergencySymptomDetector**：急诊检测器

### 第二阶段：创建混合协调和LLM集成
7. **实现LlmTriageAdapter**：LLM适配器，复用现有 `LlmService`
8. **实现HybridTriageServiceImpl**：混合分流服务实现
9. **实现统计收集器**：`TriageStatsCollector` 收集使用指标
10. **配置Spring Bean**：在 `TriageEngineConfig` 中配置所有组件

### 第三阶段：集成和验证
11. **更新应用配置**：在 `application.yml` 添加引擎参数
12. **创建集成测试**：验证各组件协同工作
13. **编译验证**：确保所有代码编译通过
14. **功能验证**：测试典型症状场景和边界条件

## 关键文件

### 需要创建的新文件
1. `src/main/java/com/student/service/triage/TriageService.java` - 主服务接口
2. `src/main/java/com/student/service/triage/impl/HybridTriageServiceImpl.java` - 核心实现
3. `src/main/java/com/student/service/triage/impl/SymptomNormalizer.java` - 症状标准化器
4. `src/main/java/com/student/service/triage/impl/RuleBasedTriageEngine.java` - 规则引擎
5. `src/main/java/com/student/service/triage/impl/EmergencySymptomDetector.java` - 急诊检测器
6. `src/main/java/com/student/service/triage/impl/LlmTriageAdapter.java` - LLM适配器
7. `src/main/java/com/student/service/triage/config/TriageConfig.java` - 引擎配置
8. `src/main/java/com/student/service/triage/stats/TriageStatsCollector.java` - 统计收集器

### 需要修改的现有文件
1. `src/main/resources/application.yml` - 添加引擎配置参数
2. `pom.xml` - 确认依赖完整性（无需新增，已有必要依赖）

## 技术细节

### 混合执行流程
```java
// 伪代码：混合分流核心逻辑（顺序执行+条件触发）
public DepartmentTriageResult executeHybridTriage(String symptoms) {
    // 1. 执行规则引擎（第一步）
    DepartmentTriageResult ruleResult = executeRuleEngine(symptoms);
    
    // 2. 检查规则引擎置信度
    if (ruleResult.getConfidence() >= config.getRuleEngineThreshold()) {
        return ruleResult; // 规则引擎置信度高，直接返回
    }
    
    // 3. 置信度低，触发LLM分析（条件触发）
    if (llmAdapter.isAvailable()) {
        try {
            // 异步执行LLM分析，带超时控制
            CompletableFuture<DepartmentTriageResult> llmFuture = CompletableFuture.supplyAsync(
                () -> llmAdapter.analyze(symptoms),
                executorService
            );
            
            DepartmentTriageResult llmResult = llmFuture.get(
                config.getLlmTimeout(), 
                TimeUnit.MILLISECONDS
            );
            
            // 4. 融合规则引擎和LLM结果
            return fuseResults(ruleResult, llmResult);
            
        } catch (TimeoutException e) {
            log.warn("LLM分析超时，使用规则引擎结果");
            return ruleResult; // LLM超时，返回规则引擎结果
        } catch (Exception e) {
            log.error("LLM分析异常", e);
            return ruleResult; // LLM异常，返回规则引擎结果
        }
    }
    
    // 5. LLM不可用，返回规则引擎结果（即使置信度低）
    return ruleResult;
}
```

### 配置参数
```yaml
imkqas:
  triage:
    engine:
      # 顺序执行配置
      rule-engine-threshold: 0.6  # 规则引擎置信度阈值，低于此值触发LLM
      llm-timeout: 3000           # LLM调用超时时间（毫秒）
      # 结果融合权重
      rule-engine-weight: 0.8
      llm-weight: 0.2
      # 规则引擎配置
      enable-fuzzy-match: true
      fuzzy-match-threshold: 0.7
      # 统计配置
      enable-stats: true
      stats-retention-days: 30
```

### 异常处理策略
1. **LLM服务异常**：降级为纯规则引擎模式，记录降级事件
2. **规则引擎异常**：使用LLM结果（如果可用），否则返回兜底建议
3. **超时异常**：使用已完成的组件结果，记录超时事件
4. **配置加载异常**：使用默认配置，记录配置错误

### 统计监控指标
1. **请求统计**：总请求数、规则引擎请求数、LLM请求数
2. **性能统计**：平均处理时间、规则引擎平均时间、LLM平均时间
3. **质量统计**：规则引擎成功率、LLM成功率、混合分流成功率
4. **业务统计**：急诊检测分布、科室推荐分布、置信度分布

## 验证策略

### 编译验证
```bash
mvn compile -DskipTests
```
确保所有新代码编译通过，无语法错误。

### 功能测试场景
1. **典型症状匹配**：测试常见症状到科室的准确匹配
2. **急诊症状检测**：验证不同紧急级别的正确识别
3. **混合分流验证**：测试规则引擎和LLM的协同工作
4. **降级机制验证**：模拟LLM不可用场景，验证降级逻辑
5. **批量处理验证**：测试同时处理多个症状的性能和准确性

### 边界条件测试
1. **空症状输入**：处理空字符串或null值
2. **超长症状描述**：处理500字符以上的症状描述
3. **罕见症状**：测试未配置症状的处理
4. **并发请求**：测试多线程环境下的稳定性

## 成功标准

1. **代码质量**：符合项目编码规范，包含完整的中文注释
2. **功能完整**：实现所有要求的特性（混合架构、急诊检测、批量处理等）
3. **性能达标**：单次分流响应时间 < 5秒，批量处理支持20个症状
4. **可靠性**：具备完善的异常处理和降级机制
5. **可维护性**：配置驱动，参数可调，便于后续优化

## 风险缓解

1. **LLM服务依赖风险**：通过多层降级机制确保基本功能可用
2. **配置错误风险**：提供默认配置，记录配置加载异常
3. **性能风险**：设置超时控制，防止单点故障影响整体服务
4. **扩展性风险**：模块化设计，支持后续添加新的分析引擎

## 后续步骤

计划批准后，将按照以下顺序实施：
1. 创建包结构和基础接口
2. 实现各个引擎组件
3. 实现混合协调服务
4. 配置Spring Bean和参数
5. 创建集成测试
6. 编译验证和功能测试