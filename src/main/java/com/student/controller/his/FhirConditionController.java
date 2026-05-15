package com.student.controller.his;

import com.student.dto.ApiResponse;
import com.student.service.his.FhirConditionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Condition;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * FHIR病情资源控制器
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/his/fhir/Condition")
@RequiredArgsConstructor
public class FhirConditionController {

    private final FhirConditionService service;

    @GetMapping("/{fhirId}")
    @SuppressWarnings("unchecked")
    public ApiResponse<Condition> getByFhirId(@PathVariable String fhirId) {
        return service.findByFhirId(fhirId)
                .map(ApiResponse::success)
                .orElseGet(() -> (ApiResponse<Condition>) (Object) ApiResponse.error("病情记录不存在: " + fhirId));
    }

    @GetMapping("/by-patient")
    public ApiResponse<List<Condition>> findByPatient(@RequestParam String patientFhirId,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.findByPatient(patientFhirId, page, size));
    }

    @GetMapping("/by-status")
    public ApiResponse<List<Condition>> findByPatientAndStatus(@RequestParam String patientFhirId,
                                                                @RequestParam String clinicalStatus,
                                                                @RequestParam(defaultValue = "1") int page,
                                                                @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.findByPatientAndStatus(patientFhirId, clinicalStatus, page, size));
    }

    @GetMapping("/by-code")
    public ApiResponse<List<Condition>> findByCode(@RequestParam String patientFhirId,
                                                    @RequestParam String codeSystem,
                                                    @RequestParam String codeValue) {
        return ApiResponse.success(service.findByCode(patientFhirId, codeSystem, codeValue));
    }

    @PostMapping
    public ApiResponse<Condition> save(@RequestBody Condition condition,
                                        @RequestParam(defaultValue = "HIS") String source) {
        Condition saved = service.save(condition, source);
        log.info("保存病情记录: fhirId={}", saved.getId());
        return ApiResponse.success(saved);
    }

    @DeleteMapping("/{fhirId}")
    @SuppressWarnings("unchecked")
    public ApiResponse<Void> delete(@PathVariable String fhirId) {
        boolean deleted = service.deleteByFhirId(fhirId);
        return deleted ? ApiResponse.success() : (ApiResponse<Void>) (Object) ApiResponse.error("病情记录不存在或删除失败");
    }
}
