<template>
  <div class="hp-page">
    <h3 class="hp-page-title">采购管理</h3>

    <div class="hp-toolbar">
      <el-input
        v-model="query.purchaseNo"
        placeholder="采购单号"
        clearable
        style="width: 180px"
        @clear="reload"
        @keyup.enter="reload"
      />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="reload">
        <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
      <el-button :icon="Refresh" @click="reload">查询</el-button>
      <span class="spacer" />
      <el-button v-perm="'pharmacy:purchase:edit'" type="primary" :icon="Plus" @click="openCreate">
        新建采购单
      </el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="purchaseNo" label="采购单号" width="170" />
      <el-table-column prop="supplierName" label="供应商" width="150" show-overflow-tooltip />
      <el-table-column prop="warehouseName" label="目标库房" width="130" />
      <el-table-column label="金额" width="110">
        <template #default="{ row }">¥{{ row.totalAmount }}</template>
      </el-table-column>
      <el-table-column prop="expectDate" label="期望到货" width="120" />
      <el-table-column prop="applicantName" label="申请人" width="100" />
      <el-table-column prop="auditorName" label="审核人" width="100" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">明细</el-button>
          <el-button
            v-if="row.status === 1"
            v-perm="'pharmacy:purchase:audit'"
            link
            type="success"
            @click="audit(row)"
          >
            审核
          </el-button>
          <el-button
            v-if="row.status === 2"
            v-perm="'pharmacy:stock:in'"
            link
            type="warning"
            @click="openIn(row)"
          >
            入库
          </el-button>
          <el-button
            v-if="row.status !== 3 && row.status !== 4"
            v-perm="'pharmacy:purchase:edit'"
            link
            type="danger"
            @click="voidOne(row)"
          >
            作废
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">暂无采购单</span>
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

    <!-- 明细 -->
    <el-dialog v-model="detailVisible" title="采购单明细" width="720px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="采购单号">{{ current?.purchaseNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTag(current?.status ?? 0)" size="small">{{ statusText(current?.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="供应商">{{ current?.supplierName }}</el-descriptions-item>
        <el-descriptions-item label="目标库房">{{ current?.warehouseName }}</el-descriptions-item>
        <el-descriptions-item label="总金额">¥{{ current?.totalAmount }}</el-descriptions-item>
        <el-descriptions-item label="期望到货">{{ current?.expectDate || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="current?.items ?? []" border size="small" style="margin-top: 12px">
        <el-table-column prop="drugName" label="药品" min-width="140" show-overflow-tooltip />
        <el-table-column prop="spec" label="规格" width="90" />
        <el-table-column prop="quantity" label="采购数" width="80" />
        <el-table-column label="单价" width="90">
          <template #default="{ row }">¥{{ row.price }}</template>
        </el-table-column>
        <el-table-column label="小计" width="90">
          <template #default="{ row }">¥{{ row.amount }}</template>
        </el-table-column>
        <el-table-column prop="arriveNum" label="实到" width="80" />
      </el-table>
    </el-dialog>

    <!-- 新建 -->
    <el-dialog v-model="formVisible" title="新建采购单" width="820px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="供应商" prop="supplierId">
              <el-select v-model="form.supplierId" filterable placeholder="选择供应商" style="width: 100%">
                <el-option v-for="s in suppliers" :key="s.id" :label="s.supplierName" :value="s.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="目标库房" prop="warehouseId">
              <el-select v-model="form.warehouseId" placeholder="选择库房" style="width: 100%">
                <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="期望到货">
          <el-date-picker v-model="form.expectDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="采购明细" required>
          <el-table :data="form.items" border size="small">
            <el-table-column label="药品" min-width="200">
              <template #default="{ row, $index }">
                <el-select
                  v-model="row.drugId"
                  filterable
                  remote
                  reserve-keyword
                  placeholder="搜索药品"
                  :remote-method="(kw: string) => searchDrugs(kw, $index)"
                  style="width: 100%"
                >
                  <el-option v-for="d in row.options" :key="d.id" :label="d.label" :value="d.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="数量" width="120">
              <template #default="{ row }">
                <el-input-number v-model="row.quantity" :min="1" size="small" controls-position="right" />
              </template>
            </el-table-column>
            <el-table-column label="单价" width="140">
              <template #default="{ row }">
                <el-input-number v-model="row.price" :min="0" :precision="2" size="small" controls-position="right" />
              </template>
            </el-table-column>
            <el-table-column label="小计" width="100">
              <template #default="{ row }">¥{{ (row.quantity * row.price).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="70">
              <template #default="{ $index }">
                <el-button link type="danger" :disabled="form.items.length === 1" @click="removeItem($index)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button size="small" style="margin-top: 8px" @click="addItem">+ 添加明细</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交（待审核）</el-button>
      </template>
    </el-dialog>

    <!-- 入库 -->
    <el-dialog v-model="inVisible" title="采购入库" width="780px" :close-on-click-modal="false">
      <el-alert
        type="info"
        :closable="false"
        style="margin-bottom: 16px"
        title="实到数量可以少于采购数量"
        description="供应商少发、破损拒收都靠「采购数 ≠ 实到数」体现。实到为 0 的行请直接删除，不要留 0 行混淆对账。"
      />
      <el-row :gutter="12" style="margin-bottom: 12px">
        <el-col :span="12">
          <el-date-picker
            v-model="inForm.produceDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="生产日期（可空）"
            style="width: 100%"
          />
        </el-col>
        <el-col :span="12">
          <el-input-number v-model="inForm.warnNum" :min="0" placeholder="预警线" style="width: 100%" />
        </el-col>
      </el-row>
      <el-table :data="inForm.items" border size="small">
        <el-table-column prop="drugName" label="药品" min-width="140" show-overflow-tooltip />
        <el-table-column prop="quantity" label="采购数" width="80" />
        <el-table-column label="实到" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.arriveNum" :min="1" :max="row.quantity" size="small" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="批号" width="150">
          <template #default="{ row }">
            <el-input v-model="row.batchNo" size="small" placeholder="必填" maxlength="50" />
          </template>
        </el-table-column>
        <el-table-column label="有效期" width="170">
          <template #default="{ row }">
            <el-date-picker
              v-model="row.expireDate"
              type="date"
              value-format="YYYY-MM-DD"
              size="small"
              style="width: 100%"
            />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="inVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitIn">确认入库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Refresh, Plus } from '@element-plus/icons-vue'
import {
  auditPurchase,
  createPurchase,
  getPurchase,
  listWarehouses,
  pageDrugs,
  pagePurchases,
  pageSuppliers,
  purchaseIn,
  voidPurchase,
  type PurchaseVO,
  type SupplierVO,
  type WarehouseVO,
} from '@/api/drug'

/**
 * 采购管理：建单 → 审核 → 入库。
 *
 * <p><b>审核不加库存，这是刻意的设计</b>：审核是「同意买」，
 * 货有没有到、到了多少、效期如何，是验收时才知道的事。
 * 审核即加库存，库存会凭空多出一批还在供应商车上的药，账实不符从这里就开始了。</p>
 *
 * <p><b>审核按钮对自己提交的单据不可见是后端保证的</b>：即使前端漏判，
 * 后端也会以 40902 拒绝（不相容职务）。前端只做提示，不做最终防线。</p>
 */

const STATUS_OPTIONS = [
  { value: 0, label: '草稿' },
  { value: 1, label: '待审核' },
  { value: 2, label: '已审核' },
  { value: 3, label: '已入库' },
  { value: 4, label: '已作废' },
]

const rows = ref<PurchaseVO[]>([])
const suppliers = ref<SupplierVO[]>([])
const warehouses = ref<WarehouseVO[]>([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)
const formVisible = ref(false)
const detailVisible = ref(false)
const inVisible = ref(false)
const current = ref<PurchaseVO | null>(null)
const formRef = ref<FormInstance>()

const query = reactive<{ pageNum: number; pageSize: number; purchaseNo?: string; status?: number }>({
  pageNum: 1,
  pageSize: 10,
})

const form = reactive<{
  supplierId: string
  warehouseId: string
  expectDate: string
  items: { drugId: string; quantity: number; price: number; options: { id: string; label: string }[] }[]
}>({
  supplierId: '',
  warehouseId: '',
  expectDate: '',
  items: [newItem()],
})

const inForm = reactive<{
  purchaseId: string
  produceDate: string
  warnNum: number
  items: {
    itemId: string
    drugName: string
    quantity: number
    arriveNum: number
    batchNo: string
    expireDate: string
  }[]
}>({
  purchaseId: '',
  produceDate: '',
  warnNum: 0,
  items: [],
})

const rules: FormRules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择库房', trigger: 'change' }],
}

function newItem() {
  return { drugId: '', quantity: 1, price: 0, options: [] as { id: string; label: string }[] }
}

function statusText(v?: number) {
  return STATUS_OPTIONS.find((s) => s.value === v)?.label ?? '-'
}
function statusTag(v: number): 'info' | 'warning' | 'success' | 'danger' {
  return v === 3 ? 'success' : v === 2 ? 'warning' : v === 4 ? 'danger' : 'info'
}

async function load() {
  loading.value = true
  try {
    const res = await pagePurchases(query)
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

function addItem() {
  form.items.push(newItem())
}

function removeItem(i: number) {
  form.items.splice(i, 1)
}

function openCreate() {
  Object.assign(form, { supplierId: '', warehouseId: '', expectDate: '', items: [newItem()] })
  formVisible.value = true
}

async function searchDrugs(kw: string, index: number) {
  if (!kw) {
    form.items[index].options = []
    return
  }
  const res = await pageDrugs({ pageNum: 1, pageSize: 20, keyword: kw })
  form.items[index].options = res.list.map((d) => ({
    id: d.id,
    label: `${d.drugName}（${d.spec}）`,
  }))
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const items = form.items.filter((i) => i.drugId)
  if (!items.length) {
    ElMessage.warning('请至少添加一条采购明细')
    return
  }
  submitting.value = true
  try {
    await createPurchase({
      supplierId: form.supplierId,
      warehouseId: form.warehouseId,
      expectDate: form.expectDate || undefined,
      items: items.map((i) => ({ drugId: i.drugId, quantity: i.quantity, price: i.price })),
    })
    ElMessage.success('采购单已提交，等待审核')
    formVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function audit(row: PurchaseVO) {
  const ok = await ElMessageBox.confirm(
    `确认审核通过采购单「${row.purchaseNo}」？审核不等于入库，货到后仍需单独办理入库。`,
    '采购审核',
    { type: 'warning' },
  ).then(() => true).catch(() => false)
  if (!ok) return
  try {
    await auditPurchase(row.id)
    ElMessage.success('审核通过')
    load()
  } catch {
    /* 后端会说明原因（如申请人不能审自己的单） */
  }
}

async function openDetail(row: PurchaseVO) {
  current.value = await getPurchase(row.id)
  detailVisible.value = true
}

async function openIn(row: PurchaseVO) {
  const detail = await getPurchase(row.id)
  current.value = detail
  inForm.purchaseId = row.id
  inForm.produceDate = ''
  inForm.warnNum = 0
  inForm.items = detail.items.map((i) => ({
    itemId: i.id,
    drugName: `${i.drugName}（${i.spec}）`,
    quantity: i.quantity,
    arriveNum: i.quantity,
    batchNo: '',
    expireDate: '',
  }))
  inVisible.value = true
}

async function submitIn() {
  const missing = inForm.items.find((i) => !i.batchNo || !i.expireDate)
  if (missing) {
    ElMessage.warning(`「${missing.drugName}」的批号与有效期必须填写`)
    return
  }
  submitting.value = true
  try {
    await purchaseIn(inForm.purchaseId, {
      produceDate: inForm.produceDate || undefined,
      warnNum: inForm.warnNum,
      items: inForm.items.map((i) => ({
        itemId: i.itemId,
        arriveNum: i.arriveNum,
        batchNo: i.batchNo,
        expireDate: i.expireDate,
      })),
    })
    ElMessage.success('入库完成，库存与流水已同步')
    inVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function voidOne(row: PurchaseVO) {
  const ok = await ElMessageBox.confirm(
    `确认作废采购单「${row.purchaseNo}」？`,
    '作废确认',
    { type: 'warning' },
  ).then(() => true).catch(() => false)
  if (!ok) return
  try {
    await voidPurchase(row.id)
    ElMessage.success('已作废')
    load()
  } catch {
    /* 已入库的不能作废，后端会给原因 */
  }
}

onMounted(async () => {
  const [w, s] = await Promise.all([
    listWarehouses(),
    pageSuppliers({ pageNum: 1, pageSize: 100, status: 1 }),
  ])
  warehouses.value = w
  suppliers.value = s.list
  await load()
})
</script>
