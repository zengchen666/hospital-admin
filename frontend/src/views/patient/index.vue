<template>
  <div class="hp-page">
    <h3 class="hp-page-title">患者档案</h3>

    <div class="hp-toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="姓名 / 患者编号 / 手机号"
        clearable
        style="width: 240px"
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-select v-model="query.gender" placeholder="性别" clearable style="width: 120px" @change="reload">
        <el-option label="女" :value="0" />
        <el-option label="男" :value="1" />
        <el-option label="未知" :value="2" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="reload">查询</el-button>
      <el-button :icon="Refresh" @click="reset">重置</el-button>
      <span class="spacer" />
      <el-button v-perm="'patient:add'" type="primary" :icon="Plus" @click="openCreate">
        建档
      </el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="patientNo" label="患者编号" width="120" />
      <el-table-column prop="name" label="姓名" width="100" />
      <el-table-column label="性别" width="70">
        <template #default="{ row }">{{ genderText(row.gender) }}</template>
      </el-table-column>
      <el-table-column prop="age" label="年龄" width="70" />
      <el-table-column prop="idCard" label="身份证" width="190" />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column prop="address" label="住址" min-width="160" show-overflow-tooltip />
      <el-table-column prop="createTime" label="建档时间" width="170" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button v-perm="'patient:edit'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="info" @click="openDetail(row)">详情</el-button>
          <el-button
            v-perm="'patient:decrypt'"
            link
            type="warning"
            @click="viewPlain(row)"
          >
            明文
          </el-button>
          <el-button v-perm="'patient:delete'" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">暂无患者数据</span>
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

    <!-- 建档 / 编辑 -->
    <el-dialog v-model="formVisible" :title="editing ? '编辑患者' : '患者建档'" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" maxlength="50" />
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-radio-group v-model="form.gender">
            <el-radio :value="0">女</el-radio>
            <el-radio :value="1">男</el-radio>
            <el-radio :value="2">未知</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="出生日期">
          <el-date-picker v-model="form.birthDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="身份证" prop="idCard">
          <el-input v-model="form.idCard" maxlength="18" :placeholder="sensitivePlaceholder" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" maxlength="11" :placeholder="sensitivePlaceholder" />
        </el-form-item>
        <el-form-item label="住址">
          <el-input v-model="form.address" maxlength="255" />
        </el-form-item>
        <el-form-item label="过敏史">
          <el-input v-model="form.allergyHistory" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" title="患者详情" width="620px">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="患者编号">{{ detail.patientNo }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ detail.name }}</el-descriptions-item>
        <el-descriptions-item label="性别">{{ genderText(detail.gender) }}</el-descriptions-item>
        <el-descriptions-item label="年龄">{{ detail.age ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="身份证">{{ detail.idCard }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detail.phone }}</el-descriptions-item>
        <el-descriptions-item label="血型">{{ detail.bloodType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="就诊卡">{{ detail.cardNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="过敏史" :span="2">
          <span style="color: #dc2626">{{ detail.allergyHistory || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="既往病史" :span="2">
          {{ detail.medicalHistory || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="家族史" :span="2">
          {{ detail.familyHistory || '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import {
  pagePatients,
  createPatient,
  updatePatient,
  deletePatient,
  getPatientPlain,
  checkIdCard,
  type PatientQuery,
  type PatientSaveRequest,
} from '@/api/patient'
import type { PatientVO } from '@/api/types'

/**
 * 患者档案列表。
 *
 * <p>两个刻意的处理：</p>
 * <ol>
 *   <li>列表里的身份证/手机号是后端<b>已经脱敏</b>的，前端不做二次加工 ——
 *       脱敏规则必须只有一处，否则迟早出现「列表脱敏了但导出没有」；</li>
 *   <li>「明文」按钮单独权限 {@code patient:decrypt}，点一次就在审计日志留一条，
 *       谁看了谁的身份证可追溯。</li>
 * </ol>
 */

const rows = ref<PatientVO[]>([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)
const formVisible = ref(false)
const detailVisible = ref(false)
const editing = ref(false)
const currentId = ref<string>('')
const detail = ref<PatientVO | null>(null)
const formRef = ref<FormInstance>()

const query = reactive<PatientQuery>({ pageNum: 1, pageSize: 10, keyword: '', gender: undefined })

const form = reactive<PatientSaveRequest>({
  name: '',
  gender: 1,
  birthDate: '',
  idCard: '',
  phone: '',
  address: '',
  allergyHistory: '',
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  idCard: [
    { pattern: /^$|^[1-9]\d{16}[\dXx]$/, message: '身份证号格式不正确', trigger: 'blur' },
  ],
  phone: [{ pattern: /^$|^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
}

function genderText(gender: number) {
  return ['女', '男', '未知'][gender] ?? '未知'
}

async function load() {
  loading.value = true
  try {
    const res = await pagePatients(query)
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
  query.keyword = ''
  query.gender = undefined
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
  editing.value = false
  currentId.value = ''
  Object.assign(form, {
    name: '',
    gender: 1,
    birthDate: '',
    idCard: '',
    phone: '',
    address: '',
    allergyHistory: '',
  })
  formVisible.value = true
}

function openEdit(row: PatientVO) {
  editing.value = true
  currentId.value = row.id
  Object.assign(form, {
    name: row.name,
    gender: row.gender,
    birthDate: row.birthDate ?? '',
    // 列表/详情返回的是脱敏值（4403****1234），绝不能回填进编辑框 ——
    // 一保存就会把星号写进库。留空即可：后端对身份证/手机号是「不传即不改」，
    // 改住址不会把真实证件号连 hash 一起抹掉。
    idCard: '',
    phone: '',
    address: row.address ?? '',
    allergyHistory: row.allergyHistory ?? '',
  })
  formVisible.value = true
}

/** 编辑态下身份证/手机号留空表示不修改，必须提示清楚，否则用户以为清空了 */
const sensitivePlaceholder = computed(() => (editing.value ? '留空表示不修改' : '18 位，末位可为 X'))

function openDetail(row: PatientVO) {
  detail.value = row
  detailVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  // 身份证查重：提前提示比让后端抛 40904 友好
  if (form.idCard && form.idCard.indexOf('*') < 0) {
    try {
      const exists = await checkIdCard(form.idCard)
      if (exists && !editing.value) {
        ElMessage.warning('该身份证号已建档，请勿重复建档')
        return
      }
    } catch {
      // 没有 patient:add 之外的权限时忽略，交给后端兜底
    }
  }

  submitting.value = true
  try {
    if (editing.value) {
      await updatePatient(currentId.value, form)
      ElMessage.success('修改成功')
    } else {
      await createPatient(form)
      ElMessage.success('建档成功')
    }
    formVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function remove(row: PatientVO) {
  try {
    await ElMessageBox.confirm(`确定删除患者「${row.name}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  await deletePatient(row.id)
  ElMessage.success('已删除')
  load()
}

async function viewPlain(row: PatientVO) {
  try {
    await ElMessageBox.confirm(
      '查看明文身份证与手机号会被记录到审计日志，确定继续吗？',
      '敏感信息',
      { type: 'warning' },
    )
  } catch {
    return
  }
  const plain = await getPatientPlain(row.id)
  ElMessageBox.alert(
    `身份证：${plain.idCard}\n手机号：${plain.phone}`,
    `${row.name} 的明文信息`,
    { confirmButtonText: '知道了' },
  )
}

onMounted(load)
</script>
