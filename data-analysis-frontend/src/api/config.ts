import axios from 'axios'

export const configApi = {
  getAiConfig: () => axios.get('/api/config/ai'),
  updateAiConfig: (config: any) => axios.put('/api/config/ai', config)
}

export const authApi = {
  login: (username: string, password: string) => axios.post('/api/auth/login', { username, password }),
  logout: () => axios.post('/api/auth/logout'),
  me: () => axios.get('/api/auth/me'),
  updateLastConnection: (connectionId: number | null) =>
    axios.put('/api/auth/me/last-connection', { connectionId })
}
