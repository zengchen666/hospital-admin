<template>
  <div class="hp-page">
    <h3 class="hp-page-title">报告查询</h3>

    <el-tabs v-model="tab" @tab-change="reload">
      <el-tab-pane label="全部报告" name="all" />
      <el-tab-pane name="critical">
        <template #label>
          危急值待处理
          <el-badge v-if="criticalTotal > 0" :value="criticalTotal" type="danger" style="margin-left: 4px" />
        </template>
      </el-tab-pane>
    </el-tabs>

    <div class="hp-toolbar">
      <el-input
        v-model="query.patientName"
        placeholder="患者姓名"
        clearable
        style="width: 140px"
        @clear="reload"
        @keyup.enter="reload"
      />
      <el-input
        v-model="query.reportNo"
        placeholder="报告号"
        clearable
        style="width: 180px"
        @clear="reload"
        @keyup.enter="reload"
      />
      <el-select
        v-if="tab === 'all'"
        v-model="query.status"
        placeholder="发布状态"
        clearable
        style="width: 120px"
        @change="reload"
      >
        <el-option label="草稿" :value="0" />
        <el-option label="已发布" :value="1" />
      </el-select>
      <el-button :icon="Refresh" :loading="loading" @click="reload">查询</el-button>
    </div>

    <el-alert
      v-if="tab === 'critical'"
      type="error"
      :closable="false"
      style="margin-bottom: 12px"
      title="危急值必须逐条登记处理措施"
      description="处理措施会连同处理人、处理时间一并留档。只标记「已处理」而不写做了什么，事后追责时无从查证。"
    />

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="reportNo" label="报告号" width="180" />
      <el-table-column prop="applyNo" label="申请单号" width="170" />
      <el-table-column prop="patientName" label="患者" width="100" />
      <el-table-column label="结论" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.conclusion || row.result || '—' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'warning'" size="small">
            {{ REPORT_STATUS_LABEL[row.status] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="危急值" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.isCritical === 1" type="danger" size="small">危急</el-tag>
          <span v-else class="hp-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column v-if="tab === 'critical'" label="处理状态" width="110">
        <template #default="{ row }">
          <el-tag :type="row.handleStatus === 1 ? 'success' : 'danger'" size="small">
            {{ row.handleStatus === 1 ? '已处理' : '待处理' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="reporterName" label="录入" width="100" />
      <el-table-column prop="auditorName" label="审核" width="100" />
      <el-table-column prop="reportTime" label="报告时间" width="170" />
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button
            v-if="tab === 'critical'"
            v-perm="'exam:critical:handle'"
            link
            type="warning"
            :disabled="row.handleStatus === 1"
            @click="openHandle(row)"
          >
            登记处理
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">{{ tab === 'critical' ? '暂无待处理的危急值' : '暂无报告' }}</span>
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

    <!-- 报告详情 -->
    <el-dialog v-model="detailVisible" :title="`报告详情 · ${detail?.reportNo ?? ''}`" width="760px">
      <div v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="患者">{{ detail.patientName }}</el-descriptions-item>
          <el-descriptions-item label="申请单号">{{ detail.applyNo }}</el-descriptions-item>
          <el-descriptions-item label="录入者">{{ detail.reporterName }}</el-descriptions-item>
          <el-descriptions-item label="审核者">{{ detail.auditorName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="报告时间">{{ detail.reportTime }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="detail.status === 1 ? 'success' : 'warning'" size="small">
              {{ REPORT_STATUS_LABEL[detail.status] }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="detail.isCritical === 1"
          type="error"
          :closable="false"
          style="margin: 14px 0"
          :title="`危急值 · ${detail.handleStatus === 1 ? '已处理' : '待处理'}`"
          :description="detail.handleRemark || '尚未登记处理措施'"
        />

        <h4 style="margin: 16px 0 8px">检验项目</h4>
        <el-table :data="detail.items" border size="small">
          <el-table-column prop="itemName" label="项目" min-width="150" />
          <el-table-column label="结果" width="140">
            <template #default="{ row }">
              <span :class="{ 'hp-text-danger': (row.abnormalFlag ?? 0) > 0 }">
                {{ row.resultValue || '—' }}{{ row.unit ? ' ' + row.unit : '' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="refRange" label="参考范围" width="130" />
          <el-table-column label="判定" width="100">
            <template #default="{ row }">
              <el-tag :type="(row.abnormalFlag ?? 0) > 0 ? 'danger' : 'success'" size="small">
                {{ ABNORMAL_LABEL[row.abnormalFlag ?? 0] }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>

        <h4 style="margin: 16px 0 8px">结果摘要</h4>
        <div class="hp-block">{{ detail.result || '—' }}</div>
        <h4 style="margin: 16px 0 8px">结论</h4>
        <div class="hp-block">{{ detail.conclusion || '—' }}</div>

        <template v-if="detail.handleStatus === 1">
          <h4 style="margin: 16px 0 8px">危急值处理</h4>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="处理人">{{ detail.handlerName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="处理时间">{{ detail.handleTime || '—' }}</el-descriptions-item>
            <el-descriptions-item label="处理措施" :span="2">
              {{ detail.handleRemark }}
            </el-descriptions-item>
          </el-descriptions>
        </template>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 危急值处理 -->
    <el-dialog v-model="handleVisible" title="危急值处理登记" width="520px">
      <el-form label-width="96px">
        <el-form-item label="报告号">
          <span>{{ handling?.reportNo }}</span>
        </el-form-item>
        <el-form-item label="患者">
          <span>{{ handling?.patientName }}</span>
        </el-form-item>
        <el-form-item label="处理措施" required>
          <el-input
            v-model="handleRemark"
            type="textarea"
            :rows="4"
            maxlength="255"
            show-word-limit
            placeholder="如：已电话通知患者立即返院，给予葡萄糖酸钙静推，复测血钾 3.1mmol/L"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="submitHandle">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  handleCritical,
  pageExamReports,
  ABNORMAL_LABEL,
  REPORT_STATUS_LABEL,
  type ExamReportQuery,
  type ExamReportVO,
} from '@/api/exam'

/**
 * 报告查询与危急值闭环。
 *
 * <p><b>「危急值待处理」单独一个页签</b>：这个队列是临床的「必须看一眼」清单，
 * 混在全部报告里会被翻过去。只列已发布且未处理的 —— 草稿阶段的异常结果还没复核，
 * 不该惊动临床。</p>
 *
 * <p>角标数字单独查一次 pendingCritical：切页签时才刷新，不做轮询，
 * 危急值的实时通知由后端推消息给申请医生，这里只做兜底。</p>
 */

const rows = ref<ExamReportVO[]>([])
const total = ref(0)
const criticalTotal = ref(0)
const loading = ref(false)
const submitting = ref(false)

const tab = ref<'all' | 'critical'>('all')

const query = reactive<ExamReportQuery>({
  pageNum: 1,
  pageSize: 10,
  patientName: undefined,
  reportNo: undefined,
  status: undefined,
})

const detailVisible = ref(false)
const detail = ref<ExamReportVO | null>(null)

const handleVisible = ref(false)
const handling = ref<ExamReportVO | null>(null)
const handleRemark = ref('')

async function load() {
  loading.value = true
  try {
    const params: ExamReportQuery = {
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      patientName: query.patientName || undefined,
      reportNo: query.reportNo || undefined,
    }
    if (tab.value === 'critical') {
      params.pendingCritical = true
    } else if (query.status !== undefined) {
      params.status = query.status
    }
    const res = await pageExamReports(params)
    rows.value = res.list
    total.value = res.total
    if (tab.value === 'critical') {
      criticalTotal.value = res.total
    }
  } finally {
    loading.value = false
  }
}

/** 角标只在初始与处理完成后刷新 */
async function loadCriticalBadge() {
  const res = await pageExamReports({ pageNum: 1, pageSize: 1, pendingCritical: true })
  criticalTotal.value = res.total
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

watch(tab, () => {
  query.status = undefined
  reload()
})

function openDetail(row: ExamReportVO) {
  detail.value = row
  detailVisible.value = true
}

function openHandle(row: ExamReportVO) {
  handling.value = row
  handleRemark.value = ''
  handleVisible.value = true
}

async function submitHandle() {
  if (!handling.value) return
  if (!handleRemark.value.trim()) {
    ElMessage.warning('处理措施不能为空')
    return
  }
  submitting.value = true
  try {
    await handleCritical({ reportId: Number(handling.value.id), remark: handleRemark.value.trim() })
    ElMessage.success('危急值处理已登记')
    handleVisible.value = false
    await load()
    await loadCriticalBadge()
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await load()
  await loadCriticalBadge()
})
</script>

<style scoped>
.hp-block {
  padding: 10px 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  white-space: pre-wrap;
  min-height: 40px;
}

.hp-text-danger {
  color: #d93026;
  font-weight: 600;
}
</style>
