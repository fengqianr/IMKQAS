---
name: 科室导诊服务设计
description: IMKQAS项目阶段2第11天任务 - 混合分层架构的科室导诊服务
type: project
---

# 科室导诊服务设计

## 概述

### 项目背景
作为IMKQAS医疗知识问答系统阶段2（RAG核心）的第11天任务，实现症状到科室的导诊分流功能。用户描述症状后，系统推荐合适的就诊科室，展示医疗专业性，提升用户体验。

### 业务目标
1. **快速响应**：常见症状通过规则引擎实时匹配（<100ms）
2. **智能降级**：复杂/模糊症状调用LLM进行语义分析
3. **医疗安全**：急诊症状识别和紧急就医提醒
4. **成本控制**：80%请求走规则引擎，20%走LLM，使用Redis缓存

### 技术目标
- 集成现有`LlmService`，避免重复实现
- 复用现有`RedisService`进行结果缓存
- 遵循项目`RagConfig`配置管理模式
- 支持JSON知识库热更新

## 架构设计

### 混合分层架构
```
┌─────────────────────────────────────────┐
│          DepartmentTriageService         │
├─────────────────────────────────────────┤
│ 1. 症状预处理层                          │
│   - 症状文本清洗/标准化                  │
│   - 医学术语标准化                      │
│   - 同义词扩展                          │
├─────────────────────────────────────────┤
│ 2. 规则引擎层 (第一层)                   │
│   - JSON知识库快速匹配                   │
│   - 关键词/症状规则匹配                  │
│   - 置信度计算与阈值判断                 │
│   - 匹配失败或低置信度时触发LLM降级      │
├─────────────────────────────────────────┤
│ 3. LLM分析层 (第二层)                    │
│   - 调用现有LlmService                   │
│   - 复杂症状语义分析                     │
│   - 多科室概率分布生成                   │
│   - 急诊症状识别                         │
├─────────────────────────────────────────┤
│ 4. 结果后处理层                          │
│   - 结果格式化与排序                     │
│   - 急诊提醒标记                         │
│   - 结果缓存(Redis)                      │
│   - 知识库反馈学习（可选）               │
└─────────────────────────────────────────┘
```

### 核心设计原则
1. **性能优先**：80%常见症状走规则引擎（<100ms响应）
2. **智能降级**：规则引擎匹配失败或置信度<0.6时自动调用LLM
3. **成本控制**：利用Redis缓存LLM分析结果（TTL 1小时）
4. **医疗安全**：急诊症状识别和紧急就医提醒
5. **渐进优化**：LLM分析结果可反馈更新规则知识库

## 数据模型

### 1. 科室-症状知识库配置 (JSON/YAML)
```yaml
# src/main/resources/config/department-knowledge.yml
departments:
  - id: "cardiology"
    name: "心血管内科"
    symptoms: ["胸痛", "心悸", "气短", "头晕", "胸闷"]
    keywords: ["心脏", "血压", "心跳", "脉搏", "心电图"]
    priority: 1
    emergency: false
    description: "心血管系统疾病诊疗"
    
  - id: "gastroenterology"
    name: "消化内科"
    symptoms: ["腹痛", "腹泻", "恶心", "呕吐", "胃痛"]
    keywords: ["胃", "肠", "消化", "反酸", "便血"]
    priority: 2
    emergency: false
    
  - id: "emergency"
    name: "急诊科"
    symptoms: ["剧烈胸痛", "意识丧失", "呼吸困难", "大量出血", "高热惊厥"]
    keywords: ["晕倒", "昏迷", "窒息", "吐血", "休克"]
    priority: 0
    emergency: true
    emergencyLevel: "CRITICAL"

# 症状同义词库
symptomSynonyms:
  肚子疼: ["腹痛", "腹部疼痛"]
  头疼: ["头痛", "头部疼痛"]
  发烧: ["发热", "体温升高"]
  拉肚子: ["腹泻", "拉稀"]
  恶心想吐: ["恶心", "呕吐"]
```

### 2. 急诊症状分级规则
```java
// 急诊级别定义
enum EmergencyLevel {
    CRITICAL(0, "危急", "立即就医"),      // 立即就医
    HIGH(1, "高危", "2小时内就医"),       // 2小时内就医
    MEDIUM(2, "中危", "24小时内就医"),    // 24小时内就医
    LOW(3, "低危", "常规门诊")           // 常规门诊
    
    private final int priority;
    private final String levelName;
    private final String action;
}

// 急诊症状规则（可配置）
Map<EmergencyLevel, List<String>> emergencyRules = Map.of(
    EmergencyLevel.CRITICAL, List.of("剧烈胸痛", "意识丧失", "呼吸困难", "大出血", "窒息"),
    EmergencyLevel.HIGH, List.of("持续高热", "剧烈腹痛", "视力模糊", "言语不清", "惊厥"),
    EmergencyLevel.MEDIUM, List.of("反复呕吐", "皮疹", "关节肿痛", "尿频尿急", "持续咳嗽")
);
```

### 3. 数据实体类
```java
// 科室推荐结果
@Data
@AllArgsConstructor
@NoArgsConstructor
class DepartmentTriageResult {
    private String symptoms;                      // 原始症状
    private List<DepartmentRecommendation> recommendations; // 科室推荐列表
    private EmergencyCheckResult emergencyCheck;  // 急诊检查结果
    private double confidence;                    // 总体置信度 (0-1)
    private String source;                        // 结果来源：RULE/LLM
    private long processingTimeMs;                // 处理时间(毫秒)
    private String recommendationAdvice;          // 就诊建议文本
}

// 科室推荐项
@Data
@AllArgsConstructor
@NoArgsConstructor
class DepartmentRecommendation {
    private String departmentId;                  // 科室ID
    private String departmentName;                // 科室名称
    private double confidence;                    // 推荐置信度 (0-1)
    private String reasoning;                     // 推荐理由
    private List<String> matchedSymptoms;         // 匹配的症状
    private boolean emergencyDepartment;          // 是否为急诊科室
    private int priority;                         // 推荐优先级
}

// 急诊检查结果
@Data
@AllArgsConstructor
@NoArgsConstructor
class EmergencyCheckResult {
    private boolean isEmergency;                  // 是否为急诊
    private EmergencyLevel emergencyLevel;        // 紧急程度
    private List<String> emergencySymptoms;       // 识别的急诊症状
    private String immediateAction;               // 紧急行动建议
    private String warningMessage;                // 警告消息
}

// 科室知识库实体
@Data
class DepartmentKnowledge {
    private String id;
    private String name;
    private List<String> symptoms;
    private List<String> keywords;
    private int priority;
    private boolean emergency;
    private String emergencyLevel;
    private String description;
}
```

## 接口设计

### 1. 服务接口 (DepartmentTriageService)
```java
package com.student.service;

import java.util.List;

/**
 * 科室导诊服务接口
 * 实现症状到科室的智能分流功能
 */
public interface DepartmentTriageService {
    
    /**
     * 症状科室分流
     * @param symptoms 症状描述
     * @param includeEmergency 是否包含急诊检查
     * @return 科室推荐结果
     */
    DepartmentTriageResult triage(String symptoms, boolean includeEmergency);
    
    /**
     * 批量症状分流
     * @param symptomsList 症状描述列表
     * @return 科室推荐结果列表
     */
    List<DepartmentTriageResult> triageBatch(List<String> symptomsList);
    
    /**
     * 获取急诊症状识别结果
     * @param symptoms 症状描述
     * @return 急诊检查结果
     */
    EmergencyCheckResult checkEmergency(String symptoms);
    
    /**
     * 更新科室-症状知识库
     * @param knowledgeBase 知识库配置
     * @return 是否更新成功
     */
    boolean updateKnowledgeBase(DepartmentKnowledgeBase knowledgeBase);
    
    /**
     * 获取分流统计信息
     * @return 统计信息
     */
    TriageStats getStats();
    
    /**
     * 检查服务是否可用
     * @return 服务状态
     */
    boolean isAvailable();
}
```

### 2. REST API 端点 (MedicalTriageController)
```java
package com.student.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/medical/triage")
@Tag(name = "医疗导诊", description = "症状科室分流相关API")
public class MedicalTriageController {
    
    private final DepartmentTriageService triageService;
    
    @PostMapping("/department")
    @Operation(summary = "科室导诊分流", 
              description = "根据症状描述推荐就诊科室，支持急诊症状识别")
    public ResponseEntity<DepartmentTriageResult> triageDepartment(
            @RequestBody @Valid TriageRequest request) {
        DepartmentTriageResult result = triageService.triage(
            request.getSymptoms(), request.isIncludeEmergencyCheck());
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/emergency-check")
    @Operation(summary = "急诊症状检查", 
              description = "识别症状是否为急诊并分级，提供紧急就医建议")
    public ResponseEntity<EmergencyCheckResult> checkEmergency(
            @RequestBody @Valid EmergencyCheckRequest request) {
        EmergencyCheckResult result = triageService.checkEmergency(request.getSymptoms());
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/batch-triage")
    @Operation(summary = "批量症状分流", 
              description = "批量处理症状科室分流，提高处理效率")
    public ResponseEntity<List<DepartmentTriageResult>> batchTriage(
            @RequestBody @Valid BatchTriageRequest request) {
        List<DepartmentTriageResult> results = triageService.triageBatch(
            request.getSymptomsList());
        return ResponseEntity.ok(results);
    }
    
    @GetMapping("/knowledge-base")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "获取科室知识库", 
              description = "管理员查看科室-症状知识库配置")
    public ResponseEntity<DepartmentKnowledgeBase> getKnowledgeBase() {
        DepartmentKnowledgeBase knowledgeBase = triageService.getKnowledgeBase();
        return ResponseEntity.ok(knowledgeBase);
    }
    
    @PutMapping("/knowledge-base")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "更新科室知识库", 
              description = "管理员更新科室-症状知识库配置")
    public ResponseEntity<Boolean> updateKnowledgeBase(
            @RequestBody @Valid DepartmentKnowledgeBase knowledgeBase) {
        boolean success = triageService.updateKnowledgeBase(knowledgeBase);
        return ResponseEntity.ok(success);
    }
    
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "获取分流统计", 
              description = "获取科室导诊服务统计信息")
    public ResponseEntity<TriageStats> getStats() {
        TriageStats stats = triageService.getStats();
        return ResponseEntity.ok(stats);
    }
}
```

### 3. 请求/响应DTO
```java
// 请求DTO类
package com.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
class TriageRequest {
    @NotBlank(message = "症状描述不能为空")
    @Size(max = 500, message = "症状描述不能超过500字符")
    private String symptoms;
    
    private Long userId; // 可选，用于个性化（如年龄、性别）
    private boolean includeEmergencyCheck = true;
}

@Data
class EmergencyCheckRequest {
    @NotBlank(message = "症状描述不能为空")
    @Size(max = 500, message = "症状描述不能超过500字符")
    private String symptoms;
}

@Data
class BatchTriageRequest {
    @NotEmpty(message = "症状列表不能为空")
    @Size(max = 20, message = "批量处理最多支持20个症状")
    private List<String> symptomsList;
    
    private Long userId;
    private boolean includeEmergencyCheck = true;
}

// 响应DTO已在数据模型部分定义
```

## 实现细节

### 1. 核心实现类结构
```
src/main/java/com/student/service/DepartmentTriageService.java (接口)
src/main/java/com/student/service/impl/DepartmentTriageServiceImpl.java (实现)
src/main/java/com/student/controller/MedicalTriageController.java (控制器)
src/main/java/com/student/dto/ (请求/响应DTO)
src/main/java/com/student/model/ (数据模型)
src/main/resources/config/department-knowledge.yml (知识库配置)
src/main/resources/config/symptom-synonyms.yml (同义词配置)
```

### 2. 规则引擎实现
```java
@Component
class RuleBasedTriageEngine {
    
    private DepartmentKnowledgeBase knowledgeBase;
    private SymptomNormalizer symptomNormalizer;
    private RedisService redisService;
    
    /**
     * 规则匹配核心算法
     */
    public DepartmentTriageResult matchByRules(String normalizedSymptoms) {
        // 1. 关键词匹配：计算每个科室的匹配分数
        Map<String, Double> departmentScores = calculateKeywordScores(normalizedSymptoms);
        
        // 2. 症状匹配：精确症状匹配加分
        enhanceWithSymptomMatch(departmentScores, normalizedSymptoms);
        
        // 3. 生成推荐结果
        List<DepartmentRecommendation> recommendations = generateRecommendations(departmentScores);
        
        // 4. 计算总体置信度
        double confidence = calculateConfidence(departmentScores);
        
        return new DepartmentTriageResult(
            normalizedSymptoms, recommendations, null, confidence, "RULE", 
            System.currentTimeMillis(), generateAdvice(recommendations)
        );
    }
    
    /**
     * 置信度判断：是否需要LLM降级
     */
    public boolean requiresLlmFallback(double confidence) {
        return confidence < 0.6; // 置信度阈值可配置
    }
}
```

### 3. LLM集成实现
```java
@Component
class LlmTriageAdapter {
    
    private final LlmService llmService;
    private final RedisService redisService;
    
    /**
     * 调用LLM进行症状分析
     */
    public DepartmentTriageResult analyzeWithLlm(String symptoms) {
        // 检查缓存
        String cacheKey = generateCacheKey(symptoms);
        Object cached = redisService.get(cacheKey);
        if (cached instanceof DepartmentTriageResult) {
            return (DepartmentTriageResult) cached;
        }
        
        // 构建LLM Prompt
        String prompt = buildTriagePrompt(symptoms);
        
        // 调用LLM服务
        String llmResponse = llmService.generateAnswer(prompt, Collections.emptyList());
        
        // 解析LLM响应
        DepartmentTriageResult result = parseLlmResponse(llmResponse, symptoms);
        
        // 缓存结果 (TTL: 1小时)
        redisService.set(cacheKey, result, 3600L);
        
        return result;
    }
    
    private String buildTriagePrompt(String symptoms) {
        return String.format("""
            作为医疗专家，请根据以下症状描述推荐就诊科室：
            症状：%s
            
            请按以下格式输出：
            1. 主要推荐科室：[科室名称]，置信度：[0-1]，理由：[简短理由]
            2. 次要推荐科室：[科室名称]，置信度：[0-1]，理由：[简短理由]
            3. 是否急诊：[是/否]，紧急程度：[危急/高危/中危/低危]
            4. 紧急行动建议：[具体建议]
            
            请确保推荐基于标准医疗知识。
            """, symptoms);
    }
}
```

### 4. 急诊症状识别
```java
@Component
class EmergencySymptomDetector {
    
    private final Map<EmergencyLevel, List<String>> emergencyRules;
    
    public EmergencyCheckResult detect(String symptoms) {
        EmergencyLevel detectedLevel = EmergencyLevel.LOW;
        List<String> matchedSymptoms = new ArrayList<>();
        
        // 分级检查
        for (EmergencyLevel level : EmergencyLevel.values()) {
            List<String> levelSymptoms = emergencyRules.get(level);
            if (levelSymptoms != null) {
                for (String emergencySymptom : levelSymptoms) {
                    if (symptoms.contains(emergencySymptom)) {
                        if (level.getPriority() < detectedLevel.getPriority()) {
                            detectedLevel = level;
                        }
                        matchedSymptoms.add(emergencySymptom);
                    }
                }
            }
        }
        
        boolean isEmergency = detectedLevel != EmergencyLevel.LOW;
        
        return new EmergencyCheckResult(
            isEmergency, detectedLevel, matchedSymptoms,
            getActionForLevel(detectedLevel), getWarningMessage(detectedLevel)
        );
    }
}
```

## 配置管理

### 1. 应用配置
```yaml
# application.yml
imkqas:
  triage:
    enabled: true
    rule-engine:
      confidence-threshold: 0.6  # 低于此值触发LLM降级
      cache-enabled: true
      cache-ttl: 3600  # 缓存时间(秒)
    llm-fallback:
      enabled: true
      model: "medical-specialist"
      temperature: 0.3
      max-tokens: 500
    emergency-detection:
      enabled: true
      warning-message-enabled: true
    knowledge-base:
      path: "classpath:config/department-knowledge.yml"
      auto-reload: true
      reload-interval: 300  # 5分钟
```

### 2. 科室知识库配置类
```java
@Configuration
@ConfigurationProperties(prefix = "imkqas.triage.knowledge-base")
@Data
public class DepartmentKnowledgeConfig {
    private String path = "classpath:config/department-knowledge.yml";
    private boolean autoReload = true;
    private int reloadInterval = 300;
    
    @PostConstruct
    public void init() {
        loadKnowledgeBase();
        if (autoReload) {
            scheduleReload();
        }
    }
}
```

## 测试策略

### 1. 单元测试
```java
// DepartmentTriageServiceImplTest.java
@SpringBootTest
class DepartmentTriageServiceImplTest {
    
    @Autowired
    private DepartmentTriageService triageService;
    
    @Test
    void testTriageCommonSymptom() {
        // 测试常见症状（应走规则引擎）
        DepartmentTriageResult result = triageService.triage("头痛发热", true);
        assertNotNull(result);
        assertTrue(result.getConfidence() > 0.7);
        assertEquals("RULE", result.getSource());
        assertFalse(result.getEmergencyCheck().isEmergency());
    }
    
    @Test
    void testTriageComplexSymptom() {
        // 测试复杂症状（可能触发LLM降级）
        DepartmentTriageResult result = triageService.triage("间歇性头晕伴随耳鸣和视力模糊", true);
        assertNotNull(result);
        assertTrue(result.getRecommendations().size() > 0);
    }
    
    @Test
    void testEmergencyDetection() {
        // 测试急诊症状识别
        EmergencyCheckResult result = triageService.checkEmergency("剧烈胸痛呼吸困难");
        assertTrue(result.isEmergency());
        assertEquals(EmergencyLevel.CRITICAL, result.getEmergencyLevel());
    }
}
```

### 2. 集成测试
```java
// MedicalTriageControllerIntegrationTest.java
@WebMvcTest(MedicalTriageController.class)
@AutoConfigureMockMvc
class MedicalTriageControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void testTriageEndpoint() throws Exception {
        TriageRequest request = new TriageRequest();
        request.setSymptoms("腹痛腹泻");
        request.setIncludeEmergencyCheck(true);
        
        mockMvc.perform(post("/api/medical/triage/department")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symptoms").value("腹痛腹泻"))
                .andExpect(jsonPath("$.recommendations").isArray())
                .andExpect(jsonPath("$.confidence").isNumber());
    }
}
```

## 部署与监控

### 1. 健康检查端点
```java
@RestController
@RequestMapping("/actuator")
public class TriageHealthController {
    
    @GetMapping("/health/triage")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", triageService.isAvailable() ? "UP" : "DOWN");
        health.put("knowledgeBaseLoaded", knowledgeBase != null);
        health.put("ruleEngineEnabled", ruleEngine.isEnabled());
        health.put("llmFallbackEnabled", llmFallback.isEnabled());
        health.put("cacheHitRate", cacheService.getHitRate());
        return ResponseEntity.ok(health);
    }
}
```

### 2. 监控指标
- 请求量统计（规则引擎 vs LLM）
- 平均响应时间（分位数：p50, p90, p99）
- 缓存命中率
- LLM调用成功率/失败率
- 急诊症状识别统计

## 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| LLM API不稳定 | 高 | 实现本地规则引擎降级、结果缓存、重试机制 |
| 规则库覆盖不全 | 中 | 定期更新知识库、支持热加载、LLM结果反馈学习 |
| 急诊识别误报 | 高 | 多级验证、置信度阈值、人工审核机制 |
| 性能瓶颈 | 中 | 结果缓存、异步处理、监控告警 |
| 数据隐私 | 高 | 症状数据脱敏、访问控制、审计日志 |

## 后续优化方向

1. **个性化推荐**：结合用户年龄、性别、病史优化科室推荐
2. **地理位置集成**：推荐附近医院和科室
3. **症状图谱构建**：建立症状-疾病-科室关联图谱
4. **机器学习优化**：基于历史数据训练推荐模型
5. **多模态输入**：支持图片（皮疹等）症状识别

---
**设计状态**: ✅ 完成  
**技术评审**: □ 待评审  
**用户确认**: ✅ 已确认
**下一步**: 创建实现计划并开始编码