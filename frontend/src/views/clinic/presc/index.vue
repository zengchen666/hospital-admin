<template>
  <div class="hp-page">
    <h3 class="hp-page-title">处方管理</h3>

    <div class="hp-toolbar">
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="reload">
        <el-option label="待审核" :value="0" />
        <el-option label="已审核" :value="1" />
        <el-option label="已收费" :value="2" />
        <el-option label="已发药" :value="3" />
        <el-option label="已退" :value="4" />
      </el-select>
      <el-select v-model="query.prescType" placeholder="处方类型" clearable style="width: 130px" @change="reload">
        <el-option label="西药" :value="1" />
        <el-option label="中药" :value="2" />
        <el-option label="中成药" :value="3" />
      </el-select>
      <el-button :icon="Refresh" @click="reload">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="prescNo" label="处方号" width="170" />
      <el-table-column prop="patientName" label="患者" width="100" />
      <el-table-column prop="doctorName" label="开方医生" width="100" />
      <el-table-column label="类型" width="80">
        <template #default="{ row }">{{ prescTypeText(row.prescType) }}</template>
      </el-table-column>
      <el-table-column prop="diagnosis" label="诊断" min-width="160" show-overflow-tooltip />
      <el-table-column label="金额" width="100">
        <template #default="{ row }">¥{{ row.totalAmount }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="开方时间" width="170" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button
            v-if="row.status === 0"
            v-perm="'clinic:presc:audit'"
            link
            type="success"
            @click="audit(row)"
          >
            审核
          </el-button>
          <el-button
            v-if="row.status === 0"
            v-perm="'clinic:prescribe'"
            link
            type="danger"
            @click="cancel(row)"
          >
            作废
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">暂无处方</span>
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

    <el-dialog v-model="detailVisible" title="处方详情" width="760px">
      <el-descriptions v-if="detail" :column="3" border>
        <el-descriptions-item label="处方号">{{ detail.prescNo }}</el-descriptions-item>
        <el-descriptions-item label="患者">{{ detail.patientName }}</el-descriptions-item>
        <el-descriptions-item label="开方医生">{{ detail.doctorName }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ prescTypeText(detail.prescType) }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusText(detail.status) }}</el-descriptions-item>
        <el-descriptions-item label="金额">¥{{ detail.totalAmount }}</el-descriptions-item>
        <el-descriptions-item label="诊断" :span="3">{{ detail.diagnosis || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="detail?.items ?? []" border size="small" style="margin-top: 12px">
        <el-table-column prop="drugName" label="药品" min-width="160" />
        <el-table-column prop="spec" label="规格" width="120" />
        <el-table-column prop="usage" label="用法" width="90" />
        <el-table-column prop="dosage" label="用量" width="90" />
        <el-table-column prop="frequency" label="频次" width="80" />
        <el-table-column prop="days" label="天数" width="70" />
        <el-table-column prop="quantity" label="数量" width="70" />
        <el-table-column label="单价" width="80">
          <template #default="{ row }">¥{{ row.price }}</template>
        </el-table-column>
        <el-table-column label="小计" width="90">
          <template #default="{ row }">¥{{ row.amount }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  auditPrescription,
  cancelPrescription,
  getPrescription,
  pagePrescriptions,
  type PrescriptionQuery,
  type PrescriptionVO,
} from '@/api/clinic'

/**
 * 处方管理。
 *
 * <p><b>已收费之后的处方不给「作废」按钮</b>：钱都收了、药都可能发出去了，
 * 此时作废处方会让账实对不上。要走退费流程，由财务模块处理。</p>
 */

const rows = ref<PrescriptionVO[]>([])
const total = ref(0)
const loading = ref(false)
const detailVisible = ref(false)
const detail = ref<PrescriptionVO | null>(null)

const query = reactive<PrescriptionQuery>({ pageNum: 1, pageSize: 10 })

function prescTypeText(v?: number) {
  return ['', '西药', '中药', '中成药'][v ?? 0] ?? '-'
}
function statusText(v?: number) {
  return ['待审核', '已审核', '已收费', '已发药', '已退'][v ?? 0] ?? '-'
}
function statusTag(v: number): 'info' | 'success' | 'warning' | 'danger' {
  return v === 0 ? 'warning' : v === 4 ? 'danger' : v === 3 ? 'success' : 'info'
}

async function load() {
  loading.value = true
  try {
    const res = await pagePrescriptions(query)
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

async function openDetail(row: PrescriptionVO) {
  detail.value = await getPrescription(row.id)
  detailVisible.value = true
}

async function audit(row: PrescriptionVO) {
  await auditPrescription(row.id)
  ElMessage.success('审核通过，患者可去缴费')
  load()
}

async function cancel(row: PrescriptionVO) {
  try {
    await ElMessageBox.confirm(
      `确定作废处方「${row.prescNo}」吗？作废后不可恢复。`,
      '作废确认',
      { type: 'warning', confirmButtonText: '作废', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await cancelPrescription(row.id)
  ElMessage.success('处方已作废')
  load()
}

onMounted(load)
</script>
