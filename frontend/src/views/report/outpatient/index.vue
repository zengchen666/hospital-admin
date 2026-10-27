<template>
  <div class="hp-page">
    <h3 class="hp-page-title">门诊统计</h3>

    <div class="hp-toolbar">
      <el-radio-group v-model="view" @change="load">
        <el-radio-button value="dept">科室分布</el-radio-button>
        <el-radio-button value="date">按日趋势</el-radio-button>
      </el-radio-group>
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
      v-if="view === 'dept'"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
      title="人次看工作量，人数看服务覆盖"
      description="一个人一个月挂三次号，人次算 3、人数算 1。复诊率高的科室用「人次」看会显得比实际更忙，两个指标要对着看。"
    />

    <el-table v-loading="loading" :data="rows" border stripe show-summary :summary-method="summary">
      <el-table-column :label="view === 'dept' ? '科室' : '日期'" min-width="140">
        <template #default="{ row }">{{ row.deptName || row.statDate || '—' }}</template>
      </el-table-column>
      <el-table-column prop="regCount" label="挂号人次" min-width="110" align="right" />
      <el-table-column prop="patientCount" label="就诊人数" min-width="110" align="right" />
      <el-table-column label="挂号费收入" min-width="120" align="right">
        <template #default="{ row }"><span class="hp-amount">¥{{ row.regFeeAmount }}</span></template>
      </el-table-column>
      <el-table-column v-if="view === 'dept'" prop="ratio" label="占比" min-width="150">
        <template #default="{ row }">
          <div class="hp-ratio">
            <el-progress :percentage="Number(row.ratio)" :stroke-width="10" :show-text="false" />
            <span class="hp-ratio-text">{{ row.ratio }}%</span>
          </div>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">本区间暂无挂号数据</span>
      </template>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Refresh } from '@element-plus/icons-vue'
import {
  exportReport,
  outpatientByDept,
  outpatientTrend,
  type OutpatientStatVO,
} from '@/api/report'
import { defaultRange, SHORTCUTS } from '../range'

/**
 * 门诊统计。
 *
 * <p>「占比」只在科室视图有意义：按天算占比，分母是整段区间的总量，
 * 出来的数字没有解释价值，反而让人误读成「当天占全院的比例」。</p>
 */

const view = ref<'dept' | 'date'>('dept')
const range = ref<string[]>(defaultRange())
const loading = ref(false)
const exporting = ref(false)
const rows = ref<OutpatientStatVO[]>([])

const params = computed(() => ({
  startTime: range.value?.[0],
  endTime: range.value?.[1],
}))

async function load() {
  loading.value = true
  try {
    rows.value =
      view.value === 'dept' ? await outpatientByDept(params.value) : await outpatientTrend(params.value)
  } finally {
    loading.value = false
  }
}

async function doExport(format: 'excel' | 'pdf') {
  exporting.value = true
  try {
    await exportReport('outpatient', format, { ...params.value, dimension: view.value })
    ElMessage.success('导出已开始')
  } finally {
    exporting.value = false
  }
}

/** 合计行：占比不累加（各科室占比之和恒为 100%，展示无意义） */
function summary({ columns }: { columns: unknown[] }) {
  const totalReg = rows.value.reduce((sum, r) => sum + (r.regCount ?? 0), 0)
  const totalPatient = rows.value.reduce((sum, r) => sum + (r.patientCount ?? 0), 0)
  const totalFee = rows.value.reduce((sum, r) => sum + Number(r.regFeeAmount ?? 0), 0)
  return columns.map((_, index) => {
    if (index === 0) return '合计'
    if (index === 1) return totalReg
    if (index === 2) return totalPatient
    if (index === 3) return `¥${totalFee.toFixed(2)}`
    return ''
  })
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
