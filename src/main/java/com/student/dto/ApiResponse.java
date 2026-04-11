package com.student.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用API响应类
 * 用于统一API响应格式
 *
 * @param <T> 响应数据类型
 * @author 系统
 * @version 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
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
     * 时间戳
     */
    private long timestamp = System.currentTimeMillis();

    /**
     * 成功响应（无数据）
     * @return API响应
     */
    public static ApiResponse<Void> success() {
        return new ApiResponse<Void>(true, "操作成功", null, System.currentTimeMillis());
    }

    /**
     * 成功响应（带数据）
     * @param data 响应数据
     * @param <T> 数据类型
     * @return API响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<T>(true, "操作成功", data, System.currentTimeMillis());
    }

    /**
     * 成功响应（带消息和数据）
     * @param message 响应消息
     * @param data 响应数据
     * @param <T> 数据类型
     * @return API响应
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<T>(true, message, data, System.currentTimeMillis());
    }

    /**
     * 错误响应
     * @param message 错误消息
     * @return API响应
     */
    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<Void>(false, message, null, System.currentTimeMillis());
    }

    /**
     * 错误响应（带数据）
     * @param message 错误消息
     * @param data 错误数据
     * @param <T> 数据类型
     * @return API响应
     */
    public static <T> ApiResponse<T> error(String message, T data) {
        return new ApiResponse<T>(false, message, data, System.currentTimeMillis());
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
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pagination<T> {
        private T data;
        private long total;
        private int page;
        private int size;
        private int totalPages;

        public Pagination(T data, long total, int page, int size) {
            this.data = data;
            this.total = total;
            this.page = page;
            this.size = size;
            this.totalPages = (int) Math.ceil((double) total / size);
        }
    }
}