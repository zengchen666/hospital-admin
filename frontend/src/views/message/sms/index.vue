<template>
  <div class="hp-page">
    <h3 class="hp-page-title">短信记录</h3>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
      title="一期未接入短信服务商"
      description="当前所有发送均落库为「失败 - 未接入短信服务商」。记录仍然完整保留：它回答的是「谁在什么时候给谁发了什么」，是合规凭证，不是调试日志。"
    />

    <div class="hp-toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="按手机号搜索"
        clearable
        style="width: 220px"
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-button :icon="Search" @click="reload">查询</el-button>
      <span class="spacer" />
      <el-button v-perm="'message:sms:send'" type="primary" :icon="Promotion" @click="openSend">
        发送短信
      </el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column prop="templateCode" label="模板编码" width="160" />
      <el-table-column prop="content" label="内容" min-width="240" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.sendStatus === 1 ? 'success' : 'danger'" size="small">
            {{ row.sendStatus === 1 ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="失败原因" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.failReason || '-' }}</template>
      </el-table-column>
      <el-table-column prop="sendTime" label="发送时间" width="170" />
      <template #empty>
        <span class="hp-muted">暂无短信记录</span>
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

    <el-dialog v-model="sendVisible" title="发送短信" width="520px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" maxlength="11" placeholder="11 位手机号" />
        </el-form-item>
        <el-form-item label="模板编码" prop="templateCode">
          <el-input v-model="form.templateCode" maxlength="50" placeholder="如 SMS_001" />
        </el-form-item>
        <el-form-item label="模板参数">
          <el-input
            v-model="form.params"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder='JSON 字符串，如 {"code":"123456"}'
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sendVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Search, Promotion } from '@element-plus/icons-vue'
import { pageSms, sendSms, type SmsSendRequest } from '@/api/message'
import type { SmsVO } from '@/api/types'

/**
 * 短信记录。
 *
 * <p><b>手机号列不做二次脱敏</b>：后端下发的已经是 {@code 138****1111}。
 * 脱敏规则必须只有一处，前端再加工一次，迟早出现「列表脱敏了但导出没有」。</p>
 *
 * <p><b>发送失败也不弹错误</b>：后端把失败原因写进 {@code failReason} 并正常返回 200，
 * 短信是主业务的副产品，不该让服务商抖动把整单卡住。前端只负责如实展示结果。</p>
 */

const rows = ref<SmsVO[]>([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)
const sendVisible = ref(false)
const formRef = ref<FormInstance>()

const query = reactive<{ pageNum: number; pageSize: number; keyword: string }>({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
})

const form = reactive<SmsSendRequest>({
  phone: '',
  templateCode: '',
  params: '',
})

const rules: FormRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  templateCode: [{ required: true, message: '请输入模板编码', trigger: 'blur' }],
  params: [
    {
      validator: (_r, value: string, callback: (e?: Error) => void) => {
        if (!value) return callback()
        try {
          JSON.parse(value)
          callback()
        } catch {
          callback(new Error('模板参数必须是合法 JSON'))
        }
      },
      trigger: 'blur',
    },
  ],
}

async function load() {
  loading.value = true
  try {
    const res = await pageSms(query)
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

function openSend() {
  Object.assign(form, { phone: '', templateCode: '', params: '' })
  sendVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const res = await sendSms(form)
    if (res.sendStatus === 1) {
      ElMessage.success('发送成功')
    } else {
      ElMessage.warning(`已记录，但发送失败：${res.failReason || '未知原因'}`)
    }
    sendVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>
