<template>
  <div class="hp-page">
    <h3 class="hp-page-title">现场挂号</h3>

    <el-card shadow="never" style="margin-bottom: 12px">
      <el-form :model="form" label-width="90px" inline>
        <el-form-item label="挂号日期">
          <el-date-picker
            v-model="query.startTime"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width: 160px"
            @change="loadSchedules"
          />
        </el-form-item>
        <el-form-item label="科室">
          <el-select
            v-model="query.deptId"
            placeholder="全部科室"
            clearable
            style="width: 150px"
            @change="loadSchedules"
          >
            <el-option v-for="d in depts" :key="d.id" :label="d.deptName" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="号别">
          <el-select
            v-model="query.regType"
            placeholder="全部"
            clearable
            style="width: 120px"
            @change="loadSchedules"
          >
            <el-option label="普通" :value="1" />
            <el-option label="专家" :value="2" />
            <el-option label="急诊" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="query.onlyAvailable" @change="loadSchedules">
            只看有号
          </el-checkbox>
        </el-form-item>
      </el-form>
    </el-card>

    <el-table
      v-loading="loading"
      :data="schedules"
      border
      stripe
      highlight-current-row
      @current-change="onPick"
    >
      <el-table-column prop="doctorName" label="医生" width="100" />
      <el-table-column label="科室" width="110">
        <template #default="{ row }">{{ deptName(row.deptId) }}</template>
      </el-table-column>
      <el-table-column prop="scheduleDate" label="日期" width="120" />
      <el-table-column label="时段" width="80">
        <template #default="{ row }">{{ periodText(row.timePeriod) }}</template>
      </el-table-column>
      <el-table-column label="号别" width="80">
        <template #default="{ row }">
          <el-tag :type="regTypeTag(row.regType)" size="small">
            {{ regTypeText(row.regType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="剩余号源" width="100">
        <template #default="{ row }">
          <span :class="{ 'remain-zero': row.remainNum <= 0 }">
            {{ row.remainNum }} / {{ row.totalNum }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="挂号费" width="90">
        <template #default="{ row }">¥{{ row.regFee }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '正常' : '停诊' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button
            type="primary"
            link
            :disabled="row.status !== 1 || row.remainNum <= 0"
            @click="openRegister(row)"
          >
            挂号
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">该条件下没有排班</span>
      </template>
    </el-table>

    <el-pagination
      style="margin-top: 12px; justify-content: flex-end"
      layout="total, prev, pager, next"
      :total="total"
      :current-page="query.pageNum"
      :page-size="query.pageSize"
      @current-change="onPageChange"
    />

    <!-- 挂号确认 -->
    <el-dialog v-model="regVisible" title="确认挂号" width="520px" :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="排班信息">
          <span>
            {{ picked?.doctorName }} · {{ deptName(picked?.deptId) }} ·
            {{ picked?.scheduleDate }} {{ periodText(picked?.timePeriod) }} ·
            {{ regTypeText(picked?.regType) }} · ¥{{ picked?.regFee }}
          </span>
        </el-form-item>
        <el-form-item label="患者" required>
          <el-select
            v-model="form.patientId"
            filterable
            remote
            reserve-keyword
            placeholder="输入姓名 / 患者编号 / 手机号搜索"
            :remote-method="searchPatients"
            :loading="searching"
            style="width: 100%"
          >
            <el-option
              v-for="p in patients"
              :key="p.id"
              :label="`${p.name}（${p.patientNo}）`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="regVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确认挂号</el-button>
      </template>
    </el-dialog>

    <!-- 挂号回执 -->
    <el-dialog v-model="receiptVisible" title="挂号成功" width="420px">
      <el-descriptions v-if="receipt" :column="1" border>
        <el-descriptions-item label="挂号单号">{{ receipt.regNo }}</el-descriptions-item>
        <el-descriptions-item label="排队号">
          <span class="queue-no">{{ receipt.queueNo }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="挂号费">¥{{ receipt.regFee }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="finish">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  pageSchedules,
  register,
  type RegistrationResult,
  type ScheduleQuery,
  type ScheduleVO,
} from '@/api/registration'
import { listDepts, type DeptOption } from '@/api/user'
import { pagePatients } from '@/api/patient'
import type { PatientVO } from '@/api/types'

/**
 * 现场挂号。
 *
 * <p><b>挂号费不显示在输入框里</b>：费用与号别由排班决定，前端传了后端也会忽略。
 * 把价格做成可编辑的输入框，是在诱导用户相信它能改。</p>
 *
 * <p>「只看有号」默认打开：挂不上号的排班列出来只会让人白点一次。</p>
 */

const schedules = ref<ScheduleVO[]>([])
const depts = ref<DeptOption[]>([])
const patients = ref<PatientVO[]>([])
const total = ref(0)
const loading = ref(false)
const searching = ref(false)
const submitting = ref(false)
const regVisible = ref(false)
const receiptVisible = ref(false)
const picked = ref<ScheduleVO | null>(null)
const receipt = ref<RegistrationResult | null>(null)

const query = reactive<ScheduleQuery>({
  pageNum: 1,
  pageSize: 10,
  startTime: new Date().toISOString().slice(0, 10),
  onlyAvailable: true,
})

const form = reactive<{ patientId: string }>({ patientId: '' })

function deptName(id?: number) {
  return depts.value.find((d) => d.id === String(id))?.deptName ?? '-'
}
function periodText(v?: number) {
  return ['', '上午', '下午', '晚上'][v ?? 0] ?? '-'
}
function regTypeText(v?: number) {
  return ['', '普通', '专家', '急诊'][v ?? 0] ?? '-'
}
function regTypeTag(v: number): 'info' | 'warning' | 'danger' {
  return v === 3 ? 'danger' : v === 2 ? 'warning' : 'info'
}

async function loadSchedules() {
  loading.value = true
  try {
    const res = await pageSchedules(query)
    schedules.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function onPageChange(p: number) {
  query.pageNum = p
  loadSchedules()
}

function onPick(row: ScheduleVO | null) {
  picked.value = row
}

function openRegister(row: ScheduleVO) {
  picked.value = row
  form.patientId = ''
  patients.value = []
  regVisible.value = true
}

async function searchPatients(keyword: string) {
  if (!keyword) {
    patients.value = []
    return
  }
  searching.value = true
  try {
    const res = await pagePatients({ pageNum: 1, pageSize: 20, keyword })
    patients.value = res.list
  } finally {
    searching.value = false
  }
}

async function submit() {
  if (!form.patientId) {
    ElMessage.warning('请选择患者')
    return
  }
  if (!picked.value) {
    return
  }
  submitting.value = true
  try {
    receipt.value = await register({
      patientId: form.patientId,
      scheduleId: picked.value.id,
    })
    regVisible.value = false
    receiptVisible.value = true
    loadSchedules()
  } finally {
    submitting.value = false
  }
}

function finish() {
  receiptVisible.value = false
  receipt.value = null
}

onMounted(async () => {
  try {
    depts.value = await listDepts()
  } catch {
    depts.value = []
  }
  await loadSchedules()
})
</script>

<style scoped>
.remain-zero {
  color: #dc2626;
  font-weight: 600;
}

.queue-no {
  font-size: 22px;
  font-weight: 700;
  color: #2563eb;
}
</style>
