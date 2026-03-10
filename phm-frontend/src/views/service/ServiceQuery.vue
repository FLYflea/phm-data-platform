<template>
  <div class="service-query">
    <h2>统一数据查询服务</h2>
    <p class="desc">服务层核心功能：数据查询与封装</p>
    
    <el-card>
      <template #header>统一查询接口</template>
      <el-form :model="queryForm" label-width="100px">
        <el-form-item label="查询类型">
          <el-radio-group v-model="queryForm.type">
            <el-radio label="raw">原始数据</el-radio>
            <el-radio label="processed">处理后数据</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="设备ID">
          <el-input v-model="queryForm.deviceId" placeholder="EQ-001" />
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="queryForm.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="performQuery" :loading="loading">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="result" class="result-card">
      <template #header>查询结果</template>
      <pre>{{ JSON.stringify(result, null, 2) }}</pre>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { serviceApi } from '../../api/request'

const loading = ref(false)
const result = ref(null)

const queryForm = reactive({
  type: 'raw',
  deviceId: 'EQ-001',
  timeRange: [
    new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString().slice(0, 19),
    new Date().toISOString().slice(0, 19)
  ]
})

const performQuery = async () => {
  loading.value = true
  try {
    const params = {
      deviceId: queryForm.deviceId,
      startTime: queryForm.timeRange[0] + 'Z',
      endTime: queryForm.timeRange[1] + 'Z'
    }
    
    const res = queryForm.type === 'raw' 
      ? await serviceApi.queryRawData(params)
      : await serviceApi.queryProcessedData(params)
    
    result.value = res.data
    ElMessage.success('查询成功')
  } catch (error) {
    ElMessage.error('查询失败: ' + error.message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.service-query {
  padding: 20px;
}
.desc {
  color: #909399;
  margin-bottom: 20px;
}
.result-card {
  margin-top: 20px;
}
pre {
  background: #f5f7fa;
  padding: 15px;
  border-radius: 4px;
  max-height: 400px;
  overflow: auto;
}
</style>
