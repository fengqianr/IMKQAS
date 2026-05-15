package com.student.service.his.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.student.dto.his.FhirConverter;
import com.student.entity.his.FhirPatientCache;
import com.student.mapper.his.FhirPatientCacheMapper;
import com.student.service.his.FhirPatientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public boolean deleteByFhirId(String fhirId) {
        int deleted = mapper.delete(
                new LambdaQueryWrapper<FhirPatientCache>()
                        .eq(FhirPatientCache::getFhirId, fhirId));
        return deleted > 0;
    }

    @Override
    public long count() {
        return mapper.selectCount(null);
    }
}
