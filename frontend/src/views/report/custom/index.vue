<template>
  <div class="hp-page">
    <h3 class="hp-page-title">自定义报表</h3>

    <!-- 拖拽画布：选数据集 → 选维度 → 选展示形式 → 预览 -->
    <el-card shadow="never" class="hp-canvas">
      <template #header>
        <div class="hp-canvas-head">
          <span>配置画布</span>
          <span class="hp-muted hp-tip-text">
            先选数据集，再选统计维度；不在矩阵内的组合后端会直接拒绝
          </span>
        </div>
      </template>

      <el-form :inline="true">
        <el-form-item label="数据集">
          <el-select v-model="canvas.reportType" style="width: 150px" @change="onTypeChange">
            <el-option
              v-for="(name, code) in REPORT_TYPE_LABEL"
              :key="code"
              :label="name"
              :value="code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="统计维度">
          <el-select v-model="canvas.dimension" style="width: 150px">
            <el-option
              v-for="d in dimensionOptions"
              :key="d.code"
              :label="d.name"
              :value="d.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="展示形式">
          <el-select v-model="canvas.chartType" style="width: 120px">
            <el-option label="表格" value="table" />
            <el-option label="柱状图" value="bar" />
            <el-option label="折线图" value="line" />
            <el-option label="饼图" value="pie" />
          </el-select>
        </el-form-item>
        <el-form-item label="统计区间">
          <el-date-picker
            v-model="range"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :shortcuts="SHORTCUTS"
            style="width: 250px"
          />
        </el-form-item>
        <el-form-item>
          <el-button :icon="View" :loading="previewing" @click="preview">预览</el-button>
          <el-button
            v-perm="'report:config'"
            type="primary"
            :icon="Check"
            :loading="saving"
            @click="save"
          >
            保存为我的报表
          </el-button>
        </el-form-item>
      </el-form>

      <div v-if="previewResult" class="hp-preview">
        <div class="hp-preview-head">
          <span class="hp-muted">统计区间：{{ previewResult.rangeDesc }}</span>
          <span class="spacer" />
          <el-button-group>
            <el-button v-perm="'report:export'" size="small" :icon="Download" @click="exportCurrent('excel')">
              Excel
            </el-button>
            <el-button v-perm="'report:export'" size="small" :icon="Download" @click="exportCurrent('pdf')">
              PDF
            </el-button>
          </el-button-group>
        </div>

        <el-table :data="previewResult.rows" border stripe size="small" max-height="360">
          <el-table-column
            v-for="col in previewResult.columns"
            :key="col.key"
            :prop="col.key"
            :label="col.title"
            min-width="130"
            show-overflow-tooltip
          />
          <template #empty>
            <span class="hp-muted">该区间内没有数据</span>
          </template>
        </el-table>
      </div>
    </el-card>

    <!-- 我的报表 -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header><span>我的报表</span></template>

      <el-table v-loading="listLoading" :data="configs" border stripe>
        <el-table-column prop="reportName" label="报表名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="reportTypeName" label="数据集" width="110" />
        <el-table-column prop="dimensionName" label="维度" width="110">
          <template #default="{ row }">{{ row.dimensionName || '—' }}</template>
        </el-table-column>
        <el-table-column label="展示形式" width="100">
          <template #default="{ row }">{{ CHART_LABEL[row.chartType || 'table'] }}</template>
        </el-table-column>
        <el-table-column prop="creatorName" label="创建人" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="runConfig(row)">执行</el-button>
            <el-button v-perm="'report:config'" link type="primary" @click="editConfig(row)">编辑</el-button>
            <el-button v-perm="'report:config'" link type="danger" @click="removeConfig(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="hp-muted">还没有自定义报表，先在上方画布里配一个吧</span>
        </template>
      </el-table>

      <el-pagination
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :current-page="pageNum"
        :page-size="pageSize"
        @current-change="onPageChange"
      />
    </el-card>

    <!-- 命名对话框 -->
    <el-dialog v-model="nameVisible" :title="editing ? '编辑报表' : '保存报表'" width="420px">
      <el-form label-width="90px">
        <el-form-item label="报表名称" required>
          <el-input v-model="nameInput" maxlength="100" placeholder="例如：内科收入月报" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nameVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Download, View } from '@element-plus/icons-vue'
import {
  createReportConfig,
  deleteReportConfig,
  executeReport,
  exportReport,
  pageReportConfigs,
  previewReport,
  updateReportConfig,
  REPORT_DIMENSIONS,
  REPORT_TYPE_LABEL,
  type ReportConfigVO,
  type ReportResultVO,
} from '@/api/report'
import { defaultRange, SHORTCUTS } from '../range'

/**
 * 自定义报表。
 *
 * <p>「拖拽式配置」在前端是自由拖，落到后端必须是<b>有限集合里的选择</b>：
 * 数据集 × 维度走白名单矩阵，非法组合直接 40001。
 * 这里把维度下拉框按数据集联动，就是为了<b>让用户根本选不出非法组合</b>
 * —— 与其等后端报错，不如让错误选项不出现。</p>
 *
 * <p>预览不落库：要求先保存才能看结果，会把画布逼成「一堆半成品配置」。</p>
 */

const CHART_LABEL: Record<string, string> = {
  table: '表格',
  bar: '柱状图',
  line: '折线图',
  pie: '饼图',
}

const canvas = reactive({
  reportType: 'outpatient',
  dimension: 'dept',
  chartType: 'table',
})
const range = ref<string[]>(defaultRange())

const previewing = ref(false)
const saving = ref(false)
const previewResult = ref<ReportResultVO | null>(null)

const configs = ref<ReportConfigVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const listLoading = ref(false)

const nameVisible = ref(false)
const nameInput = ref('')
const editing = ref<ReportConfigVO | null>(null)

const dimensionOptions = computed(
  () => REPORT_DIMENSIONS[canvas.reportType] ?? [{ code: 'summary', name: '全院汇总' }],
)

const queryParams = computed(() => ({
  startTime: range.value?.[0],
  endTime: range.value?.[1],
}))

function onTypeChange() {
  // 切换数据集后原维度可能已不在可选范围内，回落到第一个合法维度
  const options = dimensionOptions.value
  if (!options.some((d) => d.code === canvas.dimension)) {
    canvas.dimension = options[0]?.code ?? ''
  }
  previewResult.value = null
}

async function loadList() {
  listLoading.value = true
  try {
    const res = await pageReportConfigs({ pageNum: pageNum.value, pageSize })
    configs.value = res.list
    total.value = res.total
  } finally {
    listLoading.value = false
  }
}

async function preview() {
  previewing.value = true
  try {
    previewResult.value = await previewReport(canvas.reportType, canvas.dimension, queryParams.value)
  } finally {
    previewing.value = false
  }
}

async function runConfig(row: ReportConfigVO) {
  previewing.value = true
  try {
    const res = await executeReport(row.id, queryParams.value)
    canvas.reportType = row.reportType
    canvas.dimension = row.dimension ?? ''
    canvas.chartType = row.chartType ?? 'table'
    previewResult.value = res
  } finally {
    previewing.value = false
  }
}

function save() {
  editing.value = null
  nameInput.value = ''
  nameVisible.value = true
}

function editConfig(row: ReportConfigVO) {
  editing.value = row
  nameInput.value = row.reportName
  nameVisible.value = true
}

async function submitSave() {
  const name = nameInput.value.trim()
  if (!name) {
    ElMessage.warning('请填写报表名称')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      await updateReportConfig(editing.value.id, {
        reportName: name,
        reportType: canvas.reportType,
        dimension: canvas.dimension,
        chartType: canvas.chartType,
      })
      ElMessage.success('报表已更新')
    } else {
      await createReportConfig({
        reportName: name,
        reportType: canvas.reportType,
        dimension: canvas.dimension,
        chartType: canvas.chartType,
      })
      ElMessage.success('报表已保存')
    }
    nameVisible.value = false
    await loadList()
  } finally {
    saving.value = false
  }
}

async function removeConfig(row: ReportConfigVO) {
  await ElMessageBox.confirm(`删除后不可恢复，确认删除「${row.reportName}」？`, '删除报表', {
    type: 'warning',
  })
  await deleteReportConfig(row.id)
  ElMessage.success('已删除')
  await loadList()
}

async function exportCurrent(format: 'excel' | 'pdf') {
  await exportReport(canvas.reportType, format, {
    ...queryParams.value,
    dimension: canvas.dimension,
  })
}

function onPageChange(p: number) {
  pageNum.value = p
  loadList()
}

onMounted(loadList)
</script>

<style scoped>
.hp-canvas-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.hp-tip-text {
  font-size: 12px;
}

.hp-preview {
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.hp-preview-head {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
  font-size: 13px;
}
</style>
