<template>
  <div class="computation-sync">
    <h2>时间同步服务</h2>
    <p class="desc">计算层核心功能：基于隶属度的不确定性时间同步算法</p>

    <el-row :gutter="20">
      <!-- 数据输入 -->
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>原始数据输入</span>
              <el-tag type="primary">多源传感器</el-tag>
            </div>
          </template>

          <el-form :model="form" label-width="120px">
            <el-form-item label="设备ID">
              <el-select v-model="form.deviceId" placeholder="选择设备" style="width: 150px">
                <el-option label="设备 EQ-001" value="EQ-001" />
                <el-option label="设备 EQ-002" value="EQ-002" />
                <el-option label="设备 EQ-003" value="EQ-003" />
              </el-select>
            </el-form-item>

            <el-form-item label="传感器类型">
              <el-select v-model="form.sensorType" style="width: 140px">
                <el-option label="温度" value="temperature" />
                <el-option label="振动" value="vibration" />
                <el-option label="压力" value="pressure" />
                <el-option label="电流" value="current" />
              </el-select>
            </el-form-item>

            <el-form-item label="数据值">
              <el-input
                v-model="form.values"
                type="textarea"
                :rows="4"
                placeholder="输入数值，用逗号分隔，如: 10.5, 20.3, 15.8, 25.1, 18.7"
              />
              <div v-if="dataSourceInfo" class="data-source-info">
                <el-tag type="success" size="small">数据来源</el-tag>
                <span>设备: {{ dataSourceInfo.deviceId }}</span>
                <span>类型: {{ dataSourceInfo.sensorType }}</span>
                <span>已加载: {{ dataSourceInfo.loadedCount }}/{{ dataSourceInfo.totalCount }}条</span>
                <span>时间范围: {{ dataSourceInfo.oldestTimestamp?.slice(0, 19) }} ~ {{ dataSourceInfo.latestTimestamp?.slice(0, 19) }}</span>
              </div>
            </el-form-item>

            <el-form-item label="同步参数">
              <el-row :gutter="10">
                <el-col :span="12">
                  <el-form-item label="K邻居数">
                    <el-input-number v-model="form.kNeighbors" :min="1" :max="5" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="期望间隔(ms)">
                    <el-input-number v-model="form.expectedIntervalMs" :min="100" :max="5000" :step="100" />
                  </el-form-item>
                </el-col>
              </el-row>
            </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="performSync" :loading="loading">
                <el-icon><Timer /></el-icon>
                执行时间同步
              </el-button>
              <el-button type="success" @click="loadFromStorage" :loading="loadingFromStorage">
                从存储层加载
              </el-button>
              <el-button @click="generateSampleData">
                <el-icon><DataLine /></el-icon>
                生成示例数据
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 同步结果 -->
      <el-col :span="12">
        <el-card v-if="result" class="result-card">
          <template #header>
            <div class="card-header">
              <span>同步结果</span>
              <el-tag :type="result.interpolatedCount > 0 ? 'warning' : 'success'">
                {{ result.interpolatedCount > 0 ? '有插值' : '无插值' }}
              </el-tag>
            </div>
          </template>

          <el-descriptions :column="1" border>
            <el-descriptions-item label="输入数据量">{{ result.inputCount }} 条</el-descriptions-item>
            <el-descriptions-item label="输出数据量">{{ result.outputCount }} 条</el-descriptions-item>
            <el-descriptions-item label="平均置信度">
              <el-progress :percentage="Math.round((result.averageConfidence || 0) * 100)" />
            </el-descriptions-item>
            <el-descriptions-item label="插值数量">{{ result.interpolatedCount }} 条</el-descriptions-item>
            <el-descriptions-item label="处理耗时">{{ result.processingTimeMs }} ms</el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 算法说明 -->
        <el-card class="algorithm-card">
          <template #header>算法说明</template>
          <el-timeline>
            <el-timeline-item type="primary">
              <h4>初始隶属概率计算</h4>
              <p class="desc-text">基于数据点与期望采样率的偏差计算初始概率</p>
            </el-timeline-item>
            <el-timeline-item type="success">
              <h4>K邻居加权迭代</h4>
              <p class="desc-text">利用K个最近邻居的概率加权更新当前点概率</p>
            </el-timeline-item>
            <el-timeline-item type="warning">
              <h4>低概率点插值修复</h4>
              <p class="desc-text">对置信度低于阈值的数据点进行插值修复</p>
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>

    <!-- 同步后数据展示 -->
    <el-card v-if="syncedData.length > 0" class="data-card">
      <template #header>
        <div class="card-header">
          <span>同步后数据明细</span>
          <el-tag type="info">{{ syncedData.length }} 条</el-tag>
        </div>
      </template>

      <el-table :data="syncedData.slice(0, 20)" stripe size="small">
        <el-table-column type="index" width="50" />
        <el-table-column prop="syncedTimestamp" label="同步后时间戳" width="180">
          <template #default="scope">
            {{ formatTime(scope.row.syncedTimestamp) }}
          </template>
        </el-table-column>
        <el-table-column prop="value" label="数值" width="100" />
        <el-table-column prop="confidence" label="置信度">
          <template #default="scope">
            <el-progress
              :percentage="Math.round((scope.row.confidence || 0) * 100)"
              :status="scope.row.confidence > 0.8 ? 'success' : scope.row.confidence > 0.5 ? '' : 'exception'"
            />
          </template>
        </el-table-column>
        <el-table-column prop="interpolated" label="是否插值" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.interpolated ? 'warning' : 'info'" size="small">
              {{ scope.row.interpolated ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="syncedData.length > 20" class="more-data">
        <el-text type="info">... 还有 {{ syncedData.length - 20 }} 条数据</el-text>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Timer, DataLine } from '@element-plus/icons-vue'
import { computationApi, storageApi } from '../../api/request'

const loading = ref(false)
const result = ref(null)
const syncedData = ref([])
const loadingFromStorage = ref(false)
const dataSourceInfo = ref(null) // 数据来源信息

const form = reactive({
  deviceId: 'EQ-001',
  sensorType: 'temperature',
  values: '20.5, 21.3, 20.8, 22.1, 21.5, 20.9, 21.7, 22.3, 21.8, 22.0',
  kNeighbors: 2,
  expectedIntervalMs: 1000
})

// 格式化时间
const formatTime = (timestamp) => {
  if (!timestamp) return '-'
  if (typeof timestamp === 'string') {
    const date = new Date(timestamp)
    return date.toLocaleString()
  }
  return timestamp
}

// 生成示例数据
const generateSampleData = () => {
  const baseValue = 20 + Math.random() * 10
  const values = []
  const count = 10 + Math.floor(Math.random() * 10)

  for (let i = 0; i < count; i++) {
    // 添加一些随机噪声
    const noise = (Math.random() - 0.5) * 2
    values.push((baseValue + noise).toFixed(2))
  }

  form.values = values.join(', ')
  ElMessage.success(`已生成 ${count} 个示例数据点`)
}

// 从存储层加载数据
const loadFromStorage = async () => {
  loadingFromStorage.value = true
  dataSourceInfo.value = null
  try {
    const params = {
      deviceId: form.deviceId,
      start: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
      end: new Date().toISOString()
    }
    if (form.sensorType) {
      params.sensorType = form.sensorType
    }
    
    const res = await storageApi.queryTimeSeries(params)
    let data = res.data || []
    
    if (data.length === 0) {
      ElMessage.warning('未找到数据，请先在采集层录入数据')
      return
    }
    
    // 限制最多加载50条数据（按时间倒序取最新的）
    const maxLoad = 50
    const originalCount = data.length
    if (data.length > maxLoad) {
      // 按时间戳排序，取最新的数据
      data = data.sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp)).slice(0, maxLoad)
    }
    
    // 记录数据来源信息
    dataSourceInfo.value = {
      deviceId: form.deviceId,
      sensorType: form.sensorType,
      totalCount: originalCount,
      loadedCount: data.length,
      timeRange: `${params.start.slice(0, 19)} ~ ${params.end.slice(0, 19)}`,
      latestTimestamp: data[0]?.timestamp,
      oldestTimestamp: data[data.length - 1]?.timestamp
    }
    
    // 提取数值
    const values = data.map(d => d.value)
    form.values = values.map(v => v.toFixed(2)).join(', ')
    
    ElMessage.success(`从存储层加载了 ${data.length} 条数据${originalCount > maxLoad ? '（共' + originalCount + '条，已限制为最新' + maxLoad + '条）' : ''}`)
  } catch (error) {
    ElMessage.error('加载失败: ' + error.message)
  } finally {
    loadingFromStorage.value = false
  }
}

// 执行时间同步
const performSync = async () => {
  if (!form.values.trim()) {
    ElMessage.error('请输入数据值')
    return
  }

  loading.value = true
  result.value = null
  syncedData.value = []

  try {
    // 解析数值
    const values = form.values.split(',').map(v => parseFloat(v.trim())).filter(v => !isNaN(v))

    if (values.length === 0) {
      ElMessage.error('请输入有效的数值')
      return
    }

    // 构建原始数据（带时间戳）
    const now = new Date()
    const rawData = values.map((value, index) => ({
      deviceId: form.deviceId,
      sensorType: form.sensorType,
      value,
      // 模拟不规则的时间戳（添加随机偏移）
      timestamp: new Date(now.getTime() - (values.length - index) * 1000 + (Math.random() - 0.5) * 500).toISOString()
    }))

    const res = await computationApi.timeSync({
      rawData,
      kNeighbors: form.kNeighbors,
      expectedIntervalMs: form.expectedIntervalMs
    })

    result.value = res.data
    syncedData.value = res.data?.syncedData || []

    ElMessage.success(`时间同步完成，平均置信度 ${((res.data?.averageConfidence || 0) * 100).toFixed(1)}%`)
  } catch (error) {
    ElMessage.error('同步失败: ' + error.message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.computation-sync {
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

.result-card,
.algorithm-card {
  margin-bottom: 20px;
}

.algorithm-card h4 {
  margin: 0 0 5px 0;
  color: #303133;
}

.desc-text {
  margin: 0;
  color: #909399;
  font-size: 13px;
}

.data-card {
  margin-top: 20px;
}

.more-data {
  text-align: center;
  padding: 10px;
}

.data-source-info {
  margin-top: 8px;
  padding: 8px 12px;
  background: #f0f9ff;
  border-radius: 4px;
  font-size: 12px;
  color: #606266;
}

.data-source-info span {
  margin-left: 10px;
}
</style>
