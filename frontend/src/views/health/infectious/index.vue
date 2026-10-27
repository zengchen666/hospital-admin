<template>
  <div class="hp-page">
    <h3 class="hp-page-title">传染病上报</h3>

    <el-alert
      type="error"
      :closable="false"
      style="margin-bottom: 12px"
      title="甲类及按甲类管理的传染病须 24 小时内上报"
      description="超时未上报的卡片在列表里用红色标出并标注超时小时数。这是法定时限，不是内部考核指标。"
    />

    <div class="hp-toolbar">
      <el-input
        v-model="query.diseaseName"
        placeholder="病种"
        clearable
        style="width: 150px"
        @clear="reload"
        @keyup.enter="reload"
      />
      <el-select v-model="query.reportStatus" placeholder="上报状态" clearable style="width: 120px" @change="reload">
        <el-option label="待上报" :value="0" />
        <el-option label="已上报" :value="1" />
      </el-select>
      <el-button :icon="Refresh" :loading="loading" @click="reload">查询</el-button>
      <span class="spacer" />
      <el-button v-perm="'health:infectious:report'" type="primary" :icon="Plus" @click="openCreate">
        填卡
      </el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="patientName" label="患者" width="100" />
      <el-table-column prop="diseaseName" label="病种" min-width="160" show-overflow-tooltip />
      <el-table-column prop="icdCode" label="ICD-10" width="110" />
      <el-table-column prop="reportDate" label="报告日期" width="120" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.reportStatus === 1 ? 'success' : 'danger'" size="small">
            {{ INFECTIOUS_STATUS_LABEL[row.reportStatus] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时限" width="140">
        <template #default="{ row }">
          <span v-if="(row.overdueHours ?? 0) > 0" class="hp-overdue">
            已超时 {{ row.overdueHours }} 小时
          </span>
          <span v-else-if="row.reportStatus === 1" class="hp-muted">已按时上报</span>
          <span v-else class="hp-muted">时限内</span>
        </template>
      </el-table-column>
      <el-table-column prop="reporterName" label="填卡人" width="100" />
      <el-table-column label="疾控反馈" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.cdcResponse || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button
            v-perm="'health:infectious:report'"
            link
            type="warning"
            :disabled="row.reportStatus === 1"
            @click="doReport(row)"
          >
            上报疾控
          </el-button>
          <el-button
            v-perm="'health:infectious:report'"
            link
            :disabled="row.reportStatus !== 1"
            @click="doFeedback(row)"
          >
            登记反馈
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">暂无传染病报告卡</span>
      </template>
    </el-table>

    <el-pagination
      style="margin-top: 12px; justify-content: flex-end"
      layout="total, sizes, prev, pager, next"
      :total="total"
      :current-page="query.pageNum"
      :page-size="query.pageSize"
      :page-sizes="[10, 20, 50]"
      @current-change="onPageChange"
      @size-change="onSizeChange"
    />

    <el-dialog v-model="dialogVisible" title="传染病报告卡" width="540px" :close-on-click-modal="false">
      <el-form :model="form" label-width="100px">
        <el-form-item label="患者" required>
          <el-select
            v-model="form.patientId"
            filterable
            remote
            reserve-keyword
            placeholder="输入姓名或患者号搜索"
            :remote-method="searchPatients"
            :loading="patientLoading"
            style="width: 100%"
          >
            <el-option
              v-for="p in patients"
              :key="p.id"
              :label="`${p.name} · ${p.patientNo}`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="病种" required>
          <el-select v-model="form.diseaseName" filterable allow-create default-first-option
                     placeholder="选择或直接输入" style="width: 100%">
            <el-option v-for="d in COMMON_DISEASES" :key="d.name" :label="d.name" :value="d.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="ICD-10">
          <el-input v-model="form.icdCode" maxlength="20" />
        </el-form-item>
        <el-form-item label="报告日期" required>
          <el-date-picker
            v-model="form.reportDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存卡片</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createInfectious,
  feedbackInfectious,
  pageInfectious,
  reportInfectious,
  INFECTIOUS_STATUS_LABEL,
  type InfectiousVO,
} from '@/api/health'
import { pagePatients } from '@/api/patient'
import type { PatientVO } from '@/api/types'

/**
 * 传染病上报。
 *
 * <p><b>填卡与上报分成两步</b>：医生先在诊疗现场填卡，
 * 公卫科复核后统一上报疾控。合成一步，医生一边看病一边点「上报」，
 * 填错的病名就直接进了疾控系统，撤回要走公文。</p>
 *
 * <p>疾控反馈只允许追加，不允许改病名与日期 ——
 * 报告卡一旦上报就是给监管方的正式文书，改内容等于改证据。</p>
 */

const COMMON_DISEASES = [
  { name: '新型冠状病毒感染' },
  { name: '肺结核' },
  { name: '病毒性肝炎（乙型）' },
  { name: '细菌性痢疾' },
  { name: '手足口病' },
  { name: '流行性感冒' },
  { name: '麻疹' },
  { name: '登革热' },
]

const rows = ref<InfectiousVO[]>([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)

const query = reactive<{
  pageNum: number
  pageSize: number
  diseaseName?: string
  reportStatus?: number
}>({ pageNum: 1, pageSize: 10 })

const dialogVisible = ref(false)
const patients = ref<PatientVO[]>([])
const patientLoading = ref(false)

const form = reactive<{
  patientId?: number
  diseaseName: string
  icdCode?: string
  reportDate?: string
}>({ diseaseName: '' })

// 选了内置病种就自动带出 ICD-10，减少手抄出错
const ICD_MAP: Record<string, string> = {
  新型冠状病毒感染: 'U07.1',
  肺结核: 'A15.9',
  '病毒性肝炎（乙型）': 'B16.9',
  细菌性痢疾: 'A03.9',
  手足口病: 'B08.4',
  流行性感冒: 'J11.1',
  麻疹: 'B05.9',
  登革热: 'A90',
}

watch(() => form.diseaseName, (v) => {
  if (v && ICD_MAP[v]) {
    form.icdCode = ICD_MAP[v]
  }
})

async function load() {
  loading.value = true
  try {
    const res = await pageInfectious(query)
    rows.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function reload() {
  query.pageNum = 1
  load()
}

function onPageChange(p: number) {
  query.pageNum = p
  load()
}

function onSizeChange(s: number) {
  query.pageSize = s
  reload()
}

async function searchPatients(keyword: string) {
  patientLoading.value = true
  try {
    const res = await pagePatients({ pageNum: 1, pageSize: 20, keyword })
    patients.value = res.list
  } finally {
    patientLoading.value = false
  }
}

function openCreate() {
  form.patientId = undefined
  form.diseaseName = ''
  form.icdCode = undefined
  form.reportDate = new Date().toISOString().slice(0, 10)
  patients.value = []
  dialogVisible.value = true
}

async function submit() {
  if (form.patientId == null) {
    ElMessage.warning('请选择患者')
    return
  }
  if (!form.diseaseName.trim()) {
    ElMessage.warning('病种不能为空')
    return
  }
  if (!form.reportDate) {
    ElMessage.warning('请选择报告日期')
    return
  }
  submitting.value = true
  try {
    await createInfectious({
      patientId: form.patientId,
      diseaseName: form.diseaseName.trim(),
      icdCode: form.icdCode,
      reportDate: form.reportDate,
    })
    ElMessage.success('报告卡已保存为待上报')
    dialogVisible.value = false
    reload()
  } finally {
    submitting.value = false
  }
}

async function doReport(row: InfectiousVO) {
  const ok = await ElMessageBox.confirm(
    `确认将「${row.patientName}」的${row.diseaseName}上报疾控？上报后病种与日期不可修改。`,
    '上报确认',
    { type: 'warning' },
  )
    .then(() => true)
    .catch(() => false)
  if (!ok) return
  await reportInfectious(row.id)
  ElMessage.success('已上报疾控')
  load()
}

async function doFeedback(row: InfectiousVO) {
  const { value } = await ElMessageBox.prompt('登记疾控反馈意见', '疾控反馈', {
    inputType: 'textarea',
    inputPlaceholder: '如：已纳入属地管理，密接筛查完成',
    inputPattern: /\S+/,
    inputErrorMessage: '反馈内容不能为空',
  }).catch(() => ({ value: null }))
  if (!value) return
  await feedbackInfectious(row.id, value)
  ElMessage.success('反馈已登记')
  load()
}

onMounted(load)
</script>

<style scoped>
.hp-overdue {
  color: #d93026;
  font-weight: 600;
}
</style>
