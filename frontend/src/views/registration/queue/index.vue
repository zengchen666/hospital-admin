<template>
  <div class="hp-page">
    <h3 class="hp-page-title">候诊队列</h3>

    <div class="hp-toolbar">
      <el-select v-model="deptId" placeholder="选择科室" style="width: 180px" @change="load">
        <el-option v-for="d in depts" :key="d.id" :label="d.deptName" :value="d.id" />
      </el-select>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      <span class="spacer" />
      <el-tag v-if="waiting.length" type="primary" size="large">
        待就诊 {{ waiting.length }} 人
      </el-tag>
    </div>

    <el-empty v-if="!deptId" description="请先选择科室" />

    <el-table v-else v-loading="loading" :data="rows" border stripe>
      <el-table-column label="排队号" width="90">
        <template #default="{ row }">
          <span class="queue-no">{{ row.queueNo }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="regNo" label="挂号单号" width="170" />
      <el-table-column prop="patientName" label="患者" width="100" />
      <el-table-column prop="patientNo" label="患者编号" width="110" />
      <el-table-column prop="doctorName" label="接诊医生" width="100" />
      <el-table-column label="号别" width="80">
        <template #default="{ row }">
          <el-tag :type="row.regType === 3 ? 'danger' : row.regType === 2 ? 'warning' : 'info'" size="small">
            {{ regTypeText(row.regType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="regTime" label="挂号时间" width="170" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 0"
            v-perm="'registration:call'"
            type="primary"
            size="small"
            @click="call(row)"
          >
            叫号
          </el-button>
          <el-button
            v-if="row.status === 0"
            v-perm="'registration:cancel'"
            link
            type="danger"
            @click="cancel(row)"
          >
            退号
          </el-button>
          <span v-if="row.status !== 0" class="hp-muted" style="font-size: 12px">
            {{ statusText(row.status) }}
          </span>
        </template>
      </el-table-column>
      <template #empty>
        <span class="hp-muted">该科室当前没有候诊患者</span>
      </template>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { callPatient, cancelRegistration, queueOfDept, type RegistrationVO } from '@/api/registration'
import { listDepts, type DeptOption } from '@/api/user'
import { useUserStore } from '@/stores/user'

/**
 * 候诊队列。
 *
 * <p><b>默认选中当前用户所在科室</b>：收费员、护士大多只盯一个科室，
 * 每次进来都要先点一次下拉是纯浪费。但科室必须可切换 ——
 * 有人就是需要代班看别科的队列。</p>
 *
 * <p>已叫号或已完成的记录不给「叫号」按钮：重复叫号会把别人的队列状态改乱，
 * 后端也会以 40900 拦下，但按钮不该让人先点错一次。</p>
 */

const store = useUserStore()
const depts = ref<DeptOption[]>([])
const deptId = ref<string>('')
const rows = ref<RegistrationVO[]>([])
const loading = ref(false)

const waiting = computed(() => rows.value.filter((r) => r.status === 0))

function regTypeText(v?: number) {
  return ['', '普通', '专家', '急诊'][v ?? 0] ?? '-'
}
function statusText(v?: number) {
  return ['待就诊', '就诊中', '已完成', '已退号'][v ?? 0] ?? '-'
}
function statusTag(v: number): 'info' | 'primary' | 'success' | 'danger' {
  return v === 0 ? 'info' : v === 1 ? 'primary' : v === 2 ? 'success' : 'danger'
}

async function load() {
  if (!deptId.value) {
    rows.value = []
    return
  }
  loading.value = true
  try {
    rows.value = await queueOfDept(deptId.value)
  } finally {
    loading.value = false
  }
}

async function call(row: RegistrationVO) {
  await callPatient(row.id)
  ElMessage.success(`已叫号：${row.patientName}（${row.queueNo} 号）`)
  load()
}

async function cancel(row: RegistrationVO) {
  try {
    await ElMessageBox.confirm(
      `确定为「${row.patientName}」退号吗？退号后号源会退回排班。`,
      '退号确认',
      { type: 'warning', confirmButtonText: '退号', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await cancelRegistration(row.id)
  ElMessage.success('已退号')
  load()
}

onMounted(async () => {
  try {
    depts.value = await listDepts()
  } catch {
    depts.value = []
  }
  // 后端 MenuVO 的 deptId 是字符串；当前用户科室可能不在科室表里（如已停用），需校验
  const mine = store.userInfo?.deptId
  if (mine && depts.value.some((d) => d.id === mine)) {
    deptId.value = mine
  } else if (depts.value.length) {
    deptId.value = depts.value[0].id
  }
  await load()
})
</script>

<style scoped>
.queue-no {
  font-size: 18px;
  font-weight: 700;
  color: #2563eb;
}
</style>
