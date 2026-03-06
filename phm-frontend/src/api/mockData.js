// 批量生成传感器测试数据

/**
 * 生成指定时间范围内的模拟传感器数据
 * @param {string} deviceId - 设备ID
 * @param {string} sensorType - 传感器类型
 * @param {number} count - 数据条数
 * @param {Date} startTime - 开始时间
 * @param {number} intervalMinutes - 数据间隔（分钟）
 */
export function generateSensorData(deviceId, sensorType, count, startTime = new Date(), intervalMinutes = 10) {
  const data = []
  
  // 根据传感器类型设置基础值和波动范围
  const config = {
    temperature: { base: 45, range: 15, unit: '°C' },
    vibration: { base: 2.5, range: 1.5, unit: 'm/s²' },
    pressure: { base: 100, range: 30, unit: 'kPa' },
    current: { base: 10, range: 5, unit: 'A' }
  }
  
  const { base, range, unit } = config[sensorType] || config.temperature
  
  for (let i = 0; i < count; i++) {
    const timestamp = new Date(startTime.getTime() - i * intervalMinutes * 60 * 1000)
    
    // 生成带有趋势和随机波动的数据
    const trend = Math.sin(i / 10) * range * 0.3  // 趋势项
    const random = (Math.random() - 0.5) * range  // 随机波动
    const value = base + trend + random
    
    data.push({
      deviceId,
      sensorType,
      value: parseFloat(value.toFixed(2)),
      unit,
      timestamp: timestamp.toISOString(),
      location: `${deviceId}-传感器位置`
    })
  }
  
  return data.reverse()  // 按时间正序排列
}

/**
 * 生成多设备、多类型的批量测试数据
 */
export function generateBatchTestData() {
  const devices = ['DEV-001', 'DEV-002', 'DEV-003']
  const sensorTypes = ['temperature', 'vibration', 'pressure', 'current']
  const allData = []
  
  // 生成最近7天的数据，每10分钟一条
  const now = new Date()
  const countPerDevice = 7 * 24 * 6  // 7天 * 24小时 * 6条/小时
  
  devices.forEach(deviceId => {
    sensorTypes.forEach(sensorType => {
      const data = generateSensorData(deviceId, sensorType, countPerDevice, now, 10)
      allData.push(...data)
    })
  })
  
  return allData
}

/**
 * 生成小批量测试数据（用于快速测试）
 */
export function generateQuickTestData() {
  const allData = []
  const devices = ['DEV-001', 'DEV-002']
  const sensorTypes = ['temperature', 'vibration']
  const now = new Date()
  
  devices.forEach(deviceId => {
    sensorTypes.forEach(sensorType => {
      // 生成最近24小时，每小时一条
      const data = generateSensorData(deviceId, sensorType, 24, now, 60)
      allData.push(...data)
    })
  })
  
  return allData
}

// 设备信息模板
export const equipmentTemplates = [
  {
    equipmentId: 'EQ-001',
    name: '主轴承设备',
    type: '轴承',
    location: '车间A-01',
    status: 'RUNNING',
    description: '主轴核心轴承设备'
  },
  {
    equipmentId: 'EQ-002',
    name: '齿轮箱设备',
    type: '齿轮箱',
    location: '车间A-02',
    status: 'RUNNING',
    description: '传动齿轮箱'
  },
  {
    equipmentId: 'EQ-003',
    name: '电机设备',
    type: '电机',
    location: '车间B-01',
    status: 'STANDBY',
    description: '主驱动电机'
  }
]

// 组件信息模板
export const componentTemplates = [
  { componentId: 'COMP-001', name: '轴承外圈', type: '零部件', status: 'NORMAL' },
  { componentId: 'COMP-002', name: '轴承内圈', type: '零部件', status: 'NORMAL' },
  { componentId: 'COMP-003', name: '滚珠', type: '零部件', status: 'WARNING' },
  { componentId: 'COMP-004', name: '齿轮A', type: '零部件', status: 'NORMAL' },
  { componentId: 'COMP-005', name: '齿轮B', type: '零部件', status: 'NORMAL' }
]

// 文档模板
export const documentTemplates = [
  {
    docId: 'DOC-001',
    title: '设备维护手册',
    content: '主轴承设备的日常维护指南，包括润滑、清洁、检查等操作步骤...',
    docType: 'manual',
    tags: ['维护', '轴承', '操作指南']
  },
  {
    docId: 'DOC-002',
    title: '故障案例分析',
    content: '2023年齿轮箱故障案例分析，故障原因：润滑不足，处理方案...',
    docType: 'report',
    tags: ['故障', '齿轮箱', '案例']
  },
  {
    docId: 'DOC-003',
    title: '传感器安装规范',
    content: '振动传感器和温度传感器的安装位置、安装方法、接线规范...',
    docType: 'specification',
    tags: ['传感器', '安装', '规范']
  }
]
