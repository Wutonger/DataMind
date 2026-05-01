<template>
  <div class="workspace-page sql-studio-page">
    <div v-if="appStore.currentConnectionId" class="sql-studio-layout">
      <aside class="page-section sql-side-panel">
        <section class="sql-side-section">
          <div class="sql-panel-heading">
            <div>
              <div class="sql-panel-kicker">SQL 工作台</div>
              <h3>自然语言转查询</h3>
            </div>
            <span class="sql-connection-badge">{{ appStore.currentConnectionName || '当前连接' }}</span>
          </div>

          <n-input
            v-model:value="naturalLanguage"
            type="textarea"
            class="sql-intent-input"
            placeholder="例如：统计近 6 个月各渠道订单金额，并按金额从高到低排序"
            :autosize="{ minRows: 7, maxRows: 12 }"
          />

          <div class="sql-side-actions">
            <n-button size="small" type="primary" :loading="generating" @click="generateSql">
              生成 SQL
            </n-button>
            <n-button size="small" tertiary @click="openWorkflowPage">
              执行链路
            </n-button>
          </div>
        </section>

        <section class="sql-side-section sql-history-section">
          <div class="sql-subhead">
            <h4>生成历史</h4>
            <span>{{ sqlHistory.length }} 条</span>
          </div>

          <div v-if="sqlHistory.length > 0" class="history-list">
            <button
              v-for="(item, index) in sqlHistory"
              :key="item.id ?? `${index}-${item.time}`"
              type="button"
              class="history-item"
              :class="{ active: isHistoryActive(item) }"
              @click="loadHistory(item)"
            >
              <span class="history-sql">{{ summarizeHistoryLabel(item) }}</span>
              <span class="history-time">{{ item.time }}</span>
            </button>
          </div>

          <div v-else class="sql-history-empty">还没有查询记录</div>
        </section>
      </aside>

      <section class="page-section sql-main-panel">
        <div class="sql-main-head">
          <div class="sql-subhead sql-main-subhead">
            <h3>SQL 编辑器</h3>
            <span>{{ mainStatusText }}</span>
          </div>

          <div class="section-actions">
            <n-button size="small" type="primary" :loading="executing" @click="executeSql">
              执行
            </n-button>
            <n-button size="small" secondary @click="formatSql">格式化</n-button>
            <n-button size="small" tertiary @click="clearEditor">清空</n-button>
          </div>
        </div>

        <SqlEditor
          v-model="sqlContent"
          placeholder="SELECT * FROM table_name"
          :rows="13"
        />

        <div class="sql-result-head">
          <div class="sql-subhead">
            <h4>查询结果</h4>
            <span>{{ resultSummaryText }}</span>
          </div>

          <n-button
            v-if="queryResult.columns.length > 0"
            size="small"
            secondary
            @click="exportResult"
          >
            导出 CSV
          </n-button>
        </div>

        <DataTable
          :columns="queryResult.columns"
          :rows="queryResult.rows"
          :error="queryError"
          empty-text="执行查询后，结果会显示在这里"
        />
      </section>
    </div>

    <div v-else class="empty-panel">
      <h3>请先选择数据库连接</h3>
      <n-button type="primary" @click="$router.push('/connections')">前往连接管理</n-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NInput, useMessage } from 'naive-ui'
import { sqlApi } from '@/api'
import DataTable from '@/components/DataTable.vue'
import SqlEditor from '@/components/SqlEditor.vue'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
const message = useMessage()
const router = useRouter()

const naturalLanguage = ref('')
const sqlContent = ref('')
const generating = ref(false)
const executing = ref(false)
const queryError = ref('')
const latestWorkflowRunId = ref('')
type SqlHistoryItem = {
  id?: number
  sql: string
  naturalLanguage?: string
  time: string
}

const sqlHistory = ref<SqlHistoryItem[]>([])

const queryResult = ref<{
  columns: string[]
  rows: Record<string, unknown>[]
}>({
  columns: [],
  rows: []
})

const hasResult = computed(() => queryResult.value.columns.length > 0)

const mainStatusText = computed(() => {
  if (executing.value) {
    return '正在执行查询'
  }

  if (queryError.value) {
    return '上一次执行失败'
  }

  if (hasResult.value) {
    return `${queryResult.value.rows.length} 行 · ${queryResult.value.columns.length} 列`
  }

  if (sqlContent.value.trim()) {
    return '可直接执行当前 SQL'
  }

  return '先描述需求，或直接输入 SQL'
})

const resultSummaryText = computed(() => {
  if (queryError.value) {
    return '返回了错误信息'
  }

  if (!hasResult.value) {
    return '尚未执行'
  }

  return `${queryResult.value.rows.length} 行 · ${queryResult.value.columns.length} 列`
})

const openWorkflowPage = () => {
  router.push({
    name: 'Workflow',
    query: {
      scene: 'sql',
      ...(latestWorkflowRunId.value ? { runId: latestWorkflowRunId.value } : {})
    }
  })
}

const generateSql = async () => {
  if (!naturalLanguage.value.trim()) {
    message.warning('请先输入自然语言描述')
    return
  }

  if (!appStore.currentConnectionId) {
    message.warning('请先选择数据库连接')
    return
  }

  generating.value = true
  try {
    const res = await sqlApi.generate(appStore.currentConnectionId, naturalLanguage.value)
    sqlContent.value = res.data.sql
    latestWorkflowRunId.value = res.data.workflowRunId || ''
    await loadHistoryFromServer()
    message.success('SQL 已生成')
  } catch {
    message.error('SQL 生成失败')
  } finally {
    generating.value = false
  }
}

const executeSql = async () => {
  if (!sqlContent.value.trim()) {
    message.warning('请输入 SQL 语句')
    return
  }

  if (!appStore.currentConnectionId) {
    message.warning('请先选择数据库连接')
    return
  }

  executing.value = true
  queryError.value = ''

  try {
    const res = await sqlApi.execute(appStore.currentConnectionId, sqlContent.value)
    queryResult.value = res.data
    message.success(`查询成功，返回 ${res.data.rows.length} 行`)
  } catch (error: any) {
    queryError.value = error.response?.data?.message || '查询执行失败'
    message.error('查询执行失败')
  } finally {
    executing.value = false
  }
}

const formatSql = async () => {
  if (!sqlContent.value.trim()) {
    return
  }

  try {
    const res = await sqlApi.format(sqlContent.value)
    sqlContent.value = res.data.formatted || res.data.sql || sqlContent.value
  } catch {
    sqlContent.value = sqlContent.value
      .replace(/\bSELECT\b/gi, '\nSELECT')
      .replace(/\bFROM\b/gi, '\nFROM')
      .replace(/\bWHERE\b/gi, '\nWHERE')
      .replace(/\bAND\b/gi, '\n  AND')
      .replace(/\bORDER BY\b/gi, '\nORDER BY')
      .replace(/\bGROUP BY\b/gi, '\nGROUP BY')
      .replace(/\bHAVING\b/gi, '\nHAVING')
      .replace(/\bJOIN\b/gi, '\nJOIN')
      .replace(/\bLEFT JOIN\b/gi, '\nLEFT JOIN')
      .replace(/\bRIGHT JOIN\b/gi, '\nRIGHT JOIN')
      .replace(/\bINNER JOIN\b/gi, '\nINNER JOIN')
      .replace(/\bLIMIT\b/gi, '\nLIMIT')
      .replace(/^\n/, '')
  }
}

const clearEditor = () => {
  sqlContent.value = ''
  queryResult.value = { columns: [], rows: [] }
  queryError.value = ''
}

const summarizeSql = (sql: string) => {
  const normalized = sql.replace(/\s+/g, ' ').trim()
  if (!normalized) {
    return '空白查询'
  }
  return normalized.length > 88 ? `${normalized.slice(0, 88)}...` : normalized
}

const summarizeHistoryLabel = (item: SqlHistoryItem) => {
  const naturalLanguageLabel = item.naturalLanguage?.replace(/\s+/g, ' ').trim() || ''
  if (naturalLanguageLabel) {
    return naturalLanguageLabel.length > 30
      ? `${naturalLanguageLabel.slice(0, 30)}...`
      : naturalLanguageLabel
  }

  return summarizeSql(item.sql)
}

const isHistoryActive = (item: SqlHistoryItem) => item.sql === sqlContent.value

const loadHistory = (item: SqlHistoryItem) => {
  sqlContent.value = item.sql
  naturalLanguage.value = item.naturalLanguage || ''
  queryError.value = ''
}

const formatHistoryTime = (item: any) => {
  if (item.executionTimeMs) {
    return `${item.executionTimeMs} ms`
  }

  if (item.createdAt) {
    const time = new Date(item.createdAt)
    if (!Number.isNaN(time.getTime())) {
      return time.toLocaleString('zh-CN', {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      })
    }
    return item.createdAt
  }

  return '刚刚'
}

const loadHistoryFromServer = async () => {
  if (!appStore.currentConnectionId) {
    sqlHistory.value = []
    return
  }

  try {
    const res = await sqlApi.getHistory(appStore.currentConnectionId)
    sqlHistory.value = (res.data || []).map((item: any) => ({
      id: item.id,
      sql: item.sql,
      naturalLanguage: item.naturalLanguage,
      time: formatHistoryTime(item)
    }))
  } catch (error) {
    console.error('Failed to load SQL history', error)
  }
}

const escapeCsvCell = (value: unknown) => {
  if (value === null || value === undefined) {
    return ''
  }

  const normalized = typeof value === 'object' ? JSON.stringify(value) : String(value)
  return `"${normalized.replace(/"/g, '""')}"`
}

const downloadBlob = (blob: Blob, fileName: string) => {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  anchor.click()
  URL.revokeObjectURL(url)
}

const exportResult = () => {
  if (queryResult.value.rows.length === 0) {
    return
  }

  const headers = queryResult.value.columns.map((col) => escapeCsvCell(col)).join(',')
  const rows = queryResult.value.rows.map((row) =>
    queryResult.value.columns.map((col) => escapeCsvCell(row[col])).join(',')
  )
  const csv = [headers, ...rows].join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  downloadBlob(blob, `query_result_${Date.now()}.csv`)
  message.success('CSV 导出成功')
}

onMounted(() => {
  loadHistoryFromServer()
})

watch(
  () => appStore.currentConnectionId,
  (connectionId) => {
    if (!connectionId) {
      sqlHistory.value = []
      latestWorkflowRunId.value = ''
    } else {
      loadHistoryFromServer()
    }

    queryResult.value = { columns: [], rows: [] }
    queryError.value = ''
  }
)
</script>

<style scoped>
.sql-studio-page {
  min-width: 0;
}

.sql-studio-layout {
  display: grid;
  grid-template-columns: minmax(320px, 360px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.sql-side-panel,
.sql-main-panel {
  min-width: 0;
}

.sql-side-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 18px;
}

.sql-side-section {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.sql-side-section + .sql-side-section {
  padding-top: 18px;
  border-top: 1px solid var(--line-soft);
}

.sql-panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.sql-panel-kicker {
  color: var(--primary-color);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.sql-panel-heading h3 {
  margin: 6px 0 0;
  color: var(--text-color);
  font-family: var(--font-display);
  font-size: 26px;
  line-height: 1.04;
  letter-spacing: -0.04em;
}

.sql-connection-badge {
  flex: 0 0 auto;
  padding: 6px 11px;
  border-radius: 999px;
  background: var(--surface-active);
  color: var(--primary-color-strong);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.sql-intent-input :deep(.n-input) {
  --n-color: var(--background-elevated) !important;
  --n-color-focus: var(--background-strong) !important;
  --n-color-disabled: var(--surface-disabled) !important;
  --n-text-color: var(--text-color) !important;
  --n-placeholder-color: var(--text-muted) !important;
  --n-border: 1px solid var(--line-soft) !important;
  --n-border-hover: 1px solid var(--border-accent-soft) !important;
  --n-border-focus: 1px solid var(--border-accent) !important;
  --n-box-shadow-focus: none !important;
  border-radius: 16px !important;
}

.sql-intent-input :deep(.n-input__textarea-el),
.sql-intent-input :deep(.n-input__textarea-mirror),
.sql-intent-input :deep(.n-input__placeholder) {
  box-sizing: border-box;
  padding: 15px 16px !important;
  line-height: 1.75 !important;
}

.sql-side-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.sql-history-section {
  min-height: 0;
}

.sql-subhead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.sql-subhead h3,
.sql-subhead h4 {
  margin: 0;
  color: var(--text-color);
  font-family: var(--font-display);
  line-height: 1.08;
  letter-spacing: -0.03em;
}

.sql-subhead h3 {
  font-size: 24px;
}

.sql-subhead h4 {
  font-size: 19px;
}

.sql-subhead span {
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: calc(100vh - 360px);
  overflow: auto;
  padding-right: 4px;
}

.history-item {
  appearance: none;
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid transparent;
  background: var(--background-muted);
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.18s ease,
    background-color 0.18s ease,
    transform 0.18s ease;
}

.history-item:hover {
  border-color: var(--border-accent-soft);
  background: var(--background-elevated);
  transform: translateY(-1px);
}

.history-item.active {
  border-color: var(--border-accent);
  background: var(--surface-active);
}

.history-sql {
  color: var(--text-color);
  font-size: 13px;
  line-height: 1.6;
  display: block;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.history-time {
  color: var(--text-secondary);
  font-size: 12px;
}

.sql-history-empty {
  padding: 28px 16px;
  border-radius: 16px;
  background: var(--surface-subtle);
  color: var(--text-secondary);
  text-align: center;
}

.sql-main-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 18px;
}

.sql-main-head,
.sql-result-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.sql-main-subhead {
  min-width: 0;
}

@media (max-width: 980px) {
  .sql-studio-layout {
    grid-template-columns: 1fr;
  }

  .sql-side-panel {
    order: 2;
  }

  .sql-main-panel {
    order: 1;
  }

  .history-list {
    max-height: none;
  }
}

@media (max-width: 720px) {
  .sql-panel-heading,
  .sql-main-head,
  .sql-result-head {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
