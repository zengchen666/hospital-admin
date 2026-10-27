package com.hospital.web;

import com.hospital.common.api.Result;
import com.hospital.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringBootVersion;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查：供部署探针与联调自检使用，<b>无需鉴权</b>。
 *
 * <p>因为免鉴权，所以这里只允许输出「服务是否存活」。
 * 数据库连接串、版本号、异常原文等都属于内网信息，泄露给未登录者等于免费侦察，
 * 因此仅在 dev profile 下输出明细。</p>
 *
 * <p>探针约定：DB 不可用时返回 HTTP 503，便于 K8s / Nginx 直接判定，
 * 不必解析响应体。</p>
 */
@Slf4j
@RestController
@RequestMapping("/system/health")
public class HealthController {

    private final DataSource dataSource;
    private final Environment environment;

    public HealthController(DataSource dataSource, Environment environment) {
        this.dataSource = dataSource;
        this.environment = environment;
    }

    @GetMapping
    public ResponseEntity<Result<Map<String, Object>>> health() {
        boolean dev = environment.acceptsProfiles(Profiles.of("dev"));

        Map<String, Object> info = new LinkedHashMap<>();
        boolean dbUp = checkDatabase();
        if (!dbUp) {
            // 失败只回通用文案，异常细节进日志（带 traceId 可追）
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Result.fail(ErrorCode.SERVER_ERROR, "数据库不可用"));
        }

        info.put("status", "UP");
        if (dev) {
            info.put("application", "hospital-admin");
            info.put("javaVersion", System.getProperty("java.version"));
            info.put("springBootVersion", SpringBootVersion.getVersion());
            info.put("profiles", String.join(",", environment.getActiveProfiles()));
        }
        return ResponseEntity.ok(Result.ok(info));
    }

    private boolean checkDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(2);
            if (environment.acceptsProfiles(Profiles.of("dev"))) {
                log.debug("健康检查：数据库连接 {}", valid ? "正常" : "无效");
            }
            if (!valid) {
                log.warn("健康检查：数据库连接无效");
            }
            return valid;
        } catch (Exception e) {
            log.error("健康检查：数据库连接失败", e);
            return false;
        }
    }
}
