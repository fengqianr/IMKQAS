package com.student.dto.triage;

import com.student.model.triage.DepartmentKnowledge;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 科室知识库配置
 */
@Data
public class DepartmentKnowledgeBase {
    private List<DepartmentKnowledge> departments;
    private Map<String, List<String>> symptomSynonyms;
    private String version = "1.0";
    private String lastUpdated;

    /**
     * 根据科室ID查找科室
     */
    public DepartmentKnowledge findDepartmentById(String departmentId) {
        if (departments == null) return null;
        return departments.stream()
                .filter(dept -> departmentId.equals(dept.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取急诊科室列表
     */
    public List<DepartmentKnowledge> getEmergencyDepartments() {
        if (departments == null) return List.of();
        return departments.stream()
                .filter(DepartmentKnowledge::isEmergency)
                .toList();
    }
}