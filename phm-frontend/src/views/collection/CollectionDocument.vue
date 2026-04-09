<template>
  <div class="collection-document">
    <div class="page-header">
      <h2><el-icon><Document /></el-icon> 多模态文档解析</h2>
      <p class="desc">采集层核心功能：设计文档、维护手册、维修记录、FMECA数据解析</p>
    </div>
    
    <el-row :gutter="20">
      <!-- 图像文档解析 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span><el-icon><Picture /></el-icon> 图像文档解析</span>
              <el-tag type="primary" effect="dark" round size="small">设计图纸</el-tag>
            </div>
          </template>
          <el-upload
            ref="imageUploadRef"
            v-model:file-list="imageFileList"
            drag
            :auto-upload="false"
            :limit="1"
            :on-change="handleImageFileChange"
            accept=".jpg,.png,.pdf"
          >
            <el-icon :size="50"><Upload /></el-icon>
            <div>拖拽文件到此处或点击上传</div>
          </el-upload>
          <el-button 
            type="primary" 
            style="margin-top: 15px;" 
            @click="uploadAndParseImage" 
            :loading="imageLoading"
            :disabled="imageFileList.length === 0"
          >
            {{ imageLoading ? '解析中...' : '开始解析' }}
          </el-button>

          <!-- 图像解析结果展示 -->
          <div v-if="imageResult" class="result-section">
            <el-divider content-position="left">解析结果</el-divider>
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="文件名">{{ imageResult.fileName }}</el-descriptions-item>
              <el-descriptions-item label="部件数量">{{ imageResult.components?.length || 0 }} 个</el-descriptions-item>
            </el-descriptions>
            
            <el-table :data="imageResult.components" style="margin-top: 15px;" border size="small">
              <el-table-column prop="componentName" label="部件名称" width="120" />
              <el-table-column prop="componentType" label="部件类型" width="100" />
              <el-table-column label="置信度" width="100">
                <template #default="{ row }">
                  {{ (row.confidence * 100).toFixed(1) }}%
                </template>
              </el-table-column>
              <el-table-column label="边界框">
                <template #default="{ row }">
                  <span v-if="row.boundingBox">
                    x:{{ row.boundingBox.x }}, y:{{ row.boundingBox.y }}, 
                    w:{{ row.boundingBox.width }}, h:{{ row.boundingBox.height }}
                  </span>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column prop="note" label="备注" />
            </el-table>
          </div>
        </el-card>
      </el-col>
      
      <!-- 文本文档解析 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span><el-icon><EditPen /></el-icon> 文本文档解析</span>
              <el-tag type="success" effect="dark" round size="small">维修记录/FMECA</el-tag>
            </div>
          </template>
          <el-form>
            <el-form-item label="文档类型">
              <el-radio-group v-model="textType">
                <el-radio value="maintenance">维修记录</el-radio>
                <el-radio value="fmeca">FMECA数据</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="文本内容">
              <div style="margin-bottom: 8px;">
                <el-button link type="primary" @click="fillMaintenanceExample">填入维修记录示例</el-button>
                <el-button link type="primary" @click="fillFmecaExample">填入FMECA示例</el-button>
              </div>
              <el-input
                v-model="textContent"
                type="textarea"
                :rows="6"
                placeholder="输入维修记录或FMECA文本..."
              />
            </el-form-item>
            <el-button type="primary" @click="parseText" :loading="textLoading">
              {{ textLoading ? '解析中...' : '解析文本' }}
            </el-button>
          </el-form>

          <!-- 文本解析结果展示 -->
          <div v-if="textResult" class="result-section">
            <el-divider content-position="left">解析结果</el-divider>
            
            <!-- 维修记录结果 -->
            <template v-if="textType === 'maintenance'">
              <div class="summary-section">
                <el-descriptions :column="1" border size="small">
                  <el-descriptions-item label="主要故障">
                    {{ textResult.parseResult?.faultPattern || '-' }}
                  </el-descriptions-item>
                  <el-descriptions-item label="维修措施">
                    {{ textResult.parseResult?.maintenanceAction || '-' }}
                  </el-descriptions-item>
                  <el-descriptions-item label="涉及部件">
                    {{ textResult.parseResult?.component || '-' }}
                  </el-descriptions-item>
                  <el-descriptions-item label="维修时间">
                    {{ textResult.parseResult?.maintenanceTime || '-' }}
                  </el-descriptions-item>
                  <el-descriptions-item label="维修人员">
                    {{ textResult.parseResult?.maintenancePerson || '-' }}
                  </el-descriptions-item>
                  <el-descriptions-item label="置信度">
                    <el-progress 
                      :percentage="(textResult.parseResult?.confidence || 0) * 100" 
                      :format="(p) => p.toFixed(1) + '%'"
                      style="width: 200px;"
                    />
                  </el-descriptions-item>
                </el-descriptions>
              </div>

              <el-tabs v-model="activeTab" style="margin-top: 15px;">
                <el-tab-pane label="实体识别" name="entities">
                  <el-table :data="textResult.parseResult?.entities || []" border size="small" max-height="300">
                    <el-table-column prop="text" label="实体文本" width="150" />
                    <el-table-column label="实体类型" width="150">
                      <template #default="{ row }">
                        <el-tag :type="getEntityTagType(row.type)" size="small">
                          {{ row.type }}
                        </el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column prop="startPos" label="起始位置" width="80" />
                    <el-table-column prop="endPos" label="结束位置" width="80" />
                  </el-table>
                </el-tab-pane>
                
                <el-tab-pane label="关系提取" name="relations">
                  <el-table :data="textResult.parseResult?.relations || []" border size="small" max-height="300">
                    <el-table-column prop="subject" label="主体" width="120" />
                    <el-table-column prop="predicate" label="谓词" width="100" />
                    <el-table-column prop="object" label="客体" width="120" />
                    <el-table-column prop="relationType" label="关系类型">
                      <template #default="{ row }">
                        <el-tag size="small">{{ row.relationType }}</el-tag>
                      </template>
                    </el-table-column>
                  </el-table>
                </el-tab-pane>
              </el-tabs>
            </template>

            <!-- FMECA结果 -->
            <template v-else-if="textType === 'fmeca'">
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="部件名称">
                  {{ textResult.parseResult?.component || '-' }}
                </el-descriptions-item>
                <el-descriptions-item label="失效模式">
                  <el-tag type="danger">{{ textResult.parseResult?.failureMode || '-' }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="故障原因" :span="2">
                  {{ textResult.parseResult?.failureCause || '-' }}
                </el-descriptions-item>
                <el-descriptions-item label="故障影响" :span="2">
                  {{ textResult.parseResult?.failureEffect || '-' }}
                </el-descriptions-item>
              </el-descriptions>

              <div style="margin-top: 15px;">
                <el-row :gutter="15">
                  <el-col :span="6">
                    <div class="stat-card stat-card-severity">
                      <div class="stat-title">严酷度 (S)</div>
                      <div class="stat-value">
                        {{ textResult.parseResult?.severity || '-' }}
                        <el-tag size="small" style="margin-left: 5px;">
                          {{ textResult.parseResult?.severityLevel || '' }}
                        </el-tag>
                      </div>
                    </div>
                  </el-col>
                  <el-col :span="6">
                    <div class="stat-card stat-card-occurrence">
                      <div class="stat-title">发生度 (O)</div>
                      <div class="stat-value">{{ textResult.parseResult?.occurrence || '-' }}</div>
                    </div>
                  </el-col>
                  <el-col :span="6">
                    <div class="stat-card stat-card-detection">
                      <div class="stat-title">探测度 (D)</div>
                      <div class="stat-value">{{ textResult.parseResult?.detection || '-' }}</div>
                    </div>
                  </el-col>
                  <el-col :span="6">
                    <div class="stat-card stat-card-rpn">
                      <div class="stat-title">RPN值</div>
                      <div class="stat-value" :style="{ color: getRpnColor(textResult.parseResult?.rpn) }">
                        {{ textResult.parseResult?.rpn || '-' }}
                      </div>
                      <el-progress 
                        :percentage="Math.min((textResult.parseResult?.rpn || 0) / 3, 100)" 
                        :color="getRpnColor(textResult.parseResult?.rpn)"
                        :show-text="false"
                        :stroke-width="4"
                        style="margin-top: 8px;"
                      />
                    </div>
                  </el-col>
                </el-row>
              </div>

              <el-descriptions :column="1" border size="small" style="margin-top: 15px;">
                <el-descriptions-item label="风险等级">
                  <el-tag :type="getRiskTagType(textResult.parseResult?.rpn)">
                    {{ textResult.parseResult?.riskLevel || '-' }}
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="建议措施">
                  {{ textResult.parseResult?.recommendedAction || '-' }}
                </el-descriptions-item>
              </el-descriptions>
            </template>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload, Document, Picture, EditPen } from '@element-plus/icons-vue'
import { collectionApi } from '../../api/request'

const textType = ref('maintenance')
const textContent = ref('')
const textLoading = ref(false)
const textResult = ref(null)
const activeTab = ref('entities')

const imageFileList = ref([])
const imageLoading = ref(false)
const imageResult = ref(null)

// 图像文件变化处理
const handleImageFileChange = (file, fileList) => {
  imageFileList.value = fileList
  imageResult.value = null
}

// 上传并解析图像
const uploadAndParseImage = async () => {
  if (imageFileList.value.length === 0) {
    ElMessage.warning('请先选择要解析的图像文件')
    return
  }

  imageLoading.value = true
  imageResult.value = null

  try {
    const formData = new FormData()
    formData.append('file', imageFileList.value[0].raw)

    const res = await collectionApi.parseImage(formData)
    imageResult.value = res.data
    ElMessage.success('图像解析成功')
  } catch (error) {
    ElMessage.error('图像解析失败: ' + error.message)
  } finally {
    imageLoading.value = false
  }
}

// 解析文本
const parseText = async () => {
  if (!textContent.value.trim()) {
    ElMessage.warning('请输入要解析的文本内容')
    return
  }

  textLoading.value = true
  textResult.value = null

  try {
    const res = await collectionApi.parseText({
      text: textContent.value,
      type: textType.value
    })
    textResult.value = res.data
    ElMessage.success('文本解析成功')
  } catch (error) {
    ElMessage.error('解析失败: ' + error.message)
  } finally {
    textLoading.value = false
  }
}

// 获取实体类型对应的 Tag 颜色
const getEntityTagType = (type) => {
  const typeMap = {
    'COMPONENT': 'primary',
    'FAULT_MODE': 'danger',
    'MAINTENANCE_ACTION': 'success',
    'TIME': 'info',
    'PERSON': 'warning',
    'VALUE_UNIT': 'info',
    'ACTION_PHRASE': 'success'
  }
  return typeMap[type] || ''
}

// RPN值颜色
const getRpnColor = (rpn) => {
  if (!rpn) return '#909399'
  if (rpn >= 200) return '#F56C6C'
  if (rpn >= 120) return '#E6A23C'
  if (rpn >= 80) return '#409EFF'
  return '#67C23A'
}

// 风险等级Tag类型
const getRiskTagType = (rpn) => {
  if (!rpn) return 'info'
  if (rpn >= 200) return 'danger'
  if (rpn >= 120) return 'warning'
  if (rpn >= 80) return ''
  return 'success'
}

// 填入维修记录示例
const fillMaintenanceExample = () => {
  textType.value = 'maintenance'
  textContent.value = '2024年3月5日上午10时30分，技术员张伟在巡检中发现设备EQ-001的主轴承温度异常升高至85°C，振动值达到15.3mm/s。经分析，润滑不良导致轴承磨损严重，转速下降至1200rpm。维修人员李明对主轴承进行了拆卸检查，更换了深沟球轴承并重新加注润滑油，耗时3小时。更换轴承后设备恢复正常运行，温度降至42°C。'
}

// 填入FMECA示例
const fillFmecaExample = () => {
  textType.value = 'fmeca'
  textContent.value = '部件：主轴承\n故障模式：磨损失效\n故障原因：润滑不良、负载过大、运行时间超过设计寿命\n故障影响：可能导致设备停机，振动增大，噪音升高\n严酷度：II\n发生度：6\n探测度：4'
}
</script>

<style scoped>
.collection-document {
  padding: 0;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 600;
  color: #1a1a2e;
}

.desc {
  color: #909399;
  margin: 0;
  font-size: 14px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header span {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.result-section {
  margin-top: 20px;
}

.summary-section {
  margin-bottom: 10px;
}

.stat-card {
  background: #f9fafb;
  border-radius: 8px;
  padding: 16px;
  text-align: center;
  border: 1px solid #e8ecf1;
}

.stat-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
}

.stat-card-severity { border-left: 3px solid #f56c6c; }
.stat-card-occurrence { border-left: 3px solid #e6a23c; }
.stat-card-detection { border-left: 3px solid #409eff; }
.stat-card-rpn { border-left: 3px solid #9b59b6; }
</style>
