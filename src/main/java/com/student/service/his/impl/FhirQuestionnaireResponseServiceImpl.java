package com.student.service.his.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.student.dto.his.FhirConverter;
import com.student.entity.his.FhirQuestionnaireResponseCache;
import com.student.mapper.his.FhirQuestionnaireResponseCacheMapper;
import com.student.service.his.FhirQuestionnaireResponseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * FHIR问卷回答资源服务实现
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FhirQuestionnaireResponseServiceImpl
        implements FhirQuestionnaireResponseService {

    private final FhirQuestionnaireResponseCacheMapper mapper;
    private final FhirConverter converter;

    @Override
    public Optional<QuestionnaireResponse> findByFhirId(String fhirId) {
        FhirQuestionnaireResponseCache cache = mapper.selectOne(
                new LambdaQueryWrapper<FhirQuestionnaireResponseCache>()
                        .eq(FhirQuestionnaireResponseCache::getFhirId, fhirId));
        if (cache == null) return Optional.empty();
        return Optional.of(converter.toFhirQuestionnaireResponse(cache));
    }

    @Override
    public List<QuestionnaireResponse> findByPatient(String patientFhirId,
                                                      int page, int size) {
        Page<FhirQuestionnaireResponseCache> pageResult = mapper.selectPage(
                new Page<>(page + 1, size),
                new LambdaQueryWrapper<FhirQuestionnaireResponseCache>()
                        .eq(FhirQuestionnaireResponseCache::getPatientFhirId, patientFhirId)
                        .orderByDesc(FhirQuestionnaireResponseCache::getAuthoredDate));
        return pageResult.getRecords().stream()
                .map(converter::toFhirQuestionnaireResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<QuestionnaireResponse> findByQuestionnaire(String questionnaireId,
                                                            int page, int size) {
        Page<FhirQuestionnaireResponseCache> pageResult = mapper.selectPage(
                new Page<>(page + 1, size),
                new LambdaQueryWrapper<FhirQuestionnaireResponseCache>()
                        .eq(FhirQuestionnaireResponseCache::getQuestionnaireId, questionnaireId)
                        .orderByDesc(FhirQuestionnaireResponseCache::getAuthoredDate));
        return pageResult.getRecords().stream()
                .map(converter::toFhirQuestionnaireResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<QuestionnaireResponse> findByPatientAndQuestionnaire(
            String patientFhirId, String questionnaireId, int page, int size) {
        Page<FhirQuestionnaireResponseCache> pageResult = mapper.selectPage(
                new Page<>(page + 1, size),
                new LambdaQueryWrapper<FhirQuestionnaireResponseCache>()
                        .eq(FhirQuestionnaireResponseCache::getPatientFhirId, patientFhirId)
                        .eq(FhirQuestionnaireResponseCache::getQuestionnaireId, questionnaireId)
                        .orderByDesc(FhirQuestionnaireResponseCache::getAuthoredDate));
        return pageResult.getRecords().stream()
                .map(converter::toFhirQuestionnaireResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public QuestionnaireResponse save(QuestionnaireResponse qr, String source) {
        FhirQuestionnaireResponseCache cache =
                converter.toQuestionnaireResponseCache(qr, source);
        FhirQuestionnaireResponseCache existing = mapper.selectOne(
                new LambdaQueryWrapper<FhirQuestionnaireResponseCache>()
                        .eq(FhirQuestionnaireResponseCache::getFhirId, cache.getFhirId()));
        if (existing != null) {
            cache.setId(existing.getId());
            mapper.updateById(cache);
        } else {
            mapper.insert(cache);
        }
        return qr;
    }

    @Override
    public boolean deleteByFhirId(String fhirId) {
        return mapper.delete(new LambdaQueryWrapper<FhirQuestionnaireResponseCache>()
                .eq(FhirQuestionnaireResponseCache::getFhirId, fhirId)) > 0;
    }
}
