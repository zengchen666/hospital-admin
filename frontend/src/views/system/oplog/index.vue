<template>
  <div class="hp-page">
    <h3 class="hp-page-title">操作日志</h3>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
      title="审计日志只追加，不提供删除"
      description="一条能被删掉的审计日志等于没有审计 —— 出事之后第一件事就是看「是谁删了日志」，而这个问题本身已经没有答案了。"
    />

    <div class="hp-toolbar">
      <el-input
        v-model="query.username"
        placeholder="操作人"
        clearable
        style="width: 130px"
        @clear="reload"
        @keyup.enter="reload"
      />
      <el-input
        v-model="query.operation"
        placeholder="操作名"
        clearable
        style="width: 150px"
        @clear="reload"
        @keyup.enter="reload"
      />
      <el-input
        v-model="query.requestUri"
        placeholder="请求 URI"
        clearable
        style="width: 180px"
        @clear="reload"
        @keyup.enter="reload"
      />
      <el-select v-model="query.status" placeholder="结果" clearable style="width: 110px" @change="reload">
        <el-option label="成功" :value="1" />
        <el-option label="失败" :value="0" />
      </el-select>
      <el-date-picker
        v-model="range"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        :shortcuts="SHORTCUTS"
        style="width: 250px"
        @change="onRangeChange"
      />
      <el-button :icon="Refresh" :loading="loading" @click="reload">查询</el-button>
      <span class="spacer" />
      <el-button v-perm="'security:log:export'" :icon="Download" @click="doExport">导出</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe size="small">
      <el-table-column prop="username" label="操作人" width="110" />
      <el-table-column prop="module" label="模块" width="110" />
      <el-table-column prop="operation" label="操作" min-width="140" show-overflow-tooltip />
      <el-table-column prop="requestUri" label="请求 URI" min-width="180" show-overflow-tooltip />
      <el-table-column prop="ip" label="IP" width="130" />
      <el-table-column label="结果" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="耗时" width="90" align="right">
        <template #default="{ row }">
          <span :class="{ 'hp-slow': (row.costTime ?? 0) > 1000 }">{{ row.costTime ?? '—' }} ms</span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="时间" width="165" />
      <el-table-column label="详情" width="80" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">查看</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">暂无操作日志</span>
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

    <el-dialog v-model="detailVisible" title="操作详情" width="760px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="操作人">{{ detail?.username || '—' }}</el-descriptions-item>
        <el-descriptions-item label="模块">{{ detail?.module || '—' }}</el-descriptions-item>
        <el-descriptions-item label="操作">{{ detail?.operation || '—' }}</el-descriptions-item>
        <el-descriptions-item label="结果">
          <el-tag :type="detail?.status === 1 ? 'success' : 'danger'" size="small">
            {{ detail?.status === 1 ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="请求 URI" :span="2">{{ detail?.requestUri || '—' }}</el-descriptions-item>
        <el-descriptions-item label="方法" :span="2">{{ detail?.method || '—' }}</el-descriptions-item>
        <el-descriptions-item label="IP" :span="2">{{ detail?.ip || '—' }}</el-descriptions-item>
        <el-descriptions-item label="错误信息" :span="2">
          <span v-if="detail?.errorMsg" class="hp-error">{{ detail.errorMsg }}</span>
          <span v-else class="hp-muted">—</span>
        </el-descriptions-item>
      </el-descriptions>

      <div style="margin-top: 12px">
        <div class="hp-muted" style="margin-bottom: 4px; font-size: 12px">
          请求参数（敏感字段已由后端切面脱敏）
        </div>
        <pre class="hp-params">{{ detail?.params || '—' }}</pre>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Refresh, Download } from '@element-plus/icons-vue'
import { exportOperationLogs, pageOperationLogs, type OperationLogVO } from '@/api/system'
import { defaultRange, SHORTCUTS } from '@/views/report/range'

/**
 * 操作日志（审计）。
 *
 * <p>详情页直接展示后端已脱敏的参数原文，<b>不在前端再脱一次</b>：
 * 审计的价值就在于能还原「当时到底提交了什么」，
 * 二次脱敏会把「谁把药品价格从 100 改成 1」变成「谁改了价格」。</p>
 */

const rows = ref<OperationLogVO[]>([])
const total = ref(0)
const loading = ref(false)
const range = ref<string[]>(defaultRange())

const query = reactive<{
  pageNum: number
  pageSize: number
  username?: string
  operation?: string
  requestUri?: string
  status?: number
  startTime?: string
  endTime?: string
}>({ pageNum: 1, pageSize: 20 })

const detailVisible = ref(false)
const detail = ref<OperationLogVO | null>(null)

function onRangeChange() {
  query.startTime = range.value?.[0]
  query.endTime = range.value?.[1]
  reload()
}

async function load() {
  loading.value = true
  try {
    const res = await pageOperationLogs({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      username: query.username || undefined,
      operation: query.operation || undefined,
      requestUri: query.requestUri || undefined,
      status: query.status,
      startTime: query.startTime,
      endTime: query.endTime,
    })
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

function openDetail(row: OperationLogVO) {
  detail.value = row
  detailVisible.value = true
}

async function doExport() {
  await exportOperationLogs({
    username: query.username || undefined,
    operation: query.operation || undefined,
    requestUri: query.requestUri || undefined,
    status: query.status,
    startTime: query.startTime,
    endTime: query.endTime,
  })
}

onMounted(load)
</script>

<style scoped>
.hp-slow {
  color: #d93026;
  font-weight: 600;
}

.hp-error {
  color: #d93026;
}

.hp-params {
  max-height: 240px;
  margin: 0;
  padding: 10px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}
</style>
