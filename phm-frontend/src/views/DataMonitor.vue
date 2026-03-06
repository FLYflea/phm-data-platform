<template>
  <div class="data-monitor">
    <h2>数据监控</h2>
    
    <el-row :gutter="20">
      <el-col :span="12">
        <SensorDataForm />
      </el-col>
      
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>系统状态</span>
            </div>
          </template>
          
          <div class="status-list">
            <div class="status-item">
              <span class="status-label">Gateway 服务:</span>
              <el-tag :type="gatewayStatus.type">{{ gatewayStatus.text }}</el-tag>
            </div>
            <div class="status-item">
              <span class="status-label">数据采集服务:</span>
              <el-tag :type="collectionStatus.type">{{ collectionStatus.text }}</el-tag>
            </div>
            <div class="status-item">
              <span class="status-label">数据存储服务:</span>
              <el-tag :type="storageStatus.type">{{ storageStatus.text }}</el-tag>
            </div>
            <div class="status-item">
              <span class="status-label">数据计算服务:</span>
              <el-tag :type="computationStatus.type">{{ computationStatus.text }}</el-tag>
            </div>
          </div>
          
          <el-button type="primary" @click="checkHealth" :loading="checking">
            刷新状态
          </el-button>
        </el-card>
      </el-col>
    </el-row>

    <el-divider />

    <DataInitializer />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import SensorDataForm from '../components/SensorDataForm.vue'
import DataInitializer from '../components/DataInitializer.vue'
import { healthApi } from '../api/request'

const checking = ref(false)
const gatewayStatus = ref({ type: 'info', text: '未知' })
const collectionStatus = ref({ type: 'info', text: '未知' })
const storageStatus = ref({ type: 'info', text: '未知' })
const computationStatus = ref({ type: 'info', text: '未知' })

const checkHealth = async () => {
  checking.value = true
  
  // 检查 Gateway
  try {
    await healthApi.checkGateway()
    gatewayStatus.value = { type: 'success', text: '正常' }
  } catch {
    gatewayStatus.value = { type: 'danger', text: '异常' }
  }
  
  // 检查各服务
  const services = [
    { name: 'data-collection', ref: collectionStatus },
    { name: 'data-storage', ref: storageStatus },
    { name: 'data-computation', ref: computationStatus }
  ]
  
  for (const service of services) {
    try {
      await healthApi.checkService(service.name)
      service.ref.value = { type: 'success', text: '正常' }
    } catch {
      service.ref.value = { type: 'danger', text: '异常' }
    }
  }
  
  checking.value = false
  ElMessage.success('状态检查完成')
}

onMounted(() => {
  checkHealth()
})
</script>

<style scoped>
.data-monitor {
  padding: 20px;
}

h2 {
  margin-bottom: 20px;
  color: #303133;
}

.card-header {
  font-weight: bold;
}

.status-list {
  margin-bottom: 20px;
}

.status-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #ebeef5;
}

.status-item:last-child {
  border-bottom: none;
}

.status-label {
  color: #606266;
}
</style>