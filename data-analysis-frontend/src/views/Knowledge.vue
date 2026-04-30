<template>
  <div class="workspace-page">
    <section v-if="appStore.currentConnectionId" class="page-section knowledge-admin">
      <div class="section-head">
        <div class="section-copy">
          <p class="section-note section-note-compact">
            当前连接：{{ appStore.currentConnectionName || `连接 #${appStore.currentConnectionId}` }}
          </p>
        </div>
        <div class="section-actions">
          <n-button type="primary" :loading="uploading" @click="openFilePicker">
            {{ uploading ? '上传中' : '上传文档' }}
          </n-button>
        </div>
      </div>

      <div class="knowledge-stage" :class="{ 'is-uploading': uploading }">
        <div v-if="uploading" class="knowledge-upload-mask">
          <n-spin size="large" />
          <strong>正在上传并解析文档</strong>
          <span>文档入库完成后会自动刷新列表。</span>
        </div>

        <div class="knowledge-summary">
          <div class="knowledge-stat">
            <span>文档总数</span>
            <strong>{{ documents.length }}</strong>
          </div>
          <div class="knowledge-stat">
            <span>可用文档</span>
            <strong>{{ readyCount }}</strong>
          </div>
          <div class="knowledge-stat">
            <span>异常文档</span>
            <strong>{{ errorCount }}</strong>
          </div>
          <div class="knowledge-stat">
            <span>总分块数</span>
            <strong>{{ totalChunks }}</strong>
          </div>
        </div>

        <input
          ref="fileInput"
          class="hidden-input"
          type="file"
          multiple
          accept=".pdf,.txt,.md,.markdown,.docx"
          @change="handleFileChange"
        />

        <div class="knowledge-search-bar">
          <n-input
            v-model:value="searchQuery"
            clearable
            placeholder="搜索知识库内容"
            @keydown.enter.prevent="runSearch"
          />
          <n-button :loading="searching" @click="runSearch">搜索</n-button>
          <n-button v-if="searchResult" quaternary @click="clearSearch">返回文档</n-button>
        </div>

        <div v-if="searchResult" class="detail-surface">
          <div class="panel-header">
            <div>
              <strong class="panel-title">搜索结果</strong>
              <div class="panel-description">{{ searchResult.summary || '暂无摘要' }}</div>
            </div>
          </div>

          <div v-if="searchResult.citations.length" class="result-list">
            <button
              v-for="citation in searchResult.citations"
              :key="`${citation.documentId}-${citation.chunkIndex}`"
              type="button"
              class="result-item"
              @click="openCitation(citation)"
            >
              <div class="result-item-top">
                <strong>{{ citation.documentName }}</strong>
                <span>
                  <template v-if="citation.metadata?.page">第 {{ citation.metadata.page }} 页</template>
                  <template v-else>片段 {{ citation.chunkIndex + 1 }}</template>
                </span>
              </div>
              <p>{{ citation.snippet }}</p>
            </button>
          </div>

          <div v-else class="detail-empty">没有找到相关片段</div>
        </div>

        <div class="knowledge-list-panel">
          <div class="knowledge-table-shell">
            <n-data-table
              :columns="columns"
              :data="documents"
              :bordered="false"
              :loading="loadingDocuments"
              :pagination="false"
              :row-key="(row) => row.id"
              :row-props="tableRowProps"
              :row-class-name="rowClassName"
              :scroll-x="920"
              max-height="680"
              striped
              size="small"
            />
          </div>
        </div>
      </div>
    </section>

    <section v-else class="page-section">
      <div class="detail-empty">请先选择数据库连接，再使用知识库。</div>
    </section>

    <n-modal
      v-model:show="showPreviewModal"
      preset="card"
      :title="previewModalTitle"
      :bordered="false"
      :segmented="{ content: true }"
      style="width: 1080px; max-width: 94vw;"
    >
      <template v-if="previewData">
        <div class="knowledge-preview-meta">
          <span>{{ previewData.document.type.toUpperCase() }}</span>
          <span class="separator">/</span>
          <span>{{ statusLabel(previewData.document.status) }}</span>
          <span class="separator">/</span>
          <span>{{ previewData.chunks.length }} chunks</span>
        </div>

        <div v-if="previewData.document.errorMessage" class="detail-error">
          {{ previewData.document.errorMessage }}
        </div>

        <div v-if="previewData.document.status !== 'ready'" class="detail-empty">当前文档尚未就绪，请稍后再查看。</div>


        <div v-else-if="previewData.chunks.length" class="chunk-browser">
          <div class="chunk-nav">
            <div class="chunk-nav-head">
              <span>片段目录</span>
              <strong>{{ activeChunkOrderLabel }}</strong>
            </div>

            <div class="chunk-nav-list">
              <button
                v-for="chunk in previewData.chunks"
                :key="chunk.chunkIndex"
                type="button"
                class="chunk-nav-item"
                :class="{ active: currentChunk?.chunkIndex === chunk.chunkIndex }"
                @click="focusChunk(chunk.chunkIndex)"
              >
                <strong>片段 {{ chunk.chunkIndex + 1 }}</strong>
              </button>
            </div>
          </div>

          <div v-if="currentChunk" class="chunk-viewer">
            <div class="chunk-viewer-head">
              <div class="chunk-viewer-meta">
                <strong>片段 {{ currentChunk.chunkIndex + 1 }}</strong>
                <span>{{ chunkMetaLabel(currentChunk) }}</span>
              </div>
              <div class="chunk-viewer-actions">
                <n-button
                  size="small"
                  quaternary
                  :disabled="activeChunkPosition <= 0"
                  @click="focusAdjacentChunk(-1)"
                >
                  上一个</n-button>
                <n-button
                  size="small"
                  quaternary
                  :disabled="activeChunkPosition >= previewData.chunks.length - 1"
                  @click="focusAdjacentChunk(1)"
                >
                  下一个</n-button>
              </div>
            </div>

            <div class="chunk-viewer-body">
              {{ currentChunk.content }}
            </div>
          </div>
        </div>

        <div v-else class="detail-empty">当前文档没有可预览内容</div>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { DataTableColumns } from 'naive-ui'
import {
  NButton,
  NDataTable,
  NInput,
  NModal,
  NSpace,
  NSpin,
  NTag,
  useDialog,
  useMessage
} from 'naive-ui'
import type {
  KnowledgeCitation,
  KnowledgeDocument,
  KnowledgePreview,
  KnowledgeSearchResponse
} from '@/api'
import { knowledgeApi } from '@/api'
import { useAppStore } from '@/stores/app'

type KnowledgePreviewChunk = KnowledgePreview['chunks'][number]

const appStore = useAppStore()
const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()

const fileInput = ref<HTMLInputElement | null>(null)
const documents = ref<KnowledgeDocument[]>([])
const previewData = ref<KnowledgePreview | null>(null)
const searchResult = ref<KnowledgeSearchResponse | null>(null)
const searchQuery = ref('')
const selectedDocumentId = ref<number | null>(null)
const activeChunkIndex = ref<number | null>(null)
const loadingDocuments = ref(false)
const uploading = ref(false)
const searching = ref(false)
const showPreviewModal = ref(false)

const readyCount = computed(() => documents.value.filter((item) => item.status === 'ready').length)
const errorCount = computed(() => documents.value.filter((item) => item.status === 'error').length)
const totalChunks = computed(() => documents.value.reduce((sum, item) => sum + (item.totalChunks || 0), 0))

const currentChunk = computed(() => {
  const chunks = previewData.value?.chunks || []
  if (!chunks.length) {
    return null
  }
  return chunks.find((item) => item.chunkIndex === activeChunkIndex.value) || chunks[0]
})

const activeChunkPosition = computed(() => {
  const activeChunk = currentChunk.value
  const chunks = previewData.value?.chunks || []
  if (!activeChunk || !chunks.length) {
    return -1
  }
  return chunks.findIndex((item) => item.chunkIndex === activeChunk.chunkIndex)
})

const activeChunkOrderLabel = computed(() => {
  if (!previewData.value?.chunks?.length || activeChunkPosition.value < 0) {
    return '--'
  }
  return `${activeChunkPosition.value + 1} / ${previewData.value.chunks.length}`
})

const previewModalTitle = computed(() => previewData.value?.document?.name || '文档预览')

const statusLabel = (status?: string) => {
  switch (status) {
    case 'ready':
      return '已就绪'
    case 'embedding':
      return '处理中'
    case 'error':
      return '失败'
    default:
      return '待处理'
  }
}

const statusType = (status?: string) => {
  switch (status) {
    case 'ready':
      return 'success'
    case 'embedding':
      return 'warning'
    case 'error':
      return 'error'
    default:
      return 'default'
  }
}

const parseNumberQuery = (value: unknown) => {
  const source = Array.isArray(value) ? value[0] : value
  const parsed = Number(source)
  return Number.isFinite(parsed) ? parsed : null
}

const resolveChunkIndex = (preview: KnowledgePreview | null, chunkIndex?: number | null) => {
  const chunks = preview?.chunks || []
  if (!preview || preview.document.status !== 'ready' || !chunks.length) {
    return null
  }

  if (chunkIndex !== null && chunkIndex !== undefined) {
    const matched = chunks.find((item) => item.chunkIndex === chunkIndex)
    if (matched) {
      return matched.chunkIndex
    }
  }

  return chunks[0].chunkIndex
}

const syncKnowledgeRoute = async (documentId: number | null, chunkIndex?: number | null) => {
  await router.replace({
    path: '/knowledge',
    query: {
      ...(documentId ? { documentId: String(documentId) } : {}),
      ...(chunkIndex !== null && chunkIndex !== undefined ? { chunkIndex: String(chunkIndex) } : {})
    }
  })
}

const chunkMetaLabel = (chunk?: KnowledgePreviewChunk | null) => {
  if (!chunk) {
    return '未标注位置'
  }
  if (chunk.metadata?.page) {
    return `第 ${chunk.metadata.page} 页`
  }
  if (chunk.metadata?.sectionTitle) {
    return chunk.metadata.sectionTitle
  }
  return '未标注位置'
}

const loadPreview = async (documentId: number, chunkIndex?: number | null) => {
  const res = await knowledgeApi.preview(documentId)
  previewData.value = res.data
  selectedDocumentId.value = documentId
  activeChunkIndex.value = resolveChunkIndex(res.data, chunkIndex)
}

const clearKnowledgeRoute = async () => {
  if (!route.query.documentId && !route.query.chunkIndex) {
    return
  }

  await router.replace({
    path: '/knowledge',
    query: {}
  })
}

const selectDocument = async (documentId: number, chunkIndex?: number | null) => {
  searchResult.value = null
  try {
    await loadPreview(documentId, chunkIndex)
  } catch (error: any) {
    message.error(error?.response?.data?.error || '加载预览失败')
  }
}

const openPreviewModal = async (documentId?: number | null, chunkIndex?: number | null) => {
  const targetDocumentId = documentId ?? selectedDocumentId.value
  if (!targetDocumentId) {
    return
  }

  try {
    if (!previewData.value || selectedDocumentId.value !== targetDocumentId) {
      await loadPreview(targetDocumentId, chunkIndex)
    } else {
      const resolved = resolveChunkIndex(previewData.value, chunkIndex)
      if (resolved !== activeChunkIndex.value) {
        activeChunkIndex.value = resolved
      }
    }

    await syncKnowledgeRoute(targetDocumentId, activeChunkIndex.value)
    showPreviewModal.value = true
  } catch (error: any) {
    message.error(error?.response?.data?.error || '加载预览失败')
  }
}

const openCitation = async (citation: KnowledgeCitation) => {
  await openPreviewModal(citation.documentId, citation.chunkIndex)
}

const openFilePicker = () => {
  if (uploading.value) {
    return
  }
  fileInput.value?.click()
}

const handleFileChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  if (!files.length || !appStore.currentConnectionId) {
    return
  }

  const formData = new FormData()
  formData.append('connectionId', String(appStore.currentConnectionId))
  files.forEach((file) => formData.append('files', file))

  uploading.value = true
  try {
    const res = await knowledgeApi.upload(formData)
    const created = res.data || []
    message.success(`已上传 ${created.length} 份文档`)
    await loadDocuments()
    if (created[0]?.id) {
      await selectDocument(created[0].id)
    }
  } catch (error: any) {
    message.error(error?.response?.data?.error || '上传失败')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

const loadDocuments = async () => {
  if (!appStore.currentConnectionId) {
    documents.value = []
    previewData.value = null
    showPreviewModal.value = false
    return
  }

  loadingDocuments.value = true
  try {
    const res = await knowledgeApi.list(appStore.currentConnectionId)
    documents.value = res.data || []
    await applyRouteSelection()
  } catch (error) {
    console.error('Failed to load knowledge documents', error)
    message.error('加载文档失败')
  } finally {
    loadingDocuments.value = false
  }
}

const downloadDocument = async (document: KnowledgeDocument) => {
  try {
    const res = await knowledgeApi.download(document.id)
    const blob = new Blob([res.data], {
      type: res.headers['content-type'] || 'application/octet-stream'
    })
    const url = URL.createObjectURL(blob)
    const link = window.document.createElement('a')
    link.href = url
    link.download = document.name
    link.click()
    URL.revokeObjectURL(url)
  } catch (error: any) {
    message.error(error?.response?.data?.error || '下载失败')
  }
}

const removeDocument = async (documentId: number) => {
  dialog.warning({
    title: '删除文档',
    content: '删除后会同时移除分块、预览和搜索结果。',
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await knowledgeApi.delete(documentId)
        if (selectedDocumentId.value === documentId) {
          previewData.value = null
          selectedDocumentId.value = null
          activeChunkIndex.value = null
          showPreviewModal.value = false
        }
        if (Number(route.query.documentId) === documentId) {
          await router.replace({ path: '/knowledge', query: {} })
        }
        await loadDocuments()
        message.success('文档已删除')
      } catch (error: any) {
        message.error(error?.response?.data?.error || '删除失败')
      }
    }
  })
}

const reindexDocument = async (documentId: number) => {
  try {
    await knowledgeApi.reindex(documentId)
    message.success('已重新开始索引')
    await loadDocuments()
    if (selectedDocumentId.value === documentId) {
      await selectDocument(documentId, activeChunkIndex.value)
    }
  } catch (error: any) {
    message.error(error?.response?.data?.error || '重建索引失败')
  }
}

const runSearch = async () => {
  if (!appStore.currentConnectionId || !searchQuery.value.trim()) {
    return
  }

  searching.value = true
  try {
    const res = await knowledgeApi.search({
      connectionId: appStore.currentConnectionId,
      query: searchQuery.value.trim()
    })
    searchResult.value = res.data
  } catch (error: any) {
    message.error(error?.response?.data?.error || '搜索失败')
  } finally {
    searching.value = false
  }
}

const clearSearch = () => {
  searchQuery.value = ''
  searchResult.value = null
}

const focusChunk = async (chunkIndex: number) => {
  const resolved = resolveChunkIndex(previewData.value, chunkIndex)
  if (resolved === null || resolved === activeChunkIndex.value || !selectedDocumentId.value) {
    return
  }

  activeChunkIndex.value = resolved
  await syncKnowledgeRoute(selectedDocumentId.value, resolved)
}

const focusAdjacentChunk = async (step: number) => {
  const chunks = previewData.value?.chunks || []
  if (!chunks.length) {
    return
  }

  const currentIndex = activeChunkPosition.value >= 0 ? activeChunkPosition.value : 0
  const nextIndex = currentIndex + step
  if (nextIndex < 0 || nextIndex >= chunks.length) {
    return
  }

  await focusChunk(chunks[nextIndex].chunkIndex)
}

const applyRouteSelection = async () => {
  const routeDocumentId = parseNumberQuery(route.query.documentId)
  const routeChunkIndex = parseNumberQuery(route.query.chunkIndex)

  if (routeDocumentId) {
    await loadPreview(routeDocumentId, routeChunkIndex)
    showPreviewModal.value = true
    return
  }

  if (selectedDocumentId.value) {
    const exists = documents.value.find((item) => item.id === selectedDocumentId.value)
    if (!exists) {
      previewData.value = null
      selectedDocumentId.value = null
      activeChunkIndex.value = null
    }
  }
}

const tableRowProps = (row: KnowledgeDocument) => ({
  onClick: () => {
    selectDocument(row.id)
  }
})

const rowClassName = (row: KnowledgeDocument) => {
  return row.id === selectedDocumentId.value ? 'knowledge-row-selected' : ''
}

const columns = computed<DataTableColumns<KnowledgeDocument>>(() => [
  {
    title: '文档名称',
    key: 'name',
    minWidth: 240,
    render: (row) =>
      h('div', { class: 'table-name-cell' }, [h('span', { class: 'table-name-text' }, row.name)])
  },
  {
    title: '状态',
    key: 'status',
    width: 100,
    render: (row) =>
      h(
        NTag,
        { size: 'small', bordered: false, type: statusType(row.status) },
        () => statusLabel(row.status)
      )
  },
  {
    title: '分块数',
    key: 'totalChunks',
    width: 90
  },
  {
    title: '操作',
    key: 'actions',
    width: 220,
    render: (row) =>
      h(NSpace, { size: 'small' }, () => [
        h(
          NButton,
          {
            size: 'tiny',
            quaternary: true,
            onClick: (event: MouseEvent) => {
              event.stopPropagation()
              openPreviewModal(row.id)
            }
          },
          () => '预览'
        ),
        h(
          NButton,
          {
            size: 'tiny',
            quaternary: true,
            onClick: (event: MouseEvent) => {
              event.stopPropagation()
              downloadDocument(row)
            }
          },
          () => '下载'
        ),
        row.status === 'error'
          ? h(
              NButton,
              {
                size: 'tiny',
                quaternary: true,
                onClick: (event: MouseEvent) => {
                  event.stopPropagation()
                  reindexDocument(row.id)
                }
              },
              () => '重试'
            )
          : null,
        h(
          NButton,
          {
            size: 'tiny',
            quaternary: true,
            type: 'error',
            onClick: (event: MouseEvent) => {
              event.stopPropagation()
              removeDocument(row.id)
            }
          },
          () => '删除'
        )
      ].filter(Boolean))
  }
])

watch(
  () => appStore.currentConnectionId,
  () => {
    searchResult.value = null
    previewData.value = null
    selectedDocumentId.value = null
    activeChunkIndex.value = null
    showPreviewModal.value = false
    loadDocuments()
  }
)

watch(
  () => [route.query.documentId, route.query.chunkIndex],
  async () => {
    if (!appStore.currentConnectionId) {
      return
    }

    const routeDocumentId = parseNumberQuery(route.query.documentId)
    if (!routeDocumentId) {
      if (showPreviewModal.value) {
        showPreviewModal.value = false
      }
      return
    }

    if (selectedDocumentId.value !== routeDocumentId) {
      await loadPreview(routeDocumentId, parseNumberQuery(route.query.chunkIndex))
      showPreviewModal.value = true
      return
    }

    const routeChunkIndex = parseNumberQuery(route.query.chunkIndex)
    const resolvedChunkIndex = resolveChunkIndex(previewData.value, routeChunkIndex)
    if (resolvedChunkIndex !== activeChunkIndex.value) {
      activeChunkIndex.value = resolvedChunkIndex
    }
    showPreviewModal.value = true
  }
)

watch(
  showPreviewModal,
  async (visible) => {
    if (!visible) {
      await clearKnowledgeRoute()
    }
  }
)

onMounted(() => {
  loadDocuments()
})
</script>

<style scoped>
.knowledge-admin {
  padding-bottom: 18px;
}

.knowledge-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.knowledge-stage {
  position: relative;
}

.knowledge-stage.is-uploading {
  pointer-events: none;
}

.knowledge-upload-mask {
  position: absolute;
  inset: 0;
  z-index: 6;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(3px);
}

.knowledge-upload-mask strong {
  color: var(--text-color);
  font-size: 15px;
  font-weight: 700;
}

.knowledge-upload-mask span {
  color: var(--text-secondary);
  font-size: 13px;
}

.knowledge-stat {
  padding: 16px 18px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.98);
  border: 1px solid var(--line-soft);
}

.knowledge-stat span {
  display: block;
  margin-bottom: 6px;
  color: var(--text-muted);
  font-size: 12px;
}

.knowledge-stat strong {
  color: var(--text-color);
  font-family: var(--font-display);
  font-size: 24px;
  letter-spacing: -0.04em;
}

.knowledge-search-bar {
  display: grid;
  grid-template-columns: minmax(0, 360px) auto auto;
  gap: 10px;
  margin-bottom: 12px;
  justify-content: start;
}

.knowledge-search-bar :deep(.n-input) {
  --n-border-radius: 8px !important;
  border-radius: 8px;
}

.knowledge-preview-meta {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.knowledge-list-panel {
  min-width: 0;
}

.knowledge-table-shell,
.detail-surface {
  overflow: hidden;
  border-radius: 16px;
  border: 1px solid var(--line-soft);
  background: rgba(255, 255, 255, 0.98);
}

.detail-surface {
  margin-bottom: 16px;
  padding: 16px;
}

.knowledge-table-shell :deep(.n-data-table-th) {
  background: rgba(250, 250, 250, 0.96);
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 500;
}

.knowledge-table-shell :deep(.n-data-table-td) {
  color: var(--text-color);
  font-size: 13px;
  padding-top: 14px;
  padding-bottom: 14px;
}

.knowledge-table-shell :deep(.n-data-table-tr:hover .n-data-table-td) {
  background: rgba(115, 77, 57, 0.04);
}

.knowledge-table-shell :deep(.knowledge-row-selected td) {
  background: rgba(115, 77, 57, 0.05) !important;
}

.table-name-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.table-name-text {
  color: var(--text-color);
  font-size: 14px;
  line-height: 1.5;
}

.table-name-cell span {
  color: var(--text-muted);
  font-size: 12px;
}

.separator {
  display: inline-block;
  margin: 0 8px;
  color: var(--text-muted);
}

.detail-error {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  color: var(--error-color);
  background: rgba(200, 85, 71, 0.08);
  font-size: 13px;
  line-height: 1.7;
}

.result-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.result-item {
  width: 100%;
  padding: 14px;
  border-radius: 14px;
  border: 1px solid var(--line-soft);
  background: rgba(250, 250, 250, 0.78);
  text-align: left;
  cursor: pointer;
}

.result-item-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.result-item-top span {
  color: var(--text-muted);
  font-size: 12px;
}

.result-item strong {
  color: var(--text-color);
  font-size: 14px;
}

.result-item p {
  margin: 10px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
}

.chunk-browser {
  display: grid;
  grid-template-columns: minmax(220px, 240px) minmax(0, 1fr);
  gap: 14px;
  min-height: 560px;
}

.chunk-nav,
.chunk-viewer {
  min-width: 0;
}

.chunk-nav-head,
.chunk-viewer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.chunk-nav-head span,
.chunk-viewer-meta span {
  color: var(--text-muted);
  font-size: 12px;
}

.chunk-nav-head strong,
.chunk-viewer-meta strong {
  color: var(--text-color);
  font-size: 14px;
}

.chunk-nav-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 520px;
  overflow: auto;
  padding-right: 4px;
}

.chunk-nav-item {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  background: rgba(250, 250, 250, 0.72);
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.18s ease,
    background-color 0.18s ease;
}

.chunk-nav-item:hover,
.chunk-nav-item.active {
  border-color: rgba(239, 91, 42, 0.18);
  background: rgba(255, 248, 244, 0.92);
}

.chunk-nav-item strong {
  color: var(--text-color);
  font-size: 13px;
  font-weight: 600;
}

.chunk-viewer-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.chunk-viewer-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.chunk-viewer-body {
  max-height: 520px;
  overflow: auto;
  padding: 18px;
  border-radius: 16px;
  border: 1px solid var(--line-soft);
  background: rgba(250, 250, 250, 0.88);
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.9;
  white-space: pre-wrap;
}

.detail-empty {
  padding: 48px 18px;
  border-radius: 16px;
  border: 1px dashed var(--line-strong);
  background: rgba(255, 250, 247, 0.9);
  color: var(--text-secondary);
  text-align: center;
}

.hidden-input {
  display: none;
}

@media (max-width: 1180px) {
  .knowledge-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .chunk-browser {
    grid-template-columns: 1fr;
    min-height: 0;
  }

  .chunk-nav-list,
  .chunk-viewer-body {
    max-height: 320px;
  }
}

@media (max-width: 820px) {
  .knowledge-search-bar,
  .knowledge-summary {
    grid-template-columns: 1fr;
  }

  .result-item-top,
  .chunk-viewer-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .chunk-viewer-actions {
    width: 100%;
  }
}
</style>

