<template>
  <div class="dashboard">
    <h1>欢迎使用 IMKQAS 医疗知识问答系统</h1>
    <p class="subtitle">您的智能医疗助手</p>

    <div class="stats-grid">
      <el-card class="stat-card">
        <div class="stat-content">
          <div class="stat-icon">
            <el-icon size="32" color="#005eb8">
              <ChatDotRound />
            </el-icon>
          </div>
          <div class="stat-info">
            <h3>智能问答</h3>
            <p class="stat-desc">进行医疗咨询和诊断建议</p>
          </div>
        </div>
      </el-card>

      <el-card class="stat-card">
        <div class="stat-content">
          <div class="stat-icon">
            <el-icon size="32" color="#005eb8">
              <Document />
            </el-icon>
          </div>
          <div class="stat-info">
            <h3>知识库管理</h3>
            <p class="stat-desc">上传和管理医疗文档</p>
          </div>
        </div>
      </el-card>

    </div>

    <el-card class="quick-actions-card">
      <h3>快速开始</h3>
      <div class="quick-actions">
        <el-button type="primary" size="large" @click="gotoQa">
          <el-icon><ChatDotRound /></el-icon>
          开始医疗问答
        </el-button>
        <el-button size="large" @click="gotoKnowledge">
          <el-icon><Document /></el-icon>
          管理知识库
        </el-button>
      </div>
    </el-card>

    <div class="dashboard-grid">
      <!-- 统计图表 -->
      <el-card class="chart-card">
        <template #header>
          <div class="card-header">
            <h3>问答使用趋势</h3>
            <el-select v-model="chartPeriod" size="small" style="width: 120px">
              <el-option label="最近7天" value="7d" />
              <el-option label="最近30天" value="30d" />
              <el-option label="最近90天" value="90d" />
            </el-select>
          </div>
        </template>
        <div class="chart-container">
          <div class="chart-placeholder">
            <el-icon size="48" color="#cbd5e1">
              <Histogram />
            </el-icon>
            <p>图表展示问答使用趋势数据</p>
            <p class="chart-note">集成ECharts后可显示可视化图表</p>
          </div>
        </div>
      </el-card>

      <!-- 最近活动 -->
      <el-card class="activity-card">
        <template #header>
          <div class="card-header">
            <h3>最近活动</h3>
            <span></span>          </div>
        </template>
        <div class="activity-list">
          <div v-for="(activity, index) in recentActivities" :key="index" class="activity-item">
            <div class="activity-icon">
              <el-icon :size="20" :color="getActivityColor(activity.type)">
                <component :is="getActivityIcon(activity.type)" />
              </el-icon>
            </div>
            <div class="activity-content">
              <div class="activity-title">{{ activity.title }}</div>
              <div class="activity-time">{{ activity.time }}</div>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 系统状态 -->
      <el-card class="status-card">
        <template #header>
          <h3>系统状态</h3>
        </template>
        <div class="status-list">
          <div class="status-item">
            <div class="status-label">问答服务</div>
            <div class="status-value">
              <el-tag type="success" size="small">正常</el-tag>
            </div>
          </div>
          <div class="status-item">
            <div class="status-label">知识库</div>
            <div class="status-value">
              <el-tag type="success" size="small">在线</el-tag>
            </div>
          </div>
          <div class="status-item">
            <div class="status-label">向量数据库</div>
            <div class="status-value">
              <el-tag type="warning" size="small">负载较高</el-tag>
            </div>
          </div>
          <div class="status-item">
            <div class="status-label">用户认证</div>
            <div class="status-value">
              <el-tag type="success" size="small">正常</el-tag>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 快捷操作 -->
      <el-card class="quick-card">
        <template #header>
          <h3>快捷操作</h3>
        </template>
        <div class="quick-actions-grid">
          <el-button class="quick-action-btn" @click="gotoUpload">
            <el-icon><UploadFilled /></el-icon>
            <span>上传文档</span>
          </el-button>
          <el-button class="quick-action-btn" @click="gotoCreateQa">
            <el-icon><EditPen /></el-icon>
            <span>新建问答</span>
          </el-button>
          <el-button class="quick-action-btn" @click="gotoSettings">
            <el-icon><Setting /></el-icon>
            <span>系统设置</span>
          </el-button>
          <el-button class="quick-action-btn" @click="gotoHelp">
            <el-icon><QuestionFilled /></el-icon>
            <span>帮助中心</span>
          </el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ChatDotRound,
  Document,
  Histogram,
  UploadFilled,
  EditPen,
  Setting,
  QuestionFilled,
  ChatLineRound,
  Warning,
  Clock
} from '@element-plus/icons-vue'

import { ElMessage } from 'element-plus'

const router = useRouter()

const gotoQa = () => {
  router.push('/qa')
}

const gotoKnowledge = () => {
  router.push('/knowledge')
}

// 新添加的数据和方法
const chartPeriod = ref('7d')

const recentActivities = ref([
  { type: 'qa', title: '用户张三进行了医疗咨询', time: '10分钟前' },
  { type: 'upload', title: '李医生上传了新的医疗指南', time: '1小时前' },
  { type: 'system', title: '系统完成夜间数据备份', time: '3小时前' },
  { type: 'user', title: '王护士更新了个人信息', time: '5小时前' },
  { type: 'warning', title: '问答服务负载较高，请注意', time: '昨天' }
])

const getActivityIcon = (type: string) => {
  const icons: Record<string, any> = {
    qa: ChatLineRound,
    upload: UploadFilled,
    system: Setting,
    user: EditPen,
    warning: Warning
  }
  return icons[type] || Clock
}

const getActivityColor = (type: string) => {
  const colors: Record<string, string> = {
    qa: '#005eb8', // brand
    upload: '#ed6c02', // processing
    system: '#424752', // on-surface-variant
    user: '#2e7d32', // success
    warning: '#d32f2f' // danger
  }
  return colors[type] || '#424752' // on-surface-variant
}

const gotoUpload = () => {
  router.push('/knowledge')
}

const gotoCreateQa = () => {
  router.push('/qa')
}

const gotoSettings = () => {
  ElMessage.info('系统设置功能开发中')
}

const gotoHelp = () => {
  ElMessage.info('帮助中心功能开发中')
}
</script>

<style scoped>
.dashboard {
  max-width: 1200px;
  margin: 0 auto;
  padding: var(--spacing-xl);
}

.subtitle {
  color: var(--color-on-surface-variant);
  margin-bottom: var(--spacing-xl);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: var(--spacing-lg);
  margin: var(--spacing-xl) 0;
}

.stat-card {
  cursor: pointer;
  transition: transform 0.2s ease;
}

.stat-card:hover {
  transform: translateY(-4px);
}

.stat-content {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 60px;
  height: 60px;
  background-color: var(--color-surface-container-low);
  border-radius: var(--radius-lg);
}

.stat-info h3 {
  margin: 0;
  font-size: 1.1rem;
  color: var(--color-on-surface);
}

.stat-desc {
  margin: var(--spacing-xs) 0 0;
  color: var(--color-on-surface-variant);
  font-size: 0.9rem;
}

.quick-actions-card {
  margin-top: var(--spacing-xl);
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-md);
  margin-top: var(--spacing-lg);
}

/* 新添加的样式 */
.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(350px, 1fr));
  gap: var(--spacing-lg);
  margin-top: var(--spacing-xl);
}

.chart-card,
.activity-card,
.status-card,
.quick-card {
  min-height: 300px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-md);
}

.card-header h3 {
  margin: 0;
  font-size: 1.1rem;
  color: var(--color-on-surface);
}

.chart-container {
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chart-placeholder {
  text-align: center;
  color: var(--color-on-surface-variant);
}

.chart-note {
  font-size: 0.8rem;
  margin-top: var(--spacing-xs);
  color: var(--color-outline-variant);
}

.activity-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.activity-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-sm);
  border-radius: var(--radius-md);
  transition: background-color 0.2s ease;
}

.activity-item:hover {
  background-color: var(--color-surface-container-low);
}

.activity-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  background-color: var(--color-surface-container-low);
  border-radius: var(--radius-md);
}

.activity-content {
  flex: 1;
}

.activity-title {
  font-size: 0.9rem;
  color: var(--color-on-surface);
  margin-bottom: 2px;
}

.activity-time {
  font-size: 0.8rem;
  color: var(--color-on-surface-variant);
}

.status-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.status-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-sm);
  border-bottom: 1px solid var(--color-outline-variant);
}

.status-item:last-child {
  border-bottom: none;
}

.status-label {
  font-size: 0.9rem;
  color: var(--color-on-surface);
}

.quick-actions-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--spacing-md);
}

.quick-action-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 80px;
  padding: var(--spacing-md);
  background: var(--color-surface-container-low);
  border: 1px solid var(--color-outline-variant);
  border-radius: var(--radius-lg);
  transition: all 0.2s ease;
}

.quick-action-btn:hover {
  background: var(--color-surface);
  border-color: var(--color-primary);
  transform: translateY(-2px);
}

.quick-action-btn .el-icon {
  font-size: 24px;
  margin-bottom: var(--spacing-xs);
}

.quick-action-btn span {
  font-size: 0.9rem;
  color: var(--color-on-surface);
}

/* 响应式调整 */
@media (max-width: 768px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }

  .quick-actions-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>