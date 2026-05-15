package com.student.service.his;

import org.hl7.fhir.r4.model.Bundle;

/**
 * FHIR Bundle统一入口服务
 * Bundle是传输容器，不单独建表，资源分别落入对应缓存表
 *
 * @author 系统
 * @version 1.0
 */
public interface FhirBundleService {

    /**
     * 处理Bundle（transaction模式 — 原子性，全部成功或全部回滚）
     */
    Bundle processTransaction(Bundle bundle);

    /**
     * 处理Bundle（batch模式 — 逐条独立执行，返回结果Bundle含每个条目的状态）
     */
    Bundle processBatch(Bundle bundle);

    /**
     * 校验Bundle结构完整性
     */
    void validateBundle(Bundle bundle);

    /**
     * 自动模式：根据Bundle.type选择transaction或batch
     */
    Bundle process(Bundle bundle);
}
