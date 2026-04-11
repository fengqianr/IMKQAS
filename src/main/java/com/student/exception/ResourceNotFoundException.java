package com.student.exception;

/**
 * 资源未找到异常
 * 用于请求的资源不存在的情况
 *
 * @author 系统
 * @version 1.0
 */
public class ResourceNotFoundException extends BaseException {

    /**
     * 构造资源未找到异常
     *
     * @param code 错误码
     * @param message 错误消息
     */
    public ResourceNotFoundException(String code, String message) {
        super(code, message);
    }

    /**
     * 构造资源未找到异常（带原因）
     *
     * @param code 错误码
     * @param message 错误消息
     * @param cause 原因异常
     */
    public ResourceNotFoundException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }

    /**
     * 构造资源未找到异常（使用通用错误码）
     *
     * @param message 错误消息
     */
    public ResourceNotFoundException(String message) {
        this("RESOURCE_NOT_FOUND", message);
    }

    /**
     * 构造资源未找到异常（指定资源类型和ID）
     *
     * @param resourceType 资源类型
     * @param resourceId 资源ID
     */
    public ResourceNotFoundException(String resourceType, Object resourceId) {
        this("RESOURCE_NOT_FOUND", String.format("%s (ID: %s) 未找到", resourceType, resourceId));
    }
}