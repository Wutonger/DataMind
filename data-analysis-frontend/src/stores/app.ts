import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'

export const useAppStore = defineStore('app', () => {
  const currentConnectionId = ref<number | null>(null)
  const currentConnectionName = ref('')

  const setCurrentConnection = (id: number | null, name?: string | null) => {
    currentConnectionId.value = id
    currentConnectionName.value = id ? name?.trim() || '' : ''
  }

  const loadActiveConnection = async () => {
    try {
      const res = await axios.get('/api/connections/active')
      if (res.data && res.data.length > 0) {
        setCurrentConnection(res.data[0].id, res.data[0].name)
      } else {
        setCurrentConnection(null)
      }
    } catch (e) {
      console.error('Failed to load active connection', e)
      setCurrentConnection(null)
    }
  }

  return {
    currentConnectionId,
    currentConnectionName,
    setCurrentConnection,
    loadActiveConnection
  }
})
