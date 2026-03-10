/**
 * API 请求封装 - 前后端拉通核心配置
 * 
 * 四层架构API路径：
 * - 采集层: /api/collection/**  → 端口8101
 * - 计算层: /api/computation/** → 端口8102
 * - 存储层: /api/storage/**     → 端口8103 (内部服务，不直接暴露)
 * - 服务层: /api/service/**     → 端口8104
 * 
 * 所有请求通过 Gateway (8080) 统一路由
 */

import axios from 'axios'

// 创建 axios 实例
const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    console.log(`[Request] ${config.method.toUpperCase()} ${config.url}`)
    return config
  },
  (error) => {
    console.error('[Request Error]', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    const data = response.data
    console.log(`[Response] ${response.config.url}:`, data)
    
    // 统一响应格式处理
    const isSuccess = data.status === 'success' || 
                      data.status === 'UP' ||
                      (data.data && data.data.status === 'success')
    
    if (isSuccess) {
      return data
    }
    return Promise.reject(new Error(data.message || '请求失败'))
  },
  (error) => {
    console.error('[Response Error]', error.message)
    return Promise.reject(error)
  }
)

// ==================== 采集层 API ====================
export const collectionApi = {
  // 单条传感器数据
  sendSensorData: (data) => request.post('/collection/sensor', data),
  // 批量传感器数据
  sendBatchSensorData: (data) => request.post('/collection/sensor/batch', data),
  // 图像解析
  parseImage: (formData) => request.post('/collection/document/image', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
  // 文本解析
  parseText: (data) => request.post('/collection/document/text', data)
}

// ==================== 计算层 API ====================
export const computationApi = {
  // 时间同步
  timeSync: (data) => request.post('/computation/sync/uncertainty', data),
  // 数据融合
  dataFusion: (data) => request.post('/computation/fusion/probabilistic', data),
  // 特征提取
  extractFeatures: (data) => request.post('/computation/feature/time-domain', data),
  // 知识图谱构建
  buildKnowledgeGraph: (data) => request.post('/computation/knowledge/build', data),
  // 维修时间抽取
  extractMaintenanceTime: (text) => request.post('/computation/maintenance/extract-time', { text })
}

// ==================== 存储层 API (内部调用) ====================
export const storageApi = {
  // 时序数据查询
  queryTimeSeries: (params) => request.get('/storage/timeseries/query', { params }),
  // 知识图谱查询
  queryKnowledgeGraph: (equipmentId) => request.get(`/storage/graph/equipment/${equipmentId}/components`)
}

// ==================== 服务层 API ====================
export const serviceApi = {
  // 原始数据查询
  queryRawData: (data) => request.post('/service/query/raw', data),
  // 处理后数据查询
  queryProcessedData: (data) => request.post('/service/query/processed', data),
  // 可视化数据
  getVisualizationData: (params) => request.get('/service/visualization/timeseries', { params }),
  // 聚合统计
  getAggregation: (params) => request.get('/service/analysis/aggregate', { params })
}

export default request
