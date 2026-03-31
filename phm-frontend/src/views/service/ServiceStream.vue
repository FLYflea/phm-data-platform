<template>
  <div class="service-stream">
    <h2>数据流计算服务</h2>
    <p class="desc">服务层数据流：基于 SSE 的实时数据推送，滑动窗口统计，实时告警</p>

    <el-row :gutter="20">
      <!-- 左侧：实时数据流 -->
      <el-col :span="14">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>实时数据流</span>
              <div>
                <el-tag :type="connected ? 'success' : 'info'" style="margin-right: 10px">
                  {{ connected ? '已连接' : '未连接' }}
                </el-tag>
                <el-button v-if="!connected" type="primary" size="small" @click="startStream">
                  开始订阅
                </el-button>
                <el-button v-else type="danger" size="small" @click="stopStream">
                  断开连接
                </el-button>
              </div>
            </div>
          </template>

          <el-form inline style="margin-bottom: 15px">
            <el-form-item label="设备">
              <el-select v-model="streamForm.deviceId" style="width: 120px" :disabled="connected">
                <el-option label="EQ-001" value="EQ-001" />
                <el-option label="EQ-002" value="EQ-002" />
                <el-option label="EQ-003" value="EQ-003" />
              </el-select>
            </el-form-item>
            <el-form-item label="传感器">
              <el-select v-model="streamForm.sensorType" style="width: 110px" :disabled="connected">
                <el-option label="温度" value="temperature" />
                <el-option label="振动" value="vibration" />
                <el-option label="压力" value="pressure" />
                <el-option label="电流" value="current" />
              </el-select>
            </el-form-item>
            <el-form-item label="推送间隔">
              <el-select v-model="streamForm.interval" style="width: 100px" :disabled="connected">
                <el-option label="1秒" :value="1000" />
                <el-option label="3秒" :value="3000" />
                <el-option label="5秒" :value="5000" />
                <el-option label="10秒" :value="10000" />
              </el-select>
            </el-form-item>
          </el-form>

          <!-- 实时图表 -->
          <div ref="realtimeChartRef" class="chart-container"></div>

          <!-- 最新数据 -->
          <div v-if="latestData" class="latest-data">
            <el-row :gutter="15">
              <el-col :span="6">
                <el-statistic title="最新值" :value="latestData.value || 0" :precision="2" />
              </el-col>
              <el-col :span="6">
                <el-statistic title="接收数据" :value="messageCount" />
              </el-col>
              <el-col :span="6">
                <el-statistic title="窗口数据量" :value="latestData.count || 0" />
              </el-col>
              <el-col :span="6">
                <div class="stat-label">数据时间</div>
                <div class="stat-value">{{ formatTime(latestData.dataTimestamp) }}</div>
              </el-col>
            </el-row>
          </div>
        </el-card>

        <!-- 数据日志 -->
        <el-card class="section-card">
          <template #header>
            <div class="card-header">
              <span>数据接收日志</span>
              <el-tag type="info">最近 {{ streamLog.length }} 条</el-tag>
            </div>
          </template>
          <el-table :data="streamLog" stripe size="small" max-height="200">
            <el-table-column type="index" width="50" />
            <el-table-column prop="time" label="接收时间" width="150" />
            <el-table-column prop="value" label="数值" width="100">
              <template #default="scope">
                <span :style="{ color: scope.row.value > 80 ? '#f56c6c' : '#409eff', fontWeight: 'bold' }">
                  {{ scope.row.value?.toFixed ? scope.row.value.toFixed(2) : '--' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="count" label="窗口量" width="80" />
            <el-table-column prop="status" label="状态" width="80">
              <template #default="scope">
                <el-tag size="small" :type="scope.row.value ? 'success' : 'warning'">
                  {{ scope.row.value ? '正常' : '无数据' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 右侧：窗口统计 -->
      <el-col :span="10">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>滑动窗口统计</span>
              <el-button size="small" @click="loadWindowStats">刷新</el-button>
            </div>
          </template>
          <el-form inline style="margin-bottom: 15px">
            <el-form-item label="窗口">
              <el-select v-model="windowMinutes" style="width: 100px" @change="loadWindowStats">
                <el-option label="1分钟" :value="1" />
                <el-option label="5分钟" :value="5" />
                <el-option label="15分钟" :value="15" />
                <el-option label="30分钟" :value="30" />
              </el-select>
            </el-form-item>
          </el-form>

          <div v-if="windowStats" class="window-stats">
            <el-row :gutter="15">
              <el-col :span="12">
                <el-statistic title="窗口均值" :value="windowStats.avg || 0" :precision="2" />
              </el-col>
              <el-col :span="12">
                <el-statistic title="窗口最大" :value="windowStats.max || 0" :precision="2" />
              </el-col>
              <el-col :span="12" style="margin-top: 15px">
                <el-statistic title="窗口最小" :value="windowStats.min || 0" :precision="2" />
              </el-col>
              <el-col :span="12" style="margin-top: 15px">
                <el-statistic title="窗口数据量" :value="windowStats.count || 0" />
              </el-col>
            </el-row>
            <div v-if="windowStats.latestTimestamp" style="margin-top: 15px; color: #909399; font-size: 13px">
              最新数据时间: {{ formatTime(windowStats.latestTimestamp) }}
            </div>
          </div>
          <el-empty v-else description="点击刷新获取窗口统计" />
        </el-card>

        <!-- 连接信息 -->
        <el-card class="section-card">
          <template #header>
            <div class="card-header">
              <span>连接信息</span>
              <el-button size="small" @click="loadConnections">刷新</el-button>
            </div>
          </template>
          <div v-if="connectionInfo">
            <el-descriptions :column="1" size="small" border>
              <el-descriptions-item label="活跃连接数">
                <el-tag type="primary">{{ connectionInfo.activeCount }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="连接列表">
                <div v-for="conn in connectionInfo.connections" :key="conn" style="margin: 2px 0">
                  <el-tag size="small" type="success">{{ conn }}</el-tag>
                </div>
                <span v-if="!connectionInfo.connections?.length" style="color: #909399">无活跃连接</span>
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { serviceApi } from '../../api/request'
import * as echarts from 'echarts'

const connected = ref(false)
const messageCount = ref(0)
const latestData = ref(null)
const streamLog = ref([])
const windowMinutes = ref(5)
const windowStats = ref(null)
const connectionInfo = ref(null)
const realtimeChartRef = ref(null)
let realtimeChartInstance = null
let eventSource = null

// 实时图表数据缓冲
const realtimeValues = ref([])
const realtimeTimes = ref([])
const MAX_POINTS = 50

const streamForm = reactive({
  deviceId: 'EQ-001',
  sensorType: 'temperature',
  interval: 3000
})

const formatTime = (ts) => {
  if (!ts) return '--'
  const d = new Date(ts)
  if (isNaN(d.getTime())) return ts
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const startStream = () => {
  const url = `/api/service/stream/subscribe?deviceId=${streamForm.deviceId}&sensorType=${streamForm.sensorType}&interval=${streamForm.interval}`
  eventSource = new EventSource(url)

  eventSource.addEventListener('data', (event) => {
    const data = JSON.parse(event.data)
    latestData.value = data
    messageCount.value++

    // 更新日志
    const now = new Date()
    const pad = (n) => String(n).padStart(2, '0')
    streamLog.value.unshift({
      time: `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`,
      value: data.value,
      count: data.count
    })
    if (streamLog.value.length > 30) streamLog.value.pop()

    // 更新实时图表
    if (data.value != null) {
      realtimeTimes.value.push(formatTime(data.timestamp))
      realtimeValues.value.push(data.value)
      if (realtimeTimes.value.length > MAX_POINTS) {
        realtimeTimes.value.shift()
        realtimeValues.value.shift()
      }
      updateRealtimeChart()
    }
  })

  eventSource.onerror = () => {
    connected.value = false
    ElMessage.warning('数据流连接断开')
    eventSource.close()
    eventSource = null
  }

  eventSource.onopen = () => {
    connected.value = true
    ElMessage.success('数据流连接成功')
    nextTick(() => initRealtimeChart())
  }
}

const stopStream = () => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  connected.value = false
  ElMessage.info('已断开数据流')
}

const initRealtimeChart = () => {
  if (realtimeChartRef.value && !realtimeChartInstance) {
    realtimeChartInstance = echarts.init(realtimeChartRef.value)
  }
}

const updateRealtimeChart = () => {
  if (!realtimeChartInstance) return
  realtimeChartInstance.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '5%', right: '3%', top: '8%', bottom: '8%', containLabel: true },
    xAxis: { type: 'category', data: realtimeTimes.value, axisLabel: { color: '#606266' }, boundaryGap: false },
    yAxis: { type: 'value', axisLabel: { color: '#909399' }, splitLine: { lineStyle: { color: '#e4e7ed', type: 'dashed' } } },
    series: [{
      name: '实时值',
      type: 'line',
      data: realtimeValues.value,
      smooth: true,
      symbol: 'circle',
      symbolSize: 4,
      lineStyle: { width: 2, color: '#409eff' },
      itemStyle: { color: '#409eff' },
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(64,158,255,0.3)' }, { offset: 1, color: 'rgba(64,158,255,0.02)' }]) }
    }],
    animation: false
  })
}

const loadWindowStats = async () => {
  try {
    const res = await serviceApi.streamWindowStats({
      deviceId: streamForm.deviceId,
      sensorType: streamForm.sensorType,
      windowMinutes: windowMinutes.value
    })
    if (res.status === 'success') {
      windowStats.value = res
    }
  } catch (e) {
    ElMessage.error('获取窗口统计失败: ' + e.message)
  }
}

const loadConnections = async () => {
  try {
    const res = await serviceApi.streamConnections()
    if (res.status === 'success') {
      connectionInfo.value = res
    }
  } catch (e) {
    console.error('获取连接信息失败:', e)
  }
}

const resizeHandler = () => { realtimeChartInstance?.resize() }

onMounted(() => {
  window.addEventListener('resize', resizeHandler)
  loadConnections()
  loadWindowStats()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeHandler)
  stopStream()
  realtimeChartInstance?.dispose()
})
</script>

<style scoped>
.service-stream {
  padding: 20px;
}
.desc {
  color: #909399;
  margin-bottom: 20px;
}
.section-card {
  margin-top: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.chart-container {
  width: 100%;
  height: 300px;
}
.latest-data {
  margin-top: 15px;
  padding: 15px;
  background: #f5f7fa;
  border-radius: 6px;
}
.window-stats {
  padding: 10px;
}
.stat-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.stat-value {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
</style>
