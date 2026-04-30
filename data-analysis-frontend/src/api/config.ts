import axios from 'axios'

export const configApi = {
  getAiConfig: () => axios.get('/api/config/ai'),
  updateAiConfig: (config: any) => axios.put('/api/config/ai', config)
}
