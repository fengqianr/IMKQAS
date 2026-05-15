package com.student.service.his;

import org.hl7.fhir.r4.model.Condition;

import java.util.List;
import java.util.Optional;

/**
 * FHIR病情资源服务接口
 *
 * @author 系统
 * @version 1.0
 */
public interface FhirConditionService {

    Optional<Condition> findByFhirId(String fhirId);

    List<Condition> findByPatient(String patientFhirId, int page, int size);

    List<Condition> findByPatientAndStatus(String patientFhirId,
                                            String clinicalStatus, int page, int size);

    List<Condition> findByCode(String patientFhirId, String codeSystem,
                                String codeValue);

    Condition save(Condition condition, String source);

    boolean deleteByFhirId(String fhirId);
}
