package com.student.service.triage;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.student.dto.triage.BatchTriageRequest;
import com.student.dto.triage.TriageRequest;
import com.student.model.triage.DepartmentTriageResult;
import com.student.model.triage.TriageStats;

/**
 * 科室导诊服务接口
 * 负责症状到科室的智能分流，支持混合架构（规则引擎+LLM）和急诊症状检测
 *
 * @author 系统生成
 * @version 1.0
 */
public interface TriageService {

    /**
     * 单次症状分流
     * 根据症状描述推荐最合适的就诊科室，支持急诊症状检测
     *
     * @param request 分流请求，包含症状描述和用户ID
     * @return 分流结果，包含科室推荐、置信度和急诊检测结果
     */
    DepartmentTriageResult triage(TriageRequest request);

    /**
     * 批量症状分流
     * 同时处理多个症状描述，提高处理效率
     *
     * @param request 批量分流请求，包含症状列表
     * @return 分流结果列表，每个症状对应一个结果
     */
    List<DepartmentTriageResult> batchTriage(BatchTriageRequest request);

    /**
     * 异步症状分流
     * 非阻塞方式执行分流，适用于高并发场景
     *
     * @param request 分流请求
     * @return 异步任务，完成后返回分流结果
     */
    CompletableFuture<DepartmentTriageResult> triageAsync(TriageRequest request);

    /**
     * 获取服务统计信息
     * 包括请求总数、成功率、响应时间等指标
     *
     * @return 服务统计信息
     */
    TriageStats getStats();

    /**
     * 检查服务是否可用
     * 验证所有依赖组件（规则引擎、LLM等）的状态
     *
     * @return 服务可用状态
     */
    boolean isAvailable();

    /**
     * 分流模式枚举
     * 定义不同的分流策略模式
     */
    enum TriageMode {
        RULE_ENGINE("规则引擎模式", "完全依赖规则引擎进行分流"),
        LLM("LLM模式", "完全依赖大语言模型进行分流"),
        HYBRID("混合模式", "规则引擎为主，LLM为补充的混合分流"),
        FALLBACK("降级模式", "当主要组件不可用时使用的降级分流");

        private final String name;
        private final String description;

        TriageMode(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * 分流来源枚举
     * 标识分流结果的来源组件
     */
    enum TriageSource {
        RULE_ENGINE("规则引擎", "由规则引擎生成的分流结果"),
        LLM("大语言模型", "由LLM分析生成的分流结果"),
        HYBRID("混合分流", "规则引擎和LLM融合生成的分流结果"),
        FALLBACK("降级分流", "降级模式下生成的分流结果");

        private final String name;
        private final String description;

        TriageSource(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}