<template>
  <div class="hp-error">
    <el-result icon="warning" title="403" sub-title="没有访问该资源的权限">
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
