<template>
  <div class="login-page">
    <section class="login-panel">
      <div class="login-stage">
        <div class="login-copy">
          <span class="login-kicker">DataMind</span>
          <h1>登录工作台</h1>
          <p>连接你的数据空间，继续智能执行、SQL 生成与报表分析。</p>
        </div>

        <div class="login-highlights">
          <article class="highlight-card">
            <strong>智能执行</strong>
            <span>用自然语言完成分析任务</span>
          </article>
          <article class="highlight-card">
            <strong>数据连接</strong>
            <span>按账号授权访问可用数据库</span>
          </article>
          <article class="highlight-card">
            <strong>报表协作</strong>
            <span>沉淀查询、图表与专业报告</span>
          </article>
        </div>
      </div>

      <div class="login-form-panel">
        <div class="login-form-head">
          <h2>账号登录</h2>
          <p>请输入用户名和密码</p>
        </div>

        <n-form :model="formValue" class="login-form" @submit.prevent="handleSubmit">
          <n-form-item label="用户名">
            <n-input v-model:value="formValue.username" placeholder="请输入用户名" />
          </n-form-item>
          <n-form-item label="密码">
            <n-input
              v-model:value="formValue.password"
              type="password"
              show-password-on="click"
              placeholder="请输入密码"
            />
          </n-form-item>
          <n-button type="primary" block size="large" :loading="submitting" @click="handleSubmit">
            登录
          </n-button>
        </n-form>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NForm, NFormItem, NInput, useMessage } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'

const router = useRouter()
const route = useRoute()
const message = useMessage()
const authStore = useAuthStore()
const appStore = useAppStore()

const submitting = ref(false)
const formValue = reactive({
  username: '',
  password: ''
})

const handleSubmit = async () => {
  if (!formValue.username.trim() || !formValue.password.trim()) {
    message.warning('请输入用户名和密码')
    return
  }

  submitting.value = true
  try {
    await authStore.login(formValue.username.trim(), formValue.password)
    await appStore.loadAccessibleConnections(authStore.user?.lastConnectionId ?? null)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    await router.replace(redirect)
  } catch (error: any) {
    message.error(error?.response?.data?.message || '登录失败，请检查账号和密码')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    linear-gradient(180deg, rgba(239, 91, 42, 0.04), transparent 18%),
    linear-gradient(rgba(115, 77, 57, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(115, 77, 57, 0.05) 1px, transparent 1px),
    #ffffff;
  background-size:
    auto,
    28px 28px,
    28px 28px,
    auto;
}

.login-panel {
  width: min(100%, 960px);
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(360px, 0.95fr);
  gap: 28px;
  padding: 28px;
  border-radius: 32px;
  background: rgba(255, 255, 255, 0.98);
  border: 1px solid var(--line-soft);
  box-shadow: var(--shadow-floating);
}

.login-stage {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 24px;
  min-width: 0;
  padding: 10px 6px 10px 4px;
}

.login-copy {
  max-width: 420px;
}

.login-kicker {
  display: inline-flex;
  margin-bottom: 10px;
  color: var(--primary-color);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.login-copy h1 {
  margin: 0 0 8px;
  font-family: var(--font-display);
  font-size: 48px;
  line-height: 1.02;
  letter-spacing: -0.05em;
}

.login-copy p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 15px;
  line-height: 1.8;
}

.login-highlights {
  display: grid;
  gap: 12px;
}

.highlight-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 16px 18px;
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(255, 250, 247, 0.96), rgba(255, 255, 255, 0.92));
  border: 1px solid var(--line-soft);
}

.highlight-card strong {
  color: var(--text-color);
  font-size: 15px;
  font-weight: 700;
}

.highlight-card span {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.login-form-panel {
  padding: 28px;
  border-radius: 24px;
  background: rgba(255, 252, 250, 0.92);
  border: 1px solid rgba(239, 91, 42, 0.08);
}

.login-form-head {
  margin-bottom: 18px;
}

.login-form-head h2 {
  margin: 0 0 6px;
  color: var(--text-color);
  font-family: var(--font-display);
  font-size: 28px;
  letter-spacing: -0.04em;
}

.login-form-head p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

@media (max-width: 880px) {
  .login-panel {
    grid-template-columns: 1fr;
    gap: 18px;
    padding: 22px;
  }

  .login-copy h1 {
    font-size: 38px;
  }

  .login-form-panel {
    padding: 22px;
  }
}
</style>
