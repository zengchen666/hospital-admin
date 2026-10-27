package com.hospital.common.security;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.system.service.SysUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/**
 * JWT 认证过滤器：解析 Authorization 头并把登录态写入 SecurityContext。
 *
 * <p>解析失败时<b>不抛异常</b>，保持匿名身份继续走过滤器链，
 * 由 Security 的 AuthenticationEntryPoint 统一返回 40101，避免 500 与 401 混用。</p>
 *
 * <p>除签名与有效期外，还做<b>版本号校验</b>：
 * token 里的 {@code ver} 必须等于当前用户的 {@code version}。
 * 改密、禁用、管理员强制下线都会让库里的 version 递增，
 * 从而让所有已签发的 access token 立刻失效 —— 这是「改密即踢下线」的实现方式，
 * 也是不引入 access token 黑名单（每请求多一次 Redis 往返）的替代方案。</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final SysUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, SysUserDetailsService userDetailsService) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                JwtTokenProvider.TokenPayload payload = tokenProvider.parseAccessToken(token);
                LoginUser cached = userDetailsService.loadByUserId(payload.userId());

                // 版本号不一致 = 改密/禁用/被踢之后签发的旧 token，直接拒绝
                if (!Objects.equals(payload.version(), cached.getTokenVersion())) {
                    throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
                }

                // sid 是请求级信息（不参与缓存），还原登录态时注入，登出时据此定位会话
                LoginUser loginUser = cached.toBuilder().sessionId(payload.sessionId()).build();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (BusinessException e) {
                // token 非法/过期/用户被禁用/版本已失效：清空上下文，交给 EntryPoint 返回 40101
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}
