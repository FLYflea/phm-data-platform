<template>
  <div class="computation-feature">
    <h2>特征工程服务</h2>
    <p class="desc">计算层核心功能：时域/频域特征提取</p>
    
    <el-card>
      <template #header>时域特征提取</template>
      <el-form :model="form" label-width="120px">
        <el-form-item label="设备ID">
          <el-select v-model="form.deviceId" placeholder="选择设备" style="width: 150px">
            <el-option label="设备 EQ-001" value="EQ-001" />
            <el-option label="设备 EQ-002" value="EQ-002" />
            <el-option label="设备 EQ-003" value="EQ-003" />
          </el-select>
        </el-form-item>
        <el-form-item label="传感器类型">
          <el-select v-model="form.sensorType" style="width: 150px">
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
            placeholder="输入数值，用逗号分隔，如: 10.5, 20.3, 15.8, 25.1"
          />
          <div v-if="dataSourceInfo" class="data-source-info">
            <el-tag type="success" size="small">数据来源</el-tag>
            <span>设备: {{ dataSourceInfo.deviceId }}</span>
            <span>类型: {{ dataSourceInfo.sensorType }}</span>
            <span>已加载: {{ dataSourceInfo.loadedCount }}/{{ dataSourceInfo.totalCount }}条</span>
            <span>时间范围: {{ dataSourceInfo.oldestTimestamp?.slice(0, 19) }} ~ {{ dataSourceInfo.latestTimestamp?.slice(0, 19) }}</span>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="extractFeatures" :loading="loading">
            提取特征
          </el-button>
          <el-button type="success" @click="loadFromStorage" :loading="loadingFromStorage">
            从存储层加载
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="features" class="result-card">
      <template #header>提取的特征</template>
      <el-row :gutter="10">
        <el-col :span="6" v-for="(value, key) in features" :key="key">
          <div class="feature-item">
            <div class="feature-name">{{ key }}</div>
            <div class="feature-value">{{ value?.toFixed ? value.toFixed(4) : value }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { computationApi, storageApi } from '../../api/request'

const loading = ref(false)
const loadingFromStorage = ref(false)
const features = ref(null)
const dataSourceInfo = ref(null) // 数据来源信息

const form = reactive({
  deviceId: 'EQ-001',
  sensorType: 'temperature',
  values: '10.5, 20.3, 15.8, 25.1, 18.7, 22.4, 19.6'
})

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

const extractFeatures = async () => {
  loading.value = true
  try {
    const values = form.values.split(',').map(v => parseFloat(v.trim())).filter(v => !isNaN(v))
    const res = await computationApi.extractFeatures({ values })
    features.value = res.data.features
    ElMessage.success(`成功提取 ${res.data.featureCount} 个特征`)
  } catch (error) {
    ElMessage.error('特征提取失败: ' + error.message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.computation-feature {
  padding: 20px;
}
.desc {
  color: #909399;
  margin-bottom: 20px;
}
.result-card {
  margin-top: 20px;
}
.feature-item {
  background: #f5f7fa;
  padding: 15px;
  margin-bottom: 10px;
  border-radius: 4px;
  text-align: center;
}
.feature-name {
  font-size: 12px;
  color: #909399;
  margin-bottom: 5px;
}
.feature-value {
  font-size: 18px;
  font-weight: bold;
  color: #409eff;
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
