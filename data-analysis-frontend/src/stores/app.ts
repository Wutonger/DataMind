import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import axios from 'axios'
import { authApi } from '@/api/config'

export interface ConnectionSummary {
  id: number
  name: string
  type: string
  host: string
  port: number
  database: string
  username: string
  status: string
}

export const useAppStore = defineStore('app', () => {
  const accessibleConnections = ref<ConnectionSummary[]>([])
  const currentConnectionId = ref<number | null>(null)
  const loadingConnections = ref(false)

  const currentConnection = computed(
    () => accessibleConnections.value.find((item) => item.id === currentConnectionId.value) || null
  )
  const currentConnectionName = computed(() => currentConnection.value?.name || '')

  const setCurrentConnection = (id: number | null) => {
    currentConnectionId.value = id
  }

  const resolvePreferredConnection = (preferredId?: number | null) => {
    if (
      preferredId &&
      accessibleConnections.value.some((connection) => connection.id === preferredId)
    ) {
      return preferredId
    }
    return accessibleConnections.value[0]?.id ?? null
  }

  const syncCurrentConnection = async (preferredId?: number | null) => {
    const nextId = resolvePreferredConnection(preferredId)
    currentConnectionId.value = nextId
    return nextId
  }

  const loadAccessibleConnections = async (preferredId?: number | null) => {
    loadingConnections.value = true
    try {
      const res = await axios.get('/api/connections/accessible')
      accessibleConnections.value = Array.isArray(res.data) ? res.data : []
      const nextId = await syncCurrentConnection(preferredId ?? currentConnectionId.value)
      if (preferredId !== undefined && preferredId !== nextId) {
        await authApi.updateLastConnection(nextId)
      }
      return accessibleConnections.value
    } catch (e) {
      console.error('Failed to load accessible connections', e)
      accessibleConnections.value = []
      currentConnectionId.value = null
      return []
    } finally {
      loadingConnections.value = false
    }
  }

  const selectConnection = async (connectionId: number | null) => {
    await authApi.updateLastConnection(connectionId)
    currentConnectionId.value = connectionId
  }

  const reset = () => {
    accessibleConnections.value = []
    currentConnectionId.value = null
  }

  return {
    accessibleConnections,
    currentConnectionId,
    currentConnection,
    currentConnectionName,
    loadingConnections,
    setCurrentConnection,
    loadAccessibleConnections,
    selectConnection,
    reset
  }
})
