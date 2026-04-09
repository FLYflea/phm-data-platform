<template>
  <div class="phm-platform">
    <!-- 顶部导航 -->
    <el-header class="platform-header">
      <div class="header-content">
        <div class="header-left">
          <el-icon :size="28" color="#fff"><Platform /></el-icon>
          <h1>PHM Platform</h1>
          <el-divider direction="vertical" style="border-color: rgba(255,255,255,0.3)" />
          <span class="subtitle">设备全寿命周期数据管理平台</span>
        </div>
        <div class="header-right">
          <el-tag effect="dark" round type="success" size="small">v1.0</el-tag>
        </div>
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
              <el-icon><Upload /></el-icon>
              <span>采集层</span>
            </template>
            <el-menu-item index="collection-sensor">
              <el-icon><Monitor /></el-icon>
              <span>传感器数据采集</span>
            </el-menu-item>
            <el-menu-item index="collection-document">
              <el-icon><Document /></el-icon>
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
              <el-icon><Timer /></el-icon>
              <span>时间同步</span>
            </el-menu-item>
            <el-menu-item index="computation-fusion">
              <el-icon><Connection /></el-icon>
              <span>数据融合</span>
            </el-menu-item>
            <el-menu-item index="computation-feature">
              <el-icon><TrendCharts /></el-icon>
              <span>特征工程</span>
            </el-menu-item>
            <el-menu-item index="computation-knowledge">
              <el-icon><Share /></el-icon>
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
              <el-icon><DataLine /></el-icon>
              <span>时序数据查询</span>
            </el-menu-item>
            <el-menu-item index="storage-graph">
              <el-icon><Share /></el-icon>
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
              <el-icon><Search /></el-icon>
              <span>统一查询</span>
            </el-menu-item>
            <el-menu-item index="service-visualization">
              <el-icon><PieChart /></el-icon>
              <span>数据可视化</span>
            </el-menu-item>
            <el-menu-item index="service-analysis">
              <el-icon><DataAnalysis /></el-icon>
              <span>数据分析</span>
            </el-menu-item>
            <el-menu-item index="service-distribution">
              <el-icon><Promotion /></el-icon>
              <span>数据分发</span>
            </el-menu-item>
            <el-menu-item index="service-stream">
              <el-icon><VideoPlay /></el-icon>
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
          <div class="welcome-hero">
            <el-icon :size="64" color="#409eff"><Platform /></el-icon>
            <h2>PHM 智能运维平台</h2>
            <p class="welcome-desc">基于四层微服务架构的设备全寿命周期数据管理系统</p>
          </div>
          <div class="layer-intro">
            <el-row :gutter="20">
              <el-col :span="6">
                <div class="layer-card layer-card-collection" @click="handleMenuSelect('collection-sensor')">
                  <div class="layer-card-icon">
                    <el-icon :size="32"><Upload /></el-icon>
                  </div>
                  <h4>采集层</h4>
                  <p>传感器接入 / CSV导入 / 文档解析</p>
                  <div class="layer-card-count">2 个功能模块</div>
                </div>
              </el-col>
              <el-col :span="6">
                <div class="layer-card layer-card-computation" @click="handleMenuSelect('computation-sync')">
                  <div class="layer-card-icon">
                    <el-icon :size="32"><Cpu /></el-icon>
                  </div>
                  <h4>计算层</h4>
                  <p>时间同步 / 数据融合 / 特征工程</p>
                  <div class="layer-card-count">4 个功能模块</div>
                </div>
              </el-col>
              <el-col :span="6">
                <div class="layer-card layer-card-storage" @click="handleMenuSelect('storage-timeseries')">
                  <div class="layer-card-icon">
                    <el-icon :size="32"><Coin /></el-icon>
                  </div>
                  <h4>存储层</h4>
                  <p>时序数据 / 知识图谱 / FMECA</p>
                  <div class="layer-card-count">2 个功能模块</div>
                </div>
              </el-col>
              <el-col :span="6">
                <div class="layer-card layer-card-service" @click="handleMenuSelect('service-query')">
                  <div class="layer-card-icon">
                    <el-icon :size="32"><Service /></el-icon>
                  </div>
                  <h4>服务层</h4>
                  <p>查询 / 可视化 / 分析 / 分发</p>
                  <div class="layer-card-count">5 个功能模块</div>
                </div>
              </el-col>
            </el-row>
          </div>
          <div class="welcome-footer">
            <p>PHM Platform &copy; 2026 · Spring Boot + Vue 3 + PostgreSQL + Neo4j</p>
          </div>
        </div>
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Upload, Cpu, Coin, Service, Platform, Monitor, Document, Timer, Connection, TrendCharts, Share, DataLine, Search, PieChart, DataAnalysis, Promotion, VideoPlay } from '@element-plus/icons-vue'

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
  background: #f0f2f5;
}

.platform-header {
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  color: white;
  display: flex;
  align-items: center;
  padding: 0 24px;
  box-shadow: 0 2px 16px rgba(0,0,0,0.15);
  height: 56px;
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-content h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 1px;
}

.subtitle {
  font-size: 13px;
  opacity: 0.8;
  letter-spacing: 0.5px;
}

.main-container {
  height: calc(100vh - 56px);
}

.sidebar {
  background: white;
  box-shadow: 2px 0 12px rgba(0,0,0,0.06);
  overflow-y: auto;
}

.layer-menu {
  border-right: none;
  padding-top: 8px;
}

.content-area {
  padding: 24px;
  overflow-y: auto;
  background: #f0f2f5;
}

/* 欢迎页样式 */
.welcome-page {
  padding: 20px 0;
}

.welcome-hero {
  text-align: center;
  padding: 40px 0 20px;
}

.welcome-hero h2 {
  margin: 16px 0 8px;
  font-size: 28px;
  font-weight: 700;
  color: #1a1a2e;
}

.welcome-desc {
  color: #909399;
  font-size: 15px;
  margin: 0;
}

.layer-intro {
  margin-top: 32px;
  padding: 0 12px;
}

.layer-card {
  background: white;
  border-radius: 12px;
  padding: 28px 20px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s ease;
  border: 1px solid #e8ecf1;
  position: relative;
  overflow: hidden;
}

.layer-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
}

.layer-card:hover {
  transform: translateY(-6px);
  box-shadow: 0 12px 32px rgba(0,0,0,0.1);
}

.layer-card-collection::before { background: linear-gradient(90deg, #409eff, #79bbff); }
.layer-card-computation::before { background: linear-gradient(90deg, #e6a23c, #f0c78a); }
.layer-card-storage::before { background: linear-gradient(90deg, #67c23a, #95d475); }
.layer-card-service::before { background: linear-gradient(90deg, #9b59b6, #c39bd3); }

.layer-card-collection .layer-card-icon { color: #409eff; }
.layer-card-computation .layer-card-icon { color: #e6a23c; }
.layer-card-storage .layer-card-icon { color: #67c23a; }
.layer-card-service .layer-card-icon { color: #9b59b6; }

.layer-card-icon {
  margin-bottom: 12px;
}

.layer-card h4 {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.layer-card p {
  margin: 0 0 12px;
  color: #909399;
  font-size: 13px;
  line-height: 1.5;
}

.layer-card-count {
  font-size: 12px;
  color: #c0c4cc;
  font-weight: 500;
}

.welcome-footer {
  text-align: center;
  margin-top: 48px;
  padding-top: 24px;
  border-top: 1px solid #e8ecf1;
}

.welcome-footer p {
  color: #c0c4cc;
  font-size: 12px;
  margin: 0;
}
</style>

<!-- 全局样式增强 -->
<style>
/* 全局卡片动画 */
.el-card {
  transition: all 0.3s ease;
  border-radius: 8px !important;
}
.el-card:hover {
  border-color: #d4d7de;
}

/* 全局按钮圆角 */
.el-button {
  border-radius: 6px;
}

/* 表单必填项星号 */
.el-form-item.is-required .el-form-item__label::before {
  content: '*';
  color: #f56c6c;
  margin-right: 4px;
}

/* 图表容器加载效果 */
.chart-container:empty,
.chart-container-large:empty {
  background: linear-gradient(90deg, #f0f2f5 25%, #e8ecf1 50%, #f0f2f5 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s infinite;
  border-radius: 6px;
}

@keyframes skeleton-loading {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* 统一表格样式 */
.el-table {
  border-radius: 6px;
  overflow: hidden;
}

/* 标签圆角 */
.el-tag {
  border-radius: 4px;
}

/* 全局滚动条美化 */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}
::-webkit-scrollbar-thumb {
  background: #c0c4cc;
  border-radius: 3px;
}
::-webkit-scrollbar-thumb:hover {
  background: #909399;
}
::-webkit-scrollbar-track {
  background: transparent;
}

/* 全局通知样式增强 */
.el-message {
  border-radius: 8px !important;
  box-shadow: 0 4px 16px rgba(0,0,0,0.1) !important;
}
.el-message-box {
  border-radius: 12px !important;
}

/* 全局过渡动画 */
.el-card .el-card__body {
  transition: opacity 0.3s ease;
}

/* el-descriptions 圆角 */
.el-descriptions {
  border-radius: 6px;
  overflow: hidden;
}

/* el-statistic 增强 */
.el-statistic__head {
  font-size: 13px;
  color: #909399;
}
.el-statistic__content {
  font-weight: 700;
}

/* el-empty 增强 */
.el-empty__description p {
  color: #909399;
  font-size: 14px;
}
</style>
