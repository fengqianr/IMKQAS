package com.student.exception;

/**
 * 错误码枚举
 * 定义系统通用的错误码
 *
 * @author 系统
 * @version 1.0
 */
public enum ErrorCode {

    /**
     * 成功
     */
    SUCCESS("00000", "成功"),

    /**
     * 系统内部错误
     */
    SYSTEM_ERROR("10000", "系统内部错误"),

    /**
     * 服务不可用
     */
    SERVICE_UNAVAILABLE("10001", "服务暂时不可用，请稍后重试"),

    /**
     * 数据库错误
     */
    DATABASE_ERROR("10002", "数据库操作失败"),

    /**
     * 参数验证失败
     */
    VALIDATION_ERROR("20000", "参数验证失败"),

    /**
     * 缺少必要参数
     */
    MISSING_PARAMETER("20001", "缺少必要参数"),

    /**
     * 参数格式错误
     */
    PARAMETER_FORMAT_ERROR("20002", "参数格式错误"),

    /**
     * 认证失败
     */
    AUTHENTICATION_ERROR("30000", "认证失败"),

    /**
     * 令牌无效或过期
     */
    TOKEN_INVALID("30001", "令牌无效或已过期"),

    /**
     * 令牌缺失
     */
    TOKEN_MISSING("30002", "未提供授权令牌"),

    /**
     * 用户名或密码错误
     */
    INVALID_CREDENTIALS("30003", "用户名或密码错误"),

    /**
     * 权限不足
     */
    AUTHORIZATION_ERROR("40000", "权限不足"),

    /**
     * 访问被拒绝
     */
    ACCESS_DENIED("40001", "访问被拒绝"),

    /**
     * 资源未找到
     */
    RESOURCE_NOT_FOUND("50000", "资源未找到"),

    /**
     * 用户未找到
     */
    USER_NOT_FOUND("50001", "用户未找到"),

    /**
     * 文档未找到
     */
    DOCUMENT_NOT_FOUND("50002", "文档未找到"),

    /**
     * 业务逻辑错误
     */
    BUSINESS_ERROR("60000", "业务逻辑错误"),

    /**
     * 操作不允许
     */
    OPERATION_NOT_ALLOWED("60001", "操作不允许"),

    /**
     * 数据重复
     */
    DUPLICATE_DATA("60002", "数据重复"),

    /**
     * 第三方服务错误
     */
    THIRD_PARTY_ERROR("70000", "第三方服务错误"),

    /**
     * 外部API调用失败
     */
    EXTERNAL_API_ERROR("70001", "外部API调用失败"),

    /**
     * 未知错误
     */
    UNKNOWN_ERROR("99999", "未知错误");

    /**
     * 错误码
     */
    private final String code;

    /**
     * 错误消息
     */
    private final String message;

    /**
     * 构造错误码
     *
     * @param code 错误码
     * @param message 错误消息
     */
    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 获取错误码
     *
     * @return 错误码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取错误消息
     *
     * @return 错误消息
     */
    public String getMessage() {
        return message;
    }

    /**
     * 根据错误码获取错误码枚举
     *
     * @param code 错误码
     * @return 错误码枚举，如果未找到返回UNKNOWN_ERROR
     */
    public static ErrorCode fromCode(String code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.getCode().equals(code)) {
                return errorCode;
            }
        }
        return UNKNOWN_ERROR;
    }

    /**
     * 获取完整的错误信息
     *
     * @return 错误码:错误消息
     */
    @Override
    public String toString() {
        return String.format("%s: %s", code, message);
    }
}