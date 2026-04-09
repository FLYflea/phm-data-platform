<template>
  <div class="computation-feature">
    <div class="page-header">
      <h2><el-icon><TrendCharts /></el-icon> 特征工程服务</h2>
      <p class="desc">计算层核心功能：时域/频域/时频域（小波变换）特征提取</p>
    </div>
    
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span><el-icon><DataLine /></el-icon> 数据输入</span>
        </div>
      </template>
      <el-form :model="form" label-width="120px">
        <el-form-item label="设备ID">
          <el-select v-model="form.deviceId" placeholder="选择设备" style="width: 150px">
            <el-option v-for="device in deviceList" :key="device" :label="device" :value="device" />
          </el-select>
        </el-form-item>
        <el-form-item label="传感器类型">
          <el-select v-model="form.sensorType" style="width: 140px">
            <el-option v-for="type in sensorTypeList" :key="type" :label="type" :value="type" />
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
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="extractAll" :loading="loadingAll">
            全部提取
          </el-button>
          <el-button type="success" @click="loadFromStorage" :loading="loadingFromStorage">
            从存储层加载
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 特征结果展示 -->
    <el-card v-if="hasResults" class="result-card">
      <template #header>提取结果</template>
      <el-tabs v-model="activeTab">
        <!-- 时域特征 -->
        <el-tab-pane label="时域特征" name="timeDomain">
          <el-descriptions v-if="timeDomainFeatures" :column="2" border size="small">
            <el-descriptions-item v-for="(value, key) in timeDomainFeatures" :key="key">
              <template #label>
                <el-tooltip :content="featureDescMap[key] || ''" placement="top" :disabled="!featureDescMap[key]">
                  <span>{{ featureNameMap[key] || key }}
                    <span class="en-name">{{ key }}</span>
                  </span>
                </el-tooltip>
              </template>
              {{ typeof value === 'number' ? value.toFixed(4) : value }}
            </el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="点击「全部提取」获取时域特征" />
        </el-tab-pane>

        <!-- 频域特征 -->
        <el-tab-pane label="频域特征" name="frequencyDomain">
          <el-descriptions v-if="frequencyDomainFeatures" :column="2" border size="small">
            <el-descriptions-item v-for="(value, key) in frequencyDomainFeatures" :key="key">
              <template #label>
                <el-tooltip :content="featureDescMap[key] || ''" placement="top" :disabled="!featureDescMap[key]">
                  <span>{{ featureNameMap[key] || key }}
                    <span class="en-name">{{ key }}</span>
                  </span>
                </el-tooltip>
              </template>
              <template v-if="Array.isArray(value)">
                <el-tag v-for="(item, i) in value" :key="i" size="small" style="margin: 2px;">
                  频率{{ item.index }}: {{ typeof item.amplitude === 'number' ? item.amplitude.toFixed(4) : item.amplitude }}
                </el-tag>
              </template>
              <template v-else>
                {{ typeof value === 'number' ? value.toFixed(4) : value }}
              </template>
            </el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="点击「全部提取」获取频域特征" />
        </el-tab-pane>

        <!-- 时频域特征 -->
        <el-tab-pane label="时频域特征（小波）" name="timeFrequency">
          <el-descriptions v-if="timeFrequencyFeatures" :column="2" border size="small">
            <el-descriptions-item v-for="(value, key) in timeFrequencyFeatures" :key="key">
              <template #label>
                <el-tooltip :content="featureDescMap[key] || ''" placement="top" :disabled="!featureDescMap[key]">
                  <span>{{ featureNameMap[key] || key }}
                    <span class="en-name">{{ key }}</span>
                  </span>
                </el-tooltip>
              </template>
              {{ typeof value === 'number' ? value.toFixed(4) : value }}
            </el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="点击「全部提取」获取时频域特征" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { TrendCharts, DataLine } from '@element-plus/icons-vue'
import { computationApi, storageApi } from '../../api/request'

const loadingAll = ref(false)
const loadingFromStorage = ref(false)
const activeTab = ref('timeDomain')
const dataSourceInfo = ref(null)

const timeDomainFeatures = ref(null)
const frequencyDomainFeatures = ref(null)
const timeFrequencyFeatures = ref(null)

const hasResults = computed(() => timeDomainFeatures.value || frequencyDomainFeatures.value || timeFrequencyFeatures.value)

// 动态设备和传感器类型列表
const deviceList = ref([])
const sensorTypeList = ref([])

const form = reactive({
  deviceId: '',
  sensorType: '',
  values: '12.5, 13.8, 11.2, 15.6, 14.1, 10.8, 16.3, 13.5, 12.0, 14.7, 11.5, 15.2, 13.0, 12.8, 14.5, 11.9'
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
    if (deviceList.value.length > 0 && !form.deviceId) {
      form.deviceId = deviceList.value[0]
    }
    if (sensorTypeList.value.length > 0 && !form.sensorType) {
      form.sensorType = sensorTypeList.value[0]
    }
  } catch (e) {
    console.warn('加载设备/传感器列表失败，使用默认值', e)
    deviceList.value = ['EQ-001', 'EQ-002', 'EQ-003']
    sensorTypeList.value = ['temperature', 'vibration', 'pressure', 'current']
    form.deviceId = 'EQ-001'
    form.sensorType = 'vibration'
  }
}

onMounted(() => {
  loadDevicesAndSensorTypes()
})

// 特征中英文名映射
const featureNameMap = {
  // 时域
  mean: '均值', variance: '方差', stdDev: '标准差', rms: '均方根值',
  peak: '峰值', max: '最大值', min: '最小值', peakToPeak: '峰峰值',
  waveformIndex: '波形指标', crestFactor: '峰值因子', kurtosis: '峭度', skewness: '偏度',
  // 频域
  dominantFrequencyIndex: '主频索引', dominantAmplitude: '主频幅值', dcComponent: '直流分量',
  totalSpectralEnergy: '总谱能量', lowBandEnergy: '低频能量', midBandEnergy: '中频能量',
  highBandEnergy: '高频能量', lowBandRatio: '低频占比', highBandRatio: '高频占比',
  top3Frequencies: '前3主频',
  // 时频域
  waveletEnergy: '小波能量', waveletEntropy: '小波熵', energyRatio: '高低频能量比',
  maxDetailCoeff: '最大细节系数', approximationMean: '近似系数均值',
  decompositionLevels: '分解层数',
  level1Energy: '第1层能量', level2Energy: '第2层能量', level3Energy: '第3层能量',
  level4Energy: '第4层能量', level5Energy: '第5层能量'
}

// 特征含义说明
const featureDescMap = {
  mean: '所有数据点的算术平均值，反映信号的整体水平',
  variance: '数据分散程度的度量，方差越大波动越剧烈',
  stdDev: '方差的平方根，与原始数据同单位',
  rms: '均方根值，反映信号的有效值/能量大小',
  peak: '信号的最大绝对值',
  peakToPeak: '最大值与最小值之差，反映信号的振幅范围',
  waveformIndex: 'RMS与绝对均值之比，正弦波约1.11',
  crestFactor: '峰值与RMS之比，反映冲击性，正弦波约1.414',
  kurtosis: '分布尖锐程度，正态分布约3，冲击信号>5',
  skewness: '分布对称性，0=对称，正值=右偏，负值=左偏',
  dominantFrequencyIndex: '幅值最大的频率分量对应的索引',
  dominantAmplitude: '主频率分量的幅值大小',
  dcComponent: '信号的直流分量（零频分量），反映信号的均值偏移',
  totalSpectralEnergy: '全部频率分量的能量总和',
  lowBandRatio: '低频能量占总能量的比例',
  highBandRatio: '高频能量占总能量的比例',
  waveletEnergy: '小波分解各层细节系数的能量总和，反映信号非平稳性',
  waveletEntropy: '各层能量分布的Shannon熵，熵越大信号越复杂',
  energyRatio: '高频层能量与低频层能量的比值，反映信号频率分布特性',
  maxDetailCoeff: '所有细节系数中的最大绝对值，反映瞬态冲击强度',
  approximationMean: '最终近似系数的均值，反映信号的趋势成分',
  decompositionLevels: 'Haar小波分解的层数，由信号长度自适应决定（最多5层）'
}

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
    
    const maxLoad = 50
    const originalCount = data.length
    if (data.length > maxLoad) {
      data = data.sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp)).slice(0, maxLoad)
    }
    
    dataSourceInfo.value = {
      deviceId: form.deviceId,
      sensorType: form.sensorType,
      totalCount: originalCount,
      loadedCount: data.length
    }
    
    const values = data.map(d => d.value)
    form.values = values.map(v => v.toFixed(2)).join(', ')
    
    ElMessage.success(`从存储层加载了 ${data.length} 条数据`)
  } catch (error) {
    ElMessage.error('加载失败: ' + error.message)
  } finally {
    loadingFromStorage.value = false
  }
}

const extractAll = async () => {
  const values = form.values.split(',').map(v => parseFloat(v.trim())).filter(v => !isNaN(v))
  if (values.length < 4) {
    ElMessage.warning('至少需要4个数据点')
    return
  }

  loadingAll.value = true
  timeDomainFeatures.value = null
  frequencyDomainFeatures.value = null
  timeFrequencyFeatures.value = null

  try {
    const res = await computationApi.extractAllFeatures({ values })
    const data = res.data
    timeDomainFeatures.value = data.timeDomain || null
    frequencyDomainFeatures.value = data.frequencyDomain || null
    timeFrequencyFeatures.value = data.timeFrequency || null

    const totalCount = Object.keys(data.timeDomain || {}).length +
                       Object.keys(data.frequencyDomain || {}).length +
                       Object.keys(data.timeFrequency || {}).length
    ElMessage.success(`成功提取 ${totalCount} 个特征（时域+频域+时频域）`)
  } catch (error) {
    ElMessage.error('特征提取失败: ' + error.message)
  } finally {
    loadingAll.value = false
  }
}
</script>

<style scoped>
.computation-feature {
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

.result-card {
  margin-top: 20px;
}
.en-name {
  font-size: 11px;
  color: #c0c4cc;
  margin-left: 4px;
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
