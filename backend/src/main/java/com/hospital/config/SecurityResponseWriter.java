package com.hospital.config;

import com.hospital.common.api.Result;
import com.hospital.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 安全异常响应写出器：把 401/403 统一成 {@link Result} 结构。
 *
 * <p>为什么不用 {@code response.sendError}：容器默认会跳到 /error，返回结构不是我们约定的
 * {@code {code,msg,data}}，前端要额外分支处理。</p>
 */
final class SecurityResponseWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SecurityResponseWriter() {
    }

    static void write(HttpServletResponse response, int httpStatus, int code, String msg) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Result<Void> body = Result.fail(code, msg);
        response.getWriter().write(MAPPER.writeValueAsString(body));
    }

    static void writeUnauthorized(HttpServletResponse response) throws IOException {
        write(response, HttpServletResponse.SC_UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMsg());
    }
}
