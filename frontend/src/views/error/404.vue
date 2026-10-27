<template>
  <div class="hp-error">
    <el-result icon="info" title="404" sub-title="页面不存在或已被移除">
      <template #extra>
        <el-button type="primary" @click="$router.push('/dashboard')">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.hp-error {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
