package com.hospital.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.LoginUser;
import com.hospital.system.entity.SysUser;
import com.hospital.system.mapper.SysUserMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;

/**
 * 用户认证信息加载。
 *
 * <p><b>两条路径的成本不同，不要混用：</b></p>
 * <ul>
 *   <li>{@link #loadUserByUsername}：仅登录时调用一次，<b>直查库</b>，
 *       必须拿到真实密码做 bcrypt 校验，绝不能走缓存。</li>
 *   <li>{@link #loadByUserId}：每个带 token 的请求都调用，
 *       <b>走 {@link LoginUserCacheService}</b>，命中时 0 次查库（原为 3 次）。</li>
 * </ul>
 *
 * <p>权限收回的即时性由缓存层保证：用户维度的变更精确删 key，
 * 角色/菜单维度的变更递增全局权限版本号，详见 {@link LoginUserCacheService}。</p>
 */
@Service
public class SysUserDetailsService implements UserDetailsService {

    private final SysUserMapper userMapper;
    private final LoginUserCacheService cacheService;

    public SysUserDetailsService(SysUserMapper userMapper, LoginUserCacheService cacheService) {
        this.userMapper = userMapper;
        this.cacheService = cacheService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username));
        if (user == null) {
            // 统一抛凭证错误，避免通过报错内容枚举账号是否存在
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        }
        return toLoginUser(user);
    }

    /** 每个请求的鉴权入口：缓存优先 */
    public LoginUser loadByUserId(Long userId) {
        return cacheService.get(userId, this::loadFromDb);
    }

    /**
     * 强制读最新权限：登录成功、刷新令牌时使用。
     *
     * <p>这两处的返回结果会直接渲染前端菜单与按钮，必须是最新的。</p>
     */
    public LoginUser reloadByUserId(Long userId) {
        return cacheService.reload(userId, this::loadFromDb);
    }

    /** 回源：账号不存在或已禁用直接拒绝，结果不会进缓存 */
    private LoginUser loadFromDb(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号已被禁用");
        }
        return toLoginUser(user);
    }

    private LoginUser toLoginUser(SysUser user) {
        return LoginUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .password(user.getPassword())
                .realName(user.getRealName())
                .deptId(user.getDeptId())
                .tokenVersion(user.getTokenVersion())
                .roleCodes(new LinkedHashSet<>(userMapper.selectRoleCodesByUserId(user.getId())))
                .permissions(new LinkedHashSet<>(userMapper.selectPermCodesByUserId(user.getId())))
                .build();
    }
}
