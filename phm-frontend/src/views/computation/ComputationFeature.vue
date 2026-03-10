<template>
  <div class="computation-feature">
    <h2>特征工程服务</h2>
    <p class="desc">计算层核心功能：时域/频域特征提取</p>
    
    <el-card>
      <template #header>时域特征提取</template>
      <el-form :model="form" label-width="120px">
        <el-form-item label="数据值">
          <el-input
            v-model="form.values"
            type="textarea"
            :rows="4"
            placeholder="输入数值，用逗号分隔，如: 10.5, 20.3, 15.8, 25.1"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="extractFeatures" :loading="loading">
            提取特征
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="features" class="result-card">
      <template #header>提取的特征</template>
      <el-row :gutter="10">
        <el-col :span="6" v-for="(value, key) in features" :key="key">
          <div class="feature-item">
            <div class="feature-name">{{ key }}</div>
            <div class="feature-value">{{ value?.toFixed ? value.toFixed(4) : value }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { computationApi } from '../../api/request'

const loading = ref(false)
const features = ref(null)

const form = reactive({
  values: '10.5, 20.3, 15.8, 25.1, 18.7, 22.4, 19.6'
})

const extractFeatures = async () => {
  loading.value = true
  try {
    const values = form.values.split(',').map(v => parseFloat(v.trim())).filter(v => !isNaN(v))
    const res = await computationApi.extractFeatures({ values })
    features.value = res.data.features
    ElMessage.success(`成功提取 ${res.data.featureCount} 个特征`)
  } catch (error) {
    ElMessage.error('特征提取失败: ' + error.message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.computation-feature {
  padding: 20px;
}
.desc {
  color: #909399;
  margin-bottom: 20px;
}
.result-card {
  margin-top: 20px;
}
.feature-item {
  background: #f5f7fa;
  padding: 15px;
  margin-bottom: 10px;
  border-radius: 4px;
  text-align: center;
}
.feature-name {
  font-size: 12px;
  color: #909399;
  margin-bottom: 5px;
}
.feature-value {
  font-size: 18px;
  font-weight: bold;
  color: #409eff;
}
</style>
