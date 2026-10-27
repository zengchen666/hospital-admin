package com.hospital.common.exception;

import com.hospital.common.api.Result;
import com.hospital.common.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理：保证任何异常都以 {@link Result} 结构返回，且不把堆栈泄露给前端。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：HTTP 200 + 业务码，前端按 code 分支 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e, HttpServletRequest request) {
        log.info("业务异常 uri={} code={} msg={}", request.getRequestURI(), e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** @RequestBody + @Valid 校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("；"));
        return Result.fail(ErrorCode.PARAM_INVALID, msg.isBlank() ? ErrorCode.PARAM_INVALID.getMsg() : msg);
    }

    /** 表单/查询参数绑定失败 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("；"));
        return Result.fail(ErrorCode.PARAM_INVALID, msg.isBlank() ? ErrorCode.PARAM_INVALID.getMsg() : msg);
    }

    /** 方法级校验（@Validated 在类上，参数直接是简单类型） */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("；"));
        return Result.fail(ErrorCode.PARAM_INVALID, msg);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public Result<Void> handleHandlerMethodValidation(HandlerMethodValidationException e) {
        String msg = e.getAllErrors().stream()
                .map(err -> err.getDefaultMessage() == null ? "参数不合法" : err.getDefaultMessage())
                .collect(Collectors.joining("；"));
        return Result.fail(ErrorCode.PARAM_INVALID, msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.fail(ErrorCode.PARAM_INVALID, "缺少必填参数：" + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.fail(ErrorCode.PARAM_INVALID, "参数类型不正确：" + e.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return Result.fail(ErrorCode.PARAM_INVALID, "请求体格式不正确");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return Result.fail(ErrorCode.PARAM_INVALID, "不支持 " + e.getMethod() + " 请求");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResource(NoResourceFoundException e) {
        return Result.fail(ErrorCode.NO_RESOURCE);
    }

    /**
     * 唯一索引冲突。
     *
     * <p>为什么需要单独处理：逻辑删除的行仍占用唯一索引（已实测
     * {@code ERROR 1062 Duplicate entry}），而业务层查重带 {@code deleted = 0} 过滤，
     * 两边口径不一致时会漏判，最终把 {@code DuplicateKeyException} 抛到兜底变成 50001，
     * 用户看到「服务器开小差」，实际只是账号重名。</p>
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一索引冲突：{}", e.getMostSpecificCause().getMessage());
        return Result.fail(ErrorCode.DUPLICATE_DATA);
    }

    /**
     * 方法级权限拒绝（@PreAuthorize 不通过）。
     *
     * <p>必须单独处理：否则会被下面的 Exception 兜底吞成 50001，
     * 前端拿不到「无权限」这个可判断的信号（Spring Security 6.3+ 抛的是
     * AuthorizationDeniedException，它是 AccessDeniedException 的子类）。</p>
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        log.warn("权限不足 uri={} user={}", request.getRequestURI(),
                CurrentUser.orNull() == null ? "anonymous" : CurrentUser.orNull().getUsername());
        return Result.fail(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleAuthentication(AuthenticationException e) {
        return Result.fail(ErrorCode.UNAUTHORIZED);
    }

    /** 兜底：未预期异常记录完整堆栈，对外只给通用提示 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("未处理异常 uri={} method={}", request.getMethod(), request.getRequestURI(), e);
        return Result.fail(ErrorCode.SERVER_ERROR);
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + " " + fieldError.getDefaultMessage();
    }
}
