<template>
  <div class="workspace-page dashboard-page">
    <section class="page-section dashboard-hero">
        <div class="dashboard-hero-copy">
          <h3 class="dashboard-title">工作区概览</h3>
          <p class="dashboard-subtitle">
            <template v-if="activeConnection">
              当前正在使用
              <span class="dashboard-connection-highlight">{{ activeConnection.name }}</span>
              ，可以直接查看表分析、SQL 工作台和最近会话。
            </template>
            <template v-else>
              {{ dashboardSubtitle }}
            </template>
          </p>
        </div>

      <div class="dashboard-hero-actions">
        <n-button
          secondary
          circle
          class="dashboard-refresh-button"
          :loading="loading"
          aria-label="刷新首页数据"
          @click="loadDashboardData"
        >
          <template #icon>
            <n-icon :size="16">
              <RefreshOutline />
            </n-icon>
          </template>
        </n-button>
      </div>
    </section>

    <section class="dashboard-stats">
      <article
        v-for="card in statCards"
        :key="card.key"
        class="page-section dashboard-stat-card"
      >
        <span class="stat-label">{{ card.label }}</span>
        <strong class="stat-value">{{ card.value }}</strong>
        <span class="stat-meta">{{ card.meta }}</span>
      </article>
    </section>

    <section class="dashboard-grid">
      <div class="page-section dashboard-panel">
        <div class="section-head">
          <div class="section-copy">
            <h3 class="section-title">最近会话</h3>
          </div>
          <div class="section-actions">
            <n-button tertiary size="small" @click="goToChat">进入问答</n-button>
          </div>
        </div>

        <div v-if="recentSessions.length > 0" class="dashboard-session-list">
          <button
            v-for="(session, index) in recentSessions"
            :key="session.id"
            type="button"
            class="dashboard-session-item"
            @click="goToChat"
          >
            <div class="dashboard-session-main">
              <span class="dashboard-session-title">{{ formatSessionLabel(index) }}</span>
              <span class="dashboard-session-note">最近更新的智能问答记录</span>
            </div>
            <span class="dashboard-session-time">
              {{ formatTime(session.updatedAt || session.createdAt) }}
            </span>
          </button>
        </div>

        <div v-else class="dashboard-empty">
          <h4>还没有会话记录</h4>
          <p>从智能问答开始一次分析后，最近会话会显示在这里。</p>
        </div>
      </div>

      <div class="page-section dashboard-panel">
        <div class="section-head">
          <div class="section-copy">
            <h3 class="section-title">当前连接</h3>
          </div>
        </div>

        <div v-if="activeConnection" class="dashboard-connection-card">
          <div class="dashboard-connection-head">
            <div>
              <span class="dashboard-connection-type">
                {{ formatDbType(activeConnection.type) }}
              </span>
              <h4>{{ activeConnection.name }}</h4>
            </div>
            <span class="dashboard-connection-status">已激活</span>
          </div>

          <div class="dashboard-connection-facts">
            <div class="dashboard-connection-fact">
              <span>数据库</span>
              <strong>{{ activeConnection.database || '-' }}</strong>
            </div>
            <div class="dashboard-connection-fact">
              <span>地址</span>
              <strong>{{ activeConnection.host }}:{{ activeConnection.port }}</strong>
            </div>
            <div class="dashboard-connection-fact">
              <span>已分析表</span>
              <strong>{{ analyzedTableCount }}</strong>
            </div>
            <div class="dashboard-connection-fact">
              <span>报表数量</span>
              <strong>{{ reportCount }}</strong>
            </div>
          </div>

          <div class="dashboard-panel-actions">
            <n-button size="small" type="primary" @click="router.push('/analysis')">查看表分析</n-button>
            <n-button size="small" secondary @click="router.push('/sql-studio')">打开 SQL 工作台</n-button>
          </div>
        </div>

        <div v-else class="dashboard-empty">
          <h4>还没有激活连接</h4>
          <p>先选择一个数据库连接，首页就会显示分析表数、报表数和最近活动。</p>
          <n-button type="primary" size="small" @click="router.push('/connections')">
            前往连接管理
          </n-button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { NButton, NIcon } from 'naive-ui'
import axios from 'axios'
import { RefreshOutline } from '@vicons/ionicons5'
import { useRouter } from 'vue-router'
import { chatApi, reportApi, tableApi } from '@/api'
import { useAppStore } from '@/stores/app'

interface DashboardConnection {
  id: number
  name: string
  type: string
  host: string
  port: number
  database: string
}

interface ChatSessionRecord {
  id: string
  createdAt?: string
  updatedAt?: string
}

interface TableMetadataRecord {
  analyzedAt?: string
}

interface ReportRecord {
  id: number
}

const router = useRouter()
const appStore = useAppStore()

const loading = ref(false)
const connections = ref<DashboardConnection[]>([])
const recentSessions = ref<ChatSessionRecord[]>([])
const analyzedTables = ref<TableMetadataRecord[]>([])
const reports = ref<ReportRecord[]>([])

const activeConnection = computed(() =>
  connections.value.find((item) => item.id === appStore.currentConnectionId) || null
)

const analyzedTableCount = computed(() => analyzedTables.value.length)
const reportCount = computed(() => reports.value.length)
const sessionCount = computed(() => recentSessions.value.length)

const lastSessionTime = computed(() => recentSessions.value[0]?.updatedAt || recentSessions.value[0]?.createdAt)

const lastAnalyzedTime = computed(() => {
  const values = analyzedTables.value
    .map((item) => item.analyzedAt)
    .filter((value): value is string => Boolean(value))
    .sort((a, b) => new Date(b).getTime() - new Date(a).getTime())

  return values[0]
})

const dashboardSubtitle = computed(() => {
  if (connections.value.length > 0) {
    return '你已经配置了数据连接，选择一个连接后，首页会显示表分析和报表统计。'
  }

  return '从添加数据库连接开始，首页会自动汇总连接、表分析、报表和最近会话。'
})

const statCards = computed(() => [
  {
    key: 'connections',
    label: '连接数量',
    value: connections.value.length,
    meta: connections.value.length > 0 ? '已配置的数据源总数' : '还没有可用连接'
  },
  {
    key: 'tables',
    label: '已分析表',
    value: analyzedTableCount.value,
    meta: activeConnection.value
      ? lastAnalyzedTime.value
        ? `最近分析于 ${formatTime(lastAnalyzedTime.value)}`
        : '当前连接还没有分析记录'
      : '选择连接后显示'
  },
  {
    key: 'sessions',
    label: '会话数量',
    value: sessionCount.value,
    meta: activeConnection.value
      ? lastSessionTime.value
        ? `最近更新于 ${formatTime(lastSessionTime.value)}`
        : '当前连接还没有问答记录'
      : '选择连接后显示'
  },
  {
    key: 'reports',
    label: '报表数量',
    value: reportCount.value,
    meta: activeConnection.value ? '当前连接下的报表中心内容' : '选择连接后显示'
  }
])

const formatDbType = (type?: string) => {
  const typeMap: Record<string, string> = {
    mysql: 'MySQL',
    doris: 'Apache Doris',
    postgresql: 'PostgreSQL',
    oracle: 'Oracle',
    sqlserver: 'SQL Server',
    clickhouse: 'ClickHouse'
  }

  return type ? typeMap[type] || type : '-'
}

const formatTime = (value?: string) => {
  if (!value) {
    return '刚刚'
  }

  const time = new Date(value)
  if (Number.isNaN(time.getTime())) {
    return value
  }

  return time.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const formatSessionLabel = (index: number) => `最近会话 ${index + 1}`

const goToChat = () => {
  router.push('/chat')
}

const loadDashboardData = async () => {
  loading.value = true

  try {
    const connectionRes = await axios.get('/api/connections')
    connections.value = Array.isArray(connectionRes.data) ? connectionRes.data : []

    if (!appStore.currentConnectionId) {
      recentSessions.value = []
      analyzedTables.value = []
      reports.value = []
      return
    }

    const [sessionRes, metadataRes, reportRes] = await Promise.allSettled([
      chatApi.getSessions(appStore.currentConnectionId),
      tableApi.getMetadata(appStore.currentConnectionId),
      reportApi.list(appStore.currentConnectionId)
    ])

    recentSessions.value =
      sessionRes.status === 'fulfilled' && Array.isArray(sessionRes.value.data)
        ? sessionRes.value.data.slice(0, 5)
        : []

    analyzedTables.value =
      metadataRes.status === 'fulfilled' && Array.isArray(metadataRes.value.data)
        ? metadataRes.value.data
        : []

    reports.value =
      reportRes.status === 'fulfilled' && Array.isArray(reportRes.value.data)
        ? reportRes.value.data
        : []
  } catch (error) {
    console.error('Failed to load dashboard data', error)
    connections.value = []
    recentSessions.value = []
    analyzedTables.value = []
    reports.value = []
  } finally {
    loading.value = false
  }
}

watch(
  () => appStore.currentConnectionId,
  () => {
    loadDashboardData()
  }
)

onMounted(() => {
  loadDashboardData()
})
</script>

<style scoped>
.dashboard-page {
  gap: 16px;
}

.dashboard-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
}

.dashboard-hero-copy {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.dashboard-title {
  margin: 0;
  color: var(--text-color);
  font-family: var(--font-display);
  font-size: 32px;
  line-height: 1.02;
  letter-spacing: -0.05em;
}

.dashboard-subtitle {
  margin: 0;
  max-width: 760px;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.8;
}

.dashboard-connection-highlight {
  margin: 0 4px;
  color: var(--primary-color);
  font-weight: 700;
}

.dashboard-hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.dashboard-refresh-button {
  flex: 0 0 auto;
}

.dashboard-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.dashboard-stat-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.stat-label {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 600;
}

.stat-value {
  color: var(--text-color);
  font-family: var(--font-display);
  font-size: 30px;
  line-height: 1;
  letter-spacing: -0.05em;
}

.stat-meta {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(320px, 0.95fr);
  gap: 16px;
}

.dashboard-panel {
  min-width: 0;
}

.dashboard-session-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.dashboard-session-item {
  appearance: none;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid var(--line-soft);
  border-radius: 16px;
  background: var(--background-elevated);
  cursor: pointer;
  text-align: left;
  transition:
    border-color 0.18s ease,
    transform 0.18s ease,
    background-color 0.18s ease;
}

.dashboard-session-item:hover {
  transform: translateY(-1px);
  border-color: var(--border-accent);
  background: var(--surface-hover);
}

.dashboard-session-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.dashboard-session-title {
  color: var(--text-color);
  font-size: 14px;
  font-weight: 700;
}

.dashboard-session-note {
  color: var(--text-secondary);
  font-size: 12px;
}

.dashboard-session-time {
  flex: 0 0 auto;
  color: var(--text-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.dashboard-connection-card {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.dashboard-connection-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.dashboard-connection-type {
  color: var(--primary-color);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.dashboard-connection-head h4 {
  margin: 6px 0 0;
  color: var(--text-color);
  font-family: var(--font-display);
  font-size: 26px;
  line-height: 1.08;
  letter-spacing: -0.04em;
}

.dashboard-connection-status {
  padding: 6px 10px;
  border-radius: 999px;
  background: var(--surface-active);
  color: var(--primary-color-strong);
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.dashboard-connection-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.dashboard-connection-fact {
  padding: 14px 14px 12px;
  border-radius: 14px;
  background: var(--surface-subtle);
  border: 1px solid var(--border-accent-soft);
}

.dashboard-connection-fact span {
  display: block;
  margin-bottom: 6px;
  color: var(--text-muted);
  font-size: 12px;
}

.dashboard-connection-fact strong {
  color: var(--text-color);
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
}

.dashboard-panel-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.dashboard-empty {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 0;
}

.dashboard-empty h4 {
  margin: 0;
  color: var(--text-color);
  font-size: 18px;
}

.dashboard-empty p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.8;
}

@media (max-width: 1100px) {
  .dashboard-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .dashboard-hero {
    flex-direction: column;
    align-items: stretch;
  }

  .dashboard-stats,
  .dashboard-connection-facts {
    grid-template-columns: 1fr;
  }

  .dashboard-session-item {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
