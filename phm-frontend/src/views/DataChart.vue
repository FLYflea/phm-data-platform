<template>
  <div class="data-chart">
    <h2>可视化图表</h2>
    
    <el-card class="chart-card">
      <template #header>
        <div class="card-header">
          <span>传感器数据趋势</span>
        </div>
      </template>
      
      <el-form :model="chartForm" inline>
        <el-form-item label="设备ID">
          <el-input v-model="chartForm.deviceId" placeholder="如 DEV-001" />
        </el-form-item>
        
        <el-form-item label="传感器类型">
          <el-select v-model="chartForm.sensorType" placeholder="选择类型">
            <el-option label="温度" value="temperature" />
            <el-option label="振动" value="vibration" />
            <el-option label="压力" value="pressure" />
            <el-option label="电流" value="current" />
          </el-select>
        </el-form-item>
        
        <el-form-item>
          <el-button type="primary" @click="loadChartData" :loading="loading">加载数据</el-button>
        </el-form-item>
      </el-form>
      
      <div ref="chartRef" class="chart-container"></div>
    </el-card>
    
    <el-card class="stats-card">
      <template #header>
        <div class="card-header">
          <span>统计信息</span>
        </div>
      </template>
      
      <el-row :gutter="20">
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ stats.count }}</div>
            <div class="stat-label">数据点数</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ stats.avg.toFixed(2) }}</div>
            <div class="stat-label">平均值</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ stats.max.toFixed(2) }}</div>
            <div class="stat-label">最大值</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ stats.min.toFixed(2) }}</div>
            <div class="stat-label">最小值</div>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { storageApi } from '../api/request'

const chartRef = ref(null)
const loading = ref(false)
let chartInstance = null

const chartForm = reactive({
  deviceId: 'DEV-001',
  sensorType: 'temperature'
})

const stats = reactive({
  count: 0,
  avg: 0,
  max: 0,
  min: 0
})

const initChart = () => {
  if (chartRef.value) {
    chartInstance = echarts.init(chartRef.value)
    
    const option = {
      title: {
        text: '传感器数据趋势',
        left: 'center'
      },
      tooltip: {
        trigger: 'axis',
        formatter: function(params) {
          const data = params[0]
          return `时间: ${data.name}<br/>数值: ${data.value.toFixed(2)}`
        }
      },
      xAxis: {
        type: 'category',
        data: [],
        axisLabel: {
          rotate: 45,
          formatter: function(value) {
            return new Date(value).toLocaleTimeString('zh-CN')
          }
        }
      },
      yAxis: {
        type: 'value',
        name: '数值'
      },
      series: [{
        data: [],
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        lineStyle: {
          width: 2
        },
        areaStyle: {
          opacity: 0.3
        }
      }],
      grid: {
        left: '3%',
        right: '4%',
        bottom: '15%',
        containLabel: true
      }
    }
    
    chartInstance.setOption(option)
  }
}

const loadChartData = async () => {
  if (!chartForm.deviceId) {
    ElMessage.warning('请输入设备ID')
    return
  }
  
  loading.value = true
  
  try {
    // 查询最近7天的数据
    const now = new Date()
    const weekAgo = new Date(now - 7 * 24 * 60 * 60 * 1000)
    
    const params = {
      deviceId: chartForm.deviceId,
      sensorType: chartForm.sensorType,
      start: weekAgo.toISOString(),
      end: now.toISOString()
    }
    
    const res = await storageApi.queryTimeSeries(params)
    const data = res.data || []
    
    if (data.length === 0) {
      ElMessage.info('暂无数据')
      loading.value = false
      return
    }
    
    // 按时间排序
    data.sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp))
    
    // 更新图表
    const times = data.map(item => item.timestamp)
    const values = data.map(item => item.value)
    
    chartInstance.setOption({
      xAxis: { data: times },
      series: [{ data: values }]
    })
    
    // 计算统计信息
    stats.count = data.length
    stats.avg = values.reduce((a, b) => a + b, 0) / values.length
    stats.max = Math.max(...values)
    stats.min = Math.min(...values)
    
    ElMessage.success(`加载成功，共 ${data.length} 条数据`)
  } catch (error) {
    ElMessage.error('加载失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

const handleResize = () => {
  if (chartInstance) {
    chartInstance.resize()
  }
}

onMounted(() => {
  initChart()
  loadChartData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (chartInstance) {
    chartInstance.dispose()
  }
})
</script>

<style scoped>
.data-chart {
  padding: 20px;
}

h2 {
  margin-bottom: 20px;
  color: #303133;
}

.chart-card {
  margin-bottom: 20px;
}

.card-header {
  font-weight: bold;
}

.chart-container {
  width: 100%;
  height: 400px;
  margin-top: 20px;
}

.stats-card {
  margin-top: 20px;
}

.stat-item {
  text-align: center;
  padding: 20px;
  background: #f5f7fa;
  border-radius: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #409eff;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 14px;
  color: #606266;
}
</style>
