package com.imkqas.service.his;

import org.hl7.fhir.r4.model.Questionnaire;

import java.util.List;
import java.util.Optional;

/**
 * FHIR 问卷客户端接口
 * 抽象问卷资源的读取来源：当前本地 JSON 实现，后续可切换真实 HAPI FHIR 服务器。
 *
 * @author 系统
 * @version 1.0
 */
public interface FhirQuestionnaireClient {

    /**
     * 按标识获取问卷资源
     *
     * @param id 问卷标识（对应 Questionnaire.name，如 PHQ-9）
     * @return 问卷资源，不存在时返回空
     */
    Optional<Questionnaire> getById(String id);

    /**
     * 按标题模糊检索问卷
     *
     * @param title 标题关键词
     * @return 匹配的问卷列表
     */
    List<Questionnaire> searchByTitle(String title);

    /**
     * 获取所有活跃（ACTIVE）状态的问卷
     *
     * @return 活跃问卷列表
     */
    List<Questionnaire> listActive();
}
