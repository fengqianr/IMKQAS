package com.student.service.his;

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

    List<QuestionnaireResponse> findByPatient(String patientFhirId, int page, int size);

    List<QuestionnaireResponse> findByQuestionnaire(String questionnaireId,
                                                     int page, int size);

    List<QuestionnaireResponse> findByPatientAndQuestionnaire(
            String patientFhirId, String questionnaireId, int page, int size);

    QuestionnaireResponse save(QuestionnaireResponse qr, String source);

    boolean deleteByFhirId(String fhirId);
}
