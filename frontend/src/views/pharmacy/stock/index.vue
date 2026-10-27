<template>
  <div class="hp-page">
    <h3 class="hp-page-title">库存管理</h3>

    <el-alert
      v-if="warnCount > 0"
      type="warning"
      :closable="false"
      style="margin-bottom: 12px"
      :title="`库存预警：${warnCount} 个批次低于预警线`"
    />

    <div class="hp-toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="药品名称 / 编码"
        clearable
        style="width: 180px"
        @clear="reload"
        @keyup.enter="reload"
      />
      <el-select v-model="query.warehouseId" placeholder="全部库房" clearable style="width: 160px" @change="reload">
        <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
      </el-select>
      <el-checkbox v-model="query.onlyWarn" style="margin: 0 4px" @change="reload">只看预警</el-checkbox>
      <el-select v-model="expireDays" placeholder="效期筛选" clearable style="width: 150px" @change="onExpireChange">
        <el-option label="90 天内到期" :value="90" />
        <el-option label="30 天内到期" :value="30" />
        <el-option label="180 天内到期" :value="180" />
      </el-select>
      <el-button :icon="Refresh" @click="reload">刷新</el-button>
      <span class="spacer" />
      <el-button v-perm="'pharmacy:stock:in'" type="primary" :icon="Plus" @click="openIn">入库</el-button>
      <el-button v-perm="'pharmacy:stock:out'" :icon="Minus" @click="openOut">出库</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="drugCode" label="药品编码" width="110" />
      <el-table-column prop="drugName" label="药品名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="spec" label="规格" width="90" />
      <el-table-column prop="batchNo" label="批号" width="120" />
      <el-table-column prop="warehouseName" label="库房" width="130" />
      <el-table-column label="库存" width="90">
        <template #default="{ row }">
          <span :class="{ 'hp-text-danger': row.warn }">{{ row.quantity }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="warnNum" label="预警线" width="80" />
      <el-table-column label="有效期" width="120">
        <template #default="{ row }">
          <span :class="expireClass(row)">{{ row.expireDate || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="剩余天数" width="90">
        <template #default="{ row }">
          <span :class="expireClass(row)">{{ row.daysToExpire ?? '-' }}</span>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">暂无库存</span>
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

    <!-- 入库 -->
    <el-dialog v-model="inVisible" title="药品入库" width="520px" :close-on-click-modal="false">
      <el-alert
        type="info"
        :closable="false"
        style="margin-bottom: 16px"
        title="批号必填"
        description="药品追溯靠批号。批号为空的库存一旦遇到召回，只能整库报废。同一药品+库房+批号会累加而不是新建一行。"
      />
      <el-form ref="inRef" :model="inForm" :rules="inRules" label-width="100px">
        <el-form-item label="药品" prop="drugId">
          <el-select
            v-model="inForm.drugId"
            filterable
            remote
            reserve-keyword
            placeholder="输入名称或编码搜索"
            :remote-method="searchDrugs"
            :loading="searching"
            style="width: 100%"
          >
            <el-option v-for="d in drugOptions" :key="d.id" :label="d.label" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="库房" prop="warehouseId">
          <el-select v-model="inForm.warehouseId" placeholder="选择库房" style="width: 100%">
            <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="批号" prop="batchNo">
          <el-input v-model="inForm.batchNo" maxlength="50" placeholder="如 20261001A" />
        </el-form-item>
        <el-form-item label="数量" prop="quantity">
          <el-input-number v-model="inForm.quantity" :min="1" />
        </el-form-item>
        <el-form-item label="预警线">
          <el-input-number v-model="inForm.warnNum" :min="0" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="生产日期">
              <el-date-picker v-model="inForm.produceDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="有效期" prop="expireDate">
              <el-date-picker v-model="inForm.expireDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="来源单号">
          <el-input v-model="inForm.relatedNo" maxlength="50" placeholder="采购单号（可空）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitIn">确认入库</el-button>
      </template>
    </el-dialog>

    <!-- 出库 -->
    <el-dialog v-model="outVisible" title="药品出库（报损 / 领用）" width="480px" :close-on-click-modal="false">
      <el-alert
        type="warning"
        :closable="false"
        style="margin-bottom: 16px"
        title="发药不走这里"
        description="发药要写发药单并推进处方状态，请到「门诊发药」页面操作。这里只用于报损、科室领用等非发药出库。"
      />
      <el-form ref="outRef" :model="outForm" :rules="outRules" label-width="100px">
        <el-form-item label="药品" prop="drugId">
          <el-select
            v-model="outForm.drugId"
            filterable
            remote
            reserve-keyword
            placeholder="输入名称或编码搜索"
            :remote-method="searchDrugs"
            :loading="searching"
            style="width: 100%"
          >
            <el-option v-for="d in drugOptions" :key="d.id" :label="d.label" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="库房" prop="warehouseId">
          <el-select v-model="outForm.warehouseId" placeholder="选择库房" style="width: 100%">
            <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="批号" prop="batchNo">
          <el-input v-model="outForm.batchNo" maxlength="50" />
        </el-form-item>
        <el-form-item label="数量" prop="quantity">
          <el-input-number v-model="outForm.quantity" :min="1" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="outForm.remark" type="textarea" :rows="2" maxlength="255" placeholder="如：破损报损" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="outVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitOut">确认出库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Refresh, Plus, Minus } from '@element-plus/icons-vue'
import {
  listWarehouses,
  pageDrugs,
  pageStock,
  stockIn,
  stockOut,
  stockWarn,
  type StockQuery,
  type StockVO,
  type WarehouseVO,
} from '@/api/drug'

/**
 * 库存管理。
 *
 * <p><b>库存按「药品 + 库房 + 批号」一行</b>：这不是过度拆分 ——
 * 药品召回时只召回某个批号，没有批号就只能整库报废。</p>
 *
 * <p><b>发药按钮刻意不放在这里</b>：发药要写发药单、推进处方状态、按近效期拆批次，
 * 属于另一个业务动作。放在库存页会让「出库」和「发药」两个语义混在一起，
 * 最后分不清某笔库存减少到底有没有对应的发药单。</p>
 */

const rows = ref<StockVO[]>([])
const warehouses = ref<WarehouseVO[]>([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)
const searching = ref(false)
const inVisible = ref(false)
const outVisible = ref(false)
const expireDays = ref<number | undefined>(undefined)
const drugOptions = ref<{ id: string; label: string }[]>([])
const inRef = ref<FormInstance>()
const outRef = ref<FormInstance>()

const query = reactive<StockQuery & { onlyWarn?: boolean }>({ pageNum: 1, pageSize: 10 })

const warnRows = ref<StockVO[]>([])
const warnCount = computed(() => warnRows.value.length)

const inForm = reactive({
  drugId: '',
  warehouseId: '',
  batchNo: '',
  quantity: 1,
  warnNum: 0,
  produceDate: '',
  expireDate: '',
  relatedNo: '',
})

const outForm = reactive({
  drugId: '',
  warehouseId: '',
  batchNo: '',
  quantity: 1,
  remark: '',
})

const inRules: FormRules = {
  drugId: [{ required: true, message: '请选择药品', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择库房', trigger: 'change' }],
  batchNo: [{ required: true, message: '批号不能为空', trigger: 'blur' }],
  expireDate: [{ required: true, message: '请选择有效期', trigger: 'change' }],
}

const outRules: FormRules = {
  drugId: [{ required: true, message: '请选择药品', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择库房', trigger: 'change' }],
  batchNo: [{ required: true, message: '批号不能为空', trigger: 'blur' }],
  remark: [{ required: true, message: '请填写出库原因', trigger: 'blur' }],
}

/** 已过期红、即将到期黄、正常不标色 */
function expireClass(row: StockVO) {
  if (row.daysToExpire == null) return ''
  if (row.daysToExpire < 0) return 'hp-text-danger'
  if (row.daysToExpire <= 90) return 'hp-text-warn'
  return ''
}

async function load() {
  loading.value = true
  try {
    const res = await pageStock(query)
    rows.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadWarn() {
  try {
    warnRows.value = await stockWarn()
  } catch {
    warnRows.value = []
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

function onExpireChange(v?: number) {
  query.expireDays = v
  reload()
}

async function searchDrugs(kw: string) {
  if (!kw) {
    drugOptions.value = []
    return
  }
  searching.value = true
  try {
    const res = await pageDrugs({ pageNum: 1, pageSize: 20, keyword: kw })
    drugOptions.value = res.list.map((d) => ({
      id: d.id,
      label: `${d.drugName}（${d.spec} · ${d.unit}）`,
    }))
  } finally {
    searching.value = false
  }
}

function openIn() {
  Object.assign(inForm, {
    drugId: '', warehouseId: '', batchNo: '', quantity: 1, warnNum: 0,
    produceDate: '', expireDate: '', relatedNo: '',
  })
  drugOptions.value = []
  inVisible.value = true
}

function openOut() {
  Object.assign(outForm, { drugId: '', warehouseId: '', batchNo: '', quantity: 1, remark: '' })
  drugOptions.value = []
  outVisible.value = true
}

async function submitIn() {
  const valid = await inRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await stockIn({ ...inForm })
    ElMessage.success('入库成功')
    inVisible.value = false
    load()
    loadWarn()
  } finally {
    submitting.value = false
  }
}

async function submitOut() {
  const valid = await outRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await stockOut({ ...outForm })
    ElMessage.success('出库成功')
    outVisible.value = false
    load()
    loadWarn()
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  warehouses.value = await listWarehouses()
  await load()
  await loadWarn()
})
</script>

<style scoped>
.hp-text-danger {
  color: var(--el-color-danger);
  font-weight: 600;
}
.hp-text-warn {
  color: var(--el-color-warning);
  font-weight: 600;
}
</style>
