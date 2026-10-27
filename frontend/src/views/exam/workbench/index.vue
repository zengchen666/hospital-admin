<template>
  <div class="hp-page">
    <h3 class="hp-page-title">检验工作台</h3>

    <div class="hp-toolbar">
      <el-radio-group v-model="statusFilter" @change="reload">
        <el-radio-button :value="1">待采样</el-radio-button>
        <el-radio-button :value="2">检验中</el-radio-button>
        <el-radio-button :value="3">已出报告</el-radio-button>
        <el-radio-button value="all">全部</el-radio-button>
      </el-radio-group>
      <el-button :icon="Refresh" :loading="loading" @click="reload">刷新</el-button>
      <span class="spacer" />
      <span class="hp-muted" style="font-size: 12px">
        采样 → 录结果 → 写报告 → 发布（发布需审核权限，不能自己录自己发）
      </span>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="applyNo" label="申请单号" width="170" />
      <el-table-column prop="patientName" label="患者" width="100" />
      <el-table-column label="类型" width="80">
        <template #default="{ row }">{{ EXAM_TYPE_LABEL[row.examType] }}</template>
      </el-table-column>
      <el-table-column prop="examItem" label="检查项目" min-width="150" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">{{ APPLY_STATUS_LABEL[row.status] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="条码" width="140">
        <template #default="{ row }">
          <span v-if="row.barcodeNo">{{ row.barcodeNo }}</span>
          <span v-else class="hp-muted">未采样</span>
        </template>
      </el-table-column>
      <el-table-column label="报告" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.reportStatus === 1" type="success" size="small">已发布</el-tag>
          <el-tag v-else-if="row.reportStatus === 0" type="warning" size="small">草稿</el-tag>
          <span v-else class="hp-muted">未录入</span>
        </template>
      </el-table-column>
      <el-table-column prop="applyTime" label="开单时间" width="170" />
      <el-table-column label="操作" width="310" fixed="right">
        <template #default="{ row }">
          <el-button
            v-perm="'exam:report:entry'"
            link
            type="primary"
            :disabled="row.status !== 1"
            @click="doSample(row)"
          >
            采样
          </el-button>
          <el-button
            v-perm="'exam:report:entry'"
            link
            :disabled="row.status === 0 || row.status === 3 || row.reportStatus === 1"
            @click="openItems(row)"
          >
            录结果
          </el-button>
          <el-button
            v-perm="'exam:report:entry'"
            link
            :disabled="row.status === 0 || row.reportStatus === 1"
            @click="openReport(row)"
          >
            写报告
          </el-button>
          <el-button
            v-perm="'exam:report:audit'"
            link
            type="success"
            :disabled="!row.reportId || row.reportStatus === 1"
            @click="doPublish(row)"
          >
            发布
          </el-button>
          <el-button v-perm="'exam:query'" link :disabled="!row.barcodeNo" @click="showBarcode(row)">
            条码
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">该状态下暂无检查单</span>
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

    <!-- 结果录入 -->
    <el-dialog v-model="itemVisible" :title="`结果录入 · ${current?.applyNo ?? ''}`" width="760px">
      <el-table :data="itemRows" border size="small">
        <el-table-column prop="itemName" label="项目" min-width="150" show-overflow-tooltip />
        <el-table-column label="结果值" width="150">
          <template #default="{ row }">
            <el-input v-model="row.resultValue" size="small" placeholder="如 12.5 / 阴性" />
          </template>
        </el-table-column>
        <el-table-column label="参考范围" width="130">
          <template #default="{ row }">
            <el-input v-model="row.refRange" size="small" placeholder="如 4-10" />
          </template>
        </el-table-column>
        <el-table-column label="单位" width="100">
          <template #default="{ row }">
            <el-input v-model="row.unit" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="异常" width="130">
          <template #default="{ row }">
            <el-select v-model="row.abnormalFlag" size="small">
              <el-option label="正常" :value="0" />
              <el-option label="偏高" :value="1" />
              <el-option label="偏低" :value="2" />
              <el-option label="定性异常" :value="3" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="itemVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitItems">保存结果</el-button>
      </template>
    </el-dialog>

    <!-- 报告录入 -->
    <el-dialog v-model="reportVisible" :title="`报告录入 · ${current?.applyNo ?? ''}`" width="640px">
      <el-alert
        type="warning"
        :closable="false"
        style="margin-bottom: 14px"
        title="危急值须由录入者人工判定"
        description="系统不按数值阈值自动判危急值 —— 阈值因项目、机构而异，漏判的代价是患者生命。"
      />
      <el-form label-width="96px">
        <el-form-item label="结果摘要">
          <el-input v-model="reportForm.result" type="textarea" :rows="4" maxlength="2000" />
        </el-form-item>
        <el-form-item label="结论">
          <el-input v-model="reportForm.conclusion" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
        <el-form-item label="危急值">
          <el-switch
            v-model="critical"
            active-text="是"
            inactive-text="否"
            active-color="#d93026"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReport">保存报告</el-button>
      </template>
    </el-dialog>

    <!-- 条码 -->
    <el-dialog v-model="barcodeVisible" title="样本条码" width="440px">
      <div v-if="barcode" class="barcode-card">
        <div class="barcode-no">{{ barcode.barcodeNo }}</div>
        <el-descriptions :column="1" size="small" border>
          <el-descriptions-item label="患者">{{ barcode.patientName }}</el-descriptions-item>
          <el-descriptions-item label="项目">{{ barcode.examItem || '—' }}</el-descriptions-item>
          <el-descriptions-item label="明细">{{ barcode.itemNames.join('、') }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="barcodeVisible = false">关闭</el-button>
        <el-button type="primary" @click="doPrint">打印</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  examBarcode,
  listExamItems,
  pageExamApplies,
  publishExamReport,
  sampleExam,
  saveExamItems,
  saveExamReport,
  APPLY_STATUS_LABEL,
  EXAM_TYPE_LABEL,
  type ExamApplyVO,
  type ExamBarcodeVO,
  type ExamItemVO,
} from '@/api/exam'

/**
 * 检验工作台（技师视角）。
 *
 * <p><b>发布按钮与录入按钮分开判权</b>：技师拿 exam:report:entry，审核医师拿
 * exam:report:audit。这不是形式主义 —— 自己录的数值自己签发，等于取消了复核环节，
 * 检验科的质控就形同虚设。</p>
 *
 * <p><b>「录结果」与「写报告」分成两步</b>：前者是客观数值（谁测的都一样），
 * 后者是主观结论（需要资质）。合成一步会让「数值对但结论错」这类问题无法定位责任人。</p>
 */

const rows = ref<ExamApplyVO[]>([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)

/** 字符串哨兵 'all'：el-radio-button 的 :value 不支持 undefined */
const statusFilter = ref<number | 'all'>(1)

const query = reactive<{ pageNum: number; pageSize: number }>({ pageNum: 1, pageSize: 10 })

const current = ref<ExamApplyVO | null>(null)

/* 结果录入 */
const itemVisible = ref(false)
const itemRows = ref<ExamItemVO[]>([])

/* 报告录入 */
const reportVisible = ref(false)
const critical = ref(false)
const reportForm = reactive<{ result: string; conclusion: string }>({ result: '', conclusion: '' })

/* 条码 */
const barcodeVisible = ref(false)
const barcode = ref<ExamBarcodeVO | null>(null)

async function load() {
  loading.value = true
  try {
    const res = await pageExamApplies({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      status: statusFilter.value === 'all' ? undefined : statusFilter.value,
    })
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

function statusTag(status: number) {
  return (['warning', 'primary', '', 'success'] as const)[status] ?? 'info'
}

/* ---------------- 采样 ---------------- */

async function doSample(row: ExamApplyVO) {
  const code = await sampleExam({ applyId: Number(row.id) })
  ElMessage.success(`采样成功，条码号 ${code}`)
  load()
}

/* ---------------- 结果 ---------------- */

async function openItems(row: ExamApplyVO) {
  current.value = row
  const list = await listExamItems(row.id)
  itemRows.value = list.map((i) => ({
    ...i,
    abnormalFlag: i.abnormalFlag ?? 0,
  }))
  itemVisible.value = true
}

async function submitItems() {
  if (!current.value) return
  submitting.value = true
  try {
    await saveExamItems(
      current.value.id,
      itemRows.value.map((i) => ({
        itemId: Number(i.id),
        resultValue: i.resultValue,
        refRange: i.refRange,
        unit: i.unit,
        abnormalFlag: i.abnormalFlag ?? 0,
      })),
    )
    ElMessage.success('结果已保存')
    itemVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

/* ---------------- 报告 ---------------- */

async function openReport(row: ExamApplyVO) {
  current.value = row
  critical.value = row.isCritical === 1
  reportForm.result = ''
  reportForm.conclusion = ''
  reportVisible.value = true
}

async function submitReport() {
  if (!current.value) return
  const list = await listExamItems(current.value.id)
  if (list.length === 0) {
    ElMessage.warning('该检查单没有明细项目，无法保存报告')
    return
  }
  submitting.value = true
  try {
    await saveExamReport({
      applyId: Number(current.value.id),
      result: reportForm.result || undefined,
      conclusion: reportForm.conclusion || undefined,
      isCritical: critical.value ? 1 : 0,
      items: list.map((i) => ({
        itemId: Number(i.id),
        resultValue: i.resultValue,
        refRange: i.refRange,
        unit: i.unit,
        abnormalFlag: i.abnormalFlag ?? 0,
      })),
    })
    ElMessage.success('报告已保存为草稿，需审核发布后临床才能看到')
    reportVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

/* ---------------- 发布 ---------------- */

async function doPublish(row: ExamApplyVO) {
  if (!row.reportId) return
  const ok = await ElMessageBox.confirm(
    `确认发布「${row.patientName}」的${row.examItem || '检查'}报告？发布后结果不可修改。`,
    '发布确认',
    { type: 'warning' },
  )
    .then(() => true)
    .catch(() => false)
  if (!ok) return
  await publishExamReport(row.reportId)
  ElMessage.success('报告已发布')
  load()
}

/* ---------------- 条码 ---------------- */

async function showBarcode(row: ExamApplyVO) {
  barcode.value = await examBarcode(row.id)
  barcodeVisible.value = true
}

function doPrint() {
  window.print()
}

onMounted(load)
</script>

<style scoped>
.barcode-card {
  text-align: center;
}

.barcode-no {
  font-family: 'Courier New', Courier, monospace;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 2px;
  margin-bottom: 14px;
}
</style>
