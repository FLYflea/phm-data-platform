<template>
  <div class="computation-fusion">
    <h2>数据融合服务</h2>
    <p class="desc">计算层核心功能：PDA概率数据关联融合算法</p>

    <el-row :gutter="20">
      <!-- 数据源A -->
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>数据源 A</span>
              <el-tag type="primary">传感器1</el-tag>
            </div>
          </template>
          <el-form :model="sourceA" label-width="100px">
            <el-form-item label="设备ID">
              <el-select v-model="sourceA.deviceId" placeholder="选择设备" style="width: 150px">
                <el-option label="设备 EQ-001" value="EQ-001" />
                <el-option label="设备 EQ-002" value="EQ-002" />
                <el-option label="设备 EQ-003" value="EQ-003" />
              </el-select>
            </el-form-item>
            <el-form-item label="传感器类型">
              <el-select v-model="sourceA.sensorType" style="width: 150px">
                <el-option label="温度" value="temperature" />
                <el-option label="振动" value="vibration" />
                <el-option label="压力" value="pressure" />
              </el-select>
            </el-form-item>
            <el-form-item label="数据值">
              <el-input
                v-model="sourceA.values"
                type="textarea"
                :rows="3"
                placeholder="输入数值，用逗号分隔，如: 10.5, 20.3, 15.8"
              />
              <div v-if="sourceAInfo" class="data-source-info">
                <el-tag type="success" size="small">已加载 {{ sourceAInfo.loadedCount }}条</el-tag>
                <span>时间: {{ sourceAInfo.oldestTimestamp?.slice(0, 19) }} ~ {{ sourceAInfo.latestTimestamp?.slice(0, 19) }}</span>
              </div>
            </el-form-item>
            <el-form-item>
              <el-button type="success" size="small" @click="loadSourceA" :loading="loadingA">从存储层加载</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 数据源B -->
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>数据源 B</span>
              <el-tag type="success">传感器2</el-tag>
            </div>
          </template>
          <el-form :model="sourceB" label-width="100px">
            <el-form-item label="设备ID">
              <el-select v-model="sourceB.deviceId" placeholder="选择设备" style="width: 150px">
                <el-option label="设备 EQ-001" value="EQ-001" />
                <el-option label="设备 EQ-002" value="EQ-002" />
                <el-option label="设备 EQ-003" value="EQ-003" />
              </el-select>
            </el-form-item>
            <el-form-item label="传感器类型">
              <el-select v-model="sourceB.sensorType" style="width: 150px">
                <el-option label="温度" value="temperature" />
                <el-option label="振动" value="vibration" />
                <el-option label="压力" value="pressure" />
              </el-select>
            </el-form-item>
            <el-form-item label="数据值">
              <el-input
                v-model="sourceB.values"
                type="textarea"
                :rows="3"
                placeholder="输入数值，用逗号分隔，如: 11.2, 19.8, 16.1"
              />
              <div v-if="sourceBInfo" class="data-source-info">
                <el-tag type="success" size="small">已加载 {{ sourceBInfo.loadedCount }}条</el-tag>
                <span>时间: {{ sourceBInfo.oldestTimestamp?.slice(0, 19) }} ~ {{ sourceBInfo.latestTimestamp?.slice(0, 19) }}</span>
              </div>
            </el-form-item>
            <el-form-item>
              <el-button type="success" size="small" @click="loadSourceB" :loading="loadingB">从存储层加载</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <!-- 操作按钮 -->
    <el-card class="action-card">
      <el-button type="primary" @click="performFusion" :loading="loading" size="large">
        <el-icon><Connection /></el-icon>
        执行PDA数据融合
      </el-button>
      <el-button @click="generateSampleData" size="large">
        <el-icon><DataLine /></el-icon>
        生成示例数据
      </el-button>
    </el-card>

    <!-- 融合结果 -->
    <el-card v-if="result" class="result-card">
      <template #header>
        <span>融合结果对比</span>
      </template>

      <el-row :gutter="20">
        <!-- PDA结果 -->
        <el-col :span="12">
          <div class="result-section pda-result">
            <h4>PDA概率融合</h4>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="融合结果数">{{ result.pda?.resultCount || 0 }}</el-descriptions-item>
              <el-descriptions-item label="平均置信度">{{ formatPercent(result.pda?.avgConfidence) }}</el-descriptions-item>
              <el-descriptions-item label="方差缩减率">{{ formatPercent(result.pda?.avgVarianceReduction) }}</el-descriptions-item>
            </el-descriptions>
          </div>
        </el-col>

        <!-- NN基线 -->
        <el-col :span="12">
          <div class="result-section nn-result">
            <h4>NN最近邻基线</h4>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="融合结果数">{{ result.nn?.resultCount || 0 }}</el-descriptions-item>
              <el-descriptions-item label="平均置信度">{{ formatPercent(result.nn?.avgConfidence) }}</el-descriptions-item>
              <el-descriptions-item label="方差缩减率">{{ formatPercent(result.nn?.avgVarianceReduction) }}</el-descriptions-item>
            </el-descriptions>
          </div>
        </el-col>
      </el-row>

      <!-- 性能提升 -->
      <div v-if="result.improvement" class="improvement-section">
        <h4>PDA相比NN的提升</h4>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-statistic
              title="置信度提升"
              :value="result.improvement.confidenceGain"
              :precision="4"
            />
          </el-col>
          <el-col :span="12">
            <el-statistic
              title="方差缩减提升"
              :value="result.improvement.varianceReductionGain"
              :precision="4"
            />
          </el-col>
        </el-row>
      </div>

      <!-- 详细数据 -->
      <div v-if="result.pda?.results" class="detail-section">
        <h4>融合详细数据</h4>
        <el-table :data="result.pda.results.slice(0, 10)" stripe size="small">
          <el-table-column prop="timestamp" label="时间戳" width="180" />
          <el-table-column prop="fusedValue" label="融合值" width="120">
            <template #default="scope">
              {{ scope.row.fusedValue?.toFixed ? scope.row.fusedValue.toFixed(4) : scope.row.fusedValue }}
            </template>
          </el-table-column>
          <el-table-column prop="confidence" label="置信度" width="120">
            <template #default="scope">
              {{ formatPercent(scope.row.confidence) }}
            </template>
          </el-table-column>
          <el-table-column prop="fusionMethod" label="融合方法" />
          <el-table-column prop="sourceCount" label="数据源数" width="100" />
        </el-table>
      </div>

      <div class="processing-info">
        <el-tag type="info">处理耗时: {{ result.processingTimeMs }} ms</el-tag>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection, DataLine } from '@element-plus/icons-vue'
import { computationApi, storageApi } from '../../api/request'

const loading = ref(false)
const result = ref(null)
const loadingA = ref(false)
const loadingB = ref(false)
const sourceAInfo = ref(null)
const sourceBInfo = ref(null)

const sourceA = reactive({
  deviceId: 'EQ-001',
  sensorType: 'temperature',
  values: '20.5, 21.3, 20.8, 22.1, 21.5, 20.9, 21.7'
})

const sourceB = reactive({
  deviceId: 'EQ-002',
  sensorType: 'temperature',
  values: '20.8, 21.0, 21.2, 21.8, 21.3, 21.1, 21.5'
})

// 格式化百分比
const formatPercent = (value) => {
  if (value === null || value === undefined) return '-'
  return (value * 100).toFixed(2) + '%'
}

// 从存储层加载数据的通用函数
const loadFromStorage = async (source, infoRef, loadingRef) => {
  loadingRef.value = true
  infoRef.value = null
  try {
    const params = {
      deviceId: source.deviceId,
      start: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
      end: new Date().toISOString()
    }
    if (source.sensorType) {
      params.sensorType = source.sensorType
    }
    
    const res = await storageApi.queryTimeSeries(params)
    let data = res.data || []
    
    if (data.length === 0) {
      ElMessage.warning(`未找到设备 ${source.deviceId} 的数据，请先在采集层录入数据`)
      return
    }
    
    // 限制最多加载50条数据
    const maxLoad = 50
    const originalCount = data.length
    if (data.length > maxLoad) {
      data = data.sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp)).slice(0, maxLoad)
    }
    
    // 记录数据来源信息
    infoRef.value = {
      deviceId: source.deviceId,
      sensorType: source.sensorType,
      totalCount: originalCount,
      loadedCount: data.length,
      latestTimestamp: data[0]?.timestamp,
      oldestTimestamp: data[data.length - 1]?.timestamp
    }
    
    // 提取数值
    const values = data.map(d => d.value)
    source.values = values.map(v => v.toFixed(2)).join(', ')
    
    ElMessage.success(`数据源已加载 ${data.length} 条数据`)
  } catch (error) {
    ElMessage.error('加载失败: ' + error.message)
  } finally {
    loadingRef.value = false
  }
}

// 加载数据源A
const loadSourceA = () => loadFromStorage(sourceA, sourceAInfo, loadingA)

// 加载数据源B
const loadSourceB = () => loadFromStorage(sourceB, sourceBInfo, loadingB)

// 生成示例数据
const generateSampleData = () => {
  const baseValue = 20 + Math.random() * 10
  const generateValues = (count, variance) => {
    return Array.from({ length: count }, (_, i) => {
      const noise = (Math.random() - 0.5) * variance
      return (baseValue + noise + i * 0.1).toFixed(2)
    }).join(', ')
  }

  sourceA.values = generateValues(10, 2)
  sourceB.values = generateValues(10, 1.5)
  sourceAInfo.value = null
  sourceBInfo.value = null

  ElMessage.success('已生成示例数据')
}

// 执行数据融合
const performFusion = async () => {
  loading.value = true
  result.value = null

  try {
    // 解析数据
    const parseValues = (valuesStr) => {
      return valuesStr.split(',').map(v => parseFloat(v.trim())).filter(v => !isNaN(v))
    }

    const valuesA = parseValues(sourceA.values)
    const valuesB = parseValues(sourceB.values)

    if (valuesA.length === 0 || valuesB.length === 0) {
      ElMessage.error('请输入有效的数据值')
      return
    }

    // 构建多源数据格式
    const now = new Date()
    const buildDataList = (values, deviceId, sensorType) => {
      return values.map((value, index) => ({
        deviceId,
        sensorType,
        value,
        timestamp: new Date(now.getTime() - (values.length - index) * 1000).toISOString()
      }))
    }

    const multiSourceData = [
      buildDataList(valuesA, sourceA.deviceId, sourceA.sensorType),
      buildDataList(valuesB, sourceB.deviceId, sourceB.sensorType)
    ]

    const res = await computationApi.dataFusion({ multiSourceData })
    result.value = res.data
    ElMessage.success('数据融合完成')
  } catch (error) {
    ElMessage.error('融合失败: ' + error.message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.computation-fusion {
  padding: 20px;
}

.desc {
  color: #909399;
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.action-card {
  margin-top: 20px;
  text-align: center;
}

.result-card {
  margin-top: 20px;
}

.result-section {
  padding: 15px;
  border-radius: 4px;
}

.pda-result {
  background: #f0f9ff;
  border: 1px solid #409eff;
}

.nn-result {
  background: #f6ffed;
  border: 1px solid #52c41a;
}

.result-section h4 {
  margin: 0 0 15px 0;
  color: #303133;
}

.improvement-section {
  margin-top: 20px;
  padding: 20px;
  background: #fff7e6;
  border: 1px solid #fa8c16;
  border-radius: 4px;
}

.improvement-section h4 {
  margin: 0 0 15px 0;
  color: #fa8c16;
}

.detail-section {
  margin-top: 20px;
}

.detail-section h4 {
  margin: 0 0 15px 0;
  color: #303133;
}

.processing-info {
  margin-top: 20px;
  text-align: right;
}

.data-source-info {
  margin-top: 8px;
  padding: 6px 10px;
  background: #f0f9ff;
  border-radius: 4px;
  font-size: 12px;
  color: #606266;
}

.data-source-info span {
  margin-left: 8px;
}
</style>
