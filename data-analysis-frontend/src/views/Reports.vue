<template>
  <div class="workspace-page">
    <section v-if="appStore.currentConnectionId" class="page-section report-page">
      <div class="section-head section-head-actions-only">
        <div class="section-actions">
          <n-button tertiary @click="openWorkflowPage">执行链路</n-button>
          <n-button type="primary" @click="showAiModal = true">AI 生成报表</n-button>
        </div>
      </div>

      <div class="report-overview">
        <div class="report-stat-card">
          <span>报表总数</span>
          <strong>{{ reports.length }}</strong>
        </div>
        <div class="report-stat-card">
          <span>文档报告</span>
          <strong>{{ reportStats.documentCount }}</strong>
        </div>
        <div class="report-stat-card">
          <span>图表报表</span>
          <strong>{{ reportStats.chartCount }}</strong>
        </div>
        <div class="report-stat-card">
          <span>当前连接</span>
          <strong>{{ appStore.currentConnectionName || '未命名连接' }}</strong>
        </div>
      </div>

      <div class="report-toolbar">
        <div class="report-toolbar-main">
          <n-input
            v-model:value="keyword"
            clearable
            placeholder="搜索报表名称、摘要或 SQL"
          />
          <n-select
            v-model:value="typeFilter"
            clearable
            :options="typeFilterOptions"
            placeholder="筛选类型"
          />
        </div>
      </div>

      <div class="report-table-shell">
        <n-data-table
          :columns="columns"
          :data="paginatedReports"
          :bordered="false"
          :loading="loading"
          :pagination="false"
          :scroll-x="1080"
          striped
          size="small"
        />
      </div>

      <div class="report-pagination">
        <div class="report-pagination-meta">共 {{ filteredReports.length }} 条</div>
        <n-pagination
          v-model:page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :item-count="filteredReports.length"
          show-size-picker
          show-quick-jumper
        />
      </div>
    </section>

    <div v-else class="empty-panel">
      <h3>请先选择数据库连接</h3>
      <n-button type="primary" @click="$router.push('/connections')">前往连接管理</n-button>
    </div>

    <n-modal
      v-model:show="showAiModal"
      preset="card"
      title="AI 生成报表"
      style="width: 560px; max-width: 92vw;"
    >
      <n-form label-placement="top">
        <n-form-item label="报表需求">
          <n-input
            v-model:value="aiRequirement"
            type="textarea"
            placeholder="例如：生成一份博客系统月度运营分析报告，或生成博客发布趋势图"
            :rows="4"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showAiModal = false">取消</n-button>
          <n-button type="primary" :loading="generatingReport" @click="generateAiReport">生成</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal
      v-model:show="showPreviewModal"
      preset="card"
      :title="modalTitle"
      style="width: 920px; max-width: 92vw;"
    >
      <div v-if="previewMode === 'markdown'" class="report-detail markdown-body" v-html="previewMarkdownHtml"></div>
      <ChartRenderer v-else-if="previewOption" :option="previewOption" :height="460" />
      <div v-else class="report-preview-empty">暂无可预览内容</div>

      <div v-if="previewSql" class="preview-sql">
        <h4>生成 SQL</h4>
        <pre class="code-preview">{{ previewSql }}</pre>
      </div>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import type { DataTableColumns } from 'naive-ui'
import {
  NButton,
  NDataTable,
  NForm,
  NFormItem,
  NInput,
  NModal,
  NPagination,
  NSelect,
  NSpace,
  NTag,
  useMessage
} from 'naive-ui'
import { reportApi } from '@/api'
import ChartRenderer from '@/components/ChartRenderer.vue'
import { useAppStore } from '@/stores/app'
import { renderMarkdownContent } from '@/utils/markdown'

interface ReportRecord {
  id: number
  name: string
  query?: string
  chartType?: string
  config?: string | Record<string, any> | null
  createdAt?: string
}

interface StoredReportConfig extends Record<string, any> {
  type?: string
  content?: string
}

const CHART_EXPORT_PALETTE = ['#ef5b2a', '#f08a24', '#d64550', '#f3a35c', '#b8613c', '#f7c08a']

const REPORT_TYPE_LABELS: Record<string, string> = {
  report: '文档报告',
  auto: '图表报表',
  line: '折线图',
  bar: '柱状图',
  pie: '饼图',
  scatter: '散点图'
}

const appStore = useAppStore()
const message = useMessage()
const router = useRouter()

const reports = ref<ReportRecord[]>([])
const loading = ref(false)
const showAiModal = ref(false)
const showPreviewModal = ref(false)
const generatingReport = ref(false)
const aiRequirement = ref('')
const keyword = ref('')
const typeFilter = ref<string | null>(null)
const page = ref(1)
const pageSize = ref(10)
const latestWorkflowRunId = ref('')
const previewSql = ref('')
const previewOption = ref<Record<string, any> | null>(null)
const previewMarkdown = ref('')
const previewMode = ref<'chart' | 'markdown'>('chart')

const modalTitle = computed(() => (previewMode.value === 'markdown' ? '查看报表' : '预览报表'))
const previewMarkdownHtml = computed(() => renderMarkdownContent(previewMarkdown.value || ''))

const typeFilterOptions = [
  { label: '文档报告', value: 'report' },
  { label: '图表报表', value: 'chart' }
]

const parseReportConfig = (report: ReportRecord): StoredReportConfig | null => {
  if (!report.config) {
    return null
  }

  if (typeof report.config === 'string') {
    try {
      const parsed = JSON.parse(report.config)
      return parsed && typeof parsed === 'object' ? (parsed as StoredReportConfig) : null
    } catch {
      return null
    }
  }

  return report.config
}

const isMarkdownReport = (report: ReportRecord): boolean => {
  const config = parseReportConfig(report)
  return report.chartType === 'report' || config?.type === 'markdown'
}

const getChartOption = (report: ReportRecord): Record<string, any> | undefined => {
  const config = parseReportConfig(report)
  if (!config || config.type === 'markdown') {
    return undefined
  }
  return config
}

const getReportMarkdown = (report: ReportRecord): string => {
  const config = parseReportConfig(report)
  if (config?.type === 'markdown' && typeof config.content === 'string') {
    return config.content
  }
  return ''
}

const getReportTypeLabel = (report: ReportRecord): string => {
  if (isMarkdownReport(report)) {
    return REPORT_TYPE_LABELS.report
  }

  if (!report.chartType) {
    return '图表报表'
  }

  return REPORT_TYPE_LABELS[report.chartType] || report.chartType
}

const canExportPdf = (report: ReportRecord): boolean => {
  if (isMarkdownReport(report)) {
    return Boolean(report.id)
  }

  return Boolean(getChartOption(report)) || Boolean(report.id)
}

const summarizePreviewText = (value: string, maxLength = 120): string => {
  const normalized = value.replace(/\s+/g, ' ').trim()
  if (!normalized) {
    return ''
  }

  return normalized.length > maxLength ? `${normalized.slice(0, maxLength)}...` : normalized
}

const extractChartTitleText = (titleValue: unknown): string => {
  if (typeof titleValue === 'string') {
    return titleValue.trim()
  }

  if (Array.isArray(titleValue)) {
    for (const item of titleValue) {
      const text = extractChartTitleText(item)
      if (text) {
        return text
      }
    }
    return ''
  }

  if (titleValue && typeof titleValue === 'object') {
    const titleRecord = titleValue as Record<string, unknown>
    const text = typeof titleRecord.text === 'string' ? titleRecord.text.trim() : ''
    const subtext = typeof titleRecord.subtext === 'string' ? titleRecord.subtext.trim() : ''
    return [text, subtext].filter(Boolean).join(' ')
  }

  return ''
}

const getChartSummaryText = (report: ReportRecord): string => {
  const option = getChartOption(report)
  if (!option) {
    return report.name || '图表报表'
  }

  const titleText = extractChartTitleText(option.title)
  if (titleText) {
    return summarizePreviewText(titleText)
  }

  const series = Array.isArray(option.series) ? option.series : []
  const seriesNames = series
    .map((item) =>
      item && typeof item === 'object' && typeof (item as Record<string, unknown>).name === 'string'
        ? ((item as Record<string, unknown>).name as string).trim()
        : ''
    )
    .filter(Boolean)

  if (seriesNames.length > 0) {
    return summarizePreviewText(`${getReportTypeLabel(report)}：${seriesNames.join('、')}`)
  }

  return summarizePreviewText(`${getReportTypeLabel(report)}：${report.name || '未命名图表'}`)
}

const getReportPreviewText = (report: ReportRecord): string => {
  const markdown = getReportMarkdown(report)
  if (markdown) {
    const plainText = markdown
      .replace(/!\[[^\]]*]\([^)]*\)/g, ' ')
      .replace(/\[(.*?)\]\([^)]*\)/g, '$1')
      .replace(/^#{1,6}\s+/gm, '')
      .replace(/[`>*_~-]/g, ' ')
      .replace(/\|/g, ' ')
      .replace(/\s+/g, ' ')
      .trim()

    if (plainText) {
      return plainText.length > 120 ? `${plainText.slice(0, 120)}...` : plainText
    }
  }

  if (report.query) {
    const sqlPreview = report.query.replace(/\s+/g, ' ').trim()
    return sqlPreview.length > 120 ? `${sqlPreview.slice(0, 120)}...` : sqlPreview
  }

  return '暂无摘要'
}

const getReportDisplaySummary = (report: ReportRecord): string => {
  if (!isMarkdownReport(report)) {
    return getChartSummaryText(report)
  }

  return getReportPreviewText(report)
}

const reportStats = computed(() => {
  const documentCount = reports.value.filter((item) => isMarkdownReport(item)).length
  return {
    documentCount,
    chartCount: reports.value.length - documentCount
  }
})

const filteredReports = computed(() => {
  const searchText = keyword.value.trim().toLowerCase()

  return reports.value.filter((report) => {
    if (typeFilter.value === 'report' && !isMarkdownReport(report)) {
      return false
    }

    if (typeFilter.value === 'chart' && isMarkdownReport(report)) {
      return false
    }

    if (!searchText) {
      return true
    }

    const haystack = [report.name, getReportDisplaySummary(report), report.query || '']
      .join(' ')
      .toLowerCase()

    return haystack.includes(searchText)
  })
})

const paginatedReports = computed(() => {
  const start = (page.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredReports.value.slice(start, end)
})

const columns = computed<DataTableColumns<ReportRecord>>(() => [
  {
    title: '报表名称',
    key: 'name',
    minWidth: 240,
    render: (row) => h('div', { class: 'report-cell-title' }, row.name)
  },
  {
    title: '类型',
    key: 'type',
    width: 120,
    render: (row) =>
      h(
        NTag,
        {
          size: 'small',
          type: isMarkdownReport(row) ? 'success' : 'warning'
        },
        () => getReportTypeLabel(row)
      )
  },
  {
    title: '摘要',
    key: 'summary',
    minWidth: 360,
    render: (row) => h('div', { class: 'report-cell-summary' }, getReportDisplaySummary(row))
  },
  {
    title: '创建时间',
    key: 'createdAt',
    width: 160,
    render: (row) => h('span', { class: 'report-time' }, formatTime(row.createdAt))
  },
  {
    title: '操作',
    key: 'actions',
    width: 260,
    render: (row) =>
      h(NSpace, { size: 'small' }, () => [
        h(
          NButton,
          { size: 'tiny', secondary: true, onClick: () => viewReport(row) },
          () => '查看'
        ),
        h(
          NButton,
          { size: 'tiny', disabled: !row.query, onClick: () => exportReportExcel(row) },
          () => 'Excel'
        ),
        h(
          NButton,
          { size: 'tiny', disabled: !canExportPdf(row), onClick: () => exportReportPdf(row) },
          () => 'PDF'
        ),
        h(
          NButton,
          { size: 'tiny', type: 'error', secondary: true, onClick: () => deleteReport(row.id) },
          () => '删除'
        )
      ])
  }
])

const openWorkflowPage = () => {
  router.push({
    name: 'Workflow',
    query: {
      scene: 'report',
      ...(latestWorkflowRunId.value ? { runId: latestWorkflowRunId.value } : {})
    }
  })
}

const loadReports = async () => {
  if (!appStore.currentConnectionId) {
    reports.value = []
    return
  }

  loading.value = true
  try {
    const res = await reportApi.list(appStore.currentConnectionId)
    reports.value = Array.isArray(res.data) ? res.data : []
  } catch {
    message.error('加载报表失败')
  } finally {
    loading.value = false
  }
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

const deleteReport = async (id: number) => {
  try {
    await reportApi.delete(id)
    message.success('报表已删除')
    await loadReports()
  } catch {
    message.error('删除报表失败')
  }
}

const viewReport = (report: ReportRecord) => {
  showPreviewModal.value = true
  previewSql.value = report.query || ''

  if (isMarkdownReport(report)) {
    previewMode.value = 'markdown'
    previewMarkdown.value = getReportMarkdown(report)
    previewOption.value = null
    return
  }

  previewMode.value = 'chart'
  previewOption.value = getChartOption(report) || null
  previewMarkdown.value = ''
}

const generateAiReport = async () => {
  if (!appStore.currentConnectionId) {
    message.warning('请先选择数据库连接')
    return
  }

  if (!aiRequirement.value.trim()) {
    message.warning('请输入报表需求')
    return
  }

  generatingReport.value = true
  try {
    const res = await reportApi.generateReport(appStore.currentConnectionId, aiRequirement.value)
    latestWorkflowRunId.value = res.data.workflowRunId || ''
    if (res.data.error) {
      message.error(res.data.message || '图表生成失败')
      return
    }

    showAiModal.value = false
    aiRequirement.value = ''
    const successMessage =
      res.data.savedToReportCenter && res.data.reportName
        ? res.data.artifactType === 'report'
          ? `文档报告已生成并保存：${res.data.reportName}`
          : `图表已生成并保存：${res.data.reportName}`
        : res.data.artifactType === 'report'
          ? '文档报告已生成'
          : '图表已生成'
    message.success(successMessage)
    await loadReports()
  } catch {
    message.error('AI 报表生成失败')
  } finally {
    generatingReport.value = false
  }
}

const exportReportExcel = async (report: ReportRecord) => {
  if (!appStore.currentConnectionId || !report.query) {
    return
  }

  try {
    const res = await reportApi.exportExcel(appStore.currentConnectionId, report.query, report.name)
    downloadBlob(res.data, `${report.name}.xlsx`)
    message.success('Excel 导出成功')
  } catch {
    message.error('Excel 导出失败')
  }
}

const downloadBlob = (blob: Blob, fileName: string) => {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  anchor.click()
  URL.revokeObjectURL(url)
}

const waitForChartPaint = async () => {
  await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()))
  await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()))
}

const exportChartReportPdf = async (report: ReportRecord, option: Record<string, any>) => {
  const width = 1280
  const height = 720
  const host = document.createElement('div')

  host.style.position = 'fixed'
  host.style.left = '-10000px'
  host.style.top = '0'
  host.style.width = `${width}px`
  host.style.height = `${height}px`
  host.style.opacity = '0'
  host.style.pointerEvents = 'none'
  host.style.background = '#ffffff'

  document.body.appendChild(host)

  let chart: echarts.ECharts | null = null

  try {
    chart = echarts.init(host, undefined, {
      renderer: 'canvas',
      width,
      height
    })

    chart.setOption(
      {
        backgroundColor: '#ffffff',
        color: option.color || CHART_EXPORT_PALETTE,
        animation: false,
        ...option
      },
      true
    )

    chart.resize({ width, height })
    await waitForChartPaint()

    const imageDataUrl = chart.getDataURL({
      type: 'png',
      pixelRatio: 2,
      backgroundColor: '#ffffff',
      excludeComponents: ['toolbox']
    })

    const { jsPDF } = await import('jspdf')
    const pdf = new jsPDF({
      orientation: 'landscape',
      unit: 'pt',
      format: 'a4',
      compress: true
    })

    const pageWidth = pdf.internal.pageSize.getWidth()
    const pageHeight = pdf.internal.pageSize.getHeight()
    const margin = 20
    const contentWidth = pageWidth - margin * 2
    const contentHeight = pageHeight - margin * 2
    const chartAspectRatio = width / height
    const contentAspectRatio = contentWidth / contentHeight

    const renderWidth =
      chartAspectRatio > contentAspectRatio ? contentWidth : contentHeight * chartAspectRatio
    const renderHeight =
      chartAspectRatio > contentAspectRatio ? contentWidth / chartAspectRatio : contentHeight
    const x = (pageWidth - renderWidth) / 2
    const y = (pageHeight - renderHeight) / 2

    pdf.addImage(imageDataUrl, 'PNG', x, y, renderWidth, renderHeight, undefined, 'FAST')
    downloadBlob(pdf.output('blob'), `${report.name}.pdf`)
  } finally {
    chart?.dispose()
    host.remove()
  }
}

const exportReportPdf = async (report: ReportRecord) => {
  try {
    const chartOption = getChartOption(report)

    if (!isMarkdownReport(report) && chartOption) {
      await exportChartReportPdf(report, chartOption)
      message.success('PDF 导出成功')
      return
    }

    if (!report.id) {
      return
    }

    const res = await reportApi.exportPdf({
      reportId: report.id,
      connectionId: appStore.currentConnectionId,
      sql: report.query,
      title: report.name
    })
    downloadBlob(res.data, `${report.name}.pdf`)
    message.success('PDF 导出成功')
  } catch {
    message.error('PDF 导出失败')
  }
}

watch([keyword, typeFilter], () => {
  page.value = 1
})

watch(pageSize, () => {
  page.value = 1
})

watch(
  () => appStore.currentConnectionId,
  () => {
    page.value = 1
    latestWorkflowRunId.value = ''
    loadReports()
  }
)

onMounted(() => {
  loadReports()
})
</script>

<style scoped>
.report-page {
  gap: 0;
}

.report-overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.report-stat-card {
  padding: 16px 18px;
  border-radius: 16px;
  background: var(--background-muted);
  border: 1px solid var(--line-soft);
}

.report-stat-card span {
  display: block;
  margin-bottom: 6px;
  color: var(--text-muted);
  font-size: 12px;
}

.report-stat-card strong {
  color: var(--text-color);
  font-family: var(--font-display);
  font-size: 24px;
  letter-spacing: -0.04em;
}

.report-toolbar {
  margin-bottom: 16px;
  padding: 0;
  background: var(--background-strong);
}

.report-toolbar-main {
  display: grid;
  grid-template-columns: minmax(0, 360px) 180px;
  gap: 12px;
  justify-content: start;
}

.report-toolbar-main :deep(.n-input),
.report-toolbar-main :deep(.n-base-selection) {
  --n-border-radius: 12px !important;
  border-radius: 12px;
}

.report-table-shell {
  overflow: hidden;
  border-radius: 16px;
  border: 1px solid var(--line-soft);
  background: var(--background-elevated);
}

.report-table-shell :deep(.n-data-table-th) {
  background: var(--surface-table-head);
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 500;
}

.report-table-shell :deep(.n-data-table-td) {
  color: var(--text-color);
  font-size: 13px;
  padding-top: 14px;
  padding-bottom: 14px;
}

.report-table-shell :deep(.n-data-table-tr:hover .n-data-table-td) {
  background: var(--surface-hover);
}

.report-pagination {
  margin-top: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.report-pagination-meta {
  color: var(--text-secondary);
  font-size: 13px;
}

.report-cell-title {
  color: var(--text-color);
  font-weight: 400;
  line-height: 1.5;
}

.report-cell-summary {
  display: -webkit-box;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.8;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.report-time {
  color: var(--text-secondary);
  font-size: 13px;
}

.report-detail {
  max-height: 65vh;
  overflow: auto;
  padding-right: 4px;
}

.report-preview-empty {
  padding: 48px 16px;
  text-align: center;
  color: var(--text-secondary);
  border-radius: 16px;
  background: var(--background-soft);
  border: 1px solid var(--line-soft);
}

.preview-sql {
  margin-top: 14px;
}

.preview-sql h4 {
  margin: 0 0 8px;
  font-family: var(--font-display);
  font-size: 20px;
  line-height: 1.2;
}

@media (max-width: 1100px) {
  .report-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 820px) {
  .report-overview,
  .report-toolbar-main {
    grid-template-columns: 1fr;
  }

  .report-pagination {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
