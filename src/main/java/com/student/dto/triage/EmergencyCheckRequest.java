package com.student.dto.triage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 急诊症状检查请求
 * 用于接收用户症状描述并进行急诊症状检查
 *
 * @author 系统生成
 * @version 1.0
 */
@Data
public class EmergencyCheckRequest {
    @NotBlank(message = "症状描述不能为空")
    @Size(max = 500, message = "症状描述不能超过500字符")
    private String symptoms;

    private Long userId;

    /**
     * 获取脱敏症状
     */
    public String getMaskedSymptoms() {
        if (symptoms == null) return null;
        if (symptoms.length() <= 10) return symptoms;
        return symptoms.substring(0, 10) + "...";
    }
}