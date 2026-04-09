<template>
  <div class="service-distribution">
    <div class="page-header">
      <h2><el-icon><Promotion /></el-icon> 主动数据分发服务</h2>
      <p class="desc">服务层数据分发：基于 Lazy-Automata 模式的订阅管理与主动数据推送</p>
    </div>

    <el-row :gutter="20">
      <!-- 左侧：订阅管理 -->
      <el-col :span="14">
        <!-- 新建订阅 -->
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span><el-icon><Plus /></el-icon> 新建订阅</span>
              <el-tag type="primary" effect="dark" round size="small">Lazy-Automata</el-tag>
            </div>
          </template>
          <el-form :model="subForm" inline>
            <el-form-item label="设备">
              <el-select v-model="subForm.deviceId" style="width: 130px">
                <el-option label="EQ-001" value="EQ-001" />
                <el-option label="EQ-002" value="EQ-002" />
                <el-option label="EQ-003" value="EQ-003" />
              </el-select>
            </el-form-item>
            <el-form-item label="传感器">
              <el-select v-model="subForm.sensorType" style="width: 110px">
                <el-option label="温度" value="temperature" />
                <el-option label="振动" value="vibration" />
                <el-option label="压力" value="pressure" />
                <el-option label="电流" value="current" />
              </el-select>
            </el-form-item>
            <el-form-item label="策略">
              <el-select v-model="subForm.strategy" style="width: 120px">
                <el-option label="数据变更" value="on_change" />
                <el-option label="周期推送" value="periodic" />
                <el-option label="阈值触发" value="threshold" />
              </el-select>
            </el-form-item>
            <el-form-item label="回调URL">
              <el-input v-model="subForm.callbackUrl" placeholder="http://..." style="width: 200px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="registerSubscription" :loading="registerLoading">
                注册订阅
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 订阅列表 -->
        <el-card shadow="hover" class="section-card">
          <template #header>
            <div class="card-header">
              <span><el-icon><List /></el-icon> 订阅列表</span>
              <el-button size="small" @click="loadSubscriptions">刷新</el-button>
            </div>
          </template>
          <el-table :data="subscriptions" stripe size="small" v-loading="listLoading">
            <el-table-column prop="id" label="ID" width="50" />
            <el-table-column prop="deviceId" label="设备" width="80" />
            <el-table-column prop="sensorType" label="传感器" width="80" />
            <el-table-column prop="strategy" label="策略" width="90">
              <template #default="scope">
                <el-tag size="small" :type="strategyTag(scope.row.strategy)">{{ strategyLabel(scope.row.strategy) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="70">
              <template #default="scope">
                <span :class="['status-dot', scope.row.status === 'active' ? 'status-active' : 'status-inactive']"></span>
                <el-tag size="small" :type="scope.row.status === 'active' ? 'success' : 'info'">{{ scope.row.status === 'active' ? '活跃' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="pushCount" label="推送次数" width="80" />
            <el-table-column prop="lastPushAt" label="最近推送" width="160">
              <template #default="scope">
                {{ scope.row.lastPushAt ? formatTime(scope.row.lastPushAt) : '--' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140">
              <template #default="scope">
                <el-button size="small" type="primary" link @click="triggerPush(scope.row.id)">触发推送</el-button>
                <el-button size="small" type="danger" link @click="unregister(scope.row.id)">取消</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!listLoading && subscriptions.length === 0" description="暂无订阅" />
        </el-card>
      </el-col>

      <!-- 右侧：事件日志 -->
      <el-col :span="10">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span><el-icon><Bell /></el-icon> 分发事件日志</span>
              <el-button size="small" @click="loadEvents">刷新</el-button>
            </div>
          </template>
          <el-timeline>
            <el-timeline-item
              v-for="(event, idx) in events"
              :key="idx"
              :type="eventTagType(event.type)"
              :timestamp="formatTime(event.timestamp)"
              placement="top"
            >
              <div class="event-item">
                <el-tag size="small" :type="eventTagType(event.type)" style="margin-right: 8px">{{ event.type }}</el-tag>
                {{ event.message }}
              </div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-if="events.length === 0" description="暂无事件" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Promotion, Plus, List, Bell } from '@element-plus/icons-vue'
import { serviceApi } from '../../api/request'

const registerLoading = ref(false)
const listLoading = ref(false)
const subscriptions = ref([])
const events = ref([])

const subForm = reactive({
  deviceId: 'EQ-001',
  sensorType: 'temperature',
  strategy: 'on_change',
  callbackUrl: 'http://localhost:9090/callback'
})

const strategyLabel = (s) => ({ on_change: '数据变更', periodic: '周期推送', threshold: '阈值触发' }[s] || s)
const strategyTag = (s) => ({ on_change: 'primary', periodic: 'success', threshold: 'warning' }[s] || 'info')
const eventTagType = (t) => ({ REGISTER: 'success', UNREGISTER: 'info', PUSH: 'primary', ERROR: 'danger' }[t] || 'info')

const formatTime = (ts) => {
  if (!ts) return '--'
  const d = new Date(ts)
  if (isNaN(d.getTime())) return ts
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const registerSubscription = async () => {
  registerLoading.value = true
  try {
    const res = await serviceApi.distributeRegister(subForm)
    if (res.status === 'success') {
      ElMessage.success('订阅注册成功，ID: ' + res.subscriptionId)
      await loadSubscriptions()
      await loadEvents()
    }
  } catch (e) {
    ElMessage.error('注册失败: ' + e.message)
  } finally {
    registerLoading.value = false
  }
}

const unregister = async (id) => {
  try {
    await ElMessageBox.confirm('确定要取消该订阅吗？', '取消确认', { type: 'warning' })
    const res = await serviceApi.distributeUnregister(id)
    if (res.status === 'success') {
      ElMessage.success('订阅已取消')
      await loadSubscriptions()
      await loadEvents()
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('取消失败: ' + e.message)
  }
}

const triggerPush = async (id) => {
  try {
    await ElMessageBox.confirm('确定要触发推送吗？', '推送确认', { type: 'warning' })
    const res = await serviceApi.distributeTrigger(id)
    if (res.status === 'success') {
      ElMessage.success('推送触发成功')
      await loadSubscriptions()
      await loadEvents()
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('推送失败: ' + e.message)
  }
}

const loadSubscriptions = async () => {
  listLoading.value = true
  try {
    const res = await serviceApi.distributeList()
    subscriptions.value = res.data || []
  } catch (e) {
    console.error('加载订阅失败:', e)
  } finally {
    listLoading.value = false
  }
}

const loadEvents = async () => {
  try {
    const res = await serviceApi.distributeEvents()
    events.value = res.data || []
  } catch (e) {
    console.error('加载事件失败:', e)
  }
}

onMounted(() => {
  loadSubscriptions()
  loadEvents()
})
</script>

<style scoped>
.service-distribution {
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
  margin: 0 0 20px;
  font-size: 14px;
}
.section-card {
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
.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
  vertical-align: middle;
}
.status-active {
  background: #67c23a;
  box-shadow: 0 0 6px rgba(103, 194, 58, 0.6);
  animation: pulse 1.5s infinite;
}
.status-inactive {
  background: #909399;
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}
.event-item {
  font-size: 13px;
  color: #303133;
}
</style>
