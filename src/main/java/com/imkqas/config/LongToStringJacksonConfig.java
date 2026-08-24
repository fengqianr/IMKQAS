package com.imkqas.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Long 转 String 序列化配置
 *
 * 主键使用雪花算法生成（如 userId=2088194171323465729），超过 JS Number 安全整数上限
 * （2^53-1 ≈ 9007199254740991）。前端 Axios 默认用 JSON.parse 反序列化响应体，超过安全
 * 整数的 Long 会被转成 double 丢失精度（末位被舍入，如 2088194171323465729 被解析为
 * 2088194171323465700），导致以 userId 等雪花 ID 拼接的请求路径查不到数据（HTTP 404），
 * 表现为「健康档案/身份信息加载失败、保存失败」。
 *
 * 统一将 Long/long 序列化为字符串规避该问题：字符串形式无精度损失，前端可直接拼接 URL；
 * 后端反序列化时 Jackson 自动支持 String→Long 转换，不影响以 Long 接收的请求体。
 * 注意：必须用 modulesToInstall() 追加而非 modules() 替换，否则会冲掉 Spring Boot 默认的
 * JavaTimeModule，导致 java.time 类型序列化异常（详见 FhirJacksonConfig 中的同类说明）。
 *
 * @author 系统
 * @version 1.0
 */
@Configuration
public class LongToStringJacksonConfig {

    /**
     * 将 Long/long 序列化为字符串，避免雪花 ID 精度丢失
     *
     * 注意：必须用 serializerByType 直接做类型映射，而非 modulesToInstall 追加模块。
     * 追加模块与 FhirJacksonConfig 的 FHIR 资源模块合并时，会破坏 FHIR 资源序列化器的注册
     * （Patient 等资源退化为标准 Jackson 递归序列化，自引用链展开至 1000 层上限，
     * 抛 Document nesting depth (1001) exceeds 1000 并产出损坏 JSON）。
     *
     * @return Jackson 定制器
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringJacksonCustomizer() {
        return builder -> {
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
        };
    }
}
