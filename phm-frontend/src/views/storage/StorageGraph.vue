<template>
  <div class="storage-graph">
    <div class="page-header">
      <h2><el-icon><Share /></el-icon> 知识图谱查询</h2>
      <p class="desc">存储层核心功能：图数据存储与查询（Neo4j）</p>
    </div>

    <!-- 图谱统计卡片 -->
    <el-row :gutter="15" class="stats-row">
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card stat-card-blue">
          <div class="stat-title"><el-icon><Monitor /></el-icon> 设备总数</div>
          <div class="stat-value">{{ graphStats.equipmentCount || 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card stat-card-green">
          <div class="stat-title"><el-icon><Cpu /></el-icon> 组件总数</div>
          <div class="stat-value">{{ graphStats.componentCount || 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card stat-card-orange">
          <div class="stat-title"><el-icon><Connection /></el-icon> 关系总数</div>
          <div class="stat-value">{{ graphStats.relationCount || 0 }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 查询条件 -->
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span><el-icon><Search /></el-icon> 查询条件</span>
          <el-tag type="primary" effect="dark" round size="small">Neo4j</el-tag>
        </div>
      </template>
      <el-form :model="queryForm" inline>
        <el-form-item label="设备ID">
          <el-input v-model="queryForm.equipmentId" placeholder="EQ-001" style="width: 150px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="queryEquipment" :loading="loading">查询设备</el-button>
          <el-button type="success" @click="queryAllEquipments" :loading="loadingAll">查询所有设备</el-button>
          <el-button @click="refreshStats">刷新统计</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 设备列表（查询所有设备时显示） -->
    <el-card v-if="allEquipments.length > 0 && !equipment" shadow="hover" class="result-card">
      <template #header>
        <div class="card-header">
          <span>设备列表</span>
          <el-tag type="primary">{{ allEquipments.length }} 个设备</el-tag>
        </div>
      </template>
      <el-table :data="allEquipments" stripe size="small">
        <el-table-column prop="equipmentId" label="设备ID" width="120" />
        <el-table-column prop="name" label="名称" width="150" />
        <el-table-column prop="type" label="类型" width="120" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="componentCount" label="组件数" width="80" />
        <el-table-column label="操作" width="100">
          <template #default="scope">
            <el-button link type="primary" @click="selectEquipment(scope.row.equipmentId)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 设备信息 -->
    <el-card v-if="equipment" shadow="hover" class="result-card">
      <template #header>
        <div class="card-header">
          <span><el-icon><Monitor /></el-icon> 设备信息</span>
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

    <!-- 图谱可视化 (ECharts 力导向图) -->
    <el-card v-if="equipment" shadow="hover" class="result-card">
      <template #header>
        <div class="card-header">
          <span><el-icon><Share /></el-icon> 知识图谱可视化</span>
          <el-tag type="info">力导向关系图</el-tag>
        </div>
      </template>
      <div ref="graphChartRef" class="graph-chart"></div>
    </el-card>

    <!-- 组件列表 -->
    <el-card v-if="components.length > 0" shadow="hover" class="result-card">
      <template #header>
        <div class="card-header">
          <span><el-icon><Cpu /></el-icon> 组件列表</span>
          <el-tag type="primary">{{ components.length }} 个组件</el-tag>
        </div>
      </template>
      <el-table :data="components" stripe size="small">
        <el-table-column prop="componentId" label="组件ID" width="150" />
        <el-table-column prop="name" label="组件名称" width="150" />
        <el-table-column prop="type" label="类型" width="120" />
        <el-table-column prop="function" label="功能描述" />
        <el-table-column label="操作" width="120">
          <template #default="scope">
            <el-button link type="primary" @click="queryComponentRelations(scope.row)">查看关系</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 关系查询 -->
    <el-card v-if="showRelationQuery" shadow="hover" class="result-card">
      <template #header>
        <div class="card-header">
          <span><el-icon><Connection /></el-icon> 组件关系查询</span>
          <el-tag type="warning" effect="dark" round size="small">路径分析</el-tag>
        </div>
      </template>
      <el-form :model="relationForm" inline>
        <el-form-item label="起始组件">
          <el-select v-model="relationForm.startId" placeholder="选择组件" style="width: 180px">
            <el-option
              v-for="comp in components"
              :key="comp.componentId"
              :label="comp.name || comp.componentId"
              :value="comp.componentId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="目标组件">
          <el-select v-model="relationForm.endId" placeholder="选择组件" style="width: 180px">
            <el-option
              v-for="comp in components"
              :key="comp.componentId"
              :label="comp.name || comp.componentId"
              :value="comp.componentId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="最大深度">
          <el-input-number v-model="relationForm.maxDepth" :min="1" :max="5" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="queryPath" :loading="pathLoading">查询路径</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 路径结果 -->
    <el-card v-if="paths.length > 0" shadow="hover" class="result-card">
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
  </div>
</template>

<script setup>
import { ref, reactive, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Share, Monitor, Cpu, Connection, Search } from '@element-plus/icons-vue'
import { storageApi } from '../../api/request'
import * as echarts from 'echarts'

const loading = ref(false)
const loadingAll = ref(false)
const pathLoading = ref(false)
const equipment = ref(null)
const components = ref([])
const paths = ref([])
const showRelationQuery = ref(false)
const allEquipments = ref([])
const graphStats = ref({})

const graphChartRef = ref(null)
let graphChart = null

const queryForm = reactive({
  equipmentId: 'EQ-001'
})

const relationForm = reactive({
  startId: '',
  endId: '',
  maxDepth: 3
})

// 页面加载时刷新统计
onMounted(() => {
  refreshStats()
})

onBeforeUnmount(() => {
  if (graphChart) {
    graphChart.dispose()
    graphChart = null
  }
})

// 刷新图谱统计
const refreshStats = async () => {
  try {
    const res = await storageApi.getGraphStats()
    graphStats.value = res
  } catch (error) {
    console.warn('获取图谱统计失败:', error.message)
  }
}

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
  allEquipments.value = []

  try {
    const res = await storageApi.queryEquipmentGraph(queryForm.equipmentId)
    if (res.status === 'success') {
      equipment.value = res.equipment
      components.value = res.components || []
      showRelationQuery.value = components.value.length > 0
      ElMessage.success(`查询成功，找到 ${components.value.length} 个组件`)

      // 渲染图谱
      await nextTick()
      renderGraph()
    } else {
      ElMessage.warning(res.message || '设备不存在')
    }
  } catch (error) {
    ElMessage.error('查询失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

// 查询所有设备
const queryAllEquipments = async () => {
  loadingAll.value = true
  equipment.value = null
  components.value = []
  paths.value = []

  try {
    const res = await storageApi.queryAllEquipments()
    allEquipments.value = res.equipments || []
    if (allEquipments.value.length === 0) {
      ElMessage.warning('暂无设备数据，请先在计算层构建知识图谱')
    } else {
      ElMessage.success(`查询到 ${allEquipments.value.length} 个设备`)
    }
  } catch (error) {
    ElMessage.error('查询失败: ' + error.message)
  } finally {
    loadingAll.value = false
  }
}

// 从设备列表选择一个设备查看详情
const selectEquipment = (equipmentId) => {
  queryForm.equipmentId = equipmentId
  queryEquipment()
}

// 渲染ECharts力导向图
const renderGraph = () => {
  if (!graphChartRef.value) return

  if (graphChart) {
    graphChart.dispose()
  }
  graphChart = echarts.init(graphChartRef.value)

  // 构建节点
  const nodes = []
  const links = []

  // 设备节点（大圆、蓝色）
  if (equipment.value) {
    nodes.push({
      id: equipment.value.equipmentId,
      name: equipment.value.name || equipment.value.equipmentId,
      symbolSize: 60,
      category: 0,
      itemStyle: { color: '#409EFF' },
      label: { show: true, fontSize: 14, fontWeight: 'bold' }
    })
  }

  // 组件节点（中圆、绿色）
  components.value.forEach(comp => {
    nodes.push({
      id: comp.componentId,
      name: comp.name || comp.componentId,
      symbolSize: 40,
      category: 1,
      itemStyle: { color: '#67C23A' },
      label: { show: true, fontSize: 12 }
    })
    // HAS_COMPONENT 关系边
    links.push({
      source: equipment.value.equipmentId,
      target: comp.componentId,
      label: { show: true, formatter: 'HAS_COMPONENT', fontSize: 10, color: '#909399' },
      lineStyle: { color: '#C0C4CC', width: 2, curveness: 0.1 }
    })
  })

  // 组件间 RELATED_TO 关系
  components.value.forEach(comp => {
    if (comp.relatedComponents && comp.relatedComponents.length > 0) {
      comp.relatedComponents.forEach(related => {
        const targetId = related.componentId || related
        if (nodes.find(n => n.id === targetId)) {
          links.push({
            source: comp.componentId,
            target: targetId,
            label: { show: true, formatter: 'RELATED_TO', fontSize: 10, color: '#E6A23C' },
            lineStyle: { color: '#E6A23C', width: 1.5, type: 'dashed', curveness: 0.2 }
          })
        }
      })
    }
  })

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        if (params.dataType === 'node') {
          const node = params.data
          return `<b>${node.name}</b><br/>ID: ${node.id}<br/>类型: ${node.category === 0 ? '设备' : '组件'}`
        }
        if (params.dataType === 'edge') {
          return `${params.data.source} → ${params.data.target}`
        }
        return ''
      }
    },
    legend: {
      data: ['设备', '组件'],
      top: 10
    },
    animationDuration: 1500,
    animationEasingUpdate: 'quinticInOut',
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      categories: [
        { name: '设备', itemStyle: { color: '#409EFF' } },
        { name: '组件', itemStyle: { color: '#67C23A' } }
      ],
      data: nodes,
      links: links,
      force: {
        repulsion: 300,
        edgeLength: [100, 200],
        gravity: 0.1
      },
      emphasis: {
        focus: 'adjacency',
        lineStyle: { width: 4 }
      },
      label: {
        show: true,
        position: 'bottom'
      },
      edgeLabel: {
        show: true,
        fontSize: 10
      },
      lineStyle: {
        opacity: 0.9,
        width: 2
      }
    }]
  }

  graphChart.setOption(option)
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
    const res = await storageApi.queryGraphPath({
      startId: relationForm.startId,
      endId: relationForm.endId,
      maxDepth: relationForm.maxDepth
    })
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
.stats-row {
  margin-bottom: 20px;
}
.stat-card {
  text-align: center;
  border-left: 3px solid #409eff;
}
.stat-card-blue { border-left-color: #409eff; }
.stat-card-green { border-left-color: #67c23a; }
.stat-card-orange { border-left-color: #e6a23c; }
.stat-title {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
}
.result-card {
  margin-top: 20px;
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
.graph-chart {
  height: 450px;
  width: 100%;
  background: #fafafa;
  border-radius: 4px;
  border: 1px solid #ebeef5;
}
</style>
