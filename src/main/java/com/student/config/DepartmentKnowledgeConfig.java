package com.student.config;

import com.student.dto.triage.DepartmentKnowledgeBase;
import com.student.model.triage.DepartmentKnowledge;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 科室知识库配置类
 * 加载科室知识库配置和症状同义词配置
 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "imkqas.triage")
@Data
public class DepartmentKnowledgeConfig {

    private String knowledgeBasePath = "config/department-knowledge.yml";
    private String synonymsPath = "config/symptom-synonyms.yml";
    private boolean enableCache = true;
    private int cacheTtl = 3600; // 1小时
    private double ruleEngineThreshold = 0.8;
    private int llmFallbackTimeout = 3000; // 3秒

    /**
     * 加载科室知识库配置
     */
    @Bean
    public DepartmentKnowledgeBase departmentKnowledgeBase() {
        try {
            Yaml yaml = new Yaml();
            ClassPathResource resource = new ClassPathResource(knowledgeBasePath);

            if (!resource.exists()) {
                log.warn("科室知识库配置文件不存在: {}", knowledgeBasePath);
                return createDefaultKnowledgeBase();
            }

            try (InputStream inputStream = resource.getInputStream()) {
                DepartmentKnowledgeBase knowledgeBase = yaml.loadAs(inputStream, DepartmentKnowledgeBase.class);
                log.info("成功加载科室知识库配置，共{}个科室",
                    knowledgeBase.getDepartments() != null ? knowledgeBase.getDepartments().size() : 0);
                return knowledgeBase;
            }
        } catch (Exception e) {
            log.error("加载科室知识库配置失败: {}", e.getMessage(), e);
            return createDefaultKnowledgeBase();
        }
    }

    /**
     * 加载症状同义词配置
     */
    @Bean
    public Map<String, List<String>> symptomSynonyms() {
        try {
            Yaml yaml = new Yaml();
            ClassPathResource resource = new ClassPathResource(synonymsPath);

            if (!resource.exists()) {
                log.warn("症状同义词配置文件不存在: {}", synonymsPath);
                return Map.of();
            }

            try (InputStream inputStream = resource.getInputStream()) {
                Map<String, Object> data = yaml.load(inputStream);
                @SuppressWarnings("unchecked")
                Map<String, List<String>> synonyms = (Map<String, List<String>>) data.get("synonyms");

                int synonymCount = synonyms != null ? synonyms.size() : 0;
                log.info("成功加载症状同义词配置，共{}组同义词", synonymCount);
                return synonyms != null ? synonyms : Map.of();
            }
        } catch (Exception e) {
            log.error("加载症状同义词配置失败: {}", e.getMessage(), e);
            return Map.of();
        }
    }

    /**
     * 创建默认的科室知识库（兜底配置）
     */
    private DepartmentKnowledgeBase createDefaultKnowledgeBase() {
        log.warn("使用默认科室知识库配置");
        DepartmentKnowledgeBase knowledgeBase = new DepartmentKnowledgeBase();

        // 创建默认急诊科室
        DepartmentKnowledge emergencyDept = new DepartmentKnowledge();
        emergencyDept.setId("emergency");
        emergencyDept.setName("急诊科");
        emergencyDept.setSymptoms(List.of("剧烈胸痛", "意识丧失", "呼吸困难"));
        emergencyDept.setEmergency(true);
        emergencyDept.setEmergencyLevel("CRITICAL");
        emergencyDept.setDescription("急危重症抢救治疗");

        knowledgeBase.setDepartments(List.of(emergencyDept));
        knowledgeBase.setVersion("1.0-default");
        knowledgeBase.setLastUpdated(java.time.LocalDateTime.now().toString());

        return knowledgeBase;
    }
}