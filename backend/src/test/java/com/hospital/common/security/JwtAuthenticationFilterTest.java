package com.hospital.common.security;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.system.service.SysUserDetailsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JWT 过滤器：身份还原与「版本号踢人」。
 *
 * <p>关键点：token 签名有效不代表还能用 —— 改密/禁用后版本号会变，
 * 过滤器必须把这种 token 判为无效，否则「改密即踢下线」形同虚设。</p>
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String TOKEN = "header.payload.signature";

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private SysUserDetailsService userDetailsService;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(tokenProvider, userDetailsService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private LoginUser cachedUser(int tokenVersion) {
        return LoginUser.builder()
                .userId(1L)
                .username("admin")
                .realName("系统管理员")
                .deptId(1001L)
                .tokenVersion(tokenVersion)
                .roleCodes(new LinkedHashSet<>(Set.of("admin")))
                .permissions(new LinkedHashSet<>(Set.of("system:user:query")))
                .build();
    }

    private JwtTokenProvider.TokenPayload payload(Integer version) {
        return new JwtTokenProvider.TokenPayload(1L, "admin", 1001L, "sid-1", "jti-1", version,
                System.currentTimeMillis() / 1000 + 3600);
    }

    private MockHttpServletRequest requestWithToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + TOKEN);
        return request;
    }

    @Test
    @DisplayName("签名有效且版本一致：写入登录态，并把会话 ID 带进上下文")
    void validTokenPopulatesSecurityContext() throws Exception {
        when(tokenProvider.parseAccessToken(TOKEN)).thenReturn(payload(0));
        when(userDetailsService.loadByUserId(1L)).thenReturn(cachedUser(0));

        filter.doFilter(requestWithToken(), new MockHttpServletResponse(), new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(LoginUser.class);
        LoginUser principal = (LoginUser) auth.getPrincipal();
        assertThat(principal.getUserId()).isEqualTo(1L);
        // sid 不进缓存，必须由过滤器注入，否则登出无法定位会话
        assertThat(principal.getSessionId()).isEqualTo("sid-1");
    }

    @Test
    @DisplayName("版本号不一致（改密/被踢后的旧 token）：拒绝认证")
    void versionMismatchRejectsAuthentication() throws Exception {
        when(tokenProvider.parseAccessToken(TOKEN)).thenReturn(payload(0));
        // 库里的版本已经涨到 1，说明 token 是改密前签发的
        when(userDetailsService.loadByUserId(1L)).thenReturn(cachedUser(1));

        filter.doFilter(requestWithToken(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("没有 Authorization 头时按匿名放行，不查库")
    void noTokenPassesThroughAsAnonymous() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
        verify(userDetailsService, never()).loadByUserId(any());
    }

    @Test
    @DisplayName("token 解析失败不抛异常，交给 EntryPoint 统一返回 40101")
    void invalidTokenDoesNotThrow() throws Exception {
        when(tokenProvider.parseAccessToken(TOKEN))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithToken(), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        // 关键：请求还要继续走下去，否则 401 会变成 500
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("账号被禁用时（回源抛 401）不建立登录态")
    void disabledUserDoesNotAuthenticate() throws Exception {
        when(tokenProvider.parseAccessToken(TOKEN)).thenReturn(payload(0));
        when(userDetailsService.loadByUserId(1L))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "账号已被禁用"));

        filter.doFilter(requestWithToken(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("旧版 token（无 ver 声明）解析出 null，同样视为失效")
    void legacyTokenWithoutVersionIsRejected() throws Exception {
        when(tokenProvider.parseAccessToken(TOKEN)).thenReturn(payload(null));
        when(userDetailsService.loadByUserId(1L)).thenReturn(cachedUser(0));

        filter.doFilter(requestWithToken(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
