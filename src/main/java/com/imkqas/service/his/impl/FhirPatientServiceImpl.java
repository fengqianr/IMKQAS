package com.imkqas.service.his.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.imkqas.dto.his.FhirConverter;
import com.imkqas.entity.his.FhirPatientCache;
import com.imkqas.exception.BusinessException;
import com.imkqas.mapper.his.FhirPatientCacheMapper;
import com.imkqas.service.his.FhirPatientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * FHIR患者资源服务实现
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FhirPatientServiceImpl implements FhirPatientService {

    private final FhirPatientCacheMapper mapper;
    private final FhirConverter converter;

    @Override
    public Optional<Patient> findByFhirId(String fhirId) {
        FhirPatientCache cache = mapper.selectOne(
                new LambdaQueryWrapper<FhirPatientCache>()
                        .eq(FhirPatientCache::getFhirId, fhirId));
        if (cache == null) return Optional.empty();
        return Optional.of(converter.toFhirPatient(cache));
    }

    @Override
    public Optional<FhirPatientCache> findCacheByFhirId(String fhirId) {
        FhirPatientCache cache = mapper.selectOne(
                new LambdaQueryWrapper<FhirPatientCache>()
                        .eq(FhirPatientCache::getFhirId, fhirId));
        return Optional.ofNullable(cache);
    }

    @Override
    public Optional<Patient> findByIdentifier(String system, String value) {
        FhirPatientCache cache = mapper.selectOne(
                new LambdaQueryWrapper<FhirPatientCache>()
                        .eq(FhirPatientCache::getIdentifierSystem, system)
                        .eq(FhirPatientCache::getIdentifierValue, value));
        if (cache == null) return Optional.empty();
        return Optional.of(converter.toFhirPatient(cache));
    }

    @Override
    public List<Patient> searchByName(String name, int page, int size) {
        Page<FhirPatientCache> pageResult = mapper.selectPage(
                new Page<>(page + 1, size),
                new LambdaQueryWrapper<FhirPatientCache>()
                        .like(FhirPatientCache::getFamilyName, name)
                        .or().like(FhirPatientCache::getGivenName, name));
        return pageResult.getRecords().stream()
                .map(converter::toFhirPatient)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Patient> findByPhone(String phone) {
        FhirPatientCache cache = mapper.selectOne(
                new LambdaQueryWrapper<FhirPatientCache>()
                        .eq(FhirPatientCache::getPhone, phone));
        if (cache == null) return Optional.empty();
        return Optional.of(converter.toFhirPatient(cache));
    }

    @Override
    @Transactional
    public Patient save(Patient patient, String source) {
        FhirPatientCache cache = converter.toPatientCache(patient, source);
        FhirPatientCache existing = mapper.selectOne(
                new LambdaQueryWrapper<FhirPatientCache>()
                        .eq(FhirPatientCache::getFhirId, cache.getFhirId()));
        if (existing != null) {
            cache.setId(existing.getId());
            mapper.updateById(cache);
            log.info("更新患者: fhirId={}", cache.getFhirId());
        } else {
            mapper.insert(cache);
            log.info("新增患者: fhirId={}", cache.getFhirId());
        }
        return patient;
    }

    @Override
    @Transactional
    public void upsertLocalPatient(Long userId, String phone, String name, String gender) {
        // 健康档案同步：只更新姓名/电话/性别，保留已有的身份派生字段
        // （出生日期/证件号/家庭住址），避免健康档案保存把个人中心同步的身份字段清空。
        FhirPatientCache existing = mapper.selectOne(
                new LambdaQueryWrapper<FhirPatientCache>()
                        .eq(FhirPatientCache::getFhirId, "pat-" + userId));
        LocalDate birthDate = existing != null ? existing.getBirthDate() : null;
        String idCard = existing != null ? existing.getIdentifierValue() : null;
        String address = existing != null ? existing.getAddressText() : null;
        upsertLocalPatient(userId, phone, name, gender, birthDate, idCard, address);
    }

    @Override
    @Transactional
    public void upsertLocalPatient(Long userId, String phone, String name, String gender,
                                   LocalDate birthDate, String idCard, String address) {
        String fhirId = "pat-" + userId;
        // 证件号唯一性预检（排除自身），命中抛业务异常由全局处理器转 400。
        // 极端并发下预检后另一事务先插入，由 uk_identifier 唯一索引兜底。
        if (idCard != null && !idCard.isBlank()) {
            FhirPatientCache conflict = mapper.selectOne(
                    new LambdaQueryWrapper<FhirPatientCache>()
                            .eq(FhirPatientCache::getIdentifierSystem, IDENTIFIER_SYSTEM_IDCARD)
                            .eq(FhirPatientCache::getIdentifierValue, idCard)
                            .ne(FhirPatientCache::getFhirId, fhirId)
                            .last("LIMIT 1"));
            if (conflict != null) {
                throw new BusinessException("该证件号已被其他患者登记，请核对后重试");
            }
        }

        FhirPatientCache cache = mapper.selectOne(
                new LambdaQueryWrapper<FhirPatientCache>()
                        .eq(FhirPatientCache::getFhirId, fhirId));
        if (cache == null) {
            cache = FhirPatientCache.builder().fhirId(fhirId).build();
        }
        // 中文姓名整串放入 givenName，医生端按姓名 LIKE 搜索可命中
        boolean hasIdCard = idCard != null && !idCard.isBlank();
        cache.setGivenName(name);
        cache.setPhone(phone);
        cache.setGender(mapGender(gender));
        cache.setBirthDate(birthDate);
        cache.setIdentifierSystem(hasIdCard ? IDENTIFIER_SYSTEM_IDCARD : null);
        cache.setIdentifierValue(hasIdCard ? idCard : null); // 清空时置 null，uk_identifier 允许多个 NULL
        cache.setAddressText(address);
        cache.setLocalUserId(userId);
        cache.setSource("LOCAL");
        cache.setLastUpdated(java.time.LocalDateTime.now());
        // resource_json 为 NOT NULL 列：优先序列化完整 FHIR Patient，失败则最小 JSON 兜底
        String resourceJson = null;
        try {
            resourceJson = converter.toJson(converter.toFhirPatient(cache));
        } catch (Throwable ignored) {
            // 序列化失败时忽略，使用最小 JSON 兜底
        }
        if (resourceJson == null) {
            resourceJson = "{\"resourceType\":\"Patient\",\"id\":\"" + fhirId + "\"}";
        }
        cache.setResourceJson(resourceJson);

        if (cache.getId() != null) {
            mapper.updateById(cache);
            log.info("更新本地患者缓存: fhirId={}, name={}, birthDate={}, idCard={}",
                    fhirId, name, birthDate, hasIdCard ? idCard : null);
        } else {
            mapper.insert(cache);
            log.info("新增本地患者缓存: fhirId={}, name={}, birthDate={}, idCard={}",
                    fhirId, name, birthDate, hasIdCard ? idCard : null);
        }
    }

    /**
     * 性别映射：前端 MALE/FEMALE/OTHER -> FHIR male/female/other
     *
     * @param gender 健康档案性别枚举
     * @return FHIR 性别编码
     */
    private String mapGender(String gender) {
        if (gender == null) {
            return "unknown";
        }
        return switch (gender.toUpperCase()) {
            case "MALE" -> "male";
            case "FEMALE" -> "female";
            case "OTHER" -> "other";
            default -> "unknown";
        };
    }

    @Override
    @Transactional
    public void updatePhone(Long userId, String newPhone) {
        // 仅更新联系电话，不改动姓名/性别/出生日期/证件号等身份字段，
        // 避免管理员改手机号时破坏患者其他资料。
        FhirPatientCache cache = mapper.selectOne(
                new LambdaQueryWrapper<FhirPatientCache>()
                        .eq(FhirPatientCache::getFhirId, "pat-" + userId));
        if (cache == null) {
            // 缓存行不存在（如仅注册未完善身份/档案），无需同步
            return;
        }
        cache.setPhone(newPhone);
        cache.setLastUpdated(java.time.LocalDateTime.now());
        // resource_json 同步新手机号：序列化失败保留原 JSON
        try {
            cache.setResourceJson(converter.toJson(converter.toFhirPatient(cache)));
        } catch (Throwable ignored) {
            // 序列化失败时保留原 resource_json
        }
        mapper.updateById(cache);
        log.info("更新本地患者缓存手机号: fhirId=pat-{}, phone={}", userId, newPhone);
    }

    @Override
    public boolean deleteByFhirId(String fhirId) {
        int deleted = mapper.delete(
                new LambdaQueryWrapper<FhirPatientCache>()
                        .eq(FhirPatientCache::getFhirId, fhirId));
        return deleted > 0;
    }

    @Override
    public long count() {
        // 与医生端列表口径一致：仅统计有姓名（可被姓名检索）的患者。
        // 无姓名缓存行（如仅填性别未填姓名的本地缓存）会被医生端列表的姓名 LIKE 检索排除，
        // 若总数按全部行统计会出现「总数 N 列表 N-1」不一致。
        return mapper.selectCount(new LambdaQueryWrapper<FhirPatientCache>()
                .isNotNull(FhirPatientCache::getGivenName)
                .or().isNotNull(FhirPatientCache::getFamilyName));
    }
}
