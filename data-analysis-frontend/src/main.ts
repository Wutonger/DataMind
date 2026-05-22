import { createApp } from 'vue'
import { createPinia } from 'pinia'
import naive from 'naive-ui'
import App from './App.vue'
import router from './router'
import './styles/main.css'
import { installMarkdownCopyHandler } from './utils/markdownCopy'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(naive)
installMarkdownCopyHandler()

app.mount('#app')
