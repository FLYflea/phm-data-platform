import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as echarts from 'echarts'
import App from './App.vue'

const app = createApp(App)

// 全局挂载 echarts
app.config.globalProperties.$echarts = echarts

app.use(ElementPlus)
app.mount('#app')
