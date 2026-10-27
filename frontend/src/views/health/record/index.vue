<template>
  <div class="hp-page">
    <h3 class="hp-page-title">健康档案</h3>

    <div class="hp-toolbar">
      <el-input
        v-model="query.chronicDisease"
        placeholder="慢病关键字"
        clearable
        style="width: 150px"
        @clear="reload"
        @keyup.enter="reload"
      />
      <el-select v-model="query.smoking" placeholder="是否吸烟" clearable style="width: 120px" @change="reload">
        <el-option label="否" :value="0" />
        <el-option label="是" :value="1" />
      </el-select>
      <el-button :icon="Refresh" :loading="loading" @click="reload">查询</el-button>
      <span class="spacer" />
      <el-button v-perm="'health:record:edit'" type="primary" :icon="Plus" @click="openCreate">
        建档
      </el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="recordNo" label="档案号" width="160" />
      <el-table-column prop="patientName" label="姓名" width="100" />
      <el-table-column label="性别" width="70">
        <template #default="{ row }">{{ row.gender === 1 ? '男' : '女' }}</template>
      </el-table-column>
      <el-table-column prop="age" label="年龄" width="70" />
      <el-table-column label="慢病" min-width="200">
        <template #default="{ row }">
          <template v-if="row.chronicDisease">
            <el-tag
              v-for="d in splitTags(row.chronicDisease)"
              :key="d"
              type="warning"
              size="small"
              style="margin-right: 4px"
            >
              {{ d }}
            </el-tag>
          </template>
          <span v-else class="hp-muted">无</span>
        </template>
      </el-table-column>
      <el-table-column label="吸烟" width="80">
        <template #default="{ row }">{{ row.smoking === 1 ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="饮酒" width="80">
        <template #default="{ row }">{{ row.drinking === 1 ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column prop="exercise" label="运动频率" width="120" />
      <el-table-column prop="createTime" label="建档时间" width="170" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button v-perm="'health:record:edit'" link type="primary" @click="openEdit(row)">
            编辑
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">暂无健康档案</span>
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

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑健康档案' : '新建健康档案'" width="560px">
      <el-form :model="form" label-width="96px">
        <el-form-item label="患者" required>
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
        <el-form-item label="慢病">
          <el-select v-model="chronicList" multiple filterable allow-create default-first-option
                     placeholder="可多选或直接输入" style="width: 100%">
            <el-option v-for="d in COMMON_CHRONIC" :key="d" :label="d" :value="d" />
          </el-select>
        </el-form-item>
        <el-form-item label="吸烟">
          <el-radio-group v-model="form.smoking">
            <el-radio :value="0">否</el-radio>
            <el-radio :value="1">是</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="饮酒">
          <el-radio-group v-model="form.drinking">
            <el-radio :value="0">否</el-radio>
            <el-radio :value="1">是</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="运动频率">
          <el-select v-model="form.exercise" clearable style="width: 100%">
            <el-option v-for="e in EXERCISE_OPTIONS" :key="e" :label="e" :value="e" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createHealthRecord,
  pageHealthRecords,
  updateHealthRecord,
  type HealthRecordVO,
} from '@/api/health'
import { pagePatients } from '@/api/patient'
import type { PatientVO } from '@/api/types'

/**
 * 居民健康档案。
 *
 * <p>慢病用标签多选而不是一个文本框：慢病是「有没有高血压」这种可枚举的事实，
 * 自由文本写「血压有点高」就没法做统计与随访筛选了。
 * 但仍保留 allow-create —— 公卫遇到的病种比下拉里那几个多。</p>
 */

const COMMON_CHRONIC = ['高血压', '糖尿病', '冠心病', '慢性阻塞性肺疾病', '脑卒中', '恶性肿瘤']
const EXERCISE_OPTIONS = ['几乎不运动', '每周 1-2 次', '每周 3-5 次', '每天']

const rows = ref<HealthRecordVO[]>([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)

const query = reactive<{
  pageNum: number
  pageSize: number
  chronicDisease?: string
  smoking?: number
}>({ pageNum: 1, pageSize: 10 })

const dialogVisible = ref(false)
const editing = ref<HealthRecordVO | null>(null)
const chronicList = ref<string[]>([])

const patients = ref<PatientVO[]>([])
const patientLoading = ref(false)

const form = reactive<{
  patientId?: number
  smoking: number
  drinking: number
  exercise?: string
}>({ smoking: 0, drinking: 0 })

async function load() {
  loading.value = true
  try {
    const res = await pageHealthRecords(query)
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

function splitTags(v?: string) {
  return v ? v.split(/[,，]/).map((x) => x.trim()).filter(Boolean) : []
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
  editing.value = null
  form.patientId = undefined
  form.smoking = 0
  form.drinking = 0
  form.exercise = undefined
  chronicList.value = []
  patients.value = []
  dialogVisible.value = true
}

function openEdit(row: HealthRecordVO) {
  editing.value = row
  form.patientId = row.patientId
  form.smoking = row.smoking ?? 0
  form.drinking = row.drinking ?? 0
  form.exercise = row.exercise
  chronicList.value = splitTags(row.chronicDisease)
  patients.value = []
  dialogVisible.value = true
}

async function submit() {
  if (form.patientId == null) {
    ElMessage.warning('请选择患者')
    return
  }
  submitting.value = true
  try {
    const body = {
      patientId: form.patientId,
      chronicDisease: chronicList.value.join(',') || undefined,
      smoking: form.smoking,
      drinking: form.drinking,
      exercise: form.exercise,
    }
    if (editing.value) {
      await updateHealthRecord(editing.value.id, body)
      ElMessage.success('档案已更新')
    } else {
      await createHealthRecord(body)
      ElMessage.success('建档成功')
    }
    dialogVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>
