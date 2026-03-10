<template>
  <div class="storage-timeseries">
    <h2>时序数据查询</h2>
    <p class="desc">存储层核心功能：多维时序数据存储与查询</p>
    
    <el-card>
      <template #header>查询条件</template>
      <el-form :model="queryForm" inline>
        <el-form-item label="设备ID">
          <el-input v-model="queryForm.deviceId" placeholder="EQ-001" />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker
            v-model="queryForm.startTime"
            type="datetime"
            placeholder="开始时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker
            v-model="queryForm.endTime"
            type="datetime"
            placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="queryData" :loading="loading">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="dataList.length > 0" class="result-card">
      <template #header>查询结果 ({{ dataList.length }} 条)</template>
      <el-table :data="dataList" stripe>
        <el-table-column prop="deviceId" label="设备ID" width="120" />
        <el-table-column prop="sensorType" label="传感器类型" width="120" />
        <el-table-column prop="value" label="数值" width="100" />
        <el-table-column prop="timestamp" label="时间戳" width="180" />
        <el-table-column prop="mean" label="均值(特征)" width="100" />
        <el-table-column prop="rms" label="RMS(特征)" width="100" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { serviceApi } from '../../api/request'

const loading = ref(false)
const dataList = ref([])

const queryForm = reactive({
  deviceId: 'EQ-001',
  startTime: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString().slice(0, 19),
  endTime: new Date().toISOString().slice(0, 19)
})

const queryData = async () => {
  loading.value = true
  try {
    const res = await serviceApi.queryRawData({
      deviceId: queryForm.deviceId,
      startTime: queryForm.startTime + 'Z',
      endTime: queryForm.endTime + 'Z'
    })
    dataList.value = res.data.data || []
    ElMessage.success(`查询到 ${dataList.value.length} 条数据`)
  } catch (error) {
    ElMessage.error('查询失败: ' + error.message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.storage-timeseries {
  padding: 20px;
}
.desc {
  color: #909399;
  margin-bottom: 20px;
}
.result-card {
  margin-top: 20px;
}
</style>
