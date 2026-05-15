package com.student.controller.his;

import com.student.dto.ApiResponse;
import com.student.service.his.FhirQuestionnaireResponseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * FHIR问卷回答资源控制器
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/his/fhir/QuestionnaireResponse")
@RequiredArgsConstructor
public class FhirQuestionnaireResponseController {

    private final FhirQuestionnaireResponseService service;

    @GetMapping("/{fhirId}")
    @SuppressWarnings("unchecked")
    public ApiResponse<QuestionnaireResponse> getByFhirId(@PathVariable String fhirId) {
        return service.findByFhirId(fhirId)
                .map(ApiResponse::success)
                .orElseGet(() -> (ApiResponse<QuestionnaireResponse>) (Object) ApiResponse.error("问卷回答不存在: " + fhirId));
    }

    @GetMapping("/by-patient")
    public ApiResponse<List<QuestionnaireResponse>> findByPatient(@RequestParam String patientFhirId,
                                                                   @RequestParam(defaultValue = "1") int page,
                                                                   @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.findByPatient(patientFhirId, page, size));
    }

    @GetMapping("/by-questionnaire")
    public ApiResponse<List<QuestionnaireResponse>> findByQuestionnaire(@RequestParam String questionnaireId,
                                                                         @RequestParam(defaultValue = "1") int page,
                                                                         @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.findByQuestionnaire(questionnaireId, page, size));
    }

    @GetMapping("/by-patient-and-questionnaire")
    public ApiResponse<List<QuestionnaireResponse>> findByPatientAndQuestionnaire(
            @RequestParam String patientFhirId,
            @RequestParam String questionnaireId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.findByPatientAndQuestionnaire(patientFhirId, questionnaireId, page, size));
    }

    @PostMapping
    public ApiResponse<QuestionnaireResponse> save(@RequestBody QuestionnaireResponse qr,
                                                    @RequestParam(defaultValue = "HIS") String source) {
        QuestionnaireResponse saved = service.save(qr, source);
        log.info("保存问卷回答: fhirId={}", saved.getId());
        return ApiResponse.success(saved);
    }

    @DeleteMapping("/{fhirId}")
    @SuppressWarnings("unchecked")
    public ApiResponse<Void> delete(@PathVariable String fhirId) {
        boolean deleted = service.deleteByFhirId(fhirId);
        return deleted ? ApiResponse.success() : (ApiResponse<Void>) (Object) ApiResponse.error("问卷回答不存在或删除失败");
    }
}
