<template>
  <div class="hp-page">
    <h3 class="hp-page-title">医嘱管理</h3>

    <div class="hp-toolbar">
      <el-select
        v-model="admissionId"
        filterable
        placeholder="选择在院患者"
        style="width: 300px"
        @change="loadOrders"
      >
        <el-option
          v-for="a in admissions"
          :key="a.id"
          :label="`${a.admissionNo} · ${a.patientName}（${a.bedNo || '未分床'}）`"
          :value="a.id"
        />
      </el-select>
      <el-button :icon="Refresh" :loading="loading" @click="loadOrders">刷新</el-button>
      <span class="spacer" />
      <el-button
        v-perm="'inpatient:order:add'"
        type="primary"
        :icon="Plus"
        :disabled="!admissionId"
        @click="openCreate"
      >
        开立医嘱
      </el-button>
    </div>

    <el-empty v-if="!admissionId" description="请先选择在院患者" />

    <template v-else>
      <el-table v-loading="loading" :data="orders" border stripe>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="row.orderType === 1 ? 'primary' : 'warning'" size="small">
              {{ row.orderType === 1 ? '长期' : '临时' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="医嘱内容" min-width="240" show-overflow-tooltip />
        <el-table-column prop="dosage" label="用量" width="100" />
        <el-table-column prop="frequency" label="频次" width="90" />
        <el-table-column prop="doctorName" label="开立医生" width="100" />
        <el-table-column prop="startTime" label="开始时间" width="170" />
        <el-table-column prop="stopTime" label="停止时间" width="170" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0"
              v-perm="'inpatient:order:exec'"
              link
              type="success"
              @click="execute(row)"
            >
              执行
            </el-button>
            <el-button
              v-if="row.status !== 2"
              v-perm="'inpatient:order:add'"
              link
              type="danger"
              @click="stop(row)"
            >
              停止
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="hp-muted">该患者暂无医嘱</span>
        </template>
      </el-table>
    </template>

    <el-dialog v-model="formVisible" title="开立医嘱" width="560px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="医嘱类型" prop="orderType">
          <el-radio-group v-model="form.orderType">
            <el-radio-button :value="1">长期医嘱</el-radio-button>
            <el-radio-button :value="2">临时医嘱</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="医嘱内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="如：头孢曲松钠 2g 静脉滴注"
          />
        </el-form-item>
        <el-form-item label="用量">
          <el-input v-model="form.dosage" maxlength="50" style="width: 200px" placeholder="2g" />
        </el-form-item>
        <el-form-item label="频次">
          <el-input v-model="form.frequency" maxlength="20" style="width: 200px" placeholder="qd" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">开立</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Refresh, Plus } from '@element-plus/icons-vue'
import {
  createOrder,
  executeOrder,
  listOrders,
  pageAdmissions,
  stopOrder,
  type AdmissionVO,
  type OrderRequest,
  type OrderVO,
} from '@/api/inpatient'

/**
 * 医嘱管理。
 *
 * <p><b>先选患者再看医嘱</b>：医嘱必须挂在具体的住院记录下，
 * 不做「全部医嘱一张表」的列表 —— 那样的列表对医生没有意义，
 * 他关心的是「我管的这个病人今天要做什么」。</p>
 *
 * <p><b>已停止的医嘱不给「停止」按钮</b>：重复停止会在时间轴上留下第二条
 * 停止记录，护士站对医嘱时会看到两条互相矛盾的停止时间。</p>
 */

const admissions = ref<AdmissionVO[]>([])
const orders = ref<OrderVO[]>([])
const admissionId = ref('')
const loading = ref(false)
const submitting = ref(false)
const formVisible = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<OrderRequest>({
  admissionId: '',
  orderType: 1,
  content: '',
  dosage: '',
  frequency: '',
})

const rules: FormRules = {
  content: [{ required: true, message: '请输入医嘱内容', trigger: 'blur' }],
}

function statusText(v?: number) {
  return ['未执行', '已执行', '已停止'][v ?? 0] ?? '-'
}
function statusTag(v: number): 'info' | 'success' | 'danger' {
  return v === 0 ? 'info' : v === 1 ? 'success' : 'danger'
}

async function loadAdmissions() {
  const res = await pageAdmissions({ status: 0, pageSize: 200 })
  admissions.value = res.list
}

async function loadOrders() {
  if (!admissionId.value) {
    orders.value = []
    return
  }
  loading.value = true
  try {
    orders.value = await listOrders(admissionId.value)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { admissionId: admissionId.value, orderType: 1, content: '', dosage: '', frequency: '' })
  formVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await createOrder(form)
    ElMessage.success('医嘱已开立')
    formVisible.value = false
    loadOrders()
  } finally {
    submitting.value = false
  }
}

async function execute(row: OrderVO) {
  await executeOrder(row.id)
  ElMessage.success('已标记执行')
  loadOrders()
}

async function stop(row: OrderVO) {
  await stopOrder(row.id)
  ElMessage.success('医嘱已停止')
  loadOrders()
}

onMounted(async () => {
  await loadAdmissions()
})
</script>
