<template>
  <div class="data-query">
    <h2>数据查询</h2>
    
    <el-card class="query-card">
      <template #header>
        <div class="card-header">
          <span>时序数据查询</span>
        </div>
      </template>
      
      <el-form :model="queryForm" inline>
        <el-form-item label="设备ID">
          <el-input v-model="queryForm.deviceId" placeholder="如 DEV-001" />
        </el-form-item>
        
        <el-form-item label="传感器类型">
          <el-select v-model="queryForm.sensorType" placeholder="全部" clearable>
            <el-option label="温度" value="temperature" />
            <el-option label="振动" value="vibration" />
            <el-option label="压力" value="pressure" />
            <el-option label="电流" value="current" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="开始时间">
          <el-date-picker
            v-model="queryForm.start"
            type="datetime"
            placeholder="开始时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
        </el-form-item>
        
        <el-form-item label="结束时间">
          <el-date-picker
            v-model="queryForm.end"
            type="datetime"
            placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
        </el-form-item>
        
        <el-form-item>
          <el-button type="primary" @click="handleQuery" :loading="loading">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    
    <el-card class="result-card" v-if="queryResult.length > 0">
      <template #header>
        <div class="card-header">
          <span>查询结果 (共 {{ queryResult.length }} 条)</span>
        </div>
      </template>
      
      <el-table :data="queryResult" style="width: 100%" max-height="400">
        <el-table-column prop="deviceId" label="设备ID" width="120" />
        <el-table-column prop="sensorType" label="传感器类型" width="120" />
        <el-table-column prop="value" label="数值" width="100">
          <template #default="scope">
            {{ scope.row.value.toFixed(2) }}
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column prop="timestamp" label="时间戳" width="180">
          <template #default="scope">
            {{ formatTime(scope.row.timestamp) }}
          </template>
        </el-table-column>
        <el-table-column prop="location" label="位置" />
      </el-table>
    </el-card>
    
    <el-empty v-else-if="hasQueried" description="暂无数据" />
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { storageApi } from '../api/request'

const loading = ref(false)
const hasQueried = ref(false)
const queryResult = ref([])

const queryForm = reactive({
  deviceId: '',
  sensorType: '',
  start: '',
  end: ''
})

const handleQuery = async () => {
  if (!queryForm.deviceId) {
    ElMessage.warning('请输入设备ID')
    return
  }
  
  if (!queryForm.start || !queryForm.end) {
    // 默认查询最近24小时
    const now = new Date()
    const yesterday = new Date(now - 24 * 60 * 60 * 1000)
    queryForm.end = now.toISOString().slice(0, 19)
    queryForm.start = yesterday.toISOString().slice(0, 19)
  }
  
  loading.value = true
  hasQueried.value = true
  
  try {
    const params = {
      deviceId: queryForm.deviceId,
      sensorType: queryForm.sensorType || undefined,
      start: queryForm.start + '.000Z',
      end: queryForm.end + '.000Z'
    }
    
    const res = await storageApi.queryTimeSeries(params)
    queryResult.value = res.data || []
    
    if (queryResult.value.length === 0) {
      ElMessage.info('未找到匹配的数据')
    } else {
      ElMessage.success(`查询成功，找到 ${queryResult.value.length} 条数据`)
    }
  } catch (error) {
    ElMessage.error('查询失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  queryForm.deviceId = ''
  queryForm.sensorType = ''
  queryForm.start = ''
  queryForm.end = ''
  queryResult.value = []
  hasQueried.value = false
}

const formatTime = (timestamp) => {
  if (!timestamp) return '-'
  const date = new Date(timestamp)
  return date.toLocaleString('zh-CN')
}
</script>

<style scoped>
.data-query {
  padding: 20px;
}

h2 {
  margin-bottom: 20px;
  color: #303133;
}

.query-card {
  margin-bottom: 20px;
}

.card-header {
  font-weight: bold;
}

.result-card {
  margin-top: 20px;
}
</style>
