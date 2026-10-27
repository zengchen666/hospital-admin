package com.hospital.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.CurrentUser;
import com.hospital.common.security.JwtTokenProvider;
import com.hospital.common.security.LoginUser;
import com.hospital.system.dto.LoginRequest;
import com.hospital.system.dto.LoginResponse;
import com.hospital.system.entity.SysLoginLog;
import com.hospital.system.entity.SysUser;
import com.hospital.system.mapper.SysLoginLogMapper;
import com.hospital.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 认证服务：登录、刷新、登出、当前用户信息。
 *
 * <p><b>令牌生命周期</b>（P1-#7）：</p>
 * <ul>
 *   <li>登录：建会话（sid）→ 签发 access + refresh → 登记 refresh 的 jti</li>
 *   <li>刷新：会话在 → refresh 的 jti 未被用过 → 版本号匹配 → <b>轮换</b>（旧 jti 作废、发新令牌）</li>
 *   <li>登出：销毁会话，该设备再也无法刷新</li>
 *   <li>改密/禁用：库里的 version 递增，所有已签发的 access 立即失效（见 JwtAuthenticationFilter）</li>
 * </ul>
 */
@Slf4j
@Service
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysUserDetailsService userDetailsService;
    private final SysLoginLogMapper loginLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final LoginAttemptService loginAttemptService;
    private final AuthSessionService sessionService;

    public AuthService(SysUserMapper userMapper,
                       SysUserDetailsService userDetailsService,
                       SysLoginLogMapper loginLogMapper,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       LoginAttemptService loginAttemptService,
                       AuthSessionService sessionService) {
        this.userMapper = userMapper;
        this.userDetailsService = userDetailsService;
        this.loginLogMapper = loginLogMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.loginAttemptService = loginAttemptService;
        this.sessionService = sessionService;
    }

    public LoginResponse login(LoginRequest request, String ip, String userAgent) {
        // 先判锁定，再查库：被锁的账号不消耗数据库查询
        loginAttemptService.assertNotLocked(request.getUsername());

        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, request.getUsername()));

        // 账号不存在与密码错误返回同一错误码，防止账号枚举
        if (user == null) {
            loginAttemptService.recordFailure(request.getUsername());
            recordLoginLog(request.getUsername(), ip, userAgent, 0, "账号不存在");
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            recordLoginLog(request.getUsername(), ip, userAgent, 0, "账号已禁用");
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用，请联系管理员");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginAttemptService.recordFailure(request.getUsername());
            recordLoginLog(request.getUsername(), ip, userAgent, 0, "密码错误");
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        }

        // 登录成功清零失败计数
        loginAttemptService.reset(request.getUsername());

        // 强制回源：登录返回的角色/权限要用于渲染菜单，不能是缓存里的旧快照
        LoginUser loginUser = userDetailsService.reloadByUserId(user.getId());

        // 一次登录 = 一个会话；多设备登录各自独立，登出只影响当前设备
        String sessionId = sessionService.createSession(loginUser.getUserId());
        JwtTokenProvider.IssuedToken access = tokenProvider.createAccessToken(loginUser, sessionId);
        JwtTokenProvider.IssuedToken refresh = tokenProvider.createRefreshToken(loginUser, sessionId);
        sessionService.registerRefreshToken(refresh.jti(), sessionId);

        recordLoginLog(user.getUsername(), ip, userAgent, 1, "登录成功");
        log.info("用户登录成功 userId={} username={} ip={} sid={}", user.getId(), user.getUsername(), ip, sessionId);

        return LoginResponse.builder()
                .accessToken(access.token())
                .refreshToken(refresh.token())
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getAccessTokenExpireSeconds())
                .userInfo(toUserInfo(loginUser))
                .build();
    }

    /**
     * 刷新令牌：refreshToken 换新的 access + refresh（轮换，旧 token 立即作废）。
     *
     * <p>四道校验缺一不可，顺序也有讲究：</p>
     * <ol>
     *   <li><b>会话存在</b> —— 登出后的 refresh 即使签名有效也不能用</li>
     *   <li><b>jti 一次性</b> —— 用 {@code DEL} 的返回值判定，并发重放只有一个能成功</li>
     *   <li><b>用户状态</b> —— 被禁用/删除的账号不能靠 refresh 续命</li>
     *   <li><b>版本号</b> —— 改密后旧 refresh 一并作废，避免「改了密码但攻击者仍能刷新」</li>
     * </ol>
     */
    public LoginResponse refresh(String refreshToken) {
        JwtTokenProvider.TokenPayload payload = tokenProvider.parseRefreshToken(refreshToken);

        if (!sessionService.isSessionActive(payload.sessionId())) {
            log.warn("刷新被拒：会话不存在或已登出 userId={}", payload.userId());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
        if (!sessionService.consumeRefreshToken(payload.jti())) {
            log.warn("刷新被拒：refresh 令牌已被使用或不存在 userId={} jti={}", payload.userId(), payload.jti());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }

        // 用户被禁用/删除会在这里抛 401
        LoginUser loginUser = userDetailsService.reloadByUserId(payload.userId());
        if (!Objects.equals(payload.version(), loginUser.getTokenVersion())) {
            log.warn("刷新被拒：用户版本已变更（改密或被踢下线）userId={}", payload.userId());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }

        // 轮换：沿用同一会话，换一对新令牌
        String sessionId = payload.sessionId();
        JwtTokenProvider.IssuedToken access = tokenProvider.createAccessToken(loginUser, sessionId);
        JwtTokenProvider.IssuedToken newRefresh = tokenProvider.createRefreshToken(loginUser, sessionId);
        sessionService.registerRefreshToken(newRefresh.jti(), sessionId);

        return LoginResponse.builder()
                .accessToken(access.token())
                .refreshToken(newRefresh.token())
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getAccessTokenExpireSeconds())
                .userInfo(toUserInfo(loginUser))
                .build();
    }

    public LoginResponse.UserInfoVO currentUserInfo() {
        return toUserInfo(CurrentUser.require());
    }

    /**
     * 登出：销毁当前会话。
     *
     * <p>access token 本身不拉黑（JWT 无状态，2 小时后自然过期），
     * 但该会话的 refresh 立刻失效，攻击者拿不到新令牌；
     * 需要「立即踢掉已签发 access」的场景走改密/禁用（版本号递增）。</p>
     */
    public void logout() {
        LoginUser user = CurrentUser.orNull();
        if (user == null) {
            return;
        }
        sessionService.destroySession(user.getSessionId());
        log.info("用户登出 userId={} username={} sid={}", user.getUserId(), user.getUsername(), user.getSessionId());
    }

    private LoginResponse.UserInfoVO toUserInfo(LoginUser loginUser) {
        List<String> permissions = loginUser.getPermissions().stream().sorted().toList();
        return LoginResponse.UserInfoVO.builder()
                .userId(String.valueOf(loginUser.getUserId()))
                .username(loginUser.getUsername())
                .realName(loginUser.getRealName())
                .deptId(loginUser.getDeptId() == null ? null : String.valueOf(loginUser.getDeptId()))
                .roles(loginUser.getRoleCodes())
                .permissions(permissions)
                .build();
    }

    private void recordLoginLog(String username, String ip, String userAgent, int status, String msg) {
        SysLoginLog logEntity = new SysLoginLog();
        logEntity.setUsername(username);
        logEntity.setIp(ip);
        logEntity.setBrowser(parseBrowser(userAgent));
        logEntity.setOs(parseOs(userAgent));
        logEntity.setStatus(status);
        logEntity.setMsg(msg);
        logEntity.setLoginTime(LocalDateTime.now());
        try {
            loginLogMapper.insert(logEntity);
        } catch (Exception e) {
            // 审计写入失败不能影响登录主流程
            log.warn("登录日志写入失败 username={}", username, e);
        }
    }

    private String parseBrowser(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        if (userAgent.contains("Edg/")) {
            return "Edge";
        }
        if (userAgent.contains("Chrome/")) {
            return "Chrome";
        }
        if (userAgent.contains("Firefox/")) {
            return "Firefox";
        }
        if (userAgent.contains("Safari/")) {
            return "Safari";
        }
        if (userAgent.startsWith("curl/") || userAgent.startsWith("Postman")) {
            return userAgent.split("/")[0];
        }
        return truncate(userAgent, 100);
    }

    private String parseOs(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        if (userAgent.contains("Windows")) {
            return "Windows";
        }
        if (userAgent.contains("Mac OS X")) {
            return "macOS";
        }
        if (userAgent.contains("Android")) {
            return "Android";
        }
        if (userAgent.contains("Linux")) {
            return "Linux";
        }
        return "Other";
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
