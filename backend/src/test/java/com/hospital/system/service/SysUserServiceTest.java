package com.hospital.system.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.system.dto.UserSaveRequest;
import com.hospital.system.entity.SysUser;
import com.hospital.system.mapper.SysUserMapper;
import com.hospital.common.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户管理的业务规则与「踢人」时机。
 *
 * <p>这里覆盖几个真实踩过的坑：逻辑删除撞唯一索引、误删超管、
 * 以及「什么操作该踢人、什么不该」（改资料不该踢，禁用/改密必须踢）。</p>
 */
@ExtendWith(MockitoExtension.class)
class SysUserServiceTest {

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private LoginUserCacheService cacheService;

    private SysUserService service;

    @BeforeEach
    void setUp() {
        service = new SysUserService(userMapper, passwordEncoder, cacheService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long userId) {
        LoginUser loginUser = LoginUser.builder().userId(userId).username("operator").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    private SysUser fakeUser(Long id, String username) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setStatus(1);
        user.setRoleId(2001L);
        return user;
    }

    private UserSaveRequest request(String username, Integer status, Long roleId) {
        UserSaveRequest request = new UserSaveRequest();
        request.setUsername(username);
        request.setRealName("测试用户");
        request.setStatus(status);
        request.setRoleId(roleId);
        return request;
    }

    @Test
    @DisplayName("超级管理员账号不允许删除")
    void adminCannotBeDeleted() {
        when(userMapper.selectById(1L)).thenReturn(fakeUser(1L, "admin"));
        loginAs(9L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超级管理员");

        verify(userMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("不能删除当前登录账号（否则当场把自己锁死在系统外）")
    void cannotDeleteSelf() {
        when(userMapper.selectById(5L)).thenReturn(fakeUser(5L, "doc01"));
        loginAs(5L);

        assertThatThrownBy(() -> service.delete(5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前登录账号");
    }

    @Test
    @DisplayName("删除时给账号名加后缀让位唯一索引，否则同名账号永远无法重建")
    void deleteRenamesToFreeUniqueIndex() {
        when(userMapper.selectById(2L)).thenReturn(fakeUser(2L, "doc01"));
        loginAs(1L);

        service.delete(2L);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("doc01_del_2");
        assertThat(captor.getValue().getId()).isEqualTo(2L);

        verify(userMapper).deleteById(2L);
        verify(cacheService).evict(2L);
    }

    @Test
    @DisplayName("超长账号名删除后仍控制在 50 字符以内")
    void deleteKeepsUsernameWithinColumnLimit() {
        String longName = "a".repeat(50);
        when(userMapper.selectById(7L)).thenReturn(fakeUser(7L, longName));
        loginAs(1L);

        service.delete(7L);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        String renamed = captor.getValue().getUsername();
        assertThat(renamed).hasSizeLessThanOrEqualTo(50).endsWith("_del_7");
    }

    @Test
    @DisplayName("禁用用户时递增令牌版本号，让已签发的 token 立刻作废")
    void disableRevokesTokens() {
        when(userMapper.selectById(2L)).thenReturn(fakeUser(2L, "doc01"));
        loginAs(1L);

        service.changeStatus(2L, 0);

        verify(cacheService).evict(2L);
        verify(userMapper).bumpTokenVersion(2L, 1L);
    }

    @Test
    @DisplayName("启用用户不需要踢人（对方的旧 token 本来就已失效）")
    void enableDoesNotRevokeTokens() {
        when(userMapper.selectById(2L)).thenReturn(fakeUser(2L, "doc01"));
        loginAs(1L);

        service.changeStatus(2L, 1);

        verify(cacheService).evict(2L);
        verify(userMapper, never()).bumpTokenVersion(anyLong(), any());
    }

    @Test
    @DisplayName("重置密码必须踢人，否则改密挡不住已经登录的攻击者")
    void resetPasswordRevokesTokens() {
        when(userMapper.selectById(2L)).thenReturn(fakeUser(2L, "doc01"));
        when(passwordEncoder.encode("NewPass123")).thenReturn("{bcrypt}hash");

        service.resetPassword(2L, "NewPass123");

        verify(cacheService).evict(2L);
        verify(userMapper).bumpTokenVersion(2L, null);
    }

    @Test
    @DisplayName("超级管理员不允许被禁用")
    void adminCannotBeDisabled() {
        when(userMapper.selectById(1L)).thenReturn(fakeUser(1L, "admin"));
        loginAs(9L);

        assertThatThrownBy(() -> service.changeStatus(1L, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超级管理员");
    }

    @Test
    @DisplayName("不能禁用当前登录账号")
    void cannotDisableSelf() {
        when(userMapper.selectById(5L)).thenReturn(fakeUser(5L, "doc01"));
        loginAs(5L);

        assertThatThrownBy(() -> service.changeStatus(5L, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前登录账号");
    }

    @Test
    @DisplayName("只改姓名/手机号不该踢人（避免误伤正常使用）")
    void editingProfileDoesNotKickUser() {
        when(userMapper.selectById(2L)).thenReturn(fakeUser(2L, "doc01"));
        loginAs(1L);

        service.update(2L, request("doc01", 1, 2001L));

        verify(cacheService, never()).evict(anyLong());
        verify(userMapper, never()).bumpTokenVersion(anyLong(), any());
    }

    @Test
    @DisplayName("通过编辑接口把用户改成禁用状态时，同样要踢人")
    void disablingViaUpdateRevokesTokens() {
        when(userMapper.selectById(2L)).thenReturn(fakeUser(2L, "doc01"));
        loginAs(1L);

        service.update(2L, request("doc01", 0, 2001L));

        verify(cacheService).evict(2L);
        verify(userMapper).bumpTokenVersion(2L, 1L);
    }

    @Test
    @DisplayName("改角色只清缓存不踢人：下一次请求回源即可拿到新权限")
    void changingRoleOnlyEvictsCache() {
        when(userMapper.selectById(2L)).thenReturn(fakeUser(2L, "doc01"));
        loginAs(1L);

        service.update(2L, request("doc01", 1, 2003L));

        verify(cacheService).evict(2L);
        verify(userMapper, never()).bumpTokenVersion(anyLong(), any());
    }

    @Test
    @DisplayName("新建用户不传密码时使用默认密码，且状态默认启用")
    void createUsesDefaultPassword() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode("123456")).thenReturn("{bcrypt}default");

        service.create(request("newuser", null, 2003L));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("{bcrypt}default");
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("账号已存在时拒绝创建，提示明确而不是让数据库报 1062")
    void createRejectsDuplicateUsername() {
        when(userMapper.selectOne(any())).thenReturn(fakeUser(9L, "doc01"));

        assertThatThrownBy(() -> service.create(request("doc01", 1, 2003L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在");

        verify(userMapper, never()).insert(any(SysUser.class));
    }
}
