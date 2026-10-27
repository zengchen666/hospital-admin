<template>
  <div class="hp-page">
    <h3 class="hp-page-title">药品目录</h3>

    <div class="hp-toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="编码 / 名称 / 通用名"
        clearable
        style="width: 200px"
        @clear="reload"
        @keyup.enter="reload"
      />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 110px" @change="reload">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-select v-model="query.category" placeholder="分类" clearable style="width: 130px" @change="reload">
        <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
      </el-select>
      <el-button :icon="Refresh" @click="reload">查询</el-button>
      <span class="spacer" />
      <el-button v-perm="'pharmacy:drug:edit'" type="primary" :icon="Plus" @click="openCreate">
        新增药品
      </el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="drugCode" label="药品编码" width="110" />
      <el-table-column prop="drugName" label="药品名称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="spec" label="规格" width="100" />
      <el-table-column prop="dosageForm" label="剂型" width="90" />
      <el-table-column prop="unit" label="单位" width="70" />
      <el-table-column prop="manufacturer" label="生产厂家" min-width="150" show-overflow-tooltip />
      <el-table-column label="进价" width="90">
        <template #default="{ row }">¥{{ row.purchasePrice }}</template>
      </el-table-column>
      <el-table-column label="售价" width="90">
        <template #default="{ row }">¥{{ row.salePrice }}</template>
      </el-table-column>
      <el-table-column label="库存" width="90">
        <template #default="{ row }">
          <span :class="{ 'hp-text-danger': row.totalStock === 0 }">{{ row.totalStock ?? 0 }}</span>
        </template>
      </el-table-column>
      <el-table-column label="分类" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.isSpecial === 1" type="danger" size="small">麻精</el-tag>
          <span v-else>{{ row.category || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button v-perm="'pharmacy:drug:edit'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-perm="'pharmacy:drug:edit'" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">暂无药品</span>
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

    <el-dialog v-model="formVisible" :title="editing ? '编辑药品' : '新增药品'" width="640px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="药品编码" prop="drugCode">
              <el-input v-model="form.drugCode" maxlength="30" placeholder="D001" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="药品名称" prop="drugName">
              <el-input v-model="form.drugName" maxlength="100" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="通用名">
          <el-input v-model="form.genericName" maxlength="100" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="规格">
              <el-input v-model="form.spec" maxlength="50" placeholder="0.25g" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="剂型">
              <el-input v-model="form.dosageForm" maxlength="30" placeholder="片剂" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="单位" prop="unit">
              <el-input v-model="form.unit" maxlength="20" placeholder="盒" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="生产厂家">
          <el-input v-model="form.manufacturer" maxlength="100" />
        </el-form-item>
        <el-form-item label="批准文号">
          <el-input v-model="form.approvalNo" maxlength="50" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="进价" prop="purchasePrice">
              <el-input-number v-model="form.purchasePrice" :min="0" :precision="2" :step="0.1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="售价" prop="salePrice">
              <el-input-number v-model="form.salePrice" :min="0" :precision="2" :step="0.1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="分类">
              <el-input v-model="form.category" maxlength="50" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="特殊药品">
              <el-switch v-model="specialOn" active-text="麻精毒麻" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch v-model="statusOn" active-text="启用" inactive-text="停用" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Refresh, Plus } from '@element-plus/icons-vue'
import {
  createDrug,
  deleteDrug,
  pageDrugs,
  updateDrug,
  type DrugQuery,
  type DrugRequest,
  type DrugVO,
} from '@/api/drug'

/**
 * 药品目录。
 *
 * <p><b>售价低于进价只提示不拦截</b>：政策性亏损药品（部分急救药）真实存在，
 * 一刀切拒绝会把合规业务挡在门外，所以前端只在保存前弹一次确认。</p>
 *
 * <p><b>删除是逻辑删除且要求库存为 0</b>：药还在架子上却查不到，
 * 发药时选不出来、盘点时对不上账。库存不为 0 时后端会返回 409。</p>
 */

const rows = ref<DrugVO[]>([])
const categories = ref<string[]>([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)
const formVisible = ref(false)
const editing = ref<DrugVO | null>(null)
const specialOn = ref(false)
const statusOn = ref(true)
const formRef = ref<FormInstance>()

const query = reactive<DrugQuery>({ pageNum: 1, pageSize: 10 })

const form = reactive<DrugRequest>({
  drugCode: '',
  drugName: '',
  genericName: '',
  spec: '',
  dosageForm: '',
  unit: '盒',
  manufacturer: '',
  approvalNo: '',
  purchasePrice: 0,
  salePrice: 0,
  category: '',
  isSpecial: 0,
  status: 1,
})

const rules: FormRules = {
  drugCode: [{ required: true, message: '请输入药品编码', trigger: 'blur' }],
  drugName: [{ required: true, message: '请输入药品名称', trigger: 'blur' }],
  unit: [{ required: true, message: '请输入单位', trigger: 'blur' }],
}

const priceInverted = computed(() => form.salePrice > 0 && form.salePrice < form.purchasePrice)

async function load() {
  loading.value = true
  try {
    const res = await pageDrugs(query)
    rows.value = res.list
    total.value = res.total
    const seen = new Set<string>()
    res.list.forEach((d) => d.category && seen.add(d.category))
    categories.value = [...seen]
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
  editing.value = null
  Object.assign(form, {
    drugCode: '', drugName: '', genericName: '', spec: '', dosageForm: '', unit: '盒',
    manufacturer: '', approvalNo: '', purchasePrice: 0, salePrice: 0, category: '',
  })
  specialOn.value = false
  statusOn.value = true
  formVisible.value = true
}

function openEdit(row: DrugVO) {
  editing.value = row
  Object.assign(form, {
    drugCode: row.drugCode, drugName: row.drugName, genericName: row.genericName,
    spec: row.spec, dosageForm: row.dosageForm, unit: row.unit,
    manufacturer: row.manufacturer, approvalNo: row.approvalNo,
    purchasePrice: row.purchasePrice, salePrice: row.salePrice, category: row.category,
  })
  specialOn.value = row.isSpecial === 1
  statusOn.value = row.status === 1
  formVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (priceInverted.value) {
    const goOn = await ElMessageBox.confirm(
      '售价低于进价，将按亏损价格入库销售。确认继续？',
      '价格倒挂',
      { type: 'warning' },
    ).then(() => true).catch(() => false)
    if (!goOn) return
  }
  form.isSpecial = specialOn.value ? 1 : 0
  form.status = statusOn.value ? 1 : 0
  submitting.value = true
  try {
    if (editing.value) {
      await updateDrug(editing.value.id, form)
      ElMessage.success('药品已更新')
    } else {
      await createDrug(form)
      ElMessage.success('药品已新增')
    }
    formVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function remove(row: DrugVO) {
  const ok = await ElMessageBox.confirm(
    `确认删除药品「${row.drugName}」？有库存的药品需先清空库存。`,
    '删除确认',
    { type: 'warning' },
  ).then(() => true).catch(() => false)
  if (!ok) return
  try {
    await deleteDrug(row.id)
    ElMessage.success('已删除')
    load()
  } catch {
    /* 后端已给出具体原因（库存不为 0），不重复提示 */
  }
}

onMounted(load)
</script>

<style scoped>
.hp-text-danger {
  color: var(--el-color-danger);
}
</style>
