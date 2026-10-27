<template>
  <div class="hp-page">
    <h3 class="hp-page-title">日结月结</h3>

    <el-tabs v-model="tab" @tab-change="reload">
      <el-tab-pane label="日结" name="daily" />
      <el-tab-pane label="月结" name="monthly" />
    </el-tabs>

    <!-- 日结 -->
    <template v-if="tab === 'daily'">
      <div class="hp-toolbar">
        <el-date-picker
          v-model="day"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="结账日期"
          style="width: 170px"
          @change="loadDaily"
        />
        <el-button :icon="Refresh" :loading="loading" @click="loadDaily">刷新</el-button>
      </div>

      <el-card v-if="daily" shadow="never">
        <el-descriptions :column="2" border size="small" title="收入汇总">
          <el-descriptions-item label="收费笔数">{{ daily.chargeCount }}</el-descriptions-item>
          <el-descriptions-item label="收费总额">
            <span class="hp-amount">¥{{ daily.totalAmount }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="退费笔数">{{ daily.refundCount }}</el-descriptions-item>
          <el-descriptions-item label="退费金额">¥{{ daily.refundAmount }}</el-descriptions-item>
        </el-descriptions>

        <el-table :data="daily.payTypeAmounts" border size="small" style="margin-top: 14px">
          <el-table-column label="支付方式" width="130">
            <template #default="{ row }">{{ PAY_TYPE_LABEL[row.payType] }}</template>
          </el-table-column>
          <el-table-column prop="count" label="笔数" width="90" />
          <el-table-column label="金额" width="140">
            <template #default="{ row }">¥{{ row.amount }}</template>
          </el-table-column>
          <el-table-column label="占比">
            <template #default="{ row }">
              {{ percent(row.amount, daily.totalAmount) }}
            </template>
          </el-table-column>
        </el-table>

        <el-descriptions :column="3" border size="small" title="现金核对" style="margin-top: 18px">
          <el-descriptions-item label="应有现金">
            ¥{{ daily.expectedCash }}
          </el-descriptions-item>
          <el-descriptions-item label="实点现金">
            <el-input-number
              v-model="actualCash"
              :min="0"
              :precision="2"
              :step="10"
              size="small"
              :disabled="!canSubmit"
            />
          </el-descriptions-item>
          <el-descriptions-item label="差异">
            <span :class="{ 'hp-diff': diff !== 0 }">¥{{ diff.toFixed(2) }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="diff !== 0"
          type="error"
          :closable="false"
          style="margin-top: 12px"
          title="账实不符，必须填写差异说明才能提交"
          description="差一块钱也要写清楚差在哪。放过去一次，「差个几十块正常」就成了默许的漏洞。"
        />

        <el-form style="margin-top: 12px">
          <el-form-item label="差异说明" :required="diff !== 0">
            <el-input
              v-model="diffRemark"
              type="textarea"
              :rows="2"
              maxlength="255"
              show-word-limit
              :disabled="!canSubmit"
              placeholder="如：患者缴费时少收 20 元，已当场补齐并补录流水"
            />
          </el-form-item>
        </el-form>

        <div style="margin-top: 6px">
          <el-tag v-if="daily.submitted" type="success">该日已交班</el-tag>
          <span v-else class="hp-muted" style="font-size: 12px">尚未提交日结</span>
        </div>

        <div style="margin-top: 14px; text-align: right">
          <el-button v-perm="'finance:daily:submit'" type="primary" :loading="submitting" @click="doSubmit">
            提交日结
          </el-button>
        </div>
      </el-card>
    </template>

    <!-- 月结 -->
    <template v-else>
      <div class="hp-toolbar">
        <el-date-picker
          v-model="month"
          type="month"
          value-format="YYYY-MM"
          placeholder="结算月份"
          style="width: 170px"
          @change="loadMonthly"
        />
        <el-button :icon="Refresh" :loading="loading" @click="loadMonthly">刷新</el-button>
      </div>

      <el-card v-if="monthly" shadow="never">
        <el-descriptions :column="4" border size="small">
          <el-descriptions-item label="月份">{{ monthly.month }}</el-descriptions-item>
          <el-descriptions-item label="收费笔数">{{ monthly.chargeCount }}</el-descriptions-item>
          <el-descriptions-item label="收费总额">
            <span class="hp-amount">¥{{ monthly.totalAmount }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="退费金额">¥{{ monthly.refundAmount }}</el-descriptions-item>
        </el-descriptions>

        <el-table :data="monthly.days" border size="small" style="margin-top: 14px" show-summary>
          <el-table-column prop="day" label="日期" width="130" />
          <el-table-column prop="chargeCount" label="收费笔数" width="100" />
          <el-table-column label="收费金额" width="140">
            <template #default="{ row }">¥{{ row.chargeAmount }}</template>
          </el-table-column>
          <el-table-column label="退费金额" width="140">
            <template #default="{ row }">¥{{ row.refundAmount }}</template>
          </el-table-column>
          <el-table-column label="净收入">
            <template #default="{ row }">
              ¥{{ (Number(row.chargeAmount) - Number(row.refundAmount)).toFixed(2) }}
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  dailySettle,
  monthlySettle,
  submitDailySettle,
  PAY_TYPE_LABEL,
  type DailySettleVO,
  type MonthlySettleVO,
} from '@/api/finance'
import { useUserStore } from '@/stores/user'

/**
 * 日结月结。
 *
 * <p><b>差异非 0 时说明必填</b>：这是整条链路里唯一能拦住「随便填个数交差」的地方。
 * 前端把计算差异与校验都做了，但后端还会再校验一次 ——
 * 前端校验只是体验，绕过它发一个请求后端照样拒。</p>
 *
 * <p>提交按钮用 {@code v-perm} 判 {@code finance:daily:submit}：
 * 交班意味着「这几笔账我认了」，不能让任何能看报表的人替别人签字。</p>
 */

const store = useUserStore()
const tab = ref<'daily' | 'monthly'>('daily')
const loading = ref(false)
const submitting = ref(false)

const day = ref<string>(new Date().toISOString().slice(0, 10))
const month = ref<string>(new Date().toISOString().slice(0, 7))

const daily = ref<DailySettleVO | null>(null)
const monthly = ref<MonthlySettleVO | null>(null)

const actualCash = ref(0)
const diffRemark = ref('')

const canSubmit = computed(() => store.hasPerm('finance:daily:submit'))
const diff = computed(() => actualCash.value - Number(daily.value?.expectedCash ?? 0))

function percent(amount: number, total: number) {
  if (!total) return '—'
  return ((Number(amount) / Number(total)) * 100).toFixed(1) + '%'
}

async function loadDaily() {
  loading.value = true
  try {
    daily.value = await dailySettle(day.value)
    // 已交班的日子回填实点现金，让人看到自己交过班
    actualCash.value = Number(daily.value.actualCash ?? daily.value.expectedCash ?? 0)
    diffRemark.value = daily.value.diffRemark ?? ''
  } finally {
    loading.value = false
  }
}

async function loadMonthly() {
  loading.value = true
  try {
    monthly.value = await monthlySettle(month.value)
  } finally {
    loading.value = false
  }
}

function reload() {
  if (tab.value === 'daily') {
    loadDaily()
  } else {
    loadMonthly()
  }
}

async function doSubmit() {
  if (!daily.value) return
  if (diff.value !== 0 && !diffRemark.value.trim()) {
    ElMessage.warning('差异非 0，请先填写差异说明')
    return
  }
  submitting.value = true
  try {
    await submitDailySettle({
      settleDate: day.value,
      actualCash: actualCash.value,
      diffRemark: diffRemark.value.trim() || undefined,
    })
    ElMessage.success('日结已提交')
    await loadDaily()
  } finally {
    submitting.value = false
  }
}

onMounted(loadDaily)
</script>

<style scoped>
.hp-amount {
  color: #d93026;
  font-weight: 600;
}

.hp-diff {
  color: #d93026;
  font-weight: 700;
}
</style>
