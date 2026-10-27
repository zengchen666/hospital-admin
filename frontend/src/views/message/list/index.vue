<template>
  <div class="hp-page">
    <h3 class="hp-page-title">
      站内消息
      <el-tag v-if="unread > 0" type="danger" size="small" round style="margin-left: 8px">
        {{ unread }} 条未读
      </el-tag>
    </h3>

    <div class="hp-toolbar">
      <el-radio-group v-model="typeTab">
        <el-radio-button value="all">全部</el-radio-button>
        <el-radio-button value="1">系统</el-radio-button>
        <el-radio-button value="2">待办</el-radio-button>
        <el-radio-button value="3">预警</el-radio-button>
      </el-radio-group>
      <el-radio-group v-model="readTab">
        <el-radio-button value="all">不限</el-radio-button>
        <el-radio-button value="0">未读</el-radio-button>
        <el-radio-button value="1">已读</el-radio-button>
      </el-radio-group>
      <el-input
        v-model="query.keyword"
        placeholder="按标题搜索"
        clearable
        style="width: 200px"
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-button :icon="Refresh" @click="reset">重置</el-button>
      <span class="spacer" />
      <el-button
        type="primary"
        :icon="Check"
        :disabled="unread === 0"
        @click="readAll"
      >
        全部已读
      </el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe :row-class-name="rowClass">
      <el-table-column label="" width="44">
        <template #default="{ row }">
          <span v-if="row.isRead === 0" class="dot-unread" title="未读" />
        </template>
      </el-table-column>
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag :type="typeTag(row.msgType)" size="small">{{ row.msgTypeName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="标题" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <span :class="{ 'title-unread': row.isRead === 0 }">{{ row.title }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
      <el-table-column prop="createTime" label="时间" width="170" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">查看</el-button>
          <el-button
            v-if="row.isRead === 0"
            link
            type="info"
            @click="readOne(row)"
          >
            标记已读
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">暂无消息</span>
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

    <el-dialog v-model="detailVisible" :title="detail?.title ?? '消息详情'" width="560px">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="类型">{{ detail.msgTypeName }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ detail.createTime }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          {{ detail.isRead === 1 ? '已读' : '未读' }}
        </el-descriptions-item>
      </el-descriptions>
      <div v-if="detail" class="msg-content">{{ detail.content || '（无正文）' }}</div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="detail && detail.isRead === 0"
          type="primary"
          @click="readOne(detail)"
        >
          标记已读
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Check } from '@element-plus/icons-vue'
import { markAllRead, markRead, pageMessages, unreadCount, type MessageQuery } from '@/api/message'
import type { MessageVO } from '@/api/types'

/**
 * 站内消息收件箱。
 *
 * <p><b>查询条件里没有「收件人」这一项</b>：后端强制按当前登录用户过滤，
 * 接口也不接收 userId。这里同样不给入口 —— 消息是推给个人的，
 * 谁都不该有「看看别人收到什么」的开关。</p>
 *
 * <p>未读数单独维护一个 ref：它要显示在标题栏角标上，
 * 而分页接口每次只带当前页数据，靠 rows 算不出全量未读。</p>
 */

const rows = ref<MessageVO[]>([])
const total = ref(0)
const unread = ref(0)
const loading = ref(false)
const detailVisible = ref(false)
const detail = ref<MessageVO | null>(null)

/**
 * 两个筛选 tab 用字符串哨兵值而不是 `undefined`。
 *
 * <p>Element Plus 的 radio 在 {@code value} 缺失时会回退用 {@code label}，
 * 直接绑 `{@code :value="undefined"}` 会导致初始选中态不生效 ——
 * 界面上四个按钮一个都没高亮，但查出来又是「全部」，非常费解。
 * 用 `'all'` 做「不限」，再在查询前映射回 undefined 即可。</p>
 */
const typeTab = ref<string>('all')
const readTab = ref<string>('all')

const query = reactive<MessageQuery>({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  msgType: undefined,
  isRead: undefined,
})

function typeTag(msgType: number): 'info' | 'warning' | 'danger' | 'primary' {
  if (msgType === 3) return 'danger'
  if (msgType === 2) return 'warning'
  if (msgType === 1) return 'primary'
  return 'info'
}

/** 未读行加个浅底色，比只靠前面的小圆点更好扫 */
function rowClass({ row }: { row: MessageVO }) {
  return row.isRead === 0 ? 'row-unread' : ''
}

async function load() {
  // 哨兵值 → 后端条件：'all' 表示不筛选
  query.msgType = typeTab.value === 'all' ? undefined : Number(typeTab.value)
  query.isRead = readTab.value === 'all' ? undefined : Number(readTab.value)

  loading.value = true
  try {
    const res = await pageMessages(query)
    rows.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadUnread() {
  unread.value = await unreadCount()
}

function reload() {
  query.pageNum = 1
  load()
}

function reset() {
  query.keyword = ''
  typeTab.value = 'all'
  readTab.value = 'all'
  reload()
}

watch([typeTab, readTab], reload)

function onPageChange(p: number) {
  query.pageNum = p
  load()
}

function onSizeChange(s: number) {
  query.pageSize = s
  reload()
}

function openDetail(row: MessageVO) {
  detail.value = row
  detailVisible.value = true
}

async function readOne(row: MessageVO) {
  await markRead(row.id)
  row.isRead = 1
  await loadUnread()
  ElMessage.success('已标记为已读')
  // 正在看详情的话顺手关掉，避免「已读」按钮还挂着
  if (detail.value?.id === row.id) {
    detailVisible.value = false
  }
}

async function readAll() {
  try {
    await ElMessageBox.confirm(`确定将全部 ${unread.value} 条未读消息标记为已读吗？`, '全部已读', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  const n = await markAllRead()
  ElMessage.success(`已标记 ${n} 条为已读`)
  await loadUnread()
  load()
}

onMounted(async () => {
  await load()
  await loadUnread()
})
</script>

<style scoped>
.dot-unread {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #2563eb;
}

.title-unread {
  font-weight: 600;
}

.msg-content {
  margin-top: 12px;
  padding: 12px;
  background: #f8fafc;
  border-radius: 4px;
  line-height: 1.7;
  white-space: pre-wrap;
}

:deep(.row-unread) {
  background: #f0f7ff;
}
</style>
