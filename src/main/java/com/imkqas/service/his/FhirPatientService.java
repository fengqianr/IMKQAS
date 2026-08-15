package com.imkqas.service.his;

import com.imkqas.entity.his.FhirPatientCache;
import org.hl7.fhir.r4.model.Patient;

import java.time.LocalDate;
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

    /** 证件号在 FHIR identifier.system 中的约定值（与前端 fhir.service.ts 的 IDENTIFIER_SYSTEM 一致） */
    String IDENTIFIER_SYSTEM_IDCARD = "idcard";

    /**
     * 根据FHIR ID查询患者
     */
    Optional<Patient> findByFhirId(String fhirId);

    /**
     * 根据FHIR ID查询患者缓存实体（含 local_user_id，用于关联健康档案与问卷记录）
     *
     * @param fhirId FHIR患者ID（如 pat-4）
     * @return 患者缓存实体
     */
    Optional<FhirPatientCache> findCacheByFhirId(String fhirId);

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
     * 根据本地用户健康档案创建或更新 FHIR 患者缓存。
     * 患者自助填写的健康档案不产生 FHIR 资源，导致医生端按姓名搜索不到患者；
     * 此方法以 pat-{userId} 为 fhir_id 维护本地患者缓存，供医生端检索。
     *
     * @param userId 本地用户ID
     * @param phone  联系电话
     * @param name   患者姓名
     * @param gender 性别（MALE/FEMALE/OTHER，映射为 FHIR male/female/other）
     */
    void upsertLocalPatient(Long userId, String phone, String name, String gender);

    /**
     * 按完整身份信息创建或更新本地患者缓存（含出生日期/证件号/家庭住址）。
     * 证件号采用约定 system='idcard'（{@link #IDENTIFIER_SYSTEM_IDCARD}）；
     * 证件号与既有患者冲突时抛 BusinessException。
     *
     * @param userId    本地用户ID
     * @param phone     联系电话
     * @param name      患者姓名
     * @param gender    性别（MALE/FEMALE/OTHER，映射为 FHIR male/female/other）
     * @param birthDate 出生日期（可空）
     * @param idCard    证件号（可空；非空时校验唯一性）
     * @param address   家庭住址（可空）
     */
    void upsertLocalPatient(Long userId, String phone, String name, String gender,
                            LocalDate birthDate, String idCard, String address);

    /**
     * 更新本地患者缓存的联系电话（管理员端修改用户手机号后调用）。
     * 避免 fhir_patient_cache.phone 停留旧值，导致医生端按新手机号检索不到患者。
     *
     * @param userId   本地用户ID
     * @param newPhone 新的联系电话
     */
    void updatePhone(Long userId, String newPhone);

    /**
     * 逻辑删除患者
     */
    boolean deleteByFhirId(String fhirId);

    /**
     * 统计本地缓存患者数
     */
    long count();
}
