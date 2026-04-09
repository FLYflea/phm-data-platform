<template>
  <div class="service-visualization">
    <div class="page-header">
      <h2><el-icon><PieChart /></el-icon> 数据可视化服务</h2>
      <p class="desc">服务层数据可视化：基于图形语法的多类型图表模板库，SVG渲染，HTML5兼容</p>
    </div>

    <!-- 可视化模式选择 -->
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span><el-icon><TrendCharts /></el-icon> 可视化配置</span>
          <el-tag type="primary" effect="dark" round size="small">ECharts</el-tag>
        </div>
      </template>
      <el-form :model="queryForm" inline>
        <el-form-item label="图表模式">
          <el-radio-group v-model="vizMode" size="small">
            <el-radio-button label="timeseries">时序图表</el-radio-button>
            <el-radio-button label="pie">饼图分布</el-radio-button>
            <el-radio-button label="radar">雷达对比</el-radio-button>
            <el-radio-button label="heatmap">热力图</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="设备ID">
          <el-select v-model="queryForm.deviceId" placeholder="选择设备" style="width: 150px">
            <el-option v-for="device in deviceList" :key="device" :label="device" :value="device" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="vizMode === 'timeseries' || vizMode === 'heatmap'" label="传感器类型">
          <el-select v-model="queryForm.sensorType" style="width: 140px">
            <el-option v-for="type in sensorTypeList" :key="type" :label="type" :value="type" />
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
          <el-button type="primary" @click="loadChartData" :loading="loading">
            <el-icon><TrendCharts /></el-icon>
            加载图表
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 时序图表 -->
    <el-card v-if="vizMode === 'timeseries' && chartLoaded" shadow="hover" class="chart-card">
      <template #header>
        <div class="card-header">
          <span>时序数据可视化</span>
          <div class="chart-controls">
            <el-radio-group v-model="chartType" size="small">
              <el-radio-button label="line">折线图</el-radio-button>
              <el-radio-button label="bar">柱状图</el-radio-button>
              <el-radio-button label="scatter">散点图</el-radio-button>
              <el-radio-button label="area">面积图</el-radio-button>
            </el-radio-group>
            <el-tag type="success" style="margin-left: 15px">{{ chartData.count }} 个数据点</el-tag>
          </div>
        </div>
      </template>
      <div ref="chartRef" class="chart-container"></div>
      <div class="stats-section">
        <el-row :gutter="20">
          <el-col :span="6">
            <el-statistic title="最大值" :value="statistics.max" :precision="2" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="最小值" :value="statistics.min" :precision="2" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="平均值" :value="statistics.avg" :precision="2" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="标准差" :value="statistics.std" :precision="2" />
          </el-col>
        </el-row>
      </div>
    </el-card>

    <!-- 饼图 -->
    <el-card v-if="vizMode === 'pie' && chartLoaded" shadow="hover" class="chart-card">
      <template #header>
        <div class="card-header">
          <span><el-icon><PieChart /></el-icon> 传感器数据分布 — 饼图</span>
          <el-tag type="info">按类型统计数据量</el-tag>
        </div>
      </template>
      <div ref="pieChartRef" class="chart-container"></div>
    </el-card>

    <!-- 雷达图 -->
    <el-card v-if="vizMode === 'radar' && chartLoaded" shadow="hover" class="chart-card">
      <template #header>
        <div class="card-header">
          <span><el-icon><DataAnalysis /></el-icon> 多维特征对比 — 雷达图</span>
          <el-tag type="info">多传感器统计指标对比</el-tag>
        </div>
      </template>
      <div ref="radarChartRef" class="chart-container"></div>
    </el-card>

    <!-- 热力图 -->
    <el-card v-if="vizMode === 'heatmap' && chartLoaded" shadow="hover" class="chart-card">
      <template #header>
        <div class="card-header">
          <span><el-icon><DataLine /></el-icon> 时段热力分布 — 热力图</span>
          <el-tag type="info">按日期/小时聚合</el-tag>
        </div>
      </template>
      <div ref="heatmapChartRef" class="chart-container-large"></div>
    </el-card>

    <!-- 数据表格（时序模式） -->
    <el-card v-if="vizMode === 'timeseries' && tableData.length > 0" shadow="hover" class="table-card">
      <template #header>
        <div class="card-header">
          <span>数据明细</span>
          <el-button link @click="showAllData = !showAllData">
            {{ showAllData ? '收起' : '显示全部' }}
          </el-button>
        </div>
      </template>
      <el-table :data="displayData" stripe size="small" max-height="300">
        <el-table-column type="index" width="50" />
        <el-table-column prop="timestamp" label="时间戳" width="180" />
        <el-table-column prop="value" label="数值" width="120">
          <template #default="scope">
            <span :class="getValueClass(scope.row.value)">
              {{ scope.row.value?.toFixed ? scope.row.value.toFixed(4) : scope.row.value }}
            </span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 空状态 -->
    <el-card v-if="!loading && !chartLoaded && hasSearched" shadow="hover" class="empty-card">
      <el-empty description="暂无数据，请调整查询条件">
        <template #image>
          <el-icon :size="60" color="#909399"><DataLine /></el-icon>
        </template>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { TrendCharts, DataLine, PieChart, DataAnalysis } from '@element-plus/icons-vue'
import { serviceApi, storageApi } from '../../api/request'
import * as echarts from 'echarts'

const loading = ref(false)
const hasSearched = ref(false)
const chartLoaded = ref(false)
const showAllData = ref(false)
const vizMode = ref('timeseries')
const chartType = ref('line')

const chartRef = ref(null)
const pieChartRef = ref(null)
const radarChartRef = ref(null)
const heatmapChartRef = ref(null)

let chartInstance = null
let pieChartInstance = null
let radarChartInstance = null
let heatmapChartInstance = null

// 动态设备和传感器类型列表
const deviceList = ref([])
const sensorTypeList = ref([])

const formatLocalTime = (date) => {
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const queryForm = reactive({
  deviceId: '',
  sensorType: '',
  timeRange: [
    formatLocalTime(new Date(Date.now() - 7 * 24 * 60 * 60 * 1000)),
    formatLocalTime(new Date())
  ]
})

// 加载设备和传感器类型列表
const loadDevicesAndSensorTypes = async () => {
  try {
    const [devicesRes, sensorTypesRes] = await Promise.all([
      storageApi.getAllDevices(),
      storageApi.getAllSensorTypes()
    ])
    deviceList.value = devicesRes.devices || []
    sensorTypeList.value = sensorTypesRes.sensorTypes || []
    if (deviceList.value.length > 0 && !queryForm.deviceId) {
      queryForm.deviceId = deviceList.value[0]
    }
    if (sensorTypeList.value.length > 0 && !queryForm.sensorType) {
      queryForm.sensorType = sensorTypeList.value[0]
    }
  } catch (e) {
    console.warn('加载设备/传感器列表失败，使用默认值', e)
    deviceList.value = ['EQ-001', 'EQ-002', 'EQ-003']
    sensorTypeList.value = ['temperature', 'vibration', 'pressure', 'current']
    queryForm.deviceId = 'EQ-001'
    queryForm.sensorType = 'temperature'
  }
}

const chartData = ref({ xAxis: [], series: [], count: 0 })
const tableData = ref([])
const statistics = reactive({ max: 0, min: 0, avg: 0, std: 0 })

const displayData = computed(() => {
  if (showAllData.value) return tableData.value
  return tableData.value.slice(0, 10)
})

const resizeHandler = () => {
  chartInstance?.resize()
  pieChartInstance?.resize()
  radarChartInstance?.resize()
  heatmapChartInstance?.resize()
}

onMounted(() => {
  loadDevicesAndSensorTypes()
  window.addEventListener('resize', resizeHandler)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeHandler)
  chartInstance?.dispose()
  pieChartInstance?.dispose()
  radarChartInstance?.dispose()
  heatmapChartInstance?.dispose()
})

// 切换可视化模式时重置状态
watch(vizMode, () => {
  chartLoaded.value = false
  hasSearched.value = false
})

// 监听时序图表类型变化
watch(chartType, () => {
  nextTick(() => updateTimeseriesChart())
})

const getTimeParams = () => ({
  deviceId: queryForm.deviceId,
  start: new Date(queryForm.timeRange[0]).toISOString(),
  end: new Date(queryForm.timeRange[1]).toISOString()
})

// ========== 加载数据 ==========
const loadChartData = async () => {
  loading.value = true
  hasSearched.value = true
  chartLoaded.value = false

  try {
    if (vizMode.value === 'timeseries') {
      await loadTimeseries()
    } else if (vizMode.value === 'pie') {
      await loadPieChart()
    } else if (vizMode.value === 'radar') {
      await loadRadarChart()
    } else if (vizMode.value === 'heatmap') {
      await loadHeatmapChart()
    }
  } catch (error) {
    ElMessage.error('加载失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

// ========== 时序图 ==========
const loadTimeseries = async () => {
  const params = { ...getTimeParams(), sensorType: queryForm.sensorType }
  const res = await serviceApi.getVisualizationData(params)

  if (res.status === 'success' && res.xAxis?.length > 0) {
    chartData.value = { xAxis: res.xAxis || [], series: res.series || [], count: res.count || 0 }

    if (res.xAxis && res.series?.[0]?.data) {
      tableData.value = res.xAxis.map((time, index) => ({
        timestamp: time,
        value: res.series[0].data[index]
      }))
      const values = res.series[0].data
      statistics.max = Math.max(...values)
      statistics.min = Math.min(...values)
      statistics.avg = values.reduce((a, b) => a + b, 0) / values.length
      const variance = values.reduce((sum, val) => sum + Math.pow(val - statistics.avg, 2), 0) / values.length
      statistics.std = Math.sqrt(variance)
    }

    chartLoaded.value = true
    ElMessage.success(`加载成功，共 ${res.count} 个数据点`)
    await nextTick()
    if (chartRef.value) {
      if (!chartInstance) chartInstance = echarts.init(chartRef.value)
      updateTimeseriesChart()
    }
  } else {
    ElMessage.warning('暂无数据')
    chartData.value = { xAxis: [], series: [], count: 0 }
    tableData.value = []
  }
}

const updateTimeseriesChart = () => {
  if (!chartInstance || !chartData.value.xAxis?.length) return
  const values = chartData.value.series?.[0]?.data || []
  const times = chartData.value.xAxis || []

  const seriesConfig = {
    line: { type: 'line', smooth: true, symbol: 'circle', symbolSize: 6, lineStyle: { width: 2 }, itemStyle: { color: '#409eff' }, areaStyle: null },
    bar: { type: 'bar', itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: '#409eff' }, { offset: 1, color: '#79bbff' }]) }, barMaxWidth: 40 },
    scatter: { type: 'scatter', symbolSize: 8, itemStyle: { color: '#409eff' } },
    area: { type: 'line', smooth: true, symbol: 'circle', symbolSize: 6, lineStyle: { width: 2, color: '#409eff' }, itemStyle: { color: '#409eff' }, areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(64, 158, 255, 0.5)' }, { offset: 1, color: 'rgba(64, 158, 255, 0.1)' }]) } }
  }

  chartInstance.setOption({
    tooltip: { trigger: 'axis', backgroundColor: 'rgba(50,50,50,0.9)', borderColor: '#333', textStyle: { color: '#fff' } },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '10%', containLabel: true },
    xAxis: {
      type: 'category',
      data: times.map(t => { const d = new Date(t); return `${d.getHours()}:${d.getMinutes().toString().padStart(2, '0')}` }),
      boundaryGap: chartType.value === 'bar',
      axisLine: { lineStyle: { color: '#dcdfe6' } }, axisLabel: { color: '#606266' }, axisTick: { show: false }
    },
    yAxis: { type: 'value', axisLine: { show: false }, axisLabel: { color: '#909399' }, splitLine: { lineStyle: { color: '#e4e7ed', type: 'dashed' } }, axisTick: { show: false } },
    series: [{ name: '数值', data: values, ...seriesConfig[chartType.value] }],
    animation: true, animationDuration: 800, animationEasing: 'cubicOut'
  }, true)
}

// ========== 饼图 ==========
const loadPieChart = async () => {
  const params = getTimeParams()
  const res = await serviceApi.getPieData(params)

  if (res.status === 'success' && res.data?.length > 0) {
    chartLoaded.value = true
    await nextTick()
    if (pieChartRef.value) {
      if (!pieChartInstance) pieChartInstance = echarts.init(pieChartRef.value)

      const colors = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c']
      pieChartInstance.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
        legend: { orient: 'vertical', left: 'left', data: res.data.map(d => d.name) },
        series: [{
          name: '传感器分布',
          type: 'pie',
          radius: ['35%', '65%'],
          center: ['55%', '50%'],
          avoidLabelOverlap: true,
          itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
          label: { show: true, formatter: '{b}\n{d}%' },
          emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' }, itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0, 0, 0, 0.5)' } },
          data: res.data.map((d, i) => ({ ...d, itemStyle: { color: colors[i % colors.length] } }))
        }],
        animation: true, animationDuration: 800
      }, true)

      ElMessage.success('饼图加载成功')
    }
  } else {
    ElMessage.warning('暂无数据')
  }
}

// ========== 雷达图 ==========
const loadRadarChart = async () => {
  const params = getTimeParams()
  const res = await serviceApi.getRadarData(params)

  if (res.status === 'success' && res.data?.length > 0) {
    chartLoaded.value = true
    await nextTick()
    if (radarChartRef.value) {
      if (!radarChartInstance) radarChartInstance = echarts.init(radarChartRef.value)

      const colors = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c']
      radarChartInstance.setOption({
        tooltip: { trigger: 'item' },
        legend: { data: res.data.map(d => d.name), bottom: 10 },
        radar: {
          indicator: res.indicators || [],
          shape: 'polygon',
          splitNumber: 5,
          axisName: { color: '#606266' },
          splitLine: { lineStyle: { color: '#e4e7ed' } },
          splitArea: { areaStyle: { color: ['rgba(64, 158, 255, 0.05)', 'rgba(64, 158, 255, 0.1)'] } }
        },
        series: [{
          type: 'radar',
          data: res.data.map((d, i) => ({
            name: d.name,
            value: d.value,
            lineStyle: { color: colors[i % colors.length], width: 2 },
            itemStyle: { color: colors[i % colors.length] },
            areaStyle: { color: colors[i % colors.length].replace(')', ', 0.15)').replace('rgb', 'rgba') }
          }))
        }],
        animation: true, animationDuration: 800
      }, true)

      ElMessage.success('雷达图加载成功')
    }
  } else {
    ElMessage.warning('暂无数据')
  }
}

// ========== 热力图 ==========
const loadHeatmapChart = async () => {
  const params = { ...getTimeParams(), sensorType: queryForm.sensorType }
  const res = await serviceApi.getHeatmapData(params)

  if (res.status === 'success' && res.data?.length > 0) {
    chartLoaded.value = true
    await nextTick()
    if (heatmapChartRef.value) {
      if (!heatmapChartInstance) heatmapChartInstance = echarts.init(heatmapChartRef.value)

      heatmapChartInstance.setOption({
        tooltip: {
          position: 'top',
          formatter: (p) => `${res.xAxis[p.value[0]]} ${res.yAxis[p.value[1]]}<br/>均值: ${p.value[2]}`
        },
        grid: { left: '8%', right: '12%', top: '8%', bottom: '12%' },
        xAxis: { type: 'category', data: res.xAxis, splitArea: { show: true }, axisLabel: { color: '#606266' } },
        yAxis: { type: 'category', data: res.yAxis, splitArea: { show: true }, axisLabel: { color: '#606266' } },
        visualMap: {
          min: 0, max: res.maxValue || 100, calculable: true,
          orient: 'vertical', right: '2%', top: 'center',
          inRange: { color: ['#e0f3ff', '#409eff', '#1e3c72'] }
        },
        series: [{
          name: '热力值',
          type: 'heatmap',
          data: res.data,
          label: { show: res.data.length < 200, formatter: (p) => p.value[2] },
          emphasis: { itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0, 0, 0, 0.5)' } }
        }],
        animation: true
      }, true)

      ElMessage.success('热力图加载成功')
    }
  } else {
    ElMessage.warning('暂无数据')
  }
}

const getValueClass = (value) => {
  const avg = statistics.avg
  if (value > avg * 1.2) return 'value-high'
  if (value < avg * 0.8) return 'value-low'
  return 'value-normal'
}
</script>

<style scoped>
.service-visualization {
  padding: 0;
}
.page-header {
  margin-bottom: 24px;
}
.page-header h2 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 600;
  color: #1a1a2e;
}
.desc {
  color: #909399;
  margin: 0;
  font-size: 14px;
}
.chart-card, .table-card, .empty-card {
  margin-top: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-header span {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}
.chart-controls {
  display: flex;
  align-items: center;
}
.chart-container {
  width: 100%;
  height: 400px;
  padding: 10px;
}
.chart-container-large {
  width: 100%;
  height: 500px;
  padding: 10px;
}
.stats-section {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #e4e7ed;
}
.value-high {
  color: #f56c6c;
  font-weight: bold;
}
.value-low {
  color: #67c23a;
  font-weight: bold;
}
.value-normal {
  color: #409eff;
}
</style>
