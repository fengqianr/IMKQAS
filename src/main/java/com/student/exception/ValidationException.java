package com.student.exception;

/**
 * 参数验证异常
 * 用于请求参数验证失败的情况
 *
 * @author 系统
 * @version 1.0
 */
public class ValidationException extends BaseException {

    /**
     * 构造参数验证异常
     *
     * @param code 错误码
     * @param message 错误消息
     */
    public ValidationException(String code, String message) {
        super(code, message);
    }

    /**
     * 构造参数验证异常（带原因）
     *
     * @param code 错误码
     * @param message 错误消息
     * @param cause 原因异常
     */
    public ValidationException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }

    /**
     * 构造参数验证异常（使用通用错误码）
     *
     * @param message 错误消息
     */
    public ValidationException(String message) {
        this("VALIDATION_ERROR", message);
    }

    /**
     * 构造参数验证异常（带字段名）
     *
     * @param field 字段名
     * @param message 验证消息
     * @param args 消息格式化参数
     */
    public ValidationException(String field, String message, Object... args) {
        this("VALIDATION_ERROR", String.format("字段 '%s' 验证失败: %s", field, String.format(message, args)));
    }
}