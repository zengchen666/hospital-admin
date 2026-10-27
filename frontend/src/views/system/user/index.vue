<template>
  <div class="hp-page">
    <h3 class="hp-page-title">用户管理</h3>

    <div class="hp-toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="账号 / 姓名 / 手机号"
        clearable
        style="width: 240px"
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="reload">
        <el-option label="启用" :value="1" />
        <el-option label="禁用" :value="0" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="reload">查询</el-button>
      <span class="spacer" />
      <el-button v-perm="'system:user:add'" type="primary" :icon="Plus" @click="openCreate">
        新增用户
      </el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="username" label="账号" width="140" />
      <el-table-column prop="realName" label="姓名" width="120" />
      <el-table-column prop="roleName" label="角色" width="120" />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button v-perm="'system:user:edit'" link type="primary" @click="openEdit(row)">
            编辑
          </el-button>
          <el-button
            v-perm="'system:user:edit'"
            link
            :type="row.status === 1 ? 'warning' : 'success'"
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
          <el-button v-perm="'system:user:reset'" link type="info" @click="resetPwd(row)">
            重置密码
          </el-button>
          <el-button v-perm="'system:user:delete'" link type="danger" @click="remove(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">暂无用户数据</span>
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

    <el-dialog v-model="formVisible" :title="editing ? '编辑用户' : '新增用户'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" :disabled="editing" placeholder="字母开头，4-50 位" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" maxlength="50" />
        </el-form-item>
        <el-form-item v-if="!editing" label="初始密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="6-64 位" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" maxlength="11" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" maxlength="50" />
        </el-form-item>
        <el-form-item label="角色" prop="roleId">
          <el-select v-model="form.roleId" placeholder="请选择角色" style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import {
  pageUsers,
  createUser,
  updateUser,
  deleteUser,
  changeUserStatus,
  resetPassword,
  type UserQuery,
  type UserSaveRequest,
} from '@/api/user'
import { get } from '@/api/request'
import type { SysUserVO } from '@/api/types'

/**
 * 用户管理。
 *
 * <p>注意「启用/禁用」与「重置密码」走的是独立权限码：
 * {@code system:user:edit} 能改资料，但不一定能改密码（{@code system:user:reset}）。
 * 前端按钮按权限码显隐，后端 {@code @PreAuthorize} 做真拦截。</p>
 *
 * <p>另外后端规则：不能禁用/删除自己，也不能动超管 —— 这类请求会返回 40302/40902，
 * 前端只负责把提示说清楚。</p>
 */

interface RoleOption {
  id: string
  roleCode: string
  roleName: string
}

const rows = ref<SysUserVO[]>([])
const roles = ref<RoleOption[]>([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)
const formVisible = ref(false)
const editing = ref(false)
const currentId = ref('')
const formRef = ref<FormInstance>()

const query = reactive<UserQuery>({ pageNum: 1, pageSize: 10, keyword: '', status: undefined })
const form = reactive<UserSaveRequest>({
  username: '',
  realName: '',
  phone: '',
  email: '',
  roleId: undefined,
  status: 1,
  password: '',
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]{3,49}$/, message: '字母开头，4-50 位字母/数字/下划线', trigger: 'blur' },
  ],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  password: [{ min: 6, max: 64, message: '密码长度需在 6-64 之间', trigger: 'blur' }],
  phone: [{ pattern: /^$|^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
}

async function load() {
  loading.value = true
  try {
    const res = await pageUsers(query)
    rows.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  try {
    roles.value = await get<RoleOption[]>('/system/role/list')
  } catch {
    roles.value = []
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

function openCreate() {
  editing.value = false
  currentId.value = ''
  Object.assign(form, {
    username: '',
    realName: '',
    phone: '',
    email: '',
    roleId: undefined,
    status: 1,
    password: '',
  })
  formVisible.value = true
}

function openEdit(row: SysUserVO) {
  editing.value = true
  currentId.value = row.id
  Object.assign(form, {
    username: row.username,
    realName: row.realName,
    phone: row.phone ?? '',
    email: row.email ?? '',
    roleId: row.roleId,
    status: row.status,
    password: '',
  })
  formVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editing.value) {
      // 编辑不带 password，改密走独立接口 —— 避免「改个手机号把密码冲掉」
      const payload: UserSaveRequest = {
        username: form.username,
        realName: form.realName,
        phone: form.phone,
        email: form.email,
        roleId: form.roleId,
        status: form.status,
      }
      await updateUser(currentId.value, payload)
      ElMessage.success('修改成功')
    } else {
      await createUser(form)
      ElMessage.success('新增成功')
    }
    formVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row: SysUserVO) {
  const next = row.status === 1 ? 0 : 1
  await changeUserStatus(row.id, next)
  ElMessage.success(next === 1 ? '已启用' : '已禁用（该用户当前令牌立即失效）')
  load()
}

async function resetPwd(row: SysUserVO) {
  const { value } = await ElMessageBox.prompt(
    `将为「${row.realName}」重置密码，重置后其当前登录令牌会立即失效。`,
    '重置密码',
    { inputPattern: /^.{6,64}$/, inputErrorMessage: '密码长度需在 6-64 之间' },
  ).catch(() => ({ value: null as string | null }))
  if (!value) return
  await resetPassword(row.id, value)
  ElMessage.success('密码已重置')
}

async function remove(row: SysUserVO) {
  try {
    await ElMessageBox.confirm(`确定删除用户「${row.username}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  await deleteUser(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(() => {
  load()
  loadRoles()
})
</script>
