<template>
  <div class="login-container">
    <el-select
      class="locale-switch"
      :model-value="appStore.locale"
      size="small"
      @update:model-value="appStore.setLocale"
    >
      <el-option :label="$t('settings.zhCN')" value="zh-CN" />
      <el-option :label="$t('settings.enUS')" value="en-US" />
    </el-select>
    <!-- 背景装饰 -->
    <div class="login-background">
      <div class="circle circle-1"></div>
      <div class="circle circle-2"></div>
      <div class="circle circle-3"></div>
    </div>

    <!-- 左侧信息区 -->
    <div class="login-info">
      <div class="info-content">
        <div class="logo-section">
          <div class="logo-icon">
            <el-icon :size="48"><Management /></el-icon>
          </div>
          <h1 class="system-title">{{ $t('app.name') }}</h1>
          <p class="system-desc">Enterprise Resource Planning</p>
        </div>

        <div class="features">
          <div class="feature-item" v-for="(feature, index) in features" :key="index">
            <div class="feature-icon">
              <el-icon :size="20"><component :is="feature.icon" /></el-icon>
            </div>
            <div class="feature-text">
              <h3>{{ feature.title }}</h3>
              <p>{{ feature.desc }}</p>
            </div>
          </div>
        </div>

        <div class="copyright">
          <p>&copy; 2026 ERP System. All rights reserved.</p>
        </div>
      </div>
    </div>

    <!-- 右侧登录表单 -->
    <div class="login-form-container">
      <div class="login-box">
        <div class="login-header">
          <h2 class="login-title">{{ $t('login.welcome') }}</h2>
          <p class="login-subtitle">{{ $t('login.subtitle') }}</p>
        </div>

        <el-form
          ref="loginFormRef"
          :model="loginForm"
          :rules="rules"
          class="login-form"
          @keyup.enter="handleLogin"
        >
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              :placeholder="$t('login.username')"
              size="large"
              clearable
            >
              <template #prefix>
                <el-icon class="input-icon"><User /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              :placeholder="$t('login.password')"
              size="large"
              show-password
            >
              <template #prefix>
                <el-icon class="input-icon"><Lock /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item>
            <el-link type="primary" :underline="false" class="forgot-password" @click="showPasswordResetHint">
              {{ $t('login.forgot') }}
            </el-link>
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              size="large"
              :loading="loading"
              class="login-button"
              @click="handleLogin"
            >
              {{ loading ? $t('login.submitting') : $t('login.submit') }}
            </el-button>
          </el-form-item>
        </el-form>

        <div class="login-footer">
          <el-divider>
            <span class="divider-text">{{ $t('login.testAccount') }}</span>
          </el-divider>
          <div class="test-accounts">
            <el-tag type="success" size="small">{{ $t('login.prefilledTestAccount') }}</el-tag>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { User, Lock, Management, Monitor, DataAnalysis, DocumentChecked } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { useAppStore } from '@/store/modules/app'

// Store
const userStore = useUserStore()
const appStore = useAppStore()
const { t } = useI18n()
const router = useRouter()

// 表单引用
const loginFormRef = ref<FormInstance>()

// 表单数据
const loginForm = reactive({
  username: 'admin',
  password: 'LocalAdmin123'
})

// 加载状态
const loading = ref(false)

const showPasswordResetHint = () => {
  ElMessage.info(t('login.passwordResetHint'))
}

// 功能特性
const features = computed(() => [
  {
    icon: Monitor,
    title: t('login.feature1Title'),
    desc: t('login.feature1Desc')
  },
  {
    icon: DataAnalysis,
    title: t('login.feature2Title'),
    desc: t('login.feature2Desc')
  },
  {
    icon: DocumentChecked,
    title: t('login.feature3Title'),
    desc: t('login.feature3Desc')
  }
])

// 表单验证规则
const rules = computed<FormRules>(() => ({
  username: [{ required: true, message: t('login.usernameRequired'), trigger: 'blur' }],
  password: [
    { required: true, message: t('login.passwordRequired'), trigger: 'blur' },
    { min: 6, message: t('login.passwordMin'), trigger: 'blur' }
  ]
}))

// 登录处理
const handleLogin = async () => {
  if (!loginFormRef.value) return

  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      await userStore.doLogin(loginForm)
      await router.push('/')
    } catch (error) {
      console.error('登录失败:', error)
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-container {
  display: flex;
  min-height: 100vh;
  position: relative;
  overflow: hidden;
}

.locale-switch {
  position: absolute;
  top: 20px;
  right: 24px;
  width: 128px;
  z-index: 3;
}

/* 背景装饰 */
.login-background {
  position: absolute;
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  z-index: 0;
}

.circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  animation: float 20s infinite ease-in-out;
}

.circle-1 {
  width: 300px;
  height: 300px;
  top: -100px;
  left: -100px;
  animation-delay: 0s;
}

.circle-2 {
  width: 400px;
  height: 400px;
  bottom: -150px;
  right: -150px;
  animation-delay: 4s;
}

.circle-3 {
  width: 200px;
  height: 200px;
  top: 50%;
  left: 30%;
  animation-delay: 8s;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0) scale(1);
  }
  50% {
    transform: translateY(-30px) scale(1.05);
  }
}

/* 左侧信息区 */
.login-info {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px;
  color: #fff;
  position: relative;
  z-index: 1;
}

.info-content {
  max-width: 500px;
}

.logo-section {
  margin-bottom: 60px;
  text-align: center;
}

.logo-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 80px;
  height: 80px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 20px;
  margin-bottom: 20px;
  backdrop-filter: blur(10px);
}

.system-title {
  font-size: 36px;
  font-weight: 700;
  margin-bottom: 12px;
  letter-spacing: 2px;
}

.system-desc {
  font-size: 16px;
  opacity: 0.9;
  letter-spacing: 1px;
}

.features {
  margin-bottom: 60px;
}

.feature-item {
  display: flex;
  align-items: flex-start;
  margin-bottom: 30px;
  padding: 20px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  backdrop-filter: blur(10px);
  transition: all 0.3s;
}

.feature-item:hover {
  background: rgba(255, 255, 255, 0.15);
  transform: translateX(5px);
}

.feature-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 10px;
  margin-right: 16px;
  flex-shrink: 0;
}

.feature-text h3 {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 6px;
}

.feature-text p {
  font-size: 13px;
  opacity: 0.8;
  line-height: 1.5;
}

.copyright {
  text-align: center;
  font-size: 13px;
  opacity: 0.7;
}

/* 右侧登录表单 */
.login-form-container {
  width: 480px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  padding: 40px;
  position: relative;
  z-index: 1;
  box-shadow: -10px 0 40px rgba(0, 0, 0, 0.1);
}

.login-box {
  width: 100%;
  max-width: 400px;
}

.login-header {
  margin-bottom: 40px;
}

.login-title {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.login-subtitle {
  font-size: 14px;
  color: #909399;
}

.login-form {
  margin-bottom: 20px;
}

.forgot-password {
  margin-left: auto;
}

.login-form :deep(.el-input__wrapper) {
  padding: 12px 16px;
  box-shadow: 0 0 0 1px #dcdfe6 inset;
  transition: all 0.3s;
}

.login-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #c0c4cc inset;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--el-color-primary) inset;
}

.input-icon {
  color: #909399;
}

.login-button {
  width: 100%;
  height: 44px;
  font-size: 16px;
  font-weight: 500;
  letter-spacing: 1px;
  margin-top: 10px;
}

.login-footer {
  margin-top: 30px;
}

.divider-text {
  font-size: 12px;
  color: #909399;
  padding: 0 12px;
}

.test-accounts {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 16px;
}

/* 响应式 */
@media (max-width: 1024px) {
  .login-info {
    display: none;
  }

  .login-form-container {
    width: 100%;
    background: #f5f7fa;
  }

  .login-box {
    background: #fff;
    padding: 40px;
    border-radius: 12px;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  }
}
</style>
