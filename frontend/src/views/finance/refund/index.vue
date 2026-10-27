<template>
  <div class="hp-page">
    <h3 class="hp-page-title">退费管理</h3>

    <el-alert
      type="warning"
      :closable="false"
      style="margin-bottom: 12px"
      title="退费申请与审核必须不是同一个人"
      description="自己申请自己审等于没有审批。退费是唯一能把钱从医院账上拿出去的动作，这道制衡不能省。"
    />

    <div class="hp-toolbar">
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="reload">
        <el-option v-for="(v, k) in REFUND_STATUS_LABEL" :key="k" :label="v" :value="Number(k)" />
      </el-select>
      <el-button :icon="Refresh" :loading="loading" @click="reload">查询</el-button>
      <span class="spacer" />
      <el-button v-perm="'finance:refund:apply'" type="primary" @click="openApply">申请退费</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column type="expand">
        <template #default="{ row }">
          <el-table :data="row.items" border size="small" style="margin: 4px 0 4px 48px">
            <el-table-column prop="itemName" label="项目" min-width="180" show-overflow-tooltip />
            <el-table-column prop="quantity" label="退数量" width="90" />
            <el-table-column label="退费金额" width="110">
              <template #default="{ row: it }">¥{{ it.amount }}</template>
            </el-table-column>
          </el-table>
        </template>
      </el-table-column>
      <el-table-column prop="refundNo" label="退费单号" width="180" />
      <el-table-column prop="chargeNo" label="原收费单" width="180" />
      <el-table-column prop="patientName" label="患者" width="100" />
      <el-table-column label="退费金额" width="120">
        <template #default="{ row }">
          <span class="hp-amount">¥{{ row.refundAmount }}</span>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="90">
        <template #default="{ row }">{{ row.refundType === 1 ? '全额' : '部分' }}</template>
      </el-table-column>
      <el-table-column prop="reason" label="原因" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">{{ REFUND_STATUS_LABEL[row.status] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="applicantName" label="申请人" width="100" />
      <el-table-column prop="auditorName" label="审核人" width="100" />
      <el-table-column prop="createTime" label="申请时间" width="170" />
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button
            v-perm="'finance:refund:audit'"
            link
            type="success"
            :disabled="row.status !== 0"
            @click="doApprove(row)"
          >
            审核
          </el-button>
          <el-button
            v-perm="'finance:refund:audit'"
            link
            type="warning"
            :disabled="row.status !== 1"
            @click="doComplete(row)"
          >
            退款
          </el-button>
          <el-button
            v-perm="'finance:refund:audit'"
            link
            type="danger"
            :disabled="row.status !== 0"
            @click="doReject(row)"
          >
            驳回
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">暂无退费申请</span>
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

    <!-- 申请退费 -->
    <el-dialog v-model="applyVisible" title="申请退费" width="720px" :close-on-click-modal="false">
      <el-form label-width="96px">
        <el-form-item label="原收费单" required>
          <el-select
            v-model="chargeId"
            filterable
            remote
            reserve-keyword
            placeholder="输入收费单号或患者姓名搜索"
            :remote-method="searchCharges"
            :loading="chargeLoading"
            style="width: 100%"
            @change="onChargeChange"
          >
            <el-option
              v-for="c in charges"
              :key="c.id"
              :label="`${c.chargeNo} · ${c.patientName} · ¥${c.actualAmount}`"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="退费原因" required>
          <el-input v-model="reason" type="textarea" :rows="2" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="退费明细" required>
          <el-table v-if="items.length" :data="items" border size="small" style="width: 100%">
            <el-table-column prop="itemName" label="项目" min-width="160" show-overflow-tooltip />
            <el-table-column prop="quantity" label="原数量" width="90" />
            <el-table-column prop="refundNum" label="已退" width="80" />
            <el-table-column label="本次退" width="130">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.thisTime"
                  :min="0"
                  :max="row.quantity - row.refundNum"
                  size="small"
                />
              </template>
            </el-table-column>
            <el-table-column label="金额" width="100">
              <template #default="{ row }">
                ¥{{ (row.price * (row.thisTime ?? 0)).toFixed(2) }}
              </template>
            </el-table-column>
          </el-table>
          <span v-else class="hp-muted">请先选择原收费单</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitApply">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  applyRefund,
  approveRefund,
  completeRefund,
  getCharge,
  pageCharges,
  pageRefunds,
  rejectRefund,
  REFUND_STATUS_LABEL,
  type ChargeVO,
  type RefundVO,
} from '@/api/finance'

/**
 * 退费管理。
 *
 * <p><b>审核与执行退款分成两个按钮</b>：审核是「同意退」，
 * 执行退款是「钱真的出去了」，中间还隔着财务打款这一步。
 * 合成一个动作，钱还没打出去系统就显示「已退款」，对账时全是窟窿。</p>
 */

const rows = ref<RefundVO[]>([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)

const query = reactive<{ pageNum: number; pageSize: number; status?: number }>({
  pageNum: 1,
  pageSize: 10,
})

const applyVisible = ref(false)
const charges = ref<ChargeVO[]>([])
const chargeLoading = ref(false)
const chargeId = ref<string | undefined>()
const reason = ref('')
const items = ref<
  { id: string; itemName: string; quantity: number; refundNum: number; price: number; thisTime?: number }[]
>([])

/** 合计由前端按明细算出，只做展示 —— 真正入账的金额由服务端按原单价重算 */
const applyTotal = computed(() =>
  items.value.reduce((sum, i) => sum + i.price * (i.thisTime ?? 0), 0),
)

async function load() {
  loading.value = true
  try {
    const res = await pageRefunds(query)
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

function statusTag(s: number) {
  return (['warning', 'primary', 'success', 'danger'] as const)[s] ?? 'info'
}

/* ---------------- 申请 ---------------- */

async function searchCharges(keyword: string) {
  chargeLoading.value = true
  try {
    const res = await pageCharges({ pageNum: 1, pageSize: 20, chargeNo: keyword, payStatus: 1 })
    charges.value = res.list
  } finally {
    chargeLoading.value = false
  }
}

async function onChargeChange(id: string) {
  const detail = await getCharge(id)
  items.value = detail.items.map((i) => ({
    id: i.id,
    itemName: i.itemName,
    quantity: i.quantity,
    refundNum: i.refundNum,
    price: i.price,
    thisTime: 0,
  }))
}

function openApply() {
  chargeId.value = undefined
  reason.value = ''
  items.value = []
  charges.value = []
  applyVisible.value = true
}

async function submitApply() {
  if (!chargeId.value) {
    ElMessage.warning('请选择原收费单')
    return
  }
  if (!reason.value.trim()) {
    ElMessage.warning('退费原因必填')
    return
  }
  const picked = items.value.filter((i) => (i.thisTime ?? 0) > 0)
  if (picked.length === 0) {
    ElMessage.warning('请至少选择一项要退的明细')
    return
  }
  submitting.value = true
  try {
    const id = await applyRefund({
      chargeId: Number(chargeId.value),
      reason: reason.value.trim(),
      items: picked.map((i) => ({ chargeItemId: Number(i.id), quantity: i.thisTime as number })),
    })
    ElMessage.success(`退费申请已提交，单号相关记录 ID ${id}，金额 ¥${applyTotal.value.toFixed(2)}`)
    applyVisible.value = false
    reload()
  } finally {
    submitting.value = false
  }
}

/* ---------------- 审核 / 执行 / 驳回 ---------------- */

async function doApprove(row: RefundVO) {
  await approveRefund(row.id)
  ElMessage.success('审核通过，可执行退款')
  load()
}

async function doComplete(row: RefundVO) {
  const ok = await ElMessageBox.confirm(
    `确认向「${row.patientName}」退款 ¥${row.refundAmount}？执行后金额不可撤销。`,
    '退款确认',
    { type: 'warning' },
  )
    .then(() => true)
    .catch(() => false)
  if (!ok) return
  await completeRefund(row.id)
  ElMessage.success('退款已完成')
  load()
}

async function doReject(row: RefundVO) {
  const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回退费申请', {
    inputPattern: /\S+/,
    inputErrorMessage: '驳回原因不能为空',
  }).catch(() => ({ value: null }))
  if (!value) return
  await rejectRefund(row.id, value)
  ElMessage.success('已驳回')
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
