<template>
  <div class="qa-view">

    <!-- 顶部导航栏 -->
    <header class="qa-header">
      <div class="custom-flex custom-items-center custom-gap-8">
        <div class="qa-header-logo">
          Clinical Precision RAG
        </div>
        <nav class="custom-hidden custom-md-flex custom-items-center custom-gap-6">
          <a class="qa-nav-link qa-nav-link-active" href="/qa">智能问答</a>
          <a class="qa-nav-link qa-nav-link-inactive" href="/knowledge">知识库</a>
        </nav>
      </div>
      <div class="custom-flex custom-items-center custom-gap-4">
        <button class="qa-icon-btn material-symbols-outlined">notifications</button>
        <button class="qa-icon-btn material-symbols-outlined">settings</button>
        <img alt="User Profile" class="qa-header-avatar" data-alt="close-up professional portrait of a medical doctor in a white coat with a clean clinical background" src="https://lh3.googleusercontent.com/aida-public/AB6AXuDMKPVJL-B3aLQu4CtZ_KOGUSY3VDwcOYDbQaQbUQspANy_0Ie-w9P92EaTPnn6QSN0VqL5W2tyPmdWOra_LQYUSq7f3u8wKEjXbhb_oQmjYT9M-oJkgZJsjFsMfLtW2n5pRZV_wRSgR27cQLetYJP--OkjG_2v03qr2MRNl_66Ba7Aluj_lMEe5wlSKT2HJ-ATtZhSYgWpw4qILX2CIEX0Um5CbiBlIhnGqbbZoILW5Gl4rGmzfhFQrAERT2VMBn7-EYLXnzDmLBg"/>
      </div>
    </header>

    <!-- 主内容区域（侧边栏 + 聊天区域 + 检索面板） -->
    <div class="qa-main-content">

      <!-- 侧边栏 -->
      <aside class="qa-sidebar">
        <div class="custom-p-6 custom-pt-4">
          <div class="qa-sidebar-header">
            <div class="qa-sidebar-icon">
              <span class="material-symbols-outlined" style="font-variation-settings: 'FILL' 1;">clinical_notes</span>
            </div>
            <div>
              <div class="qa-sidebar-title">会话管理</div>
              <div class="qa-sidebar-subtitle">最近咨询列表</div>
            </div>
          </div>
          <button @click="createNewSession" class="qa-new-btn">
            <span class="material-symbols-outlined text-sm">add</span>
            新建咨询
          </button>
        </div>

        <nav class="custom-flex-1 qa-no-scrollbar custom-overflow-y-auto space-y-1">
          <div v-for="session in sessions" :key="session.id"
               @click="switchSession(session.id)"
               :class="['qa-session-item', session.id === activeSessionId ? 'qa-session-active' : 'qa-session-inactive']">
            <span class="material-symbols-outlined text-lg">{{ session.icon }}</span>
            <span class="qa-session-title">{{ session.title }}</span>
          </div>
        </nav>

        <div class="qa-sidebar-footer">
          <div @click="deleteActiveSession" class="qa-sidebar-footer-item qa-clickable">
            <span class="material-symbols-outlined text-lg">delete</span>
            回收站
          </div>
          <div class="qa-sidebar-footer-item">
            <span class="material-symbols-outlined text-lg">help</span>
            帮助中心
          </div>
        </div>
      </aside>

      <!-- 聊天界面 -->
      <section class="qa-chat-section">
        <!-- 聊天滑动区域 -->
        <div class="qa-chat-scroll qa-no-scrollbar qa-scroll-smooth">
          <!-- 医疗安全提示 -->
          <div class="custom-mx-6 custom-mt-4">
            <div class="qa-safety-tip">
              <span class="qa-safety-tip-icon material-symbols-outlined">warning</span>
              <p class="qa-safety-tip-text">
                <span class="qa-safety-tip-bold">医疗安全提示：</span>本系统提供的信息仅供临床参考，不作为最终诊断及用药依据。请结合患者实际体征及相关检查结果，由执业医师进行最终决策。
              </p>
            </div>
          </div>
          <!-- 聊天消息列表 -->
          <div class="qa-messages-container">
            <div class="qa-messages-list">
              <div v-for="message in messages" :key="message.id"
                   :class="['custom-flex', message.role === 'user' ? 'custom-justify-end' : 'custom-justify-start']">
                <!-- 用户消息 -->
                <div v-if="message.role === 'user'" class="qa-message-user">
                  <p class="qa-message-user-text">{{ message.content }}</p>
                </div>
                <!-- AI回复 -->
                <div v-else class="qa-message-ai-wrapper">
                  <div class="qa-ai-identity">
                    <div class="qa-ai-avatar">
                      <span class="material-symbols-outlined text-sm" style="font-variation-settings: 'FILL' 1;">smart_toy</span>
                    </div>
                    <span class="qa-ai-name">PRECISION AI 临床决策支持</span>
                  </div>
                  <div v-if="message.content || !message.questionnaire" class="qa-message-ai">
                    <!-- 思考中状态（仅当无内容且无问卷卡片时显示） -->
                    <div v-if="!message.content" class="qa-thinking">
                      <span class="qa-thinking-text">正在思考中</span>
                      <span class="qa-thinking-dots">
                        <span class="qa-thinking-dot">.</span>
                        <span class="qa-thinking-dot">.</span>
                        <span class="qa-thinking-dot">.</span>
                      </span>
                    </div>
                    <div v-else class="qa-ai-content text-sm text-on-surface leading-relaxed" v-html="message.content"></div>
                  </div>
                  <!-- 问卷建议卡片 -->
                  <div v-if="message.questionnaire?.type === 'suggestion'" class="qa-questionnaire-card qa-suggestion-card">
                    <div class="qa-suggestion-header">
                      <span class="material-symbols-outlined qa-suggestion-icon">assignment</span>
                      <div>
                        <div class="qa-suggestion-title">{{ message.questionnaire.suggestionText || '检测到您可能需要填写量表' }}</div>
                        <div v-if="message.questionnaire.questionnaireTitle" class="qa-suggestion-subtitle">
                          建议填写：{{ message.questionnaire.questionnaireTitle }}
                        </div>
                        <div v-if="message.questionnaire.confidence" class="qa-suggestion-confidence">
                          匹配置信度：{{ (message.questionnaire.confidence * 100).toFixed(0) }}%
                        </div>
                      </div>
                    </div>
                    <div class="qa-suggestion-actions">
                      <button @click="startInterviewFlow(message.questionnaire.questionnaireId!, message.questionnaire.questionnaireTitle!)"
                              :disabled="interviewActive"
                              class="qa-suggestion-btn qa-suggestion-btn-primary">
                        <span class="material-symbols-outlined text-sm">play_arrow</span>
                        {{ interviewActive ? '填表中...' : '开始填表' }}
                      </button>
                      <button @click="cancelInterview" class="qa-suggestion-btn qa-suggestion-btn-secondary">
                        忽略
                      </button>
                    </div>
                  </div>
                  <!-- 问卷问题卡片 -->
                  <div v-if="message.questionnaire?.type === 'question'" class="qa-questionnaire-card qa-question-card">
                    <div class="qa-question-progress-bar">
                      <div class="qa-question-progress-fill" :style="{ width: ((message.questionnaire.currentIndex! + 1) / message.questionnaire.totalQuestions! * 100) + '%' }"></div>
                    </div>
                    <div class="qa-question-progress-text">
                      问题 {{ message.questionnaire.currentIndex! + 1 }} / {{ message.questionnaire.totalQuestions }}
                    </div>
                    <div class="qa-question-text">{{ message.questionnaire.questionText }}</div>
                    <div class="qa-question-hint">
                      <span class="material-symbols-outlined qa-hint-icon">info</span>
                      可直接点击下方选项，也可在输入框中用自然语言描述您的情况
                    </div>
                    <div class="qa-question-options">
                      <button v-for="opt in message.questionnaire.options"
                              :key="opt.code"
                              @click="submitInterviewAnswer(opt.code, opt.display)"
                              :disabled="interviewLoading"
                              class="qa-option-btn">
                        <span class="qa-option-code">{{ opt.code }}</span>
                        <span class="qa-option-display">{{ opt.display }}</span>
                      </button>
                    </div>
                  </div>
                  <!-- 问卷完成卡片 -->
                  <div v-if="message.questionnaire?.type === 'completion'" class="qa-questionnaire-card qa-completion-card">
                    <div class="qa-completion-header">
                      <span class="material-symbols-outlined qa-completion-icon">check_circle</span>
                      <span class="qa-completion-title">问卷填写完成</span>
                    </div>
                    <div v-if="message.questionnaire.severity" class="qa-completion-score">
                      <span class="qa-completion-score-label">评估结果：</span>
                      <span :class="['qa-severity-badge', 'qa-severity-' + getSeverityLevel(message.questionnaire.severity)]">
                        {{ message.questionnaire.severity }}
                      </span>
                    </div>
                    <div v-if="message.questionnaire.totalScore !== undefined" class="qa-completion-score-num">
                      总分：{{ message.questionnaire.totalScore }} / {{ message.questionnaire.maxScore }}
                    </div>
                    <div v-if="message.questionnaire.interpretation" class="qa-completion-interpretation">
                      {{ message.questionnaire.interpretation }}
                    </div>
                    <div v-if="message.questionnaire.analysisSummary" class="qa-analysis-summary">
                      <div class="qa-analysis-summary-title">AI 分析摘要</div>
                      <div class="qa-analysis-summary-content">{{ message.questionnaire.analysisSummary }}</div>
                    </div>
                    <button v-if="message.questionnaire.analysisId"
                            @click="showDetailedReport(message)"
                            class="qa-report-btn">
                      <span class="material-symbols-outlined text-sm">description</span>
                      查看详细评估报告
                    </button>
                    <div v-if="message.questionnaire.completionMessage" class="qa-completion-message">
                      {{ message.questionnaire.completionMessage }}
                    </div>
                  </div>
                  <!-- 安全警报卡片 -->
                  <div v-if="message.questionnaire?.type === 'safety_alert'" class="qa-questionnaire-card qa-safety-alert-card">
                    <div class="qa-safety-alert-header">
                      <span class="material-symbols-outlined qa-safety-alert-icon">warning</span>
                      <span class="qa-safety-alert-title">安全提醒</span>
                    </div>
                    <div v-if="message.questionnaire.reason" class="qa-safety-alert-reason">
                      {{ message.questionnaire.reason }}
                    </div>
                    <div v-if="message.questionnaire.alertMessage" class="qa-safety-alert-message">
                      {{ message.questionnaire.alertMessage }}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <!-- 输入区域 -->
        <div class="qa-input-area">
          <div class="qa-input-wrapper">
            <textarea v-model="inputText" @keyup.enter="sendMessage"
                      @compositionstart="isComposing = true"
                      @compositionend="isComposing = false"
                      class="qa-input-field"
                      placeholder="输入临床问题或上传病例附件..." rows="1"></textarea>
            <div class="qa-input-actions">
              <button class="qa-input-btn material-symbols-outlined">attach_file</button>
              <button type="button" @click="sendMessage" class="qa-send-btn">
                <span class="material-symbols-outlined text-sm">send</span>
              </button>
            </div>
          </div>
          <div class="qa-input-hint">
            <span class="qa-input-hint-text">支持 PDF 病历解析 · 实时检索临床指南</span>
          </div>
        </div>
      </section>

      <!-- 检索路径面板（右侧） -->
      <aside class="qa-panel">
        <div class="qa-panel-section">
          <h3 class="qa-panel-title">
            <span class="qa-panel-title-icon material-symbols-outlined">schema</span>
            知识检索路径
          </h3>
          <div>
            <div v-for="(step, index) in retrievalSteps" :key="index"
                 v-show="expandedSteps || index < 3"
                 class="qa-retrieval-step">
              <div :class="['qa-step-dot', `qa-step-dot-${getStepColor(step)}`]"></div>
              <div :class="['qa-step-title', `qa-step-title-${getStepColor(step)}`]">{{ step.stepName }}</div>
              <div class="qa-step-card">
                <div class="qa-step-card-header">
                  <span class="qa-step-status" :class="`qa-step-status-${getStepColor(step)}`">
                    {{ getStatusLabel(step.status) }}
                  </span>
                  <span class="qa-step-duration">{{ formatDuration(step.durationMs) }}</span>
                </div>
                <div v-if="getStepResultItems(step).length > 0" class="qa-step-result">
                  <div v-for="item in getStepResultItems(step)" :key="item.label" class="qa-step-result-item">
                    <span class="qa-step-result-label">{{ item.label }}</span>
                    <span class="qa-step-result-value">{{ item.value }}</span>
                  </div>
                </div>
              </div>
            </div>
            <button v-if="retrievalSteps.length > 3"
                    @click="expandedSteps = !expandedSteps"
                    class="qa-expand-toggle">
              {{ expandedSteps ? '收起' : `展开全部 ${retrievalSteps.length} 步` }}
            </button>
          </div>
        </div>
      </aside>

    </div>

    <!-- 详细评估报告弹窗 -->
    <div v-if="showReportDialog" class="qa-report-overlay" @click.self="showReportDialog = false">
      <div class="qa-report-dialog">
        <div class="qa-report-dialog-header">
          <h3 class="qa-report-dialog-title">AI 详细评估报告</h3>
          <button @click="showReportDialog = false" class="qa-report-dialog-close">
            <span class="material-symbols-outlined">close</span>
          </button>
        </div>
        <div v-if="reportLoading" class="qa-report-loading">
          <span class="qa-thinking-text">正在加载报告...</span>
        </div>
        <div v-else-if="currentReport" class="qa-report-content">
          <!-- 摘要 -->
          <div class="qa-report-section">
            <h4 class="qa-report-section-title">评估摘要</h4>
            <p class="qa-report-text">{{ currentReport.summary }}</p>
          </div>
          <!-- 风险评估 -->
          <div v-if="currentReport.riskAssessment" class="qa-report-section">
            <h4 class="qa-report-section-title">风险评估</h4>
            <div class="qa-report-risk">
              <span :class="['qa-severity-badge', 'qa-severity-' + getSeverityLevel(currentReport.riskAssessment.level)]">
                {{ currentReport.riskAssessment.level }}
              </span>
              <p class="qa-report-text">{{ currentReport.riskAssessment.description }}</p>
              <p v-if="currentReport.riskAssessment.requiresUrgentAttention" class="qa-report-urgent">
                需要紧急关注
              </p>
            </div>
          </div>
          <!-- 详细分析 -->
          <div v-if="currentReport.detailAnalysis" class="qa-report-section">
            <h4 class="qa-report-section-title">详细分析</h4>
            <p class="qa-report-text">{{ currentReport.detailAnalysis.overview }}</p>
            <ul v-if="currentReport.detailAnalysis.patterns?.length" class="qa-report-list">
              <li v-for="(p, i) in currentReport.detailAnalysis.patterns" :key="i">{{ p }}</li>
            </ul>
            <p class="qa-report-conclusion">{{ currentReport.detailAnalysis.conclusion }}</p>
          </div>
          <!-- 建议 -->
          <div v-if="currentReport.recommendations" class="qa-report-section">
            <h4 class="qa-report-section-title">分层建议</h4>
            <div v-if="currentReport.recommendations.immediate?.length" class="qa-report-rec-group">
              <h5 class="qa-report-rec-label">即时行动</h5>
              <div v-for="(r, i) in currentReport.recommendations.immediate" :key="i" class="qa-report-rec-item">
                <strong>{{ r.title }}</strong>
                <p>{{ r.description }}</p>
              </div>
            </div>
            <div v-if="currentReport.recommendations.shortTerm?.length" class="qa-report-rec-group">
              <h5 class="qa-report-rec-label">短期建议</h5>
              <div v-for="(r, i) in currentReport.recommendations.shortTerm" :key="i" class="qa-report-rec-item">
                <strong>{{ r.title }}</strong>
                <p>{{ r.description }}</p>
              </div>
            </div>
            <div v-if="currentReport.recommendations.professional?.length" class="qa-report-rec-group">
              <h5 class="qa-report-rec-label">专业资源</h5>
              <div v-for="(r, i) in currentReport.recommendations.professional" :key="i" class="qa-report-rec-item">
                <strong>{{ r.title }}</strong>
                <p>{{ r.description }}</p>
                <span v-if="r.resource" class="qa-report-rec-resource">{{ r.resource }}</span>
              </div>
            </div>
          </div>
          <!-- 随访建议 -->
          <div v-if="currentReport.followUp" class="qa-report-section">
            <h4 class="qa-report-section-title">随访建议</h4>
            <p class="qa-report-text"><strong>建议时间：</strong>{{ currentReport.followUp.suggestedDate }}</p>
            <p class="qa-report-text">{{ currentReport.followUp.rationale }}</p>
          </div>
          <!-- 免责声明 -->
          <div v-if="currentReport.disclaimer" class="qa-report-disclaimer">
            {{ currentReport.disclaimer }}
          </div>
        </div>
        <div v-else class="qa-report-loading">
          <p>无法加载报告，请稍后重试</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { qaService } from '@/api/services/qa.service'
import { conversationService } from '@/api/services/conversation.service'
import { interviewService } from '@/api/services/interview.service'
import { authService } from '@/api/services/auth.service'
import type { Conversation, RetrievalStep } from '@/api/types/qa.types'
import type { AnswerOption, InterviewMessageItem, AnalysisReport } from '@/api/types/interview.types'

interface Session {
  id: string
  title: string
  icon: string
  conversation?: Conversation
}

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  sourceReferences?: string
  questionnaire?: QuestionnaireBlock
}

interface QuestionnaireBlock {
  type: 'suggestion' | 'question' | 'completion' | 'safety_alert'
  suggestionText?: string
  questionnaireTitle?: string
  questionnaireId?: string
  confidence?: number
  linkId?: string
  questionText?: string
  currentIndex?: number
  totalQuestions?: number
  options?: AnswerOption[]
  totalScore?: number
  maxScore?: number
  severity?: string
  interpretation?: string
  analysisSummary?: string
  analysisId?: string
  completionMessage?: string
  reason?: string
  alertMessage?: string
  sessionId?: string
}

// 会话数据
const sessions = ref<Session[]>([])
const activeSessionId = ref<string | null>(null)
const loadingSessions = ref(false)

// 消息数据
const messages = ref<ChatMessage[]>([])
const loadingMessages = ref(false)

// 输入文本
const inputText = ref('')

// 检索路径步骤（初始为空，收到API响应后填充）
const retrievalSteps = ref<RetrievalStep[]>([])
const expandedSteps = ref(false)

// 问卷访谈状态
const interviewActive = ref(false)
const interviewLoading = ref(false)
const interviewSessionId = ref<string | null>(null)

// IME 组合输入状态（防止中文输入法 Enter 确认时重复触发 sendMessage）
const isComposing = ref(false)
// 防抖时间戳（防止同一毫秒内重复触发）
let lastSendTime = 0

// 删除当前活跃会话（移入回收站）
const deleteActiveSession = async () => {
  if (!activeSessionId.value) return
  try {
    await conversationService.deleteConversation(activeSessionId.value)
    // 从侧边栏移除
    sessions.value = sessions.value.filter(s => s.id !== activeSessionId.value)
    // 切换到第一个剩余会话
    if (sessions.value.length > 0) {
      await switchSession(sessions.value[0].id)
    } else {
      activeSessionId.value = null
      messages.value = []
    }
  } catch (error) {
    console.error('删除对话失败:', error)
  }
}

// 根据严重程度文本映射 CSS 等级
const getSeverityLevel = (severity: string): string => {
  const s = severity.toLowerCase()
  if (s.includes('无') || s.includes('正常') || s.includes('none')) return 'none'
  if (s.includes('轻度') || s.includes('轻微') || s.includes('mild')) return 'mild'
  if (s.includes('中度') || s.includes('moderate')) return 'moderate'
  if (s.includes('重度') || s.includes('严重') || s.includes('severe')) return 'severe'
  if (s.includes('极') || s.includes('危') || s.includes('critical')) return 'critical'
  return 'moderate'
}

// 根据步骤状态和类型确定显示颜色
const getStepColor = (step: RetrievalStep): string => {
  if (step.status === 'BLOCKED' || step.status === 'ERROR') return 'error'
  if (step.stepName.includes('安全兜底')) return 'warning'
  if (step.stepName.includes('缓存')) return 'secondary'
  return 'primary'
}

// 格式化耗时显示
const formatDuration = (ms: number): string => {
  if (ms === 0) return '<1ms'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  return `${(ms / 60000).toFixed(1)}m`
}

// 从步骤 intermediateData 提取结构化结果项
const getStepResultItems = (step: RetrievalStep): { label: string; value: string }[] => {
  const items: { label: string; value: string }[] = []
  // 优先展示输入输出
  if (step.inputCount > 0) items.push({ label: '输入', value: String(step.inputCount) })
  if (step.outputCount > 0) items.push({ label: '输出', value: String(step.outputCount) })
  // 展示中间数据中的关键结果
  if (step.intermediateData) {
    const displayKeys = ['结果', '命中片段', '示例来源', '通过', '丢弃', '通过率',
                         '最高分', '最高置信度', '已改写', '候选数', '上下文片段',
                         '答案长度', '引用数', '置信度', 'intentType']
    for (const key of displayKeys) {
      const val = step.intermediateData[key]
      if (val !== undefined && val !== null && String(val).length > 0) {
        items.push({ label: key, value: String(val) })
      }
    }
  }
  return items.length > 0 ? items : [{ label: '状态', value: '执行中...' }]
}

// 状态中文映射
const getStatusLabel = (status: string): string => {
  switch (status) {
    case 'BLOCKED': return '已阻断'
    case 'ERROR': return '异常'
    default: return '成功'
  }
}

// 加载会话列表
const loadConversations = async () => {
  try {
    loadingSessions.value = true
    const conversations = await conversationService.getConversations()
    sessions.value = conversations.map(conv => ({
      id: conv.id.toString(),
      title: conv.title,
      icon: getSessionIcon(conv.title),
      conversation: conv
    }))

    // 如果没有活跃会话，选择第一个
    if (sessions.value.length > 0 && !activeSessionId.value) {
      activeSessionId.value = sessions.value[0].id
      await loadMessages(activeSessionId.value)
    }
  } catch (error) {
    console.error('加载会话列表失败:', error)
    sessions.value = []
  } finally {
    loadingSessions.value = false
  }
}

// 从 sourceReferences JSON 构建参考文献 HTML
const buildReferenceSection = (sourceReferencesJson: string): string => {
  try {
    const refs = JSON.parse(sourceReferencesJson)
    if (!Array.isArray(refs) || refs.length === 0) return ''
    return `
<div class="mt-6 pt-4 border-t border-outline-variant">
<div class="text-[10px] text-on-surface-variant font-bold uppercase mb-3 tracking-widest flex items-center gap-2"><span class="material-symbols-outlined text-xs">auto_stories</span>数据源与参考文献</div>
<div class="flex flex-wrap gap-2">
${refs.map((ref: any) => `<span class="px-2 py-1 bg-surface-container text-[11px] text-on-surface-variant rounded flex items-center gap-1 cursor-pointer hover:bg-secondary-fixed transition-colors" title="${ref.snippet || ''}">${ref.label}</span>`).join('\n')}
</div>
</div>`
  } catch {
    return ''
  }
}

// 根据会话标题获取图标
const getSessionIcon = (title: string): string => {
  if (title.includes('查房') || title.includes('建议')) return 'clinical_notes'
  if (title.includes('手术') || title.includes('风险')) return 'medical_services'
  if (title.includes('药物')) return 'pill'
  if (title.includes('病历')) return 'history_edu'
  return 'clinical_notes'
}

// 加载会话消息
const loadMessages = async (sessionId: string) => {
  try {
    loadingMessages.value = true
    // sessionId就是conversationId
    const messagesList = await conversationService.getMessages(sessionId)
    const historyMessages: ChatMessage[] = messagesList.map(msg => {
      let content = msg.content
      if (msg.role === 'assistant' && msg.sourceReferences) {
        content += buildReferenceSection(msg.sourceReferences)
      }
      return {
        id: msg.id.toString(),
        role: msg.role.toLowerCase() as 'user' | 'assistant',
        content,
        sourceReferences: msg.sourceReferences
      }
    })

    // 加载该对话下的历史访谈消息，重建问卷卡片
    try {
      const interviewMsgs = await interviewService.getInterviewsByConversation(sessionId)
      for (const histItem of interviewMsgs) {
        const details = await interviewService.getSessionMessages(histItem.sessionId)
        for (const im of details) {
          const qMsg = buildQuestionnaireMessage(im)
          if (qMsg) historyMessages.push(qMsg)
        }
      }
      // 按ID排序（兼容带前缀的访谈消息ID，如 um-xxx / im-xxx）
      historyMessages.sort((a, b) => {
        const aNum = Number(String(a.id).replace(/^[^0-9]+/, ''))
        const bNum = Number(String(b.id).replace(/^[^0-9]+/, ''))
        return aNum - bNum
      })
    } catch (e) {
      console.warn('加载访谈历史消息失败:', e)
    }

    messages.value = historyMessages
  } catch (error) {
    console.error('加载消息失败:', error)
    messages.value = []
  } finally {
    loadingMessages.value = false
  }
}

// 将 InterviewMessageItem 重建为问卷卡片 ChatMessage
const buildQuestionnaireMessage = (im: InterviewMessageItem): ChatMessage | null => {
  const d = im.messageData
  let questionnaire: QuestionnaireBlock

  switch (im.messageType) {
    case 'question':
      questionnaire = {
        type: 'question',
        linkId: d.linkId,
        questionText: d.text,
        currentIndex: d.currentIndex,
        totalQuestions: d.totalQuestions,
        options: d.options,
        sessionId: im.sessionId
      }
      break
    case 'completion':
      questionnaire = {
        type: 'completion',
        totalScore: d.totalScore,
        maxScore: d.maxScore,
        severity: d.severity,
        interpretation: d.interpretation,
        analysisSummary: d.analysisSummary,
        analysisId: d.analysisId,
        completionMessage: d.message
      }
      break
    case 'safety_alert':
      questionnaire = {
        type: 'safety_alert',
        reason: d.reason,
        alertMessage: d.message
      }
      break
    case 'clarify':
      questionnaire = {
        type: 'question',
        linkId: d.linkId,
        questionText: d.text,
        options: d.options,
        sessionId: im.sessionId
      }
      break
    case 'user_message':
      return {
        id: 'um-' + im.id,
        role: 'user',
        content: typeof d.text === 'string' ? d.text : (d.display || ''),
      }
    default:
      return null
  }

  return {
    id: 'im-' + im.id,
    role: 'assistant',
    content: '',
    questionnaire
  }
}

// 切换会话
const switchSession = async (sessionId: string) => {
  activeSessionId.value = sessionId
  retrievalSteps.value = []
  expandedSteps.value = false
  await loadMessages(sessionId)
}

// 创建新会话
const createNewSession = async () => {
  try {
    const newConversation = await conversationService.createConversation({
      title: `新咨询 ${new Date().toLocaleDateString()}`,
      type: 'general'
    })

    sessions.value.push({
      id: newConversation.id.toString(),
      title: newConversation.title,
      icon: 'clinical_notes',
      conversation: newConversation
    })

    activeSessionId.value = newConversation.id.toString()
    messages.value = []
    inputText.value = ''
  } catch (error) {
    console.error('创建新会话失败:', error)
    // 本地创建模拟会话
    const newId = (sessions.value.length + 1).toString()
    sessions.value.push({
      id: newId,
      title: `新咨询 ${newId}`,
      icon: 'clinical_notes'
    })
    activeSessionId.value = newId
    messages.value = []
    inputText.value = ''
  }
}

// 发送消息（流式）
const sendMessage = async () => {
  // 中文输入法组合输入期间跳过
  if (isComposing.value) return
  // 300ms 内重复触发则跳过（防止 Enter 键同时触发 keyup + click 等场景）
  const now = Date.now()
  if (now - lastSendTime < 300) return
  lastSendTime = now

  const content = inputText.value.trim()
  if (!content || !activeSessionId.value) return

  inputText.value = ''

  // 如果访谈活跃中，路由到访谈答案流程
  if (interviewActive.value && interviewSessionId.value) {
    await submitInterviewAnswer(content, content)
    return
  }

  // 添加用户消息到UI
  const userMessage: ChatMessage = {
    id: Date.now().toString(),
    role: 'user',
    content: content
  }
  messages.value.push(userMessage)
  // 清空上一轮的检索路径，等待本次新路径
  retrievalSteps.value = []
  expandedSteps.value = false

  // 创建AI消息占位
  const aiMsgId = (Date.now() + 1).toString()
  const aiMessage: ChatMessage = {
    id: aiMsgId,
    role: 'assistant',
    content: ''
  }
  messages.value.push(aiMessage)
  let streamingContent = ''
  let pendingSources: any[] = []
  let pendingSuggestion: any = null

  try {
    // 保存用户消息到后端
    conversationService.createMessage({
      conversationId: activeSessionId.value,
      content: content,
      role: 'user'
    }).catch(err => console.error('保存用户消息失败:', err))

    // 首条消息时自动更新对话标题
    const isFirstMessage = messages.value.length === 2 // 用户消息 + AI占位
    if (isFirstMessage) {
      const newTitle = content.length > 30 ? content.substring(0, 30) + '...' : content
      conversationService.updateConversationTitle(activeSessionId.value, newTitle)
        .then(() => {
          const session = sessions.value.find(s => s.id === activeSessionId.value)
          if (session) session.title = newTitle
        })
        .catch(() => {})
    }

    // 调用流式问答
    qaService.streamAsk(
      {
        question: content,
        conversationId: activeSessionId.value,
        userId: authService.getToken() ? 1 : undefined
      },
      // onChunk
      (chunk) => {
        if (chunk.type === 'text' && chunk.content) {
          streamingContent += chunk.content
          const msg = messages.value.find(m => m.id === aiMsgId)
          if (msg) msg.content = streamingContent
        } else if (chunk.type === 'sources' && chunk.sources) {
          pendingSources = chunk.sources
        } else if (chunk.type === 'retrievalPath' && chunk.retrievalPath) {
          retrievalSteps.value = chunk.retrievalPath.steps
          expandedSteps.value = false
        } else if (chunk.type === 'done') {
          console.log('[DATA_COLLECTION] SSE done事件收到:', {
            intentType: chunk.intentType,
            hasQuestionnaireSuggestion: !!chunk.questionnaireSuggestion,
            suggestionMatched: chunk.questionnaireSuggestion?.matched,
            questionnaireId: chunk.questionnaireSuggestion?.questionnaireId,
            questionnaireTitle: chunk.questionnaireSuggestion?.questionnaireTitle,
            answerPreview: chunk.answer?.substring(0, 80)
          })
          if (chunk.questionnaireSuggestion?.matched) {
            pendingSuggestion = chunk.questionnaireSuggestion
            console.log('[DATA_COLLECTION] pendingSuggestion已设置, 等待onComplete触发')
          } else if (chunk.intentType === 'DATA_COLLECTION') {
            console.warn('[DATA_COLLECTION] 意图为DATA_COLLECTION但questionnaireSuggestion未匹配!', chunk)
          }
        }
      },
      // onError
      (error) => {
        const msg = messages.value.find(m => m.id === aiMsgId)
        if (msg && !streamingContent) {
          msg.content = `抱歉，处理您的请求时出现错误：${error.message}。请稍后重试。`
        }
      },
      // onComplete
      async () => {
        console.log('[DATA_COLLECTION] onComplete触发:', {
          hasPendingSuggestion: !!pendingSuggestion,
          interviewActive: interviewActive.value,
          streamingContentLength: streamingContent.length
        })
        // 检测问卷建议，自动开始填表流程
        if (pendingSuggestion) {
          const suggestion = pendingSuggestion
          pendingSuggestion = null
          console.log('[DATA_COLLECTION] 调用startInterviewFlow:', {
            questionnaireId: suggestion.questionnaireId,
            questionnaireTitle: suggestion.questionnaireTitle
          })
          try {
            await startInterviewFlow(
              suggestion.questionnaireId,
              suggestion.questionnaireTitle
            )
            console.log('[DATA_COLLECTION] startInterviewFlow完成')
          } catch (err) {
            console.error('[DATA_COLLECTION] startInterviewFlow异常:', err)
          }
          return
        }
        // 附加参考文献
        if (pendingSources.length > 0) {
          const sourceRefs = pendingSources.map((s: any, i: number) => ({
            id: s.documentId || s.id,
            chunkId: s.chunkId,
            label: `[${i + 1}] ${s.title}`,
            snippet: s.snippet || s.content,
            score: s.relevanceScore || s.similarity
          }))
          const refSection = buildReferenceSection(JSON.stringify(sourceRefs))
          const msg = messages.value.find(m => m.id === aiMsgId)
          if (msg) msg.content = streamingContent + refSection
          // 异步保存AI消息
          conversationService.createMessage({
            conversationId: activeSessionId.value!,
            content: streamingContent,
            role: 'assistant',
            sourceReferences: JSON.stringify(sourceRefs)
          }).catch(err => console.error('保存AI消息失败:', err))
        } else {
          conversationService.createMessage({
            conversationId: activeSessionId.value!,
            content: streamingContent,
            role: 'assistant'
          }).catch(err => console.error('保存AI消息失败:', err))
        }
      }
    )
  } catch (error) {
    console.error('发送消息失败:', error)
    const msg = messages.value.find(m => m.id === aiMsgId)
    if (msg && !streamingContent) {
      msg.content = `抱歉，处理您的请求时出现错误：${(error as Error).message}。请稍后重试。`
    }
  }
}

// 开始问卷访谈流程
const startInterviewFlow = async (questionnaireId: string, _questionnaireTitle: string) => {
  if (interviewActive.value) {
    console.warn('[DATA_COLLECTION] startInterviewFlow: 访谈已活跃,跳过')
    return
  }
  console.log('[DATA_COLLECTION] startInterviewFlow 开始:', {
    questionnaireId,
    title: _questionnaireTitle,
    userId: authService.getToken() ? 1 : undefined,
    conversationId: activeSessionId.value || undefined
  })
  interviewActive.value = true
  interviewLoading.value = true

  try {
    console.log('[DATA_COLLECTION] 调用 interviewService.startLlmInterview...')
    await interviewService.startLlmInterview(
      {
        questionnaireId,
        userId: authService.getToken() ? 1 : undefined,
        conversationId: activeSessionId.value || undefined
      },
      // onEvent
      (event) => {
        console.log('[DATA_COLLECTION] 访谈SSE事件:', event.type, event)
        interviewLoading.value = false
        switch (event.type) {
          case 'question':
            interviewSessionId.value = event.sessionId || interviewSessionId.value
            messages.value.push({
              id: Date.now().toString(),
              role: 'assistant',
              content: '',
              questionnaire: {
                type: 'question',
                linkId: event.linkId,
                questionText: event.text,
                currentIndex: event.currentIndex,
                totalQuestions: event.totalQuestions,
                options: event.options,
                sessionId: interviewSessionId.value || undefined
              }
            })
            nextTick(() => scrollToBottom())
            break
          case 'completion':
            messages.value.push({
              id: Date.now().toString(),
              role: 'assistant',
              content: '',
              questionnaire: {
                type: 'completion',
                totalScore: event.totalScore,
                maxScore: event.maxScore,
                severity: event.severity,
                interpretation: event.interpretation,
                analysisSummary: event.analysisSummary,
                analysisId: (event as any).analysisId,
                completionMessage: event.message
              }
            })
            nextTick(() => scrollToBottom())
            break
          case 'safety_alert':
            messages.value.push({
              id: Date.now().toString(),
              role: 'assistant',
              content: '',
              questionnaire: {
                type: 'safety_alert',
                reason: event.reason,
                alertMessage: event.message
              }
            })
            nextTick(() => scrollToBottom())
            break
          case 'clarify':
            messages.value.push({
              id: Date.now().toString(),
              role: 'assistant',
              content: event.text,
              questionnaire: {
                type: 'question',
                linkId: event.linkId,
                questionText: event.text,
                options: event.options,
                sessionId: interviewSessionId.value || undefined
              }
            })
            nextTick(() => scrollToBottom())
            break
          case 'degradation':
            console.warn('问卷采集降级:', (event as any).level, (event as any).reason)
            ElMessage.warning((event as any).reason || '问卷采集已降级')
            break
          case 'done':
            // SSE流结束但访谈仍活跃，不重置状态
            break
          case 'error':
            console.error('问卷访谈错误:', (event as any).message)
            interviewActive.value = false
            interviewSessionId.value = null
            break
        }
      },
      // onError
      (error) => {
        console.error('[DATA_COLLECTION] 启动访谈失败(onError):', error.message, error)
        interviewLoading.value = false
        interviewActive.value = false
      },
      // onComplete
      () => {
        console.log('[DATA_COLLECTION] 访谈SSE流结束(onComplete)')
        interviewLoading.value = false
      }
    )
    console.log('[DATA_COLLECTION] startLlmInterview 返回')
  } catch (err) {
    console.error('[DATA_COLLECTION] 启动访谈异常(catch):', err)
    interviewLoading.value = false
    interviewActive.value = false
  }
}

// 提交问卷答案
const submitInterviewAnswer = async (selectedCode: string, displayText: string) => {
  if (!interviewSessionId.value || interviewLoading.value) return
  interviewLoading.value = true

  // 立即显示用户消息
  messages.value.push({
    id: Date.now().toString(),
    role: 'user',
    content: displayText
  })
  nextTick(() => scrollToBottom())

  try {
    await interviewService.submitLlmAnswer(
      {
        sessionId: interviewSessionId.value,
        userInput: displayText,
        selectedCode
      },
      // onEvent
      (event) => {
        interviewLoading.value = false
        switch (event.type) {
          case 'question':
            interviewSessionId.value = event.sessionId || interviewSessionId.value
            messages.value.push({
              id: Date.now().toString(),
              role: 'assistant',
              content: '',
              questionnaire: {
                type: 'question',
                linkId: event.linkId,
                questionText: event.text,
                currentIndex: event.currentIndex,
                totalQuestions: event.totalQuestions,
                options: event.options,
                sessionId: interviewSessionId.value || undefined
              }
            })
            nextTick(() => scrollToBottom())
            break
          case 'completion':
            interviewActive.value = false
            interviewSessionId.value = null
            messages.value.push({
              id: Date.now().toString(),
              role: 'assistant',
              content: '',
              questionnaire: {
                type: 'completion',
                totalScore: event.totalScore,
                maxScore: event.maxScore,
                severity: event.severity,
                interpretation: event.interpretation,
                analysisSummary: event.analysisSummary,
                analysisId: (event as any).analysisId,
                completionMessage: event.message
              }
            })
            nextTick(() => scrollToBottom())
            break
          case 'safety_alert':
            messages.value.push({
              id: Date.now().toString(),
              role: 'assistant',
              content: '',
              questionnaire: {
                type: 'safety_alert',
                reason: event.reason,
                alertMessage: event.message
              }
            })
            nextTick(() => scrollToBottom())
            break
          case 'clarify':
            messages.value.push({
              id: Date.now().toString(),
              role: 'assistant',
              content: event.text,
              questionnaire: {
                type: 'question',
                linkId: event.linkId,
                questionText: event.text,
                options: event.options,
                sessionId: interviewSessionId.value || undefined
              }
            })
            nextTick(() => scrollToBottom())
            break
          case 'degradation':
            console.warn('问卷采集降级:', (event as any).level, (event as any).reason)
            ElMessage.warning((event as any).reason || '问卷采集已降级')
            break
          case 'done':
            // SSE流结束但访谈仍活跃，不重置状态
            break
          case 'error':
            console.error('提交答案错误:', (event as any).message)
            break
        }
      },
      // onError
      (error) => {
        console.error('提交答案失败:', error)
        interviewLoading.value = false
      },
      // onComplete
      () => {
        interviewLoading.value = false
      }
    )
  } catch (err) {
    console.error('提交答案异常:', err)
    interviewLoading.value = false
  }
}

// 详细报告弹窗状态
const showReportDialog = ref(false)
const reportLoading = ref(false)
const currentReport = ref<AnalysisReport | null>(null)

// 查看详细评估报告
const showDetailedReport = async (message: ChatMessage) => {
  const analysisId = message.questionnaire?.analysisId
  if (!analysisId) return

  reportLoading.value = true
  showReportDialog.value = true

  try {
    // 从 analysisId 提取 sessionId (格式: analysis-{sessionId})
    const sessionId = analysisId.startsWith('analysis-')
      ? analysisId.substring(9)
      : analysisId
    const report = await interviewService.getAnalysisReport(sessionId)
    currentReport.value = report
  } catch (e) {
    console.error('加载分析报告失败:', e)
    currentReport.value = null
  } finally {
    reportLoading.value = false
  }
}

// 取消访谈
const cancelInterview = () => {
  interviewActive.value = false
  interviewSessionId.value = null
  interviewLoading.value = false
  qaService.stopStreaming()
}

// 滚动到底部
const scrollToBottom = () => {
  const el = document.querySelector('.qa-chat-scroll')
  if (el) el.scrollTop = el.scrollHeight
}

// 组件挂载时加载会话列表
onMounted(() => {
  loadConversations()
})
</script>

<style scoped>
/* ===== 平滑滚动 ===== */
.qa-scroll-smooth {
  scroll-behavior: smooth;
}

/* ===== Material Symbols 图标 ===== */
.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
  transition: font-variation-settings 150ms cubic-bezier(0.4, 0, 0.2, 1);
}

.material-symbols-outlined[style*="font-variation-settings: 'FILL' 1"] {
  font-variation-settings: 'FILL' 1, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}

/* ===== 滚动区域 ===== */
.qa-chat-scroll {
  scroll-behavior: smooth;
}

/* ===== 弹跳动画 ===== */
.active-scale-95:active {
  transform: scale(0.95);
}

/* ===== 过渡效果 ===== */
.transition-all {
  transition-property: all;
  transition-timing-function: cubic-bezier(0.4, 0, 0.2, 1);
  transition-duration: 150ms;
}

.transition-colors {
  transition-property: color, background-color, border-color, text-decoration-color, fill, stroke;
  transition-timing-function: cubic-bezier(0.4, 0, 0.2, 1);
  transition-duration: 150ms;
}

/* ===== 玻璃效果 ===== */
.glass-effect {
  backdrop-filter: blur(20px);
  background: linear-gradient(
    135deg,
    rgba(255, 255, 255, 0.7) 0%,
    rgba(255, 255, 255, 0.4) 100%
  );
  border: 1px solid rgba(255, 255, 255, 0.2);
}

/* ===== 可点击元素 ===== */
.qa-clickable {
  cursor: pointer;
}

/* ===== 思考中动画 ===== */
.qa-thinking {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 4px 0;
}

.qa-thinking-text {
  font-size: 14px;
  color: #727783;
}

.qa-thinking-dots {
  display: inline-flex;
}

.qa-thinking-dot {
  font-size: 18px;
  font-weight: 700;
  color: #00478d;
  animation: qa-dot-bounce 1.4s infinite both;
}

.qa-thinking-dot:nth-child(2) {
  animation-delay: 0.2s;
}

.qa-thinking-dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes qa-dot-bounce {
  0%, 80%, 100% {
    opacity: 0;
    transform: translateY(0);
  }
  40% {
    opacity: 1;
    transform: translateY(-4px);
  }
}

/* ===== 问卷卡片通用样式 ===== */
.qa-questionnaire-card {
  margin-top: 12px;
  border-radius: 12px;
  padding: 16px;
  border: 1px solid;
  animation: qa-card-slide-in 0.3s ease-out;
}

@keyframes qa-card-slide-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ===== 建议卡片 ===== */
.qa-suggestion-card {
  background: linear-gradient(135deg, #e8f0fe 0%, #f0f4ff 100%);
  border-color: #c4d7f2;
}

.qa-suggestion-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.qa-suggestion-icon {
  font-size: 28px;
  color: #00478d;
  margin-top: 2px;
}

.qa-suggestion-title {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 4px;
}

.qa-suggestion-subtitle {
  font-size: 13px;
  color: #00478d;
  font-weight: 500;
}

.qa-suggestion-confidence {
  font-size: 11px;
  color: #727783;
  margin-top: 2px;
}

.qa-suggestion-actions {
  display: flex;
  gap: 8px;
  margin-top: 14px;
}

.qa-suggestion-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  border: none;
  cursor: pointer;
  transition: all 0.2s;
}

.qa-suggestion-btn-primary {
  background: #00478d;
  color: #fff;
}

.qa-suggestion-btn-primary:hover:not(:disabled) {
  background: #003a70;
}

.qa-suggestion-btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.qa-suggestion-btn-secondary {
  background: #fff;
  color: #727783;
  border: 1px solid #dadce0;
}

.qa-suggestion-btn-secondary:hover {
  background: #f1f3f4;
}

/* ===== 问题卡片 ===== */
.qa-question-card {
  background: #fff;
  border-color: #e0e0e0;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.qa-question-progress-bar {
  height: 4px;
  background: #e8eaed;
  border-radius: 2px;
  overflow: hidden;
  margin-bottom: 8px;
}

.qa-question-progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #00478d, #1976d2);
  border-radius: 2px;
  transition: width 0.4s ease;
}

.qa-question-progress-text {
  font-size: 11px;
  color: #727783;
  margin-bottom: 12px;
}

.qa-question-text {
  font-size: 15px;
  font-weight: 500;
  color: #1a1a1a;
  line-height: 1.5;
  margin-bottom: 10px;
}

.qa-question-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #727783;
  margin-bottom: 14px;
  padding: 6px 10px;
  background: #f0f4ff;
  border-radius: 6px;
  border-left: 3px solid #00478d;
}

.qa-hint-icon {
  font-size: 14px;
  color: #00478d;
  font-variation-settings: 'FILL' 0;
}

.qa-question-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.qa-option-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 14px;
  background: #f8f9fa;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  text-align: left;
}

.qa-option-btn:hover:not(:disabled) {
  background: #e8f0fe;
  border-color: #00478d;
}

.qa-option-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.qa-option-code {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  background: #00478d;
  color: #fff;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
}

.qa-option-display {
  font-size: 13px;
  color: #333;
}

/* ===== 完成卡片 ===== */
.qa-completion-card {
  background: linear-gradient(135deg, #e8f5e9 0%, #f1f8e9 100%);
  border-color: #a5d6a7;
}

.qa-completion-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.qa-completion-icon {
  font-size: 24px;
  color: #2e7d32;
}

.qa-completion-title {
  font-size: 15px;
  font-weight: 600;
  color: #1b5e20;
}

.qa-completion-score {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.qa-completion-score-label {
  font-size: 13px;
  color: #555;
}

.qa-severity-badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
}

.qa-severity-none {
  background: #e8f5e9;
  color: #2e7d32;
}

.qa-severity-mild {
  background: #fff3e0;
  color: #e65100;
}

.qa-severity-moderate {
  background: #fff8e1;
  color: #f57f17;
}

.qa-severity-severe {
  background: #fbe9e7;
  color: #bf360c;
}

.qa-severity-critical {
  background: #ffebee;
  color: #b71c1c;
  border: 1px solid #ef5350;
}

.qa-completion-score-num {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin-bottom: 8px;
}

.qa-completion-interpretation {
  font-size: 13px;
  color: #555;
  line-height: 1.6;
  margin-bottom: 8px;
  padding: 10px;
  background: rgba(255, 255, 255, 0.7);
  border-radius: 8px;
}

.qa-analysis-summary {
  margin-bottom: 8px;
  padding: 12px;
  background: rgba(0, 71, 141, 0.04);
  border-left: 3px solid #00478d;
  border-radius: 0 8px 8px 0;
}

.qa-analysis-summary-title {
  font-size: 12px;
  font-weight: 600;
  color: #00478d;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 6px;
}

.qa-analysis-summary-content {
  font-size: 13px;
  color: #444;
  line-height: 1.7;
}

.qa-completion-message {
  font-size: 12px;
  color: #727783;
  font-style: italic;
}

/* ===== 安全警报卡片 ===== */
.qa-safety-alert-card {
  background: linear-gradient(135deg, #fff3e0 0%, #fbe9e7 100%);
  border-color: #ef9a9a;
}

.qa-safety-alert-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.qa-safety-alert-icon {
  font-size: 24px;
  color: #d32f2f;
}

.qa-safety-alert-title {
  font-size: 15px;
  font-weight: 600;
  color: #c62828;
}

.qa-safety-alert-reason {
  font-size: 14px;
  font-weight: 500;
  color: #b71c1c;
  margin-bottom: 6px;
}

.qa-safety-alert-message {
  font-size: 13px;
  color: #555;
  line-height: 1.5;
}

/* ===== 查看详细报告按钮 ===== */
.qa-report-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  padding: 8px 16px;
  background: #00478d;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s;
}

.qa-report-btn:hover {
  background: #003666;
}

/* ===== 报告弹窗 ===== */
.qa-report-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
}

.qa-report-dialog {
  width: 90%;
  max-width: 680px;
  max-height: 85vh;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.qa-report-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid #e0e0e0;
  flex-shrink: 0;
}

.qa-report-dialog-title {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0;
}

.qa-report-dialog-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  background: #f5f5f5;
  border-radius: 50%;
  cursor: pointer;
  color: #666;
  transition: background 0.2s;
}

.qa-report-dialog-close:hover {
  background: #e0e0e0;
}

.qa-report-loading {
  padding: 40px;
  text-align: center;
  color: #727783;
}

.qa-report-content {
  padding: 24px;
  overflow-y: auto;
  flex: 1;
}

.qa-report-section {
  margin-bottom: 20px;
}

.qa-report-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #00478d;
  margin: 0 0 8px 0;
  padding-bottom: 6px;
  border-bottom: 1px solid #e8f0fe;
}

.qa-report-text {
  font-size: 13px;
  color: #444;
  line-height: 1.6;
  margin: 0 0 6px 0;
}

.qa-report-risk {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.qa-report-urgent {
  display: inline-block;
  padding: 4px 10px;
  background: #ffebee;
  color: #c62828;
  font-size: 12px;
  font-weight: 600;
  border-radius: 4px;
  width: fit-content;
}

.qa-report-list {
  margin: 6px 0;
  padding-left: 18px;
  font-size: 13px;
  color: #555;
  line-height: 1.6;
}

.qa-report-conclusion {
  font-size: 13px;
  color: #333;
  font-weight: 500;
  margin-top: 8px;
  padding: 10px;
  background: #f8f9fa;
  border-radius: 6px;
  line-height: 1.6;
}

.qa-report-rec-group {
  margin-bottom: 12px;
}

.qa-report-rec-label {
  font-size: 12px;
  font-weight: 600;
  color: #727783;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin: 0 0 6px 0;
}

.qa-report-rec-item {
  padding: 8px 12px;
  background: #f8f9fa;
  border-radius: 8px;
  margin-bottom: 6px;
  font-size: 13px;
  line-height: 1.5;
}

.qa-report-rec-item strong {
  color: #1a1a1a;
  display: block;
  margin-bottom: 2px;
}

.qa-report-rec-item p {
  margin: 0;
  color: #555;
}

.qa-report-rec-resource {
  display: inline-block;
  margin-top: 4px;
  padding: 2px 8px;
  background: #e8f0fe;
  color: #00478d;
  font-size: 11px;
  border-radius: 4px;
}

.qa-report-disclaimer {
  margin-top: 16px;
  padding: 12px;
  background: #fff8e1;
  border: 1px solid #ffe082;
  border-radius: 8px;
  font-size: 11px;
  color: #8d6e00;
  line-height: 1.5;
  text-align: center;
}
</style>
