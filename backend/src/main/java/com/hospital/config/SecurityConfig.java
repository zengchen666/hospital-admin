package com.hospital.config;

import com.hospital.common.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 安全配置：全站无状态（JWT），仅白名单接口放行。
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final HospitalProperties properties;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(HospitalProperties properties, JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.properties = properties;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        List<String> ignoreUrls = properties.getSecurity().getIgnoreUrls();

        http
                // 前后端分离 + Token 鉴权，CSRF 无意义，必须关闭否则所有 POST 403
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(registry -> {
                    if (!ignoreUrls.isEmpty()) {
                        registry.requestMatchers(ignoreUrls.toArray(String[]::new)).permitAll();
                    }
                    // 预检请求必须放行，否则浏览器端跨域直接失败
                    registry.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    registry.anyRequest().authenticated();
                })
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // 未登录/无权限返回 JSON，而不是重定向或容器默认页面
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                SecurityResponseWriter.writeUnauthorized(response))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                SecurityResponseWriter.write(response, 403, 40301, "无操作权限")));

        return http.build();
    }

    /**
     * 密码加密器：DelegatingPasswordEncoder。
     *
     * <p>新密码按 {@code {bcrypt}} 存储；同时兼容初始化数据里的 {@code {noop}} 明文，
     * 便于「先跑起来、再逐步迁移到 BCrypt」的过渡期，避免一上来就卡登录。</p>
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = properties.getCors().getAllowedOrigins();
        configuration.setAllowedOrigins(origins.isEmpty() ? List.of() : origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization", "Content-Disposition", "X-Request-Id"));
        configuration.setAllowCredentials(properties.getCors().isAllowCredentials());
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
