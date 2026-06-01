<template>
  <div :class="['chat-message', msg.role, { live }]">
    <div class="message-meta">
      <span class="message-avatar">{{ msg.role === 'user' ? '我' : 'AI' }}</span>
      <span class="message-label">{{ msg.role === 'user' ? '我的问题' : 'DataMind AI' }}</span>
    </div>

    <div v-if="showThoughtPanel" class="thought-panel">
      <button type="button" class="thought-toggle" @click="toggleThoughtExpanded">
        <span class="thought-toggle-main">
          <span class="thought-title">思考过程</span>
          <span class="thought-summary" :class="{ 'is-waiting': isAwaitingFirstSignal }">
            {{ thoughtSummary }}
          </span>
        </span>
        <n-icon class="thought-arrow" :class="{ expanded: thoughtExpanded }">
          <ChevronDownOutline />
        </n-icon>
      </button>

      <div v-if="showThoughtBody" class="thought-body">
        <div v-if="timelineItems.length" class="thought-section">
          <div class="thought-section-head">
            <span>处理轨迹</span>
          </div>

          <div class="timeline-list">
            <div
              v-for="item in timelineItems"
              :key="item.key"
              class="timeline-item"
              :class="`status-${item.status.toLowerCase()}`"
            >
              <div class="timeline-indicator">
                <n-icon v-if="item.status === 'RUNNING'" class="spin"><RefreshOutline /></n-icon>
                <n-icon v-else-if="item.status === 'COMPLETED'" class="success"><CheckmarkCircleOutline /></n-icon>
                <n-icon v-else-if="item.status === 'FAILED'" class="error"><CloseCircleOutline /></n-icon>
                <n-icon v-else><TimeOutline /></n-icon>
              </div>

              <div class="timeline-main">
                <div class="timeline-title-row">
                  <span class="timeline-title">{{ item.label }}</span>
                  <span v-if="item.count > 1" class="timeline-count">x{{ item.count }}</span>
                </div>
                <span v-if="item.description" class="timeline-desc">{{ item.description }}</span>
              </div>
            </div>
          </div>
        </div>

        <div v-if="showReasoningBlock" class="thought-section">
          <div class="thought-section-head">
            <span>详细思考</span>
            <span v-if="!msg.reasoningEnabled" class="thought-muted">已关闭</span>
          </div>

          <div v-if="formattedReasoning" class="reasoning-card" v-html="formattedReasoning"></div>
        </div>
      </div>
    </div>

    <div v-if="showMessageBubble" class="message-bubble markdown-body">
      <div v-if="formattedContent" v-html="formattedContent"></div>

      <div v-if="showCitations" class="message-sources">
        <span class="sources-label">来源</span>
        <div class="sources-links">
          <button
            v-for="citation in msg.citations"
            :key="`${citation.documentId}-${citation.chunkIndex}`"
            type="button"
            class="source-link"
            :title="`${citation.documentName} - 片段 ${citation.chunkIndex + 1}`"
            @click="openCitation(citation)"
          >
            <span class="source-name">{{ citation.documentName }}</span>
            <span class="source-meta">
              <template v-if="citation.metadata?.page">第 {{ citation.metadata.page }} 页</template>
              <template v-else>片段 {{ citation.chunkIndex + 1 }}</template>
            </span>
          </button>
        </div>
      </div>

      <span v-if="showCursor" class="cursor-blink">|</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { NIcon } from 'naive-ui'
import {
  ChevronDownOutline,
  CheckmarkCircleOutline,
  CloseCircleOutline,
  RefreshOutline,
  TimeOutline
} from '@vicons/ionicons5'
import type { KnowledgeCitation } from '@/api'
import { renderMarkdownContent } from '@/utils/markdown'

interface ChatProgressStep {
  id: string
  name: string
  skill?: string
  description?: string
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'SKIPPED'
  mergedCount: number
  kind?: 'skill' | 'tool' | 'thinking' | 'finalizing'
}

const props = withDefaults(
  defineProps<{
    msg: {
      role: string
      content: string
      reasoning?: string
      reasoningEnabled?: boolean
      citations?: KnowledgeCitation[]
    }
    live?: boolean
    steps?: ChatProgressStep[]
    completedSteps?: number
  }>(),
  {
    live: false,
    steps: () => [],
    completedSteps: 0
  }
)

const emit = defineEmits<{
  (event: 'open-citation', citation: KnowledgeCitation): void
}>()

const thoughtExpanded = ref(false)
const thoughtInteracted = ref(false)

const formattedContent = computed(() => renderMarkdownContent(props.msg.content || ''))
const formattedReasoning = computed(() => renderMarkdownContent(props.msg.reasoning || ''))

const normalizeLabel = (label: string, kind?: 'skill' | 'tool' | 'thinking' | 'finalizing') => {
  const value = label.trim()
  if (!value) {
    return kind === 'skill' ? '调用技能' : '处理步骤'
  }
  if (kind === 'skill') {
    return value.startsWith('调用') ? value : `调用${value}`
  }
  return value
}

const timelineItems = computed(() =>
  (props.steps || []).map((step) => ({
    key: step.id,
    label: normalizeLabel(step.name || step.skill || '处理中', step.kind),
    description: step.description || '',
    status: step.status,
    count: step.mergedCount,
    kind: step.kind
  }))
)

const toolCount = computed(() => timelineItems.value.filter((item) => item.kind === 'tool').length)
const skillCount = computed(() => timelineItems.value.filter((item) => item.kind === 'skill').length)
const activeThinkingItem = computed(() => timelineItems.value.find((item) => item.kind === 'thinking' && item.status === 'RUNNING'))
const activeFinalizingItem = computed(() =>
  timelineItems.value.find((item) => item.kind === 'finalizing' && item.status === 'RUNNING')
)
const hasThoughtDetails = computed(
  () => timelineItems.value.length > 0 || Boolean(props.msg.reasoning?.trim())
)
const isAwaitingFirstSignal = computed(() => props.live && !hasThoughtDetails.value)
const showThoughtBody = computed(() => thoughtExpanded.value && hasThoughtDetails.value)

const thoughtSummary = computed(() => {
  if (!timelineItems.value.length) {
    return props.live ? '思考中' : '已完成'
  }

  if (activeFinalizingItem.value) {
    return '正在整理回答'
  }

  if (activeThinkingItem.value) {
    return toolCount.value || skillCount.value
      ? `思考中 · 已调用 ${skillCount.value} 个技能、${toolCount.value} 个工具`
      : '思考中'
  }

  const runningItem = timelineItems.value.find((item) => item.status === 'RUNNING')
  if (runningItem) {
    return `${runningItem.label} · 已调用 ${skillCount.value} 个技能、${toolCount.value} 个工具`
  }

  const failedItem = timelineItems.value.find((item) => item.status === 'FAILED')
  if (failedItem) {
    return `已中断 · ${failedItem.label}`
  }

  return `已完成 · 已调用 ${skillCount.value} 个技能、${toolCount.value} 个工具`
})

const showThoughtPanel = computed(() => props.msg.role !== 'user' && (timelineItems.value.length > 0 || props.live))

const showReasoningBlock = computed(
  () => props.msg.role !== 'user' && Boolean(props.msg.reasoningEnabled) && Boolean(props.msg.reasoning?.trim())
)

const showCitations = computed(
  () => props.msg.role !== 'user' && Array.isArray(props.msg.citations) && props.msg.citations.length > 0
)

const showMessageBubble = computed(
  () =>
    Boolean(formattedContent.value) ||
    showCitations.value ||
    props.msg.role === 'user'
)

const showCursor = computed(() => props.live && Boolean(props.msg.content))

const openCitation = (citation: KnowledgeCitation) => {
  emit('open-citation', citation)
}

const toggleThoughtExpanded = () => {
  thoughtInteracted.value = true
  thoughtExpanded.value = !thoughtExpanded.value
}

watch(
  [() => props.live, hasThoughtDetails],
  ([live, hasDetails]) => {
    if (live && !thoughtInteracted.value) {
      thoughtExpanded.value = hasDetails
      return
    }

    if (!live && !thoughtInteracted.value) {
      thoughtExpanded.value = false
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.chat-message {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 22px;
}

.chat-message.user {
  align-items: flex-end;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.chat-message.user .message-meta {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 30px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: var(--surface-active);
  color: var(--primary-color-strong);
  font-size: 12px;
  font-weight: 800;
}

.chat-message.user .message-avatar {
  background: var(--accent-soft);
  color: var(--accent-color);
}

.message-label {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.thought-panel,
.message-bubble {
  width: min(100%, 980px);
}

.thought-panel {
  padding: 4px 0 0;
}

.thought-toggle {
  width: auto;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 10px;
  padding: 0 0 8px;
  border: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
  color: var(--text-muted);
  transition: color 0.18s ease;
}

.thought-toggle:hover {
  color: var(--primary-color-strong);
}

.thought-toggle-main {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  min-width: 0;
}

.thought-title {
  color: currentColor;
  font-size: 14px;
  font-weight: 700;
}

.thought-summary {
  position: relative;
  display: inline-flex;
  align-items: center;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.thought-summary.is-waiting {
  color: rgba(108, 114, 132, 0.88);
  background-image: linear-gradient(
    90deg,
    rgba(108, 114, 132, 0.88) 0%,
    rgba(108, 114, 132, 0.88) 34%,
    rgba(239, 91, 42, 0.24) 45%,
    rgba(255, 255, 255, 0.96) 50%,
    rgba(239, 91, 42, 0.24) 55%,
    rgba(108, 114, 132, 0.88) 66%,
    rgba(108, 114, 132, 0.88) 100%
  );
  background-size: 240% 100%;
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  animation: thoughtWave 2.2s linear infinite;
}

.thought-arrow {
  display: inline-flex;
  color: currentColor;
  font-size: 14px;
  transform: rotate(-90deg);
  transition: transform 0.18s ease;
}

.thought-arrow.expanded {
  transform: rotate(0deg);
}

.thought-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 4px;
  padding: 0 0 0 2px;
}

.thought-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.thought-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.thought-muted {
  color: var(--text-muted);
}

.timeline-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.timeline-item {
  display: flex;
  gap: 10px;
  min-height: 46px;
  padding: 11px 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(23, 31, 58, 0.08);
}

.timeline-indicator {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  color: var(--text-secondary);
  font-size: 16px;
}

.timeline-indicator .spin {
  color: var(--primary-color);
  animation: spin 1s linear infinite;
}

.timeline-indicator .success {
  color: var(--success-color);
}

.timeline-indicator .error {
  color: var(--error-color);
}

.timeline-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.timeline-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.timeline-title {
  color: var(--text-color);
  font-size: 13px;
  font-weight: 700;
}

.timeline-desc {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.timeline-count {
  flex: none;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(239, 91, 42, 0.08);
  color: var(--primary-color-strong);
  font-size: 11px;
  font-weight: 700;
}

.timeline-item.status-running {
  border-color: rgba(239, 91, 42, 0.18);
}

.timeline-item.status-failed {
  border-color: rgba(197, 72, 63, 0.2);
  background: rgba(255, 247, 247, 0.96);
}

.reasoning-card {
  padding: 14px 16px;
  border: 1px solid rgba(23, 31, 58, 0.08);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.78);
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.8;
}

.message-bubble {
  padding: 18px 20px;
  border-radius: 18px;
  background: var(--background-elevated);
  border: 1px solid var(--line-soft);
}

.chat-message.user .message-bubble {
  width: min(82%, 760px);
  background: var(--surface-subtle);
  border-color: var(--border-accent-soft);
}

.message-sources {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--line-soft);
}

.sources-label {
  display: inline-flex;
  margin-bottom: 10px;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.sources-links {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 14px;
}

.source-link {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--primary-color-strong);
  font-size: 13px;
  line-height: 1.6;
  text-decoration: underline;
  text-decoration-color: var(--border-accent-strong);
  cursor: pointer;
  text-underline-offset: 3px;
}

.source-link:hover {
  color: var(--primary-color);
}

.source-name {
  font-weight: 700;
}

.source-meta {
  color: var(--text-muted);
  font-size: 12px;
}

.cursor-blink {
  color: var(--primary-color);
  animation: blink 1s infinite;
}

@keyframes blink {
  0%,
  50% {
    opacity: 1;
  }
  51%,
  100% {
    opacity: 0;
  }
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

@keyframes thoughtWave {
  0% {
    background-position: 140% 50%;
  }
  100% {
    background-position: -40% 50%;
  }
}

@media (max-width: 820px) {
  .thought-panel,
  .message-bubble,
  .chat-message.user .message-bubble {
    width: 100%;
  }

  .thought-toggle-main {
    align-items: flex-start;
  }

  .sources-links {
    flex-direction: column;
    gap: 8px;
  }
}
</style>
