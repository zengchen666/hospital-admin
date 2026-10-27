<template>
  <div class="hp-page">
    <h3 class="hp-page-title">门诊发药</h3>

    <div class="hp-toolbar">
      <el-input
        v-model="query.prescNo"
        placeholder="处方号"
        clearable
        style="width: 170px"
        @clear="reload"
        @keyup.enter="reload"
      />
      <el-input
        v-model="query.patientName"
        placeholder="患者姓名"
        clearable
        style="width: 140px"
        @clear="reload"
        @keyup.enter="reload"
      />
      <el-button :icon="Refresh" :loading="loading" @click="reload">查询</el-button>
      <span class="spacer" />
      <el-select v-model="warehouseId" placeholder="发药库房" style="width: 190px">
        <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
      </el-select>
    </div>

    <el-alert
      v-if="!warehouseId"
      type="warning"
      :closable="false"
      style="margin-bottom: 12px"
      title="请先选择发药库房"
      description="库存按库房分开存放，不指定库房无法定位从哪一批号扣减。"
    />

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column type="expand">
        <template #default="{ row }">
          <el-table :data="row.items" border size="small" style="margin: 4px 0 4px 48px">
            <el-table-column prop="drugName" label="药品" min-width="150" show-overflow-tooltip />
            <el-table-column prop="spec" label="规格" width="100" />
            <el-table-column prop="quantity" label="数量" width="80" />
            <el-table-column label="可用库存" width="100">
              <template #default="{ row: item }">
                <span :class="{ 'hp-text-danger': (item.availableStock ?? 0) < item.quantity }">
                  {{ item.availableStock ?? 0 }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="单价" width="90">
              <template #default="{ row: item }">¥{{ item.price }}</template>
            </el-table-column>
            <el-table-column label="小计" width="90">
              <template #default="{ row: item }">¥{{ item.amount }}</template>
            </el-table-column>
          </el-table>
        </template>
      </el-table-column>
      <el-table-column prop="prescNo" label="处方号" width="170" />
      <el-table-column prop="patientName" label="患者" width="100" />
      <el-table-column prop="doctorName" label="开方医生" width="100" />
      <el-table-column prop="diagnosis" label="诊断" min-width="160" show-overflow-tooltip />
      <el-table-column label="金额" width="100">
        <template #default="{ row }">¥{{ row.totalAmount }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="开方时间" width="170" />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button
            v-perm="'pharmacy:dispense'"
            link
            type="primary"
            :disabled="!warehouseId || shortage(row)"
            @click="doDispense(row)"
          >
            发药
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">暂无待发药处方（只显示已收费的处方）</span>
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
import {
  dispense,
  listWarehouses,
  pagePendingPresc,
  type PendingPrescVO,
  type WarehouseVO,
} from '@/api/drug'

/**
 * 门诊发药。
 *
 * <p><b>只列已收费的处方</b>：未收费就发药，钱没收、药先走了，
 * 后面补收会变成一笔说不清的账。收费是发药的前置条件，不是可选项。</p>
 *
 * <p><b>库存不足时直接禁用发药按钮</b>：发药是整单事务，
 * 发到一半发现某个药不够要整单回滚，比点之前就提示要糟得多。
 * 可用库存随列表一起返回，就是这个用途。</p>
 *
 * <p>批号由后端按<b>近效期优先</b>自动挑选，前端不指定 ——
 * 让窗口人选批号，等于把「先把快过期的发出去」这条规则交给了人的记性。</p>
 */

const rows = ref<PendingPrescVO[]>([])
const warehouses = ref<WarehouseVO[]>([])
const warehouseId = ref('')
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)

const query = reactive<{ prescNo?: string; patientName?: string; pageNum: number; pageSize: number }>({
  pageNum: 1,
  pageSize: 10,
})

/** 有任何一个药品库存不足，整单都不能发 */
function shortage(row: PendingPrescVO) {
  return row.items.some((i) => (i.availableStock ?? 0) < i.quantity)
}

async function load() {
  loading.value = true
  try {
    const res = await pagePendingPresc(query)
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

async function doDispense(row: PendingPrescVO) {
  const ok = await ElMessageBox.confirm(
    `确认按处方「${row.prescNo}」为「${row.patientName}」发药？发药后不可撤销，如需退回请走退费退药流程。`,
    '发药确认',
    { type: 'warning' },
  ).then(() => true).catch(() => false)
  if (!ok) return
  submitting.value = true
  try {
    const res = await dispense(row.prescId, warehouseId.value)
    ElMessage.success(`发药成功，单号 ${res.dispenseNo}，共 ${res.itemCount} 条明细`)
    load()
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  warehouses.value = await listWarehouses()
  const pharmacy = warehouses.value.find((w) => w.warehouseType === 2)
  warehouseId.value = pharmacy ? pharmacy.id : warehouses.value[0]?.id ?? ''
  await load()
})
</script>

<style scoped>
.hp-text-danger {
  color: var(--el-color-danger);
  font-weight: 600;
}
</style>
