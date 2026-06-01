<template>
  <div class="chat-page">
    <div v-if="appStore.currentConnectionId" class="chat-workspace">
      <div
        ref="chatContainer"
        class="chat-messages"
        :class="{ 'is-empty': !hasVisibleMessages }"
      >
        <div v-if="!hasVisibleMessages" class="chat-empty-state">
          <p class="chat-empty-intro">我可以帮你完成这些数据分析任务</p>
          <div class="chat-empty-capabilities">
            <span class="chat-empty-pill">分析数据趋势</span>
            <span class="chat-empty-pill">生成图表</span>
            <span class="chat-empty-pill">输出分析报告</span>
            <span class="chat-empty-pill">检索知识库文档</span>
          </div>
        </div>

        <template v-else>
          <ChatMessage
            v-for="messageItem in renderedMessages"
            :key="messageItem.id"
            :msg="messageItem"
            :live="messageItem.live"
            :steps="messageItem.mergedSteps"
            :completed-steps="messageItem.completedSteps"
            @open-citation="openCitationPreview"
          />
        </template>
      </div>

      <div class="chat-composer">
        <div class="action-buttons">
          <n-button quaternary size="small" @click="openWorkflowPage">
            <template #icon><GitNetworkOutline /></template>
            查看执行链路
          </n-button>
          <n-button
            quaternary
            size="small"
            :loading="compressing"
            :disabled="!sessionId"
            @click="compressContext"
          >
            <template #icon><ContractOutline /></template>
            压缩上下文
          </n-button>
          <n-button quaternary size="small" :disabled="!sessionId" @click="clearChat">
            <template #icon><TrashOutline /></template>
            清空会话
          </n-button>
        </div>

        <n-input
          v-model:value="inputMessage"
          type="textarea"
          class="composer-textarea"
          placeholder="输入问题，例如：结合知识库和当前数据库，分析博客内容表现"
          :autosize="{ minRows: 3, maxRows: 8 }"
          :disabled="loading"
          @keydown="handleKeydown"
        />

        <div class="composer-actions">
          <span class="composer-hint">Enter 发送，Shift + Enter 换行</span>
          <n-button
            type="primary"
            size="large"
            :loading="loading"
            :disabled="!inputMessage.trim()"
            @click="sendMessage"
          >
            <template #icon><PaperPlaneOutline /></template>
            发送
          </n-button>
        </div>
      </div>
    </div>

    <div v-else class="chat-unbound-state">
      <h3>请先选择数据库连接</h3>
      <n-button type="primary" @click="$router.push('/connections')">前往连接管理</n-button>
    </div>

    <n-modal
      v-model:show="showKnowledgePreviewModal"
      preset="card"
      :title="knowledgePreviewModalTitle"
      :bordered="false"
      :segmented="{ content: true }"
      style="width: 1080px; max-width: 94vw;"
    >
      <div v-if="knowledgePreviewLoading" class="knowledge-modal-loading">
        <n-spin size="large" />
        <span>正在加载文档预览...</span>
      </div>

      <template v-else-if="knowledgePreviewData">
        <div class="knowledge-preview-meta">
          <span>{{ knowledgePreviewData.document.type.toUpperCase() }}</span>
          <span class="separator">/</span>
          <span>{{ knowledgeStatusLabel(knowledgePreviewData.document.status) }}</span>
          <span class="separator">/</span>
          <span>{{ knowledgePreviewData.chunks.length }} chunks</span>
        </div>

        <div v-if="knowledgePreviewData.document.errorMessage" class="detail-error">
          {{ knowledgePreviewData.document.errorMessage }}
        </div>

        <div v-if="knowledgePreviewData.document.status !== 'ready'" class="detail-empty">当前文档尚未就绪，请稍后再查看。</div>

        <div v-else-if="knowledgePreviewData.chunks.length" class="chunk-browser">
          <div class="chunk-nav">
            <div class="chunk-nav-head">
              <span>片段目录</span>
              <strong>{{ activeKnowledgeChunkOrderLabel }}</strong>
            </div>

            <div class="chunk-nav-list">
              <button
                v-for="chunk in knowledgePreviewData.chunks"
                :key="chunk.chunkIndex"
                type="button"
                class="chunk-nav-item"
                :class="{ active: currentKnowledgeChunk?.chunkIndex === chunk.chunkIndex }"
                @click="focusKnowledgeChunk(chunk.chunkIndex)"
              >
                <strong>片段 {{ chunk.chunkIndex + 1 }}</strong>
              </button>
            </div>
          </div>

          <div v-if="currentKnowledgeChunk" class="chunk-viewer">
            <div class="chunk-viewer-head">
              <div class="chunk-viewer-meta">
                <strong>片段 {{ currentKnowledgeChunk.chunkIndex + 1 }}</strong>
                <span>{{ knowledgeChunkMetaLabel(currentKnowledgeChunk) }}</span>
              </div>
              <div class="chunk-viewer-actions">
                <n-button
                  size="small"
                  quaternary
                  :disabled="activeKnowledgeChunkPosition <= 0"
                  @click="focusAdjacentKnowledgeChunk(-1)"
                >
                  上一个
                </n-button>
                <n-button
                  size="small"
                  quaternary
                  :disabled="activeKnowledgeChunkPosition >= knowledgePreviewData.chunks.length - 1"
                  @click="focusAdjacentKnowledgeChunk(1)"
                >
                  下一个
                </n-button>
              </div>
            </div>

            <div
              v-if="isMarkdownKnowledgeChunk(currentKnowledgeChunk)"
              class="chunk-viewer-body markdown-body"
              v-html="currentKnowledgeChunkHtml"
            ></div>
            <div v-else class="chunk-viewer-body">
              {{ currentKnowledgeChunk.content }}
            </div>
          </div>
        </div>

        <div v-else class="detail-empty">当前文档没有可预览内容</div>
      </template>

      <div v-else class="detail-empty">暂无可预览内容</div>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NInput, NModal, NSpin, useMessage } from 'naive-ui'
import { ContractOutline, GitNetworkOutline, PaperPlaneOutline, TrashOutline } from '@vicons/ionicons5'
import type {
  AgentEvent,
  AgentStep,
  KnowledgeCitation,
  KnowledgePreview,
  KnowledgePreviewChunk
} from '@/api'
import { chatApi, knowledgeApi } from '@/api'
import ChatMessage from '@/components/ChatMessage.vue'
import { useAppStore } from '@/stores/app'
import { renderMarkdownContent } from '@/utils/markdown'

interface ConversationMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  reasoning?: string
  reasoningEnabled?: boolean
  steps?: AgentStep[]
  citations?: KnowledgeCitation[]
}

interface MergedAgentStep {
  id: string
  name: string
  skill: string
  description: string
  status: AgentStep['status']
  kind: 'skill' | 'tool' | 'thinking' | 'finalizing'
  mergedCount: number
}

interface DisplayMessage extends ConversationMessage {
  live: boolean
  mergedSteps: MergedAgentStep[]
  completedSteps: number
}

const appStore = useAppStore()
const message = useMessage()
const router = useRouter()

const sessionId = ref('')
const messages = ref<ConversationMessage[]>([])
const currentAssistantMsg = ref<ConversationMessage | null>(null)
const inputMessage = ref('')
const loading = ref(false)
const compressing = ref(false)
const latestWorkflowRunId = ref('')
const chatContainer = ref<HTMLElement | null>(null)
const showKnowledgePreviewModal = ref(false)
const knowledgePreviewLoading = ref(false)
const knowledgePreviewData = ref<KnowledgePreview | null>(null)
const knowledgePreviewDocumentId = ref<number | null>(null)
const activeKnowledgeChunkIndex = ref<number | null>(null)

let messageIdSeed = 0
let historyAutoScrollTimer: number | null = null

const createMessageId = (prefix: string) => `${prefix}-${Date.now()}-${messageIdSeed++}`

const isStepFinished = (status: AgentStep['status']) =>
  status === 'COMPLETED' || status === 'FAILED' || status === 'SKIPPED'

const mergeStepStatus = (
  currentStatus: AgentStep['status'],
  nextStatus: AgentStep['status']
): AgentStep['status'] => {
  if (currentStatus === 'FAILED' || nextStatus === 'FAILED') {
    return 'FAILED'
  }

  if (currentStatus === 'RUNNING' || nextStatus === 'RUNNING') {
    return 'RUNNING'
  }

  if (currentStatus === 'PENDING' || nextStatus === 'PENDING') {
    return 'PENDING'
  }

  if (currentStatus === 'COMPLETED' || nextStatus === 'COMPLETED') {
    return 'COMPLETED'
  }

  return nextStatus
}

const resolveStepKind = (step: AgentStep): 'skill' | 'tool' | 'thinking' | 'finalizing' => {
  if (step.kind) {
    return step.kind
  }

  const fingerprint = `${step.skill || ''} ${step.name || ''}`.trim().toLowerCase()

  if (
    fingerprint.includes('skill') ||
    fingerprint.includes('技能') ||
    fingerprint.includes('read_skill') ||
    fingerprint.includes('readskill')
  ) {
    return 'skill'
  }

  return 'tool'
}

const cloneSteps = (steps?: AgentStep[]) => steps?.map((step) => ({ ...step }))
const cloneReasoning = (reasoning?: string) => reasoning || ''
const resolveReasoningEnabled = (messageItem: any) =>
  Boolean(messageItem?.reasoningEnabled ?? messageItem?.reasoning?.trim())
const cloneCitations = (citations?: KnowledgeCitation[]) =>
  citations?.map((citation) => ({ ...citation, metadata: citation.metadata ? { ...citation.metadata } : undefined }))

const mergeAdjacentSteps = (steps?: AgentStep[]): MergedAgentStep[] => {
  if (!steps?.length) {
    return []
  }

  const merged: MergedAgentStep[] = []

  for (const step of steps) {
    const stepName = step.name?.trim() || step.description?.trim() || '处理中'
    const stepSkill = step.skill?.trim() || stepName
    const stepDescription = step.description?.trim() || ''
    const stepKind = resolveStepKind(step)
    const lastStep = merged[merged.length - 1]

    if (lastStep && lastStep.name === stepName && lastStep.kind === stepKind) {
      lastStep.mergedCount += 1
      lastStep.status = mergeStepStatus(lastStep.status, step.status)
      continue
    }

    merged.push({
      id: step.id,
      name: stepName,
      skill: stepSkill,
      description: stepDescription,
      status: step.status,
      kind: stepKind,
      mergedCount: 1
    })
  }

  return merged
}

const countFinishedSteps = (steps: MergedAgentStep[]) =>
  steps.filter((step) => isStepFinished(step.status)).length

const toDisplayMessage = (
  messageItem: ConversationMessage,
  options: { live?: boolean } = {}
): DisplayMessage => {
  const mergedSteps = mergeAdjacentSteps(messageItem.steps)
  return {
    ...messageItem,
    live: Boolean(options.live),
    mergedSteps,
    completedSteps: countFinishedSteps(mergedSteps)
  }
}

const renderedMessages = computed<DisplayMessage[]>(() => {
  const historyMessages = messages.value.map((messageItem) => toDisplayMessage(messageItem))
  const liveMessage = currentAssistantMsg.value

  if (!liveMessage) {
    return historyMessages
  }

  const alreadyPersisted = messages.value.some((messageItem) => messageItem.id === liveMessage.id)
  if (alreadyPersisted) {
    return historyMessages
  }

  return [
    ...historyMessages,
    toDisplayMessage(liveMessage, {
      live: true
    })
  ]
})

const hasVisibleMessages = computed(() => renderedMessages.value.length > 0)

const currentKnowledgeChunk = computed(() => {
  const chunks = knowledgePreviewData.value?.chunks || []
  if (!chunks.length) {
    return null
  }

  return chunks.find((item) => item.chunkIndex === activeKnowledgeChunkIndex.value) || chunks[0]
})

const activeKnowledgeChunkPosition = computed(() => {
  const activeChunk = currentKnowledgeChunk.value
  const chunks = knowledgePreviewData.value?.chunks || []
  if (!activeChunk || !chunks.length) {
    return -1
  }

  return chunks.findIndex((item) => item.chunkIndex === activeChunk.chunkIndex)
})

const activeKnowledgeChunkOrderLabel = computed(() => {
  if (!knowledgePreviewData.value?.chunks?.length || activeKnowledgeChunkPosition.value < 0) {
    return '--'
  }

  return `${activeKnowledgeChunkPosition.value + 1} / ${knowledgePreviewData.value.chunks.length}`
})

const knowledgePreviewModalTitle = computed(
  () => knowledgePreviewData.value?.document?.name || '知识库片段'
)
const currentKnowledgeChunkHtml = computed(() =>
  renderMarkdownContent(currentKnowledgeChunk.value?.content || '')
)

const knowledgeStatusLabel = (status?: string) => {
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

const resolveKnowledgeChunkIndex = (
  preview: KnowledgePreview | null,
  chunkIndex?: number | null
) => {
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

const knowledgeChunkMetaLabel = (chunk?: KnowledgePreviewChunk | null) => {
  if (!chunk) {
    return '未标注位置'
  }
  if (chunk.metadata?.page) {
    return `第 ${chunk.metadata.page} 页`
  }
  if (chunk.metadata?.sectionTitle) {
    return String(chunk.metadata.sectionTitle)
  }
  return '未标注位置'
}

const isMarkdownKnowledgeChunk = (chunk?: KnowledgePreviewChunk | null) => {
  const sourceType = String(
    chunk?.metadata?.sourceType || knowledgePreviewData.value?.document?.type || ''
  ).toLowerCase()
  if (sourceType === 'md' || sourceType === 'markdown') {
    return true
  }

  const content = chunk?.content || ''
  return /(^|\n)\s*#{1,6}\s+\S|(^|\n)\s*\|.+\|\s*$|```/.test(content)
}

const loadKnowledgePreview = async (documentId: number, chunkIndex?: number | null) => {
  knowledgePreviewLoading.value = true
  knowledgePreviewData.value = null
  knowledgePreviewDocumentId.value = documentId
  activeKnowledgeChunkIndex.value = null

  try {
    const res = await knowledgeApi.preview(documentId)
    knowledgePreviewData.value = res.data
    activeKnowledgeChunkIndex.value = resolveKnowledgeChunkIndex(res.data, chunkIndex)
  } finally {
    knowledgePreviewLoading.value = false
  }
}

const openCitationPreview = async (citation: KnowledgeCitation) => {
  showKnowledgePreviewModal.value = true

  try {
    if (!knowledgePreviewData.value || knowledgePreviewDocumentId.value !== citation.documentId) {
      await loadKnowledgePreview(citation.documentId, citation.chunkIndex)
      return
    }

    activeKnowledgeChunkIndex.value = resolveKnowledgeChunkIndex(
      knowledgePreviewData.value,
      citation.chunkIndex
    )
  } catch (error: any) {
    knowledgePreviewData.value = null
    knowledgePreviewDocumentId.value = null
    activeKnowledgeChunkIndex.value = null
    showKnowledgePreviewModal.value = false
    message.error(error?.response?.data?.error || '加载知识库片段失败')
  }
}

const focusKnowledgeChunk = (chunkIndex: number) => {
  const resolved = resolveKnowledgeChunkIndex(knowledgePreviewData.value, chunkIndex)
  if (resolved === null || resolved === activeKnowledgeChunkIndex.value) {
    return
  }

  activeKnowledgeChunkIndex.value = resolved
}

const focusAdjacentKnowledgeChunk = (step: number) => {
  const chunks = knowledgePreviewData.value?.chunks || []
  if (!chunks.length) {
    return
  }

  const currentIndex = activeKnowledgeChunkPosition.value >= 0 ? activeKnowledgeChunkPosition.value : 0
  const nextIndex = currentIndex + step
  if (nextIndex < 0 || nextIndex >= chunks.length) {
    return
  }

  focusKnowledgeChunk(chunks[nextIndex].chunkIndex)
}

const resolveScrollContainers = () => {
  const containers: HTMLElement[] = []
  if (chatContainer.value) {
    containers.push(chatContainer.value)
    const workspaceBody = chatContainer.value.closest('.workspace-body')
    if (workspaceBody instanceof HTMLElement) {
      containers.push(workspaceBody)
    }
  }
  return containers
}

const scrollToBottom = (behavior: ScrollBehavior = 'smooth') => {
  nextTick(() => {
    resolveScrollContainers().forEach((container) => {
      container.scrollTo({
        top: container.scrollHeight,
        behavior
      })
    })
  })
}

const scrollHistoryToBottom = () => {
  nextTick(() => {
    const containers = resolveScrollContainers()
    if (!containers.length) {
      return
    }

    const applyScroll = () => {
      containers.forEach((container) => {
        container.scrollTop = container.scrollHeight
      })
    }

    applyScroll()
    window.requestAnimationFrame(applyScroll)

    if (historyAutoScrollTimer) {
      window.clearTimeout(historyAutoScrollTimer)
    }

    historyAutoScrollTimer = window.setTimeout(() => {
      applyScroll()
      historyAutoScrollTimer = null
    }, 120)
  })
}

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    sendMessage()
  }
}

const initSession = () => {
  if (historyAutoScrollTimer) {
    window.clearTimeout(historyAutoScrollTimer)
    historyAutoScrollTimer = null
  }
  sessionId.value = ''
  messages.value = []
  currentAssistantMsg.value = null
  latestWorkflowRunId.value = ''
}

const openWorkflowPage = () => {
  router.push({
    name: 'Workflow',
    query: {
      scene: 'chat',
      ...(latestWorkflowRunId.value ? { runId: latestWorkflowRunId.value } : {})
    }
  })
}

const syncSessionBinding = async () => {
  if (!appStore.currentConnectionId) {
    return
  }

  try {
    const res = await chatApi.getSessions(appStore.currentConnectionId)
    const sessions = res.data || []
    if (sessions.length > 0) {
      const latestSession = sessions.sort(
        (a: any, b: any) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
      )[0]
      sessionId.value = latestSession.id
    }
  } catch (error) {
    console.error('Failed to sync session id', error)
  }
}

const loadSessions = async () => {
  if (!appStore.currentConnectionId) {
    initSession()
    return
  }

  try {
    const res = await chatApi.getSessions(appStore.currentConnectionId)
    const sessions = res.data || []

    if (Array.isArray(sessions) && sessions.length > 0) {
      const latestSession = sessions.sort(
        (a: any, b: any) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
      )[0]
      sessionId.value = latestSession.id
      await loadSessionHistory(latestSession.id)
    } else {
      initSession()
    }
  } catch (error) {
    console.error('Failed to load sessions', error)
    initSession()
  }
}

const loadSessionHistory = async (targetSessionId: string) => {
  try {
    const res = await chatApi.getHistory(targetSessionId)
    if (Array.isArray(res.data)) {
      messages.value = res.data.map((msg: any, index: number) => ({
        id: msg.id || createMessageId(`history-${index}`),
        role: msg.role === 'user' ? 'user' : 'assistant',
        content: msg.content || msg.text || '',
        reasoning: msg.reasoning || '',
        reasoningEnabled: resolveReasoningEnabled(msg),
        steps: msg.steps || undefined,
        citations: msg.citations || undefined
      }))
      scrollHistoryToBottom()
    } else {
      initSession()
    }
  } catch (error) {
    console.error('Failed to load chat history', error)
    initSession()
  }
}

const finalizeAssistantMessage = () => {
  const assistantMessage = currentAssistantMsg.value
  if (!assistantMessage) {
    return
  }

  const hasContent = Boolean(assistantMessage.content.trim())
  const hasReasoning = Boolean(assistantMessage.reasoning?.trim())
  const hasSteps = Boolean(assistantMessage.steps?.length)
  const hasCitations = Boolean(assistantMessage.citations?.length)

  if (hasContent || hasReasoning || hasSteps || hasCitations) {
    messages.value.push({
      id: assistantMessage.id,
      role: 'assistant',
      content: assistantMessage.content,
      reasoning: cloneReasoning(assistantMessage.reasoning),
      reasoningEnabled: Boolean(assistantMessage.reasoningEnabled),
      steps: cloneSteps(assistantMessage.steps),
      citations: cloneCitations(assistantMessage.citations)
    })
  }

  currentAssistantMsg.value = null
}

const bindSessionIdFromEvent = (event: AgentEvent) => {
  if (event.data.sessionId && !sessionId.value) {
    sessionId.value = event.data.sessionId
  }
}

const processStreamLine = (line: string) => {
  const trimmed = line.trim()
  if (!trimmed.startsWith('data:')) {
    return
  }

  const jsonStr = trimmed.replace(/^data:\s*/, '')
  if (!jsonStr || jsonStr === '[DONE]') {
    return
  }

  try {
    const event = JSON.parse(jsonStr) as AgentEvent
    handleAgentEvent(event)
  } catch (error) {
    console.error('Failed to parse streaming event', trimmed, error)
  }
}

const sendMessage = async () => {
  if (!inputMessage.value.trim() || loading.value || !appStore.currentConnectionId) {
    return
  }

  const userMessage = inputMessage.value.trim()
  messages.value.push({
    id: createMessageId('user'),
    role: 'user',
    content: userMessage
  })
  inputMessage.value = ''
  loading.value = true
  currentAssistantMsg.value = {
    id: createMessageId('assistant'),
    role: 'assistant',
    content: '',
    reasoning: '',
    reasoningEnabled: false,
    steps: [],
    citations: []
  }
  scrollToBottom()

  try {
    const response = await chatApi.send(sessionId.value || null, appStore.currentConnectionId, userMessage)

    if (!response.ok || !response.body) {
      throw new Error('Request failed')
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
        processStreamLine(line)
      }
    }

    pendingBuffer += decoder.decode()
    if (pendingBuffer.trim()) {
      processStreamLine(pendingBuffer)
    }

    if (!sessionId.value) {
      void syncSessionBinding()
    }
  } catch (error) {
    console.error('Failed to send message', error)
    if (currentAssistantMsg.value) {
      currentAssistantMsg.value.content = '抱歉，这次请求失败了。'
    }
  } finally {
    loading.value = false
    finalizeAssistantMessage()
    scrollToBottom()
  }
}

const handleAgentEvent = (event: AgentEvent) => {
  if (!currentAssistantMsg.value) {
    return
  }

  switch (event.type) {
    case 'WORKFLOW_STARTED':
      bindSessionIdFromEvent(event)
      currentAssistantMsg.value.reasoningEnabled = Boolean(event.data.reasoningEnabled)
      if (event.data.runId) {
        latestWorkflowRunId.value = event.data.runId
      }
      break

    case 'WORKFLOW_COMPLETED':
      bindSessionIdFromEvent(event)
      if (event.data.runId) {
        latestWorkflowRunId.value = event.data.runId
      }
      break

    case 'STEP_STARTED': {
      const steps = currentAssistantMsg.value.steps || []
      const existing = steps.find((step) => step.id === event.data.stepId)
      const stepName =
        event.data.displayName?.trim() ||
        event.data.stepName?.trim() ||
        event.data.description?.trim() ||
        '处理中'
      const stepDescription = event.data.description || ''

      if (existing) {
        existing.name = stepName
        existing.description = stepDescription
        existing.kind = event.data.stepKind || existing.kind
        existing.status = 'RUNNING'
      } else {
        steps.push({
          id: event.data.stepId || `${Date.now()}-${steps.length}`,
          name: stepName,
          skill: event.data.stepName || stepName,
          description: stepDescription,
          kind: event.data.stepKind || 'tool',
          status: 'RUNNING'
        })
      }

      currentAssistantMsg.value.steps = steps
      scrollToBottom()
      break
    }

    case 'STEP_COMPLETED':
      currentAssistantMsg.value.steps?.forEach((step) => {
        if (step.id === event.data.stepId) {
          step.status = 'COMPLETED'
          step.result = event.data.result
        }
      })
      scrollToBottom()
      break

    case 'STEP_FAILED':
      currentAssistantMsg.value.steps?.forEach((step) => {
        if (step.id === event.data.stepId) {
          step.status = 'FAILED'
          step.result = event.data.error
        }
      })
      scrollToBottom()
      break

    case 'THINKING':
      currentAssistantMsg.value.reasoning = `${currentAssistantMsg.value.reasoning || ''}${event.data.token || ''}`
      scrollToBottom()
      break

    case 'ANSWER_DELTA':
      currentAssistantMsg.value.content += event.data.token || ''
      scrollToBottom()
      break
      // 兼容旧后端事件；中间推理内容不在前端展示。
      break

    case 'FINAL_RESPONSE':
      bindSessionIdFromEvent(event)
      currentAssistantMsg.value.content = event.data.content || currentAssistantMsg.value.content || ''
      currentAssistantMsg.value.reasoning = event.data.reasoning || currentAssistantMsg.value.reasoning || ''
      currentAssistantMsg.value.reasoningEnabled = Boolean(
        event.data.reasoningEnabled ?? currentAssistantMsg.value.reasoningEnabled
      )
      currentAssistantMsg.value.citations = event.data.citations || []
      if (event.data.workflowRunId || event.data.runId) {
        latestWorkflowRunId.value = event.data.workflowRunId || event.data.runId || ''
      }
      finalizeAssistantMessage()
      scrollToBottom()
      break

    case 'ERROR':
      currentAssistantMsg.value.content = event.data.message || '处理失败'
      scrollToBottom()
      break
  }
}

const compressContext = async () => {
  if (!sessionId.value) {
    message.info('请先开始会话')
    return
  }

  compressing.value = true
  try {
    const res = await chatApi.compress(sessionId.value)
    if (res.data.compressed) {
      await loadSessionHistory(sessionId.value)
      message.success(`已压缩：${res.data.beforeCount} -> ${res.data.afterCount}`)
    } else {
      message.info(res.data.message || '当前无需压缩')
    }
  } catch {
    message.error('压缩失败')
  } finally {
    compressing.value = false
  }
}

const clearChat = async () => {
  if (!sessionId.value) {
    message.info('当前没有可清空的会话')
    return
  }

  try {
    await chatApi.clearHistory(sessionId.value)
    initSession()
    message.success('会话已清空')
  } catch {
    message.error('清空失败')
  }
}

watch(
  () => appStore.currentConnectionId,
  (newConnectionId) => {
    showKnowledgePreviewModal.value = false
    knowledgePreviewLoading.value = false
    knowledgePreviewData.value = null
    knowledgePreviewDocumentId.value = null
    activeKnowledgeChunkIndex.value = null

    if (newConnectionId) {
      loadSessions()
    } else {
      initSession()
    }
  }
)

watch(
  () => hasVisibleMessages.value,
  (visible) => {
    if (visible && !loading.value && !currentAssistantMsg.value) {
      scrollHistoryToBottom()
    }
  }
)

onMounted(() => {
  if (appStore.currentConnectionId) {
    loadSessions()
  } else {
    initSession()
  }
})
</script>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  margin: -10px;
  height: calc(100% + 20px);
  min-height: calc(100% + 20px);
  overflow: hidden;
}

.chat-workspace,
.chat-unbound-state {
  flex: 1;
  height: 100%;
  min-height: 0;
  border-radius: 22px;
}

.chat-workspace {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--background-elevated);
  border: 1px solid var(--line-soft);
  box-shadow: var(--card-shadow);
}

.chat-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 30px 30px 16px;
  scroll-behavior: smooth;
}

.chat-messages.is-empty {
  display: flex;
  align-items: center;
  justify-content: center;
}

.chat-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  max-width: 640px;
  padding: 12px 24px;
  text-align: center;
}

.chat-empty-intro {
  margin: 0;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.01em;
}

.chat-empty-capabilities {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}

.chat-empty-pill {
  display: inline-flex;
  align-items: center;
  min-height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  background: var(--surface-active);
  border: 1px solid var(--border-accent-soft);
  color: var(--primary-color-strong);
  font-size: 13px;
  font-weight: 500;
}

.chat-composer {
  padding: 16px 22px 22px;
  background: var(--background-elevated);
  border-top: 1px solid var(--line-soft);
}

.action-buttons {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  margin-bottom: 12px;
}

.composer-textarea :deep(.n-input) {
  --n-color: var(--background-elevated) !important;
  --n-color-focus: rgba(255, 255, 255, 1) !important;
  --n-color-disabled: var(--surface-disabled) !important;
  --n-text-color: var(--text-color) !important;
  --n-placeholder-color: var(--text-muted) !important;
  --n-border: 1px solid var(--line-strong) !important;
  --n-border-focus: 1px solid var(--border-accent-strong) !important;
  --n-border-hover: 1px solid var(--border-accent) !important;
  --n-box-shadow-focus: none !important;
  border-radius: 16px !important;
}

.composer-textarea :deep(.n-input__textarea-el),
.composer-textarea :deep(.n-input__textarea-mirror),
.composer-textarea :deep(.n-input__placeholder) {
  box-sizing: border-box;
  padding: 14px 14px 14px 12px !important;
  font-size: 14px;
  line-height: 1.7 !important;
}

.composer-textarea :deep(.n-input__textarea-el) {
  color: var(--text-color);
  caret-color: var(--primary-color);
}

.composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
}

.composer-hint {
  color: var(--text-muted);
  font-size: 12px;
}

.chat-unbound-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 40px 24px;
  text-align: center;
  background: var(--background-elevated);
  border: 1px solid var(--line-soft);
  box-shadow: var(--card-shadow);
}

.chat-unbound-state h3 {
  margin: 0;
  color: var(--text-color);
  font-family: var(--font-display);
  font-size: 28px;
  letter-spacing: -0.04em;
}

.knowledge-modal-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 320px;
  color: var(--text-secondary);
  font-size: 13px;
}

.knowledge-preview-meta {
  margin-bottom: 14px;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.7;
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
  background: var(--surface-danger);
  font-size: 13px;
  line-height: 1.7;
}

.detail-empty {
  padding: 40px 18px;
  border-radius: 16px;
  border: 1px dashed var(--line-strong);
  background: var(--surface-subtle);
  color: var(--text-secondary);
  text-align: center;
}

.chunk-browser {
  display: grid;
  grid-template-columns: minmax(210px, 230px) minmax(0, 1fr);
  gap: 14px;
  min-height: 540px;
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
  background: var(--background-muted);
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.18s ease,
    background-color 0.18s ease,
    color 0.18s ease;
}

.chunk-nav-item:hover,
.chunk-nav-item.active {
  border-color: var(--border-accent);
  background: var(--surface-subtle);
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
  background: var(--background-muted);
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.9;
  white-space: pre-wrap;
}

.chunk-viewer-body.markdown-body {
  white-space: normal;
}

.chunk-viewer-body.markdown-body :deep(*:first-child) {
  margin-top: 0;
}

.chunk-viewer-body.markdown-body :deep(*:last-child) {
  margin-bottom: 0;
}

@media (max-width: 1180px) {
  .chunk-browser {
    grid-template-columns: 1fr;
    min-height: 0;
  }

  .chunk-nav-list,
  .chunk-viewer-body {
    max-height: 320px;
  }
}

@media (max-width: 900px) {
  .chat-page {
    height: calc(100% + 20px);
    min-height: calc(100% + 20px);
  }

  .chat-messages {
    padding: 22px 18px 14px;
  }

  .chat-composer {
    padding: 16px;
  }

  .chat-empty-state {
    gap: 12px;
    padding: 8px 12px;
  }

  .chat-empty-capabilities {
    gap: 8px;
  }

  .composer-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .chunk-viewer-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .chunk-viewer-actions {
    width: 100%;
  }
}
</style>
