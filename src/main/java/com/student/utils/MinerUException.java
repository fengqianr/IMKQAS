package com.student.utils;

/**
 * MinerU PDF 转换异常
 *
 * @author 系统
 * @version 1.0
 */
public class MinerUException extends RuntimeException {

    public MinerUException(String message) {
        super(message);
    }

    public MinerUException(String message, Throwable cause) {
        super(message, cause);
    }
}
