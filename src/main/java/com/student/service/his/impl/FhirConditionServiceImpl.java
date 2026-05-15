package com.student.service.his.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.student.dto.his.FhirConverter;
import com.student.entity.his.FhirConditionCache;
import com.student.mapper.his.FhirConditionCacheMapper;
import com.student.service.his.FhirConditionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Condition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * FHIR病情资源服务实现
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FhirConditionServiceImpl implements FhirConditionService {

    private final FhirConditionCacheMapper mapper;
    private final FhirConverter converter;

    @Override
    public Optional<Condition> findByFhirId(String fhirId) {
        FhirConditionCache cache = mapper.selectOne(
                new LambdaQueryWrapper<FhirConditionCache>()
                        .eq(FhirConditionCache::getFhirId, fhirId));
        if (cache == null) return Optional.empty();
        return Optional.of(converter.toFhirCondition(cache));
    }

    @Override
    public List<Condition> findByPatient(String patientFhirId, int page, int size) {
        Page<FhirConditionCache> pageResult = mapper.selectPage(
                new Page<>(page + 1, size),
                new LambdaQueryWrapper<FhirConditionCache>()
                        .eq(FhirConditionCache::getPatientFhirId, patientFhirId)
                        .orderByDesc(FhirConditionCache::getOnsetDateTime));
        return pageResult.getRecords().stream()
                .map(converter::toFhirCondition)
                .collect(Collectors.toList());
    }

    @Override
    public List<Condition> findByPatientAndStatus(String patientFhirId,
                                                   String clinicalStatus, int page, int size) {
        Page<FhirConditionCache> pageResult = mapper.selectPage(
                new Page<>(page + 1, size),
                new LambdaQueryWrapper<FhirConditionCache>()
                        .eq(FhirConditionCache::getPatientFhirId, patientFhirId)
                        .eq(FhirConditionCache::getClinicalStatus, clinicalStatus)
                        .orderByDesc(FhirConditionCache::getOnsetDateTime));
        return pageResult.getRecords().stream()
                .map(converter::toFhirCondition)
                .collect(Collectors.toList());
    }

    @Override
    public List<Condition> findByCode(String patientFhirId, String codeSystem,
                                       String codeValue) {
        List<FhirConditionCache> caches = mapper.selectList(
                new LambdaQueryWrapper<FhirConditionCache>()
                        .eq(FhirConditionCache::getPatientFhirId, patientFhirId)
                        .eq(FhirConditionCache::getCodeSystem, codeSystem)
                        .eq(FhirConditionCache::getCodeValue, codeValue));
        return caches.stream()
                .map(converter::toFhirCondition)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Condition save(Condition condition, String source) {
        FhirConditionCache cache = converter.toConditionCache(condition, source);
        FhirConditionCache existing = mapper.selectOne(
                new LambdaQueryWrapper<FhirConditionCache>()
                        .eq(FhirConditionCache::getFhirId, cache.getFhirId()));
        if (existing != null) {
            cache.setId(existing.getId());
            mapper.updateById(cache);
        } else {
            mapper.insert(cache);
        }
        return condition;
    }

    @Override
    public boolean deleteByFhirId(String fhirId) {
        return mapper.delete(new LambdaQueryWrapper<FhirConditionCache>()
                .eq(FhirConditionCache::getFhirId, fhirId)) > 0;
    }
}
