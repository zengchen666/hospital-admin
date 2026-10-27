<template>
  <div class="hp-page">
    <h3 class="hp-page-title">角色管理</h3>

    <el-alert
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
      title="角色与权限为预置数据，界面只读"
      description="授权变更通过 sql/ 目录下的补丁脚本维护 —— 脚本即变更记录。界面上放一个勾选框，改起来是快了，但事后没有任何可追溯的依据。"
    />

    <el-table v-loading="loading" :data="roles" border stripe @row-click="onRowClick">
      <el-table-column prop="roleName" label="角色名称" min-width="140" />
      <el-table-column prop="roleCode" label="角色编码" min-width="120" />
      <el-table-column prop="remark" label="说明" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">{{ row.remark || '—' }}</template>
      </el-table-column>
      <el-table-column label="权限数" width="100" align="right">
        <template #default="{ row }">
          <el-tag v-if="row.permCount != null" size="small">{{ row.permCount }}</el-tag>
          <span v-else class="hp-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="userCount" label="用户数" width="90" align="right" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click.stop="openPerms(row)">查看权限</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">暂无角色</span>
      </template>
    </el-table>

    <!-- 权限抽屉 -->
    <el-drawer v-model="drawer" :title="current ? `「${current.roleName}」的权限` : '权限'" size="45%">
      <el-input
        v-model="keyword"
        placeholder="按权限码或名称过滤"
        clearable
        style="margin-bottom: 12px"
      />

      <el-collapse v-model="activeModules">
        <el-collapse-item v-for="(items, module) in grouped" :key="module" :name="module">
          <template #title>
            <span class="hp-module-title">
              {{ MODULE_LABEL[module] || module }}
              <el-tag size="small" type="info">{{ items.length }}</el-tag>
              <el-tag v-if="ownedCount(items) > 0" size="small" type="success">
                已授权 {{ ownedCount(items) }}
              </el-tag>
            </span>
          </template>
          <div class="hp-perm-grid">
            <div v-for="p in items" :key="p.permCode" class="hp-perm-item">
              <el-tag :type="p.status === 1 ? 'success' : 'info'" size="small" effect="plain">
                {{ p.status === 1 ? '✓' : '—' }} {{ p.permCode }}
              </el-tag>
              <span class="hp-perm-name">{{ p.permName }}</span>
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>

      <el-empty v-if="!Object.keys(grouped).length" description="没有匹配的权限" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listRoles, rolePermissionMatrix, MODULE_LABEL, type PermissionVO, type RoleVO } from '@/api/system'

/**
 * 角色管理（只读）。
 *
 * <p>真正决定权限的是「这个角色挂了哪些权限码」，不是角色名。
 * 因此列表直接带出权限数，点开还能看到每一个权限码是否持有 ——
 * 否则「收费员能不能看全院收入」这种问题只能靠猜或者翻 SQL。</p>
 */

const roles = ref<RoleVO[]>([])
const loading = ref(false)

const drawer = ref(false)
const current = ref<RoleVO | null>(null)
const perms = ref<PermissionVO[]>([])
const keyword = ref('')
const activeModules = ref<string[]>([])

const grouped = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  const map: Record<string, PermissionVO[]> = {}
  for (const p of perms.value) {
    if (kw && !(p.permCode.toLowerCase().includes(kw) || (p.permName || '').includes(kw))) {
      continue
    }
    const key = p.module || 'other'
    ;(map[key] ||= []).push(p)
  }
  return map
})

function ownedCount(items: PermissionVO[]): number {
  return items.filter((p) => p.status === 1).length
}

async function load() {
  loading.value = true
  try {
    roles.value = await listRoles()
  } finally {
    loading.value = false
  }
}

async function openPerms(row: RoleVO) {
  current.value = row
  perms.value = []
  keyword.value = ''
  drawer.value = true
  perms.value = await rolePermissionMatrix(row.id)
  // 默认只展开「已授权」的模块，一眼能看到这个角色真正能干什么
  activeModules.value = Object.entries(
    perms.value.reduce<Record<string, number>>((acc, p) => {
      const key = p.module || 'other'
      acc[key] = (acc[key] || 0) + (p.status === 1 ? 1 : 0)
      return acc
    }, {}),
  )
    .filter(([, n]) => n > 0)
    .map(([k]) => k)
}

function onRowClick(row: RoleVO) {
  openPerms(row)
}

onMounted(load)
</script>

<style scoped>
.hp-module-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.hp-perm-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 6px 12px;
}

.hp-perm-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.hp-perm-name {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
