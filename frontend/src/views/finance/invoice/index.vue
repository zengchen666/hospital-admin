<template>
  <div class="hp-page">
    <h3 class="hp-page-title">发票管理</h3>

    <el-alert
      type="info"
      :closable="false"
      style="margin-bottom: 12px"
      title="作废不等于没开过"
      description="发票号已报税，作废只是让这张票不算数，记录永久保留。作废之后才能对同一张收费单重新开票。"
    />

    <div class="hp-toolbar">
      <el-input
        v-model="query.invoiceNo"
        placeholder="发票号"
        clearable
        style="width: 180px"
        @clear="reload"
        @keyup.enter="reload"
      />
      <el-input
        v-model="query.chargeNo"
        placeholder="收费单号"
        clearable
        style="width: 180px"
        @clear="reload"
        @keyup.enter="reload"
      />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 110px" @change="reload">
        <el-option label="正常" :value="0" />
        <el-option label="已作废" :value="1" />
      </el-select>
      <el-button :icon="Refresh" :loading="loading" @click="reload">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="invoiceNo" label="发票号" width="190" />
      <el-table-column prop="chargeNo" label="收费单号" width="180" />
      <el-table-column prop="patientName" label="患者" width="110" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ row.invoiceType === 1 ? '电子' : '纸质' }}</template>
      </el-table-column>
      <el-table-column label="金额" width="120">
        <template #default="{ row }">
          <span :class="{ 'hp-voided': row.status === 1 }">¥{{ row.amount }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'danger' : 'success'" size="small">
            {{ row.status === 1 ? '已作废' : '正常' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="operatorName" label="开票人" width="110" />
      <el-table-column prop="printTime" label="开票时间" width="170" />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button
            v-perm="'finance:invoice:void'"
            link
            type="danger"
            :disabled="row.status === 1"
            @click="doVoid(row)"
          >
            作废
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">暂无发票记录</span>
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { pageInvoices, voidInvoice, type InvoiceVO } from '@/api/finance'

/**
 * 发票管理。
 *
 * <p>本页只做查询与作废，开票入口在「收费记录」页 ——
 * 开票的上下文是那笔收费，脱离了收费单单独开票，票面上的金额没有来源。</p>
 */

const rows = ref<InvoiceVO[]>([])
const total = ref(0)
const loading = ref(false)

const query = reactive<{
  pageNum: number
  pageSize: number
  invoiceNo?: string
  chargeNo?: string
  status?: number
}>({ pageNum: 1, pageSize: 10 })

async function load() {
  loading.value = true
  try {
    const res = await pageInvoices(query)
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

async function doVoid(row: InvoiceVO) {
  const { value } = await ElMessageBox.prompt(
    `确认作废发票「${row.invoiceNo}」？作废后该收费单可重新开票。`,
    '发票作废',
    { inputPlaceholder: '作废原因（选填）', inputType: 'textarea' },
  ).catch(() => ({ value: null }))
  if (value === null) return
  await voidInvoice(row.id)
  ElMessage.success('发票已作废')
  load()
}

onMounted(load)
</script>

<style scoped>
.hp-voided {
  color: #94a3b8;
  text-decoration: line-through;
}
</style>
