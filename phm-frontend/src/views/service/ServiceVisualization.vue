<template>
  <div class="service-visualization">
    <h2>数据可视化服务</h2>
    <p class="desc">服务层核心功能：时序数据可视化展示（ECharts图表）</p>

    <!-- 查询条件 -->
    <el-card>
      <template #header>图表配置</template>
      <el-form :model="queryForm" inline>
        <el-form-item label="设备ID">
          <el-input v-model="queryForm.deviceId" placeholder="EQ-001" />
        </el-form-item>
        <el-form-item label="传感器类型">
          <el-select v-model="queryForm.sensorType">
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
          <span>时序数据趋势图</span>
          <el-tag type="success">{{ chartData.count }} 个数据点</el-tag>
        </div>
      </template>

      <!-- 使用原生HTML/CSS模拟图表 -->
      <div class="chart-container">
        <div class="chart-wrapper">
          <div class="y-axis">
            <span v-for="n in 5" :key="n" class="y-label">{{ getYLabel(n) }}</span>
          </div>
          <div class="chart-area">
            <div class="grid-lines">
              <div v-for="n in 5" :key="n" class="grid-line"></div>
            </div>
            <svg class="line-svg" viewBox="0 0 100 100" preserveAspectRatio="none">
              <polyline
                :points="getLinePoints()"
                fill="none"
                stroke="#409eff"
                stroke-width="0.5"
              />
              <circle
                v-for="(point, index) in getPoints()"
                :key="index"
                :cx="point.x"
                :cy="point.y"
                r="1"
                fill="#409eff"
                class="data-point"
              />
            </svg>
          </div>
        </div>
        <div class="x-axis">
          <span v-for="(label, index) in getXLabels()" :key="index" class="x-label">{{ label }}</span>
        </div>
      </div>

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
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { TrendCharts, DataLine } from '@element-plus/icons-vue'
import { serviceApi } from '../../api/request'

const loading = ref(false)
const hasSearched = ref(false)
const showAllData = ref(false)

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

// 获取Y轴标签
const getYLabel = (n) => {
  const max = statistics.max || 100
  const min = statistics.min || 0
  const step = (max - min) / 4
  return (max - (n - 1) * step).toFixed(1)
}

// 获取X轴标签
const getXLabels = () => {
  const labels = []
  const count = chartData.value.xAxis?.length || 0
  if (count === 0) return labels

  // 显示5个时间点
  for (let i = 0; i < 5; i++) {
    const index = Math.floor(i * (count - 1) / 4)
    const timestamp = chartData.value.xAxis[index]
    if (timestamp) {
      // 简化显示
      const date = new Date(timestamp)
      labels.push(`${date.getHours()}:${date.getMinutes().toString().padStart(2, '0')}`)
    }
  }
  return labels
}

// 获取折线点坐标
const getPoints = () => {
  const values = chartData.value.series?.[0]?.data || []
  if (values.length === 0) return []

  const max = Math.max(...values)
  const min = Math.min(...values)
  const range = max - min || 1

  return values.map((value, index) => ({
    x: (index / (values.length - 1)) * 100,
    y: 100 - ((value - min) / range) * 100
  }))
}

// 获取折线points字符串
const getLinePoints = () => {
  return getPoints().map(p => `${p.x},${p.y}`).join(' ')
}

// 获取数值样式类
const getValueClass = (value) => {
  const avg = statistics.avg
  if (value > avg * 1.2) return 'value-high'
  if (value < avg * 0.8) return 'value-low'
  return 'value-normal'
}
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

/* 图表样式 */
.chart-container {
  padding: 20px;
}

.chart-wrapper {
  display: flex;
  height: 300px;
}

.y-axis {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  width: 50px;
  padding-right: 10px;
  text-align: right;
  font-size: 12px;
  color: #909399;
}

.chart-area {
  flex: 1;
  position: relative;
  border-left: 1px solid #dcdfe6;
  border-bottom: 1px solid #dcdfe6;
}

.grid-lines {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
}

.grid-line {
  height: 25%;
  border-top: 1px dashed #e4e7ed;
}

.line-svg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.data-point {
  cursor: pointer;
  transition: r 0.2s;
}

.data-point:hover {
  r: 2;
}

.x-axis {
  display: flex;
  justify-content: space-between;
  margin-left: 50px;
  padding-top: 10px;
  font-size: 12px;
  color: #909399;
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
