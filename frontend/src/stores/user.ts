import { defineStore } from 'pinia'
import { fetchMenuTree, fetchUserInfo, logout as logoutApi } from '@/api/auth'
import type { MenuVO, UserInfo } from '@/api/types'

const TOKEN_KEY = 'hospital_token'
const REFRESH_KEY = 'hospital_refresh_token'

/**
 * 登录态。
 *
 * <p><b>权限码只作为前端「藏按钮」的依据，不是安全边界</b> —— 真正的拦截在后端
 * {@code @PreAuthorize}。前端隐藏只是让界面不出现点了必然报错的按钮。</p>
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    refreshToken: localStorage.getItem(REFRESH_KEY) || '',
    userInfo: null as UserInfo | null,
    menus: [] as MenuVO[],
    /** 菜单是否已加载，路由守卫据此判断要不要拉一次 */
    menusLoaded: false,
  }),
  getters: {
    isLogin: (s) => !!s.token,
    permissions: (s) => s.userInfo?.permissions ?? [],
    roles: (s) => s.userInfo?.roles ?? [],
    displayName: (s) => s.userInfo?.realName || s.userInfo?.username || '',
  },
  actions: {
    setTokens(access: string, refresh: string) {
      this.token = access
      this.refreshToken = refresh
      localStorage.setItem(TOKEN_KEY, access)
      localStorage.setItem(REFRESH_KEY, refresh)
    },

    async loadUserInfo() {
      if (!this.userInfo) {
        this.userInfo = await fetchUserInfo()
      }
      return this.userInfo
    },

    async loadMenus(force = false) {
      if (this.menusLoaded && !force) {
        return this.menus
      }
      this.menus = await fetchMenuTree()
      this.menusLoaded = true
      return this.menus
    },

    /** 判断是否有某个权限码；admin 直接放行（后端同样是这个口径） */
    hasPerm(code?: string | string[]): boolean {
      if (!code || (Array.isArray(code) && code.length === 0)) {
        return true
      }
      if (this.roles.includes('admin')) {
        return true
      }
      const list = Array.isArray(code) ? code : [code]
      return list.some((c) => this.permissions.includes(c))
    },

    async logout(silent = false) {
      if (!silent && this.token) {
        try {
          await logoutApi()
        } catch {
          // 登出失败也要清本地，否则用户卡在半登录态
        }
      }
      this.token = ''
      this.refreshToken = ''
      this.userInfo = null
      this.menus = []
      this.menusLoaded = false
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(REFRESH_KEY)
    },
  },
})
