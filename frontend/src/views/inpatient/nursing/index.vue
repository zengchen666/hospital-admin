<template>
  <div class="hp-page">
    <h3 class="hp-page-title">护理记录</h3>

    <div class="hp-toolbar">
      <el-select
        v-model="admissionId"
        filterable
        placeholder="选择在院患者"
        style="width: 300px"
        @change="loadRecords"
      >
        <el-option
          v-for="a in admissions"
          :key="a.id"
          :label="`${a.admissionNo} · ${a.patientName}（${a.bedNo || '未分床'}）`"
          :value="a.id"
        />
      </el-select>
      <el-button :icon="Refresh" :loading="loading" @click="loadRecords">刷新</el-button>
      <span class="spacer" />
      <el-button
        v-perm="'inpatient:nursing:add'"
        type="primary"
        :icon="Plus"
        :disabled="!admissionId"
        @click="openCreate"
      >
        新增记录
      </el-button>
    </div>

    <el-empty v-if="!admissionId" description="请先选择在院患者" />

    <template v-else>
      <el-radio-group v-model="recordType" style="margin-bottom: 12px" @change="loadRecords">
        <el-radio-button :value="1">护理记录</el-radio-button>
        <el-radio-button :value="2">体温单</el-radio-button>
        <el-radio-button :value="3">生命体征</el-radio-button>
      </el-radio-group>

      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column prop="recordTime" label="记录时间" width="170" />
        <template v-if="recordType === 1">
          <el-table-column prop="content" label="护理内容" min-width="320" show-overflow-tooltip />
          <el-table-column prop="nurseName" label="记录护士" width="110" />
        </template>
        <template v-else>
          <el-table-column v-if="recordType === 2" prop="temperature" label="体温(℃)" width="100">
            <template #default="{ row }">
              <span :class="tempClass(row.temperature)">{{ row.temperature ?? '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="pulse" label="脉搏(次/分)" width="110" />
          <el-table-column prop="breath" label="呼吸(次/分)" width="110" />
          <el-table-column prop="bloodPressure" label="血压" width="110" />
          <el-table-column prop="spo2" label="血氧(%)" width="100" />
          <el-table-column v-if="recordType === 3" label="出入量(ml)" width="130">
            <template #default="{ row }">
              {{ row.intake ?? '-' }} / {{ row.output ?? '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="content" label="备注" min-width="180" show-overflow-tooltip />
          <el-table-column prop="nurseName" label="记录护士" width="110" />
        </template>
        <template #empty>
          <span class="hp-muted">暂无{{ typeName }}记录</span>
        </template>
      </el-table>
    </template>

    <el-dialog v-model="formVisible" :title="`新增${typeName}`" width="620px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="记录类型" prop="recordType">
          <el-radio-group v-model="form.recordType">
            <el-radio-button :value="1">护理记录</el-radio-button>
            <el-radio-button :value="2">体温单</el-radio-button>
            <el-radio-button :value="3">生命体征</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="记录时间">
          <el-date-picker
            v-model="form.recordTime"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="不填则取当前时间"
          />
        </el-form-item>

        <template v-if="form.recordType === 1">
          <el-form-item label="护理内容" prop="content">
            <el-input
              v-model="form.content"
              type="textarea"
              :rows="4"
              maxlength="2000"
              show-word-limit
              placeholder="如：协助患者翻身，皮肤完整无压红"
            />
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="体温(℃)" prop="temperature">
            <el-input-number v-model="form.temperature" :min="25" :max="45" :precision="1" :step="0.1" />
          </el-form-item>
          <el-form-item label="脉搏(次/分)">
            <el-input-number v-model="form.pulse" :min="0" :max="300" />
          </el-form-item>
          <el-form-item label="呼吸(次/分)">
            <el-input-number v-model="form.breath" :min="0" :max="100" />
          </el-form-item>
          <el-form-item label="血压">
            <el-input v-model="form.bloodPressure" style="width: 160px" placeholder="120/80" />
          </el-form-item>
          <el-form-item label="血氧(%)">
            <el-input-number v-model="form.spo2" :min="0" :max="100" />
          </el-form-item>
          <el-form-item v-if="form.recordType === 3" label="入量(ml)">
            <el-input-number v-model="form.intake" :min="0" />
          </el-form-item>
          <el-form-item v-if="form.recordType === 3" label="出量(ml)">
            <el-input-number v-model="form.output" :min="0" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.content" type="textarea" :rows="2" maxlength="2000" />
          </el-form-item>
        </template>
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
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Refresh, Plus } from '@element-plus/icons-vue'
import {
  addNursing,
  pageAdmissions,
  pageNursing,
  type AdmissionVO,
  type NursingRequest,
  type NursingVO,
} from '@/api/inpatient'

/**
 * 护理记录（含体温单、生命体征）。
 *
 * <p><b>三类记录共用一张表，界面必须带类型过滤</b>：后端 biz_nursing_record 用
 * record_type 区分，不做过滤的话体温曲线上会混进「今日协助翻身两次」这种文本记录，
 * 而且看起来像乱码而不是错误 —— 护士踩到才会发现。所以类型切换不是「筛选」，
 * 是换一种完全不同的视图（列都不一样）。</p>
 *
 * <p><b>体温异常标红</b>：≥37.3 是临床发热阈值，≥39 属高热。
 * 只是颜色区分，不做任何自动预警 —— 判断交给护士，界面只负责让人一眼看到。</p>
 */

const TYPE_NAMES = ['', '护理记录', '体温单', '生命体征']

const admissions = ref<AdmissionVO[]>([])
const records = ref<NursingVO[]>([])
const admissionId = ref('')
const recordType = ref(1)
const loading = ref(false)
const submitting = ref(false)
const formVisible = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<NursingRequest>({
  admissionId: '',
  recordType: 1,
})

const rules: FormRules = {
  content: [{ required: true, message: '请输入护理内容', trigger: 'blur' }],
}

const typeName = computed(() => TYPE_NAMES[recordType.value] ?? '')

/** 体温分级：<37.3 正常；37.3~38.9 发热；≥39 高热 */
function tempClass(t?: number) {
  if (t == null) return ''
  if (t >= 39) return 'hp-text-danger'
  if (t >= 37.3) return 'hp-text-warn'
  return ''
}

async function loadRecords() {
  if (!admissionId.value) {
    records.value = []
    return
  }
  loading.value = true
  try {
    const res = await pageNursing({
      admissionId: admissionId.value,
      recordType: recordType.value,
      pageSize: 200,
    })
    records.value = res.list
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    admissionId: admissionId.value,
    recordType: recordType.value,
    recordTime: undefined,
    temperature: undefined,
    pulse: undefined,
    breath: undefined,
    bloodPressure: '',
    spo2: undefined,
    intake: undefined,
    output: undefined,
    content: '',
  })
  formVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await addNursing(form)
    ElMessage.success('记录已保存')
    formVisible.value = false
    recordType.value = form.recordType
    await loadRecords()
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  const res = await pageAdmissions({ status: 0, pageSize: 200 })
  admissions.value = res.list
})
</script>

<style scoped>
.hp-text-danger {
  color: var(--el-color-danger);
  font-weight: 600;
}
.hp-text-warn {
  color: var(--el-color-warning);
  font-weight: 600;
}
</style>
