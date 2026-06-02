<template>
  <div class="workspace-page">
    <section class="page-section">
      <div v-if="authStore.isAdmin" class="section-head section-head-actions-only">
        <div class="section-actions">
          <n-button type="primary" @click="openAddModal">添加连接</n-button>
        </div>
      </div>

      <div v-if="appStore.accessibleConnections.length > 0" class="connection-grid">
        <article
          v-for="conn in appStore.accessibleConnections"
          :key="conn.id"
          class="connection-surface"
          :class="{ selected: conn.id === appStore.currentConnectionId }"
          @click="selectConnection(conn)"
        >
          <div class="connection-top">
            <div>
              <span class="connection-type">{{ formatDbType(conn.type) }}</span>
              <h3>{{ conn.name }}</h3>
            </div>
            <span v-if="conn.id === appStore.currentConnectionId" class="connection-badge">当前连接</span>
          </div>

          <p class="connection-endpoint">{{ conn.host }}:{{ conn.port }}</p>

          <div class="connection-facts">
            <div class="connection-fact">
              <span>数据库</span>
              <strong>{{ conn.database || '-' }}</strong>
            </div>
            <div class="connection-fact">
              <span>用户名</span>
              <strong>{{ conn.username }}</strong>
            </div>
          </div>

          <div class="connection-actions">
            <n-button size="small" @click.stop="testConnection(conn)">测试</n-button>
            <n-button v-if="authStore.isAdmin" size="small" @click.stop="openEditModal(conn)">编辑</n-button>
            <n-button
              v-if="authStore.isAdmin"
              size="small"
              type="error"
              secondary
              @click.stop="deleteConnection(conn)"
            >
              删除
            </n-button>
          </div>
        </article>
      </div>

      <div v-else class="empty-panel">
        <h3>{{ authStore.isAdmin ? '还没有连接' : '当前没有可用连接' }}</h3>
        <p v-if="!authStore.isAdmin">请联系管理员为当前账号分配数据库连接权限。</p>
        <n-button v-if="authStore.isAdmin" type="primary" @click="openAddModal">添加第一个连接</n-button>
      </div>
    </section>

    <n-modal
      v-model:show="showModal"
      preset="card"
      :title="modalTitle"
      style="width: 560px; max-width: 92vw;"
    >
      <n-form :model="formValue" label-placement="top">
        <n-form-item label="连接名称">
          <n-input v-model:value="formValue.name" placeholder="例如：博客生产库" />
        </n-form-item>
        <n-form-item label="数据库类型">
          <n-select v-model:value="formValue.type" :options="dbTypeOptions" />
        </n-form-item>
        <n-form-item label="主机地址">
          <n-input v-model:value="formValue.host" placeholder="localhost" />
        </n-form-item>
        <n-form-item label="端口">
          <n-input-number v-model:value="formValue.port" :min="1" :max="65535" style="width: 100%;" />
        </n-form-item>
        <n-form-item label="数据库名">
          <n-input v-model:value="formValue.database" placeholder="请输入数据库名" />
        </n-form-item>
        <n-form-item label="用户名">
          <n-input v-model:value="formValue.username" placeholder="root" />
        </n-form-item>
        <n-form-item :label="isEdit ? '密码（留空则保持不变）' : '密码'">
          <n-input
            v-model:value="formValue.password"
            type="password"
            show-password-on="click"
            :placeholder="isEdit ? '留空则保持原密码' : '请输入密码'"
          />
        </n-form-item>
      </n-form>

      <template #footer>
        <n-space justify="end">
          <n-button @click="showModal = false">取消</n-button>
          <n-button :loading="testing" @click="testNewConnection">测试连接</n-button>
          <n-button type="primary" @click="submitConnection">保存</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  NButton,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NModal,
  NSelect,
  NSpace,
  useDialog,
  useMessage
} from 'naive-ui'
import axios from 'axios'
import { encryptPassword } from '@/utils/crypto'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'

type ConnectionItem = {
  id: number
  name: string
  type: string
  host: string
  port: number
  database: string
  username: string
  status: string
}

const appStore = useAppStore()
const authStore = useAuthStore()
const message = useMessage()
const dialog = useDialog()

const showModal = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const testing = ref(false)

const defaultForm = {
  name: '',
  type: 'mysql',
  host: 'localhost',
  port: 3306,
  database: '',
  username: 'root',
  password: ''
}

const formValue = reactive({ ...defaultForm })

const dbTypeOptions = [
  { label: 'MySQL', value: 'mysql' },
  { label: 'Apache Doris', value: 'doris' },
  { label: 'PostgreSQL', value: 'postgresql' },
  { label: 'Oracle', value: 'oracle' },
  { label: 'SQL Server', value: 'sqlserver' },
  { label: 'ClickHouse', value: 'clickhouse' }
]

const modalTitle = computed(() => (isEdit.value ? '编辑数据连接' : '添加数据连接'))

const formatDbType = (type: string) =>
  dbTypeOptions.find((item) => item.value === type)?.label || type

watch(
  () => formValue.type,
  (value) => {
    const defaultPorts: Record<string, number> = {
      mysql: 3306,
      doris: 9030,
      postgresql: 5432,
      oracle: 1521,
      sqlserver: 1433,
      clickhouse: 8123
    }

    if (defaultPorts[value]) {
      formValue.port = defaultPorts[value]
    }
  }
)

const loadConnections = async () => {
  await appStore.loadAccessibleConnections(authStore.user?.lastConnectionId ?? null)
}

const resetForm = () => {
  Object.assign(formValue, defaultForm)
  editingId.value = null
}

const openAddModal = () => {
  isEdit.value = false
  resetForm()
  showModal.value = true
}

const openEditModal = (conn: ConnectionItem) => {
  isEdit.value = true
  editingId.value = conn.id
  Object.assign(formValue, {
    name: conn.name,
    type: conn.type,
    host: conn.host,
    port: conn.port,
    database: conn.database,
    username: conn.username,
    password: ''
  })
  showModal.value = true
}

const buildPayload = () => {
  const payload: Record<string, any> = { ...formValue }
  if (payload.password) {
    payload.password = encryptPassword(payload.password)
  } else if (isEdit.value) {
    delete payload.password
  }
  return payload
}

const submitConnection = async () => {
  try {
    const payload = buildPayload()
    if (isEdit.value && editingId.value) {
      await axios.put(`/api/connections/${editingId.value}`, payload)
      message.success('连接已更新')
    } else {
      await axios.post('/api/connections', payload)
      message.success('连接已创建')
    }

    showModal.value = false
    await loadConnections()
  } catch (error: any) {
    message.error(error?.response?.data?.message || '保存连接失败')
  }
}

const testConnection = async (conn: ConnectionItem) => {
  try {
    const res = await axios.post(`/api/connections/${conn.id}/test`)
    message[res.data ? 'success' : 'error'](res.data ? '连接测试成功' : '连接测试失败')
    if (authStore.isAdmin) {
      await loadConnections()
    }
  } catch (error: any) {
    message.error(error?.response?.data?.message || '连接测试失败')
  }
}

const testNewConnection = async () => {
  testing.value = true
  try {
    if (isEdit.value && !formValue.password) {
      message.warning('编辑模式下请填写密码后再测试')
      return
    }

    const res = await axios.post('/api/connections/test', buildPayload())
    message[res.data ? 'success' : 'error'](res.data ? '连接测试成功' : '连接测试失败')
  } catch (error: any) {
    message.error(error?.response?.data?.message || '连接测试失败')
  } finally {
    testing.value = false
  }
}

const selectConnection = async (conn: ConnectionItem) => {
  if (appStore.currentConnectionId === conn.id) {
    return
  }

  try {
    await appStore.selectConnection(conn.id)
    message.success(`已切换到 ${conn.name}`)
  } catch (error: any) {
    message.error(error?.response?.data?.message || '切换连接失败')
  }
}

const deleteConnection = (conn: ConnectionItem) => {
  dialog.warning({
    title: '删除连接',
    content: `确认删除连接“${conn.name}”吗？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await axios.delete(`/api/connections/${conn.id}`)
        if (conn.id === appStore.currentConnectionId) {
          await appStore.selectConnection(null)
        }
        message.success('连接已删除')
        await loadConnections()
      } catch (error: any) {
        message.error(error?.response?.data?.message || '删除连接失败')
      }
    }
  })
}

onMounted(() => {
  loadConnections()
})
</script>
