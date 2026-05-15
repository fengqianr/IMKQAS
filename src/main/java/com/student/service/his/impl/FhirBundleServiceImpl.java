package com.student.service.his.impl;

import com.student.dto.his.FhirConverter;
import com.student.dto.his.FhirConverter.BundleResources;
import com.student.exception.BusinessException;
import com.student.exception.ErrorCode;
import com.student.mapper.his.*;
import com.student.service.his.FhirBundleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FHIR Bundle统一入口服务实现
 * Bundle是传输容器，不单独建表，资源分别落入对应缓存表
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FhirBundleServiceImpl implements FhirBundleService {

    private final FhirConverter converter;
    private final FhirPatientCacheMapper patientMapper;
    private final FhirObservationCacheMapper observationMapper;
    private final FhirConditionCacheMapper conditionMapper;
    private final FhirQuestionnaireResponseCacheMapper qrMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Bundle processTransaction(Bundle bundle) {
        validateBundle(bundle);

        BundleResources resources = converter.parseBundle(bundle, "BUNDLE");

        if (resources.isEmpty()) {
            log.warn("Bundle为空，跳过处理");
            return buildResponseBundle(bundle, Bundle.BundleType.TRANSACTIONRESPONSE);
        }

        log.info("处理Bundle[transaction]: {}条资源 (Patient:{}, Observation:{}, Condition:{}, QR:{})",
                resources.totalCount(),
                resources.patients.size(), resources.observations.size(),
                resources.conditions.size(), resources.questionnaireResponses.size());

        // 全部插入（同一事务，失败全部回滚）
        resources.patients.forEach(p -> {
            var existing = patientMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.student.entity.his.FhirPatientCache>()
                            .eq(com.student.entity.his.FhirPatientCache::getFhirId, p.getFhirId()));
            if (existing != null) {
                p.setId(existing.getId());
                patientMapper.updateById(p);
            } else {
                patientMapper.insert(p);
            }
        });

        resources.observations.forEach(o -> {
            var existing = observationMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.student.entity.his.FhirObservationCache>()
                            .eq(com.student.entity.his.FhirObservationCache::getFhirId, o.getFhirId()));
            if (existing != null) {
                o.setId(existing.getId());
                observationMapper.updateById(o);
            } else {
                observationMapper.insert(o);
            }
        });

        resources.conditions.forEach(c -> {
            var existing = conditionMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.student.entity.his.FhirConditionCache>()
                            .eq(com.student.entity.his.FhirConditionCache::getFhirId, c.getFhirId()));
            if (existing != null) {
                c.setId(existing.getId());
                conditionMapper.updateById(c);
            } else {
                conditionMapper.insert(c);
            }
        });

        resources.questionnaireResponses.forEach(q -> {
            var existing = qrMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.student.entity.his.FhirQuestionnaireResponseCache>()
                            .eq(com.student.entity.his.FhirQuestionnaireResponseCache::getFhirId, q.getFhirId()));
            if (existing != null) {
                q.setId(existing.getId());
                qrMapper.updateById(q);
            } else {
                qrMapper.insert(q);
            }
        });

        log.info("Bundle[transaction]处理完成: {}条资源", resources.totalCount());
        return buildResponseBundle(bundle, Bundle.BundleType.TRANSACTIONRESPONSE);
    }

    @Override
    public Bundle processBatch(Bundle bundle) {
        validateBundle(bundle);

        BundleResources resources = converter.parseBundle(bundle, "BUNDLE");
        Bundle responseBundle = new Bundle();
        responseBundle.setType(Bundle.BundleType.BATCHRESPONSE);

        if (resources.isEmpty()) {
            return responseBundle;
        }

        log.info("处理Bundle[batch]: {}条资源", resources.totalCount());

        // 逐条独立执行，每条用独立事务，不影响其他条目
        processResourcesIndependently(resources.patients, "Patient", patientMapper, responseBundle);
        processResourcesIndependently(resources.observations, "Observation", observationMapper, responseBundle);
        processResourcesIndependently(resources.conditions, "Condition", conditionMapper, responseBundle);
        processResourcesIndependently(resources.questionnaireResponses, "QR", qrMapper, responseBundle);

        return responseBundle;
    }

    /**
     * 逐条独立处理资源（每条独立事务，失败不影响其他）
     */
    private <T> void processResourcesIndependently(
            java.util.List<T> resources, String type,
            com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper,
            Bundle responseBundle) {

        for (T resource : resources) {
            Bundle.BundleEntryComponent entry = responseBundle.addEntry();
            try {
                // 通过反射获取fhirId
                String fhirId = getFhirId(resource);
                // 尝试查询已存在记录
                T existing = findExisting(mapper, fhirId);
                if (existing != null) {
                    setId(resource, getId(existing));
                    mapper.updateById(resource);
                } else {
                    mapper.insert(resource);
                }
                entry.getResponse().setStatus("200 OK");
                entry.getResponse().setLocation(type + "/" + fhirId);
            } catch (Exception e) {
                log.error("Batch处理{}失败: {}", type, e.getMessage());
                entry.getResponse().setStatus("400 Bad Request");
                entry.getResponse().setOutcome(
                        createOperationOutcome("处理失败: " + e.getMessage()));
            }
        }
    }

    @Override
    public void validateBundle(Bundle bundle) {
        if (bundle == null) {
            throw new BusinessException(ErrorCode.FHIR_VALIDATION_ERROR.getCode(), "Bundle不能为空");
        }
        if (bundle.getType() == null) {
            throw new BusinessException(ErrorCode.FHIR_VALIDATION_ERROR.getCode(), "Bundle.type不能为空");
        }
        if (bundle.getType() != Bundle.BundleType.TRANSACTION
                && bundle.getType() != Bundle.BundleType.BATCH
                && bundle.getType() != Bundle.BundleType.COLLECTION) {
            throw new BusinessException(ErrorCode.FHIR_VALIDATION_ERROR.getCode(),
                    "不支持的Bundle类型: " + bundle.getType().toCode()
                    + "，仅支持 transaction/batch/collection");
        }
    }

    @Override
    public Bundle process(Bundle bundle) {
        Bundle.BundleType type = bundle.getType();
        if (type == Bundle.BundleType.TRANSACTION) {
            return processTransaction(bundle);
        } else if (type == Bundle.BundleType.BATCH) {
            return processBatch(bundle);
        } else {
            // COLLECTION 类型：也是走独立执行
            return processBatch(bundle);
        }
    }

    // ==================== 工具方法 ====================

    private Bundle buildResponseBundle(Bundle request, Bundle.BundleType responseType) {
        Bundle response = new Bundle();
        response.setType(responseType);
        if (request.hasId()) {
            response.setId(request.getId());
        }
        return response;
    }

    private OperationOutcome createOperationOutcome(String message) {
        OperationOutcome oo = new OperationOutcome();
        oo.addIssue()
                .setSeverity(OperationOutcome.IssueSeverity.ERROR)
                .setCode(OperationOutcome.IssueType.PROCESSING)
                .setDiagnostics(message);
        return oo;
    }

    // 反射工具 — 避免为每种实体写重复代码
    private String getFhirId(Object entity) {
        try {
            return (String) entity.getClass().getMethod("getFhirId").invoke(entity);
        } catch (Exception e) {
            return "unknown";
        }
    }

    private Long getId(Object entity) {
        try {
            return (Long) entity.getClass().getMethod("getId").invoke(entity);
        } catch (Exception e) {
            return null;
        }
    }

    private void setId(Object entity, Long id) {
        try {
            entity.getClass().getMethod("setId", Long.class).invoke(entity, id);
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T findExisting(
            com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper, String fhirId) {
        try {
            var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<T>();
            // 通用：eq("fhir_id", fhirId)
            wrapper.eq(e -> {
                try {
                    return e.getClass().getMethod("getFhirId").invoke(e);
                } catch (Exception ex) {
                    return null;
                }
            }, fhirId);
            return mapper.selectOne(wrapper);
        } catch (Exception e) {
            return null;
        }
    }
}
