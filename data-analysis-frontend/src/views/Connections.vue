<template>
  <div class="workspace-page">
    <section class="page-section">
      <div class="section-head section-head-actions-only">
        <div class="section-actions">
          <n-button type="primary" @click="openAddModal">添加连接</n-button>
        </div>
      </div>

      <div v-if="connections.length > 0" class="connection-grid">
        <article
          v-for="conn in connections"
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
              <strong>{{ conn.database }}</strong>
            </div>
            <div class="connection-fact">
              <span>用户名</span>
              <strong>{{ conn.username }}</strong>
            </div>
          </div>

          <div class="connection-actions">
            <n-button size="small" @click.stop="testConnection(conn)">测试</n-button>
            <n-button size="small" @click.stop="openEditModal(conn)">编辑</n-button>
            <n-button size="small" type="error" secondary @click.stop="deleteConnection(conn)">删除</n-button>
          </div>
        </article>
      </div>

      <div v-else class="empty-panel">
        <h3>还没有连接</h3>
        <n-button type="primary" @click="openAddModal">添加第一个连接</n-button>
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
          <n-input v-model:value="formValue.name" placeholder="例如：生产订单库" />
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
          <n-button @click="testNewConnection" :loading="testing">测试连接</n-button>
          <n-button type="primary" @click="isEdit ? updateConnection() : addConnection()">保存</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  NButton,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NModal,
  NSelect,
  NSpace,
  useMessage
} from 'naive-ui'
import axios from 'axios'
import { encryptPassword } from '@/utils/crypto'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
const message = useMessage()

const connections = ref<any[]>([])
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

const formValue = ref({ ...defaultForm })

const defaultPorts: Record<string, number> = {
  mysql: 3306,
  doris: 9030,
  postgresql: 5432,
  oracle: 1521,
  sqlserver: 1433,
  clickhouse: 8123
}

watch(
  () => formValue.value.type,
  (newType) => {
    if (defaultPorts[newType]) {
      formValue.value.port = defaultPorts[newType]
    }
  }
)

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

const openAddModal = () => {
  isEdit.value = false
  editingId.value = null
  formValue.value = { ...defaultForm }
  showModal.value = true
}

const openEditModal = (conn: any) => {
  isEdit.value = true
  editingId.value = conn.id
  formValue.value = {
    name: conn.name,
    type: conn.type,
    host: conn.host,
    port: conn.port,
    database: conn.database,
    username: conn.username,
    password: ''
  }
  showModal.value = true
}

const loadConnections = async () => {
  try {
    const res = await axios.get('/api/connections')
    connections.value = res.data || []
    if (appStore.currentConnectionId) {
      const currentConnection = connections.value.find((item) => item.id === appStore.currentConnectionId)
      if (currentConnection) {
        appStore.setCurrentConnection(currentConnection.id, currentConnection.name)
      }
    }
  } catch (error) {
    console.error('Failed to load connections', error)
    message.error('加载连接列表失败')
  }
}

const addConnection = async () => {
  try {
    const payload = {
      ...formValue.value,
      password: encryptPassword(formValue.value.password)
    }
    await axios.post('/api/connections', payload)
    message.success('连接已添加')
    showModal.value = false
    await loadConnections()
  } catch {
    message.error('添加连接失败')
  }
}

const updateConnection = async () => {
  if (!editingId.value) {
    return
  }

  try {
    const payload: Record<string, any> = { ...formValue.value }
    if (payload.password) {
      payload.password = encryptPassword(payload.password)
    } else {
      delete payload.password
    }

    await axios.put(`/api/connections/${editingId.value}`, payload)
    message.success('连接已更新')
    showModal.value = false
    await loadConnections()
  } catch {
    message.error('更新连接失败')
  }
}

const testConnection = async (conn: any) => {
  try {
    const res = await axios.post(`/api/connections/${conn.id}/test`)
    if (res.data) {
      message.success('连接测试成功')
    } else {
      message.error('连接测试失败')
    }
  } catch {
    message.error('连接测试失败')
  }
}

const testNewConnection = async () => {
  testing.value = true

  try {
    const payload: Record<string, any> = { ...formValue.value }

    if (payload.password) {
      payload.password = encryptPassword(payload.password)
    } else if (isEdit.value) {
      message.warning('编辑模式下请填写密码后再测试')
      testing.value = false
      return
    }

    const res = await axios.post('/api/connections/test', payload)
    if (res.data) {
      message.success('连接测试成功')
    } else {
      message.error('连接测试失败')
    }
  } catch {
    message.error('连接测试失败')
  } finally {
    testing.value = false
  }
}

const selectConnection = async (conn: any) => {
  if (appStore.currentConnectionId === conn.id) {
    try {
      await axios.post(`/api/connections/${conn.id}/deactivate`)
      appStore.setCurrentConnection(null)
      message.success('已取消当前连接')
    } catch {
      message.error('切换连接失败')
    }
    return
  }

  try {
    await axios.post(`/api/connections/${conn.id}/activate`)
    appStore.setCurrentConnection(conn.id, conn.name)
    message.success(`已切换到 ${conn.name}`)
  } catch {
    message.error('切换连接失败')
  }
}

const deleteConnection = async (conn: any) => {
  try {
    await axios.delete(`/api/connections/${conn.id}`)
    if (conn.id === appStore.currentConnectionId) {
      appStore.setCurrentConnection(null)
    }
    message.success('连接已删除')
    await loadConnections()
  } catch {
    message.error('删除连接失败')
  }
}

onMounted(() => {
  loadConnections()
})
</script>
