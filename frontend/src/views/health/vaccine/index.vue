<template>
  <div class="hp-page">
    <h3 class="hp-page-title">疫苗接种</h3>

    <el-tabs v-model="tab" @tab-change="reload">
      <el-tab-pane label="接种记录" name="list" />
      <el-tab-pane name="remind">
        <template #label>
          接种提醒
          <el-badge v-if="remindCount > 0" :value="remindCount" type="warning" style="margin-left: 4px" />
        </template>
      </el-tab-pane>
    </el-tabs>

    <!-- 接种记录 -->
    <template v-if="tab === 'list'">
      <div class="hp-toolbar">
        <el-input
          v-model="query.vaccineName"
          placeholder="疫苗名称"
          clearable
          style="width: 160px"
          @clear="reload"
          @keyup.enter="reload"
        />
        <el-input
          v-model="query.batchNo"
          placeholder="批号"
          clearable
          style="width: 150px"
          @clear="reload"
          @keyup.enter="reload"
        />
        <el-button :icon="Refresh" :loading="loading" @click="reload">查询</el-button>
        <span class="spacer" />
        <el-button v-perm="'health:vaccine:add'" type="primary" :icon="Plus" @click="openCreate">
          接种登记
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="patientName" label="姓名" width="100" />
        <el-table-column prop="vaccineName" label="疫苗" min-width="160" show-overflow-tooltip />
        <el-table-column prop="batchNo" label="批号" width="130" />
        <el-table-column label="剂次" width="80">
          <template #default="{ row }">第 {{ row.doseNo ?? '—' }} 剂</template>
        </el-table-column>
        <el-table-column prop="vaccinateDate" label="接种日期" width="120" />
        <el-table-column label="下次接种" width="130">
          <template #default="{ row }">
            <span v-if="row.nextDate" :class="{ 'hp-overdue': row.overdue }">
              {{ row.nextDate }}
            </span>
            <span v-else class="hp-muted">已完成</span>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="接种人" width="100" />
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
    </template>

    <!-- 接种提醒 -->
    <template v-else>
      <div class="hp-toolbar">
        <span class="hp-muted">未来</span>
        <el-input-number v-model="days" :min="1" :max="365" size="small" />
        <span class="hp-muted">天内到期或已逾期</span>
        <el-button :icon="Refresh" :loading="loading" @click="loadRemind">刷新</el-button>
      </div>

      <el-alert
        type="info"
        :closable="false"
        style="margin-bottom: 12px"
        title="逾期用红色标出"
        description="已逾期的接种排在前面。疫苗逾期不是「补种就行」——剂次间隔是免疫程序的一部分，漏了要重新评估。"
      />

      <el-table v-loading="loading" :data="remindRows" border stripe>
        <el-table-column prop="patientName" label="姓名" width="100" />
        <el-table-column prop="vaccineName" label="疫苗" min-width="160" show-overflow-tooltip />
        <el-table-column label="剂次" width="80">
          <template #default="{ row }">第 {{ row.doseNo ?? '—' }} 剂</template>
        </el-table-column>
        <el-table-column prop="vaccinateDate" label="上次接种" width="120" />
        <el-table-column label="下次接种" width="130">
          <template #default="{ row }">
            <span :class="{ 'hp-overdue': row.overdue }">
              {{ row.nextDate }}
              <el-tag v-if="row.overdue" type="danger" size="small" style="margin-left: 4px">逾期</el-tag>
            </span>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <el-dialog v-model="dialogVisible" title="接种登记" width="540px" :close-on-click-modal="false">
      <el-form :model="form" label-width="100px">
        <el-form-item label="受种者" required>
          <el-select
            v-model="form.patientId"
            filterable
            remote
            reserve-keyword
            placeholder="输入姓名或患者号搜索"
            :remote-method="searchPatients"
            :loading="patientLoading"
            style="width: 100%"
          >
            <el-option
              v-for="p in patients"
              :key="p.id"
              :label="`${p.name} · ${p.patientNo}`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="疫苗名称" required>
          <el-input v-model="form.vaccineName" maxlength="100" />
        </el-form-item>
        <el-form-item label="疫苗批号">
          <el-input v-model="form.batchNo" placeholder="留空表示未关联库存批次" maxlength="50" />
        </el-form-item>
        <el-form-item label="剂次">
          <el-input-number v-model="form.doseNo" :min="1" :max="10" />
          <span class="hp-muted" style="margin-left: 8px; font-size: 12px">
            留空则按该疫苗的历史剂次自动推算
          </span>
        </el-form-item>
        <el-form-item label="接种日期" required>
          <el-date-picker
            v-model="form.vaccinateDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="下次接种">
          <el-date-picker
            v-model="form.nextDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">登记</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  addVaccination,
  pageVaccinations,
  vaccinationRemind,
  type VaccinationVO,
} from '@/api/health'
import { pagePatients } from '@/api/patient'
import type { PatientVO } from '@/api/types'

/**
 * 疫苗接种。
 *
 * <p>「接种提醒」单独一个页签而不是混在记录里：
 * 这个列表是公卫专员的「今天要打几个电话」清单，
 * 混在历史记录里会被翻过去。</p>
 */

const tab = ref<'list' | 'remind'>('list')
const rows = ref<VaccinationVO[]>([])
const remindRows = ref<VaccinationVO[]>([])
const remindCount = ref(0)
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)
const days = ref(30)

const query = reactive<{
  pageNum: number
  pageSize: number
  vaccineName?: string
  batchNo?: string
}>({ pageNum: 1, pageSize: 10 })

const dialogVisible = ref(false)
const patients = ref<PatientVO[]>([])
const patientLoading = ref(false)

const form = reactive<{
  patientId?: number
  vaccineName: string
  batchNo?: string
  doseNo?: number
  vaccinateDate?: string
  nextDate?: string
}>({ vaccineName: '' })

async function load() {
  loading.value = true
  try {
    const res = await pageVaccinations(query)
    rows.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadRemind() {
  loading.value = true
  try {
    remindRows.value = await vaccinationRemind(days.value)
    remindCount.value = remindRows.value.length
  } finally {
    loading.value = false
  }
}

function reload() {
  if (tab.value === 'list') {
    query.pageNum = 1
    load()
  } else {
    loadRemind()
  }
}

function onPageChange(p: number) {
  query.pageNum = p
  load()
}

function onSizeChange(s: number) {
  query.pageSize = s
  reload()
}

async function searchPatients(keyword: string) {
  patientLoading.value = true
  try {
    const res = await pagePatients({ pageNum: 1, pageSize: 20, keyword })
    patients.value = res.list
  } finally {
    patientLoading.value = false
  }
}

function openCreate() {
  form.patientId = undefined
  form.vaccineName = ''
  form.batchNo = undefined
  form.doseNo = undefined
  form.vaccinateDate = new Date().toISOString().slice(0, 10)
  form.nextDate = undefined
  patients.value = []
  dialogVisible.value = true
}

async function submit() {
  if (form.patientId == null) {
    ElMessage.warning('请选择受种者')
    return
  }
  if (!form.vaccineName.trim()) {
    ElMessage.warning('疫苗名称不能为空')
    return
  }
  if (!form.vaccinateDate) {
    ElMessage.warning('请选择接种日期')
    return
  }
  submitting.value = true
  try {
    await addVaccination({
      patientId: form.patientId,
      vaccineName: form.vaccineName.trim(),
      batchNo: form.batchNo || undefined,
      doseNo: form.doseNo,
      vaccinateDate: form.vaccinateDate,
      nextDate: form.nextDate,
    })
    ElMessage.success('接种登记成功')
    dialogVisible.value = false
    reload()
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await load()
  await loadRemind()
})
</script>

<style scoped>
.hp-overdue {
  color: #d93026;
  font-weight: 600;
}
</style>
