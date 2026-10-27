<template>
  <div class="hp-page">
    <h3 class="hp-page-title">门诊收费记录</h3>

    <div class="hp-toolbar">
      <el-input
        v-model="query.chargeNo"
        placeholder="收费单号"
        clearable
        style="width: 180px"
        @clear="reload"
        @keyup.enter="reload"
      />
      <el-select v-model="query.payType" placeholder="支付方式" clearable style="width: 120px" @change="reload">
        <el-option v-for="(v, k) in PAY_TYPE_LABEL" :key="k" :label="v" :value="Number(k)" />
      </el-select>
      <el-select v-model="query.payStatus" placeholder="支付状态" clearable style="width: 120px" @change="reload">
        <el-option v-for="(v, k) in PAY_STATUS_LABEL" :key="k" :label="v" :value="Number(k)" />
      </el-select>
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        style="width: 240px"
        @change="onDateChange"
      />
      <el-button :icon="Refresh" :loading="loading" @click="reload">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column type="expand">
        <template #default="{ row }">
          <el-table :data="row.items" border size="small" style="margin: 4px 0 4px 48px">
            <el-table-column label="类型" width="90">
              <template #default="{ row: it }">{{ itemTypeLabel(it.itemType) }}</template>
            </el-table-column>
            <el-table-column prop="itemName" label="项目" min-width="180" show-overflow-tooltip />
            <el-table-column prop="quantity" label="数量" width="80" />
            <el-table-column label="单价" width="100">
              <template #default="{ row: it }">¥{{ it.price }}</template>
            </el-table-column>
            <el-table-column label="金额" width="100">
              <template #default="{ row: it }">¥{{ it.amount }}</template>
            </el-table-column>
            <el-table-column prop="refundNum" label="已退数量" width="100" />
          </el-table>
        </template>
      </el-table-column>
      <el-table-column prop="chargeNo" label="收费单号" width="180" />
      <el-table-column prop="patientName" label="患者" width="100" />
      <el-table-column prop="operatorName" label="收费员" width="100" />
      <el-table-column label="应收" width="100">
        <template #default="{ row }">¥{{ row.totalAmount }}</template>
      </el-table-column>
      <el-table-column label="优惠" width="90">
        <template #default="{ row }">¥{{ row.discountAmount }}</template>
      </el-table-column>
      <el-table-column label="实收" width="110">
        <template #default="{ row }">
          <span class="hp-amount">¥{{ row.actualAmount }}</span>
        </template>
      </el-table-column>
      <el-table-column label="支付方式" width="100">
        <template #default="{ row }">{{ PAY_TYPE_LABEL[row.payType] }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="payStatusTag(row.payStatus)" size="small">
            {{ PAY_STATUS_LABEL[row.payStatus] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="chargeTime" label="收费时间" width="170" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button
            v-perm="'finance:charge:add'"
            link
            type="primary"
            :disabled="!invoicable(row)"
            @click="doPrint(row)"
          >
            开票
          </el-button>
          <el-button v-perm="'finance:charge:add'" link type="warning" @click="doSettle(row)">
            医保结算
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">暂无收费记录</span>
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

    <!-- 开票 -->
    <el-dialog v-model="printVisible" title="开具发票" width="460px">
      <el-form label-width="96px">
        <el-form-item label="收费单号">
          <span>{{ printing?.chargeNo }}</span>
        </el-form-item>
        <el-form-item label="患者">
          <span>{{ printing?.patientName }}</span>
        </el-form-item>
        <el-form-item label="发票金额">
          <span class="hp-amount">¥{{ printing?.actualAmount }}</span>
        </el-form-item>
        <el-form-item label="发票类型" required>
          <el-radio-group v-model="invoiceType">
            <el-radio :value="1">电子发票</el-radio>
            <el-radio :value="2">纸质发票</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <p class="hp-muted" style="font-size: 12px; margin: 0">
        金额取收费单实收金额，不可修改 —— 收 100 开 50 的票属于虚开。
      </p>
      <template #footer>
        <el-button @click="printVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitPrint">开票</el-button>
      </template>
    </el-dialog>

    <!-- 医保结算结果 -->
    <el-dialog v-model="insureVisible" title="医保结算结果" width="520px">
      <div v-if="insure">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="结算号">{{ insure.settleNo }}</el-descriptions-item>
          <el-descriptions-item label="参保类型">{{ insure.insuranceType }}</el-descriptions-item>
          <el-descriptions-item label="总费用">¥{{ insure.totalAmount }}</el-descriptions-item>
          <el-descriptions-item label="医保支付">
            <span class="hp-amount">¥{{ insure.insuranceAmount }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="个人自付">¥{{ insure.selfAmount }}</el-descriptions-item>
          <el-descriptions-item label="结算时间">{{ insure.settleTime }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="insureVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  getInsuranceSettle,
  pageCharges,
  printInvoice,
  settleInsurance,
  PAY_STATUS_LABEL,
  PAY_TYPE_LABEL,
  type ChargeVO,
  type InsuranceSettleVO,
} from '@/api/finance'

/**
 * 收费记录。
 *
 * <p>本页只做「查」与「开票 / 医保结算」两个后续动作，
 * 收费动作本身发生在发药与检查流程里（收费是它们的前置闸门），
 * 单独做一个「手工收费」入口等于绕过了业务单据，钱收上来也不知道收的是什么。</p>
 */

const rows = ref<ChargeVO[]>([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)

const query = reactive<{
  pageNum: number
  pageSize: number
  chargeNo?: string
  payType?: number
  payStatus?: number
  startTime?: string
  endTime?: string
}>({ pageNum: 1, pageSize: 10 })

const dateRange = ref<string[]>([])

const printVisible = ref(false)
const printing = ref<ChargeVO | null>(null)
const invoiceType = ref(1)

const insureVisible = ref(false)
const insure = ref<InsuranceSettleVO | null>(null)

async function load() {
  loading.value = true
  try {
    const res = await pageCharges(query)
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

function onDateChange(val: string[] | null) {
  query.startTime = val?.[0]
  query.endTime = val?.[1]
  reload()
}

function itemTypeLabel(t: number) {
  return (['', '药品', '诊疗', '检查', '床位'] as const)[t] ?? '其他'
}

function payStatusTag(s: number) {
  return (['warning', 'success', 'danger', ''] as const)[s] ?? 'info'
}

/** 待支付的单不能开票：票是收款凭证，钱没到就开票等于替患者向税务承诺收入 */
function invoicable(row: ChargeVO) {
  return row.payStatus === 1 || row.payStatus === 3
}

function doPrint(row: ChargeVO) {
  printing.value = row
  invoiceType.value = 1
  printVisible.value = true
}

async function submitPrint() {
  if (!printing.value) return
  submitting.value = true
  try {
    const id = await printInvoice({
      chargeId: Number(printing.value.id),
      invoiceType: invoiceType.value,
    })
    ElMessage.success(`开票成功，发票 ID ${id}`)
    printVisible.value = false
  } finally {
    submitting.value = false
  }
}

async function doSettle(row: ChargeVO) {
  await settleInsurance({ chargeId: Number(row.id), insuranceType: '职工' })
  insure.value = await getInsuranceSettle(row.id)
  insureVisible.value = true
  ElMessage.success('医保结算成功')
}

onMounted(load)
</script>

<style scoped>
.hp-amount {
  color: #d93026;
  font-weight: 600;
}
</style>
