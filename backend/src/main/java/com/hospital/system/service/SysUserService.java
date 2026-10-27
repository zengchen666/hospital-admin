package com.hospital.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.CurrentUser;
import com.hospital.system.dto.UserOptionVO;
import com.hospital.system.dto.UserQuery;
import com.hospital.system.dto.UserSaveRequest;
import com.hospital.system.dto.UserVO;
import com.hospital.system.entity.SysUser;
import com.hospital.system.mapper.SysUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 用户管理服务。
 *
 * <p><b>每个写操作结束都要处理权限缓存</b>，否则会出现「已禁用但还能访问」
 * 「改了角色还是旧权限」这类问题。规则：</p>
 * <ul>
 *   <li>只影响当前用户 → {@code cacheService.evict(id)} 精确失效</li>
 *   <li>影响一批用户（角色授权、菜单权限） → {@code cacheService.bumpPermissionVersion()}</li>
 * </ul>
 */
@Service
public class SysUserService {

    private static final String DEFAULT_PASSWORD = "123456";
    private static final String ADMIN_USERNAME = "admin";

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final LoginUserCacheService cacheService;

    public SysUserService(SysUserMapper userMapper, PasswordEncoder passwordEncoder,
                          LoginUserCacheService cacheService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.cacheService = cacheService;
    }

    public PageResult<UserVO> page(UserQuery query) {
        LambdaQueryWrapper<SysUser> wrapper = Wrappers.<SysUser>lambdaQuery()
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                .eq(query.getDeptId() != null, SysUser::getDeptId, query.getDeptId())
                .eq(query.getRoleId() != null, SysUser::getRoleId, query.getRoleId());

        String keyword = query.getKeyword();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getRealName, keyword));
        }
        wrapper.orderByDesc(SysUser::getCreateTime);

        Page<SysUser> page = userMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return PageResult.of(page, this::toVO);
    }

    public UserVO detail(Long id) {
        return toVO(getExisting(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(UserSaveRequest request) {
        assertUsernameAvailable(request.getUsername(), null);

        SysUser entity = new SysUser();
        copyBasic(request, entity);
        entity.setUsername(request.getUsername());
        String rawPassword = (request.getPassword() == null || request.getPassword().isBlank())
                ? DEFAULT_PASSWORD : request.getPassword();
        // DelegatingPasswordEncoder 会存成 {bcrypt}$2a$10$...，比明文/MD5 安全
        entity.setPassword(passwordEncoder.encode(rawPassword));
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());

        userMapper.insert(entity);
        // 新用户还没有任何缓存，无需失效
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UserSaveRequest request) {
        SysUser entity = getExisting(id);
        assertUsernameAvailable(request.getUsername(), id);

        boolean roleChanged = !Objects.equals(entity.getRoleId(), request.getRoleId());
        boolean statusChanged = request.getStatus() != null
                && !Objects.equals(entity.getStatus(), request.getStatus());

        entity.setUsername(request.getUsername());
        copyBasic(request, entity);
        if (request.getStatus() != null) {
            // 不允许把自己禁用，否则当前会话立刻失效且无法自救
            if (id.equals(CurrentUser.idOrNull()) && request.getStatus() == 0) {
                throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "不能禁用当前登录账号");
            }
            entity.setStatus(request.getStatus());
        }
        userMapper.updateById(entity);

        // 角色变更或启停变更：必须立刻让该用户的鉴权缓存作废，
        // 否则被禁用的人还能拿旧 token 继续访问最长 5 分钟（TTL 上限）
        if (roleChanged || statusChanged) {
            cacheService.evict(id);
        }
        // 禁用是安全事件，额外递增版本号直接作废已签发的 token；
        // 改姓名/手机号这类普通资料变更则不踢人，避免误伤正常使用
        if (statusChanged && Integer.valueOf(0).equals(request.getStatus())) {
            revokeTokens(id);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysUser entity = getExisting(id);
        if (ADMIN_USERNAME.equals(entity.getUsername())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "超级管理员账号不允许删除");
        }
        if (id.equals(CurrentUser.idOrNull())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "不能删除当前登录账号");
        }

        // 关键：逻辑删除的行仍然占用 uk_username 唯一索引（已实测 ERROR 1062）。
        // 若只置 deleted = 1，同名账号将永远无法重建，且业务层查重带 deleted = 0 会误判可用，
        // 最终让用户看到「服务器开小差」。故删除时给账号名加后缀让位。
        String suffix = "_del_" + id;
        String original = entity.getUsername();
        int keep = Math.max(0, 50 - suffix.length());
        String renamed = (original.length() > keep ? original.substring(0, keep) : original) + suffix;

        SysUser update = new SysUser();
        update.setId(id);
        update.setUsername(renamed);
        userMapper.updateById(update);

        // 逻辑删除：@TableLogic 自动改写为 UPDATE ... SET deleted = 1
        userMapper.deleteById(id);

        // 删完再清缓存：已删用户的 token 不应再能通过鉴权
        cacheService.evict(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        SysUser entity = getExisting(id);
        if (ADMIN_USERNAME.equals(entity.getUsername()) && status != null && status == 0) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "超级管理员账号不允许禁用");
        }
        if (id.equals(CurrentUser.idOrNull()) && status != null && status == 0) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "不能禁用当前登录账号");
        }
        SysUser update = new SysUser();
        update.setId(id);
        update.setStatus(status);
        userMapper.updateById(update);

        // 禁用要立刻生效：清缓存让下一次鉴权回源拿到 status=0，
        // 递增版本号让手里已签发的 token 一并作废（否则对方还能用到 access 过期）
        cacheService.evict(id);
        if (status != null && status == 0) {
            revokeTokens(id);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String newPassword) {
        getExisting(id);
        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(update);

        // 改密必须踢人：否则「密码泄露后改密」这个动作挡不住已登录的攻击者，
        // 对方凭旧 access token 还能继续操作到过期
        cacheService.evict(id);
        revokeTokens(id);
    }

    private void copyBasic(UserSaveRequest request, SysUser entity) {
        entity.setRealName(request.getRealName());
        entity.setGender(request.getGender());
        entity.setPhone(request.getPhone());
        entity.setEmail(request.getEmail());
        entity.setDeptId(request.getDeptId());
        entity.setRoleId(request.getRoleId());
    }

    /**
     * 强制该用户已签发的全部 token 失效。
     *
     * <p>递增 {@code token_version} 后，鉴权过滤器比对 token 里的 {@code ver}
     * 与库里的 {@code token_version} 就不一致，直接返回 401。
     * 对所有设备同时生效 —— 这正是「改密踢掉所有设备」的语义。</p>
     */
    private void revokeTokens(Long userId) {
        userMapper.bumpTokenVersion(userId, CurrentUser.idOrNull());
    }

    private SysUser getExisting(Long id) {
        SysUser entity = userMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "用户不存在或已被删除");
        }
        return entity;
    }

    /**
     * 查重：一次查询完成。
     *
     * <p>原实现用 selectCount + 再查一次当前用户名，两次往返且逻辑绕。
     * 注意此查询受 @TableLogic 影响只看 {@code deleted = 0}，
     * 已逻辑删除的行由 {@link #delete} 的改名策略让位，避免撞唯一索引。</p>
     */
    private void assertUsernameAvailable(String username, Long excludeId) {
        SysUser existing = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username)
                .last("LIMIT 1"));
        if (existing != null && !existing.getId().equals(excludeId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "账号 " + username + " 已存在");
        }
    }

    private UserVO toVO(SysUser entity) {
        return UserVO.builder()
                .id(String.valueOf(entity.getId()))
                .username(entity.getUsername())
                .realName(entity.getRealName())
                .gender(entity.getGender())
                .phone(maskPhone(entity.getPhone()))
                .email(entity.getEmail())
                .deptId(entity.getDeptId() == null ? null : String.valueOf(entity.getDeptId()))
                .roleId(entity.getRoleId() == null ? null : String.valueOf(entity.getRoleId()))
                .status(entity.getStatus())
                .createTime(entity.getCreateTime())
                .build();
    }

    /**
     * 用户下拉选项。
     *
     * <p>{@code roleCode} 为空时返回全部启用用户。排班选医生、医嘱选执行人
     * 都走这里 —— 各自去查用户分页再自己过滤，迟早出现口径不一致。</p>
     */
    public List<UserOptionVO> options(String roleCode) {
        LambdaQueryWrapper<SysUser> wrapper = Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getStatus, 1);
        if (StringUtils.hasText(roleCode)) {
            List<Long> ids = userMapper.selectIdsByRoleCode(roleCode);
            if (ids.isEmpty()) {
                return List.of();
            }
            wrapper.in(SysUser::getId, ids);
        }
        wrapper.orderByAsc(SysUser::getId);
        return userMapper.selectList(wrapper).stream().map(this::toOption).toList();
    }

    private UserOptionVO toOption(SysUser entity) {
        return UserOptionVO.builder()
                .id(String.valueOf(entity.getId()))
                .realName(entity.getRealName())
                .deptId(entity.getDeptId() == null ? null : String.valueOf(entity.getDeptId()))
                .build();
    }

    /** 手机号脱敏：138****0000 */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
