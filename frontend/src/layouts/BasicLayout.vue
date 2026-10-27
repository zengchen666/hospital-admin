<template>
  <el-container class="hp-layout">
    <el-aside :width="collapsed ? '64px' : '210px'" class="hp-aside">
      <div class="hp-logo">
        <span v-if="!collapsed">医疗管理系统</span>
        <span v-else>医</span>
      </div>
      <el-menu
        :default-active="activePath"
        :collapse="collapsed"
        :collapse-transition="false"
        background-color="#1f2937"
        text-color="#cbd5e1"
        active-text-color="#ffffff"
        router
      >
        <template v-for="group in menuGroups" :key="group.key">
          <el-sub-menu v-if="group.children.length" :index="group.key">
            <template #title>
              <el-icon><component :is="group.icon" /></el-icon>
              <span>{{ group.title }}</span>
            </template>
            <el-menu-item v-for="item in group.children" :key="item.path" :index="item.path">
              {{ item.title }}
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="group.path">
            <el-icon><component :is="group.icon" /></el-icon>
            <template #title>{{ group.title }}</template>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="hp-header">
        <div class="hp-header-left">
          <el-icon class="hp-collapse" @click="collapsed = !collapsed">
            <Fold v-if="!collapsed" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentTitle">{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="hp-header-right">
          <el-tag size="small" type="info">{{ rolesText }}</el-tag>
          <el-dropdown @command="onCommand">
            <span class="hp-user">
              <el-icon><UserFilled /></el-icon>
              {{ displayName }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="hp-main">
        <router-view v-slot="{ Component }">
          <component :is="Component" />
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  UserFilled,
  ArrowDown,
  Fold,
  Expand,
  HomeFilled,
  Setting,
  User,
  Calendar,
  FirstAidKit,
  House,
  Goods,
  DataAnalysis,
  Money,
  Sunny,
  TrendCharts,
  Message,
  Lock,
  Menu as MenuIcon,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { resetDynamicRoutes } from '@/router'

/**
 * 主框架：侧边菜单 + 顶栏。
 *
 * <p>菜单完全来自后端 {@code GET /system/menu/tree} —— 前端不写死导航，
 * 权限变了（后端改角色授权）菜单立刻跟着变，不需要前端发版。</p>
 */

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const collapsed = ref(false)

/** 后端菜单没存图标类型，这里按路径前缀配一套默认图标 */
const iconByPath: Record<string, Component> = {
  '/dashboard': HomeFilled,
  '/system': Setting,
  '/patient': User,
  '/registration': Calendar,
  '/clinic': FirstAidKit,
  '/inpatient': House,
  '/pharmacy': Goods,
  '/exam': DataAnalysis,
  '/finance': Money,
  '/health': Sunny,
  '/report': TrendCharts,
  '/message': Message,
  '/security': Lock,
}

interface MenuItem {
  key: string
  title: string
  path: string
  icon: Component
  children: { title: string; path: string }[]
}

function resolveIcon(path: string): Component {
  if (!path) return MenuIcon
  for (const [prefix, icon] of Object.entries(iconByPath)) {
    if (path === prefix || path.startsWith(prefix + '/')) {
      return icon
    }
  }
  return MenuIcon
}

/** 把后端树整理成 el-menu 能直接渲染的两级结构 */
const menuGroups = computed<MenuItem[]>(() => {
  const top = store.menus.filter((m) => m.menuType === 1)
  const flat = store.menus.filter((m) => m.menuType === 2 && m.parentId === '0')
  const nodes = [...top, ...flat]

  return nodes.map((m) => {
    const children = (m.children ?? [])
      .filter((c) => c.menuType === 2)
      .map((c) => ({ title: c.menuName, path: c.path }))
    return {
      key: m.path || m.id,
      title: m.menuName,
      path: m.path,
      icon: resolveIcon(m.path),
      children,
    }
  })
})

const activePath = computed(() => route.path)
const currentTitle = computed(() => (route.meta.title as string) || '')
const displayName = computed(() => store.displayName)
const rolesText = computed(() => store.roles.join(' / ') || '未分配角色')

async function onCommand(cmd: string) {
  if (cmd === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
    } catch {
      return
    }
    await store.logout()
    resetDynamicRoutes()
    router.push('/login')
  }
}

onMounted(() => {
  // 刷新页面后 store 是空的，这里补一次；已加载时 loadMenus 会直接返回
  store.loadMenus().catch(() => undefined)
})
</script>

<style scoped>
.hp-layout {
  height: 100%;
}

.hp-aside {
  background: var(--hp-sidebar-bg);
  overflow-x: hidden;
  transition: width 0.2s;
}

.hp-logo {
  height: 56px;
  line-height: 56px;
  text-align: center;
  color: #fff;
  font-weight: 600;
  letter-spacing: 1px;
  background: #111827;
}

.hp-aside :deep(.el-menu) {
  border-right: none;
}

.hp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
}

.hp-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.hp-collapse {
  font-size: 20px;
  cursor: pointer;
  color: #64748b;
}

.hp-header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.hp-user {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: #334155;
}

.hp-main {
  padding: 0;
  background: #f5f7fa;
}
</style>
