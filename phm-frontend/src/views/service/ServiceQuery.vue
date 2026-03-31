<template>
  <div class="service-query">
    <h2>统一数据查询服务</h2>
    <p class="desc">服务层被动数据查询：统一接口支持时序、图谱、文本多模态数据查询</p>
    
    <el-card>
      <template #header>统一查询接口</template>
      <el-form :model="queryForm" label-width="100px">
        <el-form-item label="查询类型">
          <el-radio-group v-model="queryForm.type">
            <el-radio value="raw">原始时序数据</el-radio>
            <el-radio value="processed">处理后数据</el-radio>
            <el-radio value="graph">知识图谱</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 时序查询参数 -->
        <template v-if="queryForm.type === 'raw' || queryForm.type === 'processed'">
          <el-form-item label="设备ID">
            <el-select v-model="queryForm.deviceId" placeholder="选择设备" style="width: 150px">
              <el-option label="设备 EQ-001" value="EQ-001" />
              <el-option label="设备 EQ-002" value="EQ-002" />
              <el-option label="设备 EQ-003" value="EQ-003" />
            </el-select>
          </el-form-item>
          <el-form-item label="传感器类型">
            <el-select v-model="queryForm.sensorType" placeholder="可选过滤" style="width: 140px" clearable>
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
        </template>

        <!-- 图谱查询参数 -->
        <template v-if="queryForm.type === 'graph'">
          <el-form-item label="设备ID">
            <el-select v-model="queryForm.graphDeviceId" placeholder="选择设备" style="width: 150px">
              <el-option label="设备 EQ-001" value="EQ-001" />
              <el-option label="设备 EQ-002" value="EQ-002" />
              <el-option label="设备 EQ-003" value="EQ-003" />
            </el-select>
          </el-form-item>
        </template>

        <el-form-item>
          <el-button type="primary" @click="performQuery" :loading="loading">查询</el-button>
          <el-button @click="clearResult">清空结果</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 时序数据结果展示 -->
    <el-card v-if="resultType === 'timeseries' && timeseriesData.length > 0" class="result-card">
      <template #header>
        <div class="card-header">
          <span>查询结果 — 时序数据</span>
          <el-tag type="success">{{ timeseriesData.length }} 条记录</el-tag>
        </div>
      </template>
      <el-table :data="timeseriesData" stripe size="small" max-height="400">
        <el-table-column type="index" width="50" label="#" />
        <el-table-column prop="deviceId" label="设备ID" width="100" />
        <el-table-column prop="sensorType" label="传感器" width="90" />
        <el-table-column prop="timestamp" label="时间戳" width="200">
          <template #default="scope">
            {{ formatTimestamp(scope.row.timestamp) }}
          </template>
        </el-table-column>
        <el-table-column prop="value" label="数值" width="120">
          <template #default="scope">
            <span :class="getValueClass(scope.row.value)">
              {{ typeof scope.row.value === 'number' ? scope.row.value.toFixed(4) : scope.row.value }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="60" />
      </el-table>
    </el-card>

    <!-- 处理后数据结果 -->
    <el-card v-if="resultType === 'processed'" class="result-card">
      <template #header>
        <div class="card-header">
          <span>查询结果 — 处理后数据（含特征）</span>
          <el-tag type="success">{{ processedRawData.length }} 条原始记录</el-tag>
        </div>
      </template>

      <!-- 特征统计卡片 -->
      <div v-if="processedFeatures && Object.keys(processedFeatures).length > 0" class="features-section">
        <h4>特征提取结果</h4>
        <el-row :gutter="15">
          <el-col :span="6" v-for="(value, key) in processedFeatures" :key="key">
            <el-statistic :title="featureLabel(key)" :value="typeof value === 'number' ? value : 0" :precision="4" />
          </el-col>
        </el-row>
      </div>

      <!-- 原始数据表格 -->
      <el-table :data="processedRawData" stripe size="small" max-height="300" style="margin-top: 15px">
        <el-table-column type="index" width="50" label="#" />
        <el-table-column prop="deviceId" label="设备ID" width="100" />
        <el-table-column prop="sensorType" label="传感器" width="90" />
        <el-table-column prop="timestamp" label="时间戳" width="200">
          <template #default="scope">
            {{ formatTimestamp(scope.row.timestamp) }}
          </template>
        </el-table-column>
        <el-table-column prop="value" label="数值" width="120">
          <template #default="scope">
            {{ typeof scope.row.value === 'number' ? scope.row.value.toFixed(4) : scope.row.value }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 图谱数据结果 -->
    <el-card v-if="resultType === 'graph'" class="result-card">
      <template #header>
        <div class="card-header">
          <span>查询结果 — 知识图谱</span>
          <el-tag type="success">{{ graphNodes.length }} 个节点, {{ graphRelations.length }} 条关系</el-tag>
        </div>
      </template>

      <el-row :gutter="20">
        <el-col :span="12">
          <h4>设备与组件节点</h4>
          <el-table :data="graphNodes" stripe size="small" max-height="300">
            <el-table-column type="index" width="50" />
            <el-table-column prop="name" label="名称" width="150" />
            <el-table-column prop="type" label="类型" width="100">
              <template #default="scope">
                <el-tag :type="scope.row.type === 'equipment' ? 'primary' : 'success'" size="small">
                  {{ scope.row.type === 'equipment' ? '设备' : '组件' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="80" />
          </el-table>
        </el-col>
        <el-col :span="12">
          <h4>关系列表</h4>
          <el-table :data="graphRelations" stripe size="small" max-height="300">
            <el-table-column type="index" width="50" />
            <el-table-column prop="source" label="源节点" width="120" />
            <el-table-column prop="relation" label="关系" width="100">
              <template #default="scope">
                <el-tag type="warning" size="small">{{ scope.row.relation }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="target" label="目标节点" width="120" />
          </el-table>
        </el-col>
      </el-row>
    </el-card>

    <!-- 空状态 -->
    <el-card v-if="hasSearched && resultType === 'empty'" class="result-card">
      <el-empty description="查询无结果，请调整条件后重试" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { serviceApi, storageApi } from '../../api/request'

const loading = ref(false)
const hasSearched = ref(false)
const resultType = ref('')

const timeseriesData = ref([])
const processedRawData = ref([])
const processedFeatures = ref({})
const graphNodes = ref([])
const graphRelations = ref([])

const formatLocalTime = (date) => {
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const queryForm = reactive({
  type: 'raw',
  deviceId: 'EQ-001',
  sensorType: '',
  timeRange: [
    formatLocalTime(new Date(Date.now() - 7 * 24 * 60 * 60 * 1000)),
    formatLocalTime(new Date())
  ],
  graphDeviceId: 'EQ-001'
})

const formatTimestamp = (ts) => {
  if (!ts) return '-'
  const d = new Date(ts)
  if (isNaN(d.getTime())) return ts
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const getValueClass = (value) => {
  if (typeof value !== 'number') return ''
  if (value > 80) return 'value-high'
  if (value < 10) return 'value-low'
  return 'value-normal'
}

const featureLabel = (key) => {
  const map = {
    mean: '均值', rms: '均方根', std: '标准差', max: '最大值', min: '最小值',
    peak: '峰值', peakToPeak: '峰峰值', skewness: '偏度', kurtosis: '峭度',
    crestFactor: '波峰因子', shapeFactor: '形状因子', impulseFactor: '脉冲因子',
    marginFactor: '裕度因子', clearanceFactor: '清除因子', energy: '能量'
  }
  return map[key] || key
}

const clearResult = () => {
  resultType.value = ''
  timeseriesData.value = []
  processedRawData.value = []
  processedFeatures.value = {}
  graphNodes.value = []
  graphRelations.value = []
  hasSearched.value = false
}

const performQuery = async () => {
  loading.value = true
  hasSearched.value = true
  try {
    if (queryForm.type === 'raw') {
      const params = {
        deviceId: queryForm.deviceId,
        startTime: new Date(queryForm.timeRange[0]).toISOString(),
        endTime: new Date(queryForm.timeRange[1]).toISOString()
      }
      if (queryForm.sensorType) params.sensorType = queryForm.sensorType

      const res = await serviceApi.queryRawData(params)
      const data = res.data?.data || res.data || []
      timeseriesData.value = Array.isArray(data) ? data : []
      resultType.value = timeseriesData.value.length > 0 ? 'timeseries' : 'empty'
      if (timeseriesData.value.length > 0) {
        ElMessage.success(`查询成功，共 ${timeseriesData.value.length} 条记录`)
      }

    } else if (queryForm.type === 'processed') {
      const params = {
        deviceId: queryForm.deviceId,
        startTime: new Date(queryForm.timeRange[0]).toISOString(),
        endTime: new Date(queryForm.timeRange[1]).toISOString()
      }
      if (queryForm.sensorType) params.sensorType = queryForm.sensorType

      const res = await serviceApi.queryProcessedData(params)
      processedRawData.value = Array.isArray(res.rawData) ? res.rawData : []
      processedFeatures.value = res.features || {}
      resultType.value = processedRawData.value.length > 0 || Object.keys(processedFeatures.value).length > 0 ? 'processed' : 'empty'
      if (processedRawData.value.length > 0) {
        ElMessage.success(`查询成功，${processedRawData.value.length} 条记录 + 特征数据`)
      }

    } else if (queryForm.type === 'graph') {
      const res = await storageApi.queryEquipmentGraph(queryForm.graphDeviceId)
      const nodes = []
      const relations = []

      if (res.equipment) {
        nodes.push({ name: res.equipment.name || res.equipment.equipmentId, type: 'equipment', status: res.equipment.status || 'normal' })
      }
      if (Array.isArray(res.components)) {
        res.components.forEach(c => {
          nodes.push({ name: c.name || c.componentId, type: 'component', status: c.status || 'normal' })
          relations.push({ source: res.equipment?.name || queryForm.graphDeviceId, relation: 'HAS_COMPONENT', target: c.name || c.componentId })
        })
      }
      if (Array.isArray(res.relations)) {
        res.relations.forEach(r => {
          relations.push({ source: r.source || r.from, relation: r.type || r.relation, target: r.target || r.to })
        })
      }

      graphNodes.value = nodes
      graphRelations.value = relations
      resultType.value = nodes.length > 0 ? 'graph' : 'empty'
      if (nodes.length > 0) {
        ElMessage.success(`查询成功，${nodes.length} 个节点，${relations.length} 条关系`)
      }
    }
  } catch (error) {
    ElMessage.error('查询失败: ' + error.message)
    resultType.value = 'empty'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.service-query {
  padding: 20px;
}
.desc {
  color: #909399;
  margin-bottom: 20px;
}
.result-card {
  margin-top: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.features-section {
  margin-bottom: 15px;
  padding: 15px;
  background: #f5f7fa;
  border-radius: 6px;
}
.features-section h4 {
  margin: 0 0 12px 0;
  color: #303133;
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
