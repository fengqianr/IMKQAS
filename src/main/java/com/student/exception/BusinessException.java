package com.student.exception;

/**
 * 业务异常
 * 用于业务逻辑错误，如数据校验失败、业务规则违反等
 *
 * @author 系统
 * @version 1.0
 */
public class BusinessException extends BaseException {

    /**
     * 构造业务异常
     *
     * @param code 错误码
     * @param message 错误消息
     */
    public BusinessException(String code, String message) {
        super(code, message);
    }

    /**
     * 构造业务异常（带原因）
     *
     * @param code 错误码
     * @param message 错误消息
     * @param cause 原因异常
     */
    public BusinessException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }

    /**
     * 构造业务异常（使用通用错误码）
     *
     * @param message 错误消息
     */
    public BusinessException(String message) {
        this("BUSINESS_ERROR", message);
    }
}