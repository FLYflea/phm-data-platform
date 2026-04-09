<template>
  <div class="storage-timeseries">
    <div class="page-header">
      <h2><el-icon><DataLine /></el-icon> 时序数据查询</h2>
      <p class="desc">存储层核心功能：多维时序数据存储与查询（列分解存储模型）</p>
    </div>

    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span><el-icon><Search /></el-icon> 查询条件</span>
        </div>
      </template>
      <el-form :model="queryForm" inline>
        <el-form-item label="设备ID">
          <el-select v-model="queryForm.deviceId" placeholder="选择设备" style="width: 150px">
            <el-option v-for="device in deviceList" :key="device" :label="device" :value="device" />
          </el-select>
        </el-form-item>
        <el-form-item label="传感器类型">
          <el-select v-model="queryForm.sensorType" placeholder="全部" clearable style="width: 150px">
            <el-option label="全部" value="" />
            <el-option v-for="type in sensorTypeList" :key="type" :label="type" :value="type" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker
            v-model="queryForm.startTime"
            type="datetime"
            placeholder="开始时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker
            v-model="queryForm.endTime"
            type="datetime"
            placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="queryData" :loading="loading">查询</el-button>
          <el-button type="success" @click="queryAggregate" :loading="aggLoading">小时聚合</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 统计卡片 -->
    <el-row v-if="dataList.length > 0" :gutter="15" class="stats-row">
      <el-col :span="6">
        <el-statistic title="数据条数" :value="dataList.length" />
      </el-col>
      <el-col :span="6">
        <el-statistic title="均值" :value="stats.mean" :precision="2" />
      </el-col>
      <el-col :span="6">
        <el-statistic title="最大值" :value="stats.max" :precision="2" />
      </el-col>
      <el-col :span="6">
        <el-statistic title="最小值" :value="stats.min" :precision="2" />
      </el-col>
    </el-row>

    <!-- ECharts 时序曲线图 -->
    <el-card v-if="dataList.length > 0" class="result-card">
      <template #header>
        <div class="card-header">
          <span>时序曲线图</span>
          <el-tag type="info">{{ queryForm.deviceId }} - {{ queryForm.sensorType || '全部传感器' }}</el-tag>
        </div>
      </template>
      <div ref="chartRef" class="chart-container"></div>
    </el-card>

    <!-- 聚合统计结果 -->
    <el-card v-if="aggregations.length > 0" class="result-card">
      <template #header>
        <div class="card-header">
          <span>小时聚合统计</span>
          <el-tag type="success">{{ aggregations.length }} 个时段</el-tag>
        </div>
      </template>
      <el-table :data="aggregations" stripe size="small">
        <el-table-column prop="hour" label="时段" width="180">
          <template #default="scope">
            {{ formatTime(scope.row.hour) }}
          </template>
        </el-table-column>
        <el-table-column prop="avg_value" label="平均值" width="120">
          <template #default="scope">{{ scope.row.avg_value?.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="max_value" label="最大值" width="120">
          <template #default="scope">{{ scope.row.max_value?.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="min_value" label="最小值" width="120">
          <template #default="scope">{{ scope.row.min_value?.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="count" label="数据条数" width="100" />
      </el-table>
    </el-card>

    <!-- 数据表格 -->
    <el-card v-if="dataList.length > 0" class="result-card">
      <template #header>
        <div class="card-header">
          <span>数据明细</span>
          <el-tag type="primary">{{ dataList.length }} 条</el-tag>
        </div>
      </template>
      <el-table :data="dataList.slice(0, 100)" stripe size="small">
        <el-table-column prop="deviceId" label="设备ID" width="100" />
        <el-table-column prop="sensorType" label="传感器" width="100" />
        <el-table-column prop="value" label="数值" width="100">
          <template #default="scope">
            {{ scope.row.value?.toFixed ? scope.row.value.toFixed(2) : scope.row.value }}
          </template>
        </el-table-column>
        <el-table-column prop="timestamp" label="时间戳" width="180">
          <template #default="scope">{{ formatTime(scope.row.timestamp) }}</template>
        </el-table-column>
        <el-table-column prop="mean" label="均值" width="80">
          <template #default="scope">{{ scope.row.mean?.toFixed ? scope.row.mean.toFixed(2) : '-' }}</template>
        </el-table-column>
        <el-table-column prop="std" label="标准差" width="80">
          <template #default="scope">{{ scope.row.std?.toFixed ? scope.row.std.toFixed(2) : '-' }}</template>
        </el-table-column>
        <el-table-column prop="rms" label="RMS" width="80">
          <template #default="scope">{{ scope.row.rms?.toFixed ? scope.row.rms.toFixed(2) : '-' }}</template>
        </el-table-column>
        <el-table-column prop="peak" label="峰值" width="80">
          <template #default="scope">{{ scope.row.peak?.toFixed ? scope.row.peak.toFixed(2) : '-' }}</template>
        </el-table-column>
      </el-table>
      <div v-if="dataList.length > 100" class="more-data">
        <el-text type="info">显示前100条，共 {{ dataList.length }} 条数据</el-text>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { DataLine, Search } from '@element-plus/icons-vue'
import { storageApi } from '../../api/request'
import * as echarts from 'echarts'

const loading = ref(false)
const aggLoading = ref(false)
const dataList = ref([])
const aggregations = ref([])
const chartRef = ref(null)
let chart = null

// 动态设备和传感器类型列表
const deviceList = ref([])
const sensorTypeList = ref([])

// 格式化为本地时间字符串 YYYY-MM-DDTHH:mm:ss（与 date-picker value-format 一致）
const formatLocalTime = (date) => {
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const queryForm = reactive({
  deviceId: '',
  sensorType: '',
  startTime: formatLocalTime(new Date(Date.now() - 7 * 24 * 60 * 60 * 1000)),
  endTime: formatLocalTime(new Date())
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
    // 设置默认值
    if (deviceList.value.length > 0 && !queryForm.deviceId) {
      queryForm.deviceId = deviceList.value[0]
    }
  } catch (e) {
    console.warn('加载设备/传感器列表失败，使用默认值', e)
    deviceList.value = ['EQ-001', 'EQ-002', 'EQ-003']
    sensorTypeList.value = ['temperature', 'vibration', 'pressure', 'current']
    queryForm.deviceId = 'EQ-001'
  }
}

onMounted(() => {
  loadDevicesAndSensorTypes()
})

// 统计计算
const stats = computed(() => {
  if (dataList.value.length === 0) return { mean: 0, max: 0, min: 0 }
  const values = dataList.value.map(d => d.value).filter(v => v != null)
  return {
    mean: values.reduce((a, b) => a + b, 0) / values.length,
    max: Math.max(...values),
    min: Math.min(...values)
  }
})

const formatTime = (ts) => {
  if (!ts) return '-'
  const d = new Date(ts)
  return isNaN(d.getTime()) ? ts : d.toLocaleString()
}

onBeforeUnmount(() => {
  if (chart) {
    chart.dispose()
    chart = null
  }
})

const buildQueryParams = () => {
  const params = {
    deviceId: queryForm.deviceId,
    start: new Date(queryForm.startTime).toISOString(),
    end: new Date(queryForm.endTime).toISOString()
  }
  if (queryForm.sensorType) {
    params.sensorType = queryForm.sensorType
  }
  return params
}

const queryData = async () => {
  loading.value = true
  aggregations.value = []
  try {
    const res = await storageApi.queryTimeSeries(buildQueryParams())
    dataList.value = res.data || []
    ElMessage.success(`查询到 ${dataList.value.length} 条数据`)

    // 渲染图表
    await nextTick()
    renderChart()
  } catch (error) {
    ElMessage.error('查询失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

const queryAggregate = async () => {
  aggLoading.value = true
  try {
    const res = await storageApi.aggregateTimeSeries(buildQueryParams())
    aggregations.value = res.aggregations || []
    if (aggregations.value.length === 0) {
      ElMessage.warning('无聚合数据')
    } else {
      ElMessage.success(`获取 ${aggregations.value.length} 个时段聚合数据`)
    }
  } catch (error) {
    ElMessage.error('聚合查询失败: ' + error.message)
  } finally {
    aggLoading.value = false
  }
}

// 渲染ECharts时序曲线
const renderChart = () => {
  if (!chartRef.value || dataList.value.length === 0) return

  if (chart) chart.dispose()
  chart = echarts.init(chartRef.value)

  // 按传感器类型分组
  const groups = {}
  const sorted = [...dataList.value].sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp))
  sorted.forEach(d => {
    const key = d.sensorType || 'unknown'
    if (!groups[key]) groups[key] = []
    groups[key].push(d)
  })

  const sensorNameMap = {
    temperature: '温度', vibration: '振动', pressure: '压力', current: '电流'
  }
  const colorMap = {
    temperature: '#F56C6C', vibration: '#409EFF', pressure: '#67C23A', current: '#E6A23C'
  }

  const series = Object.entries(groups).map(([type, data]) => ({
    name: sensorNameMap[type] || type,
    type: 'line',
    smooth: true,
    symbol: 'circle',
    symbolSize: 4,
    data: data.map(d => [new Date(d.timestamp).getTime(), d.value]),
    lineStyle: { color: colorMap[type] || '#909399', width: 2 },
    itemStyle: { color: colorMap[type] || '#909399' }
  }))

  chart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        let html = `<b>${new Date(params[0].value[0]).toLocaleString()}</b><br/>`
        params.forEach(p => {
          html += `${p.marker} ${p.seriesName}: <b>${p.value[1]?.toFixed(2)}</b><br/>`
        })
        return html
      }
    },
    legend: { top: 5 },
    grid: { left: 60, right: 30, top: 40, bottom: 30 },
    xAxis: { type: 'time' },
    yAxis: { type: 'value', name: '数值' },
    dataZoom: [{ type: 'inside' }, { type: 'slider', height: 20, bottom: 5 }],
    series
  })
}
</script>

<style scoped>
.storage-timeseries {
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

.stats-row {
  margin-top: 20px;
  text-align: center;
}
.result-card {
  margin-top: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.chart-container {
  height: 350px;
  width: 100%;
}
.more-data {
  text-align: center;
  padding: 10px;
}
</style>
