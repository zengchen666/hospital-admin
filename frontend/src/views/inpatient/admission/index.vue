<template>
  <div class="hp-page">
    <h3 class="hp-page-title">入院登记</h3>

    <div class="hp-toolbar">
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="reload">
        <el-option label="在院" :value="0" />
        <el-option label="出院" :value="1" />
        <el-option label="转科" :value="2" />
      </el-select>
      <el-select v-model="query.deptId" placeholder="科室" clearable style="width: 140px" @change="reload">
        <el-option v-for="d in depts" :key="d.id" :label="d.deptName" :value="d.id" />
      </el-select>
      <el-button :icon="Refresh" @click="reload">刷新</el-button>
      <span class="spacer" />
      <el-button v-perm="'inpatient:admission:add'" type="primary" :icon="Plus" @click="openCreate">
        办理入院
      </el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="admissionNo" label="住院号" width="160" />
      <el-table-column prop="patientName" label="患者" width="100" />
      <el-table-column prop="patientNo" label="患者编号" width="110" />
      <el-table-column label="床位" width="110">
        <template #default="{ row }">
          <span v-if="row.bedNo">{{ row.bedNo }}</span>
          <el-tag v-else type="warning" size="small">未分床</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="wardName" label="病区" width="110" />
      <el-table-column prop="doctorName" label="主治医生" width="100" />
      <el-table-column prop="diagnosis" label="入院诊断" min-width="160" show-overflow-tooltip />
      <el-table-column label="预交金" width="100">
        <template #default="{ row }">¥{{ row.deposit ?? 0 }}</template>
      </el-table-column>
      <el-table-column label="住院天数" width="90">
        <template #default="{ row }">{{ row.stayDays ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'info'" size="small">
            {{ statusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="290" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 0 && !row.bedId"
            v-perm="'inpatient:admission:add'"
            link
            type="warning"
            @click="openAssign(row)"
          >
            分床
          </el-button>
          <el-button
            v-if="row.status === 0"
            v-perm="'inpatient:admission:add'"
            link
            type="primary"
            @click="openDeposit(row)"
          >
            缴预交金
          </el-button>
          <el-button
            v-if="row.status === 0"
            v-perm="'inpatient:admission:add'"
            link
            type="warning"
            @click="openTransfer(row)"
          >
            转科
          </el-button>
          <el-button
            v-if="row.status === 0"
            v-perm="'inpatient:admission:add'"
            link
            type="danger"
            @click="doDischarge(row)"
          >
            出院
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">暂无住院记录</span>
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

    <!-- 办理入院 -->
    <el-dialog v-model="formVisible" title="办理入院" width="560px" :close-on-click-modal="false">
      <el-alert
        type="info"
        :closable="false"
        style="margin-bottom: 16px"
        title="可以先登记后分床"
        description="急诊入院时床位常常还没腾出来。分床是并发争抢的焦点，请走列表里的「分床」按钮单独操作。"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="患者" prop="patientId">
          <el-select
            v-model="form.patientId"
            filterable
            remote
            reserve-keyword
            placeholder="输入姓名 / 编号 / 手机号搜索"
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
        <el-form-item label="科室" prop="deptId">
          <el-select v-model="form.deptId" filterable placeholder="选择科室" style="width: 100%">
            <el-option v-for="d in depts" :key="d.id" :label="d.deptName" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="主治医生" prop="doctorId">
          <el-select v-model="form.doctorId" filterable placeholder="选择医生" style="width: 100%">
            <el-option v-for="u in doctors" :key="u.id" :label="u.realName" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="入院诊断">
          <el-input v-model="form.diagnosis" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="预交金">
          <el-input-number v-model="form.deposit" :min="0" :precision="2" :step="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">办理入院</el-button>
      </template>
    </el-dialog>

    <!-- 分床 -->
    <el-dialog v-model="bedVisible" title="分配床位" width="460px">
      <el-form label-width="80px">
        <el-form-item label="患者">{{ current?.patientName }}</el-form-item>
        <el-form-item label="病区">
          <el-select
            v-model="bedWardId"
            clearable
            placeholder="全部病区"
            style="width: 100%"
            @change="loadFreeBeds"
          >
            <el-option
              v-for="w in wards"
              :key="w.id"
              :label="`${w.wardName}${w.floor ? ' · ' + w.floor : ''}`"
              :value="String(w.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="床位" required>
          <el-select v-model="bedId" filterable placeholder="只显示空闲床位" style="width: 100%">
            <el-option
              v-for="b in freeBeds"
              :key="b.id"
              :label="`${b.bedNo}（${b.wardName} · ${bedTypeText(b.bedType)} · ¥${b.price}/天）`"
              :value="b.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bedVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitBed">确定分床</el-button>
      </template>
    </el-dialog>

    <!-- 转科 -->
    <el-dialog v-model="transferVisible" title="转科" width="480px" :close-on-click-modal="false">
      <el-alert
        type="warning"
        :closable="false"
        style="margin-bottom: 16px"
        title="转科会释放当前床位"
        description="新病区的床位由对方护士重新分配。住院号保持不变，转科不是出院。"
      />
      <el-form label-width="90px">
        <el-form-item label="患者">{{ current?.patientName }}</el-form-item>
        <el-form-item label="目标科室" required>
          <el-select v-model="transferDeptId" filterable placeholder="选择目标科室" style="width: 100%">
            <el-option v-for="d in depts" :key="d.id" :label="d.deptName" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="接收医生">
          <el-select v-model="transferDoctorId" filterable clearable placeholder="可稍后分配" style="width: 100%">
            <el-option v-for="u in doctors" :key="u.id" :label="u.realName" :value="u.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitTransfer">确认转科</el-button>
      </template>
    </el-dialog>

    <!-- 预交金 -->
    <el-dialog v-model="depositVisible" title="缴纳预交金" width="400px">
      <el-form label-width="80px">
        <el-form-item label="患者">{{ current?.patientName }}</el-form-item>
        <el-form-item label="当前余额">¥{{ current?.deposit ?? 0 }}</el-form-item>
        <el-form-item label="金额" required>
          <el-input-number v-model="depositAmount" :min="0.01" :precision="2" :step="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="depositVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitDeposit">确认充值</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Refresh, Plus } from '@element-plus/icons-vue'
import {
  addDeposit,
  admit,
  assignBed,
  discharge,
  listAvailableBeds,
  listWards,
  pageAdmissions,
  transfer,
  type AdmissionQuery,
  type AdmissionRequest,
  type AdmissionVO,
  type BedVO,
  type WardVO,
} from '@/api/inpatient'
import { listDepts, listUserOptions, type DeptOption, type UserOption } from '@/api/user'
import { pagePatients } from '@/api/patient'
import type { PatientVO } from '@/api/types'

/**
 * 入院登记。
 *
 * <p><b>分床是独立动作，不在入院表单里</b>：床位分配是并发争抢的焦点，
 * 两个住院患者同时被分到同一张床，必须给出明确的冲突提示，
 * 混在入院表单里只会得到一个含糊的「保存失败」。</p>
 *
 * <p><b>预交金金额不提供负数</b>：退款走退费流程，靠「充负数」把钱退出去
 * 会让流水说不清。</p>
 */

const rows = ref<AdmissionVO[]>([])
const depts = ref<DeptOption[]>([])
const doctors = ref<UserOption[]>([])
const patients = ref<PatientVO[]>([])
const freeBeds = ref<BedVO[]>([])
const wards = ref<WardVO[]>([])
const bedWardId = ref('')
const total = ref(0)
const loading = ref(false)
const searching = ref(false)
const submitting = ref(false)
const formVisible = ref(false)
const bedVisible = ref(false)
const depositVisible = ref(false)
const transferVisible = ref(false)
const transferDeptId = ref('')
const transferDoctorId = ref('')
const current = ref<AdmissionVO | null>(null)
const bedId = ref('')
const depositAmount = ref(1000)
const formRef = ref<FormInstance>()

const query = reactive<AdmissionQuery>({ pageNum: 1, pageSize: 10 })

const form = reactive<AdmissionRequest>({
  patientId: '',
  deptId: '',
  doctorId: '',
  diagnosis: '',
  deposit: 1000,
})

const rules: FormRules = {
  patientId: [{ required: true, message: '请选择患者', trigger: 'change' }],
  deptId: [{ required: true, message: '请选择科室', trigger: 'change' }],
  doctorId: [{ required: true, message: '请选择主治医生', trigger: 'change' }],
}

function statusText(v?: number) {
  return ['在院', '出院', '转科'][v ?? 0] ?? '-'
}
function bedTypeText(v?: number) {
  return ['', '普通', '重症', 'VIP'][v ?? 0] ?? '-'
}

async function load() {
  loading.value = true
  try {
    const res = await pageAdmissions(query)
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

function openCreate() {
  Object.assign(form, { patientId: '', deptId: '', doctorId: '', diagnosis: '', deposit: 1000 })
  patients.value = []
  formVisible.value = true
}

async function searchPatients(kw: string) {
  if (!kw) {
    patients.value = []
    return
  }
  searching.value = true
  try {
    patients.value = (await pagePatients({ pageNum: 1, pageSize: 20, keyword: kw })).list
  } finally {
    searching.value = false
  }
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await admit(form)
    ElMessage.success('入院登记成功')
    formVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function loadFreeBeds() {
  freeBeds.value = await listAvailableBeds(bedWardId.value || undefined)
}

async function openAssign(row: AdmissionVO) {
  current.value = row
  bedId.value = ''
  bedWardId.value = ''
  if (!wards.value.length) {
    wards.value = await listWards()
  }
  await loadFreeBeds()
  bedVisible.value = true
}

async function openTransfer(row: AdmissionVO) {
  current.value = row
  transferDeptId.value = ''
  transferDoctorId.value = ''
  transferVisible.value = true
}

async function submitTransfer() {
  if (!current.value || !transferDeptId.value) {
    ElMessage.warning('请选择目标科室')
    return
  }
  submitting.value = true
  try {
    await transfer(current.value.id, transferDeptId.value, transferDoctorId.value || undefined)
    ElMessage.success('转科成功，请为新病区重新分床')
    transferVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function submitBed() {
  if (!current.value || !bedId.value) {
    ElMessage.warning('请选择床位')
    return
  }
  submitting.value = true
  try {
    await assignBed(current.value.id, bedId.value)
    ElMessage.success('分床成功')
    bedVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

function openDeposit(row: AdmissionVO) {
  current.value = row
  depositAmount.value = 1000
  depositVisible.value = true
}

async function submitDeposit() {
  if (!current.value) return
  submitting.value = true
  try {
    await addDeposit(current.value.id, depositAmount.value)
    ElMessage.success('预交金已充值')
    depositVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function doDischarge(row: AdmissionVO) {
  try {
    await ElMessageBox.confirm(
      `确定为「${row.patientName}」办理出院吗？出院后床位将释放。`,
      '出院确认',
      { type: 'warning', confirmButtonText: '办理出院', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await discharge(row.id)
  ElMessage.success('已办理出院')
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
