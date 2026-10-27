<template>
  <div class="hp-page">
    <h3 class="hp-page-title">排班管理</h3>

    <div class="hp-toolbar">
      <el-date-picker
        v-model="query.startTime"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="开始日期"
        style="width: 160px"
        clearable
        @change="reload"
      />
      <el-date-picker
        v-model="query.endTime"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="结束日期"
        style="width: 160px"
        clearable
        @change="reload"
      />
      <el-select v-model="query.deptId" placeholder="科室" clearable style="width: 140px" @change="reload">
        <el-option v-for="d in depts" :key="d.id" :label="d.deptName" :value="d.id" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="reload">
        <el-option label="正常" :value="1" />
        <el-option label="停诊" :value="0" />
      </el-select>
      <el-button :icon="Refresh" @click="reset">重置</el-button>
      <span class="spacer" />
      <el-button v-perm="'registration:schedule:edit'" type="primary" :icon="Plus" @click="openCreate">
        新增排班
      </el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="doctorName" label="医生" width="100" />
      <el-table-column label="科室" width="120">
        <template #default="{ row }">{{ deptName(row.deptId) }}</template>
      </el-table-column>
      <el-table-column prop="scheduleDate" label="日期" width="120" />
      <el-table-column label="时段" width="80">
        <template #default="{ row }">{{ periodText(row.timePeriod) }}</template>
      </el-table-column>
      <el-table-column label="号别" width="80">
        <template #default="{ row }">
          <el-tag :type="row.regType === 3 ? 'danger' : row.regType === 2 ? 'warning' : 'info'" size="small">
            {{ regTypeText(row.regType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="号源" width="110">
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
            v-if="row.status === 1"
            v-perm="'registration:schedule:edit'"
            link
            type="danger"
            @click="stop(row)"
          >
            停诊
          </el-button>
          <span v-else class="hp-muted" style="font-size: 12px">已停诊</span>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">暂无排班</span>
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

    <el-dialog v-model="formVisible" title="新增排班" width="560px" :close-on-click-modal="false">
      <el-alert
        type="info"
        :closable="false"
        style="margin-bottom: 16px"
        title="同一医生、同一日期、同一时段、同一号别只能排一次"
        description="重复提交会被唯一索引拦下（40904）。需要调整请新增另一个时段或号别。"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="医生" prop="doctorId">
          <el-select v-model="form.doctorId" filterable placeholder="选择医生" style="width: 100%">
            <el-option v-for="u in doctors" :key="u.id" :label="u.realName" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="科室" prop="deptId">
          <el-select v-model="form.deptId" filterable placeholder="选择科室" style="width: 100%">
            <el-option v-for="d in depts" :key="d.id" :label="d.deptName" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期" prop="scheduleDate">
          <el-date-picker
            v-model="form.scheduleDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="时段" prop="timePeriod">
          <el-radio-group v-model="form.timePeriod">
            <el-radio-button :value="1">上午</el-radio-button>
            <el-radio-button :value="2">下午</el-radio-button>
            <el-radio-button :value="3">晚上</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="号别" prop="regType">
          <el-radio-group v-model="form.regType">
            <el-radio-button :value="1">普通</el-radio-button>
            <el-radio-button :value="2">专家</el-radio-button>
            <el-radio-button :value="3">急诊</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="总号源" prop="totalNum">
          <el-input-number v-model="form.totalNum" :min="0" :max="500" />
        </el-form-item>
        <el-form-item label="挂号费" prop="regFee">
          <el-input-number v-model="form.regFee" :min="0" :precision="2" :step="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Refresh, Plus } from '@element-plus/icons-vue'
import { createSchedule, pageSchedules, stopSchedule, type ScheduleQuery, type ScheduleSaveRequest, type ScheduleVO } from '@/api/registration'
import { listDepts, listUserOptions, type DeptOption, type UserOption } from '@/api/user'

/**
 * 排班管理。
 *
 * <p><b>只提供「新增」和「停诊」，不给编辑</b>：排班一旦有人挂号就有号源被占用，
 * 此时改医生或改时段会让已挂患者的就诊信息对不上。
 * 要调整就停诊这一条、另排一条 —— 号源账才对得清。</p>
 */

const rows = ref<ScheduleVO[]>([])
const depts = ref<DeptOption[]>([])
const doctors = ref<UserOption[]>([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)
const formVisible = ref(false)
const formRef = ref<FormInstance>()

const query = reactive<ScheduleQuery>({ pageNum: 1, pageSize: 10 })

const form = reactive<ScheduleSaveRequest>({
  doctorId: '',
  deptId: '',
  scheduleDate: '',
  timePeriod: 1,
  regType: 1,
  totalNum: 30,
  regFee: 10,
})

const rules: FormRules = {
  doctorId: [{ required: true, message: '请选择医生', trigger: 'change' }],
  deptId: [{ required: true, message: '请选择科室', trigger: 'change' }],
  scheduleDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
}

function deptName(id?: number) {
  return depts.value.find((d) => d.id === String(id))?.deptName ?? '-'
}
function periodText(v?: number) {
  return ['', '上午', '下午', '晚上'][v ?? 0] ?? '-'
}
function regTypeText(v?: number) {
  return ['', '普通', '专家', '急诊'][v ?? 0] ?? '-'
}

async function load() {
  loading.value = true
  try {
    const res = await pageSchedules(query)
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

function reset() {
  query.startTime = undefined
  query.endTime = undefined
  query.deptId = undefined
  query.status = undefined
  reload()
}

function onPageChange(p: number) {
  query.pageNum = p
  load()
}

function onSizeChange(s: number) {
  query.pageSize = s
  reload()
}

function openCreate() {
  Object.assign(form, {
    doctorId: '',
    deptId: '',
    scheduleDate: '',
    timePeriod: 1,
    regType: 1,
    totalNum: 30,
    regFee: 10,
  })
  formVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await createSchedule(form)
    ElMessage.success('排班已创建')
    formVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function stop(row: ScheduleVO) {
  try {
    await ElMessageBox.confirm(
      `停诊后该时段不能再挂号，已挂号的患者需要另行安排。确定停诊「${row.doctorName} ${row.scheduleDate} ${periodText(row.timePeriod)}」吗？`,
      '停诊确认',
      { type: 'warning', confirmButtonText: '停诊', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await stopSchedule(row.id)
  ElMessage.success('已停诊')
  load()
}

onMounted(async () => {
  try {
    depts.value = await listDepts()
    doctors.value = await listUserOptions('doctor')
  } catch {
    depts.value = []
    doctors.value = []
  }
  await load()
})
</script>

<style scoped>
.remain-zero {
  color: #dc2626;
  font-weight: 600;
}
</style>
