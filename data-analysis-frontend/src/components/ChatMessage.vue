<template>
  <div :class="['chat-message', msg.role, { live }]">
    <div class="message-meta">
      <span class="message-avatar">{{ msg.role === 'user' ? '我' : 'AI' }}</span>
      <span class="message-label">{{ msg.role === 'user' ? '我的问题' : 'DataMind AI' }}</span>
    </div>

    <div v-if="showStepsPanel" class="steps-panel">
      <div class="steps-header">
        <span class="steps-title">执行进度</span>
        <span class="steps-count">{{ completedSteps }}/{{ steps.length }}</span>
      </div>

      <div class="steps-list">
        <div
          v-for="step in steps"
          :key="step.id"
          class="step-row"
          :class="`status-${step.status.toLowerCase()}`"
        >
          <div class="step-status-icon">
            <n-icon v-if="step.status === 'RUNNING'" class="spin"><RefreshOutline /></n-icon>
            <n-icon v-else-if="step.status === 'COMPLETED'" class="success"><CheckmarkCircleOutline /></n-icon>
            <n-icon v-else-if="step.status === 'FAILED'" class="error"><CloseCircleOutline /></n-icon>
            <n-icon v-else><TimeOutline /></n-icon>
          </div>

          <div class="step-main">
            <span class="step-name">{{ step.name }}</span>
            <span v-if="step.mergedCount > 1" class="step-merge-count">x{{ step.mergedCount }}</span>
          </div>
        </div>

        <div v-if="showLoadingStep" class="step-row status-running">
          <div class="step-status-icon">
            <n-icon class="spin"><RefreshOutline /></n-icon>
          </div>
          <span class="step-name loading-placeholder">正在思考</span>
        </div>
      </div>
    </div>

    <div v-if="showMessageBubble" class="message-bubble markdown-body">
      <div v-if="showReasoningPanel" class="reasoning-panel" :class="{ expanded: reasoningExpanded }">
        <button type="button" class="reasoning-toggle" @click="toggleReasoning">
          <span class="reasoning-toggle-main">
            <span class="reasoning-title">思考过程</span>
            <n-icon class="reasoning-arrow" :class="{ expanded: reasoningExpanded }">
              <ChevronDownOutline />
            </n-icon>
          </span>
        </button>

        <div v-if="reasoningExpanded" class="reasoning-body">
          <div class="reasoning-stream-head">
            <span class="reasoning-stream-icon" aria-hidden="true">
              <svg viewBox="0 0 16 16" class="thinking-glyph" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path
                  d="M8 2.2c-2.6 0-4.7 2.1-4.7 4.7 0 .8.2 1.5.5 2.1-.3.4-.4.8-.4 1.3 0 1.3.9 2.4 2.2 2.7.4.8 1.2 1.4 2.2 1.4.6 0 1.2-.2 1.6-.6.4.4 1 .6 1.6.6 1 0 1.8-.6 2.2-1.4 1.3-.3 2.2-1.4 2.2-2.7 0-.5-.1-.9-.4-1.3.3-.6.5-1.3.5-2.1 0-2.6-2.1-4.7-4.7-4.7-.6 0-1.3.2-1.9.5-.6-.3-1.3-.5-1.9-.5Z"
                  stroke="currentColor"
                  stroke-width="0.95"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
                <path
                  d="M8.35 4.2v7.6"
                  stroke="currentColor"
                  stroke-width="0.95"
                  stroke-linecap="round"
                />
              </svg>
            </span>
            <span class="reasoning-stream-label">思考</span>
          </div>

          <div v-if="formattedReasoning" class="reasoning-card" v-html="formattedReasoning"></div>
          <div v-else class="reasoning-card reasoning-waiting" aria-live="polite" aria-label="正在思考">
            <span class="thinking-label">正在思考</span>
            <span class="thinking-dots" aria-hidden="true">
              <i></i>
              <i></i>
              <i></i>
            </span>
          </div>
        </div>
      </div>

      <div v-if="formattedContent" v-html="formattedContent"></div>

      <div v-else-if="showThinkingIndicator" class="thinking-indicator" aria-live="polite" aria-label="正在思考">
        <span class="thinking-label">正在思考</span>
        <span class="thinking-dots" aria-hidden="true">
          <i></i>
          <i></i>
          <i></i>
        </span>
      </div>

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
import { computed, ref } from 'vue'
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
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'SKIPPED'
  mergedCount: number
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
    showLoadingStep?: boolean
  }>(),
  {
    live: false,
    steps: () => [],
    completedSteps: 0,
    showLoadingStep: false
  }
)

const emit = defineEmits<{
  (event: 'open-citation', citation: KnowledgeCitation): void
}>()

const reasoningExpanded = ref(props.live)

const formattedContent = computed(() => renderMarkdownContent(props.msg.content || ''))
const formattedReasoning = computed(() => renderMarkdownContent(props.msg.reasoning || ''))

const showStepsPanel = computed(
  () => props.msg.role !== 'user' && (props.steps.length > 0 || (props.live && props.showLoadingStep))
)

const showReasoningPanel = computed(
  () =>
    props.msg.role !== 'user' &&
    (Boolean(props.msg.reasoning?.trim()) || (props.live && props.msg.reasoningEnabled && !props.msg.content))
)

const showThinkingIndicator = computed(
  () =>
    props.live &&
    props.msg.role !== 'user' &&
    !props.msg.content &&
    props.steps.length === 0 &&
    !showReasoningPanel.value
)

const showCitations = computed(
  () => props.msg.role !== 'user' && Array.isArray(props.msg.citations) && props.msg.citations.length > 0
)

const showMessageBubble = computed(
  () =>
    Boolean(formattedContent.value) ||
    showThinkingIndicator.value ||
    showReasoningPanel.value ||
    showCitations.value
)

const showCursor = computed(() => props.live && Boolean(props.msg.content))

const openCitation = (citation: KnowledgeCitation) => {
  emit('open-citation', citation)
}

const toggleReasoning = () => {
  reasoningExpanded.value = !reasoningExpanded.value
}
</script>

<style scoped>
.chat-message {
  display: flex;
  flex-direction: column;
  gap: 8px;
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
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.steps-panel {
  width: min(100%, 980px);
  padding: 14px;
  border-radius: 16px;
  background: var(--surface-subtle);
  border: 1px solid var(--border-accent-soft);
}

.steps-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.steps-title {
  color: var(--text-color);
  font-size: 13px;
  font-weight: 700;
}

.steps-count {
  padding: 4px 9px;
  border-radius: 999px;
  background: var(--surface-active);
  color: var(--primary-color-strong);
  font-size: 12px;
  font-weight: 700;
}

.steps-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.step-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 40px;
  padding: 0 12px;
  border-radius: 12px;
  background: var(--background-elevated);
  border: 1px solid var(--line-soft);
}

.step-status-icon {
  font-size: 16px;
}

.step-status-icon .spin {
  color: var(--primary-color);
  animation: spin 1s linear infinite;
}

.step-status-icon .success {
  color: var(--success-color);
}

.step-status-icon .error {
  color: var(--error-color);
}

.step-main {
  display: flex;
  align-items: center;
  gap: 8px;
}

.step-name {
  color: var(--text-color);
  font-size: 13px;
}

.step-merge-count {
  min-width: 26px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--surface-active);
  color: var(--primary-color-strong);
  font-size: 11px;
  font-weight: 700;
  text-align: center;
}

.loading-placeholder {
  color: var(--text-muted);
}

.message-bubble {
  width: min(100%, 980px);
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

.chat-message.user .message-bubble :deep(code) {
  background: var(--surface-active);
  color: var(--primary-color-strong);
}

.chat-message.user .message-bubble :deep(blockquote) {
  border-left-color: var(--border-accent-strong);
  background: var(--surface-active);
}

.chat-message.user .message-bubble :deep(a) {
  color: var(--primary-color-strong);
}

.reasoning-panel {
  margin-bottom: 14px;
  padding: 2px 0 0;
}

.reasoning-toggle {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  padding: 0 0 10px;
  border: 0;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  text-align: left;
  transition: color 0.18s ease;
}

.reasoning-toggle:hover {
  color: var(--primary-color-strong);
}

.reasoning-toggle-main {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  width: auto;
}

.reasoning-title {
  font-size: 14px;
  font-weight: 700;
}

.reasoning-arrow {
  display: inline-flex;
  color: currentColor;
  font-size: 14px;
  transform: rotate(-90deg);
  transition: transform 0.18s ease;
}

.reasoning-arrow.expanded {
  transform: rotate(0deg);
}

.reasoning-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 0 0 6px;
}

.reasoning-stream-head {
  display: grid;
  grid-template-columns: 18px minmax(0, auto);
  align-items: center;
  column-gap: 10px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.reasoning-stream-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  color: var(--text-secondary);
}

.thinking-glyph {
  width: 18px;
  height: 18px;
}

.reasoning-stream-label {
  line-height: 1;
}

.reasoning-card {
  margin-left: 28px;
  padding: 14px 16px;
  border: 1px solid var(--line-strong);
  border-radius: 12px;
  background: var(--background-muted);
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.8;
}

.reasoning-card :deep(p:first-child) {
  margin-top: 0;
}

.reasoning-card :deep(p:last-child) {
  margin-bottom: 0;
}

.reasoning-waiting,
.thinking-indicator {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 28px;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
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
  transition:
    color 0.18s ease,
    text-decoration-color 0.18s ease;
}

.source-link:hover {
  color: var(--primary-color);
  text-decoration-color: currentColor;
}

.source-name {
  font-size: 13px;
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

.thinking-label {
  font-weight: 600;
  letter-spacing: 0.02em;
}

.thinking-dots {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.thinking-dots i {
  width: 5px;
  height: 5px;
  border-radius: 999px;
  background: currentColor;
  opacity: 0.35;
  animation: thinkingPulse 1.2s ease-in-out infinite;
}

.thinking-dots i:nth-child(2) {
  animation-delay: 0.14s;
}

.thinking-dots i:nth-child(3) {
  animation-delay: 0.28s;
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

@keyframes thinkingPulse {
  0%,
  80%,
  100% {
    opacity: 0.25;
    transform: translateY(0);
  }
  40% {
    opacity: 1;
    transform: translateY(-1px);
  }
}

@media (max-width: 820px) {
  .message-bubble,
  .steps-panel,
  .chat-message.user .message-bubble {
    width: 100%;
  }

  .sources-links {
    flex-direction: column;
    gap: 8px;
  }
}
</style>
