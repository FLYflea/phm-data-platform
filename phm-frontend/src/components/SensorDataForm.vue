<template>
  <el-card class="sensor-form-card">
    <template #header>
      <div class="card-header">
        <span>传感器数据录入</span>
      </div>
    </template>

    <el-form :model="formData" :rules="rules" ref="formRef" label-width="120px">
      <el-form-item label="设备ID" prop="deviceId">
        <el-input v-model="formData.deviceId" placeholder="请输入设备ID，如 DEV-001" />
      </el-form-item>

      <el-form-item label="传感器类型" prop="sensorType">
        <el-select v-model="formData.sensorType" placeholder="请选择传感器类型" style="width: 100%">
          <el-option label="温度传感器" value="temperature" />
          <el-option label="振动传感器" value="vibration" />
          <el-option label="压力传感器" value="pressure" />
          <el-option label="电流传感器" value="current" />
        </el-select>
      </el-form-item>

      <el-form-item label="数值" prop="value">
        <el-input-number v-model="formData.value" :precision="2" :step="0.1" style="width: 100%" />
      </el-form-item>

      <el-form-item label="单位" prop="unit">
        <el-input v-model="formData.unit" placeholder="如 °C, m/s², Pa, A" />
      </el-form-item>

      <el-form-item label="时间戳" prop="timestamp">
        <el-date-picker
          v-model="formData.timestamp"
          type="datetime"
          placeholder="选择日期时间"
          style="width: 100%"
          value-format="YYYY-MM-DDTHH:mm:ss"
        />
      </el-form-item>

      <el-form-item label="位置" prop="location">
        <el-input v-model="formData.location" placeholder="传感器安装位置" />
      </el-form-item>

      <el-form-item>
        <el-button type="primary" @click="handleSubmit" :loading="loading">提交</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { collectionApi } from '../api/request'

const formRef = ref(null)
const loading = ref(false)

const formData = reactive({
  deviceId: '',
  sensorType: '',
  value: 0,
  unit: '',
  timestamp: new Date().toISOString().slice(0, 19),
  location: ''
})

const rules = {
  deviceId: [{ required: true, message: '请输入设备ID', trigger: 'blur' }],
  sensorType: [{ required: true, message: '请选择传感器类型', trigger: 'change' }],
  value: [{ required: true, message: '请输入数值', trigger: 'blur' }],
  unit: [{ required: true, message: '请输入单位', trigger: 'blur' }],
  timestamp: [{ required: true, message: '请选择时间戳', trigger: 'change' }]
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        // 添加毫秒和时区
        const dataToSend = {
          ...formData,
          timestamp: formData.timestamp + '.000Z'
        }
        await collectionApi.sendSensorData(dataToSend)
        ElMessage.success('数据提交成功')
        handleReset()
      } catch (error) {
        ElMessage.error('提交失败: ' + error.message)
      } finally {
        loading.value = false
      }
    }
  })
}

const handleReset = () => {
  if (formRef.value) {
    formRef.value.resetFields()
    formData.timestamp = new Date().toISOString().slice(0, 19)
  }
}
</script>

<style scoped>
.sensor-form-card {
  max-width: 600px;
  margin: 0 auto;
}

.card-header {
  font-weight: bold;
  font-size: 16px;
}
</style>
