package com.student.dto;

import com.student.exception.ErrorCode;
import lombok.Data;

/**
 * 通用API响应类
 * 用于统一API响应格式
 *
 * @param <T> 响应数据类型
 * @author 系统
 * @version 2.0
 */
@Data
public class ApiResponse<T> {

    /**
     * 请求是否成功
     */
    private boolean success;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 错误码（成功时为"00000"，失败时为具体错误码）
     */
    private String code;

    /**
     * 时间戳
     */
    private long timestamp;

    /**
     * 无参构造函数（用于反序列化）
     */
    public ApiResponse() {
        this.timestamp = System.currentTimeMillis();
        this.code = ErrorCode.SUCCESS.getCode();
    }

    /**
     * 全参数构造函数
     *
     * @param success 是否成功
     * @param message 响应消息
     * @param data 响应数据
     * @param code 错误码
     * @param timestamp 时间戳
     */
    public ApiResponse(boolean success, String message, T data, String code, long timestamp) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.code = code;
        this.timestamp = timestamp;
    }

    /**
     * 兼容构造函数（保持向后兼容）
     * 成功时使用SUCCESS错误码，失败时使用SYSTEM_ERROR错误码
     *
     * @param success 是否成功
     * @param message 响应消息
     * @param data 响应数据
     * @param timestamp 时间戳
     */
    public ApiResponse(boolean success, String message, T data, long timestamp) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.timestamp = timestamp;
        this.code = success ? ErrorCode.SUCCESS.getCode() : ErrorCode.SYSTEM_ERROR.getCode();
    }

    /**
     * 成功响应（无数据）
     * @return API响应
     */
    public static ApiResponse<Void> success() {
        return new ApiResponse<>(true, "操作成功", null, System.currentTimeMillis());
    }

    /**
     * 成功响应（带数据）
     * @param data 响应数据
     * @param <T> 数据类型
     * @return API响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "操作成功", data, System.currentTimeMillis());
    }

    /**
     * 成功响应（带消息和数据）
     * @param message 响应消息
     * @param data 响应数据
     * @param <T> 数据类型
     * @return API响应
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, System.currentTimeMillis());
    }

    /**
     * 成功响应（带错误码、消息和数据）
     * @param code 错误码
     * @param message 响应消息
     * @param data 响应数据
     * @param <T> 数据类型
     * @return API响应
     */
    public static <T> ApiResponse<T> success(String code, String message, T data) {
        return new ApiResponse<>(true, message, data, code, System.currentTimeMillis());
    }

    /**
     * 错误响应
     * @param message 错误消息
     * @return API响应
     */
    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(false, message, null, System.currentTimeMillis());
    }

    /**
     * 错误响应（带数据）
     * @param message 错误消息
     * @param data 错误数据
     * @param <T> 数据类型
     * @return API响应
     */
    public static <T> ApiResponse<T> error(String message, T data) {
        return new ApiResponse<>(false, message, data, System.currentTimeMillis());
    }

    /**
     * 错误响应（带错误码和消息）
     * @param code 错误码
     * @param message 错误消息
     * @return API响应
     */
    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(false, message, null, code, System.currentTimeMillis());
    }

    /**
     * 错误响应（带错误码、消息和数据）
     * @param code 错误码
     * @param message 错误消息
     * @param data 错误数据
     * @param <T> 数据类型
     * @return API响应
     */
    public static <T> ApiResponse<T> error(String code, String message, T data) {
        return new ApiResponse<>(false, message, data, code, System.currentTimeMillis());
    }

    /**
     * 使用ErrorCode创建错误响应
     * @param errorCode 错误码枚举
     * @return API响应
     */
    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return new ApiResponse<>(false, errorCode.getMessage(), null, errorCode.getCode(), System.currentTimeMillis());
    }

    /**
     * 使用ErrorCode创建错误响应（带数据）
     * @param errorCode 错误码枚举
     * @param data 错误数据
     * @param <T> 数据类型
     * @return API响应
     */
    public static <T> ApiResponse<T> error(ErrorCode errorCode, T data) {
        return new ApiResponse<>(false, errorCode.getMessage(), data, errorCode.getCode(), System.currentTimeMillis());
    }

    /**
     * 创建分页响应
     * @param data 分页数据
     * @param total 总记录数
     * @param page 当前页码
     * @param size 每页大小
     * @param <T> 数据类型
     * @return API响应
     */
    public static <T> ApiResponse<Pagination<T>> pagination(T data, long total, int page, int size) {
        Pagination<T> pagination = new Pagination<>(data, total, page, size);
        return success(pagination);
    }

    /**
     * 分页数据包装类
     * @param <T> 数据类型
     */
    @Data
    public static class Pagination<T> {
        private T data;
        private long total;
        private int page;
        private int size;
        private int totalPages;

        /**
         * 无参构造函数（用于反序列化）
         */
        public Pagination() {
        }

        /**
         * 构造函数
         * @param data 分页数据
         * @param total 总记录数
         * @param page 当前页码
         * @param size 每页大小
         */
        public Pagination(T data, long total, int page, int size) {
            this.data = data;
            this.total = total;
            this.page = page;
            this.size = size;
            this.totalPages = (int) Math.ceil((double) total / size);
        }
    }
}