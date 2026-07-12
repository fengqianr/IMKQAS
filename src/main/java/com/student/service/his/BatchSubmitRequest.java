package com.student.service.his;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 纯表单模式批量提交请求
 * 前端收集所有剩余题目的答案后一次性提交
 * key: linkId（题目唯一标识），value: code（选项编码）
 *
 * @author 系统
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchSubmitRequest {
    /** 答案映射: linkId → 选项编码 */
    private Map<String, String> answers;
}
