<template>
  <div class="workspace-page workflow-page">
    <section class="page-section workflow-runs-section">
      <div class="workflow-admin-bar">
        <div class="workflow-scene-tabs" role="tablist" aria-label="执行链路场景">
          <button
            v-for="scene in sceneMetaList"
            :key="scene.key"
            type="button"
            class="workflow-scene-tab"
            :class="{ active: activeScene === scene.key }"
            @click="selectScene(scene.key)"
          >
            {{ scene.label }}
          </button>
        </div>

        <div class="section-actions">
          <n-button tertiary @click="openSceneSource">
            {{ activeSceneMeta.entryLabel }}
          </n-button>
        </div>
      </div>

      <div class="workflow-list-toolbar">
        <n-input
          v-model:value="keyword"
          clearable
          placeholder="搜索名称或执行 ID"
          class="workflow-search"
        />
        <n-select
          v-model:value="statusFilter"
          clearable
          :options="statusOptions"
          placeholder="运行状态"
          class="workflow-status-filter"
        />
      </div>

      <div class="workflow-table-shell">
        <n-data-table
          :columns="columns"
          :data="paginatedRuns"
          :bordered="false"
          :loading="loadingRuns"
          :pagination="false"
          :row-key="rowKey"
          :row-props="rowProps"
          :scroll-x="920"
          :single-line="false"
          striped
          size="small"
        >
          <template #empty>
            <div class="workflow-table-empty">
              <h3>{{ loadingRuns ? '正在加载执行链路记录' : '当前场景还没有运行记录' }}</h3>
              <p>{{ loadingRuns ? '正在读取运行列表。' : activeSceneMeta.emptyHint }}</p>
              <n-button v-if="!loadingRuns" type="primary" @click="openSceneSource">
                {{ activeSceneMeta.entryLabel }}
              </n-button>
            </div>
          </template>
        </n-data-table>
      </div>

      <div class="workflow-pagination">
        <div class="workflow-pagination-meta">
          共 {{ filteredRuns.length }} 条
        </div>
        <n-pagination
          v-model:page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :item-count="filteredRuns.length"
          show-size-picker
          show-quick-jumper
        />
      </div>
    </section>

    <n-modal
      v-model:show="showDetailModal"
      preset="card"
      :title="detailTitle"
      style="width: 980px; max-width: 94vw;"
      :bordered="false"
      :segmented="{ content: true }"
    >
      <div v-if="selectedRun" class="workflow-detail-page">
        <section class="workflow-detail-overview">
          <div class="workflow-detail-fact">
            <span>名称</span>
            <strong>{{ selectedRun.title || '未命名运行' }}</strong>
          </div>
          <div class="workflow-detail-fact workflow-detail-id">
            <span>执行 ID</span>
            <strong>{{ selectedRun.id }}</strong>
          </div>
          <div class="workflow-detail-fact">
            <span>场景</span>
            <strong>{{ sceneLabel(selectedRun.scene) }}</strong>
          </div>
          <div class="workflow-detail-fact">
            <span>状态</span>
            <n-tag size="small" :type="statusTagType(selectedRun.status)" :bordered="false">
              {{ statusLabel(selectedRun.status) }}
            </n-tag>
          </div>
          <div class="workflow-detail-fact">
            <span>触发时间</span>
            <strong>{{ formatDateTime(selectedRun.startedAt) }}</strong>
          </div>
          <div class="workflow-detail-fact">
            <span>总耗时</span>
            <strong>{{ formatDuration(selectedRun.totalDurationMs) }}</strong>
          </div>
          <div class="workflow-detail-fact">
            <span>步骤/事件</span>
            <strong>{{ selectedRun.steps.length }} / {{ selectedRun.timeline.length }}</strong>
          </div>
        </section>

        <section class="workflow-detail-section">
          <div class="section-head">
            <div class="section-copy">
              <h3 class="section-title">执行步骤</h3>
            </div>
          </div>

          <div v-if="selectedRun.steps.length" class="workflow-step-list">
            <article
              v-for="(step, index) in selectedRun.steps"
              :key="step.id"
              class="workflow-step-card"
              :class="{
                failed: step.status === 'FAILED'
              }"
            >
              <span class="workflow-step-index">{{ index + 1 }}</span>
              <div class="workflow-step-copy">
                <strong>{{ step.title }}</strong>
                <span>{{ step.kind || step.owner || resolveAgentTitle(step.agentId) }}</span>
              </div>
              <n-tag size="small" :type="statusTagType(step.status)" :bordered="false">
                {{ statusLabel(step.status) }}
              </n-tag>
              <em>{{ formatDuration(step.durationMs) }}</em>
            </article>
          </div>

          <div v-else class="workflow-empty-inline">当前运行还没有可展示的步骤。</div>
        </section>

        <section class="workflow-detail-section">
          <div class="section-head">
            <div class="section-copy">
              <h3 class="section-title">执行时间线</h3>
            </div>
          </div>

          <div v-if="selectedRun.timeline.length" class="workflow-timeline">
            <article
              v-for="item in selectedRun.timeline"
              :key="`${item.time}-${item.nodeId}-${item.message}`"
              class="workflow-timeline-item"
            >
              <span class="workflow-timeline-time">{{ item.time }}</span>
              <div class="workflow-timeline-content">
                <strong>{{ item.title }}</strong>
                <p>{{ item.message }}</p>
              </div>
            </article>
          </div>

          <div v-else class="workflow-empty-inline">当前运行还没有时间线事件。</div>
        </section>
      </div>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, h, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { DataTableColumns } from 'naive-ui'
import {
  NButton,
  NDataTable,
  NInput,
  NModal,
  NPagination,
  NSelect,
  NTag
} from 'naive-ui'
import { workflowApi } from '@/api'
import type { WorkflowRun } from '@/api'
import { useAppStore } from '@/stores/app'

type WorkflowScene = 'chat' | 'sql' | 'analysis' | 'report'

interface SceneMeta {
  key: WorkflowScene
  label: string
  entryLabel: string
  entryRoute: string
  emptyHint: string
}

const router = useRouter()
const route = useRoute()
const appStore = useAppStore()

const sceneMetaList: SceneMeta[] = [
  {
    key: 'chat',
    label: '智能问答',
    entryLabel: '返回智能问答',
    entryRoute: '/chat',
    emptyHint: '先在智能问答中发起一次提问，这里就会展示真实的执行过程。'
  },
  {
    key: 'sql',
    label: 'SQL 工作台',
    entryLabel: '打开 SQL 工作台',
    entryRoute: '/sql-studio',
    emptyHint: '先在 SQL 工作台生成一次 SQL，这里就会出现对应的执行链路记录。'
  },
  {
    key: 'analysis',
    label: '表结构分析',
    entryLabel: '返回表结构分析',
    entryRoute: '/analysis',
    emptyHint: '先执行一次表结构分析，这里就会展示对应的执行链路。'
  },
  {
    key: 'report',
    label: '报表中心',
    entryLabel: '打开报表中心',
    entryRoute: '/reports',
    emptyHint: '先在报表中心生成一次图表或报告，这里就会展示对应的执行链路。'
  }
]

const statusOptions = [
  { label: '成功', value: 'COMPLETED' },
  { label: '失败', value: 'FAILED' }
]

const workflowRuns = ref<WorkflowRun[]>([])
const loadingRuns = ref(false)
const keyword = ref('')
const statusFilter = ref<string | null>(null)
const page = ref(1)
const pageSize = ref(10)
let sceneRunsLoadSeq = 0

const validSceneKeys = sceneMetaList.map((item) => item.key)

const normalizeScene = (value: unknown): WorkflowScene => {
  const scene = String(value || '').trim() as WorkflowScene
  return validSceneKeys.includes(scene) ? scene : 'chat'
}

const activeScene = computed<WorkflowScene>(() => normalizeScene(route.query.scene))
const activeSceneMeta = computed(() => sceneMetaList.find((item) => item.key === activeScene.value) || sceneMetaList[0])
const queryRunId = computed(() => String(route.query.runId || '').trim())

const filteredRuns = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLowerCase()

  return workflowRuns.value.filter((run) => {
    const matchesKeyword =
      !normalizedKeyword ||
      run.title.toLowerCase().includes(normalizedKeyword) ||
      run.id.toLowerCase().includes(normalizedKeyword)
    const matchesStatus = !statusFilter.value || run.status === statusFilter.value

    return matchesKeyword && matchesStatus
  })
})

const paginatedRuns = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredRuns.value.slice(start, start + pageSize.value)
})

const selectedRun = computed(() => {
  return workflowRuns.value.find((run) => run.id === queryRunId.value) || null
})

const showDetailModal = computed({
  get: () => Boolean(selectedRun.value),
  set: (value: boolean) => {
    if (!value) {
      closeRunDetail()
    }
  }
})

const detailTitle = computed(() => {
  return selectedRun.value ? `${selectedRun.value.title} · 执行详情` : '执行详情'
})

const columns: DataTableColumns<WorkflowRun> = [
  {
    title: '名称',
    key: 'title',
    minWidth: 200,
    render: (run) =>
      h('span', { class: 'workflow-run-name' }, run.title || '未命名运行')
  },
  {
    title: '执行 ID',
    key: 'id',
    minWidth: 210,
    render: (run) => h('span', { class: 'workflow-id-cell' }, run.id)
  },
  {
    title: '状态',
    key: 'status',
    width: 110,
    render: (run) =>
      h(
        NTag,
        { size: 'small', type: statusTagType(run.status), bordered: false },
        { default: () => statusLabel(run.status) }
      )
  },
  {
    title: '场景',
    key: 'scene',
    width: 120,
    render: (run) => sceneLabel(run.scene)
  },
  {
    title: '耗时',
    key: 'totalDurationMs',
    width: 110,
    render: (run) => formatDuration(run.totalDurationMs)
  },
  {
    title: '触发时间',
    key: 'startedAt',
    width: 150,
    render: (run) => formatDateTime(run.startedAt)
  },
  {
    title: '操作',
    key: 'actions',
    width: 96,
    fixed: 'right',
    render: (run) =>
      h(
        NButton,
        {
          size: 'small',
          tertiary: true,
          onClick: (event: MouseEvent) => {
            event.stopPropagation()
            openRunDetail(run)
          }
        },
        { default: () => '查看' }
      )
  }
]

const rowKey = (run: WorkflowRun) => run.id

const rowProps = (run: WorkflowRun) => ({
  class: 'workflow-table-row',
  onClick: () => openRunDetail(run)
})

const loadSceneRuns = async (scene: WorkflowScene) => {
  const loadSeq = ++sceneRunsLoadSeq
  loadingRuns.value = true

  try {
    const connectionId = appStore.currentConnectionId
    const res = await workflowApi.list(scene, connectionId)
    let runs = Array.isArray(res.data) ? res.data : []

    if (loadSeq !== sceneRunsLoadSeq) {
      return
    }

    if (queryRunId.value && !runs.some((run) => run.id === queryRunId.value)) {
      const detail = await fetchRunDetail(queryRunId.value)
      if (loadSeq !== sceneRunsLoadSeq) {
        return
      }

      if (detail?.scene === scene) {
        runs = [detail, ...runs.filter((run) => run.id !== detail.id)]
      }
    }

    workflowRuns.value = runs

    if (queryRunId.value && !runs.some((run) => run.id === queryRunId.value)) {
      closeRunDetail()
    }
  } catch (error) {
    if (loadSeq !== sceneRunsLoadSeq) {
      return
    }

    console.error('Failed to load workflow runs', error)
    workflowRuns.value = []
  } finally {
    if (loadSeq === sceneRunsLoadSeq) {
      loadingRuns.value = false
    }
  }
}

const fetchRunDetail = async (runId: string) => {
  try {
    const connectionId = appStore.currentConnectionId
    const detail = await workflowApi.get(runId, connectionId)
    return detail.data || null
  } catch (error) {
    console.error('Failed to load workflow run detail', error)
    return null
  }
}

const loadRunDetailIfMissing = async (runId: string) => {
  if (workflowRuns.value.some((run) => run.id === runId)) {
    return
  }

  const detail = await fetchRunDetail(runId)
  if (!detail || detail.scene !== activeScene.value) {
    closeRunDetail()
    return
  }

  workflowRuns.value = [detail, ...workflowRuns.value.filter((run) => run.id !== detail.id)]
}

const selectScene = (scene: WorkflowScene) => {
  router.replace({
    name: 'Workflow',
    query: {
      scene
    }
  })
}

const openRunDetail = (run: WorkflowRun) => {
  router.replace({
    name: 'Workflow',
    query: {
      scene: run.scene,
      runId: run.id
    }
  })
}

const closeRunDetail = () => {
  router.replace({
    name: 'Workflow',
    query: {
      scene: activeScene.value
    }
  })
}

const openSceneSource = () => {
  router.push(activeSceneMeta.value.entryRoute)
}

const sceneLabel = (scene: string) => {
  return sceneMetaList.find((item) => item.key === scene)?.label || scene || '--'
}

const resolveAgentTitle = (agentId: string) => {
  return agentId === 'assistant' || !agentId ? 'AssistantAgent' : agentId
}

const formatDuration = (durationMs: number) => {
  if (!durationMs) {
    return '0 ms'
  }

  if (durationMs >= 1000) {
    return `${(durationMs / 1000).toFixed(durationMs >= 10000 ? 0 : 1)} s`
  }

  return `${durationMs} ms`
}

const formatDateTime = (value: string) => {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

const statusLabel = (status: string) => {
  switch (status) {
    case 'FAILED':
      return '失败'
    default:
      return '成功'
  }
}

const statusTagType = (status: string): 'default' | 'info' | 'success' | 'warning' | 'error' => {
  switch (status) {
    case 'FAILED':
      return 'error'
    default:
      return 'success'
  }
}

watch(
  activeScene,
  async (scene) => {
    page.value = 1
    await loadSceneRuns(scene)
  },
  { immediate: true }
)

watch(
  () => appStore.currentConnectionId,
  async () => {
    await loadSceneRuns(activeScene.value)
  }
)

watch([keyword, statusFilter], () => {
  page.value = 1
})

watch(
  filteredRuns,
  (runs) => {
    const maxPage = Math.max(1, Math.ceil(runs.length / pageSize.value))
    if (page.value > maxPage) {
      page.value = maxPage
    }
  },
  { flush: 'post' }
)

watch(
  queryRunId,
  async (runId) => {
    if (runId) {
      await loadRunDetailIfMissing(runId)
    }
  }
)
</script>

<style scoped>
.workflow-page {
  --workflow-surface: rgba(255, 255, 255, 0.98);
}

.workflow-runs-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px;
}

.workflow-admin-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.workflow-scene-tabs {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 4px;
  border: 1px solid var(--line-soft);
  border-radius: 16px;
  background: var(--background-muted);
}

.workflow-scene-tab {
  min-height: 34px;
  padding: 0 14px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 700;
  transition:
    background-color 0.18s ease,
    color 0.18s ease,
    box-shadow 0.18s ease;
}

.workflow-scene-tab:hover {
  color: var(--text-color);
  background: var(--surface-hover);
}

.workflow-scene-tab.active {
  color: var(--primary-color-strong);
  background: var(--surface-active);
  box-shadow: inset 0 0 0 1px var(--border-accent-soft);
}

.workflow-list-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 12px;
  padding: 0;
  background: var(--background-strong);
}

.workflow-search {
  flex: 0 0 360px;
  width: 360px;
}

.workflow-status-filter {
  flex: 0 0 180px;
  width: 180px;
}

.workflow-list-toolbar :deep(.n-input),
.workflow-list-toolbar :deep(.n-base-selection) {
  --n-border-radius: 12px !important;
  border-radius: 12px;
}

.workflow-table-shell {
  overflow: hidden;
  border: 1px solid var(--line-soft);
  border-radius: 16px;
  background: var(--background-elevated);
}

.workflow-table-shell :deep(.n-data-table-th) {
  background: var(--surface-table-head);
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 500;
}

.workflow-table-shell :deep(.n-data-table-td) {
  color: var(--text-color);
  font-size: 13px;
  padding-top: 14px;
  padding-bottom: 14px;
}

.workflow-table-shell :deep(.workflow-table-row) {
  cursor: pointer;
}

.workflow-table-shell :deep(.workflow-table-row:hover .n-data-table-td) {
  background: var(--surface-hover);
}

.workflow-run-name {
  display: block;
  color: var(--text-color);
  font-size: 14px;
  font-weight: 400;
  line-height: 1.4;
}

.workflow-id-cell {
  display: block;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.5;
  word-break: break-all;
}

.workflow-table-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 42px 16px;
  text-align: center;
}

.workflow-table-empty h3 {
  margin: 0;
  color: var(--text-color);
  font-size: 17px;
  font-weight: 600;
}

.workflow-table-empty p {
  max-width: 520px;
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.workflow-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.workflow-pagination-meta {
  color: var(--text-secondary);
  font-size: 13px;
}

.workflow-detail-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.workflow-detail-overview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.workflow-detail-fact {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--line-soft);
  border-radius: 16px;
  background: var(--background-elevated);
}

.workflow-detail-fact span {
  color: var(--text-muted);
  font-size: 12px;
}

.workflow-detail-fact strong {
  color: var(--text-color);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.6;
  word-break: break-word;
}

.workflow-detail-fact-wide {
  grid-column: 1 / -1;
}

.workflow-detail-id {
  grid-column: span 2;
}

.workflow-detail-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.workflow-step-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.workflow-step-card {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 14px 16px;
  border: 1px solid var(--line-soft);
  border-radius: 16px;
  background: var(--background-elevated);
  text-align: left;
}

.workflow-step-card.failed {
  border-color: var(--border-danger-soft);
  background: var(--surface-danger);
}

.workflow-step-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 12px;
  background: var(--surface-active);
  color: var(--primary-color-strong);
  font-size: 13px;
  font-weight: 600;
}

.workflow-step-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.workflow-step-copy strong {
  color: var(--text-color);
  font-size: 14px;
  font-weight: 600;
}

.workflow-step-copy span {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.workflow-step-card em {
  color: var(--text-muted);
  font-size: 12px;
  font-style: normal;
  white-space: nowrap;
}

.workflow-timeline {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-left: 12px;
}

.workflow-timeline::before {
  content: '';
  position: absolute;
  top: 6px;
  bottom: 6px;
  left: 3px;
  width: 1px;
  background: var(--line-soft);
}

.workflow-timeline-item {
  position: relative;
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 16px;
}

.workflow-timeline-item::before {
  content: '';
  position: absolute;
  top: 8px;
  left: -12px;
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--primary-color);
}

.workflow-timeline-time {
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.workflow-timeline-content {
  padding: 0 0 12px;
}

.workflow-timeline-content strong {
  display: block;
  margin-bottom: 4px;
  color: var(--text-color);
  font-size: 13px;
  font-weight: 600;
}

.workflow-timeline-content p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.workflow-empty-inline {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

@media (max-width: 980px) {
  .workflow-admin-bar,
  .workflow-list-toolbar,
  .workflow-pagination {
    align-items: stretch;
    flex-direction: column;
  }

  .workflow-search,
  .workflow-status-filter {
    flex-basis: auto;
    max-width: none;
    width: 100%;
  }

  .workflow-detail-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .workflow-detail-overview {
    grid-template-columns: minmax(0, 1fr);
  }

  .workflow-detail-id {
    grid-column: auto;
  }

  .workflow-step-card {
    grid-template-columns: 34px minmax(0, 1fr);
  }

  .workflow-step-card .n-tag,
  .workflow-step-card em {
    grid-column: 2;
  }

  .workflow-timeline-item {
    grid-template-columns: minmax(0, 1fr);
    gap: 8px;
  }

  .workflow-timeline-time {
    padding-left: 2px;
  }
}
</style>
