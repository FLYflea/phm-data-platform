import axios from 'axios'

// 创建 axios 实例，指向 Gateway 服务 localhost:8080
const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    // 可以在这里添加 token 等认证信息
    console.log('Request:', config.method.toUpperCase(), config.url)
    return config
  },
  (error) => {
    console.error('Request Error:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    const data = response.data
    if (data.status === 'success' || data.status === 'UP') {
      return data
    }
    return Promise.reject(new Error(data.message || '请求失败'))
  },
  (error) => {
    console.error('Response Error:', error.message)
    return Promise.reject(error)
  }
)

// ==================== 数据采集 API ====================
export const collectionApi = {
  // 发送传感器数据
  sendSensorData: (data) => request.post('/collect/sensor', data),
  // 批量发送传感器数据
  sendBatchSensorData: (dataList) => request.post('/collect/sensor/batch', dataList)
}

// ==================== 数据存储 API ====================
export const storageApi = {
  // 保存时序数据
  saveTimeSeries: (data) => request.post('/storage/timeseries/save', data),
  // 批量保存时序数据
  saveBatchTimeSeries: (dataList) => request.post('/storage/timeseries/batch', dataList),
  // 查询时序数据
  queryTimeSeries: (params) => request.get('/storage/timeseries/query', { params }),
  // 聚合统计
  aggregateTimeSeries: (params) => request.get('/storage/timeseries/aggregate', { params }),
  // 保存设备节点
  saveEquipment: (data) => request.post('/storage/graph/equipment', data),
  // 保存组件节点
  saveComponent: (equipmentId, data) => request.post('/storage/graph/component', data, { params: { equipmentId } }),
  // 查询设备及其组件
  getEquipmentWithComponents: (equipmentId) => request.get(`/storage/graph/equipment/${equipmentId}`),
  // 保存文档
  saveDocument: (data) => request.post('/storage/document', data),
  // 搜索文档
  searchDocuments: (keyword) => request.get('/storage/document/search', { params: { keyword } })
}

// ==================== 数据计算 API ====================
export const computationApi = {
  // 数据同步
  syncData: (data) => request.post('/compute/sync', data),
  // 数据融合
  fuseData: (data) => request.post('/compute/fuse', data)
}

// ==================== 数据服务 API ====================
export const serviceApi = {
  // 统一查询
  unifiedQuery: (params) => request.get('/service/query', { params }),
  // 获取可视化数据
  getVisualizationData: (params) => request.get('/service/visualization', { params })
}

// ==================== 健康检查 API ====================
export const healthApi = {
  // Gateway 健康检查
  checkGateway: () => request.get('/actuator/health'),
  // 各服务健康检查
  checkService: (service) => request.get(`/${service}/health`)
}

export default request
