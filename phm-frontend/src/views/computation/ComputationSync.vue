<template>
  <div class="computation-sync">
    <h2>时间同步服务</h2>
    <p class="desc">计算层核心功能：不确定性同步算法</p>
    
    <el-card>
      <template #header>多源数据时间同步</template>
      <el-form :model="form" label-width="120px">
        <el-form-item label="设备ID">
          <el-input v-model="form.deviceId" placeholder="EQ-001" />
        </el-form-item>
        <el-form-item label="同步窗口(ms)">
          <el-slider v-model="form.window" :min="100" :max="5000" :step="100" />
          <span>{{ form.window }} ms</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="performSync" :loading="loading">
            执行时间同步
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="result" class="result-card">
      <template #header>同步结果</template>
      <pre>{{ JSON.stringify(result, null, 2) }}</pre>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { computationApi } from '../../api/request'

const loading = ref(false)
const result = ref(null)

const form = reactive({
  deviceId: 'EQ-001',
  window: 1000
})

const performSync = async () => {
  loading.value = true
  try {
    const res = await computationApi.timeSync({
      deviceId: form.deviceId,
      window: form.window
    })
    result.value = res.data
    ElMessage.success('时间同步完成')
  } catch (error) {
    ElMessage.error('同步失败: ' + error.message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.computation-sync {
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
}
</style>
