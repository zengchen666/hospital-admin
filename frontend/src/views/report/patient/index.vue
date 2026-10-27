<template>
  <div class="hp-page">
    <h3 class="hp-page-title">患者分析</h3>

    <div class="hp-toolbar">
      <el-radio-group v-model="dimension" @change="load">
        <el-radio-button value="age">年龄分布</el-radio-button>
        <el-radio-button value="gender">性别分布</el-radio-button>
        <el-radio-button value="disease">病种分布</el-radio-button>
        <el-radio-button value="new">建档趋势</el-radio-button>
      </el-radio-group>
      <el-date-picker
        v-if="useRange"
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
      <el-input-number
        v-if="dimension === 'disease'"
        v-model="topN"
        :min="1"
        :max="100"
        :step="5"
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
      v-if="!useRange"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
      title="年龄与性别是全院画像，不随时间区间变化"
      description="统计的是「当前在册患者」的构成，不是「这段时间来过谁」。要看就诊病种的时间分布请切到病种分布。"
    />

    <el-row :gutter="16">
      <el-col :span="14">
        <el-table v-loading="loading" :data="rows" border stripe>
          <el-table-column type="index" label="#" width="60" />
          <el-table-column :label="dimension === 'new' ? '月份' : '分组'" min-width="160">
            <template #default="{ row }">{{ row.displayName || row.dimensionName || '—' }}</template>
          </el-table-column>
          <el-table-column prop="patientCount" label="人数" min-width="110" align="right" />
          <el-table-column label="占比" min-width="150">
            <template #default="{ row }">
              <div class="hp-ratio">
                <el-progress :percentage="percentOf(row.patientCount)" :stroke-width="10" :show-text="false" />
                <span class="hp-ratio-text">{{ percentOf(row.patientCount) }}%</span>
              </div>
            </template>
          </el-table-column>
          <template #empty>
            <span class="hp-muted">暂无患者数据</span>
          </template>
        </el-table>
      </el-col>

      <el-col :span="10">
        <el-card shadow="never">
          <template #header><span>分布概览</span></template>
          <div v-for="row in rows" :key="row.displayName || row.dimensionName" class="hp-bar-row">
            <span class="hp-bar-label">{{ row.displayName || row.dimensionName || '—' }}</span>
            <div class="hp-bar-track">
              <div class="hp-bar-fill" :style="{ width: `${percentOf(row.patientCount)}%` }" />
            </div>
            <span class="hp-bar-value">{{ row.patientCount }}</span>
          </div>
          <div v-if="!rows.length" class="hp-muted">暂无数据</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Refresh } from '@element-plus/icons-vue'
import { exportReport, patientStat, type PatientStatVO } from '@/api/report'
import { defaultRange, SHORTCUTS } from '../range'

/**
 * 患者分析。
 *
 * <p>年龄分布<b>不传时间区间</b>：它是「在册患者画像」，
 * 传了区间也只会被后端忽略。前端干脆把日期选择器藏起来，
 * 免得使用者以为「选了 3 月就是 3 月的年龄构成」。</p>
 */

const dimension = ref<'age' | 'gender' | 'disease' | 'new'>('age')
const range = ref<string[]>(defaultRange())
const topN = ref(20)
const loading = ref(false)
const exporting = ref(false)
const rows = ref<PatientStatVO[]>([])

/** 年龄 / 性别是全量画像，病种与建档趋势才受时间区间约束 */
const useRange = computed(() => dimension.value === 'disease' || dimension.value === 'new')

const params = computed(() =>
  useRange.value
    ? { startTime: range.value?.[0], endTime: range.value?.[1], topN: topN.value }
    : { topN: topN.value },
)

const total = computed(() => rows.value.reduce((sum, r) => sum + (r.patientCount ?? 0), 0))

function percentOf(value: number | undefined): number {
  if (!value || total.value <= 0) return 0
  return Math.round((Number(value) / total.value) * 10000) / 100
}

async function load() {
  loading.value = true
  try {
    rows.value = await patientStat(dimension.value, params.value)
  } finally {
    loading.value = false
  }
}

async function doExport(format: 'excel' | 'pdf') {
  exporting.value = true
  try {
    await exportReport('patient', format, params.value)
    ElMessage.success('导出已开始')
  } finally {
    exporting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
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

.hp-bar-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
}

.hp-bar-label {
  width: 90px;
  flex-shrink: 0;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hp-bar-track {
  flex: 1;
  height: 10px;
  background: var(--el-fill-color-light);
  border-radius: 5px;
  overflow: hidden;
}

.hp-bar-fill {
  height: 100%;
  background: var(--el-color-primary);
  border-radius: 5px;
  transition: width 0.3s;
}

.hp-bar-value {
  width: 40px;
  text-align: right;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
