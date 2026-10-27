<template>
  <div class="hp-page">
    <h3 class="hp-page-title">数据字典</h3>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
      title="字典类型一经使用不可修改"
      description="dictType 会被业务代码直接引用（如「支付方式」「婚姻状况」）。在这里改名等于把所有引用一次性弄断，而编译期不会有任何提示 —— 变更必须连同代码一起发版。"
    />

    <el-row :gutter="16">
      <!-- 左：字典列表 -->
      <el-col :span="9">
        <el-card shadow="never">
          <template #header>
            <div class="hp-card-head">
              <span>字典类型</span>
              <el-button v-perm="'system:dict:edit'" type="primary" :icon="Plus" size="small" @click="openDictCreate">
                新增
              </el-button>
            </div>
          </template>

          <el-input v-model="keyword" placeholder="名称 / 类型" clearable style="margin-bottom: 8px" />
          <el-menu :default-active="currentId || ''" @select="onSelect">
            <el-menu-item v-for="d in filteredDicts" :key="d.id" :index="d.id">
              <span class="hp-dict-name">{{ d.dictName }}</span>
              <el-tag size="small" type="info">{{ d.dictType }}</el-tag>
              <el-tag v-if="d.status !== 1" size="small" type="danger">停用</el-tag>
              <span class="hp-dict-count">{{ d.items?.length || 0 }}</span>
            </el-menu-item>
          </el-menu>
          <el-empty v-if="!filteredDicts.length" description="暂无字典" :image-size="60" />
        </el-card>
      </el-col>

      <!-- 右：字典项 -->
      <el-col :span="15">
        <el-card v-if="current" shadow="never">
          <template #header>
            <div class="hp-card-head">
              <span>
                {{ current.dictName }}
                <el-tag size="small" type="info">{{ current.dictType }}</el-tag>
              </span>
              <div>
                <el-button v-perm="'system:dict:edit'" :icon="Edit" size="small" @click="openDictEdit(current)">
                  编辑字典
                </el-button>
                <el-button
                  v-perm="'system:dict:edit'"
                  type="danger"
                  :icon="Delete"
                  size="small"
                  @click="removeDict(current)"
                >
                  删除
                </el-button>
                <el-button v-perm="'system:dict:edit'" type="primary" :icon="Plus" size="small" @click="openItemCreate">
                  新增字典项
                </el-button>
              </div>
            </div>
          </template>

          <el-table :data="current.items || []" border stripe size="small">
            <el-table-column prop="itemLabel" label="名称" min-width="120" />
            <el-table-column prop="itemValue" label="取值" min-width="120">
              <template #default="{ row }"><code>{{ row.itemValue }}</code></template>
            </el-table-column>
            <el-table-column prop="sort" label="排序" width="70" align="right" />
            <el-table-column prop="cssClass" label="样式类" min-width="110">
              <template #default="{ row }">{{ row.cssClass || '—' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                  {{ row.status === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button v-perm="'system:dict:edit'" link type="primary" @click="openItemEdit(row)">编辑</el-button>
                <el-button v-perm="'system:dict:edit'" link type="danger" @click="removeItem(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <span class="hp-muted">该字典下暂无字典项</span>
            </template>
          </el-table>
        </el-card>

        <el-empty v-else description="从左侧选择一个字典类型" />
      </el-col>
    </el-row>

    <!-- 字典编辑 -->
    <el-dialog v-model="dictVisible" :title="editingDict ? '编辑字典' : '新增字典'" width="460px">
      <el-form label-width="90px">
        <el-form-item label="字典类型" required>
          <el-input
            v-model="dictForm.dictType"
            :disabled="!!editingDict"
            placeholder="小写字母 + 下划线，如 pay_type"
          />
          <div v-if="editingDict" class="hp-muted hp-form-tip">字典类型不可修改</div>
        </el-form-item>
        <el-form-item label="字典名称" required>
          <el-input v-model="dictForm.dictName" maxlength="50" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dictForm.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="dictForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dictVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitDict">保存</el-button>
      </template>
    </el-dialog>

    <!-- 字典项编辑 -->
    <el-dialog v-model="itemVisible" :title="editingItem ? '编辑字典项' : '新增字典项'" width="460px">
      <el-form label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="itemForm.itemLabel" maxlength="50" placeholder="给人看的文字，如「已婚」" />
        </el-form-item>
        <el-form-item label="取值" required>
          <el-input v-model="itemForm.itemValue" maxlength="50" placeholder="落库的值，如 1" />
          <div class="hp-muted hp-form-tip">同一字典下取值不可重复</div>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="itemForm.sort" :min="0" :step="1" />
        </el-form-item>
        <el-form-item label="样式类">
          <el-input v-model="itemForm.cssClass" maxlength="50" placeholder="如 success / danger" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="itemForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitItem">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus } from '@element-plus/icons-vue'
import {
  createDict,
  createDictItem,
  deleteDict,
  deleteDictItem,
  listDicts,
  updateDict,
  updateDictItem,
  type DictItemVO,
  type DictVO,
} from '@/api/system'

/**
 * 数据字典。
 *
 * <p>页面刻意把「字典类型」和「字典项」做成左右两栏：
 * 字典的本质是一张「类型 → 若干取值」的二维表，
 * 合成一张大表后，改一个取值要在满屏数据里找半天。</p>
 *
 * <p>编辑弹窗里 dictType 是禁用的 —— 这不是偷懒，
 * 是<b>让「不能改」这件事在界面上可见</b>，而不是等人改完再报错。</p>
 */

const dicts = ref<DictVO[]>([])
const keyword = ref('')
const currentId = ref<string>('')
const submitting = ref(false)

const dictVisible = ref(false)
const editingDict = ref<DictVO | null>(null)
const dictForm = reactive({ dictType: '', dictName: '', remark: '', status: 1 })

const itemVisible = ref(false)
const editingItem = ref<DictItemVO | null>(null)
const itemForm = reactive({ itemLabel: '', itemValue: '', sort: 0, cssClass: '', status: 1 })

const filteredDicts = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return dicts.value
  return dicts.value.filter(
    (d) => d.dictName.toLowerCase().includes(kw) || d.dictType.toLowerCase().includes(kw),
  )
})

const current = computed(() => dicts.value.find((d) => d.id === currentId.value) || null)

async function load() {
  const prevId = currentId.value
  dicts.value = await listDicts()
  // 刷新后保持选中，避免改完一个字典项就被弹回空白页
  if (prevId && dicts.value.some((d) => d.id === prevId)) {
    currentId.value = prevId
  } else if (dicts.value.length && !currentId.value) {
    currentId.value = dicts.value[0].id
  }
}

function onSelect(id: string) {
  currentId.value = id
}

function openDictCreate() {
  editingDict.value = null
  dictForm.dictType = ''
  dictForm.dictName = ''
  dictForm.remark = ''
  dictForm.status = 1
  dictVisible.value = true
}

function openDictEdit(row: DictVO) {
  editingDict.value = row
  dictForm.dictType = row.dictType
  dictForm.dictName = row.dictName
  dictForm.remark = row.remark || ''
  dictForm.status = row.status
  dictVisible.value = true
}

async function submitDict() {
  if (!dictForm.dictName.trim()) {
    ElMessage.warning('请填写字典名称')
    return
  }
  submitting.value = true
  try {
    if (editingDict.value) {
      await updateDict(editingDict.value.id, {
        dictType: editingDict.value.dictType,
        dictName: dictForm.dictName.trim(),
        remark: dictForm.remark || undefined,
        status: dictForm.status,
      })
      ElMessage.success('字典已更新')
    } else {
      if (!dictForm.dictType.trim()) {
        ElMessage.warning('请填写字典类型')
        return
      }
      await createDict({
        dictType: dictForm.dictType.trim(),
        dictName: dictForm.dictName.trim(),
        remark: dictForm.remark || undefined,
        status: dictForm.status,
      })
      ElMessage.success('字典已创建')
    }
    dictVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

async function removeDict(row: DictVO) {
  await ElMessageBox.confirm(
    `删除「${row.dictName}」会连带删除其下 ${row.items?.length || 0} 个字典项，确认删除？`,
    '删除字典',
    { type: 'warning' },
  )
  await deleteDict(row.id)
  ElMessage.success('字典已删除')
  currentId.value = ''
  await load()
}

function openItemCreate() {
  if (!current.value) return
  editingItem.value = null
  itemForm.itemLabel = ''
  itemForm.itemValue = ''
  itemForm.sort = (current.value.items?.length || 0) + 1
  itemForm.cssClass = ''
  itemForm.status = 1
  itemVisible.value = true
}

function openItemEdit(row: DictItemVO) {
  editingItem.value = row
  itemForm.itemLabel = row.itemLabel
  itemForm.itemValue = row.itemValue
  itemForm.sort = row.sort ?? 0
  itemForm.cssClass = row.cssClass || ''
  itemForm.status = row.status
  itemVisible.value = true
}

async function submitItem() {
  if (!current.value) return
  if (!itemForm.itemLabel.trim() || !itemForm.itemValue.trim()) {
    ElMessage.warning('名称与取值都不能为空')
    return
  }
  submitting.value = true
  try {
    const body = {
      itemLabel: itemForm.itemLabel.trim(),
      itemValue: itemForm.itemValue.trim(),
      sort: itemForm.sort,
      cssClass: itemForm.cssClass || undefined,
      status: itemForm.status,
    }
    if (editingItem.value) {
      await updateDictItem(current.value.id, editingItem.value.id, body)
      ElMessage.success('字典项已更新')
    } else {
      await createDictItem(current.value.id, body)
      ElMessage.success('字典项已新增')
    }
    itemVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

async function removeItem(row: DictItemVO) {
  if (!current.value) return
  await ElMessageBox.confirm(`确认删除字典项「${row.itemLabel}」？`, '删除字典项', { type: 'warning' })
  await deleteDictItem(current.value.id, row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.hp-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.hp-dict-name {
  margin-right: 8px;
}

.hp-dict-count {
  margin-left: auto;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.hp-form-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
}

:deep(.el-menu-item) {
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>
