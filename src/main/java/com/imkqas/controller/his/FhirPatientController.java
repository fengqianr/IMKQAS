package com.imkqas.controller.his;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imkqas.dto.ApiResponse;
import com.imkqas.entity.User;
import com.imkqas.entity.his.FhirPatientCache;
import com.imkqas.entity.his.FhirQuestionnaireResponseCache;
import com.imkqas.service.common.UserService;
import com.imkqas.service.his.FhirPatientService;
import com.imkqas.service.his.FhirQuestionnaireResponseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final UserService userService;
    private final FhirQuestionnaireResponseService questionnaireResponseService;
    // Spring 管理的 ObjectMapper（带 JavaTimeModule），解析健康档案 JSON 与序列化 LocalDateTime
    private final ObjectMapper objectMapper;

    @GetMapping("/{fhirId}")
    @SuppressWarnings("unchecked")
    public ApiResponse<Patient> getByFhirId(@PathVariable String fhirId) {
        return service.findByFhirId(fhirId)
                .map(ApiResponse::success)
                .orElseGet(() -> (ApiResponse<Patient>) (Object) ApiResponse.error("患者不存在: " + fhirId));
    }

    /**
     * 患者档案概览：一次返回 FHIR Patient + 健康档案 + 问卷记录。
     * 本地患者（LOCAL）通过 fhir_patient_cache.local_user_id 关联 users.health_profile 与问卷记录，
     * 问卷记录按 local_user_id 查询可规避 patient_fhir_id 历史格式不一致（null/'Patient/pat-x'/pat-x）漏查问题。
     *
     * @param fhirId FHIR患者ID（如 pat-4）
     * @return 患者概览：{patient, localUserId, hasHealthProfile, healthProfile, questionnaireResponses}
     */
    @GetMapping("/{fhirId}/overview")
    @SuppressWarnings("unchecked")
    public ApiResponse<Map<String, Object>> overview(@PathVariable String fhirId) {
        FhirPatientCache cache = service.findCacheByFhirId(fhirId).orElse(null);
        if (cache == null) {
            return (ApiResponse<Map<String, Object>>) (Object) ApiResponse.error("患者不存在: " + fhirId);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("patient", service.findByFhirId(fhirId).orElse(null));
        Long userId = cache.getLocalUserId();
        result.put("localUserId", userId);

        if (userId == null) {
            // 非本地患者（HIS 导入）无健康档案与本地问卷，返回空占位
            result.put("hasHealthProfile", false);
            result.put("healthProfile", null);
            result.put("questionnaireResponses", List.of());
            return ApiResponse.success(result);
        }

        // 健康档案（users.health_profile JSON 列）
        User user = userService.getById(userId);
        if (user != null && user.getHealthProfile() != null && !user.getHealthProfile().isEmpty()) {
            try {
                result.put("healthProfile", objectMapper.readValue(user.getHealthProfile(), Object.class));
                result.put("hasHealthProfile", true);
            } catch (Exception e) {
                log.warn("健康档案解析失败: userId={}", userId, e);
                result.put("hasHealthProfile", false);
                result.put("healthProfile", null);
            }
        } else {
            result.put("hasHealthProfile", false);
            result.put("healthProfile", null);
        }

        // 问卷记录（按 local_user_id，与患者端 /records 一致的数据源）
        result.put("questionnaireResponses",
                questionnaireResponseService.findByLocalUserId(userId).stream()
                        .map(this::toOverviewRecord)
                        .collect(Collectors.toList()));
        return ApiResponse.success(result);
    }

    /**
     * 问卷缓存实体 -> 医生端概览结构化记录
     *
     * @param r 问卷缓存实体
     * @return 概览字段（标题/评分/严重程度/填写时间等）
     */
    private Map<String, Object> toOverviewRecord(FhirQuestionnaireResponseCache r) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fhirId", r.getFhirId());
        map.put("sessionId", r.getSessionId());
        map.put("questionnaireId", r.getQuestionnaireId());
        map.put("questionnaireTitle", r.getQuestionnaireTitle());
        map.put("score", r.getTotalScore());
        map.put("severity", r.getScoreInterpretation());
        map.put("authoredDate", r.getAuthoredDate());
        map.put("status", r.getStatus());
        return map;
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
