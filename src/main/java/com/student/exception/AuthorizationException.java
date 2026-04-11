package com.student.exception;

/**
 * 授权异常
 * 用于用户权限不足的情况
 *
 * @author 系统
 * @version 1.0
 */
public class AuthorizationException extends BaseException {

    /**
     * 构造授权异常
     *
     * @param code 错误码
     * @param message 错误消息
     */
    public AuthorizationException(String code, String message) {
        super(code, message);
    }

    /**
     * 构造授权异常（带原因）
     *
     * @param code 错误码
     * @param message 错误消息
     * @param cause 原因异常
     */
    public AuthorizationException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }

    /**
     * 构造授权异常（使用通用错误码）
     *
     * @param message 错误消息
     */
    public AuthorizationException(String message) {
        this("AUTHORIZATION_ERROR", message);
    }
}