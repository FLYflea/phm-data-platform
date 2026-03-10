<template>
  <div class="collection-sensor">
    <h2>传感器数据采集</h2>
    <p class="desc">采集层核心功能：在线数据采集与批量数据采集</p>

    <el-row :gutter="20">
      <!-- 单条数据采集 -->
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>在线数据采集</span>
              <el-tag type="primary">实时监控数据</el-tag>
            </div>
          </template>

          <el-form :model="singleForm" label-width="100px">
            <el-form-item label="设备ID">
              <el-select v-model="singleForm.deviceId" placeholder="选择设备">
                <el-option label="设备 EQ-001" value="EQ-001" />
                <el-option label="设备 EQ-002" value="EQ-002" />
                <el-option label="设备 EQ-003" value="EQ-003" />
              </el-select>
            </el-form-item>

            <el-form-item label="传感器类型">
              <el-radio-group v-model="singleForm.sensorType">
                <el-radio label="temperature">温度</el-radio>
                <el-radio label="vibration">振动</el-radio>
                <el-radio label="pressure">压力</el-radio>
                <el-radio label="current">电流</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="数值">
              <el-input-number v-model="singleForm.value" :precision="2" :step="0.1" />
            </el-form-item>

            <el-form-item label="单位">
              <el-input v-model="singleForm.unit" placeholder="如: mm/s, °C, Pa" />
            </el-form-item>

            <el-form-item label="时间戳">
              <el-date-picker
                v-model="singleForm.timestamp"
                type="datetime"
                placeholder="选择时间"
                value-format="YYYY-MM-DDTHH:mm:ss"
              />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="sendSingleData" :loading="loading">
                发送数据
              </el-button>
              <el-button @click="resetSingleForm">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 批量数据采集 -->
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>批量数据采集</span>
              <el-tag type="success">车载数据库</el-tag>
            </div>
          </template>

          <el-form :model="batchForm" label-width="100px">
            <el-form-item label="设备ID">
              <el-select v-model="batchForm.deviceId" placeholder="选择设备">
                <el-option label="设备 EQ-001" value="EQ-001" />
                <el-option label="设备 EQ-002" value="EQ-002" />
                <el-option label="设备 EQ-003" value="EQ-003" />
              </el-select>
            </el-form-item>

            <el-form-item label="传感器类型">
              <el-radio-group v-model="batchForm.sensorType">
                <el-radio label="temperature">温度</el-radio>
                <el-radio label="vibration">振动</el-radio>
                <el-radio label="pressure">压力</el-radio>
                <el-radio label="current">电流</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="数据条数">
              <el-slider v-model="batchForm.count" :min="5" :max="50" show-stops />
              <span>{{ batchForm.count }} 条</span>
            </el-form-item>

            <el-form-item label="数值范围">
              <el-input-number v-model="batchForm.minValue" :precision="2" placeholder="最小值" />
              <span style="margin: 0 10px">~</span>
              <el-input-number v-model="batchForm.maxValue" :precision="2" placeholder="最大值" />
            </el-form-item>

            <el-form-item>
              <el-button type="success" @click="sendBatchData" :loading="batchLoading">
                批量发送
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <!-- 数据流向展示 -->
    <el-card class="data-flow" v-if="lastResponse">
      <template #header>
        <span>数据流向与处理结果</span>
      </template>
      
      <el-steps :active="flowActive" finish-status="success">
        <el-step title="采集层" description="接收传感器数据" />
        <el-step title="计算层" description="时间同步与特征提取" />
        <el-step title="存储层" description="保存到时序数据库" />
        <el-step title="返回结果" description="处理完成" />
      </el-steps>

      <div class="response-detail" v-if="lastResponse">
        <h4>处理结果</h4>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="设备ID">{{ lastResponse.deviceId }}</el-descriptions-item>
          <el-descriptions-item label="输入数据">{{ lastResponse.inputCount }} 条</el-descriptions-item>
          <el-descriptions-item label="保存数据">{{ lastResponse.savedCount }} 条</el-descriptions-item>
          <el-descriptions-item label="处理耗时">{{ lastResponse.processingTimeMs }} ms</el-descriptions-item>
        </el-descriptions>

        <div v-if="lastResponse.features" class="features-section">
          <h4>提取的特征</h4>
          <el-tag v-for="(value, key) in lastResponse.features" :key="key" class="feature-tag">
            {{ key }}: {{ value?.toFixed ? value.toFixed(2) : value }}
          </el-tag>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { collectionApi } from '../../api/request'

const loading = ref(false)
const batchLoading = ref(false)
const flowActive = ref(0)
const lastResponse = ref(null)

// 单条数据表单
const singleForm = reactive({
  deviceId: 'EQ-001',
  sensorType: 'vibration',
  value: 12.5,
  unit: 'mm/s',
  timestamp: new Date().toISOString().slice(0, 19)
})

// 批量数据表单
const batchForm = reactive({
  deviceId: 'EQ-001',
  sensorType: 'temperature',
  count: 10,
  minValue: 20,
  maxValue: 80
})

// 发送单条数据
const sendSingleData = async () => {
  loading.value = true
  flowActive.value = 1
  lastResponse.value = null

  try {
    const data = {
      deviceId: singleForm.deviceId,
      sensorType: singleForm.sensorType,
      value: singleForm.value,
      unit: singleForm.unit,
      timestamp: singleForm.timestamp + 'Z',
      location: '前端采集'
    }

    const res = await collectionApi.sendSensorData(data)
    flowActive.value = 4
    lastResponse.value = res.data
    ElMessage.success('数据采集成功！')
  } catch (error) {
    ElMessage.error('采集失败: ' + error.message)
    flowActive.value = 0
  } finally {
    loading.value = false
  }
}

// 发送批量数据
const sendBatchData = async () => {
  batchLoading.value = true
  flowActive.value = 1
  lastResponse.value = null

  try {
    // 生成批量数据
    const dataList = []
    const now = new Date()
    for (let i = 0; i < batchForm.count; i++) {
      const value = batchForm.minValue + Math.random() * (batchForm.maxValue - batchForm.minValue)
      const timestamp = new Date(now.getTime() - i * 60000).toISOString()
      dataList.push({
        deviceId: batchForm.deviceId,
        sensorType: batchForm.sensorType,
        value: parseFloat(value.toFixed(2)),
        timestamp: timestamp,
        location: '前端批量采集'
      })
    }

    const res = await collectionApi.sendBatchSensorData(dataList)
    flowActive.value = 4
    lastResponse.value = res.data
    ElMessage.success(`批量采集成功！共 ${batchForm.count} 条数据`)
  } catch (error) {
    ElMessage.error('批量采集失败: ' + error.message)
    flowActive.value = 0
  } finally {
    batchLoading.value = false
  }
}

// 重置表单
const resetSingleForm = () => {
  singleForm.deviceId = 'EQ-001'
  singleForm.sensorType = 'vibration'
  singleForm.value = 12.5
  singleForm.unit = 'mm/s'
  singleForm.timestamp = new Date().toISOString().slice(0, 19)
}
</script>

<style scoped>
.collection-sensor {
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

.data-flow {
  margin-top: 20px;
}

.response-detail {
  margin-top: 20px;
  padding: 20px;
  background: #f5f7fa;
  border-radius: 4px;
}

.features-section {
  margin-top: 15px;
}

.feature-tag {
  margin: 5px;
}
</style>