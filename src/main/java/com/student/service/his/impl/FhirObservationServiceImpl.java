package com.student.service.his.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.student.dto.his.FhirConverter;
import com.student.entity.his.FhirObservationCache;
import com.student.mapper.his.FhirObservationCacheMapper;
import com.student.service.his.FhirObservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Observation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * FHIR观察资源服务实现
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FhirObservationServiceImpl implements FhirObservationService {

    private final FhirObservationCacheMapper mapper;
    private final FhirConverter converter;

    @Override
    public Optional<Observation> findByFhirId(String fhirId) {
        FhirObservationCache cache = mapper.selectOne(
                new LambdaQueryWrapper<FhirObservationCache>()
                        .eq(FhirObservationCache::getFhirId, fhirId));
        if (cache == null) return Optional.empty();
        return Optional.of(converter.toFhirObservation(cache));
    }

    @Override
    public List<Observation> findByPatient(String patientFhirId, int page, int size) {
        Page<FhirObservationCache> pageResult = mapper.selectPage(
                new Page<>(page + 1, size),
                new LambdaQueryWrapper<FhirObservationCache>()
                        .eq(FhirObservationCache::getPatientFhirId, patientFhirId)
                        .orderByDesc(FhirObservationCache::getEffectiveDateTime));
        return pageResult.getRecords().stream()
                .map(converter::toFhirObservation)
                .collect(Collectors.toList());
    }

    @Override
    public List<Observation> findByCode(String patientFhirId, String codeSystem,
                                         String codeValue, int page, int size) {
        Page<FhirObservationCache> pageResult = mapper.selectPage(
                new Page<>(page + 1, size),
                new LambdaQueryWrapper<FhirObservationCache>()
                        .eq(FhirObservationCache::getPatientFhirId, patientFhirId)
                        .eq(FhirObservationCache::getCodeSystem, codeSystem)
                        .eq(FhirObservationCache::getCodeValue, codeValue)
                        .orderByDesc(FhirObservationCache::getEffectiveDateTime));
        return pageResult.getRecords().stream()
                .map(converter::toFhirObservation)
                .collect(Collectors.toList());
    }

    @Override
    public List<Observation> findByCategory(String patientFhirId, String category,
                                             int page, int size) {
        Page<FhirObservationCache> pageResult = mapper.selectPage(
                new Page<>(page + 1, size),
                new LambdaQueryWrapper<FhirObservationCache>()
                        .eq(FhirObservationCache::getPatientFhirId, patientFhirId)
                        .eq(FhirObservationCache::getCategory, category)
                        .orderByDesc(FhirObservationCache::getEffectiveDateTime));
        return pageResult.getRecords().stream()
                .map(converter::toFhirObservation)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Observation save(Observation observation, String source) {
        FhirObservationCache cache = converter.toObservationCache(observation, source);
        FhirObservationCache existing = mapper.selectOne(
                new LambdaQueryWrapper<FhirObservationCache>()
                        .eq(FhirObservationCache::getFhirId, cache.getFhirId()));
        if (existing != null) {
            cache.setId(existing.getId());
            mapper.updateById(cache);
        } else {
            mapper.insert(cache);
        }
        return observation;
    }

    @Override
    @Transactional
    public List<Observation> saveBatch(List<Observation> observations, String source) {
        return observations.stream()
                .map(obs -> save(obs, source))
                .collect(Collectors.toList());
    }

    @Override
    public boolean deleteByFhirId(String fhirId) {
        return mapper.delete(new LambdaQueryWrapper<FhirObservationCache>()
                .eq(FhirObservationCache::getFhirId, fhirId)) > 0;
    }
}
