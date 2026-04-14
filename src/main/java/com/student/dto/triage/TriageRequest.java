package com.student.dto.triage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 科室分流请求
 * 用于接收用户症状描述并进行科室分流
 *
 * @author 系统生成
 * @version 1.0
 */
@Data
public class TriageRequest {
    @NotBlank(message = "症状描述不能为空")
    @Size(max = 500, message = "症状描述不能超过500字符")
    private String symptoms;

    private Long userId;
    private boolean includeEmergencyCheck = true;

    /**
     * 获取脱敏症状（用于日志）
     */
    public String getMaskedSymptoms() {
        if (symptoms == null) return null;
        if (symptoms.length() <= 10) return symptoms;
        return symptoms.substring(0, 10) + "...";
    }
}