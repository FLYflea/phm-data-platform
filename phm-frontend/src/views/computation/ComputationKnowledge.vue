<template>
  <div class="computation-knowledge">
    <h2>知识图谱服务</h2>
    <p class="desc">计算层核心功能：从多模态数据构建设备知识图谱</p>

    <el-row :gutter="20">
      <!-- 从图像构建 -->
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>从设计图纸构建</span>
              <el-tag type="primary">图像解析</el-tag>
            </div>
          </template>

          <el-form :model="imageForm" label-width="100px">
            <el-form-item label="图像ID">
              <el-input v-model="imageForm.imageId" placeholder="如: IMG_001" />
            </el-form-item>
            <el-form-item label="设备ID">
              <el-input v-model="imageForm.equipmentId" placeholder="如: EQ-001（可选）" />
            </el-form-item>
            <el-form-item label="设计图纸">
              <el-upload
                drag
                :auto-upload="false"
                :on-change="handleImageChange"
                accept=".jpg,.jpeg,.png,.pdf"
                :limit="1"
              >
                <el-icon :size="50"><Upload /></el-icon>
                <div class="el-upload__text">
                  拖拽文件到此处或 <em>点击上传</em>
                </div>
              </el-upload>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="buildFromImage" :loading="imageLoading">
                构建知识图谱
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 从文本构建 -->
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>从维修记录构建</span>
              <el-tag type="success">文本解析</el-tag>
            </div>
          </template>

          <el-form :model="textForm" label-width="100px">
            <el-form-item label="文档ID">
              <el-input v-model="textForm.docId" placeholder="如: DOC_001" />
            </el-form-item>
            <el-form-item label="设备ID">
              <el-input v-model="textForm.equipmentId" placeholder="如: EQ-001（可选）" />
            </el-form-item>
            <el-form-item label="文本内容">
              <el-input
                v-model="textForm.text"
                type="textarea"
                :rows="6"
                placeholder="输入维修记录、FMECA分析或设备描述文本..."
              />
            </el-form-item>
            <el-form-item>
              <el-button type="success" @click="buildFromText" :loading="textLoading">
                构建知识图谱
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <!-- 构建结果 -->
    <el-card v-if="result" class="result-card">
      <template #header>
        <span>构建结果</span>
      </template>

      <el-result
        :icon="result.success ? 'success' : 'error'"
        :title="result.success ? '构建成功' : '构建失败'"
        :sub-title="result.message"
      >
        <template #extra>
          <el-button type="primary" @click="viewInStorage">在存储层查看</el-button>
        </template>
      </el-result>

      <el-descriptions v-if="result.data" :column="2" border>
        <el-descriptions-item label="设备ID">{{ result.data.equipmentId }}</el-descriptions-item>
        <el-descriptions-item label="组件/实体数">{{ result.data.componentCount || result.data.entityCount }}</el-descriptions-item>
        <el-descriptions-item v-if="result.data.relationCount" label="关系数">{{ result.data.relationCount }}</el-descriptions-item>
        <el-descriptions-item label="构建时间">{{ formatTime(result.data.buildTime) }}</el-descriptions-item>
      </el-descriptions>

      <!-- 组件列表 -->
      <div v-if="result.data?.components?.length" class="components-section">
        <h4>提取的组件</h4>
        <el-tag
          v-for="comp in result.data.components"
          :key="comp"
          class="component-tag"
          type="info"
        >
          {{ comp }}
        </el-tag>
      </div>
    </el-card>

    <!-- 使用说明 -->
    <el-card class="help-card">
      <template #header>使用说明</template>
      <el-collapse>
        <el-collapse-item title="图像构建流程">
          <ol>
            <li>上传设备设计图纸（支持JPG、PNG、PDF格式）</li>
            <li>系统自动调用YOLOv8模型识别图纸中的部件</li>
            <li>提取的部件信息保存到Neo4j图数据库</li>
            <li>构建设备-组件层级关系图谱</li>
          </ol>
        </el-collapse-item>
        <el-collapse-item title="文本构建流程">
          <ol>
            <li>输入维修记录、FMECA分析或设备描述文本</li>
            <li>系统使用BERT-BiLSTM-CRF模型提取实体和关系</li>
            <li>识别故障模式、维修操作、时间等关键信息</li>
            <li>构建故障知识图谱</li>
          </ol>
        </el-collapse-item>
      </el-collapse>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { computationApi } from '../../api/request'

const imageLoading = ref(false)
const textLoading = ref(false)
const result = ref(null)
const selectedImageFile = ref(null)

const imageForm = reactive({
  imageId: 'IMG_' + Date.now(),
  equipmentId: ''
})

const textForm = reactive({
  docId: 'DOC_' + Date.now(),
  equipmentId: '',
  text: '设备运行过程中出现异常振动，经检查发现轴承磨损严重。更换轴承后，振动值恢复正常。建议下次检修时重点关注轴承状态。'
})

// 格式化时间
const formatTime = (timestamp) => {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleString()
}

// 处理图像文件选择
const handleImageChange = (file) => {
  selectedImageFile.value = file.raw
  ElMessage.info(`已选择文件: ${file.name}`)
}

// 从图像构建
const buildFromImage = async () => {
  if (!selectedImageFile.value) {
    ElMessage.error('请先上传图像文件')
    return
  }

  imageLoading.value = true
  result.value = null

  try {
    const formData = new FormData()
    formData.append('file', selectedImageFile.value)
    formData.append('imageId', imageForm.imageId)
    if (imageForm.equipmentId) {
      formData.append('equipmentId', imageForm.equipmentId)
    }

    const res = await computationApi.buildKnowledgeGraphFromImage(formData)
    result.value = {
      success: true,
      message: res.note || '知识图谱构建成功',
      data: res.data
    }
    ElMessage.success('知识图谱构建成功')
  } catch (error) {
    result.value = {
      success: false,
      message: error.message
    }
    ElMessage.error('构建失败: ' + error.message)
  } finally {
    imageLoading.value = false
  }
}

// 从文本构建
const buildFromText = async () => {
  if (!textForm.text.trim()) {
    ElMessage.error('请输入文本内容')
    return
  }

  textLoading.value = true
  result.value = null

  try {
    const res = await computationApi.buildKnowledgeGraphFromText({
      docId: textForm.docId,
      text: textForm.text,
      equipmentId: textForm.equipmentId || undefined
    })
    result.value = {
      success: true,
      message: res.note || '知识图谱构建成功',
      data: res.data
    }
    ElMessage.success('知识图谱构建成功')
  } catch (error) {
    result.value = {
      success: false,
      message: error.message
    }
    ElMessage.error('构建失败: ' + error.message)
  } finally {
    textLoading.value = false
  }
}

// 跳转到存储层查看
const viewInStorage = () => {
  // 触发父组件切换菜单到存储层图谱查询
  ElMessage.info('请在左侧菜单选择"存储层-知识图谱查询"查看详情')
}
</script>

<style scoped>
.computation-knowledge {
  padding: 20px;
}

.desc {
  color: #909399;
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.result-card {
  margin-top: 20px;
}

.components-section {
  margin-top: 20px;
}

.components-section h4 {
  margin: 0 0 15px 0;
  color: #303133;
}

.component-tag {
  margin: 5px;
}

.help-card {
  margin-top: 20px;
}

.help-card ol {
  padding-left: 20px;
  color: #606266;
  line-height: 2;
}
</style>
