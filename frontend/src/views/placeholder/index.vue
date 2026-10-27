<template>
  <div class="hp-page">
    <el-empty :description="`「${title}」页面建设中`">
      <template #extra>
        <span class="hp-muted">
          菜单已按权限下发，前端该页面的组件尚未实现。
        </span>
      </template>
    </el-empty>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

/**
 * 未实现页面的占位。
 *
 * <p>存在的原因：菜单是后端下发的，权限一开通菜单就出现；
 * 但前端不一定已经写完对应页面。没有这个占位，点进未实现页面会直接白屏或 404，
 * 排查时很容易误判成「路由配错了」。</p>
 */
const route = useRoute()
const title = computed(() => (route.meta.title as string) || '该模块')
</script>
