package com.student.service.his;

import org.hl7.fhir.r4.model.Observation;

import java.util.List;
import java.util.Optional;

/**
 * FHIR观察资源服务接口
 *
 * @author 系统
 * @version 1.0
 */
public interface FhirObservationService {

    Optional<Observation> findByFhirId(String fhirId);

    List<Observation> findByPatient(String patientFhirId, int page, int size);

    List<Observation> findByCode(String patientFhirId, String codeSystem,
                                  String codeValue, int page, int size);

    List<Observation> findByCategory(String patientFhirId, String category,
                                     int page, int size);

    Observation save(Observation observation, String source);

    List<Observation> saveBatch(List<Observation> observations, String source);

    boolean deleteByFhirId(String fhirId);
}
