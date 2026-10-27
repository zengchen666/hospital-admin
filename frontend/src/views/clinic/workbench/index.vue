<template>
  <div class="hp-page">
    <h3 class="hp-page-title">医生工作台</h3>

    <div class="hp-toolbar">
      <el-radio-group v-model="statusTab" @change="reload">
        <el-radio-button :value="0">待就诊</el-radio-button>
        <el-radio-button :value="1">就诊中</el-radio-button>
        <el-radio-button :value="2">已完成</el-radio-button>
      </el-radio-group>
      <el-input
        v-model="keyword"
        placeholder="患者姓名 / 编号"
        clearable
        style="width: 200px"
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-button :icon="Refresh" @click="reload">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column label="排队号" width="80">
        <template #default="{ row }">
          <span class="queue-no">{{ row.queueNo }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="regNo" label="挂号单号" width="170" />
      <el-table-column prop="patientName" label="患者" width="100" />
      <el-table-column prop="patientNo" label="患者编号" width="120" />
      <el-table-column label="号别" width="80">
        <template #default="{ row }">
          <el-tag :type="row.regType === 3 ? 'danger' : row.regType === 2 ? 'warning' : 'info'" size="small">
            {{ regTypeText(row.regType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="regTime" label="挂号时间" width="170" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 0"
            v-perm="'clinic:receive'"
            type="primary"
            size="small"
            @click="receive(row)"
          >
            接诊
          </el-button>
          <el-button v-else link type="primary" @click="openRecord(row)">查看病历</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">当前没有患者</span>
      </template>
    </el-table>

    <el-pagination
      style="margin-top: 12px; justify-content: flex-end"
      layout="total, prev, pager, next"
      :total="total"
      :current-page="pageNum"
      :page-size="pageSize"
      @current-change="onPageChange"
    />

    <!-- 接诊工作区 -->
    <el-drawer v-model="drawer" :title="drawerTitle" size="62%" :close-on-click-modal="false">
      <el-alert
        v-if="current?.allergyHistory"
        type="error"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
        title="过敏史"
        :description="current.allergyHistory"
      />
      <el-alert
        v-else
        type="success"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
        title="无已知过敏史"
      />

      <el-descriptions v-if="current" :column="3" border style="margin-bottom: 16px">
        <el-descriptions-item label="患者">{{ current.patientName }}</el-descriptions-item>
        <el-descriptions-item label="编号">{{ current.patientNo }}</el-descriptions-item>
        <el-descriptions-item label="性别">{{ genderText(current.gender) }}</el-descriptions-item>
        <el-descriptions-item label="年龄">{{ current.age ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="挂号单">{{ current.regNo }}</el-descriptions-item>
        <el-descriptions-item label="排队号">{{ current.queueNo }}</el-descriptions-item>
      </el-descriptions>

      <el-tabs v-model="activeTab">
        <!-- 病历 -->
        <el-tab-pane label="病历书写" name="record">
          <el-form :model="recordForm" label-width="90px">
            <el-form-item label="主诉">
              <el-input v-model="recordForm.chiefComplaint" type="textarea" :rows="2" maxlength="500" show-word-limit />
            </el-form-item>
            <el-form-item label="现病史">
              <el-input v-model="recordForm.presentIllness" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="体格检查">
              <el-input v-model="recordForm.physicalExam" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="诊断">
              <el-input v-model="recordForm.diagnosis" maxlength="500" show-word-limit />
            </el-form-item>
            <el-form-item label="ICD 编码">
              <el-input v-model="recordForm.icdCode" maxlength="20" style="width: 220px" />
            </el-form-item>
            <el-form-item>
              <el-button :loading="saving" @click="saveRecord(false)">存草稿</el-button>
              <el-button type="primary" :loading="saving" @click="saveRecord(true)">
                提交病历
              </el-button>
              <span class="hp-muted" style="margin-left: 12px; font-size: 12px">
                提交后不可再修改
              </span>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 开处方 -->
        <el-tab-pane label="开处方" name="presc">
          <el-form :model="prescForm" label-width="90px">
            <el-form-item label="处方类型">
              <el-radio-group v-model="prescForm.prescType">
                <el-radio-button :value="1">西药</el-radio-button>
                <el-radio-button :value="2">中药</el-radio-button>
                <el-radio-button :value="3">中成药</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="诊断">
              <el-input v-model="prescForm.diagnosis" maxlength="500" style="width: 400px" />
            </el-form-item>
          </el-form>

          <el-table :data="prescForm.items" border size="small" style="margin-bottom: 8px">
            <el-table-column label="药品" min-width="220">
              <template #default="{ row, $index }">
                <el-select
                  v-model="row.drugId"
                  filterable
                  remote
                  reserve-keyword
                  placeholder="输入药品名称搜索"
                  :remote-method="searchDrugs"
                  :loading="drugLoading"
                  style="width: 100%"
                  @change="onDrugChange(row, $index)"
                >
                  <el-option
                    v-for="d in drugs"
                    :key="d.id"
                    :label="`${d.drugName} ${d.spec}（¥${d.salePrice}/${d.unit}）`"
                    :value="d.id"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="用法" width="120">
              <template #default="{ row }">
                <el-input v-model="row.usage" placeholder="口服" />
              </template>
            </el-table-column>
            <el-table-column label="用量" width="100">
              <template #default="{ row }">
                <el-input v-model="row.dosage" placeholder="1片" />
              </template>
            </el-table-column>
            <el-table-column label="频次" width="100">
              <template #default="{ row }">
                <el-input v-model="row.frequency" placeholder="tid" />
              </template>
            </el-table-column>
            <el-table-column label="天数" width="80">
              <template #default="{ row }">
                <el-input-number v-model="row.days" :min="1" :max="365" size="small" controls-position="right" style="width: 70px" />
              </template>
            </el-table-column>
            <el-table-column label="数量" width="90">
              <template #default="{ row }">
                <el-input-number v-model="row.quantity" :min="1" :max="9999" size="small" controls-position="right" style="width: 80px" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="60">
              <template #default="{ $index }">
                <el-button link type="danger" @click="removeItem($index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div style="margin-bottom: 12px">
            <el-button :icon="Plus" size="small" @click="addItem">添加药品</el-button>
          </div>

          <el-alert
            v-if="specialSelected"
            type="warning"
            :closable="false"
            show-icon
            style="margin-bottom: 12px"
            title="处方中含麻精毒麻药品"
            description="麻精药品需另行登记与专册管理，请确认处方权与用量符合规定。"
          />

          <el-button type="primary" :loading="submitting" @click="submitPresc">
            开具处方
          </el-button>
          <span class="hp-muted" style="margin-left: 12px; font-size: 12px">
            药品单价与金额由服务端按药品目录计算，不可在此修改
          </span>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Plus } from '@element-plus/icons-vue'
import {
  createPrescription,
  drugOptions,
  getRecord,
  pageVisits,
  receiveVisit,
  saveRecord as saveRecordApi,
  type DrugOption,
  type ReceiveResult,
  type VisitVO,
} from '@/api/clinic'

/**
 * 医生工作台。
 *
 * <p><b>过敏史放在抽屉最顶部且用红色告警</b>：开方前最该看的就是这一项。
 * 把它塞进患者信息表格里，等于默认医生会逐格看完 —— 实际上不会。</p>
 *
 * <p><b>药品价格不出现在界面上</b>（除选药下拉的提示）：金额由服务端算。
 * 给一个能改的价格框，就是在告诉医生「这个可以商量」。</p>
 */

const rows = ref<VisitVO[]>([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const drugLoading = ref(false)
const statusTab = ref<number>(0)
const keyword = ref('')
const pageNum = ref(1)
const pageSize = ref(10)

const drawer = ref(false)
const activeTab = ref('record')
const current = ref<ReceiveResult | null>(null)
const drugs = ref<DrugOption[]>([])

const recordForm = reactive({
  chiefComplaint: '',
  presentIllness: '',
  physicalExam: '',
  diagnosis: '',
  icdCode: '',
})

const prescForm = reactive<{
  prescType: number
  diagnosis: string
  items: { drugId: string; usage: string; dosage: string; frequency: string; days: number; quantity: number }[]
}>({
  prescType: 1,
  diagnosis: '',
  items: [],
})

const drawerTitle = computed(() =>
  current.value ? `接诊 · ${current.value.patientName}（${current.value.regNo}）` : '接诊',
)

/** 选中的药品里含麻精毒麻时给额外提示 */
const specialSelected = computed(() => {
  const ids = new Set(prescForm.items.map((i) => i.drugId))
  return drugs.value.some((d) => ids.has(d.id) && d.isSpecial === 1)
})

function regTypeText(v?: number) {
  return ['', '普通', '专家', '急诊'][v ?? 0] ?? '-'
}
function genderText(v?: number) {
  return ['女', '男', '未知'][v ?? 2] ?? '未知'
}

async function load() {
  loading.value = true
  try {
    const res = await pageVisits({ pageNum: pageNum.value, pageSize: pageSize.value, status: statusTab.value })
    rows.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function reload() {
  pageNum.value = 1
  load()
}

function onPageChange(p: number) {
  pageNum.value = p
  load()
}

async function receive(row: VisitVO) {
  current.value = await receiveVisit(row.id)
  await fillRecord(current.value)
  drawer.value = true
  activeTab.value = 'record'
  load()
}

async function openRecord(row: VisitVO) {
  current.value = await receiveVisit(row.id).catch(() => null)
  if (!current.value) {
    ElMessage.info('该就诊无法重复接诊，请从列表查看')
    return
  }
  await fillRecord(current.value)
  drawer.value = true
  activeTab.value = 'record'
}

/** 已写过病历就回填，没写过留空 */
async function fillRecord(r: ReceiveResult) {
  Object.assign(recordForm, {
    chiefComplaint: '',
    presentIllness: '',
    physicalExam: '',
    diagnosis: '',
    icdCode: '',
  })
  prescForm.items = []
  prescForm.diagnosis = ''
  if (r.recordId) {
    const rec = await getRecord(r.visitId)
    if (rec) {
      Object.assign(recordForm, {
        chiefComplaint: rec.chiefComplaint ?? '',
        presentIllness: rec.presentIllness ?? '',
        physicalExam: rec.physicalExam ?? '',
        diagnosis: rec.diagnosis ?? '',
        icdCode: rec.icdCode ?? '',
      })
      prescForm.diagnosis = rec.diagnosis ?? ''
    }
  }
}

async function saveRecord(submit: boolean) {
  if (!current.value) return
  saving.value = true
  try {
    await saveRecordApi({ visitId: current.value.visitId, ...recordForm, submit })
    ElMessage.success(submit ? '病历已提交，之后不可再修改' : '草稿已保存')
    if (submit) {
      await fillRecord({ ...current.value, recordId: current.value.recordId || '1' })
    }
  } finally {
    saving.value = false
  }
}

async function searchDrugs(kw: string) {
  if (!kw) {
    drugs.value = []
    return
  }
  drugLoading.value = true
  try {
    drugs.value = await drugOptions(kw)
  } finally {
    drugLoading.value = false
  }
}

function onDrugChange(row: { dosage: string; quantity: number }, _i: number) {
  // 选药后给个默认用量与数量，减少医生逐格录入
  if (!row.dosage) {
    row.dosage = '1次1片'
  }
}

function addItem() {
  prescForm.items.push({
    drugId: '',
    usage: '口服',
    dosage: '',
    frequency: 'tid',
    days: 3,
    quantity: 1,
  })
}

function removeItem(i: number) {
  prescForm.items.splice(i, 1)
}

async function submitPresc() {
  if (!current.value) return
  if (prescForm.items.length === 0) {
    ElMessage.warning('请至少添加一种药品')
    return
  }
  if (prescForm.items.some((i) => !i.drugId)) {
    ElMessage.warning('存在未选择药品的明细')
    return
  }
  submitting.value = true
  try {
    await createPrescription({
      visitId: current.value.visitId,
      prescType: prescForm.prescType,
      diagnosis: prescForm.diagnosis,
      items: prescForm.items,
    })
    ElMessage.success('处方已开具，待药师审核')
    prescForm.items = []
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.queue-no {
  font-size: 18px;
  font-weight: 700;
  color: #2563eb;
}
</style>
