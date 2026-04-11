package com.student.exception;

/**
 * 认证异常
 * 用于用户认证失败的情况，如无效令牌、过期令牌等
 *
 * @author 系统
 * @version 1.0
 */
public class AuthenticationException extends BaseException {

    /**
     * 构造认证异常
     *
     * @param code 错误码
     * @param message 错误消息
     */
    public AuthenticationException(String code, String message) {
        super(code, message);
    }

    /**
     * 构造认证异常（带原因）
     *
     * @param code 错误码
     * @param message 错误消息
     * @param cause 原因异常
     */
    public AuthenticationException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }

    /**
     * 构造认证异常（使用通用错误码）
     *
     * @param message 错误消息
     */
    public AuthenticationException(String message) {
        this("AUTHENTICATION_ERROR", message);
    }
}