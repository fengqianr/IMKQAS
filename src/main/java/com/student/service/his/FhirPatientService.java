package com.student.service.his;

import org.hl7.fhir.r4.model.Patient;

import java.util.List;
import java.util.Optional;

/**
 * FHIR患者资源服务接口
 * 为后续HIS对接预留，当前使用本地缓存实现
 *
 * @author 系统
 * @version 1.0
 */
public interface FhirPatientService {

    /**
     * 根据FHIR ID查询患者
     */
    Optional<Patient> findByFhirId(String fhirId);

    /**
     * 根据身份证号/病历号查询患者
     */
    Optional<Patient> findByIdentifier(String system, String value);

    /**
     * 按姓名模糊搜索患者
     */
    List<Patient> searchByName(String name, int page, int size);

    /**
     * 根据手机号查询患者
     */
    Optional<Patient> findByPhone(String phone);

    /**
     * 保存或更新患者信息（本地缓存）
     */
    Patient save(Patient patient, String source);

    /**
     * 逻辑删除患者
     */
    boolean deleteByFhirId(String fhirId);

    /**
     * 统计本地缓存患者数
     */
    long count();
}
