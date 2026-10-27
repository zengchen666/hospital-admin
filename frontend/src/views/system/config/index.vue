<template>
  <div class="hp-page">
    <h3 class="hp-page-title">系统参数</h3>

    <el-alert
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
      title="参数改动立即生效，且会记入审计日志"
      description="这里的值会被业务代码直接读取（如预约提前天数、上班时间）。改错一个值可能让全院某个功能同时失真，改之前先确认影响范围。"
    />

    <div class="hp-toolbar">
      <el-input
        v-model="keyword"
        placeholder="参数名 / 参数键"
        clearable
        style="width: 220px"
        @clear="load"
        @keyup.enter="load"
      />
      <el-select v-model="typeFilter" placeholder="类型" clearable style="width: 130px" @change="load">
        <el-option label="系统内置" :value="1" />
        <el-option label="业务参数" :value="2" />
      </el-select>
      <el-button :icon="Refresh" :loading="loading" @click="load">查询</el-button>
      <span class="spacer" />
      <el-button v-perm="'system:config:edit'" type="primary" :icon="Plus" @click="openCreate">新增参数</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="configName" label="参数名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="configKey" label="参数键" min-width="180">
        <template #default="{ row }"><code>{{ row.configKey }}</code></template>
      </el-table-column>
      <el-table-column prop="configValue" label="参数值" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.configValue">{{ row.configValue }}</span>
          <span v-else class="hp-muted">未设置</span>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="row.configType === 1 ? 'danger' : 'info'" size="small">
            {{ row.configType === 1 ? '系统内置' : '业务参数' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="说明" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.remark || '—' }}</template>
      </el-table-column>
      <el-table-column prop="updateTime" label="最后修改" width="165" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button v-perm="'system:config:edit'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button
            v-perm="'system:config:edit'"
            link
            type="danger"
            :disabled="row.configType === 1"
            @click="remove(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">暂无系统参数</span>
      </template>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑参数' : '新增参数'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="参数键" required>
          <el-input
            v-model="form.configKey"
            :disabled="!!editing"
            placeholder="小写字母 + 下划线/点，如 appointment.advance_days"
          />
          <div v-if="editing" class="hp-muted hp-form-tip">参数键不可修改</div>
        </el-form-item>
        <el-form-item label="参数名称" required>
          <el-input v-model="form.configName" maxlength="100" />
        </el-form-item>
        <el-form-item label="参数值">
          <el-input v-model="form.configValue" maxlength="500" placeholder="一律按字符串存，读取方自行转换" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.configType" :disabled="!!editing">
            <el-radio :value="2">业务参数</el-radio>
            <el-radio :value="1">系统内置</el-radio>
          </el-radio-group>
          <div class="hp-muted hp-form-tip">系统内置参数不可删除</div>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" />
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createConfig,
  deleteConfig,
  listConfigs,
  updateConfig,
  type ConfigVO,
} from '@/api/system'

/**
 * 系统参数。
 *
 * <p>「系统内置」参数在界面上禁用删除按钮并置灰 ——
 * 不是等点下去再报错，而是<b>让规则在点击之前就可见</b>。
 * 它们是启动期就被读取的配置，删掉等于让系统下次起不来。</p>
 */

const rows = ref<ConfigVO[]>([])
const loading = ref(false)
const submitting = ref(false)
const keyword = ref('')
const typeFilter = ref<number | undefined>(undefined)

const dialogVisible = ref(false)
const editing = ref<ConfigVO | null>(null)
const form = reactive({
  configKey: '',
  configName: '',
  configValue: '',
  configType: 2,
  remark: '',
})

async function load() {
  loading.value = true
  try {
    const kw = keyword.value.trim()
    rows.value = await listConfigs({
      configName: kw || undefined,
      configKey: kw || undefined,
      configType: typeFilter.value,
    })
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  form.configKey = ''
  form.configName = ''
  form.configValue = ''
  form.configType = 2
  form.remark = ''
  dialogVisible.value = true
}

function openEdit(row: ConfigVO) {
  editing.value = row
  form.configKey = row.configKey
  form.configName = row.configName
  form.configValue = row.configValue || ''
  form.configType = row.configType
  form.remark = row.remark || ''
  dialogVisible.value = true
}

async function submit() {
  if (!form.configName.trim()) {
    ElMessage.warning('请填写参数名称')
    return
  }
  submitting.value = true
  try {
    const body = {
      configKey: form.configKey,
      configName: form.configName.trim(),
      configValue: form.configValue || undefined,
      configType: form.configType,
      remark: form.remark || undefined,
    }
    if (editing.value) {
      await updateConfig(editing.value.id, body)
      ElMessage.success('参数已更新，立即生效')
    } else {
      if (!form.configKey.trim()) {
        ElMessage.warning('请填写参数键')
        return
      }
      await createConfig(body)
      ElMessage.success('参数已新增')
    }
    dialogVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

async function remove(row: ConfigVO) {
  await ElMessageBox.confirm(`确认删除参数「${row.configName}」？`, '删除参数', { type: 'warning' })
  await deleteConfig(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.hp-form-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
}
</style>
