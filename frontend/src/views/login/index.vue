<template>
  <div class="hp-login">
    <div class="hp-login-box">
      <h2 class="hp-login-title">医疗后台管理系统</h2>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="onSubmit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入账号" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-button type="primary" class="hp-login-btn" :loading="loading" @click="onSubmit">
          登录
        </el-button>
      </el-form>

      <div class="hp-login-tips">
        <span class="hp-muted">演示账号：admin / 123456（超管）、doc01 / 123456（医生）</span>
      </div>
      <div v-if="failCount >= 5" class="hp-login-warn">
        连续失败次数过多，账号将被锁定 30 分钟（后端 LoginAttemptService）
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { resetDynamicRoutes } from '@/router'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
/** 失败次数只在前端做提示用，真正的锁定判定在后端 */
const failCount = ref(0)

const form = reactive({ username: 'admin', password: '123456' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await login(form.username, form.password)
    store.setTokens(res.accessToken, res.refreshToken)
    // 换账号登录后必须重建路由，否则会带着上一个人的菜单
    resetDynamicRoutes()
    await store.loadUserInfo()
    await store.loadMenus(true)
    failCount.value = 0
    ElMessage.success(`欢迎回来，${res.userInfo.realName}`)
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.push(redirect)
  } catch {
    failCount.value += 1
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.hp-login {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1e3a8a 0%, #2563eb 55%, #38bdf8 100%);
}

.hp-login-box {
  width: 360px;
  padding: 32px 28px 24px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.25);
}

.hp-login-title {
  margin: 0 0 24px;
  text-align: center;
  font-size: 20px;
  color: #1e293b;
}

.hp-login-btn {
  width: 100%;
}

.hp-login-tips {
  margin-top: 16px;
  text-align: center;
  font-size: 12px;
  line-height: 1.6;
}

.hp-login-warn {
  margin-top: 8px;
  text-align: center;
  font-size: 12px;
  color: #dc2626;
}
</style>
