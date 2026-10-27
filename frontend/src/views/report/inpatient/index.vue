<template>
  <div class="hp-page">
    <h3 class="hp-page-title">住院统计</h3>

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
      <el-button :icon="Refresh" :loading="loading" @click="load">查询</el-button>
      <span class="spacer" />
      <el-button
        v-perm="'report:export'"
        :icon="Download"
        :loading="exporting"
        @click="doExport('excel')"
      >
        导出 Excel
      </el-button>
      <el-button v-perm="'report:export'" :icon="Download" :loading="exporting" @click="doExport('pdf')">
        导出 PDF
      </el-button>
    </div>

    <el-skeleton v-if="loading && !data" :rows="4" animated />

    <template v-else-if="data">
      <div class="hp-stat-cards">
        <el-card v-for="card in cards" :key="card.label" shadow="never" class="hp-stat-card">
          <div class="hp-stat-label">{{ card.label }}</div>
          <div class="hp-stat-value">
            {{ card.value }}<span v-if="card.unit" class="hp-stat-unit">{{ card.unit }}</span>
          </div>
        </el-card>
      </div>

      <el-card shadow="never" style="margin-top: 16px">
        <template #header>
          <span>床位情况</span>
        </template>
        <el-row :gutter="16">
          <el-col :span="12">
            <div class="hp-bed-row">
              <span class="hp-muted">占用床位</span>
              <span class="hp-strong">{{ data.occupiedBeds }}</span>
            </div>
            <div class="hp-bed-row">
              <span class="hp-muted">可用床位（已排除维修床）</span>
              <span class="hp-strong">{{ data.availableBeds }}</span>
            </div>
            <div class="hp-bed-row">
              <span class="hp-muted">使用率</span>
              <span class="hp-strong">{{ data.bedUsageRate }}%</span>
            </div>
          </el-col>
          <el-col :span="12">
            <el-progress
              type="dashboard"
              :percentage="bedRate"
              :color="bedColor"
              :width="120"
            />
            <div class="hp-muted hp-tip">
              使用率超过 90% 意味着加床风险，低于 60% 则说明床位闲置
            </div>
          </el-col>
        </el-row>
      </el-card>
    </template>

    <el-empty v-else description="本区间暂无住院数据" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Refresh } from '@element-plus/icons-vue'
import { exportReport, inpatientSummary, type InpatientStatVO } from '@/api/report'
import { defaultRange, SHORTCUTS } from '../range'

/**
 * 住院统计。
 *
 * <p><b>使用率的分母是「可用床位」而不是「总床位」</b>：维修中的床位
 * 进分母会拉低使用率，正好把床位紧张掩盖掉。这个口径后端已经在 SQL 里定好，
 * 前端只负责原样呈现，不自己再算一遍 —— 两处各算一次迟早对不上。</p>
 */

const range = ref<string[]>(defaultRange())
const loading = ref(false)
const exporting = ref(false)
const data = ref<InpatientStatVO | null>(null)

const cards = computed(() => {
  const d = data.value
  if (!d) return []
  return [
    { label: '在院人数', value: d.inHospitalCount, unit: '人' },
    { label: '区间入院', value: d.admittedCount, unit: '人次' },
    { label: '区间出院', value: d.dischargedCount, unit: '人次' },
    { label: '平均住院日', value: Number(d.avgStayDays).toFixed(1), unit: '天' },
    { label: '在院预交金', value: `¥${d.depositBalance}`, unit: '' },
  ]
})

const bedRate = computed(() => {
  const rate = Number(data.value?.bedUsageRate ?? 0)
  return Math.min(100, Math.round(rate))
})

const bedColor = computed(() => {
  if (bedRate.value >= 90) return '#d93026'
  if (bedRate.value >= 70) return '#e6a23c'
  return '#409eff'
})

async function load() {
  loading.value = true
  try {
    data.value = await inpatientSummary({
      startTime: range.value?.[0],
      endTime: range.value?.[1],
    })
  } finally {
    loading.value = false
  }
}

async function doExport(format: 'excel' | 'pdf') {
  exporting.value = true
  try {
    await exportReport('inpatient', format, {
      startTime: range.value?.[0],
      endTime: range.value?.[1],
      dimension: 'summary',
    })
    ElMessage.success('导出已开始')
  } finally {
    exporting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.hp-stat-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.hp-stat-card {
  border-radius: 8px;
}

.hp-stat-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.hp-stat-value {
  margin-top: 6px;
  font-size: 24px;
  font-weight: 600;
}

.hp-stat-unit {
  margin-left: 4px;
  font-size: 13px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.hp-bed-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.hp-strong {
  font-weight: 600;
}

.hp-tip {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
}
</style>
