<template>
  <div class="hp-page">
    <h3 class="hp-page-title">检查检验申请</h3>

    <div class="hp-toolbar">
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="reload">
        <el-option label="待缴费" :value="0" />
        <el-option label="待采样" :value="1" />
        <el-option label="检验中" :value="2" />
        <el-option label="已出报告" :value="3" />
      </el-select>
      <el-select v-model="query.examType" placeholder="类型" clearable style="width: 110px" @change="reload">
        <el-option label="检验" :value="1" />
        <el-option label="检查" :value="2" />
      </el-select>
      <el-button :icon="Refresh" :loading="loading" @click="reload">查询</el-button>
      <span class="spacer" />
      <el-button v-perm="'exam:apply:add'" type="primary" :icon="Plus" @click="openCreate">
        开检查单
      </el-button>
    </div>

    <el-alert
      v-if="unpaidCount > 0"
      type="info"
      :closable="false"
      style="margin-bottom: 12px"
      :title="`当前有 ${unpaidCount} 张待缴费检查单`"
      description="待缴费的检查单不能采样。患者需先到收费窗口缴费，状态才会推进到「待采样」。"
    />

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column type="expand">
        <template #default="{ row }">
          <el-table :data="row.items" border size="small" style="margin: 4px 0 4px 48px">
            <el-table-column prop="itemCode" label="项目编码" width="140" />
            <el-table-column prop="itemName" label="项目名称" min-width="160" />
            <el-table-column label="结果" width="120">
              <template #default="{ row: it }">{{ it.resultValue || '—' }}</template>
            </el-table-column>
            <el-table-column prop="refRange" label="参考范围" width="120" />
            <el-table-column label="异常" width="90">
              <template #default="{ row: it }">
                <el-tag :type="abnormalTag(it.abnormalFlag)" size="small">
                  {{ ABNORMAL_LABEL[it.abnormalFlag ?? 0] }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </el-table-column>
      <el-table-column prop="applyNo" label="申请单号" width="170" />
      <el-table-column prop="patientName" label="患者" width="100" />
      <el-table-column prop="doctorName" label="开单医生" width="100" />
      <el-table-column label="类型" width="80">
        <template #default="{ row }">{{ EXAM_TYPE_LABEL[row.examType] }}</template>
      </el-table-column>
      <el-table-column prop="examItem" label="检查项目" min-width="150" show-overflow-tooltip />
      <el-table-column prop="bodyPart" label="部位" width="100" />
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
      <el-table-column label="危急值" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.isCritical === 1" type="danger" size="small">危急</el-tag>
          <span v-else class="hp-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="applyTime" label="开单时间" width="170" />
      <el-table-column label="操作" width="150" fixed="right">
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
          <el-button v-perm="'exam:query'" link :disabled="!row.barcodeNo" @click="printBarcode(row)">
            条码
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">暂无检查申请单</span>
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

    <!-- 开检查单 -->
    <el-dialog v-model="createVisible" title="开检查检验单" width="720px" :close-on-click-modal="false">
      <el-form :model="form" label-width="96px">
        <el-form-item label="就诊记录" required>
          <el-select
            v-model="form.visitId"
            filterable
            remote
            reserve-keyword
            placeholder="输入患者姓名或挂号号搜索"
            :remote-method="searchVisits"
            :loading="visitLoading"
            style="width: 100%"
            @change="onVisitChange"
          >
            <el-option
              v-for="v in visits"
              :key="v.id"
              :label="`${v.patientName} · ${v.regNo}`"
              :value="v.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="检查类型" required>
          <el-radio-group v-model="form.examType">
            <el-radio :value="1">检验</el-radio>
            <el-radio :value="2">检查（影像）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="检查项目">
          <el-input v-model="form.examItem" placeholder="如：血常规、胸部 CT" maxlength="100" />
        </el-form-item>
        <el-form-item label="检查部位">
          <el-input v-model="form.bodyPart" placeholder="如：胸部" maxlength="50" />
        </el-form-item>
        <el-form-item label="临床诊断">
          <el-input
            v-model="form.clinicalDiagnosis"
            type="textarea"
            :rows="2"
            maxlength="500"
            placeholder="供技师/影像科参考"
          />
        </el-form-item>
        <el-form-item label="明细项目" required>
          <div style="width: 100%">
            <div v-for="(it, idx) in form.items" :key="idx" style="display: flex; gap: 6px; margin-bottom: 6px">
              <el-input v-model="it.itemCode" placeholder="编码" style="width: 150px" maxlength="50" />
              <el-input v-model="it.itemName" placeholder="项目名称" maxlength="100" />
              <el-button :icon="Delete" @click="removeItem(idx)" />
            </div>
            <el-button size="small" :icon="Plus" @click="addItem">添加明细</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">提交</el-button>
      </template>
    </el-dialog>

    <!-- 条码打印 -->
    <el-dialog v-model="barcodeVisible" title="样本条码" width="440px">
      <div v-if="barcode" class="barcode-card">
        <div class="barcode-no">{{ barcode.barcodeNo }}</div>
        <el-descriptions :column="1" size="small" border>
          <el-descriptions-item label="申请单号">{{ barcode.applyNo }}</el-descriptions-item>
          <el-descriptions-item label="患者">{{ barcode.patientName }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ EXAM_TYPE_LABEL[barcode.examType] }}</el-descriptions-item>
          <el-descriptions-item label="项目">{{ barcode.examItem || '—' }}</el-descriptions-item>
          <el-descriptions-item label="部位">{{ barcode.bodyPart || '—' }}</el-descriptions-item>
          <el-descriptions-item label="明细">{{ barcode.itemNames.join('、') }}</el-descriptions-item>
        </el-descriptions>
        <p class="hp-muted" style="margin: 10px 0 0; font-size: 12px">
          条码号在首次打印时生成，重复打印保持同一号，请勿手工改写。
        </p>
      </div>
      <template #footer>
        <el-button @click="barcodeVisible = false">关闭</el-button>
        <el-button type="primary" @click="doPrint">打印</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Plus, Refresh } from '@element-plus/icons-vue'
import { pageVisits, type VisitVO } from '@/api/clinic'
import {
  createExamApply,
  examBarcode,
  pageExamApplies,
  sampleExam,
  ABNORMAL_LABEL,
  APPLY_STATUS_LABEL,
  EXAM_TYPE_LABEL,
  type ExamApplyVO,
  type ExamBarcodeVO,
  type ExamItemRequest,
} from '@/api/exam'

/**
 * 检查检验申请。
 *
 * <p><b>「采样」按钮只对「待采样」状态开放</b>：待缴费的单点采样会在后端被拒，
 * 与其让患者白跑一趟，不如在按钮上就体现出来。
 * 已采样/已出报告的同样禁用 —— 重复采样会产生第二管标本，检验结果对不上人。</p>
 *
 * <p>就诊记录用远程搜索（按姓名/挂号号）而不是下拉全量：
 * 一天几百条挂号，全量下拉既卡又容易选错人。</p>
 */

const rows = ref<ExamApplyVO[]>([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)

const query = reactive<{ pageNum: number; pageSize: number; status?: number; examType?: number }>({
  pageNum: 1,
  pageSize: 10,
})

/** 未缴费单数量的提示，避免医生以为开了单就能直接去检验科 */
const unpaidCount = computed(() => rows.value.filter((r) => r.status === 0).length)

const createVisible = ref(false)
const form = reactive<{
  visitId?: number
  examType: number
  examItem?: string
  bodyPart?: string
  clinicalDiagnosis?: string
  items: ExamItemRequest[]
}>({
  examType: 1,
  items: [{ itemCode: '', itemName: '' }],
})

const visits = ref<VisitVO[]>([])
const visitLoading = ref(false)

const barcodeVisible = ref(false)
const barcode = ref<ExamBarcodeVO | null>(null)

async function load() {
  loading.value = true
  try {
    const res = await pageExamApplies(query)
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

function abnormalTag(flag?: number) {
  return flag === 0 || flag == null ? 'success' : 'danger'
}

/* ---------------- 开单 ---------------- */

async function searchVisits(keyword: string) {
  visitLoading.value = true
  try {
    const res = await pageVisits({ pageNum: 1, pageSize: 20, keyword, status: 1 })
    visits.value = res.list
  } finally {
    visitLoading.value = false
  }
}

function onVisitChange() {
  // 换就诊记录后不清空已填项目：医生常常同一个检查连开几人
}

function addItem() {
  form.items.push({ itemCode: '', itemName: '' })
}

function removeItem(idx: number) {
  form.items.splice(idx, 1)
}

function openCreate() {
  form.visitId = undefined
  form.examType = 1
  form.examItem = ''
  form.bodyPart = ''
  form.clinicalDiagnosis = ''
  form.items = [{ itemCode: '', itemName: '' }]
  visits.value = []
  createVisible.value = true
}

async function submitCreate() {
  if (form.visitId == null) {
    ElMessage.warning('请先选择就诊记录')
    return
  }
  const items = form.items.filter((i) => i.itemName.trim())
  if (items.length === 0) {
    ElMessage.warning('至少填写一个明细项目')
    return
  }
  submitting.value = true
  try {
    const id = await createExamApply({
      visitId: form.visitId,
      examType: form.examType,
      examItem: form.examItem || undefined,
      bodyPart: form.bodyPart || undefined,
      clinicalDiagnosis: form.clinicalDiagnosis || undefined,
      items: items.map((i, idx) => ({
        itemCode: i.itemCode.trim(),
        itemName: i.itemName.trim(),
        sort: idx,
      })),
    })
    ElMessage.success(`开单成功，申请单 ID ${id}。请提醒患者先到收费窗口缴费`)
    createVisible.value = false
    reload()
  } finally {
    submitting.value = false
  }
}

/* ---------------- 采样 / 条码 ---------------- */

async function doSample(row: ExamApplyVO) {
  const code = await sampleExam({ applyId: Number(row.id) })
  ElMessage.success(`采样成功，条码号 ${code}`)
  load()
}

async function printBarcode(row: ExamApplyVO) {
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
