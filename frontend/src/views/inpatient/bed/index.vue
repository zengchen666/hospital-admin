<template>
  <div class="hp-page">
    <h3 class="hp-page-title">床位一览</h3>

    <div class="hp-toolbar">
      <el-select v-model="query.wardId" placeholder="全部病区" clearable style="width: 170px" @change="reload">
        <el-option v-for="w in wards" :key="w.id" :label="w.wardName" :value="String(w.id)" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="reload">
        <el-option label="空闲" :value="0" />
        <el-option label="占用" :value="1" />
        <el-option label="维修" :value="2" />
      </el-select>
      <el-button :icon="Refresh" @click="reload">刷新</el-button>
      <span class="spacer" />
      <el-tag v-if="stats" type="info" size="large">
        空闲 {{ stats.free }} / 共 {{ stats.total }}
      </el-tag>
    </div>

    <div v-loading="loading" class="bed-grid">
      <div
        v-for="b in rows"
        :key="b.id"
        class="bed-card"
        :class="'bed-' + b.status"
      >
        <div class="bed-no">{{ b.bedNo }}</div>
        <div class="bed-ward">{{ b.wardName }}</div>
        <el-tag :type="bedTypeTag(b.bedType)" size="small">{{ bedTypeText(b.bedType) }}</el-tag>
        <div class="bed-price">¥{{ b.price }}/天</div>
        <div class="bed-occupant">
          <template v-if="b.status === 1">
            <el-icon><User /></el-icon>
            <span>{{ b.occupantName || '占用' }}</span>
          </template>
          <template v-else-if="b.status === 0">空闲</template>
          <template v-else>维修中</template>
        </div>
      </div>
    </div>

    <el-pagination
      style="margin-top: 12px; justify-content: flex-end"
      layout="total, sizes, prev, pager, next"
      :total="total"
      :current-page="query.pageNum"
      :page-size="query.pageSize"
      :page-sizes="[12, 24, 48]"
      @current-change="onPageChange"
      @size-change="onSizeChange"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh, User } from '@element-plus/icons-vue'
import { listWards, pageBeds, type BedQuery, type BedVO, type WardVO } from '@/api/inpatient'

/**
 * 床位一览。
 *
 * <p><b>用卡片网格而不是表格</b>：床位图的价值在于一眼扫出「哪几张空着」，
 * 表格在这种场景下要逐行读文字，反而不如图形化的色块。</p>
 *
 * <p>占用床位直接显示患者姓名 —— 护士找床、医生查房看的都是这个。</p>
 *
 * <p><b>病区下拉走独立接口</b>：早期版本从当前页床位里去重出病区列表，
 * 结果翻到第 2 页时下拉里只剩那一页的病区，看着像数据丢了。
 * 病区是主数据，就该单独查。</p>
 */

const rows = ref<BedVO[]>([])
const wards = ref<WardVO[]>([])
const total = ref(0)
const loading = ref(false)

const query = reactive<BedQuery>({ pageNum: 1, pageSize: 24 })

const stats = computed(() => {
  if (!rows.value.length) return null
  return {
    free: rows.value.filter((b) => b.status === 0).length,
    total: rows.value.length,
  }
})

function bedTypeText(v?: number) {
  return ['', '普通', '重症', 'VIP'][v ?? 0] ?? '-'
}
function bedTypeTag(v: number): 'info' | 'warning' | 'danger' {
  return v === 2 ? 'danger' : v === 3 ? 'warning' : 'info'
}

async function load() {
  loading.value = true
  try {
    const res = await pageBeds(query)
    rows.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadWards() {
  try {
    wards.value = await listWards()
  } catch {
    // 病区下拉拿不到时不影响床位图本身，静默降级
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

onMounted(async () => {
  await loadWards()
  await load()
})
</script>

<style scoped>
.bed-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 10px;
}

.bed-card {
  padding: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fff;
}

/* 空闲=绿、占用=蓝、维修=灰 */
.bed-0 {
  border-left: 4px solid #16a34a;
}

.bed-1 {
  border-left: 4px solid #2563eb;
  background: #f0f7ff;
}

.bed-2 {
  border-left: 4px solid #94a3b8;
  opacity: 0.75;
}

.bed-no {
  font-size: 16px;
  font-weight: 700;
}

.bed-ward,
.bed-price {
  font-size: 12px;
  color: #64748b;
  margin-top: 2px;
}

.bed-occupant {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  font-size: 12px;
}
</style>
