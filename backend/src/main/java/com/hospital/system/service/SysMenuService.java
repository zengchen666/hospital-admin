package com.hospital.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hospital.common.security.CurrentUser;
import com.hospital.common.security.LoginUser;
import com.hospital.system.dto.MenuVO;
import com.hospital.system.entity.SysMenu;
import com.hospital.system.mapper.SysMenuMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 菜单与动态路由。
 */
@Service
public class SysMenuService {

    private static final long ROOT_ID = 0L;
    private static final String ADMIN_ROLE = "admin";

    /** 菜单类型：1 目录 / 2 菜单 / 3 按钮 */
    private static final int MENU_TYPE_DIR = 1;

    private final SysMenuMapper menuMapper;

    public SysMenuService(SysMenuMapper menuMapper) {
        this.menuMapper = menuMapper;
    }

    /**
     * 当前用户可见的路由树（只含目录与菜单，按钮不参与路由）。
     */
    public List<MenuVO> currentUserMenuTree() {
        LoginUser user = CurrentUser.require();

        List<SysMenu> all = menuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                .eq(SysMenu::getStatus, 1)
                .in(SysMenu::getMenuType, 1, 2)
                .orderByAsc(SysMenu::getSort));

        boolean isAdmin = user.getRoleCodes().contains(ADMIN_ROLE);
        List<SysMenu> visible = all.stream()
                .filter(menu -> isAdmin || canSee(menu, user))
                .toList();

        return buildTree(visible, ROOT_ID);
    }

    /**
     * 全量菜单树（不过滤权限、含按钮）。
     *
     * <p>菜单管理页要看的是「系统里有哪些菜单、各自判什么权限码」，
     * 不是「我能看哪些」。因此这里<b>不</b>复用 {@link #currentUserMenuTree()}：
     * 那个方法会按当前用户的权限砍掉大半棵树，
     * 拿它当管理视图的话，越权检查做得越好，管理员看到的菜单就越少。</p>
     *
     * <p>按钮类型也一并返回（{@code menu_type = 3}），
     * 但前端默认折叠 —— 按钮数量是菜单的两倍多，平铺开根本没法看。</p>
     */
    public List<MenuVO> allMenuTree() {
        List<SysMenu> all = menuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                .orderByAsc(SysMenu::getParentId)
                .orderByAsc(SysMenu::getSort));
        return buildTree(all, ROOT_ID);
    }

    private boolean canSee(SysMenu menu, LoginUser user) {
        String perms = menu.getPerms();
        // 未配置权限码的菜单视为公共菜单
        return perms == null || perms.isBlank() || user.getPermissions().contains(perms);
    }

    private List<MenuVO> buildTree(List<SysMenu> menus, long parentId) {
        Map<Long, List<SysMenu>> grouped = menus.stream()
                .collect(Collectors.groupingBy(SysMenu::getParentId));

        List<MenuVO> result = new ArrayList<>();
        for (SysMenu menu : menus) {
            if (!Long.valueOf(parentId).equals(menu.getParentId())) {
                continue;
            }
            MenuVO vo = toVO(menu);
            vo.setChildren(buildTree(grouped.getOrDefault(menu.getId(), List.of()), menu.getId()));

            // 目录一个可见子项都没有就别下发：侧边栏会多出一个点开是空的分组，
            // 用户只会以为「这个模块坏了」，而真相只是他没权限或页面还没做。
            if (vo.getMenuType() != null && vo.getMenuType() == MENU_TYPE_DIR
                    && (vo.getChildren() == null || vo.getChildren().isEmpty())) {
                continue;
            }
            result.add(vo);
        }
        result.sort((a, b) -> Integer.compare(
                a.getSort() == null ? 0 : a.getSort(),
                b.getSort() == null ? 0 : b.getSort()));
        return result;
    }

    private MenuVO toVO(SysMenu menu) {
        MenuVO vo = new MenuVO();
        // 雪花 ID 转字符串，避免前端精度丢失
        vo.setId(String.valueOf(menu.getId()));
        vo.setParentId(String.valueOf(menu.getParentId()));
        vo.setMenuName(menu.getMenuName());
        vo.setMenuType(menu.getMenuType());
        vo.setPath(menu.getPath());
        vo.setComponent(menu.getComponent());
        vo.setPerms(menu.getPerms());
        vo.setIcon(menu.getIcon());
        vo.setSort(menu.getSort());
        return vo;
    }
}
