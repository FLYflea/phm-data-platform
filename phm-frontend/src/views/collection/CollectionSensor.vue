<template>
  <div class="collection-sensor">
    <div class="page-header">
      <h2><el-icon><Monitor /></el-icon> 传感器数据采集</h2>
      <p class="desc">采集层核心功能：在线数据采集与批量数据采集</p>
    </div>

    <el-row :gutter="20">
      <!-- 单条数据采集 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span><el-icon><Monitor /></el-icon> 在线数据采集</span>
              <el-tag type="primary" effect="dark" round size="small">实时监控</el-tag>
            </div>
          </template>

          <el-form :model="singleForm" label-width="100px">
            <el-form-item label="设备ID">
              <el-select v-model="singleForm.deviceId" placeholder="选择设备" filterable>
                <el-option v-for="device in deviceList" :key="device" :label="device" :value="device" />
              </el-select>
            </el-form-item>

            <el-form-item label="传感器类型">
              <el-radio-group v-model="singleForm.sensorType">
                <el-radio value="temperature">温度</el-radio>
                <el-radio value="vibration">振动</el-radio>
                <el-radio value="pressure">压力</el-radio>
                <el-radio value="current">电流</el-radio>
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
                {{ loading ? '采集中...' : '发送数据' }}
              </el-button>
              <el-button @click="resetSingleForm">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 批量数据采集 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span><el-icon><Document /></el-icon> 批量数据采集</span>
              <el-tag type="success" effect="dark" round size="small">车载数据库</el-tag>
            </div>
          </template>

          <el-form :model="batchForm" label-width="100px">
            <el-form-item label="设备ID">
              <el-select v-model="batchForm.deviceId" placeholder="选择设备" filterable>
                <el-option v-for="device in deviceList" :key="device" :label="device" :value="device" />
              </el-select>
            </el-form-item>

            <el-form-item label="传感器类型">
              <el-radio-group v-model="batchForm.sensorType">
                <el-radio value="temperature">温度</el-radio>
                <el-radio value="vibration">振动</el-radio>
                <el-radio value="pressure">压力</el-radio>
                <el-radio value="current">电流</el-radio>
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
                {{ batchLoading ? '批量采集中...' : '批量发送' }}
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <!-- CSV数据导入 -->
    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span><el-icon><Upload /></el-icon> CSV数据导入</span>
              <el-tag type="warning" effect="dark" round size="small">公开数据集</el-tag>
            </div>
          </template>

          <el-form :model="csvForm" label-width="120px">
            <el-form-item label="数据文件">
              <el-upload
                ref="csvUploadRef"
                v-model:file-list="csvForm.fileList"
                drag
                accept=".csv,.txt"
                :auto-upload="false"
                :limit="1"
              >
                <div class="el-upload__text">
                  将文件拖到此处，或<em>点击上传</em>
                </div>
                <template #tip>
                  <div class="el-upload__tip">支持 .csv 或 .txt 文件</div>
                </template>
              </el-upload>
            </el-form-item>

            <el-form-item label="设备ID">
              <el-input v-model="csvForm.deviceId" placeholder="设备标识" />
            </el-form-item>

            <el-form-item label="传感器列名">
              <el-input v-model="csvForm.sensorColumns" placeholder="逗号分隔，如: sensor1,sensor2,sensor3" />
            </el-form-item>

            <el-form-item label="时间戳列名">
              <el-input v-model="csvForm.timestampColumn" placeholder="可选，不填则自动生成" />
            </el-form-item>

            <el-form-item label="分隔符">
              <el-radio-group v-model="csvForm.delimiter">
                <el-radio value=",">逗号</el-radio>
                <el-radio value="\t">制表符</el-radio>
                <el-radio value=" ">空格</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="跳过表头">
              <el-switch v-model="csvForm.skipHeader" />
            </el-form-item>

            <el-form-item>
              <el-button type="warning" @click="importCsvData" :loading="csvLoading">
                {{ csvLoading ? '导入中...' : '导入数据' }}
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <!-- 数据流向展示 -->
    <el-card class="data-flow" v-if="lastResponse" shadow="hover">
      <template #header>
        <div class="card-header">
          <span><el-icon><TrendCharts /></el-icon> 数据流向与处理结果</span>
          <el-tag type="success" effect="dark" round size="small">完成</el-tag>
        </div>
      </template>
      
      <el-steps :active="flowActive" finish-status="success" process-status="process">
        <el-step title="采集层" description="接收传感器数据" icon="Upload" />
        <el-step title="计算层" description="时间同步与特征提取" icon="Cpu" />
        <el-step title="存储层" description="保存到时序数据库" icon="Coin" />
        <el-step title="返回结果" description="处理完成" icon="CircleCheck" />
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
          <div class="feature-grid">
            <div v-for="(value, key) in lastResponse.features" :key="key" class="feature-item">
              <span class="feature-label">{{ featureNameMap[key] || key }}</span>
              <span class="feature-value">{{ value?.toFixed ? value.toFixed(4) : value }}</span>
            </div>
          </div>
        </div>

        <!-- CSV导入结果展示 -->
        <div v-if="lastResponse.totalRows" class="csv-result-section">
          <h4>CSV导入结果</h4>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="文件名">{{ lastResponse.fileName }}</el-descriptions-item>
            <el-descriptions-item label="总行数">{{ lastResponse.totalRows }} 行</el-descriptions-item>
            <el-descriptions-item label="数据点数">{{ lastResponse.dataPointCount }} 个</el-descriptions-item>
            <el-descriptions-item label="成功数">{{ lastResponse.successCount }} 条</el-descriptions-item>
            <el-descriptions-item label="失败数">{{ lastResponse.failedCount }} 条</el-descriptions-item>
            <el-descriptions-item label="存储数">{{ lastResponse.storedCount }} 条</el-descriptions-item>
            <el-descriptions-item label="吞吐量">{{ lastResponse.throughput }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Monitor, Document, Upload, TrendCharts } from '@element-plus/icons-vue'
import { collectionApi, storageApi } from '../../api/request'

const loading = ref(false)
const batchLoading = ref(false)
const csvLoading = ref(false)
const flowActive = ref(0)
const lastResponse = ref(null)

// 动态设备列表
const deviceList = ref([])
const sensorTypeList = ref([])

// 加载设备和传感器类型列表
const loadDevicesAndSensorTypes = async () => {
  try {
    const [devicesRes, sensorTypesRes] = await Promise.all([
      storageApi.getAllDevices(),
      storageApi.getAllSensorTypes()
    ])
    deviceList.value = devicesRes.devices || []
    sensorTypeList.value = sensorTypesRes.sensorTypes || []
    console.log('加载设备列表:', deviceList.value)
    console.log('加载传感器类型:', sensorTypeList.value)
    // 设置默认设备ID
    if (deviceList.value.length > 0) {
      if (!singleForm.deviceId) singleForm.deviceId = deviceList.value[0]
      if (!batchForm.deviceId) batchForm.deviceId = deviceList.value[0]
    }
  } catch (e) {
    console.warn('加载设备/传感器列表失败，使用默认值', e)
    deviceList.value = ['EQ-001', 'EQ-002', 'EQ-003']
    sensorTypeList.value = ['temperature', 'vibration', 'pressure', 'current']
    singleForm.deviceId = 'EQ-001'
    batchForm.deviceId = 'EQ-001'
  }
}

onMounted(() => {
  loadDevicesAndSensorTypes()
})

// 传感器类型配置
const sensorConfig = {
  temperature: { defaultValue: 45.0, unit: '°C', range: [35, 55] },
  vibration: { defaultValue: 12.5, unit: 'mm/s', range: [5, 20] },
  pressure: { defaultValue: 101.3, unit: 'kPa', range: [95, 110] },
  current: { defaultValue: 5.2, unit: 'A', range: [3, 8] }
}

// 特征名称中英文映射
const featureNameMap = {
  mean: '均值',
  variance: '方差',
  stdDev: '标准差',
  rms: '均方根值',
  peak: '峰值',
  max: '最大值',
  min: '最小值',
  peakToPeak: '峰峰值',
  waveformIndex: '波形指标',
  crestFactor: '峰值因子',
  kurtosis: '峭度',
  skewness: '偏度'
}

// 单条数据表单
const singleForm = reactive({
  deviceId: '',
  sensorType: 'temperature',
  value: 45.0,
  unit: '°C',
  timestamp: new Date().toISOString().slice(0, 19)
})

// 监听传感器类型变化，自动更新默认值
watch(() => singleForm.sensorType, (newType) => {
  const config = sensorConfig[newType]
  if (config) {
    singleForm.value = config.defaultValue
    singleForm.unit = config.unit
  }
}, { immediate: true })

// 批量数据表单
const batchForm = reactive({
  deviceId: '',
  sensorType: 'temperature',
  count: 10,
  minValue: 35,
  maxValue: 55
})

// CSV导入表单
const csvForm = reactive({
  deviceId: 'NASA-ENGINE-001',
  sensorColumns: '',
  timestampColumn: '',
  delimiter: ',',
  skipHeader: true,
  fileList: []
})

// 监听批量表单传感器类型变化
watch(() => batchForm.sensorType, (newType) => {
  const config = sensorConfig[newType]
  if (config) {
    batchForm.minValue = config.range[0]
    batchForm.maxValue = config.range[1]
  }
}, { immediate: true })

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
      timestamp: new Date(singleForm.timestamp).toISOString(),
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
  singleForm.deviceId = deviceList.value.length > 0 ? deviceList.value[0] : 'EQ-001'
  singleForm.sensorType = 'vibration'
  singleForm.value = 12.5
  singleForm.unit = 'mm/s'
  singleForm.timestamp = new Date().toISOString().slice(0, 19)
}

// CSV数据导入
const importCsvData = async () => {
  // 校验文件
  if (!csvForm.fileList || csvForm.fileList.length === 0) {
    ElMessage.warning('请选择要导入的CSV文件')
    return
  }
  // 校验必填字段
  if (!csvForm.deviceId) {
    ElMessage.warning('请输入设备ID')
    return
  }
  if (!csvForm.sensorColumns) {
    ElMessage.warning('请输入传感器列名')
    return
  }

  csvLoading.value = true
  flowActive.value = 1
  lastResponse.value = null

  try {
    const formData = new FormData()
    formData.append('file', csvForm.fileList[0].raw)
    formData.append('deviceId', csvForm.deviceId)
    formData.append('sensorColumns', csvForm.sensorColumns)
    if (csvForm.timestampColumn) {
      formData.append('timestampColumn', csvForm.timestampColumn)
    }
    formData.append('delimiter', csvForm.delimiter)
    formData.append('skipHeader', csvForm.skipHeader)

    const res = await collectionApi.importCsv(formData)
    flowActive.value = 4
    lastResponse.value = res.data
    ElMessage.success(`CSV导入成功！共 ${res.data.successCount || res.data.totalRows} 条数据`)
  } catch (error) {
    ElMessage.error('CSV导入失败: ' + error.message)
    flowActive.value = 0
  } finally {
    csvLoading.value = false
  }
}
</script>

<style scoped>
.collection-sensor {
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

.data-flow {
  margin-top: 20px;
}

.response-detail {
  margin-top: 24px;
  padding: 20px;
  background: linear-gradient(135deg, #f5f7fa 0%, #f0f9ff 100%);
  border-radius: 8px;
}

.response-detail h4 {
  margin: 0 0 12px;
  color: #303133;
}

.features-section {
  margin-top: 20px;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.feature-item {
  background: white;
  border-radius: 8px;
  padding: 12px;
  text-align: center;
  border: 1px solid #e8ecf1;
  transition: all 0.2s;
}

.feature-item:hover {
  box-shadow: 0 4px 12px rgba(0,0,0,0.08);
  transform: translateY(-2px);
}

.feature-label {
  display: block;
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}

.feature-value {
  display: block;
  font-size: 16px;
  font-weight: 600;
  color: #409eff;
}

.csv-result-section {
  margin-top: 20px;
}
</style>