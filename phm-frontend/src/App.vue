<template>
  <div class="phm-platform">
    <!-- 顶部导航 -->
    <el-header class="platform-header">
      <div class="header-content">
        <h1>PHM Platform</h1>
        <span class="subtitle">设备全寿命周期数据管理平台</span>
      </div>
    </el-header>

    <!-- 主体布局 -->
    <el-container class="main-container">
      <!-- 左侧菜单 -->
      <el-aside width="220px" class="sidebar">
        <el-menu
          :default-active="activeMenu"
          class="layer-menu"
          @select="handleMenuSelect"
        >
          <!-- 采集层 -->
          <el-sub-menu index="collection">
            <template #title>
              <el-icon><Download /></el-icon>
              <span>采集层</span>
            </template>
            <el-menu-item index="collection-sensor">
              <span>传感器数据采集</span>
            </el-menu-item>
            <el-menu-item index="collection-document">
              <span>文档解析</span>
            </el-menu-item>
          </el-sub-menu>

          <!-- 计算层 -->
          <el-sub-menu index="computation">
            <template #title>
              <el-icon><Cpu /></el-icon>
              <span>计算层</span>
            </template>
            <el-menu-item index="computation-sync">
              <span>时间同步</span>
            </el-menu-item>
            <el-menu-item index="computation-fusion">
              <span>数据融合</span>
            </el-menu-item>
            <el-menu-item index="computation-feature">
              <span>特征工程</span>
            </el-menu-item>
            <el-menu-item index="computation-knowledge">
              <span>知识图谱</span>
            </el-menu-item>
          </el-sub-menu>

          <!-- 存储层 -->
          <el-sub-menu index="storage">
            <template #title>
              <el-icon><Coin /></el-icon>
              <span>存储层</span>
            </template>
            <el-menu-item index="storage-timeseries">
              <span>时序数据查询</span>
            </el-menu-item>
            <el-menu-item index="storage-graph">
              <span>知识图谱查询</span>
            </el-menu-item>
          </el-sub-menu>

          <!-- 服务层 -->
          <el-sub-menu index="service">
            <template #title>
              <el-icon><Service /></el-icon>
              <span>服务层</span>
            </template>
            <el-menu-item index="service-query">
              <span>统一查询</span>
            </el-menu-item>
            <el-menu-item index="service-visualization">
              <span>数据可视化</span>
            </el-menu-item>
            <el-menu-item index="service-analysis">
              <span>数据分析</span>
            </el-menu-item>
            <el-menu-item index="service-distribution">
              <span>数据分发</span>
            </el-menu-item>
            <el-menu-item index="service-stream">
              <span>实时数据流</span>
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </el-aside>

      <!-- 内容区域 -->
      <el-main class="content-area">
        <!-- 采集层 - 传感器数据采集 -->
        <CollectionSensor v-if="activeMenu === 'collection-sensor'" />
        
        <!-- 采集层 - 文档解析 -->
        <CollectionDocument v-else-if="activeMenu === 'collection-document'" />
        
        <!-- 计算层 - 时间同步 -->
        <ComputationSync v-else-if="activeMenu === 'computation-sync'" />
        
        <!-- 计算层 - 数据融合 -->
        <ComputationFusion v-else-if="activeMenu === 'computation-fusion'" />
        
        <!-- 计算层 - 特征工程 -->
        <ComputationFeature v-else-if="activeMenu === 'computation-feature'" />
        
        <!-- 计算层 - 知识图谱 -->
        <ComputationKnowledge v-else-if="activeMenu === 'computation-knowledge'" />
        
        <!-- 存储层 - 时序数据查询 -->
        <StorageTimeseries v-else-if="activeMenu === 'storage-timeseries'" />
        
        <!-- 存储层 - 知识图谱查询 -->
        <StorageGraph v-else-if="activeMenu === 'storage-graph'" />
        
        <!-- 服务层 - 统一查询 -->
        <ServiceQuery v-else-if="activeMenu === 'service-query'" />
        
        <!-- 服务层 - 数据可视化 -->
        <ServiceVisualization v-else-if="activeMenu === 'service-visualization'" />
        
        <!-- 服务层 - 数据分析 -->
        <ServiceAnalysis v-else-if="activeMenu === 'service-analysis'" />
        
        <!-- 服务层 - 数据分发 -->
        <ServiceDistribution v-else-if="activeMenu === 'service-distribution'" />
        
        <!-- 服务层 - 实时数据流 -->
        <ServiceStream v-else-if="activeMenu === 'service-stream'" />
        
        <!-- 默认页面 -->
        <div v-else class="welcome-page">
          <el-empty description="请选择左侧菜单开始操作">
            <template #image>
              <el-icon :size="80" color="#409eff"><Platform /></el-icon>
            </template>
          </el-empty>
          <div class="layer-intro">
            <el-row :gutter="20">
              <el-col :span="6">
                <el-card>
                  <h4>采集层</h4>
                  <p>多模态数据接入与压缩</p>
                </el-card>
              </el-col>
              <el-col :span="6">
                <el-card>
                  <h4>计算层</h4>
                  <p>时间同步与数据融合</p>
                </el-card>
              </el-col>
              <el-col :span="6">
                <el-card>
                  <h4>存储层</h4>
                  <p>多模态数据存储与索引</p>
                </el-card>
              </el-col>
              <el-col :span="6">
                <el-card>
                  <h4>服务层</h4>
                  <p>服务治理与数据封装</p>
                </el-card>
              </el-col>
            </el-row>
          </div>
        </div>
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Download, Cpu, Coin, Service, Platform } from '@element-plus/icons-vue'

// 导入各层组件
import CollectionSensor from './views/collection/CollectionSensor.vue'
import CollectionDocument from './views/collection/CollectionDocument.vue'
import ComputationSync from './views/computation/ComputationSync.vue'
import ComputationFusion from './views/computation/ComputationFusion.vue'
import ComputationFeature from './views/computation/ComputationFeature.vue'
import ComputationKnowledge from './views/computation/ComputationKnowledge.vue'
import StorageTimeseries from './views/storage/StorageTimeseries.vue'
import StorageGraph from './views/storage/StorageGraph.vue'
import ServiceQuery from './views/service/ServiceQuery.vue'
import ServiceVisualization from './views/service/ServiceVisualization.vue'
import ServiceAnalysis from './views/service/ServiceAnalysis.vue'
import ServiceDistribution from './views/service/ServiceDistribution.vue'
import ServiceStream from './views/service/ServiceStream.vue'

const activeMenu = ref('')

const handleMenuSelect = (index) => {
  activeMenu.value = index
}
</script>

<style scoped>
.phm-platform {
  min-height: 100vh;
  background: #f5f7fa;
}

.platform-header {
  background: linear-gradient(135deg, #1e3c72 0%, #2a5298 100%);
  color: white;
  display: flex;
  align-items: center;
  padding: 0 20px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.1);
}

.header-content {
  display: flex;
  align-items: baseline;
  gap: 15px;
}

.header-content h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
}

.subtitle {
  font-size: 14px;
  opacity: 0.9;
}

.main-container {
  height: calc(100vh - 60px);
}

.sidebar {
  background: white;
  box-shadow: 2px 0 8px rgba(0,0,0,0.05);
}

.layer-menu {
  border-right: none;
}

.content-area {
  padding: 20px;
  overflow-y: auto;
}

.welcome-page {
  padding: 40px 0;
}

.layer-intro {
  margin-top: 40px;
  padding: 0 20px;
}

.layer-intro h4 {
  margin: 0 0 10px 0;
  color: #303133;
}

.layer-intro p {
  margin: 0;
  color: #909399;
  font-size: 14px;
}
</style>
