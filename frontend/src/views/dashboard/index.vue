<template>
  <div class="hp-page">
    <h3 class="hp-page-title">工作台</h3>

    <el-row :gutter="16">
      <el-col v-for="card in cards" :key="card.label" :span="6">
        <el-card shadow="hover" class="hp-stat">
          <div class="hp-stat-label">{{ card.label }}</div>
          <div class="hp-stat-value" :style="{ color: card.color }">
            {{ loading ? '--' : card.value }}
          </div>
          <div class="hp-stat-hint hp-muted">{{ card.hint }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <span>当前登录信息</span>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="姓名">{{ store.userInfo?.realName }}</el-descriptions-item>
        <el-descriptions-item label="账号">{{ store.userInfo?.username }}</el-descriptions-item>
        <el-descriptions-item label="角色">{{ store.roles.join('、') || '-' }}</el-descriptions-item>
        <el-descriptions-item label="权限数">{{ store.permissions.length }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <span>快捷入口</span>
      </template>
      <div class="hp-quick">
        <el-button v-perm="'patient:query'" type="primary" plain @click="go('/patient/list')">
          患者档案
        </el-button>
        <el-button v-perm="'health:query'" type="success" plain @click="go('/health/record')">
          健康档案
        </el-button>
        <el-button v-perm="'system:user:query'" type="warning" plain @click="go('/system/user')">
          用户管理
        </el-button>
        <span class="hp-muted">（按钮按权限码显隐，v-perm 指令）</span>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { get } from '@/api/request'
import type { PageResult } from '@/api/types'
import { useUserStore } from '@/stores/user'

/**
 * 工作台。
 *
 * <p>统计卡片的口径说明：全部走现有分页接口的 {@code total} 字段，
 * 用 pageSize=1 只取计数，不把明细拉回前端。
 * 后续接统计报表模块后应换成专门的聚合接口（一次请求拿全部指标）。</p>
 */

const router = useRouter()
const store = useUserStore()
const loading = ref(false)

const cards = reactive([
  { label: '住院在院人数', value: 0, hint: 'status=0 的在院记录', color: '#2563eb', path: '/inpatient/admission/page', params: { status: 0 } },
  { label: '收费单总数', value: 0, hint: '全部收费单', color: '#16a34a', path: '/finance/charge/page' },
  { label: '检查申请单', value: 0, hint: '全部检查申请', color: '#d97706', path: '/exam/apply/page' },
  { label: '体检记录', value: 0, hint: '全部体检单', color: '#7c3aed', path: '/health/checkup/page' },
])

async function loadCounts() {
  loading.value = true
  // 逐个计数，任一失败只影响那一张卡，不让整页空白
  await Promise.all(
    cards.map(async (card) => {
      try {
        const res = await get<PageResult<unknown>>(card.path, { pageNum: 1, pageSize: 1, ...(card.params ?? {}) })
        card.value = res.total ?? 0
      } catch {
        card.value = 0
      }
    }),
  )
  loading.value = false
}

function go(path: string) {
  router.push(path)
}

onMounted(loadCounts)
</script>

<style scoped>
.hp-stat {
  text-align: center;
}

.hp-stat-label {
  font-size: 13px;
  color: #64748b;
}

.hp-stat-value {
  margin: 8px 0 4px;
  font-size: 28px;
  font-weight: 600;
}

.hp-stat-hint {
  font-size: 12px;
}

.hp-quick {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
