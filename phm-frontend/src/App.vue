<template>
  <div class="app-container">
    <el-container>
      <!-- 顶部导航 -->
      <el-header class="app-header">
        <div class="header-content">
          <h1 class="app-title">PHM Platform</h1>
          <span class="app-subtitle">故障预测与健康管理平台</span>
        </div>
      </el-header>

      <!-- 主体内容 -->
      <el-container>
        <!-- 侧边栏菜单 -->
        <el-aside width="200px" class="app-sidebar">
          <el-menu
            :default-active="activeMenu"
            class="sidebar-menu"
            @select="handleMenuSelect"
          >
            <el-menu-item index="monitor">
              <el-icon><Monitor /></el-icon>
              <span>数据监控</span>
            </el-menu-item>
            <el-menu-item index="query">
              <el-icon><Search /></el-icon>
              <span>数据查询</span>
            </el-menu-item>
            <el-menu-item index="chart">
              <el-icon><TrendCharts /></el-icon>
              <span>可视化图表</span>
            </el-menu-item>
          </el-menu>
        </el-aside>

        <!-- 内容区域 -->
        <el-main class="app-main">
          <DataMonitor v-if="activeMenu === 'monitor'" />
          <DataQuery v-if="activeMenu === 'query'" />
          <DataChart v-if="activeMenu === 'chart'" />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Monitor, Search, TrendCharts } from '@element-plus/icons-vue'
import DataMonitor from './views/DataMonitor.vue'
import DataQuery from './views/DataQuery.vue'
import DataChart from './views/DataChart.vue'

const activeMenu = ref('monitor')

const handleMenuSelect = (index) => {
  activeMenu.value = index
}
</script>

<style scoped>
.app-container {
  height: 100vh;
}

.app-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  display: flex;
  align-items: center;
  padding: 0 20px;
}

.header-content {
  display: flex;
  align-items: baseline;
  gap: 15px;
}

.app-title {
  margin: 0;
  font-size: 24px;
  font-weight: bold;
}

.app-subtitle {
  font-size: 14px;
  opacity: 0.9;
}

.app-sidebar {
  background-color: #f5f7fa;
  border-right: 1px solid #e4e7ed;
}

.sidebar-menu {
  height: 100%;
  border-right: none;
}

.app-main {
  background-color: #fff;
  padding: 20px;
  overflow-y: auto;
}
</style>
