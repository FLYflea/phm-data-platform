<template>
  <div class="collection-document">
    <h2>多模态文档解析</h2>
    <p class="desc">采集层核心功能：设计文档、维护手册、维修记录、FMECA数据解析</p>
    
    <el-row :gutter="20">
      <el-col :span="12">
        <el-card>
          <template #header>图像文档解析（设计图纸）</template>
          <el-upload
            drag
            action="/api/collection/document/image"
            :on-success="handleImageSuccess"
            :on-error="handleImageError"
            accept=".jpg,.png,.pdf"
          >
            <el-icon :size="50"><Upload /></el-icon>
            <div>拖拽文件到此处或点击上传</div>
          </el-upload>
        </el-card>
      </el-col>
      
      <el-col :span="12">
        <el-card>
          <template #header>文本文档解析（维修记录/FMECA）</template>
          <el-form>
            <el-form-item label="文档类型">
              <el-radio-group v-model="textType">
                <el-radio label="maintenance">维修记录</el-radio>
                <el-radio label="fmeca">FMECA数据</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="文本内容">
              <el-input
                v-model="textContent"
                type="textarea"
                :rows="6"
                placeholder="输入维修记录或FMECA文本..."
              />
            </el-form-item>
            <el-button type="primary" @click="parseText">解析文本</el-button>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { collectionApi } from '../../api/request'

const textType = ref('maintenance')
const textContent = ref('')

const handleImageSuccess = (res) => {
  ElMessage.success('图像解析成功')
  console.log('解析结果:', res)
}

const handleImageError = () => {
  ElMessage.error('图像解析失败')
}

const parseText = async () => {
  try {
    const res = await collectionApi.parseText({
      text: textContent.value,
      type: textType.value
    })
    ElMessage.success('文本解析成功')
    console.log('解析结果:', res)
  } catch (error) {
    ElMessage.error('解析失败: ' + error.message)
  }
}
</script>

<style scoped>
.collection-document {
  padding: 20px;
}
.desc {
  color: #909399;
  margin-bottom: 20px;
}
</style>
