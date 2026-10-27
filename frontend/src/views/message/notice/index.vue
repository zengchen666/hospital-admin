<template>
  <div class="hp-page">
    <h3 class="hp-page-title">公告管理</h3>

    <div class="hp-toolbar">
      <el-select v-model="query.noticeType" placeholder="公告类型" clearable style="width: 130px" @change="reload">
        <el-option label="院内公告" :value="1" />
        <el-option label="科室公告" :value="2" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="reload">
        <el-option label="草稿" :value="0" />
        <el-option label="已发布" :value="1" />
      </el-select>
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
      <el-button v-perm="'message:notice:edit'" type="primary" :icon="Plus" @click="openCreate">
        新建公告
      </el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag :type="row.noticeType === 1 ? 'primary' : 'success'" size="small">
            {{ row.noticeTypeName }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="科室" width="120">
        <template #default="{ row }">
          {{ row.noticeType === 2 ? row.deptName || '-' : '全院' }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '已发布' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="publisherName" label="发布人" width="100" />
      <el-table-column prop="publishTime" label="发布时间" width="170" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">查看</el-button>
          <el-button
            v-if="row.status === 0"
            v-perm="'message:notice:edit'"
            link
            type="warning"
            @click="openEdit(row)"
          >
            编辑
          </el-button>
          <el-button
            v-if="row.status === 0"
            v-perm="'message:notice:edit'"
            link
            type="success"
            @click="publish(row)"
          >
            发布
          </el-button>
          <el-button
            v-if="row.status === 0"
            v-perm="'message:notice:edit'"
            link
            type="danger"
            @click="remove(row)"
          >
            删除
          </el-button>
          <el-tooltip v-if="row.status === 1" content="已发布的公告不可修改或删除" placement="top">
            <span class="hp-muted" style="font-size: 12px">已锁定</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">暂无公告</span>
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

    <!-- 新建 / 编辑 -->
    <el-dialog
      v-model="formVisible"
      :title="editing ? '编辑公告' : '新建公告'"
      width="680px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="100" />
        </el-form-item>
        <el-form-item label="类型" prop="noticeType">
          <el-radio-group v-model="form.noticeType">
            <el-radio :value="1">院内公告</el-radio>
            <el-radio :value="2">科室公告</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.noticeType === 2" label="科室" prop="deptId">
          <el-select v-model="form.deptId" placeholder="请选择科室" style="width: 100%">
            <el-option v-for="d in depts" :key="d.id" :label="d.deptName" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="正文" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="6" maxlength="2000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存为草稿</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" :title="detail?.title ?? '公告详情'" width="620px">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="类型">{{ detail.noticeTypeName }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          {{ detail.status === 1 ? '已发布' : '草稿' }}
        </el-descriptions-item>
        <el-descriptions-item label="范围">
          {{ detail.noticeType === 2 ? detail.deptName || '-' : '全院' }}
        </el-descriptions-item>
        <el-descriptions-item label="发布人">{{ detail.publisherName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发布时间">{{ detail.publishTime || '未发布' }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="detail" class="notice-content">{{ detail.content || '（无正文）' }}</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Refresh, Plus } from '@element-plus/icons-vue'
import {
  createNotice,
  deleteNotice,
  pageNotices,
  publishNotice,
  updateNotice,
  type NoticeQuery,
  type NoticeSaveRequest,
} from '@/api/message'
import type { NoticeVO } from '@/api/types'
import { listDepts, type DeptOption } from '@/api/user'

/**
 * 公告管理（管理视角）。
 *
 * <p><b>已发布的公告不给「编辑」「删除」按钮</b>，只显示「已锁定」。
 * 不是省事 —— 让按钮先出现再被后端 40902 弹回来，
 * 用户只会觉得系统坏了；按钮根本不该出现在他眼前。</p>
 *
 * <p><b>保存永远是「保存为草稿」</b>，发布是列表里的独立动作。
 * 公告是发给人看的，写一半点错了全院都收到，代价太大。</p>
 */

const rows = ref<NoticeVO[]>([])
const total = ref(0)
const depts = ref<DeptOption[]>([])
const loading = ref(false)
const submitting = ref(false)
const formVisible = ref(false)
const detailVisible = ref(false)
const editing = ref(false)
const currentId = ref<string>('')
const detail = ref<NoticeVO | null>(null)
const formRef = ref<FormInstance>()

const query = reactive<NoticeQuery>({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  noticeType: undefined,
  status: undefined,
})

const form = reactive<NoticeSaveRequest>({
  title: '',
  content: '',
  noticeType: 1,
  deptId: '',
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入公告标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入公告正文', trigger: 'blur' }],
  noticeType: [{ required: true, message: '请选择公告类型', trigger: 'change' }],
  deptId: [
    {
      validator: (_r, value: string, callback: (e?: Error) => void) => {
        if (form.noticeType === 2 && !value) {
          callback(new Error('科室公告必须指定科室'))
        } else {
          callback()
        }
      },
      trigger: 'change',
    },
  ],
}

/** 从院内切到科室时先清空科室，避免带上一次选的残留值 */
watch(
  () => form.noticeType,
  (v) => {
    if (v !== 2) {
      form.deptId = ''
    }
  },
)

async function load() {
  loading.value = true
  try {
    const res = await pageNotices(query)
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

function reset() {
  query.keyword = ''
  query.noticeType = undefined
  query.status = undefined
  reload()
}

function onPageChange(p: number) {
  query.pageNum = p
  load()
}

function onSizeChange(s: number) {
  query.pageSize = s
  reload()
}

function openCreate() {
  editing.value = false
  currentId.value = ''
  Object.assign(form, { title: '', content: '', noticeType: 1, deptId: '' })
  formVisible.value = true
}

function openEdit(row: NoticeVO) {
  editing.value = true
  currentId.value = row.id
  Object.assign(form, {
    title: row.title,
    content: row.content ?? '',
    noticeType: row.noticeType ?? 1,
    deptId: row.deptId ?? '',
  })
  formVisible.value = true
}

function openDetail(row: NoticeVO) {
  detail.value = row
  detailVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (editing.value) {
      await updateNotice(currentId.value, form)
      ElMessage.success('修改成功')
    } else {
      await createNotice(form)
      ElMessage.success('已保存为草稿，确认无误后可点击「发布」')
    }
    formVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function publish(row: NoticeVO) {
  try {
    await ElMessageBox.confirm(
      `发布后全院可见且不可修改、不可删除，确定发布「${row.title}」吗？`,
      '发布确认',
      { type: 'warning', confirmButtonText: '发布', cancelButtonText: '再看看' },
    )
  } catch {
    return
  }
  await publishNotice(row.id)
  ElMessage.success('已发布')
  load()
}

async function remove(row: NoticeVO) {
  try {
    await ElMessageBox.confirm(`确定删除公告「${row.title}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  await deleteNotice(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(async () => {
  await load()
  // 科室下拉只在「科室公告」时用得到，加载失败也不该挡住列表
  try {
    depts.value = await listDepts()
  } catch {
    depts.value = []
  }
})
</script>

<style scoped>
.notice-content {
  margin-top: 12px;
  padding: 12px;
  background: #f8fafc;
  border-radius: 4px;
  line-height: 1.8;
  white-space: pre-wrap;
}
</style>
