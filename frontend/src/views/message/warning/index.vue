<template>
  <div class="hp-page">
    <h3 class="hp-page-title">预警中心</h3>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
      title="实时聚合，不落库"
      description="库存、效期、危急值、传染病超期、疫苗逾期五路数据每次打开实时计算。落库就成了快照——库存补上了预警还在，看两次发现不准就不会再有人点开。"
    />

    <div class="hp-toolbar">
      <el-radio-group v-model="filterType">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="critical">危急值</el-radio-button>
        <el-radio-button value="infectious_overdue">传染病超期</el-radio-button>
        <el-radio-button value="stock_warn">库存</el-radio-button>
        <el-radio-button value="drug_expire">效期</el-radio-button>
        <el-radio-button value="vaccine_overdue">疫苗</el-radio-button>
      </el-radio-group>
      <span class="spacer" />
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <div v-if="summary.length" class="summary">
      <div v-for="s in summary" :key="s.bizType" class="summary-item">
        <span class="summary-name">{{ s.bizTypeName }}</span>
        <span class="summary-count" :style="{ color: s.color }">{{ s.count }}</span>
      </div>
    </div>

    <el-table v-loading="loading" :data="filtered" border stripe>
      <el-table-column label="级别" width="90">
        <template #default="{ row }">
          <el-tag :type="levelTag(row.level)" size="small" effect="dark">
            {{ row.levelName }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="类别" width="110">
        <template #default="{ row }">{{ row.bizTypeName }}</template>
      </el-table-column>
      <el-table-column prop="title" label="预警项" min-width="240" show-overflow-tooltip />
      <el-table-column prop="content" label="说明" min-width="300" show-overflow-tooltip />
      <el-table-column label="相关日期" width="120">
        <template #default="{ row }">{{ row.dueDate || '-' }}</template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">当前没有预警，一切正常</span>
      </template>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { listWarnings } from '@/api/message'
import type { WarningVO } from '@/api/types'

/**
 * 预警中心。
 *
 * <p><b>不做分页</b>：预警是要一眼看完的清单，翻页会把紧急项藏到第二页去。
 * 后端已对每类封顶（默认 20、上限 100），数量天然可控。</p>
 *
 * <p>分类统计与筛选都在前端做：列表一次性取全，
 * 切分类不必重新打接口 —— 预警的价值在「此刻」，来回请求反而会看到不一致的画面。</p>
 */

const rows = ref<WarningVO[]>([])
const loading = ref(false)
const filterType = ref<string>('')

const BIZ_NAMES: Record<string, string> = {
  stock_warn: '库存预警',
  drug_expire: '效期预警',
  critical: '危急值',
  infectious_overdue: '传染病超期',
  vaccine_overdue: '疫苗逾期',
}

const BIZ_COLORS: Record<string, string> = {
  stock_warn: '#f59e0b',
  drug_expire: '#f59e0b',
  critical: '#dc2626',
  infectious_overdue: '#dc2626',
  vaccine_overdue: '#2563eb',
}

const filtered = computed(() =>
  filterType.value ? rows.value.filter((r) => r.bizType === filterType.value) : rows.value,
)

/** 按类别汇总条数，顺序固定，避免每次刷新按钮乱跳 */
const summary = computed(() =>
  Object.entries(BIZ_NAMES)
    .map(([bizType, bizTypeName]) => ({
      bizType,
      bizTypeName,
      color: BIZ_COLORS[bizType] ?? '#64748b',
      count: rows.value.filter((r) => r.bizType === bizType).length,
    }))
    .filter((s) => s.count > 0),
)

function levelTag(level: number): 'danger' | 'warning' | 'info' {
  if (level === 3) return 'danger'
  if (level === 2) return 'warning'
  return 'info'
}

async function load() {
  loading.value = true
  try {
    rows.value = await listWarnings(50)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.summary {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.summary-item {
  display: flex;
  align-items: baseline;
  gap: 6px;
  padding: 8px 14px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
}

.summary-name {
  font-size: 13px;
  color: #64748b;
}

.summary-count {
  font-size: 18px;
  font-weight: 700;
}
</style>
