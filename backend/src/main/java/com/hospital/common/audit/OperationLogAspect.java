package com.hospital.common.audit;

import com.hospital.common.security.CurrentUser;
import com.hospital.common.security.LoginUser;
import com.hospital.system.entity.SysOperationLog;
import com.hospital.system.mapper.SysOperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 审计日志切面：记录谁、在什么时间、从哪个 IP、对哪个接口做了什么、结果如何。
 *
 * <p>审计写入失败不会影响业务主流程，但会打 warn 日志 —— 审计是合规要求，
 * 失败必须有迹可循，不能静默吞掉。</p>
 */
@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    private static final int MAX_PARAMS_LENGTH = 2000;
    private static final int MAX_ERROR_LENGTH = 500;

    /** 需要脱敏的字段名（JSON 里出现即打码） */
    private static final String[] SENSITIVE_KEYS = {"password", "oldPassword", "newPassword", "idCard", "phone"};

    private final SysOperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    public OperationLogAspect(SysOperationLogMapper operationLogMapper, ObjectMapper objectMapper) {
        this.operationLogMapper = operationLogMapper;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();
        Throwable error = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            saveLog(joinPoint, auditLog, start, error);
        }
    }

    private void saveLog(ProceedingJoinPoint joinPoint, AuditLog auditLog, long start, Throwable error) {
        try {
            SysOperationLog entity = new SysOperationLog();
            LoginUser user = CurrentUser.orNull();
            if (user != null) {
                entity.setUserId(user.getUserId());
                entity.setUsername(user.getUsername());
            }
            entity.setModule(auditLog.module());
            entity.setOperation(auditLog.operation());

            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            entity.setMethod(signature.getDeclaringType().getSimpleName() + "." + signature.getName());
            entity.setParams(buildParams(joinPoint.getArgs()));

            HttpServletRequest request = currentRequest();
            if (request != null) {
                entity.setRequestUri(request.getRequestURI());
                entity.setIp(clientIp(request));
                entity.setUserAgent(truncate(request.getHeader("User-Agent"), 255));
            }

            entity.setStatus(error == null ? 1 : 0);
            if (error != null) {
                entity.setErrorMsg(truncate(error.getMessage(), MAX_ERROR_LENGTH));
            }
            // 至少要 1ms，方便统计时区分「瞬时」与「异常快」
            entity.setCostTime((int) Math.max(1L, System.currentTimeMillis() - start));
            entity.setCreateTime(LocalDateTime.now());

            operationLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("操作日志写入失败 module={} operation={}", auditLog.module(), auditLog.operation(), e);
        }
    }

    /**
     * 参数序列化为 JSON。
     *
     * <p>不能用 {@code String::valueOf}：POJO 的 toString 默认是
     * {@code UserSaveRequest@4db0c924}，等于什么都没记。</p>
     */
    private String buildParams(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (Object arg : args) {
            if (arg instanceof HttpServletRequest || arg instanceof jakarta.servlet.http.HttpServletResponse) {
                continue;
            }
            parts.add(toJson(arg));
        }
        if (parts.isEmpty()) {
            return null;
        }
        return truncate(mask("[" + String.join(",", parts) + "]"), MAX_PARAMS_LENGTH);
    }

    private String toJson(Object arg) {
        try {
            return objectMapper.writeValueAsString(arg);
        } catch (Exception e) {
            // 序列化失败（如包含无法序列化的类型）时退化为类型名，至少能看出传了什么
            return arg == null ? "null" : "\"" + arg.getClass().getSimpleName() + "(unserializable)\"";
        }
    }

    /** 简单键值打码：password=xxx / "password":"xxx" 均覆盖 */
    private String mask(String text) {
        String masked = text;
        for (String key : SENSITIVE_KEYS) {
            masked = masked.replaceAll("(?i)(\"" + key + "\"\\s*:\\s*\")[^\"]*(\")", "$1***$2");
            masked = masked.replaceAll("(?i)(" + key + "=)[^,&\\)\\s]*", "$1***");
        }
        return masked;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank() && !"unknown".equalsIgnoreCase(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
