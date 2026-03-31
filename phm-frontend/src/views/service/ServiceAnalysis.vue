<template>
  <div class="service-analysis">
    <h2>多维分析服务</h2>
    <p class="desc">服务层多维分析：按设备、传感器、时间等维度聚合分析，趋势检测与异常识别</p>

    <!-- 查询配置 -->
    <el-card>
      <template #header>分析配置</template>
      <el-form :model="queryForm" inline>
        <el-form-item label="设备ID">
          <el-select v-model="queryForm.deviceId" placeholder="选择设备" style="width: 150px">
            <el-option label="设备 EQ-001" value="EQ-001" />
            <el-option label="设备 EQ-002" value="EQ-002" />
            <el-option label="设备 EQ-003" value="EQ-003" />
          </el-select>
        </el-form-item>
        <el-form-item label="传感器类型">
          <el-select v-model="queryForm.sensorType" style="width: 140px">
            <el-option label="温度" value="temperature" />
            <el-option label="振动" value="vibration" />
            <el-option label="压力" value="pressure" />
            <el-option label="电流" value="current" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="queryForm.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="runAnalysis" :loading="loading">
            开始分析
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 统计概览 -->
    <el-card v-if="analysisLoaded" class="section-card">
      <template #header>
        <div class="card-header">
          <span>统计概览</span>
          <el-tag type="success">{{ stats.count }} 条数据, {{ stats.hourCount }} 个时段</el-tag>
        </div>
      </template>
      <el-row :gutter="20">
        <el-col :span="4">
          <el-statistic title="平均值" :value="stats.avg" :precision="2" />
        </el-col>
        <el-col :span="4">
          <el-statistic title="最大值" :value="stats.max" :precision="2">
            <template #suffix><span style="color:#f56c6c; font-size:12px">MAX</span></template>
          </el-statistic>
        </el-col>
        <el-col :span="4">
          <el-statistic title="最小值" :value="stats.min" :precision="2">
            <template #suffix><span style="color:#67c23a; font-size:12px">MIN</span></template>
          </el-statistic>
        </el-col>
        <el-col :span="4">
          <el-statistic title="标准差" :value="stats.std" :precision="2" />
        </el-col>
        <el-col :span="4">
          <el-statistic title="变异系数" :value="stats.cv" :precision="2">
            <template #suffix>%</template>
          </el-statistic>
        </el-col>
        <el-col :span="4">
          <el-statistic title="极差" :value="stats.range" :precision="2" />
        </el-col>
      </el-row>
    </el-card>

    <!-- 趋势分析图 -->
    <el-card v-if="analysisLoaded" class="section-card">
      <template #header>
        <div class="card-header">
          <span>趋势分析</span>
          <el-tag :type="trendType">{{ trendLabel }}</el-tag>
        </div>
      </template>
      <div ref="trendChartRef" class="chart-container"></div>
    </el-card>

    <!-- 数据分布直方图 -->
    <el-card v-if="analysisLoaded" class="section-card">
      <template #header>分布分析 — 直方图</template>
      <div ref="histChartRef" class="chart-container"></div>
    </el-card>

    <!-- 异常检测结果 -->
    <el-card v-if="analysisLoaded && anomalies.length > 0" class="section-card">
      <template #header>
        <div class="card-header">
          <span>异常检测</span>
          <el-tag type="danger">{{ anomalies.length }} 个异常点</el-tag>
        </div>
      </template>
      <el-table :data="anomalies" stripe size="small" max-height="250">
        <el-table-column type="index" width="50" />
        <el-table-column prop="timestamp" label="时间" width="200" />
        <el-table-column prop="value" label="数值" width="120">
          <template #default="scope">
            <span style="color: #f56c6c; font-weight: bold">{{ scope.row.value.toFixed(4) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="deviation" label="偏差倍数" width="120">
          <template #default="scope">
            {{ scope.row.deviation.toFixed(2) }} &sigma;
          </template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.value > stats.avg ? 'danger' : 'warning'" size="small">
              {{ scope.row.value > stats.avg ? '偏高' : '偏低' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="!loading && !analysisLoaded && hasSearched" class="section-card">
      <el-empty description="分析无结果，请调整条件后重试" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { serviceApi } from '../../api/request'
import * as echarts from 'echarts'

const loading = ref(false)
const hasSearched = ref(false)
const analysisLoaded = ref(false)

const trendChartRef = ref(null)
const histChartRef = ref(null)
let trendChartInstance = null
let histChartInstance = null

const trendLabel = ref('--')
const trendType = ref('info')
const anomalies = ref([])

const formatLocalTime = (date) => {
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const queryForm = reactive({
  deviceId: 'EQ-001',
  sensorType: 'temperature',
  timeRange: [
    formatLocalTime(new Date(Date.now() - 7 * 24 * 60 * 60 * 1000)),
    formatLocalTime(new Date())
  ]
})

const stats = reactive({ avg: 0, max: 0, min: 0, count: 0, hourCount: 0, std: 0, cv: 0, range: 0 })

const resizeHandler = () => {
  trendChartInstance?.resize()
  histChartInstance?.resize()
}

onMounted(() => { window.addEventListener('resize', resizeHandler) })
onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeHandler)
  trendChartInstance?.dispose()
  histChartInstance?.dispose()
})

const runAnalysis = async () => {
  loading.value = true
  hasSearched.value = true
  analysisLoaded.value = false

  try {
    const params = {
      deviceId: queryForm.deviceId,
      sensorType: queryForm.sensorType,
      start: new Date(queryForm.timeRange[0]).toISOString(),
      end: new Date(queryForm.timeRange[1]).toISOString()
    }

    // 获取时序原始数据 + 统计数据
    const [vizRes, statsRes] = await Promise.all([
      serviceApi.getVisualizationData(params),
      serviceApi.getStatistics(params)
    ])

    if (statsRes.status === 'success') {
      stats.avg = statsRes.avg || 0
      stats.max = statsRes.max || 0
      stats.min = statsRes.min || 0
      stats.count = statsRes.count || 0
      stats.hourCount = statsRes.hourCount || 0
    }

    if (vizRes.status === 'success' && vizRes.xAxis?.length > 0) {
      const values = vizRes.series?.[0]?.data || []
      const times = vizRes.xAxis || []

      // 计算额外统计
      const mean = values.reduce((a, b) => a + b, 0) / values.length
      const variance = values.reduce((s, v) => s + Math.pow(v - mean, 2), 0) / values.length
      stats.std = Math.sqrt(variance)
      stats.cv = mean !== 0 ? (stats.std / Math.abs(mean)) * 100 : 0
      stats.range = stats.max - stats.min
      stats.avg = Math.round(mean * 100) / 100

      // 趋势分析（线性回归斜率）
      const n = values.length
      let sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0
      for (let i = 0; i < n; i++) {
        sumX += i; sumY += values[i]; sumXY += i * values[i]; sumX2 += i * i
      }
      const slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
      if (Math.abs(slope) < 0.001) {
        trendLabel.value = '趋势平稳'
        trendType.value = 'success'
      } else if (slope > 0) {
        trendLabel.value = '上升趋势'
        trendType.value = 'warning'
      } else {
        trendLabel.value = '下降趋势'
        trendType.value = 'info'
      }

      // 异常检测（3-sigma）
      anomalies.value = []
      const threshold = 2.5
      for (let i = 0; i < values.length; i++) {
        const deviation = Math.abs(values[i] - mean) / (stats.std || 1)
        if (deviation > threshold) {
          const ts = new Date(times[i])
          const pad = (nn) => String(nn).padStart(2, '0')
          anomalies.value.push({
            timestamp: `${ts.getFullYear()}-${pad(ts.getMonth()+1)}-${pad(ts.getDate())} ${pad(ts.getHours())}:${pad(ts.getMinutes())}:${pad(ts.getSeconds())}`,
            value: values[i],
            deviation
          })
        }
      }

      analysisLoaded.value = true
      ElMessage.success(`分析完成，${values.length} 个数据点`)

      await nextTick()
      renderTrendChart(times, values, mean, slope)
      renderHistChart(values, mean, stats.std)
    } else {
      ElMessage.warning('暂无数据')
    }
  } catch (error) {
    ElMessage.error('分析失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

const renderTrendChart = (times, values, mean, slope) => {
  if (!trendChartRef.value) return
  if (!trendChartInstance) trendChartInstance = echarts.init(trendChartRef.value)

  // 趋势线
  const trendLine = values.map((_, i) => Math.round((mean + slope * (i - values.length / 2)) * 100) / 100)
  // 均值线
  const meanLine = values.map(() => Math.round(mean * 100) / 100)

  const xLabels = times.map(t => {
    const d = new Date(t)
    return `${d.getMonth()+1}/${d.getDate()} ${d.getHours()}:${d.getMinutes().toString().padStart(2, '0')}`
  })

  trendChartInstance.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['原始数据', '趋势线', '均值线'], bottom: 5 },
    grid: { left: '3%', right: '4%', bottom: '15%', top: '8%', containLabel: true },
    xAxis: { type: 'category', data: xLabels, axisLabel: { color: '#606266', rotate: 30 }, axisTick: { show: false } },
    yAxis: { type: 'value', axisLabel: { color: '#909399' }, splitLine: { lineStyle: { color: '#e4e7ed', type: 'dashed' } } },
    series: [
      { name: '原始数据', type: 'line', data: values, smooth: true, symbol: 'none', lineStyle: { width: 2, color: '#409eff' }, areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(64,158,255,0.3)' }, { offset: 1, color: 'rgba(64,158,255,0.02)' }]) } },
      { name: '趋势线', type: 'line', data: trendLine, smooth: false, symbol: 'none', lineStyle: { width: 2, color: '#e6a23c', type: 'dashed' } },
      { name: '均值线', type: 'line', data: meanLine, smooth: false, symbol: 'none', lineStyle: { width: 1, color: '#909399', type: 'dotted' } }
    ],
    animation: true, animationDuration: 800
  }, true)
}

const renderHistChart = (values, mean, std) => {
  if (!histChartRef.value) return
  if (!histChartInstance) histChartInstance = echarts.init(histChartRef.value)

  // 构建直方图
  const binCount = 20
  const minVal = Math.min(...values)
  const maxVal = Math.max(...values)
  const binWidth = (maxVal - minVal) / binCount || 1
  const bins = new Array(binCount).fill(0)
  const binLabels = []
  for (let i = 0; i < binCount; i++) {
    const lo = minVal + i * binWidth
    binLabels.push(lo.toFixed(1))
    for (const v of values) {
      if (v >= lo && v < lo + binWidth) bins[i]++
      else if (i === binCount - 1 && v === lo + binWidth) bins[i]++
    }
  }

  // 颜色标记异常区间
  const colors = bins.map((_, i) => {
    const center = minVal + (i + 0.5) * binWidth
    const dev = Math.abs(center - mean) / (std || 1)
    if (dev > 2.5) return '#f56c6c'
    if (dev > 2) return '#e6a23c'
    return '#409eff'
  })

  histChartInstance.setOption({
    tooltip: { trigger: 'axis', formatter: (p) => `区间 ${p[0].name}<br/>频次: ${p[0].value}` },
    grid: { left: '3%', right: '4%', bottom: '8%', top: '8%', containLabel: true },
    xAxis: { type: 'category', data: binLabels, axisLabel: { color: '#606266', rotate: 30 }, axisTick: { show: false } },
    yAxis: { type: 'value', name: '频次', axisLabel: { color: '#909399' }, splitLine: { lineStyle: { color: '#e4e7ed', type: 'dashed' } } },
    series: [{
      type: 'bar',
      data: bins.map((v, i) => ({ value: v, itemStyle: { color: colors[i] } })),
      barWidth: '80%'
    }],
    animation: true
  }, true)
}
</script>

<style scoped>
.service-analysis {
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
  height: 380px;
  padding: 10px;
}
</style>
