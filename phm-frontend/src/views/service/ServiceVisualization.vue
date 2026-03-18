<template>
  <div class="service-visualization">
    <h2>数据可视化服务</h2>
    <p class="desc">服务层核心功能：时序数据可视化展示（ECharts图表）</p>

    <!-- 查询条件 -->
    <el-card>
      <template #header>图表配置</template>
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
          <el-button type="primary" @click="loadChartData" :loading="loading">
            <el-icon><TrendCharts /></el-icon>
            加载图表
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 图表展示 -->
    <el-card v-if="chartData.xAxis?.length > 0" class="chart-card">
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

      <!-- ECharts 图表容器 -->
      <div ref="chartRef" class="chart-container"></div>

      <!-- 统计信息 -->
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

    <!-- 数据表格 -->
    <el-card v-if="tableData.length > 0" class="table-card">
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
    <el-card v-if="!loading && chartData.xAxis?.length === 0 && hasSearched" class="empty-card">
      <el-empty description="暂无数据，请调整查询条件">
        <template #image>
          <el-icon :size="60" color="#909399"><DataLine /></el-icon>
        </template>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { TrendCharts, DataLine } from '@element-plus/icons-vue'
import { serviceApi } from '../../api/request'
import * as echarts from 'echarts'

const loading = ref(false)
const hasSearched = ref(false)
const showAllData = ref(false)
const chartRef = ref(null)
const chartType = ref('line')
let chartInstance = null

const queryForm = reactive({
  deviceId: 'EQ-001',
  sensorType: 'temperature',
  timeRange: [
    new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString().slice(0, 19),
    new Date().toISOString().slice(0, 19)
  ]
})

const chartData = ref({
  xAxis: [],
  series: [],
  count: 0
})

const tableData = ref([])

const statistics = reactive({
  max: 0,
  min: 0,
  avg: 0,
  std: 0
})

// 显示的数据（限制条数）
const displayData = computed(() => {
  if (showAllData.value) return tableData.value
  return tableData.value.slice(0, 10)
})

// 初始化图表
const initChart = () => {
  if (chartRef.value && !chartInstance) {
    chartInstance = echarts.init(chartRef.value)
    window.addEventListener('resize', () => {
      chartInstance?.resize()
    })
  }
}

// 更新图表
const updateChart = () => {
  if (!chartInstance || !chartData.value.xAxis?.length) return

  const values = chartData.value.series?.[0]?.data || []
  const times = chartData.value.xAxis || []

  // 根据图表类型配置
  const seriesConfig = {
    line: {
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { width: 2 },
      itemStyle: { color: '#409eff' },
      areaStyle: null
    },
    bar: {
      type: 'bar',
      itemStyle: { 
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#409eff' },
          { offset: 1, color: '#79bbff' }
        ])
      },
      barMaxWidth: 40
    },
    scatter: {
      type: 'scatter',
      symbolSize: 8,
      itemStyle: { 
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#409eff' },
          { offset: 1, color: '#79bbff' }
        ])
      }
    },
    area: {
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { width: 2, color: '#409eff' },
      itemStyle: { color: '#409eff' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(64, 158, 255, 0.5)' },
          { offset: 1, color: 'rgba(64, 158, 255, 0.1)' }
        ])
      }
    }
  }

  const option = {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(50, 50, 50, 0.9)',
      borderColor: '#333',
      textStyle: { color: '#fff' },
      formatter: (params) => {
        const data = params[0]
        return `<div style="padding: 5px">
          <div style="margin-bottom: 5px">${data.name}</div>
          <div><strong>数值：</strong>${data.value.toFixed(2)}</div>
        </div>`
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '10%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: times.map(t => {
        const date = new Date(t)
        return `${date.getHours()}:${date.getMinutes().toString().padStart(2, '0')}`
      }),
      boundaryGap: chartType.value === 'bar',
      axisLine: { lineStyle: { color: '#dcdfe6' } },
      axisLabel: { color: '#606266', fontSize: 12 },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisLabel: { color: '#909399', fontSize: 12 },
      splitLine: { lineStyle: { color: '#e4e7ed', type: 'dashed' } },
      axisTick: { show: false }
    },
    series: [{
      name: '数值',
      data: values,
      ...seriesConfig[chartType.value]
    }],
    animation: true,
    animationDuration: 800,
    animationEasing: 'cubicOut'
  }

  chartInstance.setOption(option, true)
}

// 监听图表类型变化
watch(chartType, () => {
  nextTick(() => {
    updateChart()
  })
})

// 加载图表数据
const loadChartData = async () => {
  if (!queryForm.deviceId || !queryForm.sensorType || !queryForm.timeRange) {
    ElMessage.error('请填写完整的查询条件')
    return
  }

  loading.value = true
  hasSearched.value = true

  try {
    const params = {
      deviceId: queryForm.deviceId,
      sensorType: queryForm.sensorType,
      start: queryForm.timeRange[0] + 'Z',
      end: queryForm.timeRange[1] + 'Z'
    }

    const res = await serviceApi.getVisualizationData(params)

    if (res.status === 'success') {
      chartData.value = {
        xAxis: res.xAxis || [],
        series: res.series || [],
        count: res.count || 0
      }

      // 构建表格数据
      if (res.xAxis && res.series?.[0]?.data) {
        tableData.value = res.xAxis.map((time, index) => ({
          timestamp: time,
          value: res.series[0].data[index]
        }))

        // 计算统计值
        const values = res.series[0].data
        statistics.max = Math.max(...values)
        statistics.min = Math.min(...values)
        statistics.avg = values.reduce((a, b) => a + b, 0) / values.length
        const variance = values.reduce((sum, val) => sum + Math.pow(val - statistics.avg, 2), 0) / values.length
        statistics.std = Math.sqrt(variance)
      }

      ElMessage.success(`加载成功，共 ${res.count} 个数据点`)
      
      // 等待 DOM 更新后初始化图表
      await nextTick()
      initChart()
      updateChart()
    } else {
      ElMessage.warning(res.message || '暂无数据')
      chartData.value = { xAxis: [], series: [], count: 0 }
      tableData.value = []
    }
  } catch (error) {
    ElMessage.error('加载失败: ' + error.message)
    chartData.value = { xAxis: [], series: [], count: 0 }
    tableData.value = []
  } finally {
    loading.value = false
  }
}

// 获取数值样式类
const getValueClass = (value) => {
  const avg = statistics.avg
  if (value > avg * 1.2) return 'value-high'
  if (value < avg * 0.8) return 'value-low'
  return 'value-normal'
}

// 组件挂载时初始化
onMounted(() => {
  if (chartData.value.xAxis?.length > 0) {
    initChart()
  }
})
</script>

<style scoped>
.service-visualization {
  padding: 20px;
}

.desc {
  color: #909399;
  margin-bottom: 20px;
}

.chart-card,
.table-card,
.empty-card {
  margin-top: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chart-controls {
  display: flex;
  align-items: center;
}

/* 图表样式 */
.chart-container {
  width: 100%;
  height: 400px;
  padding: 10px;
}

/* 统计信息 */
.stats-section {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #e4e7ed;
}

/* 数值样式 */
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
