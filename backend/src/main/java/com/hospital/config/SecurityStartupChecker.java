package com.hospital.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hospital.system.entity.SysUser;
import com.hospital.system.mapper.SysUserMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * 启动期安全检查：阻止带着明文密码上生产。
 *
 * <p>初始化数据里的密码是 {@code {noop}123456}，DelegatingPasswordEncoder 能直接匹配成功 ——
 * 这是为了方便本地跑通，但生产环境绝不允许。本检查在生产 profile 下直接拒绝启动，
 * 开发环境只告警，避免把本地开发卡死。</p>
 *
 * <p>用 {@link PostConstruct} 而不是 {@code ApplicationRunner}：后者在 Tomcat 绑定端口之后才执行，
 * 会出现「日志显示 Started、端口已对外、随即进程退出」的时间窗，探针可能误判为健康。
 * PostConstruct 在容器刷新阶段执行，端口根本不会打开。</p>
 */
@Slf4j
@Component
public class SecurityStartupChecker {

    private static final String NOOP_PREFIX = "{noop}";

    private final SysUserMapper userMapper;
    private final Environment environment;

    public SecurityStartupChecker(SysUserMapper userMapper, Environment environment) {
        this.userMapper = userMapper;
        this.environment = environment;
    }

    @PostConstruct
    public void check() {
        Long plainCount = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .likeRight(SysUser::getPassword, NOOP_PREFIX));

        if (plainCount == null || plainCount == 0) {
            log.info("安全检查通过：未发现使用明文密码的账号");
            return;
        }

        String message = "发现 " + plainCount + " 个使用 {noop} 明文密码的账号";
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            // 生产必须 fail-fast：带着明文密码跑起来比启动失败危险得多
            throw new IllegalStateException(message + "，生产环境禁止启动。请先重置这些账号的密码。");
        }
        log.warn("{}，仅限开发环境。上线前必须通过「重置密码」接口改为 BCrypt。", message);
    }
}
