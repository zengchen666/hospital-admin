<template>
  <div class="hp-page">
    <h3 class="hp-page-title">收入统计</h3>

    <div class="hp-toolbar">
      <el-radio-group v-model="dimension" @change="load">
        <el-radio-button value="itemType">收入构成</el-radio-button>
        <el-radio-button value="dept">科室收入</el-radio-button>
        <el-radio-button value="doctor">医生绩效</el-radio-button>
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
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
      :title="alertTitle"
      :description="alertDesc"
    />

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column type="index" label="#" width="60" />
      <el-table-column :label="firstColumnLabel" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.dimensionName || '未关联' }}</template>
      </el-table-column>

      <template v-if="dimension === 'doctor'">
        <el-table-column prop="visitCount" label="接诊人次" min-width="110" align="right" />
        <el-table-column label="开方金额" min-width="130" align="right">
          <template #default="{ row }"><span class="hp-amount">¥{{ row.prescAmount }}</span></template>
        </el-table-column>
      </template>

      <template v-else>
        <el-table-column label="收入金额" min-width="130" align="right">
          <template #default="{ row }"><span class="hp-amount">¥{{ row.amount }}</span></template>
        </el-table-column>
        <el-table-column prop="chargeCount" label="单据数" min-width="100" align="right" />
        <el-table-column prop="quantity" label="项目数量" min-width="110" align="right" />
      </template>

      <el-table-column v-if="dimension !== 'doctor'" label="占比" min-width="150">
        <template #default="{ row }">
          <div class="hp-ratio">
            <el-progress :percentage="percentOf(row.amount)" :stroke-width="10" :show-text="false" />
            <span class="hp-ratio-text">{{ percentOf(row.amount) }}%</span>
          </div>
        </template>
      </el-table-column>

      <template #empty>
        <span class="hp-muted">本区间暂无已支付收费记录</span>
      </template>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Refresh } from '@element-plus/icons-vue'
import { exportReport, incomeStat, type IncomeStatVO } from '@/api/report'
import { defaultRange, SHORTCUTS } from '../range'

/**
 * 收入统计。
 *
 * <p>三个维度共用一张表但列不同：<b>医生绩效看的是「人次 + 开方金额」，
 * 不是收入金额</b>。用开方金额衡量绩效会鼓励多开药，
 * 所以两列分开摆，让看报表的人自己判断，而不是合成一个「绩效分」。</p>
 */

const dimension = ref<'itemType' | 'dept' | 'doctor'>('itemType')
const range = ref<string[]>(defaultRange())
const loading = ref(false)
const exporting = ref(false)
const rows = ref<IncomeStatVO[]>([])

const params = computed(() => ({
  startTime: range.value?.[0],
  endTime: range.value?.[1],
}))

const firstColumnLabel = computed(() =>
  dimension.value === 'doctor' ? '医生' : dimension.value === 'dept' ? '科室' : '项目类型',
)

const alertTitle = computed(() =>
  dimension.value === 'doctor'
    ? '只统计已收费与已发药的处方'
    : '只统计已支付（pay_status = 1）的收费单',
)

const alertDesc = computed(() =>
  dimension.value === 'doctor'
    ? '待审核与已作废的处方不计入绩效。把没抓药的处方算进去，等于鼓励医生开空方。'
    : dimension.value === 'dept'
      ? '住院收费尚未关联就诊单，暂时归到「未关联科室」。这是已知口径，不是漏统计。'
      : '未支付的单据不计入收入。把未支付算进收入，报表当天好看，月底对账全是窟窿，数量已扣除已退数量。',
)

const totalAmount = computed(() =>
  rows.value.reduce((sum, r) => sum + Number(dimension.value === 'doctor' ? r.prescAmount ?? 0 : r.amount ?? 0), 0),
)

function percentOf(value: number | undefined): number {
  if (!value || totalAmount.value <= 0) return 0
  return Math.round((Number(value) / totalAmount.value) * 10000) / 100
}

async function load() {
  loading.value = true
  try {
    rows.value = await incomeStat(dimension.value, params.value)
  } finally {
    loading.value = false
  }
}

async function doExport(format: 'excel' | 'pdf') {
  exporting.value = true
  try {
    await exportReport('income', format, { ...params.value, dimension: dimension.value })
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
