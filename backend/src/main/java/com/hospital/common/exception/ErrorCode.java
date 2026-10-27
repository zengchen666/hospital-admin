package com.hospital.common.exception;

/**
 * 业务错误码。
 *
 * <p>与《接口,字段,原型图文档》1.3 / 1.3.1 一一对应。HTTP 状态统一 200，
 * 业务结果由 code 区分，前端按 code 分支处理。</p>
 */
public enum ErrorCode {

    SUCCESS(200, "success"),

    /* ---------- 通用 ---------- */
    PARAM_INVALID(40001, "参数校验失败"),
    UNAUTHORIZED(40101, "登录状态已失效，请重新登录"),
    BAD_CREDENTIALS(40102, "账号或密码错误"),
    LOGIN_LOCKED(40103, "登录失败次数过多，请稍后再试"),
    FORBIDDEN(40301, "无操作权限"),
    DATA_FORBIDDEN(40302, "无权访问该数据"),
    CONFLICT(40900, "数据状态已变更，请刷新后重试"),
    DUPLICATE_DATA(40904, "数据已存在，请检查唯一字段"),
    NO_STOCK(40901, "号源不足"),
    STATUS_NOT_ALLOWED(40902, "当前状态不允许该操作"),
    REPEAT_SUBMIT(40903, "请勿重复提交"),
    STOCK_NOT_ENOUGH(42201, "库存不足"),
    NO_RESOURCE(40400, "请求的资源不存在"),
    SERVER_ERROR(50001, "服务器开小差了，请稍后重试");

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
