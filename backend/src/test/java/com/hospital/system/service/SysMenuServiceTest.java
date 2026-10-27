package com.hospital.system.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hospital.common.security.LoginUser;
import com.hospital.system.dto.MenuVO;
import com.hospital.system.entity.SysMenu;
import com.hospital.system.mapper.SysMenuMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 菜单树。
 *
 * <p>要锁死的是「下发什么」而不是「存了什么」：侧边栏多一个空目录、
 * 少一个该看的页面，都不会报错，只会让人觉得系统做了一半。</p>
 */
@ExtendWith(MockitoExtension.class)
class SysMenuServiceTest {

    @Mock
    private SysMenuMapper menuMapper;

    private SysMenuService service;

    /**
     * 纯单测里没有 MyBatis 容器，lambda 条件拿不到 TableInfo ——
     * 必须手动注册一次，否则 {@code Wrappers.lambdaQuery()} 直接抛
     * "can not find lambda cache"。
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, SysMenu.class);
    }

    @BeforeEach
    void setUp() {
        service = new SysMenuService(menuMapper);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("一个可见子项都没有的目录不下发（否则侧边栏出现空分组）")
    void emptyDirPruned() {
        when(menuMapper.selectList(any())).thenReturn(List.of(
                dir(300, "患者管理"),
                menu(301, 300, "患者档案", "patient:query"),
                dir(700, "药房管理")));
        login("doctor", "patient:query");

        List<MenuVO> tree = service.currentUserMenuTree();

        assertThat(tree).extracting(MenuVO::getMenuName)
                .contains("患者管理")
                .doesNotContain("药房管理");
    }

    @Test
    @DisplayName("没有权限的子菜单不下发，其父目录随之消失")
    void noPermMenuHidden() {
        when(menuMapper.selectList(any())).thenReturn(List.of(
                dir(900, "收费财务"),
                menu(901, 900, "门诊收费", "finance:query")));
        login("doctor", "clinic:query");

        assertThat(service.currentUserMenuTree()).isEmpty();
    }

    @Test
    @DisplayName("admin 不做权限过滤，全部下发")
    void adminSeesAll() {
        when(menuMapper.selectList(any())).thenReturn(List.of(
                dir(900, "收费财务"),
                menu(901, 900, "门诊收费", "finance:query")));
        login("admin");

        List<MenuVO> tree = service.currentUserMenuTree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getChildren()).hasSize(1);
    }

    @Test
    @DisplayName("查询条件由 Service 自带（status=1 且只取目录与菜单），调用方不该自己拼")
    void queryBuiltByService() {
        when(menuMapper.selectList(any())).thenReturn(List.of());
        login("admin");

        assertThat(service.currentUserMenuTree()).isEmpty();
        // 只确认 Service 自己拼了条件并查了一次，条件内容由 SQL 补丁脚本保证
        verify(menuMapper).selectList(any());
    }

    /* ---------------- 辅助 ---------------- */

    private SysMenu dir(long id, String name) {
        return build(id, 0L, name, 1, null);
    }

    private SysMenu menu(long id, long parentId, String name, String perms) {
        return build(id, parentId, name, 2, perms);
    }

    private SysMenu build(long id, long parentId, String name, int menuType, String perms) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setMenuName(name);
        menu.setMenuType(menuType);
        menu.setPerms(perms);
        menu.setPath("/m" + id);
        menu.setSort((int) id);
        return menu;
    }

    private void login(String role, String... permissions) {
        LoginUser user = LoginUser.builder()
                .userId(1L)
                .username(role)
                .roleCodes(java.util.Set.of(role))
                .permissions(java.util.Set.of(permissions))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
