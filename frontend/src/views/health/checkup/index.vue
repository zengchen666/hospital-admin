<template>
  <div class="hp-page">
    <h3 class="hp-page-title">体检管理</h3>

    <el-tabs v-model="tab" @tab-change="reload">
      <el-tab-pane label="体检登记" name="list" />
      <el-tab-pane label="套餐维护" name="package" />
    </el-tabs>

    <!-- 体检登记 -->
    <template v-if="tab === 'list'">
      <div class="hp-toolbar">
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 150px" @change="reload">
          <el-option v-for="(v, k) in CHECKUP_STATUS_LABEL" :key="k" :label="v" :value="Number(k)" />
        </el-select>
        <el-input
          v-model="query.patientId"
          placeholder="患者 ID"
          clearable
          style="width: 140px"
          @clear="reload"
          @keyup.enter="reload"
        />
        <el-button :icon="Refresh" :loading="loading" @click="reload">查询</el-button>
        <span class="spacer" />
        <el-button v-perm="'health:checkup:add'" type="primary" :icon="Plus" @click="openCreate">
          登记体检
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="checkupNo" label="体检号" width="170" />
        <el-table-column prop="patientName" label="姓名" width="100" />
        <el-table-column label="性别" width="70">
          <template #default="{ row }">{{ row.gender === 1 ? '男' : '女' }}</template>
        </el-table-column>
        <el-table-column prop="age" label="年龄" width="70" />
        <el-table-column prop="packageName" label="套餐" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.packageName || '散查（自选单项）' }}</template>
        </el-table-column>
        <el-table-column label="金额" width="100">
          <template #default="{ row }">¥{{ row.amount }}</template>
        </el-table-column>
        <el-table-column prop="checkupDate" label="体检日期" width="120" />
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">
              {{ CHECKUP_STATUS_LABEL[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="异常项" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.abnormalItems" class="hp-abnormal">{{ row.abnormalItems }}</span>
            <span v-else class="hp-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-perm="'health:checkup:add'"
              link
              type="primary"
              :disabled="row.status !== 0"
              @click="doComplete(row)"
            >
              完成体检
            </el-button>
            <el-button
              v-perm="'health:checkup:report'"
              link
              type="warning"
              :disabled="row.status !== 1"
              @click="openReport(row)"
            >
              出报告
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="hp-muted">暂无体检记录</span>
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
    </template>

    <!-- 套餐维护 -->
    <template v-else>
      <div class="hp-toolbar">
        <el-input
          v-model="pkgKeyword"
          placeholder="套餐名称"
          clearable
          style="width: 160px"
          @clear="loadPackages"
          @keyup.enter="loadPackages"
        />
        <el-button :icon="Refresh" :loading="pkgLoading" @click="loadPackages">查询</el-button>
        <span class="spacer" />
        <el-button v-perm="'health:package:edit'" type="primary" :icon="Plus" @click="openPkgCreate">
          新增套餐
        </el-button>
      </div>

      <el-table v-loading="pkgLoading" :data="packages" border stripe>
        <el-table-column prop="packageName" label="套餐名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="包含项目" min-width="240">
          <template #default="{ row }">
            <span v-if="row.itemNames?.length">{{ row.itemNames.join('、') }}</span>
            <span v-else class="hp-muted">未配置</span>
          </template>
        </el-table-column>
        <el-table-column label="价格" width="110">
          <template #default="{ row }">
            <span class="hp-amount">¥{{ row.price }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-perm="'health:package:edit'" link type="primary" @click="openPkgEdit(row)">
              编辑
            </el-button>
            <el-button
              v-perm="'health:package:edit'"
              link
              :type="row.status === 1 ? 'danger' : 'success'"
              @click="togglePkg(row)"
            >
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="hp-muted">暂无体检套餐</span>
        </template>
      </el-table>
    </template>

    <!-- 体检登记 -->
    <el-dialog v-model="dialogVisible" title="登记体检" width="520px" :close-on-click-modal="false">
      <el-form :model="form" label-width="100px">
        <el-form-item label="体检人" required>
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
        <el-form-item label="体检套餐">
          <el-select v-model="form.packageId" clearable filterable style="width: 100%"
                     placeholder="不选则为散查（自选单项）">
            <el-option
              v-for="p in enabledPackages"
              :key="p.id"
              :label="`${p.packageName} · ¥${p.price}`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="体检日期" required>
          <el-date-picker
            v-model="form.checkupDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">登记</el-button>
      </template>
    </el-dialog>

    <!-- 出报告 -->
    <el-dialog v-model="reportVisible" title="出具体检报告" width="600px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="体检号">
          <span>{{ reporting?.checkupNo }}</span>
        </el-form-item>
        <el-form-item label="健康评估" required>
          <el-input v-model="reportForm.summary" type="textarea" :rows="4" maxlength="1000" show-word-limit />
        </el-form-item>
        <el-form-item label="异常项">
          <el-select
            v-model="abnormalList"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="多选或直接输入，逗号分隔保存"
            style="width: 100%"
          >
            <el-option v-for="a in COMMON_ABNORMAL" :key="a" :label="a" :value="a" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReport">出具报告</el-button>
      </template>
    </el-dialog>

    <!-- 套餐编辑 -->
    <el-dialog v-model="pkgVisible" :title="editingPkg ? '编辑套餐' : '新增套餐'" width="560px">
      <el-form :model="pkgForm" label-width="100px">
        <el-form-item label="套餐名称" required>
          <el-input v-model="pkgForm.packageName" maxlength="100" />
        </el-form-item>
        <el-form-item label="包含项目">
          <el-select v-model="pkgItemIds" multiple filterable placeholder="从价目表选择诊疗项目" style="width: 100%">
            <el-option
              v-for="t in treatItems"
              :key="t.id"
              :label="`${t.itemName} · ¥${t.price}`"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="套餐价" required>
          <el-input-number v-model="pkgForm.price" :min="0" :precision="2" :step="10" />
          <span class="hp-muted" style="margin-left: 8px; font-size: 12px">
            套餐价按打包优惠定，不必等于单项之和
          </span>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="pkgForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pkgVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitPkg">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  changeCheckupPackageStatus,
  completeCheckup,
  createCheckup,
  createCheckupPackage,
  listCheckupPackages,
  pageCheckups,
  publishCheckupReport,
  updateCheckupPackage,
  CHECKUP_STATUS_LABEL,
  type CheckupPackageVO,
  type CheckupVO,
} from '@/api/health'
import { pageTreatItems, type TreatItemVO } from '@/api/finance'
import { pagePatients } from '@/api/patient'
import type { PatientVO } from '@/api/types'

/**
 * 体检管理。
 *
 * <p><b>登记 → 完成体检 → 出报告 三步分开</b>：
 * 登记是预约，完成体检是「项目都做完了」，出报告是「结论写好了」。
 * 合成一步会让「项目没做完就出了报告」这种事发生 —— 而体检报告上的
 * 「未见异常」是拿人命背书的。</p>
 */

const COMMON_ABNORMAL = [
  '血压偏高',
  '血脂异常',
  '血糖偏高',
  '脂肪肝',
  '尿酸偏高',
  '心电图异常',
  '甲状腺结节',
]

const tab = ref<'list' | 'package'>('list')

const rows = ref<CheckupVO[]>([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)

const query = reactive<{ pageNum: number; pageSize: number; status?: number; patientId?: string }>({
  pageNum: 1,
  pageSize: 10,
})

const dialogVisible = ref(false)
const patients = ref<PatientVO[]>([])
const patientLoading = ref(false)
const form = reactive<{ patientId?: number; packageId?: string; checkupDate?: string }>({})

const reportVisible = ref(false)
const reporting = ref<CheckupVO | null>(null)
const abnormalList = ref<string[]>([])
const reportForm = reactive<{ summary: string }>({ summary: '' })

const packages = ref<CheckupPackageVO[]>([])
const pkgLoading = ref(false)
const pkgKeyword = ref('')
const pkgVisible = ref(false)
const editingPkg = ref<CheckupPackageVO | null>(null)
const pkgItemIds = ref<string[]>([])
const pkgForm = reactive<{ packageName: string; price: number; status: number }>({
  packageName: '',
  price: 0,
  status: 1,
})

const treatItems = ref<TreatItemVO[]>([])

const enabledPackages = computed(() => packages.value.filter((p) => p.status === 1))

async function load() {
  loading.value = true
  try {
    const res = await pageCheckups({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      status: query.status,
      patientId: query.patientId ? Number(query.patientId) : undefined,
    })
    rows.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadPackages() {
  pkgLoading.value = true
  try {
    packages.value = await listCheckupPackages(undefined, pkgKeyword.value || undefined)
  } finally {
    pkgLoading.value = false
  }
}

function reload() {
  if (tab.value === 'list') {
    query.pageNum = 1
    load()
  } else {
    loadPackages()
  }
}

function onPageChange(p: number) {
  query.pageNum = p
  load()
}

function onSizeChange(s: number) {
  query.pageSize = s
  reload()
}

function statusTag(s: number) {
  return (['warning', 'primary', 'success'] as const)[s] ?? 'info'
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
  form.packageId = undefined
  form.checkupDate = new Date().toISOString().slice(0, 10)
  patients.value = []
  dialogVisible.value = true
}

async function submit() {
  if (form.patientId == null) {
    ElMessage.warning('请选择体检人')
    return
  }
  if (!form.checkupDate) {
    ElMessage.warning('请选择体检日期')
    return
  }
  submitting.value = true
  try {
    await createCheckup({
      patientId: form.patientId,
      packageId: form.packageId ? Number(form.packageId) : undefined,
      checkupDate: form.checkupDate,
    })
    ElMessage.success('体检登记成功')
    dialogVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function doComplete(row: CheckupVO) {
  await completeCheckup(row.id)
  ElMessage.success('已标记完成体检，待出报告')
  load()
}

function openReport(row: CheckupVO) {
  reporting.value = row
  reportForm.summary = ''
  abnormalList.value = []
  reportVisible.value = true
}

async function submitReport() {
  if (!reporting.value) return
  if (!reportForm.summary.trim()) {
    ElMessage.warning('健康评估结论不能为空')
    return
  }
  submitting.value = true
  try {
    await publishCheckupReport(reporting.value.id, {
      summary: reportForm.summary.trim(),
      abnormalItems: abnormalList.value.join(',') || undefined,
    })
    ElMessage.success('体检报告已出具')
    reportVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

/* ---------------- 套餐 ---------------- */

async function openPkgCreate() {
  editingPkg.value = null
  pkgForm.packageName = ''
  pkgForm.price = 0
  pkgForm.status = 1
  pkgItemIds.value = []
  if (treatItems.value.length === 0) {
    const res = await pageTreatItems({ pageNum: 1, pageSize: 100, onlyEffective: true })
    treatItems.value = res.list
  }
  pkgVisible.value = true
}

async function openPkgEdit(row: CheckupPackageVO) {
  editingPkg.value = row
  pkgForm.packageName = row.packageName
  pkgForm.price = row.price
  pkgForm.status = row.status
  pkgItemIds.value = row.itemIds ? row.itemIds.split(',').filter(Boolean) : []
  if (treatItems.value.length === 0) {
    const res = await pageTreatItems({ pageNum: 1, pageSize: 100, onlyEffective: true })
    treatItems.value = res.list
  }
  pkgVisible.value = true
}

async function submitPkg() {
  if (!pkgForm.packageName.trim()) {
    ElMessage.warning('套餐名称不能为空')
    return
  }
  submitting.value = true
  try {
    const body = {
      packageName: pkgForm.packageName.trim(),
      itemIds: pkgItemIds.value.join(',') || undefined,
      price: pkgForm.price,
      status: pkgForm.status,
    }
    if (editingPkg.value) {
      await updateCheckupPackage(editingPkg.value.id, body)
      ElMessage.success('套餐已更新')
    } else {
      await createCheckupPackage(body)
      ElMessage.success('套餐已新增')
    }
    pkgVisible.value = false
    await loadPackages()
  } finally {
    submitting.value = false
  }
}

async function togglePkg(row: CheckupPackageVO) {
  const next = row.status === 1 ? 0 : 1
  await changeCheckupPackageStatus(row.id, next)
  ElMessage.success(next === 1 ? '套餐已启用' : '套餐已停用')
  await loadPackages()
}

onMounted(async () => {
  await load()
  await loadPackages()
})
</script>

<style scoped>
.hp-amount {
  color: #d93026;
  font-weight: 600;
}

.hp-abnormal {
  color: #d93026;
}
</style>
