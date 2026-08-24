package com.imkqas.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.imkqas.dto.his.FhirConverter;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.BaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Jackson FHIR 资源序列化配置
 *
 * HAPI FHIR 的 Resource 对象内部持有 idElement 等自引用字段，
 * 若直接交给 Spring Jackson 序列化，会递归展开自引用链，
 * 导致嵌套深度超限（StreamWriteConstraints 上限 1000）抛 JsonMappingException，
 * 且单条资源体积膨胀到数百 KB，前端无法解析。
 *
 * 此处为 FHIR 资源注册专用序列化器，将序列化委托给 HAPI 官方 JSON 编码器
 * （FhirConverter.toJson），输出标准 FHIR R4 JSON，供前端按标准结构解析。
 *
 * @author 系统
 * @version 1.0
 */
@Configuration
@RequiredArgsConstructor
public class FhirJacksonConfig {

    /** 需要标准 FHIR JSON 序列化的资源类型 */
    private static final Class<?>[] FHIR_RESOURCE_TYPES = {
            Patient.class,
            Condition.class,
            Observation.class,
            QuestionnaireResponse.class,
            Bundle.class
    };

    private final FhirConverter fhirConverter;

    /**
     * 将 FHIR 资源序列化器注册到 Spring 的 ObjectMapper
     *
     * @return Jackson 定制器
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer fhirJacksonCustomizer() {
        SimpleModule module = new SimpleModule("fhir-resource-serializer");
        FhirResourceSerializer serializer = new FhirResourceSerializer(fhirConverter);
        for (Class<?> type : FHIR_RESOURCE_TYPES) {
            @SuppressWarnings("unchecked")
            Class<BaseResource> resourceType = (Class<BaseResource>) type;
            module.addSerializer(resourceType, serializer);
        }
        // 兜底：覆盖上述之外的 FHIR 资源子类（如未来新增的资源类型）
        module.addSerializer(BaseResource.class, serializer);
        // 注意：必须用 modulesToInstall() 而非 modules()。
        // modules() 是替换式注册（置 findWellKnownModules=false），会冲掉 Spring Boot 默认的
        // JavaTimeModule，导致所有直出 java.time.LocalDateTime 的接口
        // （GET /api/conversations/by-user/{id}、GET /api/his/interview/history 等）
        // 序列化抛 InvalidDefinitionException。modulesToInstall() 追加本模块并保留默认模块。
        return builder -> builder.modulesToInstall(module);
    }

    /**
     * FHIR 资源序列化器
     * 将 HAPI FHIR 资源委托给 FhirConverter.toJson（HAPI JSON 编码器）
     */
    static class FhirResourceSerializer extends JsonSerializer<BaseResource> {

        private final FhirConverter converter;

        FhirResourceSerializer(FhirConverter converter) {
            this.converter = converter;
        }

        @Override
        public void serialize(BaseResource value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            String json = converter.toJson(value);
            if (json == null) {
                // 序列化失败时输出 null，避免抛出异常中断响应
                provider.defaultSerializeNull(gen);
                return;
            }
            // 将 HAPI 编码出的标准 FHIR JSON 作为原生 JSON 写入
            gen.writeRawValue(json);
        }
    }
}
