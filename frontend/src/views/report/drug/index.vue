<template>
  <div class="hp-page">
    <h3 class="hp-page-title">药品统计</h3>

    <div class="hp-toolbar">
      <el-date-picker
        v-model="range"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        :shortcuts="SHORTCUTS"
        style="width: 260px"
        @change="load"
      />
      <span class="hp-muted">排行条数</span>
      <el-input-number v-model="topN" :min="1" :max="100" :step="5" @change="load" />
      <el-button :icon="Refresh" :loading="loading" @click="load">查询</el-button>
      <span class="spacer" />
      <el-button v-perm="'report:export'" :icon="Download" :loading="exporting" @click="doExport('excel')">
        导出 Excel
      </el-button>
      <el-button v-perm="'report:export'" :icon="Download" :loading="exporting" @click="doExport('pdf')">
        导出 PDF
      </el-button>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
      title="只统计「已发且未退」的药品"
      description="退掉的药如果还留在排行榜里，等于把没卖出去的算成卖出去的 —— 排行越靠前的药越容易被多进货，误差会顺着采购一路放大。"
    />

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="rank" label="排名" width="80" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.rank <= 3" type="danger" size="small" effect="dark">{{ row.rank }}</el-tag>
          <span v-else>{{ row.rank }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="drugName" label="药品名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="spec" label="规格" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.spec || '—' }}</template>
      </el-table-column>
      <el-table-column prop="quantity" label="消耗数量" min-width="110" align="right" />
      <el-table-column label="销售金额" min-width="130" align="right">
        <template #default="{ row }"><span class="hp-amount">¥{{ row.amount }}</span></template>
      </el-table-column>
      <el-table-column label="金额占比" min-width="150">
        <template #default="{ row }">
          <div class="hp-ratio">
            <el-progress :percentage="percentOf(row.amount)" :stroke-width="10" :show-text="false" />
            <span class="hp-ratio-text">{{ percentOf(row.amount) }}%</span>
          </div>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">本区间暂无发药记录</span>
      </template>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Refresh } from '@element-plus/icons-vue'
import { drugRanking, exportReport, type DrugStatVO } from '@/api/report'
import { defaultRange, SHORTCUTS } from '../range'

/**
 * 药品销售排行。
 *
 * <p><b>topN 交给后端做 LIMIT</b>，不在前端截断：把全量明细拉回来再取前 20，
 * 数据量一大就是一次全表扫描加一次大对象传输，排行榜这个最该轻量的功能
 * 反而成了最重的那一个。</p>
 */

const range = ref<string[]>(defaultRange())
const topN = ref(20)
const loading = ref(false)
const exporting = ref(false)
const rows = ref<DrugStatVO[]>([])

const params = computed(() => ({
  startTime: range.value?.[0],
  endTime: range.value?.[1],
  topN: topN.value,
}))

const totalAmount = computed(() => rows.value.reduce((sum, r) => sum + Number(r.amount ?? 0), 0))

function percentOf(value: number | undefined): number {
  if (!value || totalAmount.value <= 0) return 0
  return Math.round((Number(value) / totalAmount.value) * 10000) / 100
}

async function load() {
  loading.value = true
  try {
    rows.value = await drugRanking(params.value)
  } finally {
    loading.value = false
  }
}

async function doExport(format: 'excel' | 'pdf') {
  exporting.value = true
  try {
    await exportReport('drug', format, { ...params.value, dimension: 'drug' })
    ElMessage.success('导出已开始')
  } finally {
    exporting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.hp-amount {
  color: #d93026;
  font-weight: 600;
}

.hp-ratio {
  display: flex;
  align-items: center;
  gap: 8px;
}

.hp-ratio :deep(.el-progress) {
  flex: 1;
}

.hp-ratio-text {
  width: 48px;
  text-align: right;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
