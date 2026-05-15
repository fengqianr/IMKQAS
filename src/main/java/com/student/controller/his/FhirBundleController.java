package com.student.controller.his;

import com.student.dto.ApiResponse;
import com.student.service.his.FhirBundleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.web.bind.annotation.*;

/**
 * FHIR Bundle统一入口控制器
 * Bundle作为HIS接口统一写入入口，支持transaction和batch两种模式
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/his/fhir")
@RequiredArgsConstructor
public class FhirBundleController {

    private final FhirBundleService service;

    /**
     * Bundle统一入口
     * 根据Bundle.type自动选择transaction或batch模式
     */
    @PostMapping("/Bundle")
    public ApiResponse<Bundle> processBundle(@RequestBody Bundle bundle) {
        service.validateBundle(bundle);
        Bundle result = service.process(bundle);
        log.info("Bundle处理完成: type={}, entries={}", bundle.getType(), bundle.getEntry().size());
        return ApiResponse.success(result);
    }

    /**
     * Transaction模式 — 原子性操作
     */
    @PostMapping("/Bundle/transaction")
    public ApiResponse<Bundle> processTransaction(@RequestBody Bundle bundle) {
        bundle.setType(Bundle.BundleType.TRANSACTION);
        service.validateBundle(bundle);
        Bundle result = service.processTransaction(bundle);
        log.info("Bundle事务处理完成: entries={}", bundle.getEntry().size());
        return ApiResponse.success(result);
    }

    /**
     * Batch模式 — 逐条独立执行
     */
    @PostMapping("/Bundle/batch")
    public ApiResponse<Bundle> processBatch(@RequestBody Bundle bundle) {
        bundle.setType(Bundle.BundleType.BATCH);
        service.validateBundle(bundle);
        Bundle result = service.processBatch(bundle);
        log.info("Bundle批处理完成: entries={}", bundle.getEntry().size());
        return ApiResponse.success(result);
    }
}
