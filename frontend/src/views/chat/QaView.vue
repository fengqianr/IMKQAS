<template>
  <div class="qa-view">
    <!-- 顶部导航栏 -->
    <header class="qa-header">
      <div class="custom-flex custom-items-center custom-gap-8">
        <div class="qa-header-logo">Clinical Precision RAG</div>
        <nav v-if="navItems.length" class="custom-hidden custom-md-flex custom-items-center custom-gap-6">
          <router-link
            v-for="item in navItems"
            :key="item.path"
            :to="item.path"
            :class="['qa-nav-link', isActive(route.path, item) ? 'qa-nav-link-active' : 'qa-nav-link-inactive']"
          >
            {{ item.title }}
          </router-link>
        </nav>
      </div>
      <div class="custom-flex custom-items-center custom-gap-4">
        <!-- 会话管理统一在左侧栏，顶栏仅保留通知/设置/用户菜单 -->
        <button class="qa-icon-btn material-symbols-outlined">notifications</button>
        <button class="qa-icon-btn material-symbols-outlined">settings</button>
        <!-- 用户头像下拉：游客仅「登录」，已登录「个人中心」「退出登录」 -->
        <el-dropdown trigger="click" @command="handleUserCommand">
          <div v-if="isGuest" class="qa-header-avatar qa-header-avatar-guest">
            <span class="material-symbols-outlined qa-avatar-icon">person</span>
          </div>
          <div v-else class="qa-header-avatar-initial">
            {{ initialOf(authStore.user?.username, '用') }}
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-if="isGuest" command="login">
                <span class="material-symbols-outlined qa-dropdown-icon">login</span>
                登录
              </el-dropdown-item>
              <template v-else>
                <el-dropdown-item v-if="isPatientSide" command="profile">
                  <span class="material-symbols-outlined qa-dropdown-icon">person</span>
                  个人中心
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <span class="material-symbols-outlined qa-dropdown-icon">logout</span>
                  退出登录
                </el-dropdown-item>
              </template>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <!-- 主内容区域（侧边栏 + 聊天区域 + 检索面板） -->
    <div class="qa-main-content">
      <!-- 侧边栏（四端统一会话栏，仅配色随主题变化；医生端保留 Active Sessions 样式） -->
      <aside :class="['qa-sidebar', isDoctorSide ? 'qa-sidebar-active-sessions' : '']">
        <div class="custom-p-6 custom-pt-4">
          <div class="qa-sidebar-header">
            <div class="qa-sidebar-icon">
              <span class="material-symbols-outlined" style="font-variation-settings: 'FILL' 1">clinical_notes</span>
            </div>
            <div>
              <div class="qa-sidebar-title">会话管理</div>
              <div class="qa-sidebar-subtitle">最近咨询列表</div>
            </div>
          </div>
          <button class="qa-new-btn" @click="createNewSession">
            <span class="material-symbols-outlined text-sm">add</span>
            新建咨询
          </button>
        </div>

        <nav class="custom-flex-1 qa-no-scrollbar custom-overflow-y-auto space-y-1">
          <div
            v-for="session in sessions"
            :key="session.id"
            :class="['qa-session-item', session.id === activeSessionId ? 'qa-session-active' : 'qa-session-inactive']"
            @click="switchSession(session.id)"
          >
            <span class="material-symbols-outlined text-lg">{{ session.icon }}</span>
            <div class="qa-session-body">
              <span class="qa-session-title">{{ session.title }}</span>
              <span class="qa-session-date">{{ formatSessionDate(session.conversation?.updatedAt) }}</span>
            </div>
            <span class="qa-session-count">{{ session.conversation?.messageCount ?? 0 }}</span>
          </div>
        </nav>

        <div class="qa-sidebar-footer">
          <div class="qa-sidebar-footer-item qa-clickable" @click="deleteActiveSession">
            <span class="material-symbols-outlined text-lg">delete</span>
            回收站
          </div>
          <div
            v-if="authStore.userRole === 'ADMIN'"
            class="qa-sidebar-footer-item qa-clickable"
            @click="showReviewPanel = true"
          >
            <span class="material-symbols-outlined text-lg">rate_review</span>
            词条审核
          </div>
          <div class="qa-sidebar-footer-item">
            <span class="material-symbols-outlined text-lg">help</span>
            帮助中心
          </div>
        </div>
      </aside>

      <!-- 聊天界面（浮动卡片：圆角悬浮于背景之上） -->
      <section class="qa-chat-section">
        <!-- 浮动卡片顶部：当前会话标题条 -->
        <div class="qa-chat-header">
          <h2 class="qa-chat-header-title">{{ activeSessionTitle }}</h2>
          <p class="qa-chat-header-subtitle">AI 助手已就绪，输入问题开始咨询</p>
        </div>
        <!-- 聊天滑动区域 -->
        <div class="qa-chat-scroll qa-no-scrollbar">
          <!-- 医疗安全提示 -->
          <div class="custom-mx-6 custom-mt-4">
            <div class="qa-safety-tip">
              <span class="qa-safety-tip-icon material-symbols-outlined">warning</span>
              <p class="qa-safety-tip-text">
                <span class="qa-safety-tip-bold">医疗安全提示：</span
                >本系统提供的信息仅供临床参考，不作为最终诊断及用药依据。请结合患者实际体征及相关检查结果，由执业医师进行最终决策。
              </p>
            </div>
          </div>
          <!-- 聊天消息列表 -->
          <div class="qa-messages-container">
            <div class="qa-messages-list">
              <div
                v-for="message in messages"
                :key="message.id"
                :class="['custom-flex', message.role === 'user' ? 'custom-justify-end' : 'custom-justify-start']"
              >
                <!-- 用户消息 -->
                <div v-if="message.role === 'user'" class="qa-message-user">
                  <p class="qa-message-user-text">{{ message.content }}</p>
                </div>
                <!-- AI回复 -->
                <div v-else class="qa-message-ai-wrapper">
                  <div class="qa-ai-identity">
                    <div class="qa-ai-avatar">
                      <span class="material-symbols-outlined text-sm" style="font-variation-settings: 'FILL' 1"
                        >smart_toy</span
                      >
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
                    <div
                      v-else
                      class="qa-ai-content text-sm text-on-surface leading-relaxed"
                      v-html="message.content"
                    />
                  </div>
                  <!-- 问卷建议卡片 -->
                  <div
                    v-if="message.questionnaire?.type === 'suggestion'"
                    class="qa-questionnaire-card qa-suggestion-card"
                  >
                    <div class="qa-suggestion-header">
                      <span class="material-symbols-outlined qa-suggestion-icon">assignment</span>
                      <div>
                        <div class="qa-suggestion-title">
                          {{ message.questionnaire.suggestionText || '检测到您可能需要填写量表' }}
                        </div>
                        <div v-if="message.questionnaire.questionnaireTitle" class="qa-suggestion-subtitle">
                          建议填写：{{ message.questionnaire.questionnaireTitle }}
                        </div>
                        <div v-if="message.questionnaire.confidence" class="qa-suggestion-confidence">
                          匹配置信度：{{ (message.questionnaire.confidence * 100).toFixed(0) }}%
                        </div>
                      </div>
                    </div>
                    <div class="qa-suggestion-actions">
                      <button
                        :disabled="interviewActive"
                        class="qa-suggestion-btn qa-suggestion-btn-primary"
                        @click="
                          startInterviewFlow(
                            message.questionnaire.questionnaireId!,
                            message.questionnaire.questionnaireTitle!
                          )
                        "
                      >
                        <span class="material-symbols-outlined text-sm">play_arrow</span>
                        {{ interviewActive ? '填表中...' : '开始填表' }}
                      </button>
                      <button class="qa-suggestion-btn qa-suggestion-btn-secondary" @click="cancelInterview">
                        忽略
                      </button>
                    </div>
                  </div>
                  <!-- 问卷问题卡片 -->
                  <div v-if="message.questionnaire?.type === 'question'" class="qa-questionnaire-card qa-question-card">
                    <div class="qa-question-progress-bar">
                      <div
                        class="qa-question-progress-fill"
                        :style="{
                          width:
                            ((message.questionnaire.currentIndex! + 1) / message.questionnaire.totalQuestions!) * 100 +
                            '%'
                        }"
                      />
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
                      <button
                        v-for="opt in message.questionnaire.options"
                        :key="opt.code"
                        :disabled="interviewLoading"
                        class="qa-option-btn"
                        @click="submitInterviewAnswer(opt.code, opt.display)"
                      >
                        <span class="qa-option-code">{{ opt.code }}</span>
                        <span class="qa-option-display">{{ opt.display }}</span>
                      </button>
                    </div>
                  </div>
                  <!-- 问卷完成卡片 -->
                  <div
                    v-if="message.questionnaire?.type === 'completion'"
                    class="qa-questionnaire-card qa-completion-card"
                  >
                    <div class="qa-completion-header">
                      <span class="material-symbols-outlined qa-completion-icon">check_circle</span>
                      <span class="qa-completion-title">问卷填写完成</span>
                    </div>
                    <div v-if="message.questionnaire.severity" class="qa-completion-score">
                      <span class="qa-completion-score-label">评估结果：</span>
                      <span
                        :class="[
                          'qa-severity-badge',
                          'qa-severity-' + getSeverityLevel(message.questionnaire.severity)
                        ]"
                      >
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
                    <button
                      v-if="message.questionnaire.analysisId"
                      class="qa-report-btn"
                      @click="showDetailedReport(message)"
                    >
                      <span class="material-symbols-outlined text-sm">description</span>
                      查看详细评估报告
                    </button>
                    <div v-if="message.questionnaire.completionMessage" class="qa-completion-message">
                      {{ message.questionnaire.completionMessage }}
                    </div>
                  </div>
                  <!-- 安全警报卡片 -->
                  <div
                    v-if="message.questionnaire?.type === 'safety_alert'"
                    class="qa-questionnaire-card qa-safety-alert-card"
                  >
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
                  <!-- 纯表单卡片（manual_form 降级时展示所有剩余题目） -->
                  <div
                    v-if="message.questionnaire?.type === 'manual_form'"
                    class="qa-questionnaire-card qa-manual-form-card"
                  >
                    <div class="qa-manual-form-banner">
                      <span class="material-symbols-outlined qa-manual-form-banner-icon">info</span>
                      <span>LLM不可用，请点击问卷答案</span>
                    </div>
                    <div class="qa-manual-form-title">{{ message.questionnaire.questionnaireTitle || '问卷采集' }}</div>
                    <div class="qa-question-progress-bar">
                      <div class="qa-question-progress-fill" :style="{ width: manualFormFillPercent + '%' }" />
                    </div>
                    <div class="qa-question-progress-text">
                      已选择 {{ manualFormSelectedCount }} / {{ message.questionnaire.manualFormItems?.length || 0 }} 题
                    </div>
                    <div
                      v-for="item in message.questionnaire.manualFormItems"
                      :key="item.linkId"
                      class="qa-manual-form-item"
                    >
                      <div class="qa-manual-form-question-text">
                        <span class="qa-manual-form-item-index">{{ item.index + 1 }}.</span>
                        {{ item.text }}
                      </div>
                      <div class="qa-question-options">
                        <button
                          v-for="opt in item.options"
                          :key="opt.code"
                          :class="[
                            'qa-option-btn',
                            manualFormAnswers[message.id]?.[item.linkId] === opt.code ? 'qa-option-btn-selected' : ''
                          ]"
                          @click="selectManualFormOption(message.id, item.linkId, opt.code)"
                        >
                          <span class="qa-option-code">{{ opt.code }}</span>
                          <span class="qa-option-display">{{ opt.display }}</span>
                          <span
                            v-if="manualFormAnswers[message.id]?.[item.linkId] === opt.code"
                            class="qa-option-check material-symbols-outlined"
                            >check</span
                          >
                        </button>
                      </div>
                    </div>
                    <div class="qa-manual-form-actions">
                      <button
                        :disabled="!isManualFormComplete(message)"
                        class="qa-suggestion-btn qa-suggestion-btn-primary"
                        @click="submitAllManualFormAnswers(message)"
                      >
                        <span class="material-symbols-outlined text-sm">send</span>
                        提交所有答案
                      </button>
                      <span v-if="!isManualFormComplete(message)" class="qa-manual-form-hint">
                        请完成所有题目的选择
                      </span>
                    </div>
                    <div v-if="manualFormSubmitting[message.id]" class="qa-manual-form-submitting">
                      <span class="qa-thinking-text">正在提交答案...</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <!-- 输入区域 -->
        <div class="qa-input-area">
          <!-- 快捷引导提问（按角色分组） -->
          <div class="qa-quick-prompts">
            <button v-for="prompt in quickPrompts" :key="prompt" class="qa-quick-chip" @click="useQuickPrompt(prompt)">
              {{ prompt }}
            </button>
          </div>
          <div class="qa-input-wrapper">
            <textarea
              v-model="inputText"
              class="qa-input-field"
              placeholder="输入临床问题或上传病例附件..."
              rows="1"
              @keyup.enter="sendMessage"
              @compositionstart="isComposing = true"
              @compositionend="isComposing = false"
            />
            <div class="qa-input-actions">
              <button class="qa-input-btn material-symbols-outlined">attach_file</button>
              <button type="button" class="qa-send-btn" @click="sendMessage">
                <span class="material-symbols-outlined text-sm">send</span>
              </button>
            </div>
          </div>
          <div class="qa-input-hint">
            <span class="qa-input-hint-text">支持 PDF 病历解析 · 实时检索临床指南</span>
          </div>
        </div>
      </section>

      <!-- 检索路径面板（右侧，仅管理员显示；医生/患者/游客统一两栏布局） -->
      <aside v-if="isAdminSide" class="qa-panel">
        <div class="qa-panel-section">
          <h3 class="qa-panel-title">
            <span class="qa-panel-title-icon material-symbols-outlined">schema</span>
            知识检索路径
          </h3>
          <div>
            <div
              v-for="(step, index) in retrievalSteps"
              v-show="expandedSteps || index < 3"
              :key="index"
              class="qa-retrieval-step"
            >
              <div :class="['qa-step-dot', `qa-step-dot-${getStepColor(step)}`]" />
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
            <button v-if="retrievalSteps.length > 3" class="qa-expand-toggle" @click="expandedSteps = !expandedSteps">
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
          <button class="qa-report-dialog-close" @click="showReportDialog = false">
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
              <span
                :class="['qa-severity-badge', 'qa-severity-' + getSeverityLevel(currentReport.riskAssessment.level)]"
              >
                {{ currentReport.riskAssessment.level }}
              </span>
              <p class="qa-report-text">{{ currentReport.riskAssessment.description }}</p>
              <p v-if="currentReport.riskAssessment.requiresUrgentAttention" class="qa-report-urgent">需要紧急关注</p>
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

  <!-- 词条审核面板（管理员可见） -->
  <TermReviewPanel v-model="showReviewPanel" />
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ROLE_MENU_MAP, ROLE_TO_LAYOUT, isActive, type MenuItem } from '@/config/menus'
import { qaService } from '@/api/services/qa.service'
import { conversationService } from '@/api/services/conversation.service'
import { interviewService } from '@/api/services/interview.service'
import { useAuthStore } from '@/stores/auth.store'
import { useAuthActions } from '@/composables/useAuthActions'
import TermReviewPanel from '@/components/TermReviewPanel.vue'
import {
  listGuestSessions,
  getGuestSession,
  createGuestSession,
  saveGuestSession,
  removeGuestSession,
  hasGuestSessions,
  GUEST_SESSION_LIMIT
} from '@/utils/guest-session'
import { syncGuestSessionsToAccount } from '@/utils/guest-sync'
import { apiErrorMessage } from '@/utils/error'
import { initialOf } from '@/utils/format'
import type { Conversation, RetrievalStep } from '@/api/types/qa'
import type { AnswerOption, InterviewMessageItem, AnalysisReport } from '@/api/types/interview'

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
  type: 'suggestion' | 'question' | 'completion' | 'safety_alert' | 'manual_form'
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
  manualFormItems?: ManualFormItem[]
}

/** 纯表单一题的数据 */
interface ManualFormItem {
  index: number
  linkId: string
  text: string
  options: AnswerOption[]
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

// 问卷访谈状态（按 conversationId 隔离，支持断点续填）
const interviewStates = ref<Map<string, { active: boolean; loading: boolean; sessionId: string | null }>>(new Map())

// 当前访谈的问卷ID（供 degradation 处理时使用）
const currentQuestionnaireId = ref<string | null>(null)

// 纯表单模式状态
const manualFormMode = ref(false)
const manualFormAnswers = ref<Record<string, Record<string, string>>>({})
const manualFormSubmitting = ref<Record<string, boolean>>({})

function getCurrentInterview() {
  if (!activeSessionId.value) return { active: false, loading: false, sessionId: null as string | null }
  return interviewStates.value.get(activeSessionId.value) ?? { active: false, loading: false, sessionId: null }
}

function setInterviewState(partial: Partial<{ active: boolean; loading: boolean; sessionId: string | null }>) {
  if (!activeSessionId.value) return
  const current = getCurrentInterview()
  interviewStates.value.set(activeSessionId.value, { ...current, ...partial })
}

const interviewActive = computed(() => getCurrentInterview().active)
const interviewLoading = computed(() => getCurrentInterview().loading)
const interviewSessionId = computed(() => getCurrentInterview().sessionId)

// 心跳保活（60s 定时更新 lastActiveAt，防止会话过期）
let heartbeatTimer: ReturnType<typeof setInterval> | null = null

function startHeartbeat() {
  stopHeartbeat()
  heartbeatTimer = setInterval(() => {
    const sid = interviewSessionId.value
    if (sid) interviewService.heartbeat(sid)
  }, 60_000)
}

function stopHeartbeat() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}

// 纯表单卡片计算属性
const manualFormSelectedCount = computed(() => {
  const formMsg = messages.value.find((m) => m.questionnaire?.type === 'manual_form')
  if (!formMsg) return 0
  const answers = manualFormAnswers.value[formMsg.id]
  return answers ? Object.keys(answers).length : 0
})

const manualFormFillPercent = computed(() => {
  const formMsg = messages.value.find((m) => m.questionnaire?.type === 'manual_form')
  if (!formMsg || !formMsg.questionnaire?.manualFormItems?.length) return 0
  return (manualFormSelectedCount.value / formMsg.questionnaire.manualFormItems.length) * 100
})

// 页面关闭/刷新前通知后端暂停访谈（保留MySQL数据，支持断点续填）
function handleBeforeUnload() {
  const sid = interviewSessionId.value
  if (sid) {
    interviewService.pauseInterview(sid)
  }
}

// 词条审核面板
const showReviewPanel = ref(false)
const authStore = useAuthStore()

// 顶栏导航项：按当前登录角色的菜单渲染（与侧边栏一致，随路由高亮）
const route = useRoute()
const router = useRouter()
const navItems = computed<MenuItem[]>(() => ROLE_MENU_MAP[authStore.userRole] || [])

// 游客模式判断：未登录时问答会话保存在 localStorage，不落后端公共池
const isGuest = computed(() => !authStore.isAuthenticated)
// 个人中心是患者侧功能：仅患者侧角色（PATIENT/STUDENT/NURSE/HEALTH_MANAGER）显示入口，医生/管理员不显示
const isPatientSide = computed(() => ROLE_TO_LAYOUT[authStore.userRole] === 'patient')
// 医生侧：左侧会话栏保留 Active Sessions 增强样式（四端统一会话栏布局）
const isDoctorSide = computed(() => ROLE_TO_LAYOUT[authStore.userRole] === 'doctor')
// 管理端：问答页保留右侧「知识检索路径」面板（医生/患者/游客统一两栏布局）
const isAdminSide = computed(() => ROLE_TO_LAYOUT[authStore.userRole] === 'admin')

// 浮动卡片顶部标题：取当前活跃会话标题，无会话时显示「新会话」
const activeSessionTitle = computed(() => {
  const s = sessions.value.find((item) => item.id === activeSessionId.value)
  return s?.title || '新会话'
})

// 快捷引导提问：按角色分组，点击即填入输入框并发送
const quickPrompts = computed<string[]>(() => {
  if (isGuest.value) return ['这个系统能做什么', '如何开始一次咨询', '如何注册登录账号']
  const layout = ROLE_TO_LAYOUT[authStore.userRole]
  if (layout === 'doctor') return ['评估患者当前风险等级', '近期用药需要调整吗', '如何解读这条检查结果']
  if (layout === 'patient') return ['我的健康档案包含什么', '如何完成健康问卷', '评估报告在哪里查看']
  return ['知识库有多少篇文档', '词条审核流程是什么', '查看系统使用统计']
})

// 点击快捷提问：复用输入框发送流程（无活跃会话时自动创建首个会话）
const useQuickPrompt = (prompt: string) => {
  inputText.value = prompt
  sendMessage()
}

/** 会话/评估时间格式化（MM-DD HH:mm，缺省返回空串） */
const formatSessionDate = (dateStr?: string): string => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  if (isNaN(d.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

// 头像下拉命令：游客跳登录，已登录跳个人中心/退出登录
const handleUserCommand = async (command: string) => {
  if (command === 'login') {
    router.push({ path: '/login', query: { redirect: '/qa' } })
  } else if (command === 'profile') {
    router.push('/user')
  } else if (command === 'logout') {
    await logoutWithConfirm()
  }
}

// 退出登录：统一走 useAuthActions（确认 → logout → 提示 → 回登录页）
const { logoutWithConfirm } = useAuthActions()

// IME 组合输入状态（防止中文输入法 Enter 确认时重复触发 sendMessage）
const isComposing = ref(false)
// 防抖时间戳（防止同一毫秒内重复触发）
let lastSendTime = 0

// 删除当前活跃会话（游客模式本地删除；登录模式移入回收站）
const deleteActiveSession = async () => {
  if (!activeSessionId.value) return
  try {
    if (isGuest.value) {
      removeGuestSession(activeSessionId.value)
    } else {
      await conversationService.deleteConversation(activeSessionId.value)
    }
    // 从侧边栏移除
    sessions.value = sessions.value.filter((s) => s.id !== activeSessionId.value)
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
    const displayKeys = [
      '结果',
      '命中片段',
      '示例来源',
      '通过',
      '丢弃',
      '通过率',
      '最高分',
      '最高置信度',
      '已改写',
      '候选数',
      '上下文片段',
      '答案长度',
      '引用数',
      '置信度',
      'intentType'
    ]
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
    case 'BLOCKED':
      return '已阻断'
    case 'ERROR':
      return '异常'
    default:
      return '成功'
  }
}

// 加载会话列表（游客模式读本地；登录模式按用户拉取，避免不同用户会话混在一起）
const loadConversations = async () => {
  try {
    loadingSessions.value = true
    let conversations: Conversation[]
    if (isGuest.value) {
      conversations = listGuestSessions().map((s) => ({
        id: s.localId,
        userId: 0,
        title: s.title,
        type: 'general' as const,
        createdAt: s.createdAt,
        updatedAt: s.createdAt,
        messageCount: s.messages.length
      }))
    } else {
      conversations = await conversationService.getConversations(authStore.userId)
    }
    sessions.value = conversations.map((conv) => ({
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
    nextTick(() => scrollToBottom())
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

// 加载会话消息（游客模式读本地；登录模式读后端 + 重建访谈卡片）
const loadMessages = async (sessionId: string) => {
  try {
    loadingMessages.value = true
    // 游客模式：从本地会话读取消息（无访谈数据，跳过访谈历史加载）
    if (isGuest.value) {
      const localSession = getGuestSession(sessionId)
      if (!localSession) {
        messages.value = []
        return
      }
      messages.value = localSession.messages.map((msg) => {
        let content = msg.content
        if (msg.role === 'assistant' && msg.sourceReferences) {
          content += buildReferenceSection(msg.sourceReferences)
        }
        return {
          id: msg.id,
          role: msg.role,
          content,
          sourceReferences: msg.sourceReferences
        }
      })
      return
    }
    // sessionId就是conversationId
    const messagesList = await conversationService.getMessages(sessionId)
    const historyMessages: ChatMessage[] = messagesList.map((msg) => {
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

    // 加载该对话下的历史访谈消息，重建问卷卡片 + 自动恢复未完成访谈
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
      // 消息 ID 为雪花长整型，超出 JS Number 安全整数范围，需用 BigInt 精确比较避免排序错乱
      historyMessages.sort((a, b) => {
        const aNum = BigInt(String(a.id).replace(/^[^0-9]+/, '') || '0')
        const bNum = BigInt(String(b.id).replace(/^[^0-9]+/, '') || '0')
        return aNum < bNum ? -1 : aNum > bNum ? 1 : 0
      })

      // 检测可恢复的访谈：只恢复 PAUSED 状态（用户关闭页面），忽略 ABANDONED/EXPIRED
      const pendingInterview = interviewMsgs
        .filter((h) => !h.completed && h.status === 'PAUSED')
        .sort((a, b) => new Date(b.lastActiveAt).getTime() - new Date(a.lastActiveAt).getTime())[0]
      if (pendingInterview) {
        const resumed = await interviewService.resumeInterview(pendingInterview.sessionId)
        if (resumed && !resumed.completed) {
          setInterviewState({ active: true, loading: false, sessionId: pendingInterview.sessionId })
        }
      }
    } catch (e) {
      console.warn('加载访谈历史消息失败:', e)
    }

    messages.value = historyMessages
  } catch (error) {
    console.error('加载消息失败:', error)
    messages.value = []
  } finally {
    loadingMessages.value = false
    nextTick(() => scrollToBottom())
  }
}

// 将 InterviewMessageItem 重建为问卷卡片 ChatMessage
const buildQuestionnaireMessage = (im: InterviewMessageItem): ChatMessage | null => {
  const d = im.messageData

  switch (im.messageType) {
    case 'question': {
      // LLM模式下问题渲染为文本消息，与实时SSE的question事件保持一致
      const qIdx = (d.currentIndex ?? 0) + 1
      const qTotal = d.totalQuestions ?? 0
      return {
        id: 'im-' + im.id,
        role: 'assistant',
        content: `**问题 ${qIdx}/${qTotal}**\n\n${d.text}\n\n*请用自然语言描述您的情况，或输入选项编号*`
      }
    }
    case 'clarify':
      // 追问也渲染为普通文本，与实时SSE的clarify事件一致
      return {
        id: 'im-' + im.id,
        role: 'assistant',
        content: d.text || ''
      }
    case 'user_message':
      return {
        id: 'um-' + im.id,
        role: 'user',
        content: typeof d.text === 'string' ? d.text : d.display || ''
      }
    case 'completion':
      return {
        id: 'im-' + im.id,
        role: 'assistant',
        content: '',
        questionnaire: {
          type: 'completion',
          totalScore: d.totalScore,
          maxScore: d.maxScore,
          severity: d.severity,
          interpretation: d.interpretation,
          analysisSummary: d.analysisSummary,
          analysisId: d.analysisId,
          completionMessage: d.message
        }
      }
    case 'safety_alert':
      return {
        id: 'im-' + im.id,
        role: 'assistant',
        content: '',
        questionnaire: {
          type: 'safety_alert',
          reason: d.reason,
          alertMessage: d.message
        }
      }
    default:
      return null
  }
}

// 切换会话
const switchSession = async (sessionId: string) => {
  activeSessionId.value = sessionId
  retrievalSteps.value = []
  expandedSteps.value = false
  await loadMessages(sessionId)
}

// 创建新会话（游客模式本地创建；登录模式归属当前用户）
const createNewSession = async () => {
  // 游客模式：在本地创建会话
  if (isGuest.value) {
    const title = `新咨询 ${new Date().toLocaleDateString()}`
    const created = createGuestSession(title)
    if (!created) {
      ElMessage.warning(`本地会话已达上限（${GUEST_SESSION_LIMIT} 个），请删除旧会话或登录账号`)
      return
    }
    sessions.value.push({
      id: created.localId,
      title: created.title,
      icon: 'clinical_notes'
    })
    activeSessionId.value = created.localId
    messages.value = []
    inputText.value = ''
    return
  }
  try {
    const newConversation = await conversationService.createConversation({
      title: `新咨询 ${new Date().toLocaleDateString()}`,
      type: 'general',
      userId: authStore.userId || undefined
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

// 保存 AI 消息：游客模式写入本地会话，登录模式写入后端
const persistAssistantMessage = (content: string, sourceReferences?: string) => {
  if (isGuest.value) {
    const localSession = getGuestSession(activeSessionId.value!)
    if (localSession) {
      localSession.messages.push({ id: Date.now().toString(), role: 'assistant', content, sourceReferences })
      saveGuestSession(localSession)
    }
  } else {
    conversationService
      .createMessage({
        conversationId: activeSessionId.value!,
        content,
        role: 'assistant',
        sourceReferences
      })
      .catch((err) => console.error('保存AI消息失败:', err))
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
  if (!content) return

  // 首次会话：无活跃会话时输入问题即自动创建（游客本地创建；登录模式创建后端会话）
  if (!activeSessionId.value) {
    await createNewSession()
    if (!activeSessionId.value) return
  }

  inputText.value = ''

  // 纯表单模式下阻止通过输入框发送消息
  if (manualFormMode.value) {
    ElMessage.warning('请先完成问卷填写后再发送消息')
    return
  }

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
  // 用户消息与AI占位已入列表：立即滚动到最新位置，让用户看到自己的问题与AI思考状态
  nextTick(() => scrollToBottom())
  let streamingContent = ''
  let pendingSources: any[] = []
  let pendingSuggestion: any = null

  try {
    // 保存用户消息（游客模式存本地，登录模式写入后端）
    if (isGuest.value) {
      const localSession = getGuestSession(activeSessionId.value)
      if (localSession) {
        localSession.messages.push({ id: userMessage.id, role: 'user', content })
        saveGuestSession(localSession)
      }
    } else {
      conversationService
        .createMessage({
          conversationId: activeSessionId.value,
          content: content,
          role: 'user'
        })
        .catch((err) => console.error('保存用户消息失败:', err))
    }

    // 首条消息时自动更新对话标题（游客模式本地更新）
    const isFirstMessage = messages.value.length === 2 // 用户消息 + AI占位
    if (isFirstMessage) {
      const newTitle = content.length > 30 ? content.substring(0, 30) + '...' : content
      if (isGuest.value) {
        const localSession = getGuestSession(activeSessionId.value)
        if (localSession) {
          localSession.title = newTitle
          saveGuestSession(localSession)
        }
        const session = sessions.value.find((s) => s.id === activeSessionId.value)
        if (session) session.title = newTitle
      } else {
        conversationService
          .updateConversationTitle(activeSessionId.value, newTitle)
          .then(() => {
            const session = sessions.value.find((s) => s.id === activeSessionId.value)
            if (session) session.title = newTitle
          })
          .catch(() => {})
      }
    }

    // 调用流式问答（游客模式匿名调用，不带会话归属；登录模式携带会话与用户）
    qaService.streamAsk(
      isGuest.value
        ? { question: content }
        : {
            question: content,
            conversationId: activeSessionId.value,
            userId: authStore.userId || undefined
          },
      // onChunk
      (chunk) => {
        if (chunk.type === 'text' && chunk.content) {
          streamingContent += chunk.content
          const msg = messages.value.find((m) => m.id === aiMsgId)
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
        const msg = messages.value.find((m) => m.id === aiMsgId)
        if (msg && !streamingContent) {
          msg.content = `抱歉，处理您的请求时出现错误：${apiErrorMessage(error)}。请稍后重试。`
        }
      },
      // onComplete
      async () => {
        console.log('[DATA_COLLECTION] onComplete触发:', {
          hasPendingSuggestion: !!pendingSuggestion,
          interviewActive: interviewActive.value,
          streamingContentLength: streamingContent.length
        })
        // 检测问卷建议，渲染建议卡片让用户确认后再开始
        if (pendingSuggestion) {
          const suggestion = pendingSuggestion
          pendingSuggestion = null
          console.log('[DATA_COLLECTION] 渲染问卷建议卡片:', {
            questionnaireId: suggestion.questionnaireId,
            questionnaireTitle: suggestion.questionnaireTitle
          })
          messages.value.push({
            id: Date.now().toString(),
            role: 'assistant',
            content: '',
            questionnaire: {
              type: 'suggestion',
              suggestionText: suggestion.suggestionText || '根据您的描述，系统为您匹配了合适的评估量表',
              questionnaireId: suggestion.questionnaireId,
              questionnaireTitle: suggestion.questionnaireTitle,
              confidence: suggestion.confidence
            }
          })
          nextTick(() => scrollToBottom())
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
          const msg = messages.value.find((m) => m.id === aiMsgId)
          if (msg) msg.content = streamingContent + refSection
          persistAssistantMessage(streamingContent, JSON.stringify(sourceRefs))
        } else {
          persistAssistantMessage(streamingContent)
        }
      }
    )
  } catch (error) {
    console.error('发送消息失败:', error)
    const msg = messages.value.find((m) => m.id === aiMsgId)
    if (msg && !streamingContent) {
      msg.content = `抱歉，处理您的请求时出现错误：${apiErrorMessage(error)}。请稍后重试。`
    }
  }
}

// 开始问卷访谈流程
const startInterviewFlow = async (questionnaireId: string, _questionnaireTitle: string) => {
  // 游客需登录后才能填写问卷（涉及健康数据归属，需同步到账号）
  if (isGuest.value) {
    ElMessage.info('填写问卷需登录账号，请先登录')
    router.push({ path: '/login', query: { redirect: '/qa' } })
    return
  }
  if (interviewActive.value) {
    console.warn('[DATA_COLLECTION] startInterviewFlow: 访谈已活跃,跳过')
    ElMessage.warning('当前有正在进行的问卷访谈，请先完成或取消')
    return
  }
  if (!activeSessionId.value) {
    console.error('[DATA_COLLECTION] startInterviewFlow: 无活跃会话')
    ElMessage.error('请先选择或创建一个对话')
    return
  }
  console.log('[DATA_COLLECTION] startInterviewFlow 开始:', {
    questionnaireId,
    title: _questionnaireTitle,
    userId: authStore.userId || undefined,
    conversationId: activeSessionId.value || undefined
  })
  setInterviewState({ active: true, loading: true })
  currentQuestionnaireId.value = questionnaireId
  manualFormMode.value = false
  startHeartbeat()

  try {
    console.log('[DATA_COLLECTION] 调用 interviewService.startLlmInterview...')
    await interviewService.startLlmInterview(
      {
        questionnaireId,
        userId: authStore.userId || undefined,
        conversationId: activeSessionId.value || undefined
      },
      // onEvent
      (event) => {
        console.log('[DATA_COLLECTION] 访谈SSE事件:', event.type, event)
        setInterviewState({ loading: false })
        switch (event.type) {
          case 'question':
            setInterviewState({ sessionId: event.sessionId || interviewSessionId.value })
            messages.value.push({
              id: Date.now().toString(),
              role: 'assistant',
              content: `**问题 ${(event.currentIndex ?? 0) + 1}/${event.totalQuestions}**\n\n${event.text}\n\n*请用自然语言描述您的情况，或输入选项编号*`
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
              content: event.text
            })
            nextTick(() => scrollToBottom())
            break
          case 'degradation': {
            console.warn('问卷采集降级:', (event as any).level, (event as any).reason, 'scope:', (event as any).scope)
            const dScope = (event as any).scope
            if ((event as any).level === 'manual_form' && dScope === 'all') {
              // LLM彻底不可用，切换全量纯表单
              ElMessage.warning('LLM不可用，请点击问卷答案')
              const sid = interviewSessionId.value
              const qid = currentQuestionnaireId.value
              if (sid && qid) initManualForm(sid, qid)
            } else {
              // 单题降级，仅显示警告，不截断后续 SSE 流
              ElMessage.warning((event as any).reason || '问卷采集已降级')
            }
            break
          }
          case 'done':
            break
          case 'error':
            console.error('问卷访谈错误:', (event as any).message)
            setInterviewState({ active: false, sessionId: null })
            stopHeartbeat()
            break
        }
        // 全量纯表单模式下跳过后续逐题 SSE 事件
        if (manualFormMode.value && (event.type === 'clarify' || event.type === 'question')) {
          return
        }
      },
      // onError
      (error) => {
        console.error('[DATA_COLLECTION] 启动访谈失败(onError):', error.message, error)
        ElMessage.error('启动问卷访谈失败：' + apiErrorMessage(error))
        setInterviewState({ loading: false, active: false })
        stopHeartbeat()
      },
      // onComplete
      () => {
        console.log('[DATA_COLLECTION] 访谈SSE流结束(onComplete)')
        setInterviewState({ loading: false })
      }
    )
    console.log('[DATA_COLLECTION] startLlmInterview 返回')
  } catch (err) {
    console.error('[DATA_COLLECTION] 启动访谈异常(catch):', err)
    setInterviewState({ loading: false, active: false })
    stopHeartbeat()
  }
}

// 提交问卷答案
const submitInterviewAnswer = async (selectedCode: string, displayText: string) => {
  if (!interviewSessionId.value || interviewLoading.value) return
  setInterviewState({ loading: true })

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
        selectedCode,
        conversationId: activeSessionId.value || undefined
      },
      // onEvent
      (event) => {
        setInterviewState({ loading: false })
        switch (event.type) {
          case 'question':
            setInterviewState({ sessionId: event.sessionId || interviewSessionId.value })
            messages.value.push({
              id: Date.now().toString(),
              role: 'assistant',
              content: `**问题 ${(event.currentIndex ?? 0) + 1}/${event.totalQuestions}**\n\n${event.text}\n\n*请用自然语言描述您的情况，或输入选项编号*`
            })
            nextTick(() => scrollToBottom())
            break
          case 'completion':
            setInterviewState({ active: false, sessionId: null })
            stopHeartbeat()
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
              content: event.text
            })
            nextTick(() => scrollToBottom())
            break
          case 'degradation': {
            console.warn('问卷采集降级:', (event as any).level, (event as any).reason, 'scope:', (event as any).scope)
            const dScope = (event as any).scope
            if ((event as any).level === 'manual_form' && dScope === 'all') {
              // LLM彻底不可用，切换全量纯表单
              ElMessage.warning('LLM不可用，请点击问卷答案')
              const sid = interviewSessionId.value
              const qid = currentQuestionnaireId.value
              if (sid && qid) initManualForm(sid, qid)
            } else {
              // 单题降级，仅显示警告，不截断后续 SSE 流
              ElMessage.warning((event as any).reason || '问卷采集已降级')
            }
            break
          }
          case 'done':
            break
          case 'error':
            console.error('提交答案错误:', (event as any).message)
            break
        }
        // 全量纯表单模式下跳过后续逐题 SSE 事件
        if (manualFormMode.value && (event.type === 'clarify' || event.type === 'question')) {
          return
        }
      },
      // onError
      (error) => {
        console.error('提交答案失败:', error)
        ElMessage.error('提交答案失败：' + apiErrorMessage(error))
        setInterviewState({ loading: false })
      },
      // onComplete
      () => {
        setInterviewState({ loading: false })
      }
    )
  } catch (err) {
    console.error('提交答案异常:', err)
    setInterviewState({ loading: false })
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
    const sessionId = analysisId.startsWith('analysis-') ? analysisId.substring(9) : analysisId
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
const cancelInterview = async () => {
  const sid = interviewSessionId.value
  if (sid) {
    interviewService.abandonInterview(sid).catch(() => {})
  }
  if (activeSessionId.value) {
    interviewStates.value.delete(activeSessionId.value)
  }
  stopHeartbeat()
  qaService.stopStreaming()
}

// ==================== 纯表单模式方法 ====================

/** 用户在纯表单卡片中点击选中一个选项 */
const selectManualFormOption = (messageId: string, linkId: string, code: string) => {
  if (!manualFormAnswers.value[messageId]) {
    manualFormAnswers.value[messageId] = {}
  }
  manualFormAnswers.value[messageId][linkId] = code
  // 触发响应式更新
  manualFormAnswers.value = { ...manualFormAnswers.value }
}

/** 检查纯表单是否所有题目都已选择 */
const isManualFormComplete = (message: ChatMessage): boolean => {
  const items = message.questionnaire?.manualFormItems
  if (!items || items.length === 0) return false
  const answers = manualFormAnswers.value[message.id]
  if (!answers) return false
  return items.every((item) => !!answers[item.linkId])
}

/** 提交纯表单所有答案 */
const submitAllManualFormAnswers = async (message: ChatMessage) => {
  const sessionId = message.questionnaire?.sessionId
  const answers = manualFormAnswers.value[message.id]
  if (!sessionId || !answers) return

  if (!isManualFormComplete(message)) {
    ElMessage.warning('请完成所有题目的选择后再提交')
    return
  }

  manualFormSubmitting.value = { ...manualFormSubmitting.value, [message.id]: true }

  try {
    const response = await interviewService.batchSubmit(sessionId, answers)

    // 替换 manual_form 卡片为 completion 卡片
    const msgIndex = messages.value.findIndex((m) => m.id === message.id)
    if (msgIndex !== -1) {
      messages.value[msgIndex] = {
        ...message,
        questionnaire: {
          type: 'completion',
          totalScore: response.totalScore,
          maxScore: response.maxScore,
          severity: response.severity,
          interpretation: response.interpretation,
          analysisSummary: response.analysisSummary,
          analysisId: response.analysisId,
          completionMessage: response.message
        }
      }
    }

    // 重置访谈状态
    setInterviewState({ active: false, loading: false, sessionId: null })
    stopHeartbeat()
    manualFormMode.value = false
    currentQuestionnaireId.value = null

    delete manualFormAnswers.value[message.id]

    ElMessage.success('问卷提交成功')
    nextTick(() => scrollToBottom())
  } catch (error: any) {
    console.error('批量提交失败:', error)
    ElMessage.error(apiErrorMessage(error, '提交失败，请重试'))
  } finally {
    manualFormSubmitting.value = { ...manualFormSubmitting.value, [message.id]: false }
  }
}

/** 初始化纯表单模式 —— 获取完整模板，找出剩余题目，插入表单卡片 */
const initManualForm = async (sessionId: string, questionnaireId: string) => {
  if (manualFormMode.value) return // 防止重复初始化
  manualFormMode.value = true

  try {
    const template = await interviewService.getQuestionnaire(questionnaireId)
    if (!template || !template.items) {
      ElMessage.error('无法获取问卷信息')
      manualFormMode.value = false
      return
    }

    // 从会话消息中提取已答题的 linkId
    const sessionMessages = await interviewService.getSessionMessages(sessionId)
    const answeredLinkIds = new Set<string>()
    for (const msg of sessionMessages) {
      if (msg.messageType === 'user_message' && msg.messageData?.linkId) {
        answeredLinkIds.add(msg.messageData.linkId)
      }
    }

    // 筛出剩余未答题
    const remainingItems: ManualFormItem[] = template.items
      .filter((item) => !answeredLinkIds.has(item.linkId))
      .map((item) => ({
        index: item.index,
        linkId: item.linkId,
        text: item.text,
        options: item.options
      }))

    if (remainingItems.length === 0) return

    const formMessage: ChatMessage = {
      id: 'manual-form-' + Date.now().toString(),
      role: 'assistant',
      content: '',
      questionnaire: {
        type: 'manual_form',
        manualFormItems: remainingItems,
        questionnaireTitle: template.title,
        sessionId: sessionId,
        questionnaireId: questionnaireId
      }
    }
    messages.value.push(formMessage)
    manualFormAnswers.value[formMessage.id] = {}

    nextTick(() => scrollToBottom())
  } catch (error) {
    console.error('初始化纯表单失败:', error)
    ElMessage.error('初始化纯表单界面失败')
    manualFormMode.value = false
  }
}

// 滚动到底部（瞬时定位：CSS 的 scroll-behavior:smooth 会在流式高频滚动时不断打断动画导致停在半路，故这里显式 auto）
const scrollToBottom = () => {
  const el = document.querySelector('.qa-chat-scroll')
  if (el) el.scrollTo({ top: el.scrollHeight, behavior: 'auto' })
}

// 组件挂载时加载会话列表 + 注册断连通知
onMounted(() => {
  loadConversations()
  window.addEventListener('beforeunload', handleBeforeUnload)
  // 登录后静默同步本地游客会话到账号（自动触发，无需用户操作；失败保留本地可下次重试）
  if (!isGuest.value && hasGuestSessions()) {
    syncGuestSessionsToAccount().then((result) => {
      if (result.total === 0) return
      if (result.failed === 0) {
        ElMessage.success(`已同步 ${result.succeeded} 个游客会话到您的账号`)
        loadConversations()
      } else {
        ElMessage.warning(
          result.succeeded > 0
            ? `已同步 ${result.succeeded} 个游客会话，${result.failed} 个失败（本地已保留，稍后重试）`
            : '游客会话同步失败，请检查网络后重试'
        )
        if (result.succeeded > 0) loadConversations()
      }
    })
  }
})

onUnmounted(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
  stopHeartbeat()
})
</script>

<style scoped>
/* ===== Material Symbols 图标 ===== */
.material-symbols-outlined {
  font-variation-settings:
    'FILL' 0,
    'wght' 400,
    'GRAD' 0,
    'opsz' 24;
  transition: font-variation-settings 150ms cubic-bezier(0.4, 0, 0.2, 1);
}

.material-symbols-outlined[style*="font-variation-settings: 'FILL' 1"] {
  font-variation-settings:
    'FILL' 1,
    'wght' 400,
    'GRAD' 0,
    'opsz' 24;
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
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.7) 0%, rgba(255, 255, 255, 0.4) 100%);
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
  color: var(--theme-outline);
}

.qa-thinking-dots {
  display: inline-flex;
}

.qa-thinking-dot {
  font-size: 18px;
  font-weight: 700;
  color: var(--theme-primary);
  animation: qa-dot-bounce 1.4s infinite both;
}

.qa-thinking-dot:nth-child(2) {
  animation-delay: 0.2s;
}

.qa-thinking-dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes qa-dot-bounce {
  0%,
  80%,
  100% {
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
  background: linear-gradient(135deg, var(--theme-primary-soft) 0%, var(--theme-surface-container-lowest) 100%);
  border-color: var(--theme-primary-fixed);
}

.qa-suggestion-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.qa-suggestion-icon {
  font-size: 28px;
  color: var(--theme-primary);
  margin-top: 2px;
}

.qa-suggestion-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--theme-on-surface);
  margin-bottom: 4px;
}

.qa-suggestion-subtitle {
  font-size: 13px;
  color: var(--theme-primary);
  font-weight: 500;
}

.qa-suggestion-confidence {
  font-size: 11px;
  color: var(--theme-outline);
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
  background: var(--theme-primary);
  color: var(--theme-on-primary);
}

.qa-suggestion-btn-primary:hover:not(:disabled) {
  background: var(--theme-primary-strong);
}

.qa-suggestion-btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.qa-suggestion-btn-secondary {
  background: var(--theme-surface-container-lowest);
  color: var(--theme-outline);
  border: 1px solid var(--theme-outline-variant);
}

.qa-suggestion-btn-secondary:hover {
  background: var(--theme-surface-container-low);
}

/* ===== 问题卡片 ===== */
.qa-question-card {
  background: var(--theme-surface-container-lowest);
  border-color: var(--theme-outline-variant);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.qa-question-progress-bar {
  height: 4px;
  background: var(--theme-surface-container);
  border-radius: 2px;
  overflow: hidden;
  margin-bottom: 8px;
}

.qa-question-progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--theme-primary), var(--theme-secondary));
  border-radius: 2px;
  transition: width 0.4s ease;
}

.qa-question-progress-text {
  font-size: 11px;
  color: var(--theme-outline);
  margin-bottom: 12px;
}

.qa-question-text {
  font-size: 15px;
  font-weight: 500;
  color: var(--theme-on-surface);
  line-height: 1.5;
  margin-bottom: 10px;
}

.qa-question-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--theme-outline);
  margin-bottom: 14px;
  padding: 6px 10px;
  background: var(--theme-primary-soft);
  border-radius: 6px;
  border-left: 3px solid var(--theme-primary);
}

.qa-hint-icon {
  font-size: 14px;
  color: var(--theme-primary);
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
  background: var(--theme-surface-container-low);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  text-align: left;
}

.qa-option-btn:hover:not(:disabled) {
  background: var(--theme-primary-soft);
  border-color: var(--theme-primary);
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
  background: var(--theme-primary);
  color: var(--theme-on-primary);
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
}

.qa-option-display {
  font-size: 13px;
  color: var(--theme-on-surface-variant);
}

/* ===== 完成卡片 ===== */
.qa-completion-card {
  background: linear-gradient(135deg, rgba(46, 125, 50, 0.08) 0%, rgba(46, 125, 50, 0.04) 100%);
  border-color: rgba(46, 125, 50, 0.35);
}

.qa-completion-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.qa-completion-icon {
  font-size: 24px;
  color: var(--theme-success);
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
  color: var(--theme-on-surface-variant);
}

.qa-severity-badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
}

.qa-severity-none {
  background: rgba(46, 125, 50, 0.12);
  color: var(--theme-success);
}

.qa-severity-mild {
  background: rgba(237, 108, 2, 0.12);
  color: #92400e;
}

.qa-severity-moderate {
  background: rgba(234, 179, 8, 0.14);
  color: #a16207;
}

.qa-severity-severe {
  background: rgba(186, 26, 26, 0.1);
  color: var(--theme-error);
}

.qa-severity-critical {
  background: rgba(186, 26, 26, 0.14);
  color: #b71c1c;
  border: 1px solid rgba(183, 28, 28, 0.35);
}

.qa-completion-score-num {
  font-size: 14px;
  font-weight: 600;
  color: var(--theme-on-surface-variant);
  margin-bottom: 8px;
}

.qa-completion-interpretation {
  font-size: 13px;
  color: var(--theme-on-surface-variant);
  line-height: 1.6;
  margin-bottom: 8px;
  padding: 10px;
  background: rgba(255, 255, 255, 0.7);
  border-radius: 8px;
}

.qa-analysis-summary {
  margin-bottom: 8px;
  padding: 12px;
  background: var(--theme-primary-soft);
  border-left: 3px solid var(--theme-primary);
  border-radius: 0 8px 8px 0;
}

.qa-analysis-summary-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--theme-primary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 6px;
}

.qa-analysis-summary-content {
  font-size: 13px;
  color: var(--theme-on-surface-variant);
  line-height: 1.7;
}

.qa-completion-message {
  font-size: 12px;
  color: var(--theme-outline);
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
  color: var(--theme-error);
}

.qa-safety-alert-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--theme-on-error-container);
}

.qa-safety-alert-reason {
  font-size: 14px;
  font-weight: 500;
  color: var(--theme-on-error-container);
  margin-bottom: 6px;
}

.qa-safety-alert-message {
  font-size: 13px;
  color: var(--theme-on-surface-variant);
  line-height: 1.5;
}

/* ===== 查看详细报告按钮 ===== */
.qa-report-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  padding: 8px 16px;
  background: var(--theme-primary);
  color: var(--theme-on-primary);
  border: none;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s;
}

.qa-report-btn:hover {
  background: var(--theme-primary-strong);
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
  background: var(--theme-surface-container-lowest);
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
  border-bottom: 1px solid var(--theme-outline-variant);
  flex-shrink: 0;
}

.qa-report-dialog-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--theme-on-surface);
  margin: 0;
}

.qa-report-dialog-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  background: var(--theme-surface-container-low);
  border-radius: 50%;
  cursor: pointer;
  color: var(--theme-on-surface-variant);
  transition: background 0.2s;
}

.qa-report-dialog-close:hover {
  background: var(--theme-surface-container);
}

.qa-report-loading {
  padding: 40px;
  text-align: center;
  color: var(--theme-outline);
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
  color: var(--theme-primary);
  margin: 0 0 8px 0;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--theme-primary-soft);
}

.qa-report-text {
  font-size: 13px;
  color: var(--theme-on-surface-variant);
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
  background: rgba(186, 26, 26, 0.12);
  color: var(--theme-on-error-container);
  font-size: 12px;
  font-weight: 600;
  border-radius: 4px;
  width: fit-content;
}

.qa-report-list {
  margin: 6px 0;
  padding-left: 18px;
  font-size: 13px;
  color: var(--theme-on-surface-variant);
  line-height: 1.6;
}

.qa-report-conclusion {
  font-size: 13px;
  color: var(--theme-on-surface-variant);
  font-weight: 500;
  margin-top: 8px;
  padding: 10px;
  background: var(--theme-surface-container-low);
  border-radius: 6px;
  line-height: 1.6;
}

.qa-report-rec-group {
  margin-bottom: 12px;
}

.qa-report-rec-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--theme-outline);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin: 0 0 6px 0;
}

.qa-report-rec-item {
  padding: 8px 12px;
  background: var(--theme-surface-container-low);
  border-radius: 8px;
  margin-bottom: 6px;
  font-size: 13px;
  line-height: 1.5;
}

.qa-report-rec-item strong {
  color: var(--theme-on-surface);
  display: block;
  margin-bottom: 2px;
}

.qa-report-rec-item p {
  margin: 0;
  color: var(--theme-on-surface-variant);
}

.qa-report-rec-resource {
  display: inline-block;
  margin-top: 4px;
  padding: 2px 8px;
  background: var(--theme-primary-soft);
  color: var(--theme-primary);
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

/* ===== 纯表单卡片 ===== */
.qa-manual-form-card {
  background: var(--theme-surface-container-lowest);
  border-color: #e8a838;
  box-shadow: 0 1px 6px rgba(232, 168, 56, 0.15);
}

.qa-manual-form-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  margin-bottom: 16px;
  background: linear-gradient(135deg, #fff8e1, #fff3e0);
  border: 1px solid #ffe0b2;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  color: #e65100;
}

.qa-manual-form-banner-icon {
  font-size: 18px;
  color: #e65100;
}

.qa-manual-form-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--theme-on-surface);
  margin-bottom: 12px;
}

.qa-manual-form-item {
  margin-bottom: 16px;
  padding: 12px 14px;
  background: var(--theme-surface-container-low);
  border-radius: 10px;
  border: 1px solid var(--theme-outline-variant);
}

.qa-manual-form-item:last-of-type {
  margin-bottom: 8px;
}

.qa-manual-form-question-text {
  font-size: 14px;
  font-weight: 500;
  color: var(--theme-on-surface-variant);
  line-height: 1.6;
  margin-bottom: 10px;
}

.qa-manual-form-item-index {
  color: var(--theme-primary);
  font-weight: 600;
  margin-right: 4px;
}

.qa-manual-form-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--theme-outline-variant);
}

.qa-manual-form-hint {
  font-size: 12px;
  color: var(--theme-outline);
  font-style: italic;
}

.qa-manual-form-submitting {
  margin-top: 12px;
  padding: 10px;
  text-align: center;
}

.qa-option-btn-selected {
  background: var(--theme-primary-soft) !important;
  border-color: var(--theme-primary) !important;
  box-shadow: 0 0 0 1px var(--theme-primary);
}

.qa-option-check {
  font-size: 16px;
  color: var(--theme-primary);
  margin-left: auto;
}

/* ===== 医生端 Active Sessions 面板（左侧会话增强卡） ===== */
.qa-sidebar-active-sessions {
  border-right: 1px solid var(--theme-outline-variant);
}

.qa-session-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.qa-session-date {
  font-size: 10px;
  color: var(--theme-outline);
}

.qa-session-count {
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 600;
  padding: 1px 7px;
  border-radius: 9999px;
  background: var(--theme-surface-container);
  color: var(--theme-on-surface-variant);
}

.qa-session-active .qa-session-count {
  background: var(--theme-primary-fixed);
  color: var(--theme-primary);
}
</style>
