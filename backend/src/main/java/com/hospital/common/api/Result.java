package com.hospital.common.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hospital.common.exception.ErrorCode;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一响应结构：{@code { "code": 200, "msg": "success", "data": {} }}。
 *
 * <p>Controller 禁止直接返回 Map 或实体，一律包装为本类。</p>
 */
@Getter
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;
    private final String msg;
    private final T data;

    private Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> Result<T> ok() {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMsg(), null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMsg(), data);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMsg(), null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String msg) {
        return new Result<>(errorCode.getCode(), msg, null);
    }

    public static <T> Result<T> fail(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    /**
     * 仅内部判断用，不参与 JSON 序列化 —— 否则响应体会多出 success 字段，
     * 破坏 {code,msg,data} 契约。
     */
    @JsonIgnore
    public boolean isSuccess() {
        return this.code == ErrorCode.SUCCESS.getCode();
    }
}
