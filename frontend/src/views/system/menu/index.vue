<template>
  <div class="hp-page">
    <h3 class="hp-page-title">菜单管理</h3>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
      title="展示的是全量菜单结构（含停用与按钮）"
      description="与左侧边栏不同：边栏只渲染「当前用户有权限」的菜单。这里要回答的是「系统里定义了哪些菜单、各自判什么权限码」，所以不做权限过滤。"
    />

    <div class="hp-toolbar">
      <el-input
        v-model="keyword"
        placeholder="按菜单名 / 路径 / 权限码搜索"
        clearable
        style="width: 260px"
        @input="onSearch"
      />
      <el-checkbox v-model="showButtons" label="显示按钮级菜单" @change="onSearch" />
      <span class="spacer" />
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      <el-button :icon="Sort" @click="toggleAll">
        {{ allExpanded ? '全部收起' : '全部展开' }}
      </el-button>
    </div>

    <el-table
      :key="tableKey"
      v-loading="loading"
      :data="rows"
      row-key="id"
      border
      stripe
      :default-expand-all="allExpanded"
      :tree-props="{ children: 'children' }"
    >
      <el-table-column prop="menuName" label="菜单名称" min-width="180" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag :type="typeTag(row.menuType)" size="small">{{ typeLabel(row.menuType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="path" label="路由路径" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.path || '—' }}</template>
      </el-table-column>
      <el-table-column prop="component" label="前端组件" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.component || '—' }}</template>
      </el-table-column>
      <el-table-column label="判权码" min-width="160">
        <template #default="{ row }">
          <el-tag v-if="row.perms" type="warning" size="small" effect="plain">{{ row.perms }}</el-tag>
          <span v-else class="hp-muted">无（公共）</span>
        </template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="70" align="right" />
      <el-table-column label="状态" width="90">
        <!-- 全量树接口不带 status：能出现在树里即已启用 -->
        <template #default>
          <el-tag type="success" size="small">启用</el-tag>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">没有匹配的菜单</span>
      </template>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh, Sort } from '@element-plus/icons-vue'
import { allMenus, type MenuNode } from '@/api/system'

/**
 * 菜单管理（只读树形查看）。
 *
 * <p>按钮级菜单（menu_type=3）默认隐藏：数量是菜单的两倍多，
 * 平铺开之后真正想看的一级目录会被挤到看不见的地方。</p>
 */

const trees = ref<MenuNode[]>([])
const loading = ref(false)
const keyword = ref('')
const showButtons = ref(false)
const allExpanded = ref(true)
/** default-expand-all 只在挂载时生效，切换展开状态必须换 key 重建表格 */
const tableKey = ref(0)

const rows = computed(() => filterTree(trees.value))

function filterTree(nodes: MenuNode[]): MenuNode[] {
  const kw = keyword.value.trim().toLowerCase()
  const result: MenuNode[] = []
  for (const node of nodes) {
    const children = filterTree(node.children || [])
    const selfMatch =
      !kw ||
      (node.menuName || '').toLowerCase().includes(kw) ||
      (node.path || '').toLowerCase().includes(kw) ||
      (node.perms || '').toLowerCase().includes(kw)
    // 父节点不匹配但子节点匹配时保留父节点，否则树会断掉
    if (children.length || selfMatch) {
      if (node.menuType !== 3 || showButtons.value || kw) {
        result.push({ ...node, children })
      }
    }
  }
  return result
}

function typeLabel(t: number): string {
  return (['目录', '菜单', '按钮'] as const)[t - 1] ?? '未知'
}

function typeTag(t: number): 'primary' | 'success' | 'info' {
  return (['primary', 'success', 'info'] as const)[t - 1] ?? 'info'
}

function onSearch() {
  /* 过滤走 computed，无需额外请求 */
}

function toggleAll() {
  allExpanded.value = !allExpanded.value
  tableKey.value += 1
}

async function load() {
  loading.value = true
  try {
    trees.value = await allMenus()
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
