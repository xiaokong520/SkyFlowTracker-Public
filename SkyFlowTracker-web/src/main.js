import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import router from './router'

// PrimeVue
import PrimeVue from 'primevue/config'
import Aura from '@primevue/themes/aura'
import ToastService from 'primevue/toastservice'

// PrimeIcons
import 'primeicons/primeicons.css'

const app = createApp(App)

// 配置 PrimeVue - 使用亮色主题
app.use(PrimeVue, {
  theme: {
    preset: Aura,
    options: {
      prefix: 'p',
      darkModeSelector: 'none',
      cssLayer: false
    }
  }
})

app.use(ToastService)
app.use(router)

app.mount('#app')
