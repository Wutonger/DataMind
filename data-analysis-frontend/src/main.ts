import { createApp } from 'vue'
import naive from 'naive-ui'
import axios from 'axios'
import App from './App.vue'
import router from './router'
import { pinia } from './stores'
import { useAuthStore } from './stores/auth'
import './styles/main.css'
import { installMarkdownCopyHandler } from './utils/markdownCopy'

const app = createApp(App)

axios.interceptors.request.use((config) => {
  const token = localStorage.getItem('satoken')
  if (token) {
    config.headers = config.headers || {}
    config.headers.satoken = token
  }
  return config
})

axios.interceptors.response.use(
  (response) => response,
  async (error) => {
    const authStore = useAuthStore(pinia)
    if (error?.response?.status === 401) {
      authStore.clearAuth()
      if (router.currentRoute.value.name !== 'Login') {
        await router.replace({ name: 'Login' })
      }
    }
    return Promise.reject(error)
  }
)

app.use(pinia)
app.use(router)
app.use(naive)
installMarkdownCopyHandler()

app.mount('#app')
