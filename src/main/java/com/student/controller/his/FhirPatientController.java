package com.student.controller.his;

import com.student.dto.ApiResponse;
import com.student.service.his.FhirPatientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * FHIR患者资源控制器
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/his/fhir/Patient")
@RequiredArgsConstructor
public class FhirPatientController {

    private final FhirPatientService service;

    @GetMapping("/{fhirId}")
    @SuppressWarnings("unchecked")
    public ApiResponse<Patient> getByFhirId(@PathVariable String fhirId) {
        return service.findByFhirId(fhirId)
                .map(ApiResponse::success)
                .orElseGet(() -> (ApiResponse<Patient>) (Object) ApiResponse.error("患者不存在: " + fhirId));
    }

    @GetMapping("/search")
    public ApiResponse<List<Patient>> searchByName(@RequestParam String name,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size) {
        List<Patient> results = service.searchByName(name, page, size);
        return ApiResponse.success(results);
    }

    @GetMapping("/by-identifier")
    @SuppressWarnings("unchecked")
    public ApiResponse<Patient> findByIdentifier(@RequestParam String system,
                                                  @RequestParam String value) {
        return service.findByIdentifier(system, value)
                .map(ApiResponse::success)
                .orElseGet(() -> (ApiResponse<Patient>) (Object) ApiResponse.error("患者不存在"));
    }

    @GetMapping("/by-phone")
    @SuppressWarnings("unchecked")
    public ApiResponse<Patient> findByPhone(@RequestParam String phone) {
        return service.findByPhone(phone)
                .map(ApiResponse::success)
                .orElseGet(() -> (ApiResponse<Patient>) (Object) ApiResponse.error("患者不存在"));
    }

    @PostMapping
    public ApiResponse<Patient> save(@RequestBody Patient patient,
                                      @RequestParam(defaultValue = "HIS") String source) {
        Patient saved = service.save(patient, source);
        log.info("保存患者: fhirId={}, source={}", saved.getId(), source);
        return ApiResponse.success(saved);
    }

    @DeleteMapping("/{fhirId}")
    @SuppressWarnings("unchecked")
    public ApiResponse<Void> delete(@PathVariable String fhirId) {
        boolean deleted = service.deleteByFhirId(fhirId);
        return deleted ? ApiResponse.success() : (ApiResponse<Void>) (Object) ApiResponse.error("患者不存在或删除失败");
    }

    @GetMapping("/count")
    public ApiResponse<Long> count() {
        return ApiResponse.success(service.count());
    }
}
