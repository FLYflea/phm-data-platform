<template>
  <div class="storage-graph">
    <h2>知识图谱查询</h2>
    <p class="desc">存储层核心功能：图数据存储与查询（Neo4j）</p>

    <!-- 查询条件 -->
    <el-card>
      <template #header>查询条件</template>
      <el-form :model="queryForm" inline>
        <el-form-item label="设备ID">
          <el-input v-model="queryForm.equipmentId" placeholder="EQ-001" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="queryEquipment" :loading="loading">查询设备</el-button>
          <el-button @click="queryAllEquipments">查询所有设备</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 设备信息 -->
    <el-card v-if="equipment" class="result-card">
      <template #header>
        <div class="card-header">
          <span>设备信息</span>
          <el-tag type="success">{{ equipment.equipmentId }}</el-tag>
        </div>
      </template>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="设备名称">{{ equipment.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="设备类型">{{ equipment.type || '-' }}</el-descriptions-item>
        <el-descriptions-item label="设备型号">{{ equipment.model || '-' }}</el-descriptions-item>
        <el-descriptions-item label="组件数量">{{ components.length }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 组件列表 -->
    <el-card v-if="components.length > 0" class="result-card">
      <template #header>
        <div class="card-header">
          <span>组件列表</span>
          <el-tag type="primary">{{ components.length }} 个组件</el-tag>
        </div>
      </template>

      <el-table :data="components" stripe>
        <el-table-column prop="componentId" label="组件ID" width="150" />
        <el-table-column prop="name" label="组件名称" width="150" />
        <el-table-column prop="type" label="类型" width="120" />
        <el-table-column prop="function" label="功能描述" />
        <el-table-column label="操作" width="120">
          <template #default="scope">
            <el-button link type="primary" @click="queryComponentRelations(scope.row)">
              查看关系
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 关系查询 -->
    <el-card v-if="showRelationQuery" class="result-card">
      <template #header>组件关系查询</template>
      <el-form :model="relationForm" inline>
        <el-form-item label="起始组件">
          <el-select v-model="relationForm.startId" placeholder="选择组件">
            <el-option
              v-for="comp in components"
              :key="comp.componentId"
              :label="comp.name || comp.componentId"
              :value="comp.componentId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="目标组件">
          <el-select v-model="relationForm.endId" placeholder="选择组件">
            <el-option
              v-for="comp in components"
              :key="comp.componentId"
              :label="comp.name || comp.componentId"
              :value="comp.componentId"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="queryPath" :loading="pathLoading">查询路径</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 路径结果 -->
    <el-card v-if="paths.length > 0" class="result-card">
      <template #header>
        <div class="card-header">
          <span>关联路径</span>
          <el-tag type="warning">{{ paths.length }} 条路径</el-tag>
        </div>
      </template>

      <el-timeline>
        <el-timeline-item
          v-for="(path, index) in paths"
          :key="index"
          :type="index === 0 ? 'primary' : 'info'"
        >
          <h4>路径 {{ index + 1 }}</h4>
          <p>长度: {{ path.length }} 跳</p>
          <el-steps :active="path.nodes?.length || 0" simple>
            <el-step
              v-for="(node, idx) in path.nodes"
              :key="idx"
              :title="node.name || node.componentId || node"
            />
          </el-steps>
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <!-- 图可视化占位 -->
    <el-card class="result-card">
      <template #header>图谱可视化</template>
      <div class="graph-placeholder">
        <el-empty description="图谱可视化区域（待集成ECharts/Graphin）">
          <template #image>
            <el-icon :size="60" color="#409eff"><Share /></el-icon>
          </template>
        </el-empty>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Share } from '@element-plus/icons-vue'
import { storageApi } from '../../api/request'

const loading = ref(false)
const pathLoading = ref(false)
const equipment = ref(null)
const components = ref([])
const paths = ref([])
const showRelationQuery = ref(false)

const queryForm = reactive({
  equipmentId: 'EQ-001'
})

const relationForm = reactive({
  startId: '',
  endId: '',
  maxDepth: 3
})

// 查询设备及其组件
const queryEquipment = async () => {
  if (!queryForm.equipmentId) {
    ElMessage.error('请输入设备ID')
    return
  }

  loading.value = true
  equipment.value = null
  components.value = []
  paths.value = []

  try {
    // 调用存储层API查询设备
    const res = await fetch(`/api/storage/graph/equipment/${queryForm.equipmentId}`, {
      method: 'GET',
      headers: { 'Content-Type': 'application/json' }
    }).then(r => r.json())

    if (res.status === 'success') {
      equipment.value = res.equipment
      components.value = res.components || []
      showRelationQuery.value = components.value.length > 0
      ElMessage.success(`查询成功，找到 ${components.value.length} 个组件`)
    } else {
      ElMessage.warning(res.message || '设备不存在')
    }
  } catch (error) {
    ElMessage.error('查询失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

// 查询所有设备（预留）
const queryAllEquipments = () => {
  ElMessage.info('查询所有设备功能待实现')
}

// 查看组件关系
const queryComponentRelations = (component) => {
  relationForm.startId = component.componentId
  showRelationQuery.value = true
  ElMessage.info(`已选择组件: ${component.name || component.componentId}，请选择目标组件查询路径`)
}

// 查询路径
const queryPath = async () => {
  if (!relationForm.startId || !relationForm.endId) {
    ElMessage.error('请选择起始组件和目标组件')
    return
  }

  if (relationForm.startId === relationForm.endId) {
    ElMessage.error('起始组件和目标组件不能相同')
    return
  }

  pathLoading.value = true
  paths.value = []

  try {
    const params = new URLSearchParams({
      startId: relationForm.startId,
      endId: relationForm.endId,
      maxDepth: relationForm.maxDepth
    })

    const res = await fetch(`/api/storage/graph/path?${params}`, {
      method: 'GET',
      headers: { 'Content-Type': 'application/json' }
    }).then(r => r.json())

    if (res.status === 'success') {
      paths.value = res.paths || []
      if (paths.value.length === 0) {
        ElMessage.info('未找到关联路径')
      } else {
        ElMessage.success(`找到 ${paths.value.length} 条路径`)
      }
    }
  } catch (error) {
    ElMessage.error('路径查询失败: ' + error.message)
  } finally {
    pathLoading.value = false
  }
}
</script>

<style scoped>
.storage-graph {
  padding: 20px;
}

.desc {
  color: #909399;
  margin-bottom: 20px;
}

.result-card {
  margin-top: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.graph-placeholder {
  height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  border-radius: 4px;
}
</style>
