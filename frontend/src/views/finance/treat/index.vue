<template>
  <div class="hp-page">
    <h3 class="hp-page-title">诊疗项目价格</h3>

    <el-alert
      type="warning"
      :closable="false"
      style="margin-bottom: 12px"
      title="调价是新增一版价格，不是覆盖旧价"
      description="上月做的检查按上月的价收。直接改掉旧价，历史收费单就成了「金额对不上任何一版价目表」的悬案。"
    />

    <div class="hp-toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="项目编码 / 名称"
        clearable
        style="width: 180px"
        @clear="reload"
        @keyup.enter="reload"
      />
      <el-select v-model="query.itemType" placeholder="项目类型" clearable style="width: 120px" @change="reload">
        <el-option v-for="(v, k) in TREAT_TYPE_LABEL" :key="k" :label="v" :value="Number(k)" />
      </el-select>
      <el-checkbox v-model="onlyEffective" style="margin-left: 4px" @change="reload">
        只看当前生效价
      </el-checkbox>
      <el-button :icon="Refresh" :loading="loading" @click="reload">查询</el-button>
      <span class="spacer" />
      <el-button v-perm="'finance:treat:edit'" type="primary" :icon="Plus" @click="openCreate">
        新增项目
      </el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="itemCode" label="项目编码" width="140" />
      <el-table-column prop="itemName" label="项目名称" min-width="160" show-overflow-tooltip />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">{{ TREAT_TYPE_LABEL[row.itemType] }}</template>
      </el-table-column>
      <el-table-column prop="spec" label="规格" width="120" show-overflow-tooltip />
      <el-table-column prop="unit" label="单位" width="70" />
      <el-table-column label="价格" width="110">
        <template #default="{ row }">
          <span class="hp-amount">¥{{ row.price }}</span>
        </template>
      </el-table-column>
      <el-table-column label="医保类别" width="100">
        <template #default="{ row }">{{ INSURANCE_TYPE_LABEL[row.insuranceType] }}</template>
      </el-table-column>
      <el-table-column label="报销比例" width="100">
        <template #default="{ row }">{{ row.insuranceRatio }}%</template>
      </el-table-column>
      <el-table-column prop="deptName" label="执行科室" width="120" />
      <el-table-column prop="effectiveDate" label="生效日期" width="120" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button v-perm="'finance:treat:edit'" link type="warning" @click="openAdjust(row)">
            调价
          </el-button>
          <el-button v-perm="'finance:treat:edit'" link @click="openEdit(row)">维护</el-button>
          <el-button
            v-perm="'finance:treat:edit'"
            link
            type="danger"
            :disabled="row.status === 0"
            @click="doDisable(row)"
          >
            停用
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">暂无诊疗项目</span>
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

    <!-- 新增 / 维护 / 调价 共用一个表单，靠 mode 区分提交地址 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="620px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-width="100px">
        <el-form-item label="项目编码" required>
          <el-input v-model="form.itemCode" :disabled="mode !== 'create'" maxlength="30" />
          <span v-if="mode !== 'create'" class="hp-muted" style="font-size: 12px">
            编码不可改：改了等于换了一个项目
          </span>
        </el-form-item>
        <el-form-item label="项目名称" required>
          <el-input v-model="form.itemName" maxlength="100" />
        </el-form-item>
        <el-form-item label="项目类型" required>
          <el-select v-model="form.itemType" style="width: 100%">
            <el-option v-for="(v, k) in TREAT_TYPE_LABEL" :key="k" :label="v" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item label="规格">
          <el-input v-model="form.spec" maxlength="50" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="form.unit" maxlength="20" placeholder="次" />
        </el-form-item>
        <el-form-item label="价格" required>
          <el-input-number v-model="form.price" :min="0" :precision="2" :step="1" />
          <span v-if="mode === 'adjust'" class="hp-muted" style="margin-left: 10px; font-size: 12px">
            原价 ¥{{ editing?.price }}
          </span>
        </el-form-item>
        <el-form-item v-if="mode === 'adjust'" label="生效日期" required>
          <el-date-picker
            v-model="form.effectiveDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择生效日期"
          />
          <span class="hp-muted" style="margin-left: 10px; font-size: 12px">
            同一天不能有两版价
          </span>
        </el-form-item>
        <el-form-item label="医保类别">
          <el-select v-model="form.insuranceType" style="width: 100%">
            <el-option v-for="(v, k) in INSURANCE_TYPE_LABEL" :key="k" :label="v" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item label="报销比例">
          <el-input-number v-model="form.insuranceRatio" :min="0" :max="100" :precision="2" />
          <span class="hp-muted" style="margin-left: 6px">%</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  adjustTreatPrice,
  createTreatItem,
  disableTreatItem,
  pageTreatItems,
  updateTreatItem,
  INSURANCE_TYPE_LABEL,
  TREAT_TYPE_LABEL,
  type TreatItemRequest,
  type TreatItemVO,
} from '@/api/finance'

/**
 * 价目表。
 *
 * <p>「调价」与「维护」是两个按钮、两个接口：
 * 调价会产生新的价格版本并影响之后所有收费，维护只是改描述。
 * 合成一个，审计日志里就分不清这次是改了名字还是改了钱。</p>
 */

const rows = ref<TreatItemVO[]>([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)
const onlyEffective = ref(true)

const query = reactive<{
  pageNum: number
  pageSize: number
  keyword?: string
  itemType?: number
}>({ pageNum: 1, pageSize: 10 })

const dialogVisible = ref(false)
const mode = ref<'create' | 'edit' | 'adjust'>('create')
const editing = ref<TreatItemVO | null>(null)

const form = reactive<TreatItemRequest & { price: number }>({
  itemCode: '',
  itemName: '',
  itemType: 1,
  spec: '',
  unit: '次',
  price: 0,
  insuranceType: 3,
  insuranceRatio: 0,
  status: 1,
  effectiveDate: undefined,
})

const dialogTitle = computed(
  () => ({ create: '新增诊疗项目', edit: '维护诊疗项目', adjust: '调整价格' })[mode.value],
)

async function load() {
  loading.value = true
  try {
    const res = await pageTreatItems({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      itemType: query.itemType,
      onlyEffective: onlyEffective.value,
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

function fill(row: TreatItemVO | null) {
  if (!row) {
    form.itemCode = ''
    form.itemName = ''
    form.itemType = 1
    form.spec = ''
    form.unit = '次'
    form.price = 0
    form.insuranceType = 3
    form.insuranceRatio = 0
    form.status = 1
    form.effectiveDate = undefined
    return
  }
  form.itemCode = row.itemCode
  form.itemName = row.itemName
  form.itemType = row.itemType
  form.spec = row.spec ?? ''
  form.unit = row.unit
  form.price = row.price
  form.insuranceType = row.insuranceType
  form.insuranceRatio = row.insuranceRatio
  form.status = row.status
  form.effectiveDate = undefined
}

function openCreate() {
  mode.value = 'create'
  editing.value = null
  fill(null)
  dialogVisible.value = true
}

function openEdit(row: TreatItemVO) {
  mode.value = 'edit'
  editing.value = row
  fill(row)
  dialogVisible.value = true
}

function openAdjust(row: TreatItemVO) {
  mode.value = 'adjust'
  editing.value = row
  fill(row)
  // 默认给一个新价格占位，逼着人真的填一个数，而不是原样提交
  form.effectiveDate = new Date().toISOString().slice(0, 10)
  dialogVisible.value = true
}

async function submit() {
  if (!form.itemName.trim()) {
    ElMessage.warning('项目名称不能为空')
    return
  }
  if (mode.value === 'create' && !form.itemCode.trim()) {
    ElMessage.warning('项目编码不能为空')
    return
  }
  submitting.value = true
  try {
    const body: TreatItemRequest = { ...form, itemName: form.itemName.trim() }
    if (mode.value === 'create') {
      await createTreatItem(body)
      ElMessage.success('新增成功')
    } else if (mode.value === 'edit') {
      await updateTreatItem(editing.value!.id, body)
      ElMessage.success('维护成功（未改价，不产生新版本）')
    } else {
      await adjustTreatPrice(editing.value!.id, body)
      ElMessage.success(`调价成功，新价 ¥${form.price} 自 ${form.effectiveDate} 生效`)
    }
    dialogVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function doDisable(row: TreatItemVO) {
  const ok = await ElMessageBox.confirm(
    `确认停用「${row.itemName}」？停用后新开单取不到价，已开单不受影响。`,
    '停用确认',
    { type: 'warning' },
  )
    .then(() => true)
    .catch(() => false)
  if (!ok) return
  await disableTreatItem(row.id)
  ElMessage.success('已停用')
  load()
}

onMounted(load)
</script>

<style scoped>
.hp-amount {
  color: #d93026;
  font-weight: 600;
}
</style>
