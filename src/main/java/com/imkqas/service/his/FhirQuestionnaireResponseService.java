package com.imkqas.service.his;

import com.imkqas.entity.his.FhirQuestionnaireResponseCache;
import org.hl7.fhir.r4.model.QuestionnaireResponse;

import java.util.List;
import java.util.Optional;

/**
 * FHIR问卷回答资源服务接口
 *
 * @author 系统
 * @version 1.0
 */
public interface FhirQuestionnaireResponseService {

    Optional<QuestionnaireResponse> findByFhirId(String fhirId);

    /**
     * 按本地用户ID查询其全部问卷记录（按填写时间倒序）。
     * 本地问卷落库时 patient_fhir_id 存在历史格式差异（null / 'Patient/pat-x' / 'pat-x'），
     * 医生端按 local_user_id 关联可稳定取全，规避 patient_fhir_id 格式不一致问题。
     *
     * @param localUserId 本地用户ID
     * @return 问卷缓存实体列表
     */
    List<FhirQuestionnaireResponseCache> findByLocalUserId(Long localUserId);

    List<QuestionnaireResponse> findByPatient(String patientFhirId, int page, int size);

    List<QuestionnaireResponse> findByQuestionnaire(String questionnaireId,
                                                     int page, int size);

    List<QuestionnaireResponse> findByPatientAndQuestionnaire(
            String patientFhirId, String questionnaireId, int page, int size);

    QuestionnaireResponse save(QuestionnaireResponse qr, String source);

    boolean deleteByFhirId(String fhirId);
}
