package com.student.controller.his;

import com.student.dto.ApiResponse;
import com.student.service.his.FhirObservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Observation;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * FHIR观察资源控制器
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/his/fhir/Observation")
@RequiredArgsConstructor
public class FhirObservationController {

    private final FhirObservationService service;

    @GetMapping("/{fhirId}")
    @SuppressWarnings("unchecked")
    public ApiResponse<Observation> getByFhirId(@PathVariable String fhirId) {
        return service.findByFhirId(fhirId)
                .map(ApiResponse::success)
                .orElseGet(() -> (ApiResponse<Observation>) (Object) ApiResponse.error("观察记录不存在: " + fhirId));
    }

    @GetMapping("/by-patient")
    public ApiResponse<List<Observation>> findByPatient(@RequestParam String patientFhirId,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.findByPatient(patientFhirId, page, size));
    }

    @GetMapping("/by-code")
    public ApiResponse<List<Observation>> findByCode(@RequestParam String patientFhirId,
                                                      @RequestParam String codeSystem,
                                                      @RequestParam String codeValue,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.findByCode(patientFhirId, codeSystem, codeValue, page, size));
    }

    @GetMapping("/by-category")
    public ApiResponse<List<Observation>> findByCategory(@RequestParam String patientFhirId,
                                                          @RequestParam String category,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.findByCategory(patientFhirId, category, page, size));
    }

    @PostMapping
    public ApiResponse<Observation> save(@RequestBody Observation observation,
                                          @RequestParam(defaultValue = "HIS") String source) {
        Observation saved = service.save(observation, source);
        log.info("保存观察记录: fhirId={}", saved.getId());
        return ApiResponse.success(saved);
    }

    @PostMapping("/batch")
    public ApiResponse<List<Observation>> saveBatch(@RequestBody List<Observation> observations,
                                                     @RequestParam(defaultValue = "HIS") String source) {
        List<Observation> saved = service.saveBatch(observations, source);
        log.info("批量保存观察记录: count={}", saved.size());
        return ApiResponse.success(saved);
    }

    @DeleteMapping("/{fhirId}")
    @SuppressWarnings("unchecked")
    public ApiResponse<Void> delete(@PathVariable String fhirId) {
        boolean deleted = service.deleteByFhirId(fhirId);
        return deleted ? ApiResponse.success() : (ApiResponse<Void>) (Object) ApiResponse.error("观察记录不存在或删除失败");
    }
}
