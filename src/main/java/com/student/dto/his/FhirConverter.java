package com.student.dto.his;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.student.entity.his.*;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * FHIR资源转换器
 * 实现本地缓存实体 <-> HAPI FHIR R4资源 <-> JSON 的双向转换
 * 覆盖: Patient, Observation, Condition, QuestionnaireResponse, Bundle
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@Component
public class FhirConverter {

    private final FhirContext fhirContext;
    private final IParser jsonParser;

    public FhirConverter() {
        this.fhirContext = FhirContext.forR4();
        this.jsonParser = fhirContext.newJsonParser().setPrettyPrint(true);
    }

    // ==================== Patient 转换 ====================

    /**
     * HAPI FHIR Patient -> 本地缓存实体
     */
    public FhirPatientCache toPatientCache(Patient patient, String source) {
        String familyName = null;
        String givenName = null;
        if (patient.hasName()) {
            HumanName name = patient.getNameFirstRep();
            familyName = name.getFamily();
            givenName = name.getGivenAsSingleString();
        }

        String gender = patient.hasGender() ? patient.getGender().toCode() : null;

        LocalDate birthDate = null;
        if (patient.hasBirthDate()) {
            birthDate = patient.getBirthDate().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate();
        }

        String phone = null;
        if (patient.hasTelecom()) {
            phone = patient.getTelecomFirstRep().getValue();
        }

        String addressText = null;
        if (patient.hasAddress()) {
            addressText = patient.getAddressFirstRep().getText();
        }

        String identifierSystem = null;
        String identifierValue = null;
        if (patient.hasIdentifier()) {
            Identifier idf = patient.getIdentifierFirstRep();
            identifierSystem = idf.getSystem();
            identifierValue = idf.getValue();
        }

        return FhirPatientCache.builder()
                .fhirId(patient.getIdElement().getIdPart())
                .identifierSystem(identifierSystem)
                .identifierValue(identifierValue)
                .familyName(familyName)
                .givenName(givenName)
                .gender(gender)
                .birthDate(birthDate)
                .phone(phone)
                .addressText(addressText)
                .maritalStatus(patient.hasMaritalStatus()
                        ? patient.getMaritalStatus().getText() : null)
                .resourceJson(toJson(patient))
                .versionId(patient.getMeta().hasVersionId()
                        ? patient.getMeta().getVersionId() : null)
                .lastUpdated(patient.getMeta().hasLastUpdated()
                        ? toLocalDateTime(patient.getMeta().getLastUpdated()) : null)
                .source(source)
                .build();
    }

    /**
     * 本地缓存实体 -> HAPI FHIR Patient
     */
    public Patient toFhirPatient(FhirPatientCache cache) {
        if (cache.getResourceJson() != null && !cache.getResourceJson().isEmpty()) {
            try {
                return jsonParser.parseResource(Patient.class, cache.getResourceJson());
            } catch (Exception e) {
                log.warn("从resourceJson解析Patient失败，从字段重建: {}", e.getMessage());
            }
        }

        Patient patient = new Patient();
        patient.setId(cache.getFhirId());

        if (cache.getFamilyName() != null || cache.getGivenName() != null) {
            HumanName name = patient.addName();
            name.setFamily(cache.getFamilyName());
            if (cache.getGivenName() != null) {
                name.addGiven(cache.getGivenName());
            }
        }

        if (cache.getGender() != null) {
            try {
                patient.setGender(Enumerations.AdministrativeGender.fromCode(cache.getGender()));
            } catch (Exception e) {
                log.debug("无效的性别值: {}", cache.getGender());
            }
        }

        if (cache.getBirthDate() != null) {
            patient.setBirthDate(Date.from(cache.getBirthDate()
                    .atStartOfDay(ZoneId.systemDefault()).toInstant()));
        }

        if (cache.getIdentifierSystem() != null && cache.getIdentifierValue() != null) {
            patient.addIdentifier()
                    .setSystem(cache.getIdentifierSystem())
                    .setValue(cache.getIdentifierValue());
        }

        if (cache.getPhone() != null) {
            patient.addTelecom()
                    .setSystem(ContactPoint.ContactPointSystem.PHONE)
                    .setValue(cache.getPhone());
        }

        if (cache.getAddressText() != null) {
            patient.addAddress().setText(cache.getAddressText());
        }

        return patient;
    }

    // ==================== Observation 转换 ====================

    /**
     * HAPI FHIR Observation -> 本地缓存实体
     */
    public FhirObservationCache toObservationCache(Observation obs, String source) {
        String patientFhirId = null;
        if (obs.hasSubject() && obs.getSubject().hasReference()) {
            patientFhirId = extractIdFromReference(obs.getSubject().getReference());
        }

        String codeSystem = null;
        String codeValue = null;
        String codeDisplay = null;
        if (obs.hasCode() && obs.getCode().hasCoding()) {
            Coding coding = obs.getCode().getCodingFirstRep();
            codeSystem = coding.getSystem();
            codeValue = coding.getCode();
            codeDisplay = coding.getDisplay();
        }

        String valueType = null;
        Double valueQuantity = null;
        String valueUnit = null;
        String valueString = null;
        String valueCode = null;

        if (obs.hasValue()) {
            Type value = obs.getValue();
            if (value instanceof Quantity q) {
                valueType = "Quantity";
                valueQuantity = q.getValue() != null ? q.getValue().doubleValue() : null;
                valueUnit = q.getCode();
            } else if (value instanceof StringType s) {
                valueType = "string";
                valueString = s.getValue();
            } else if (value instanceof CodeableConcept cc && cc.hasCoding()) {
                valueType = "CodeableConcept";
                valueCode = cc.getCodingFirstRep().getCode();
            }
        }

        LocalDateTime effectiveDateTime = null;
        if (obs.hasEffectiveDateTimeType()) {
            effectiveDateTime = toLocalDateTime(obs.getEffectiveDateTimeType().getValue());
        }

        return FhirObservationCache.builder()
                .fhirId(obs.getIdElement().getIdPart())
                .patientFhirId(patientFhirId)
                .codeSystem(codeSystem).codeValue(codeValue).codeDisplay(codeDisplay)
                .category(obs.hasCategory() && obs.getCategoryFirstRep().hasCoding()
                        ? obs.getCategoryFirstRep().getCodingFirstRep().getCode() : null)
                .valueType(valueType)
                .valueQuantity(valueQuantity).valueUnit(valueUnit)
                .valueString(valueString).valueCode(valueCode)
                .effectiveDateTime(effectiveDateTime)
                .status(obs.hasStatus() ? obs.getStatus().toCode() : null)
                .resourceJson(toJson(obs))
                .versionId(obs.getMeta().hasVersionId()
                        ? obs.getMeta().getVersionId() : null)
                .lastUpdated(obs.getMeta().hasLastUpdated()
                        ? toLocalDateTime(obs.getMeta().getLastUpdated()) : null)
                .source(source)
                .build();
    }

    /**
     * 本地缓存实体 -> HAPI FHIR Observation
     */
    public Observation toFhirObservation(FhirObservationCache cache) {
        if (cache.getResourceJson() != null && !cache.getResourceJson().isEmpty()) {
            try {
                return jsonParser.parseResource(Observation.class, cache.getResourceJson());
            } catch (Exception e) {
                log.warn("从resourceJson解析Observation失败，从字段重建: {}", e.getMessage());
            }
        }

        Observation obs = new Observation();
        obs.setId(cache.getFhirId());

        if (cache.getPatientFhirId() != null) {
            obs.setSubject(new Reference("Patient/" + cache.getPatientFhirId()));
        }

        if (cache.getCodeSystem() != null || cache.getCodeValue() != null) {
            obs.getCode().addCoding()
                    .setSystem(cache.getCodeSystem())
                    .setCode(cache.getCodeValue())
                    .setDisplay(cache.getCodeDisplay());
        }

        if (cache.getValueType() != null) {
            switch (cache.getValueType()) {
                case "Quantity" -> obs.setValue(new Quantity()
                        .setValue(cache.getValueQuantity())
                        .setCode(cache.getValueUnit()));
                case "string" -> obs.setValue(new StringType(cache.getValueString()));
                case "CodeableConcept" -> obs.setValue(new CodeableConcept()
                        .addCoding(new Coding().setCode(cache.getValueCode())));
            }
        }

        if (cache.getEffectiveDateTime() != null) {
            obs.setEffective(new DateTimeType(Date.from(
                    cache.getEffectiveDateTime().atZone(ZoneId.systemDefault()).toInstant())));
        }

        if (cache.getStatus() != null) {
            try {
                obs.setStatus(Observation.ObservationStatus.fromCode(cache.getStatus()));
            } catch (Exception e) {
                log.debug("无效的观察状态: {}", cache.getStatus());
            }
        }

        return obs;
    }

    // ==================== Condition 转换 ====================

    /**
     * HAPI FHIR Condition -> 本地缓存实体
     */
    public FhirConditionCache toConditionCache(Condition condition, String source) {
        String patientFhirId = null;
        if (condition.hasSubject() && condition.getSubject().hasReference()) {
            patientFhirId = extractIdFromReference(condition.getSubject().getReference());
        }

        String codeSystem = null;
        String codeValue = null;
        String codeDisplay = null;
        if (condition.hasCode() && condition.getCode().hasCoding()) {
            Coding coding = condition.getCode().getCodingFirstRep();
            codeSystem = coding.getSystem();
            codeValue = coding.getCode();
            codeDisplay = coding.getDisplay();
        }

        String category = null;
        if (condition.hasCategory() && condition.getCategoryFirstRep().hasCoding()) {
            category = condition.getCategoryFirstRep().getCodingFirstRep().getCode();
        }

        String clinicalStatus = null;
        if (condition.hasClinicalStatus() && condition.getClinicalStatus().hasCoding()) {
            clinicalStatus = condition.getClinicalStatus().getCodingFirstRep().getCode();
        }

        String verificationStatus = null;
        if (condition.hasVerificationStatus() && condition.getVerificationStatus().hasCoding()) {
            verificationStatus = condition.getVerificationStatus().getCodingFirstRep().getCode();
        }

        String severity = null;
        if (condition.hasSeverity() && condition.getSeverity().hasCoding()) {
            severity = condition.getSeverity().getCodingFirstRep().getCode();
        }

        return FhirConditionCache.builder()
                .fhirId(condition.getIdElement().getIdPart())
                .patientFhirId(patientFhirId)
                .codeSystem(codeSystem).codeValue(codeValue).codeDisplay(codeDisplay)
                .category(category)
                .clinicalStatus(clinicalStatus)
                .verificationStatus(verificationStatus)
                .severity(severity)
                .onsetDateTime(condition.hasOnsetDateTimeType()
                        ? toLocalDateTime(condition.getOnsetDateTimeType().getValue()) : null)
                .abatementDateTime(condition.hasAbatementDateTimeType()
                        ? toLocalDateTime(condition.getAbatementDateTimeType().getValue()) : null)
                .recordedDate(condition.hasRecordedDate()
                        ? toLocalDateTime(condition.getRecordedDate()) : null)
                .resourceJson(toJson(condition))
                .versionId(condition.getMeta().hasVersionId()
                        ? condition.getMeta().getVersionId() : null)
                .lastUpdated(condition.getMeta().hasLastUpdated()
                        ? toLocalDateTime(condition.getMeta().getLastUpdated()) : null)
                .source(source)
                .notes(condition.hasNote() ? condition.getNoteFirstRep().getText() : null)
                .build();
    }

    /**
     * 本地缓存实体 -> HAPI FHIR Condition
     */
    public Condition toFhirCondition(FhirConditionCache cache) {
        if (cache.getResourceJson() != null && !cache.getResourceJson().isEmpty()) {
            try {
                return jsonParser.parseResource(Condition.class, cache.getResourceJson());
            } catch (Exception e) {
                log.warn("从resourceJson解析Condition失败，从字段重建: {}", e.getMessage());
            }
        }

        Condition condition = new Condition();
        condition.setId(cache.getFhirId());

        if (cache.getPatientFhirId() != null) {
            condition.setSubject(new Reference("Patient/" + cache.getPatientFhirId()));
        }

        if (cache.getCodeSystem() != null || cache.getCodeValue() != null) {
            condition.getCode().addCoding()
                    .setSystem(cache.getCodeSystem())
                    .setCode(cache.getCodeValue())
                    .setDisplay(cache.getCodeDisplay());
        }

        if (cache.getCategory() != null) {
            condition.addCategory(new CodeableConcept()
                    .addCoding(new Coding().setCode(cache.getCategory())));
        }

        if (cache.getClinicalStatus() != null) {
            condition.setClinicalStatus(new CodeableConcept()
                    .addCoding(new Coding().setCode(cache.getClinicalStatus())));
        }

        if (cache.getOnsetDateTime() != null) {
            condition.setOnset(new DateTimeType(Date.from(
                    cache.getOnsetDateTime().atZone(ZoneId.systemDefault()).toInstant())));
        }

        return condition;
    }

    // ==================== QuestionnaireResponse 转换 ====================

    /**
     * HAPI FHIR QuestionnaireResponse -> 本地缓存实体
     */
    public FhirQuestionnaireResponseCache toQuestionnaireResponseCache(
            QuestionnaireResponse qr, String source) {

        String patientFhirId = null;
        if (qr.hasSubject() && qr.getSubject().hasReference()) {
            patientFhirId = extractIdFromReference(qr.getSubject().getReference());
        }

        String questionnaireId = null;
        if (qr.hasQuestionnaire()) {
            questionnaireId = extractIdFromReference(qr.getQuestionnaire());
        }

        return FhirQuestionnaireResponseCache.builder()
                .fhirId(qr.getIdElement().getIdPart())
                .patientFhirId(patientFhirId)
                .questionnaireId(questionnaireId)
                .status(qr.hasStatus() ? qr.getStatus().toCode() : null)
                .authoredDate(qr.hasAuthored()
                        ? toLocalDateTime(qr.getAuthored()) : null)
                .itemCount(qr.hasItem() ? qr.getItem().size() : 0)
                .answeredCount(countAnsweredItems(qr.getItem()))
                .resourceJson(toJson(qr))
                .versionId(qr.getMeta().hasVersionId()
                        ? qr.getMeta().getVersionId() : null)
                .lastUpdated(qr.getMeta().hasLastUpdated()
                        ? toLocalDateTime(qr.getMeta().getLastUpdated()) : null)
                .source(source)
                .build();
    }

    /**
     * 本地缓存实体 -> HAPI FHIR QuestionnaireResponse
     */
    public QuestionnaireResponse toFhirQuestionnaireResponse(FhirQuestionnaireResponseCache cache) {
        if (cache.getResourceJson() != null && !cache.getResourceJson().isEmpty()) {
            try {
                return jsonParser.parseResource(
                        QuestionnaireResponse.class, cache.getResourceJson());
            } catch (Exception e) {
                log.warn("从resourceJson解析QuestionnaireResponse失败，从字段重建: {}", e.getMessage());
            }
        }

        QuestionnaireResponse qr = new QuestionnaireResponse();
        qr.setId(cache.getFhirId());

        if (cache.getPatientFhirId() != null) {
            qr.setSubject(new Reference("Patient/" + cache.getPatientFhirId()));
        }

        if (cache.getQuestionnaireId() != null) {
            qr.setQuestionnaire(cache.getQuestionnaireId());
        }

        if (cache.getStatus() != null) {
            try {
                qr.setStatus(QuestionnaireResponse.QuestionnaireResponseStatus
                        .fromCode(cache.getStatus()));
            } catch (Exception e) {
                log.debug("无效的问卷回答状态: {}", cache.getStatus());
            }
        }

        if (cache.getAuthoredDate() != null) {
            qr.setAuthored(Date.from(cache.getAuthoredDate()
                    .atZone(ZoneId.systemDefault()).toInstant()));
        }

        return qr;
    }

    // ==================== Generic JSON 序列化 ====================

    /**
     * 将任意FHIR资源序列化为JSON字符串
     */
    public String toJson(IBaseResource resource) {
        try {
            return jsonParser.encodeResourceToString(resource);
        } catch (Exception e) {
            log.error("FHIR资源序列化失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从JSON字符串反序列化FHIR资源
     */
    public <T extends IBaseResource> T fromJson(String json, Class<T> type) {
        try {
            return jsonParser.parseResource(type, json);
        } catch (Exception e) {
            log.error("FHIR资源反序列化失败: {}", e.getMessage(), e);
            return null;
        }
    }

    // ==================== Bundle 解析 ====================

    /**
     * 解析 Bundle，提取其中各类型资源并分别转换
     * 返回 BundleResources 包含分类后的缓存实体列表
     */
    public BundleResources parseBundle(Bundle bundle, String source) {
        BundleResources result = new BundleResources();

        if (bundle == null || !bundle.hasEntry()) {
            return result;
        }

        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            if (!entry.hasResource()) continue;

            var resource = entry.getResource();

            if (resource instanceof Patient patient) {
                result.patients.add(toPatientCache(patient, source));
            } else if (resource instanceof Observation observation) {
                result.observations.add(toObservationCache(observation, source));
            } else if (resource instanceof Condition condition) {
                result.conditions.add(toConditionCache(condition, source));
            } else if (resource instanceof QuestionnaireResponse qr) {
                result.questionnaireResponses.add(
                        toQuestionnaireResponseCache(qr, source));
            } else {
                log.warn("Bundle中包含未支持的资源类型: {}", resource.fhirType());
            }
        }

        return result;
    }

    // ==================== DiagnosticReport 转换 ====================

    /**
     * 将分析结果转换为FHIR DiagnosticReport
     */
    public DiagnosticReport toDiagnosticReport(com.student.service.his.AnalysisResult analysisResult,
                                                String patientFhirId, String qrReference) {
        DiagnosticReport report = new DiagnosticReport();
        report.setId("dr-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        report.setStatus(DiagnosticReport.DiagnosticReportStatus.FINAL);

        // 代码：心理健康评估报告
        report.getCode().addCoding()
                .setSystem("http://loinc.org")
                .setCode("86971-7")
                .setDisplay("心理健康评估报告");

        if (patientFhirId != null) {
            report.setSubject(new Reference("Patient/" + patientFhirId));
        }

        report.setIssued(new Date());

        // 关联的QuestionnaireResponse
        if (qrReference != null) {
            report.addResult().setReference(qrReference);
        }

        // 结论
        if (analysisResult.getSummary() != null) {
            report.setConclusion(analysisResult.getSummary());
        }

        // 详细分析 → Extension
        if (analysisResult.getDetailAnalysis() != null) {
            try {
                String detailJson = new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(analysisResult.getDetailAnalysis());
                report.addExtension(new Extension(
                        "http://imkqas.org/fhir/StructureDefinition/detail-analysis",
                        new StringType(detailJson)));
            } catch (Exception e) {
                log.warn("序列化详细分析失败: {}", e.getMessage());
            }
        }

        // 建议 → Extension
        if (analysisResult.getRecommendations() != null) {
            try {
                String recJson = new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(analysisResult.getRecommendations());
                report.addExtension(new Extension(
                        "http://imkqas.org/fhir/StructureDefinition/recommendations",
                        new StringType(recJson)));
            } catch (Exception e) {
                log.warn("序列化建议失败: {}", e.getMessage());
            }
        }

        // 随访建议 → Extension
        if (analysisResult.getFollowUp() != null) {
            report.addExtension(new Extension(
                    "http://imkqas.org/fhir/StructureDefinition/follow-up",
                    new StringType(String.format("%s（%s）",
                            analysisResult.getFollowUp().getSuggestedDate(),
                            analysisResult.getFollowUp().getRationale()))));
        }

        // 免责声明 → Extension
        if (analysisResult.getDisclaimer() != null) {
            report.addExtension(new Extension(
                    "http://imkqas.org/fhir/StructureDefinition/disclaimer",
                    new StringType(analysisResult.getDisclaimer())));
        }

        return report;
    }

    /**
     * 将分析结果转换为FHIR RiskAssessment
     */
    public RiskAssessment toRiskAssessment(
            com.student.service.his.AnalysisResult analysisResult,
            String patientFhirId, String qrReference) {

        RiskAssessment risk = new RiskAssessment();
        risk.setId("ra-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        risk.setStatus(RiskAssessment.RiskAssessmentStatus.FINAL);
        risk.setOccurrence(new DateTimeType(new Date()));

        if (patientFhirId != null) {
            risk.setSubject(new Reference("Patient/" + patientFhirId));
        }

        // 依据：引用对应的QuestionnaireResponse
        if (qrReference != null) {
            risk.setBasis(List.of(new Reference(qrReference)));
        }

        // 风险预测
        if (analysisResult.getRiskAssessment() != null) {
            var riskBlock = analysisResult.getRiskAssessment();
            RiskAssessment.RiskAssessmentPredictionComponent prediction =
                    new RiskAssessment.RiskAssessmentPredictionComponent();
            prediction.setOutcome(new CodeableConcept().setText(riskBlock.getLevel()));

            if (riskBlock.getDescription() != null) {
                prediction.setQualitativeRisk(new CodeableConcept()
                        .setText(riskBlock.getDescription()));
            }

            risk.addPrediction(prediction);
        }

        // 紧急标记 → Extension
        if (analysisResult.getRiskAssessment() != null
                && analysisResult.getRiskAssessment().isRequiresUrgentAttention()) {
            risk.addExtension(new Extension(
                    "http://imkqas.org/fhir/StructureDefinition/requires-urgent-attention",
                    new BooleanType(true)));
        }

        return risk;
    }

    // ==================== DiagnosticReport 缓存转换 ====================

    /**
     * 将分析结果转换为FhirDiagnosticReportCache实体
     */
    public FhirDiagnosticReportCache toDiagnosticReportCache(
            com.student.service.his.AnalysisResult analysisResult,
            String patientFhirId, String qrReference,
            String sessionId, Long localUserId, Long conversationId) {

        DiagnosticReport report = toDiagnosticReport(analysisResult, patientFhirId, qrReference);

        String codeSystem = null;
        String codeValue = null;
        String codeDisplay = null;
        if (report.hasCode() && report.getCode().hasCoding()) {
            Coding coding = report.getCode().getCodingFirstRep();
            codeSystem = coding.getSystem();
            codeValue = coding.getCode();
            codeDisplay = coding.getDisplay();
        }

        return FhirDiagnosticReportCache.builder()
                .fhirId(report.getIdElement().getIdPart())
                .patientFhirId(patientFhirId)
                .sessionId(sessionId)
                .questionnaireResponseRef(qrReference)
                .status(report.hasStatus() ? report.getStatus().toCode() : null)
                .codeSystem(codeSystem)
                .codeValue(codeValue)
                .codeDisplay(codeDisplay)
                .conclusion(report.hasConclusion() ? report.getConclusion() : null)
                .issuedDate(report.hasIssued() ? toLocalDateTime(report.getIssued()) : null)
                .resourceJson(toJson(report))
                .localUserId(localUserId)
                .conversationId(conversationId)
                .build();
    }

    // ==================== RiskAssessment 缓存转换 ====================

    /**
     * 将分析结果转换为FhirRiskAssessmentCache实体
     */
    public FhirRiskAssessmentCache toRiskAssessmentCache(
            com.student.service.his.AnalysisResult analysisResult,
            String patientFhirId, String qrReference,
            String sessionId, Long localUserId, Long conversationId) {

        RiskAssessment risk = toRiskAssessment(analysisResult, patientFhirId, qrReference);

        String riskLevel = null;
        String riskDescription = null;
        if (analysisResult.getRiskAssessment() != null) {
            riskLevel = analysisResult.getRiskAssessment().getLevel();
            riskDescription = analysisResult.getRiskAssessment().getDescription();
        }

        boolean urgent = analysisResult.getRiskAssessment() != null
                && analysisResult.getRiskAssessment().isRequiresUrgentAttention();

        return FhirRiskAssessmentCache.builder()
                .fhirId(risk.getIdElement().getIdPart())
                .patientFhirId(patientFhirId)
                .sessionId(sessionId)
                .questionnaireResponseRef(qrReference)
                .status(risk.hasStatus() ? risk.getStatus().toCode() : null)
                .occurrenceDate(risk.hasOccurrence()
                        ? toLocalDateTime(((DateTimeType) risk.getOccurrence()).getValue())
                        : null)
                .riskLevel(riskLevel)
                .riskDescription(riskDescription)
                .requiresUrgentAttention(urgent ? 1 : 0)
                .resourceJson(toJson(risk))
                .localUserId(localUserId)
                .conversationId(conversationId)
                .build();
    }

    // ==================== 工具方法 ====================

    /**
     * 从Reference中提取资源ID（如 "Patient/123" -> "123"）
     */
    private String extractIdFromReference(String reference) {
        if (reference != null && reference.contains("/")) {
            return reference.substring(reference.lastIndexOf("/") + 1);
        }
        return reference;
    }

    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * 递归统计已回答的问题数（有answer的item）
     */
    private int countAnsweredItems(List<QuestionnaireResponse.QuestionnaireResponseItemComponent> items) {
        if (items == null) return 0;
        int count = 0;
        for (var item : items) {
            if (item.hasAnswer()) count++;
            if (item.hasItem()) count += countAnsweredItems(item.getItem());
        }
        return count;
    }

    /**
     * Bundle解析结果容器
     */
    public static class BundleResources {
        public final java.util.List<FhirPatientCache> patients = new java.util.ArrayList<>();
        public final java.util.List<FhirObservationCache> observations = new java.util.ArrayList<>();
        public final java.util.List<FhirConditionCache> conditions = new java.util.ArrayList<>();
        public final java.util.List<FhirQuestionnaireResponseCache> questionnaireResponses
                = new java.util.ArrayList<>();

        public boolean isEmpty() {
            return patients.isEmpty() && observations.isEmpty()
                    && conditions.isEmpty() && questionnaireResponses.isEmpty();
        }

        public int totalCount() {
            return patients.size() + observations.size()
                    + conditions.size() + questionnaireResponses.size();
        }
    }
}
