package com.student.exception;

import lombok.Getter;

/**
 * 基础异常类
 * 所有业务异常的基类，提供错误码和错误消息
 *
 * @author 系统
 * @version 1.0
 */
@Getter
public class BaseException extends RuntimeException {

    /**
     * 错误码
     */
    private final String code;

    /**
     * 错误消息
     */
    private final String message;

    /**
     * 构造基础异常
     *
     * @param code 错误码
     * @param message 错误消息
     */
    public BaseException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    /**
     * 构造基础异常（带原因）
     *
     * @param code 错误码
     * @param message 错误消息
     * @param cause 原因异常
     */
    public BaseException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.message = message;
    }

    /**
     * 获取完整错误信息
     *
     * @return 错误码:错误消息
     */
    @Override
    public String toString() {
        return String.format("%s: %s", code, message);
    }
}