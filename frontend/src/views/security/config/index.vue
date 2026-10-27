<template>
  <div class="hp-page">
    <h3 class="hp-page-title">安全配置</h3>

    <el-alert
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
      title="这里的每一项改完立即生效"
      description="登录失败上限、锁定时长、会话超时都是下一次登录就生效的。把失败上限调成 1，等于任何人输错一次密码就锁号 —— 改之前先想清楚。"
    />

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="configName" label="配置项" min-width="160" />
      <el-table-column prop="configKey" label="参数键" min-width="200">
        <template #default="{ row }"><code>{{ row.configKey }}</code></template>
      </el-table-column>
      <el-table-column label="当前值" min-width="180">
        <template #default="{ row }">
          <el-tag v-if="row.configKey === 'sys.stock.warn.enable'" :type="row.configValue === 'true' ? 'success' : 'info'" size="small">
            {{ row.configValue === 'true' ? '开启' : '关闭' }}
          </el-tag>
          <span v-else class="hp-value">{{ row.configValue || '未设置' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="说明" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.remark || '—' }}</template>
      </el-table-column>
      <el-table-column prop="updateTime" label="最后修改" width="165" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">修改</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">暂无安全配置项</span>
      </template>
    </el-table>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header><span>已落地的其它安全措施</span></template>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="敏感字段加密">
          身份证、手机号 AES-256-GCM 落库，另存 SHA-256 摘要用于检索
        </el-descriptions-item>
        <el-descriptions-item label="密码存储">BCrypt，不明文、不可逆</el-descriptions-item>
        <el-descriptions-item label="登录失败锁定">
          按 sys.login.fail.limit 累计，超限锁定 sys.login.lock.minutes 分钟
        </el-descriptions-item>
        <el-descriptions-item label="令牌机制">
          access / refresh 双令牌，refresh 轮换 + 版本号强制下线
        </el-descriptions-item>
        <el-descriptions-item label="操作审计">@AuditLog 切面统一落 sys_operation_log，只追加不删除</el-descriptions-item>
        <el-descriptions-item label="越权防护">接口级 hasAuthority + 数据级 40302 双重校验</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-dialog v-model="dialogVisible" title="修改安全配置" width="460px">
      <el-form label-width="100px">
        <el-form-item label="配置项">
          <span>{{ editing?.configName }}</span>
        </el-form-item>
        <el-form-item label="参数键">
          <code>{{ editing?.configKey }}</code>
          <div class="hp-muted hp-form-tip">参数键不可修改</div>
        </el-form-item>
        <el-form-item label="参数值" required>
          <el-switch
            v-if="editing?.configKey === 'sys.stock.warn.enable'"
            v-model="boolValue"
            active-text="开启"
            inactive-text="关闭"
          />
          <el-input-number v-else-if="numeric" v-model="numValue" :min="minValue" :max="999" />
          <el-input v-else v-model="textValue" maxlength="500" />
        </el-form-item>
        <el-form-item label="说明">
          <span class="hp-muted">{{ editing?.remark || '—' }}</span>
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
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listConfigs, updateConfig, type ConfigVO } from '@/api/system'

/**
 * 安全配置。
 *
 * <p>这一页复用系统参数的接口，只展示 {@code configType = 1}（系统内置）的项 ——
 * 那些就是安全与系统级参数。单独再建一张安全配置表，
 * 会让「改一个登录策略」变成「先想清楚该去哪个页面改」。</p>
 *
 * <p><b>数值项用数字输入框、开关项用 el-switch</b>：
 * 安全参数的值是程序直接解析的字符串，让使用者手敲一个
 * 「三十」进去，存下来就是一条永远解析失败的配置。</p>
 */

const rows = ref<ConfigVO[]>([])
const loading = ref(false)
const submitting = ref(false)

const dialogVisible = ref(false)
const editing = ref<ConfigVO | null>(null)
const textValue = ref('')
const numValue = ref(0)
const boolValue = ref(false)

const numeric = computed(() => /^\d+$/.test((editing.value?.configValue || '').trim()))
const minValue = computed(() => (editing.value?.configKey.includes('limit') ? 1 : 0))

async function load() {
  loading.value = true
  try {
    rows.value = await listConfigs({ configType: 1 })
  } finally {
    loading.value = false
  }
}

function openEdit(row: ConfigVO) {
  editing.value = row
  const raw = (row.configValue || '').trim()
  if (row.configKey === 'sys.stock.warn.enable') {
    boolValue.value = raw === 'true'
  } else if (/^\d+$/.test(raw)) {
    numValue.value = Number(raw)
  } else {
    textValue.value = raw
  }
  dialogVisible.value = true
}

async function submit() {
  if (!editing.value) return
  const raw = (editing.value.configValue || '').trim()
  const value =
    editing.value.configKey === 'sys.stock.warn.enable'
      ? String(boolValue.value)
      : /^\d+$/.test(raw)
        ? String(numValue.value)
        : textValue.value.trim()

  if (!value) {
    ElMessage.warning('参数值不能为空')
    return
  }
  submitting.value = true
  try {
    await updateConfig(editing.value.id, {
      configKey: editing.value.configKey,
      configName: editing.value.configName,
      configValue: value,
      configType: editing.value.configType,
      remark: editing.value.remark,
    })
    ElMessage.success('已保存，立即生效')
    dialogVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.hp-value {
  font-weight: 600;
}

.hp-form-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
}
</style>
