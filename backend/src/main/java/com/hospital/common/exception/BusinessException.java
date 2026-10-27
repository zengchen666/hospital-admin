package com.hospital.common.exception;

import lombok.Getter;

/**
 * 业务异常：带错误码，由 {@link GlobalExceptionHandler} 统一转换为响应体。
 *
 * <p>约定：Service 层遇到可预期的业务失败一律抛本异常，不要返回 null 或布尔值。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.code = errorCode.getCode();
    }

    public BusinessException(ErrorCode errorCode, String msg) {
        super(msg);
        this.code = errorCode.getCode();
    }

    /** 用于「XX 不存在」这类需要拼接业务数据的提示。 */
    public static BusinessException of(ErrorCode errorCode, String template, Object... args) {
        return new BusinessException(errorCode, String.format(template, args));
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        // 业务异常是预期分支，不需要堆栈，省掉开销
        return this;
    }
}
