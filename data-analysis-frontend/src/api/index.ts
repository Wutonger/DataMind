import axios from 'axios'

export const tableApi = {
  scanAll: (connectionId: number) =>
    fetch(`/api/tables/scan/${connectionId}`, {
      headers: {
        Accept: 'text/event-stream',
        'Cache-Control': 'no-cache'
      }
    }),
  getMetadata: (connectionId: number) => axios.get(`/api/tables/metadata/${connectionId}`)
}

export interface KnowledgeCitation {
  documentId: number
  documentName: string
  chunkIndex: number
  content: string
  score: number
  metadata?: Record<string, any>
}

export interface AgentStep {
  id: string
  name: string
  skill: string
  description: string
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'SKIPPED'
  result?: string
}

export interface AgentEvent {
  type: string
  data: {
    content?: string
    intent?: string
    steps?: AgentStep[]
    stepId?: string
    stepName?: string
    displayName?: string
    description?: string
    result?: string
    error?: string
    token?: string
    message?: string
    citations?: KnowledgeCitation[]
    runId?: string
    workflowRunId?: string
    sessionId?: string
    scene?: string
    title?: string
    status?: string
    routeMode?: string
  }
}

export interface WorkflowStep {
  id: string
  agentId: string
  owner: string
  title: string
  kind: string
  status: string
  durationMs: number
  inputSummary: string
  outputSummary: string
  tools: string[]
}

export interface WorkflowTimelineItem {
  time: string
  nodeId: string
  title: string
  message: string
}

export interface WorkflowRun {
  id: string
  scene: string
  title: string
  routeMode: string
  status: string
  totalDurationMs: number
  startedAt: string
  finalPath: string[]
  usedAgents: string[]
  steps: WorkflowStep[]
  timeline: WorkflowTimelineItem[]
}

export interface KnowledgeDocument {
  id: number
  connectionId: number
  name: string
  type: string
  status: string
  totalChunks: number
  errorMessage?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface KnowledgePreviewChunk {
  chunkIndex: number
  content: string
  metadata?: Record<string, any>
}

export interface KnowledgePreview {
  document: KnowledgeDocument
  chunks: KnowledgePreviewChunk[]
}

export interface KnowledgeSearchResponse {
  summary: string
  citations: KnowledgeCitation[]
}

export const chatApi = {
  send: (sessionId: string | null, connectionId: number | null, message: string) => {
    return fetch('/api/chat/send', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sessionId: sessionId || null, connectionId, message })
    })
  },
  stream: (sessionId: string | null, connectionId: number | null, message: string) =>
    axios.post('/api/chat/stream', { sessionId: sessionId || null, connectionId, message }, { responseType: 'text' }),
  getHistory: (sessionId: string) => axios.get(`/api/chat/history/${sessionId}`),
  clearHistory: (sessionId: string) => axios.delete(`/api/chat/history/${sessionId}`),
  getSessions: (connectionId: number) => axios.get('/api/chat/sessions', { params: { connectionId } }),
  compress: (sessionId: string) => axios.post(`/api/chat/compress/${sessionId}`)
}

export const sqlApi = {
  execute: (connectionId: number, sql: string) => axios.post('/api/sql/execute', { connectionId, sql }),
  generate: (connectionId: number, question: string) => axios.post('/api/sql/generate', { connectionId, question }),
  format: (sql: string) => axios.post('/api/sql/format', { sql }),
  getHistory: (connectionId: number) => axios.get(`/api/sql/history/${connectionId}`),
  deleteHistory: (connectionId: number, historyId: number) =>
    axios.delete(`/api/sql/history/${connectionId}/${historyId}`)
}

export const reportApi = {
  list: (connectionId: number) => axios.get('/api/reports/list', { params: { connectionId } }),
  get: (id: number) => axios.get(`/api/reports/${id}`),
  update: (id: number, report: any) => axios.put(`/api/reports/${id}`, report),
  delete: (id: number) => axios.delete(`/api/reports/${id}`),
  generateReport: (connectionId: number, requirement: string) =>
    axios.post('/api/reports/generate', { connectionId, requirement }),
  exportExcel: (connectionId: number, sql: string, sheetName: string) =>
    axios.post('/api/reports/export/excel', { connectionId, sql, sheetName }, { responseType: 'blob' }),
  exportPdf: (payload: { reportId?: number; connectionId?: number | null; sql?: string; title: string }) =>
    axios.post('/api/reports/export/pdf', payload, { responseType: 'blob' })
}

export const knowledgeApi = {
  upload: (formData: FormData) =>
    axios.post<KnowledgeDocument[]>('/api/knowledge/documents/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }),
  list: (connectionId: number) =>
    axios.get<KnowledgeDocument[]>('/api/knowledge/documents', { params: { connectionId } }),
  get: (id: number) => axios.get<KnowledgeDocument>(`/api/knowledge/documents/${id}`),
  preview: (id: number) => axios.get<KnowledgePreview>(`/api/knowledge/documents/${id}/preview`),
  download: (id: number) =>
    axios.get(`/api/knowledge/documents/${id}/download`, { responseType: 'blob' }),
  delete: (id: number) => axios.delete(`/api/knowledge/documents/${id}`),
  reindex: (id: number) => axios.post<KnowledgeDocument>(`/api/knowledge/documents/${id}/reindex`),
  search: (payload: { connectionId: number; query: string }) =>
    axios.post<KnowledgeSearchResponse>('/api/knowledge/search', payload)
}

export const workflowApi = {
  list: (scene: string, connectionId?: number | null) =>
    axios.get<WorkflowRun[]>('/api/workflow/runs', {
      params: {
        scene,
        ...(connectionId !== null && connectionId !== undefined ? { connectionId } : {})
      }
    }),
  get: (runId: string, connectionId?: number | null) =>
    axios.get<WorkflowRun>(`/api/workflow/runs/${runId}`, {
      params: {
        ...(connectionId !== null && connectionId !== undefined ? { connectionId } : {})
      }
    })
}
