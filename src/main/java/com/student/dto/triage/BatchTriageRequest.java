package com.student.dto.triage;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 批量症状分流请求
 * 用于接收批量症状描述并进行科室分流
 *
 * @author 系统生成
 * @version 1.0
 */
@Data
public class BatchTriageRequest {
    @NotEmpty(message = "症状列表不能为空")
    @Size(max = 20, message = "批量处理最多支持20个症状")
    private List<String> symptomsList;

    private Long userId;
    private boolean includeEmergencyCheck = true;

    /**
     * 获取请求大小
     */
    public int getBatchSize() {
        return symptomsList != null ? symptomsList.size() : 0;
    }
}