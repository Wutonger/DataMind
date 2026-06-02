import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import axios from 'axios'

export interface AuthUser {
  id: number
  username: string
  nickname: string
  role: string
  status: string
  lastConnectionId: number | null
}

const TOKEN_KEY = 'satoken'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const user = ref<AuthUser | null>(null)
  const initialized = ref(false)

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const displayName = computed(() => user.value?.nickname || user.value?.username || '')

  const setToken = (value: string) => {
    token.value = value
    if (value) {
      localStorage.setItem(TOKEN_KEY, value)
    } else {
      localStorage.removeItem(TOKEN_KEY)
    }
  }

  const setUser = (value: AuthUser | null) => {
    user.value = value
  }

  const login = async (username: string, password: string) => {
    const res = await axios.post('/api/auth/login', { username, password })
    setToken(res.data?.token || '')
    setUser(res.data?.user || null)
    initialized.value = true
    return user.value
  }

  const fetchMe = async () => {
    const res = await axios.get('/api/auth/me')
    setUser(res.data || null)
    initialized.value = true
    return user.value
  }

  const ensureSession = async () => {
    if (!token.value) {
      initialized.value = true
      setUser(null)
      return null
    }
    if (initialized.value && user.value) {
      return user.value
    }
    try {
      return await fetchMe()
    } catch (error) {
      clearAuth()
      throw error
    }
  }

  const updateLastConnection = async (connectionId: number | null) => {
    const res = await axios.put('/api/auth/me/last-connection', { connectionId })
    setUser(res.data || null)
    return user.value
  }

  const clearAuth = () => {
    setToken('')
    setUser(null)
    initialized.value = true
  }

  const logout = async () => {
    try {
      if (token.value) {
        await axios.post('/api/auth/logout')
      }
    } finally {
      clearAuth()
    }
  }

  return {
    token,
    user,
    initialized,
    isLoggedIn,
    isAdmin,
    displayName,
    login,
    fetchMe,
    ensureSession,
    updateLastConnection,
    logout,
    clearAuth,
    setToken,
    setUser
  }
})
