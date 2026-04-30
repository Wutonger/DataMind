<template>
  <div class="workspace-page analysis-page">
    <template v-if="appStore.currentConnectionId">
      <section class="page-section">
        <div class="section-head section-head-actions-only">
          <div class="section-actions">
            <n-button tertiary :disabled="scanning" @click="openWorkflowPage">
              执行链路
            </n-button>
            <n-button type="primary" :disabled="scanning" @click="scanAllTables">扫描全部</n-button>
          </div>
        </div>

        <n-data-table
          :columns="columns"
          :data="paginatedTables"
          :bordered="false"
          :loading="loading"
          striped
          size="small"
        />

        <div class="analysis-pagination">
          <n-pagination
            v-model:page="page"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :item-count="total"
            show-size-picker
            show-quick-jumper
          />
        </div>
      </section>

      <section v-if="allRelations.length > 0" class="page-section">
        <div class="section-head">
          <div class="section-copy">
            <h3 class="section-title">关系视图</h3>
          </div>
        </div>

        <n-tabs v-model:value="relationViewType" type="segment" size="small">
          <n-tab-pane name="table" tab="关系表" style="padding-top: 16px;">
            <n-data-table
              :columns="relationColumns"
              :data="allRelations"
              :bordered="true"
              :loading="loading"
              striped
              size="small"
            />
          </n-tab-pane>

          <n-tab-pane name="er" tab="ER 视图" style="padding-top: 16px;">
            <div class="chart-container" ref="chartContainer">
              <div class="chart-controls">
                <span>{{ Math.round(zoomLevel * 100) }}%</span>
                <n-button size="small" secondary @click="resetZoom">重置视图</n-button>
              </div>
              <div
                ref="mermaidRef"
                class="mermaid-content"
                :style="{
                  transform: `scale(${zoomLevel}) translate(${position.x}px, ${position.y}px)`,
                  transformOrigin: 'center center'
                }"
              ></div>
            </div>
          </n-tab-pane>
        </n-tabs>
      </section>

      <div v-else class="empty-panel">
        <h3>还没有关系视图</h3>
      </div>

      <n-modal
        v-model:show="showTableDetailModal"
        preset="card"
        :title="selectedTable?.tableName ? `${selectedTable.tableName} · 结构详情` : '结构详情'"
        style="width: 860px; max-width: 92vw;"
        :bordered="false"
        :segmented="{ content: true }"
      >
        <div v-if="selectedTable" class="analysis-content">
          <p v-if="selectedTable.aiDescription" class="analysis-description">
            {{ selectedTable.aiDescription }}
          </p>

          <div v-if="tableRelations.length > 0" class="pill-list" style="margin-bottom: 18px;">
            <span v-for="rel in tableRelations" :key="`${rel.column}-${rel.targetTable}`" class="data-chip">
              {{ rel.column }} → {{ rel.targetTable }}.{{ rel.targetColumn }}
            </span>
          </div>

          <div v-if="parsedFields.length > 0">
            <h4 class="analysis-subtitle">字段详情</h4>
            <n-data-table
              :columns="fieldColumns"
              :data="parsedFields"
              :bordered="false"
              size="small"
              striped
            />
          </div>
        </div>
      </n-modal>

      <div v-if="scanning" class="analysis-scan-overlay">
        <div class="analysis-scan-panel">
          <n-spin size="large" />
          <div class="analysis-scan-title">{{ activeScanOverlayTitle }}</div>
          <div v-if="activeScanOverlayMeta" class="analysis-scan-meta">{{ activeScanOverlayMeta }}</div>
        </div>
      </div>
    </template>

    <div v-else class="empty-panel">
      <h3>请先选择数据库连接</h3>
      <n-button type="primary" @click="$router.push('/connections')">前往连接管理</n-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, h, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  NButton,
  NDataTable,
  NModal,
  NPagination,
  NSpin,
  NTabPane,
  NTabs,
  useMessage
} from 'naive-ui'
import mermaid from 'mermaid'
import { tableApi } from '@/api'
import { useAppStore } from '@/stores/app'

interface TableMetadataItem {
  tableName: string
  rowCount?: number
  aiDescription?: string
  fields?: string
  relations?: string
}

interface RelationItem {
  sourceTable: string
  column: string
  targetTable: string
  targetColumn: string
  type: string
}

interface ScanProgressState {
  status: 'running' | 'completed' | 'failed'
  stage?: 'scan' | 'relation'
  totalTables: number
  processedTables: number
  batchSize: number
  batchIndex: number
  totalBatches: number
  batchStart: number
  batchEnd: number
  percent: number
  message: string
}

interface TableScanEventData {
  runId?: string
  stage?: string
  tables?: TableMetadataItem[]
  totalTables?: number
  processedTables?: number
  batchSize?: number
  batchIndex?: number
  totalBatches?: number
  batchStart?: number
  batchEnd?: number
  percent?: number
  message?: string
}

interface TableScanEvent {
  type: string
  data?: TableScanEventData
}

const appStore = useAppStore()
const message = useMessage()
const router = useRouter()

const tables = ref<TableMetadataItem[]>([])
const loading = ref(false)
const scanning = ref(false)
const scanProgress = ref<ScanProgressState | null>(null)
const latestWorkflowRunId = ref('')
const selectedTable = ref<TableMetadataItem | null>(null)
const chartContainer = ref<HTMLElement | null>(null)
const mermaidRef = ref<HTMLElement | null>(null)
const showTableDetailModal = ref(false)
const zoomLevel = ref(1)
const position = ref({ x: 0, y: 0 })
const isDragging = ref(false)
const startPosition = ref({ x: 0, y: 0 })
const relationViewType = ref<'table' | 'er'>('table')

const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const createDefaultScanProgress = (): ScanProgressState => ({
  status: 'running',
  stage: 'scan',
  totalTables: 0,
  processedTables: 0,
  batchSize: 10,
  batchIndex: 0,
  totalBatches: 0,
  batchStart: 0,
  batchEnd: 0,
  percent: 0,
  message: '正在准备扫描'
})

const toNumber = (value: unknown, fallback = 0) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

const updateScanProgress = (patch: Partial<ScanProgressState>) => {
  scanProgress.value = {
    ...(scanProgress.value ?? createDefaultScanProgress()),
    ...patch
  }
}

const paginatedTables = computed(() => {
  const start = (page.value - 1) * pageSize.value
  const end = start + pageSize.value
  return tables.value.slice(start, end)
})

const scanBatchText = computed(() => {
  if (!scanProgress.value?.totalBatches) {
    return ''
  }
  const batchIndex = scanProgress.value.batchIndex || 1
  return `第 ${Math.min(batchIndex, scanProgress.value.totalBatches)}/${scanProgress.value.totalBatches} 批`
})

const scanRangeText = computed(() => {
  if (!scanProgress.value || scanProgress.value.batchStart <= 0 || scanProgress.value.batchEnd <= 0) {
    return ''
  }
  return `${scanProgress.value.batchStart} - ${scanProgress.value.batchEnd}`
})

const scanOverlayTitle = computed(() => {
  if (!scanProgress.value) {
    return '正在分析数据表'
  }
  return scanProgress.value.message || '正在分析数据表'
})

const scanOverlayMeta = computed(() => {
  if (!scanProgress.value) {
    return ''
  }

  return [
    `${scanProgress.value.processedTables} / ${scanProgress.value.totalTables} 张表`,
    scanBatchText.value,
    scanRangeText.value
  ].filter(Boolean).join(' · ')
})

const scanOverlayDisplayTitle = computed(() => {
  if (!scanProgress.value) {
    return '正在分析数据表'
  }

  if (scanProgress.value.totalTables <= 1 && scanProgress.value.message) {
    return scanProgress.value.message
  }

  if (scanProgress.value.status === 'completed') {
    return '分析完成'
  }

  if (scanProgress.value.status === 'failed') {
    return scanProgress.value.message || '表分析失败'
  }

  return '正在分析数据表'
})

const scanOverlayDisplayMeta = computed(() => {
  if (!scanProgress.value || scanProgress.value.totalTables <= 0) {
    return ''
  }

  if (scanProgress.value.status === 'completed') {
    return `共处理 ${scanProgress.value.processedTables} / ${scanProgress.value.totalTables} 张表`
  }

  if (scanProgress.value.status === 'failed') {
    return ''
  }

  return `已完成 ${scanProgress.value.processedTables} / ${scanProgress.value.totalTables} 张表`
})

const activeScanOverlayTitle = computed(() => {
  if (!scanProgress.value) {
    return scanOverlayDisplayTitle.value
  }

  if (scanProgress.value.status === 'running' && scanProgress.value.stage === 'relation') {
    return '正在分析表关系'
  }

  return scanOverlayDisplayTitle.value
})

const activeScanOverlayMeta = computed(() => {
  if (!scanProgress.value) {
    return scanOverlayDisplayMeta.value
  }

  if (scanProgress.value.status === 'running' && scanProgress.value.stage === 'relation') {
    return `已完成 ${scanProgress.value.processedTables} / ${scanProgress.value.totalTables} 张表结构，正在汇总表关系`
  }

  return scanOverlayDisplayMeta.value
})

void scanOverlayTitle
void scanOverlayMeta

const openWorkflowPage = () => {
  router.push({
    name: 'Workflow',
    query: {
      scene: 'analysis',
      ...(latestWorkflowRunId.value ? { runId: latestWorkflowRunId.value } : {})
    }
  })
}

watch(selectedTable, (newValue) => {
  showTableDetailModal.value = Boolean(newValue)
})

const columns = [
  { title: '表名', key: 'tableName', ellipsis: { tooltip: true } },
  { title: '行数', key: 'rowCount', width: 100 },
  { title: '智能描述', key: 'aiDescription', ellipsis: { tooltip: true } },
  {
    title: '操作',
    key: 'action',
    width: 100,
    render: (row: TableMetadataItem) =>
      h(
        NButton,
        { size: 'tiny', tertiary: true, disabled: scanning.value, onClick: () => (selectedTable.value = row) },
        () => '详情'
      )
  }
]

const fieldColumns = [
  { title: '字段名', key: 'name', width: 160 },
  { title: '类型', key: 'type', width: 140 },
  { title: '可空', key: 'nullable', width: 80, render: (row: any) => (row.nullable ? '是' : '否') },
  { title: '键', key: 'key', width: 80 },
  { title: '注释', key: 'comment', ellipsis: { tooltip: true } }
]

const relationColumns = [
  { title: '源表', key: 'sourceTable', width: 150 },
  { title: '源字段', key: 'column', width: 120 },
  { title: '目标表', key: 'targetTable', width: 150 },
  { title: '目标字段', key: 'targetColumn', width: 120 },
  {
    title: '关系类型',
    key: 'type',
    width: 100,
    render: (row: RelationItem) => (row.type === 'fk' ? '物理外键' : '推测关系')
  }
]

const parsedFields = computed(() => {
  if (!selectedTable.value?.fields) {
    return []
  }

  try {
    return JSON.parse(selectedTable.value.fields)
  } catch {
    return []
  }
})

const tableRelations = computed(() => {
  if (!selectedTable.value?.relations) {
    return []
  }

  try {
    return JSON.parse(selectedTable.value.relations)
  } catch {
    return []
  }
})

const allRelations = computed<RelationItem[]>(() => {
  const existingTableNames = new Set(tables.value.map((table) => table.tableName))
  const relations: RelationItem[] = []

  tables.value.forEach((table) => {
    if (!table.relations) {
      return
    }

    try {
      const parsed = JSON.parse(table.relations)
      parsed.forEach((rel: RelationItem) => {
        if (existingTableNames.has(rel.targetTable)) {
          relations.push({
            ...rel,
            sourceTable: table.tableName
          })
        }
      })
    } catch {
      // ignore invalid relation payloads
    }
  })

  return relations
})

const syncSelectedTable = () => {
  if (!selectedTable.value) {
    return
  }

  const latest = tables.value.find((table) => table.tableName === selectedTable.value?.tableName)
  if (latest) {
    selectedTable.value = latest
  }
}

const removeChartInteractions = () => {
  if (chartContainer.value) {
    chartContainer.value.removeEventListener('wheel', handleWheel)
    chartContainer.value.removeEventListener('mousedown', handleMouseDown)
  }
  document.removeEventListener('mousemove', handleMouseMove)
  document.removeEventListener('mouseup', handleMouseUp)
}

const bindChartInteractions = () => {
  removeChartInteractions()

  if (!chartContainer.value) {
    return
  }

  chartContainer.value.addEventListener('wheel', handleWheel, { passive: false })
  chartContainer.value.addEventListener('mousedown', handleMouseDown)
  document.addEventListener('mousemove', handleMouseMove)
  document.addEventListener('mouseup', handleMouseUp)
}

const initMermaid = async () => {
  if (!mermaidRef.value || tables.value.length === 0) {
    return
  }

  const existingTableNames = new Set(tables.value.map((table) => table.tableName))
  const tableSet = new Set<string>()

  allRelations.value.forEach((rel) => {
    tableSet.add(rel.sourceTable)
    tableSet.add(rel.targetTable)
  })

  if (tableSet.size === 0) {
    tables.value.forEach((table) => tableSet.add(table.tableName))
  }

  let mermaidCode = 'erDiagram\n\n'

  tableSet.forEach((tableName) => {
    const table = tables.value.find((item) => item.tableName === tableName)
    if (!table) {
      return
    }

    mermaidCode += `  ${tableName} {\n`

    if (table.fields) {
      try {
        const fields = JSON.parse(table.fields)
        fields.forEach((field: any) => {
          let fieldType = String(field.type || 'string')
          if (fieldType.includes('(')) {
            fieldType = fieldType.split('(')[0]
          }
          fieldType = fieldType.replace(/['"]/g, '')

          let fieldDef = `    ${fieldType} ${field.name}`
          if (field.key === 'PRI') {
            fieldDef += ' PK'
          } else if (field.key === 'UNI') {
            fieldDef += ' UK'
          }
          if (field.comment) {
            fieldDef += ` "${field.comment}"`
          }
          mermaidCode += `${fieldDef}\n`
        })
      } catch {
        // ignore invalid fields payloads
      }
    }

    mermaidCode += '  }\n\n'
  })

  allRelations.value.forEach((rel) => {
    if (existingTableNames.has(rel.sourceTable) && existingTableNames.has(rel.targetTable)) {
      const relationType = rel.type === 'fk' ? '||--||' : '||--o{'
      mermaidCode += `  ${rel.sourceTable} ${relationType} ${rel.targetTable} : "${rel.column}"\n`
    }
  })

  mermaidRef.value.innerHTML = `<pre class="mermaid">${mermaidCode}</pre>`

  await nextTick()
  await mermaid.run()
  bindChartInteractions()
}

const handleWheel = (event: WheelEvent) => {
  event.preventDefault()
  const delta = event.deltaY > 0 ? -0.1 : 0.1
  zoomLevel.value = Math.max(0.35, Math.min(2.8, zoomLevel.value + delta))
}

const handleMouseDown = (event: MouseEvent) => {
  isDragging.value = true
  startPosition.value = {
    x: event.clientX - position.value.x,
    y: event.clientY - position.value.y
  }
}

const handleMouseMove = (event: MouseEvent) => {
  if (!isDragging.value) {
    return
  }

  position.value = {
    x: event.clientX - startPosition.value.x,
    y: event.clientY - startPosition.value.y
  }
}

const handleMouseUp = () => {
  isDragging.value = false
}

const resetZoom = () => {
  zoomLevel.value = 1
  position.value = { x: 0, y: 0 }
}

const handleTableScanEvent = (event: TableScanEvent) => {
  const data = event.data || {}

  if (data.runId) {
    latestWorkflowRunId.value = data.runId
  }

  switch (event.type) {
    case 'SCAN_STARTED':
      scanProgress.value = {
        status: 'running',
        totalTables: toNumber(data.totalTables),
        processedTables: toNumber(data.processedTables),
        batchSize: toNumber(data.batchSize, 10),
        batchIndex: toNumber(data.batchIndex),
        totalBatches: toNumber(data.totalBatches),
        batchStart: toNumber(data.batchStart),
        batchEnd: toNumber(data.batchEnd),
        percent: toNumber(data.percent),
        message: data.message || '正在准备扫描'
      }
      break

    case 'BATCH_STARTED':
    case 'BATCH_COMPLETED':
      updateScanProgress({
        status: 'running',
        totalTables: toNumber(data.totalTables, scanProgress.value?.totalTables ?? 0),
        processedTables: toNumber(data.processedTables, scanProgress.value?.processedTables ?? 0),
        batchSize: toNumber(data.batchSize, scanProgress.value?.batchSize ?? 10),
        batchIndex: toNumber(data.batchIndex, scanProgress.value?.batchIndex ?? 0),
        totalBatches: toNumber(data.totalBatches, scanProgress.value?.totalBatches ?? 0),
        batchStart: toNumber(data.batchStart, scanProgress.value?.batchStart ?? 0),
        batchEnd: toNumber(data.batchEnd, scanProgress.value?.batchEnd ?? 0),
        percent: toNumber(data.percent, scanProgress.value?.percent ?? 0),
        message: data.message || scanProgress.value?.message || '正在扫描'
      })
      break

    case 'SCAN_COMPLETED':
      if (Array.isArray(data.tables)) {
        tables.value = data.tables
        total.value = tables.value.length
        page.value = 1
        syncSelectedTable()
      }
      updateScanProgress({
        status: 'completed',
        totalTables: toNumber(data.totalTables, tables.value.length),
        processedTables: toNumber(data.processedTables, tables.value.length),
        percent: 100,
        message: data.message || `扫描完成，共更新 ${tables.value.length} 张表`
      })
      break

    case 'SCAN_FAILED':
      updateScanProgress({
        status: 'failed',
        message: data.message || '扫描失败'
      })
      break
  }
}

const handleTableScanProgressEvent = (event: TableScanEvent) => {
  const data = event.data || {}

  if (data.runId) {
    latestWorkflowRunId.value = data.runId
  }

  switch (event.type) {
    case 'SCAN_STARTED':
      scanProgress.value = {
        status: 'running',
        stage: data.stage === 'relation' ? 'relation' : 'scan',
        totalTables: toNumber(data.totalTables),
        processedTables: toNumber(data.processedTables),
        batchSize: toNumber(data.batchSize, 10),
        batchIndex: toNumber(data.batchIndex),
        totalBatches: toNumber(data.totalBatches),
        batchStart: toNumber(data.batchStart),
        batchEnd: toNumber(data.batchEnd),
        percent: toNumber(data.percent),
        message: data.message || 'Scanning tables'
      }
      break

    case 'BATCH_STARTED':
    case 'BATCH_COMPLETED':
      updateScanProgress({
        status: 'running',
        stage: data.stage === 'relation' ? 'relation' : 'scan',
        totalTables: toNumber(data.totalTables, scanProgress.value?.totalTables ?? 0),
        processedTables: toNumber(data.processedTables, scanProgress.value?.processedTables ?? 0),
        batchSize: toNumber(data.batchSize, scanProgress.value?.batchSize ?? 10),
        batchIndex: toNumber(data.batchIndex, scanProgress.value?.batchIndex ?? 0),
        totalBatches: toNumber(data.totalBatches, scanProgress.value?.totalBatches ?? 0),
        batchStart: toNumber(data.batchStart, scanProgress.value?.batchStart ?? 0),
        batchEnd: toNumber(data.batchEnd, scanProgress.value?.batchEnd ?? 0),
        percent: toNumber(data.percent, scanProgress.value?.percent ?? 0),
        message: data.message || scanProgress.value?.message || 'Scanning tables'
      })
      break

    case 'RELATION_ANALYSIS_STARTED':
    case 'RELATION_ANALYSIS_COMPLETED':
      updateScanProgress({
        status: 'running',
        stage: 'relation',
        totalTables: toNumber(data.totalTables, scanProgress.value?.totalTables ?? 0),
        processedTables: toNumber(data.processedTables, scanProgress.value?.processedTables ?? 0),
        batchSize: toNumber(data.batchSize, scanProgress.value?.batchSize ?? 10),
        batchIndex: toNumber(data.batchIndex, scanProgress.value?.batchIndex ?? 0),
        totalBatches: toNumber(data.totalBatches, scanProgress.value?.totalBatches ?? 0),
        batchStart: toNumber(data.batchStart, scanProgress.value?.batchStart ?? 0),
        batchEnd: toNumber(data.batchEnd, scanProgress.value?.batchEnd ?? 0),
        percent: toNumber(data.percent, scanProgress.value?.percent ?? 0),
        message: data.message || scanProgress.value?.message || 'Analyzing table relations'
      })
      break

    case 'SCAN_COMPLETED':
      if (Array.isArray(data.tables)) {
        tables.value = data.tables
        total.value = tables.value.length
        page.value = 1
        syncSelectedTable()
      }
      updateScanProgress({
        status: 'completed',
        stage: 'relation',
        totalTables: toNumber(data.totalTables, tables.value.length),
        processedTables: toNumber(data.processedTables, tables.value.length),
        percent: 100,
        message: data.message || `Completed analysis for ${tables.value.length} tables`
      })
      break

    case 'SCAN_FAILED':
      updateScanProgress({
        status: 'failed',
        stage: scanProgress.value?.stage ?? 'scan',
        message: data.message || 'Table analysis failed'
      })
      break

    default:
      handleTableScanEvent(event)
      break
  }
}

const processScanStreamLine = (line: string) => {
  const trimmed = line.trim()
  if (!trimmed.startsWith('data:')) {
    return
  }

  let jsonStr = trimmed
  while (jsonStr.startsWith('data:')) {
    jsonStr = jsonStr.replace(/^data:\s*/, '')
  }
  if (!jsonStr || jsonStr === '[DONE]') {
    return
  }

  try {
    const event = JSON.parse(jsonStr) as TableScanEvent
    handleTableScanProgressEvent(event)
  } catch (error) {
    console.error('Failed to parse table scan event', trimmed, error)
  }
}

watch(allRelations, () => {
  if (relationViewType.value === 'er') {
    nextTick(() => initMermaid())
  }
})

watch(relationViewType, (newValue) => {
  if (newValue === 'er') {
    nextTick(() => initMermaid())
  }
})

watch(
  () => appStore.currentConnectionId,
  () => {
    selectedTable.value = null
    showTableDetailModal.value = false
    scanProgress.value = null
    latestWorkflowRunId.value = ''
    loadMetadata()
  }
)

const loadMetadata = async () => {
  if (!appStore.currentConnectionId) {
    tables.value = []
    total.value = 0
    return
  }

  loading.value = true
  try {
    const res = await tableApi.getMetadata(appStore.currentConnectionId)
    tables.value = res.data || []
    total.value = tables.value.length
    page.value = 1
    syncSelectedTable()
  } catch {
    message.error('加载元数据失败')
  } finally {
    loading.value = false
  }
}

const scanAllTables = async () => {
  if (!appStore.currentConnectionId) {
    return
  }

  scanning.value = true
  scanProgress.value = null

  try {
    const response = await tableApi.scanAll(appStore.currentConnectionId)

    if (!response.ok || !response.body) {
      throw new Error('扫描请求失败')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let pendingBuffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }

      pendingBuffer += decoder.decode(value, { stream: true })
      const lines = pendingBuffer.split(/\r?\n/)
      pendingBuffer = lines.pop() ?? ''

      for (const line of lines) {
        processScanStreamLine(line)
      }
    }

    pendingBuffer += decoder.decode()
    if (pendingBuffer.trim()) {
      processScanStreamLine(pendingBuffer)
    }

    const finalProgress = scanProgress.value as ScanProgressState | null

    if (finalProgress && finalProgress.status === 'failed') {
      throw new Error(finalProgress.message || '扫描失败')
    }

    if (!finalProgress || finalProgress.status !== 'completed') {
      await loadMetadata()
      updateScanProgress({
        status: 'completed',
        totalTables: tables.value.length,
        processedTables: tables.value.length,
        percent: 100,
        message: `扫描完成，共更新 ${tables.value.length} 张表`
      })
    }

    message.success((scanProgress.value as ScanProgressState | null)?.message || `扫描完成，共更新 ${tables.value.length} 张表`)
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : '扫描失败'
    updateScanProgress({
      status: 'failed',
      message: errorMessage
    })
    message.error(errorMessage)
  } finally {
    scanning.value = false
  }
}

onMounted(() => {
  mermaid.initialize({
    startOnLoad: true,
    theme: 'base',
    themeVariables: {
      primaryColor: '#fff5ef',
      primaryTextColor: '#3f3129',
      primaryBorderColor: '#ef5b2a',
      lineColor: '#d97a56',
      tertiaryColor: '#ffffff'
    },
    er: {
      diagramPadding: 20,
      layoutDirection: 'TB',
      entityPadding: 15
    }
  })

  loadMetadata()
})

onBeforeUnmount(() => {
  removeChartInteractions()
})
</script>

<style scoped>
.analysis-page {
  position: relative;
}

.analysis-content {
  font-size: 14px;
  line-height: 1.8;
}

.analysis-scan-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(6px);
  z-index: 20;
}

.analysis-scan-panel {
  min-width: 300px;
  max-width: 420px;
  padding: 24px 28px;
  border-radius: 22px;
  border: 1px solid rgba(239, 91, 42, 0.16);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 22px 60px rgba(142, 79, 44, 0.14);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  text-align: center;
}

.analysis-scan-title {
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 600;
}

.analysis-scan-meta {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.analysis-pagination {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
}

.analysis-description {
  margin: 0 0 18px;
  color: var(--text-secondary);
}

.analysis-subtitle {
  margin: 0 0 10px;
  font-family: var(--font-display);
  font-size: 20px;
  letter-spacing: -0.04em;
}

.chart-container {
  overflow: hidden;
  border-radius: 18px;
  border: 1px solid var(--line-soft);
  background: rgba(255, 255, 255, 0.98);
}

.chart-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--line-soft);
  color: var(--text-secondary);
  font-size: 13px;
}

.mermaid-content {
  min-height: 460px;
  padding: 20px;
  cursor: grab;
}

.mermaid-content:active {
  cursor: grabbing;
}

.mermaid-content :deep(svg) {
  max-width: none;
}
</style>
