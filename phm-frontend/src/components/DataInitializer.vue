<template>
  <el-card class="initializer-card">
    <template #header>
      <div class="card-header">
        <span>测试数据初始化</span>
      </div>
    </template>

    <el-alert
      title="此功能用于批量生成测试数据，方便演示系统功能"
      type="info"
      :closable="false"
      style="margin-bottom: 20px"
    />

    <el-row :gutter="20">
      <el-col :span="8">
        <el-card shadow="hover" class="init-item">
          <h4>传感器数据</h4>
          <p>生成 2 个设备、2 种传感器类型</p>
          <p>最近 24 小时，每小时一条</p>
          <p class="data-count">约 96 条数据</p>
          <el-button 
            type="primary" 
            @click="initSensorData" 
            :loading="loading.sensor"
            style="width: 100%"
          >
            生成传感器数据
          </el-button>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card shadow="hover" class="init-item">
          <h4>设备图数据</h4>
          <p>生成 3 个设备节点</p>
          <p>生成 5 个组件节点</p>
          <p>建立设备-组件关系</p>
          <el-button 
            type="success" 
            @click="initGraphData" 
            :loading="loading.graph"
            style="width: 100%"
          >
            生成图数据
          </el-button>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card shadow="hover" class="init-item">
          <h4>文档数据</h4>
          <p>生成 3 个测试文档</p>
          <p>包含维护手册、故障案例等</p>
          <p class="data-count">支持搜索测试</p>
          <el-button 
            type="warning" 
            @click="initDocumentData" 
            :loading="loading.document"
            style="width: 100%"
          >
            生成文档数据
          </el-button>
        </el-card>
      </el-col>
    </el-row>

    <el-divider />

    <el-button 
      type="danger" 
      size="large" 
      @click="initAllData" 
      :loading="loading.all"
      style="width: 100%"
    >
      一键生成所有测试数据
    </el-button>

    <el-divider />

    <h4>初始化日志</h4>
    <div class="log-container">
      <div 
        v-for="(log, index) in logs" 
        :key="index" 
        :class="['log-item', log.type]"
      >
        <span class="log-time">{{ log.time }}</span>
        <span class="log-message">{{ log.message }}</span>
      </div>
      <el-empty v-if="logs.length === 0" description="暂无日志" />
    </div>
  </el-card>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { storageApi } from '../api/request'
import { 
  generateQuickTestData, 
  equipmentTemplates, 
  componentTemplates,
  documentTemplates 
} from '../api/mockData'

const loading = reactive({
  sensor: false,
  graph: false,
  document: false,
  all: false
})

const logs = ref([])

const addLog = (message, type = 'info') => {
  const now = new Date().toLocaleTimeString('zh-CN')
  logs.value.unshift({ time: now, message, type })
}

// 分批发送数据，避免请求过大
const batchSendData = async (dataList, batchSize = 50, api) => {
  const results = []
  for (let i = 0; i < dataList.length; i += batchSize) {
    const batch = dataList.slice(i, i + batchSize)
    try {
      const res = await api(batch)
      results.push(res)
      addLog(`批次 ${Math.floor(i / batchSize) + 1}/${Math.ceil(dataList.length / batchSize)} 发送成功`, 'success')
    } catch (error) {
      addLog(`批次 ${Math.floor(i / batchSize) + 1} 发送失败: ${error.message}`, 'error')
      throw error
    }
  }
  return results
}

const initSensorData = async () => {
  loading.sensor = true
  addLog('开始生成传感器数据...')
  
  try {
    const data = generateQuickTestData()
    addLog(`生成 ${data.length} 条传感器数据`)
    
    // 分批发送
    await batchSendData(data, 50, storageApi.saveBatchTimeSeries)
    
    ElMessage.success(`成功导入 ${data.length} 条传感器数据`)
    addLog('传感器数据初始化完成', 'success')
  } catch (error) {
    ElMessage.error('初始化失败: ' + error.message)
    addLog('传感器数据初始化失败: ' + error.message, 'error')
  } finally {
    loading.sensor = false
  }
}

const initGraphData = async () => {
  loading.graph = true
  addLog('开始生成图数据...')
  
  try {
    // 创建设备
    for (const equipment of equipmentTemplates) {
      await storageApi.saveEquipment(equipment)
      addLog(`创建设备: ${equipment.name}`, 'success')
    }
    
    // 创建组件（关联到第一个设备）
    for (const component of componentTemplates) {
      await storageApi.saveComponent('EQ-001', component)
      addLog(`创建组件: ${component.name}`, 'success')
    }
    
    ElMessage.success('图数据初始化完成')
    addLog('图数据初始化完成', 'success')
  } catch (error) {
    ElMessage.error('初始化失败: ' + error.message)
    addLog('图数据初始化失败: ' + error.message, 'error')
  } finally {
    loading.graph = false
  }
}

const initDocumentData = async () => {
  loading.document = true
  addLog('开始生成文档数据...')
  
  try {
    for (const doc of documentTemplates) {
      await storageApi.saveDocument(doc)
      addLog(`创建文档: ${doc.title}`, 'success')
    }
    
    ElMessage.success('文档数据初始化完成')
    addLog('文档数据初始化完成', 'success')
  } catch (error) {
    ElMessage.error('初始化失败: ' + error.message)
    addLog('文档数据初始化失败: ' + error.message, 'error')
  } finally {
    loading.document = false
  }
}

const initAllData = async () => {
  loading.all = true
  addLog('=== 开始一键初始化所有数据 ===', 'info')
  
  try {
    await initSensorData()
    await initGraphData()
    await initDocumentData()
    
    ElMessage.success('所有测试数据初始化完成！')
    addLog('=== 所有数据初始化完成 ===', 'success')
  } catch (error) {
    ElMessage.error('批量初始化失败')
    addLog('批量初始化失败', 'error')
  } finally {
    loading.all = false
  }
}
</script>

<style scoped>
.initializer-card {
  margin-bottom: 20px;
}

.card-header {
  font-weight: bold;
  font-size: 16px;
}

.init-item {
  text-align: center;
  height: 100%;
}

.init-item h4 {
  margin: 0 0 10px 0;
  color: #303133;
}

.init-item p {
  margin: 5px 0;
  color: #606266;
  font-size: 14px;
}

.data-count {
  color: #409eff;
  font-weight: bold;
}

.log-container {
  max-height: 300px;
  overflow-y: auto;
  background: #f5f7fa;
  border-radius: 4px;
  padding: 10px;
}

.log-item {
  padding: 8px;
  margin-bottom: 5px;
  border-radius: 4px;
  font-size: 13px;
  font-family: monospace;
}

.log-time {
  color: #909399;
  margin-right: 10px;
}

.log-item.success {
  background: #f0f9eb;
  color: #67c23a;
}

.log-item.error {
  background: #fef0f0;
  color: #f56c6c;
}

.log-item.info {
  background: #f4f4f5;
  color: #909399;
}
</style>
