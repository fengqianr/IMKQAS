package com.student.handler;

import com.student.dto.ApiResponse;
import com.student.exception.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 统一响应处理器
 * 自动将控制器返回值包装为ApiResponse格式
 *
 * @author 系统
 * @version 1.0
 */
@RestControllerAdvice(basePackages = "com.student.controller")
public class ResponseAdvice implements ResponseBodyAdvice<Object> {

    /**
     * 判断是否支持响应处理
     *
     * @param returnType 返回类型
     * @param converterType 消息转换器类型
     * @return 是否支持
     */
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 排除以下情况不处理：
        // 1. 已经返回ApiResponse类型
        // 2. 返回ResponseEntity类型（让GlobalExceptionHandler处理）
        // 3. 返回String类型（可能是视图名称）
        return !returnType.getParameterType().equals(ApiResponse.class)
                && !returnType.getParameterType().equals(ResponseEntity.class)
                && !returnType.getParameterType().equals(String.class);
    }

    /**
     * 处理响应体
     *
     * @param body 响应体
     * @param returnType 返回类型
     * @param selectedContentType 选择的内容类型
     * @param selectedConverterType 选择的消息转换器类型
     * @param request 请求
     * @param response 响应
     * @return 处理后的响应体
     */
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {

        // 如果body已经是ApiResponse，直接返回
        if (body instanceof ApiResponse) {
            return body;
        }

        // 如果body是ResponseEntity，不处理（由GlobalExceptionHandler处理）
        if (body instanceof ResponseEntity) {
            return body;
        }

        // 如果body为null，返回成功的空响应
        if (body == null) {
            return ApiResponse.success();
        }

        // 包装为ApiResponse
        return ApiResponse.success(body);
    }
}