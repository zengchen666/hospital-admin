import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import type { MenuVO } from '@/api/types'

/**
 * 前端路由。
 *
 * <p>用 hash 模式而不是 history：静态部署时 history 需要服务端配 fallback，
 * 否则刷新 /patient/list 直接 404。这是内部系统，hash 更省事也更稳。</p>
 */

const BasicLayout = () => import('@/layouts/BasicLayout.vue')
const Placeholder = () => import('@/views/placeholder/index.vue')

/**
 * 后端菜单 component 字段 → 真实视图的映射。
 *
 * <p>只登记<b>已经实现</b>的页面；未实现的落到 Placeholder。
 * 这样做的好处：菜单是后端下发的（权限驱动的），但组件是前端自己的事，
 * 后端不会因为前端还没写某个页面就得改数据库。</p>
 */
const viewMap: Record<string, () => Promise<unknown>> = {
  'dashboard/index': () => import('@/views/dashboard/index.vue'),
  'patient/list/index': () => import('@/views/patient/index.vue'),
  'system/user/index': () => import('@/views/system/user/index.vue'),
  'message/list/index': () => import('@/views/message/list/index.vue'),
  'message/notice/index': () => import('@/views/message/notice/index.vue'),
  'message/sms/index': () => import('@/views/message/sms/index.vue'),
  'message/warning/index': () => import('@/views/message/warning/index.vue'),
  'registration/add/index': () => import('@/views/registration/add/index.vue'),
  'registration/sched/index': () => import('@/views/registration/sched/index.vue'),
  'registration/queue/index': () => import('@/views/registration/queue/index.vue'),
  'clinic/workbench/index': () => import('@/views/clinic/workbench/index.vue'),
  'clinic/presc/index': () => import('@/views/clinic/presc/index.vue'),
  'inpatient/bed/index': () => import('@/views/inpatient/bed/index.vue'),
  'inpatient/admission/index': () => import('@/views/inpatient/admission/index.vue'),
  'inpatient/order/index': () => import('@/views/inpatient/order/index.vue'),
  'inpatient/nursing/index': () => import('@/views/inpatient/nursing/index.vue'),
  'pharmacy/drug/index': () => import('@/views/pharmacy/drug/index.vue'),
  'pharmacy/stock/index': () => import('@/views/pharmacy/stock/index.vue'),
  'pharmacy/purchase/index': () => import('@/views/pharmacy/purchase/index.vue'),
  'pharmacy/dispense/index': () => import('@/views/pharmacy/dispense/index.vue'),
  'exam/apply/index': () => import('@/views/exam/apply/index.vue'),
  'exam/workbench/index': () => import('@/views/exam/workbench/index.vue'),
  'exam/report/index': () => import('@/views/exam/report/index.vue'),
  'finance/charge/index': () => import('@/views/finance/charge/index.vue'),
  'finance/refund/index': () => import('@/views/finance/refund/index.vue'),
  'finance/invoice/index': () => import('@/views/finance/invoice/index.vue'),
  'finance/settle/index': () => import('@/views/finance/settle/index.vue'),
  'finance/treat/index': () => import('@/views/finance/treat/index.vue'),
  'health/record/index': () => import('@/views/health/record/index.vue'),
  'health/vaccine/index': () => import('@/views/health/vaccine/index.vue'),
  'health/infectious/index': () => import('@/views/health/infectious/index.vue'),
  'health/checkup/index': () => import('@/views/health/checkup/index.vue'),
  'report/outpatient/index': () => import('@/views/report/outpatient/index.vue'),
  'report/inpatient/index': () => import('@/views/report/inpatient/index.vue'),
  'report/income/index': () => import('@/views/report/income/index.vue'),
  'report/drug/index': () => import('@/views/report/drug/index.vue'),
  'report/patient/index': () => import('@/views/report/patient/index.vue'),
  'report/custom/index': () => import('@/views/report/custom/index.vue'),
  'system/role/index': () => import('@/views/system/role/index.vue'),
  'system/menu/index': () => import('@/views/system/menu/index.vue'),
  'system/oplog/index': () => import('@/views/system/oplog/index.vue'),
  'system/dict/index': () => import('@/views/system/dict/index.vue'),
  'system/config/index': () => import('@/views/system/config/index.vue'),
  'security/backup/index': () => import('@/views/security/backup/index.vue'),
  'security/config/index': () => import('@/views/security/config/index.vue'),
}

/** 静态路由：不依赖权限的部分 */
export const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { title: '无权限', public: true },
  },
  {
    path: '/',
    component: BasicLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页', icon: 'HomeFilled' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    ...constantRoutes,
    { path: '/:pathMatch(.*)*', name: 'NotFound', component: () => import('@/views/error/404.vue') },
  ],
})

/** 把后端菜单树转成路由记录（有子菜单时只作为分组，父级不挂组件） */
function toRoute(menu: MenuVO, parentPath = ''): RouteRecordRaw {
  const fullPath = menu.path?.startsWith('/') ? menu.path : `${parentPath}/${menu.path}`
  const meta = { title: menu.menuName, icon: menu.icon, perms: menu.perms, menuId: menu.id }

  if (menu.children?.length) {
    return {
      path: fullPath,
      name: `M${menu.id}`,
      meta,
      children: menu.children.map((c) => toRoute(c, fullPath)),
    }
  }
  return {
    path: fullPath,
    name: `M${menu.id}`,
    meta,
    component: viewMap[menu.component] ?? Placeholder,
  }
}

/** 只在首次进入时挂载一次动态路由 */
let dynamicAdded = false

export async function buildDynamicRoutes(): Promise<RouteRecordRaw[]> {
  const store = useUserStore()
  const menus = await store.loadMenus()
  const routes: RouteRecordRaw[] = []
  for (const menu of menus) {
    // 顶层：有子菜单的是目录，直接作为布局的一级子路由
    if (menu.children?.length) {
      for (const child of menu.children) {
        const r = toRoute(child, menu.path)
        if (menu.path && !r.path.startsWith(menu.path)) {
          r.path = `${menu.path}${child.path}`
        }
        routes.push(r)
      }
    } else if (menu.path !== '/dashboard') {
      routes.push(toRoute(menu))
    }
  }
  return routes
}

router.beforeEach(async (to) => {
  const store = useUserStore()

  if (to.meta.public) {
    return true
  }
  if (!store.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  try {
    await store.loadUserInfo()
    if (!dynamicAdded) {
      const routes = await buildDynamicRoutes()
      for (const r of routes) {
        router.addRoute('/', r)
      }
      dynamicAdded = true
      // 追加路由后必须重新导航一次，否则首次进入会落到 404
      return { ...to, replace: true }
    }
  } catch {
    ElMessage.error('登录信息已失效，请重新登录')
    await store.logout(true)
    return { path: '/login' }
  }
  return true
})

/** 退出登录后重置，否则换账号进来会带着上一个人的路由 */
export function resetDynamicRoutes() {
  dynamicAdded = false
}

export default router
