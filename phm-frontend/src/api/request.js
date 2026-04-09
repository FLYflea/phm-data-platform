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
  parseText: (data) => request.post('/collection/document/text', data),
  // CSV数据批量导入
  importCsv: (formData) => request.post('/collection/sensor/import-csv', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

// ==================== 计算层 API ====================
export const computationApi = {
  // 时间同步
  timeSync: (data) => request.post('/computation/sync/uncertainty', data),
  // 数据融合 - 修正路径为 /computation/fusion/pda
  dataFusion: (data) => request.post('/computation/fusion/pda', data),
  // 特征提取 - 时域
  extractFeatures: (data) => request.post('/computation/feature/time-domain', data),
  // 特征提取 - 频域
  extractFrequencyFeatures: (data) => request.post('/computation/feature/frequency-domain', data),
  // 特征提取 - 时频域（小波变换）
  extractTimeFrequencyFeatures: (data) => request.post('/computation/feature/time-frequency', data),
  // 特征提取 - 全部（时域+频域+时频域）
  extractAllFeatures: (data) => request.post('/computation/feature/all', data),
  // 知识图谱构建 - 支持图像和文本
  buildKnowledgeGraphFromImage: (formData) => request.post('/computation/knowledge/build/image', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
  buildKnowledgeGraphFromText: (data) => request.post('/computation/knowledge/build/text', data),
  // 维修时间抽取
  extractMaintenanceTime: (text) => request.post('/computation/maintenance/extract-time', { text }),
  // 流水线接口
  runPipeline: (data) => request.post('/computation/pipeline/full', data),
  runSimplePipeline: (data) => request.post('/computation/pipeline/simple', data)
}

// ==================== 存储层 API (内部调用) ====================
export const storageApi = {
  // 时序数据查询
  queryTimeSeries: (params) => request.get('/storage/timeseries/query', { params }),
  // 时序数据聚合统计
  aggregateTimeSeries: (params) => request.get('/storage/timeseries/aggregate', { params }),
  // 获取所有设备ID列表（动态下拉框）
  getAllDevices: () => request.get('/storage/timeseries/devices'),
  // 获取所有传感器类型列表（动态下拉框）
  getAllSensorTypes: () => request.get('/storage/timeseries/sensor-types'),
  // 获取指定设备的传感器类型列表
  getSensorTypesByDevice: (deviceId) => request.get(`/storage/timeseries/sensor-types/${deviceId}`),
  // 知识图谱查询 - 设备及其组件
  queryEquipmentGraph: (equipmentId) => request.get(`/storage/graph/equipment/${equipmentId}`),
  // 知识图谱查询 - 组件关系路径
  queryGraphPath: (params) => request.get('/storage/graph/path', { params }),
  // 保存设备节点
  saveEquipment: (data) => request.post('/storage/graph/equipment', data),
  // 保存组件节点
  saveComponent: (equipmentId, data) => request.post(`/storage/graph/component?equipmentId=${equipmentId}`, data),
  // 创建组件关系
  createRelation: (params) => request.post('/storage/graph/relation', {}, { params }),
  // FMECA故障模式查询
  queryFailureModes: (equipmentId) => request.get(`/storage/fmeca/failure-mode/equipment/${equipmentId}`),
  // 保存故障模式
  saveFailureMode: (data) => request.post('/storage/fmeca/failure-mode', data),
  // 知识图谱统计
  getGraphStats: () => request.get('/storage/graph/stats'),
  // 查询所有设备列表
  queryAllEquipments: () => request.get('/storage/graph/equipments')
}

// ==================== 服务层 API ====================
export const serviceApi = {
  // 统一查询 - 原始数据
  queryRawData: (data) => request.post('/service/query/raw', data),
  // 统一查询 - 处理后数据
  queryProcessedData: (data) => request.post('/service/query/processed', data),
  // 可视化 - 时序图表
  getVisualizationData: (params) => request.get('/service/chart/timeseries', { params }),
  // 可视化 - 饼图
  getPieData: (params) => request.get('/service/chart/pie', { params }),
  // 可视化 - 雷达图
  getRadarData: (params) => request.get('/service/chart/radar', { params }),
  // 可视化 - 热力图
  getHeatmapData: (params) => request.get('/service/chart/heatmap', { params }),
  // 多维分析 - 统计指标
  getStatistics: (params) => request.get('/service/analysis/statistics', { params }),
  // 多维分析 - 聚合
  getAggregation: (params) => request.get('/service/analysis/aggregate', { params }),
  // 数据分发 - 注册订阅
  distributeRegister: (data) => request.post('/service/distribute/register', data),
  // 数据分发 - 取消订阅
  distributeUnregister: (id) => request.delete(`/service/distribute/unregister/${id}`),
  // 数据分发 - 订阅列表
  distributeList: () => request.get('/service/distribute/list'),
  // 数据分发 - 触发推送
  distributeTrigger: (id) => request.post(`/service/distribute/trigger/${id}`),
  // 数据分发 - 事件日志
  distributeEvents: () => request.get('/service/distribute/events'),
  // 数据流 - 连接信息
  streamConnections: () => request.get('/service/stream/connections'),
  // 数据流 - 窗口统计
  streamWindowStats: (params) => request.get('/service/stream/window-stats', { params })
}

export default request
