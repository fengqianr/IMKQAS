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
          <div class="qa-sidebar-footer-item">
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
                  <div class="qa-message-ai">
                    <div class="qa-ai-content text-sm text-on-surface leading-relaxed" v-html="message.content"></div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <!-- 输入区域 -->
        <div class="qa-input-area">
          <div class="qa-input-wrapper">
            <textarea v-model="inputText" @keyup.enter="sendMessage" class="qa-input-field"
                      placeholder="输入临床问题或上传病例附件..." rows="1"></textarea>
            <div class="qa-input-actions">
              <button class="qa-input-btn material-symbols-outlined">attach_file</button>
              <button @click="sendMessage" class="qa-send-btn">
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { qaService } from '@/api/services/qa.service'
import { conversationService } from '@/api/services/conversation.service'
import { authService } from '@/api/services/auth.service'
import type { Conversation, RetrievalStep } from '@/api/types/qa.types'

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
    // 使用模拟数据作为后备
    sessions.value = [
      { id: '1', title: '今日查房建议', icon: 'clinical_notes' },
      { id: '2', title: '术前风险评估', icon: 'medical_services' },
      { id: '3', title: '药物相互作用', icon: 'pill' },
      { id: '4', title: '病历自动补全', icon: 'history_edu' }
    ]
    if (!activeSessionId.value) {
      activeSessionId.value = '1'
    }
  } finally {
    loadingSessions.value = false
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
    messages.value = messagesList.map(msg => ({
      id: msg.id.toString(),
      role: msg.role.toLowerCase() as 'user' | 'assistant',
      content: msg.content,
      sourceReferences: msg.sourceReferences
    }))
  } catch (error) {
    console.error('加载消息失败:', error)
    // 使用模拟数据作为后备
    messages.value = [
      { id: '1', role: 'user', content: '请分析患者王某（65岁，男）在服用华法林期间出现轻微牙龈出血的临床风险，并给出调整建议。' },
      { id: '2', role: 'assistant', content: `根据最新的《华法林临床应用中国专家共识》，针对该患者的情况，分析如下：<ul class="space-y-3 mb-6">
<li class="flex gap-2"><span class="text-brand font-bold">1.</span><span><strong class="text-brand">当前风险等级：</strong>轻微出血（牙龈出血），属于临床常见不良反应。需首先核查患者最新的 INR（国际标准化比值）。</span></li>
<li class="flex gap-2"><span class="text-brand font-bold">2.</span><span><strong class="text-brand">INR 阈值参考：</strong>若 INR &lt; 3.0，建议维持原剂量，并监测牙龈状况；若 INR 在 3.0-4.5 之间，建议减少剂量或停药一次。</span></li>
<li class="flex gap-2"><span class="text-brand font-bold">3.</span><span><strong class="text-brand">相互作用核查：</strong>系统检测到患者近期曾开具阿司匹林，两者合用显著增加出血风险，建议评估双联抗栓的必要性。</span></li>
</ul>
<div class="mt-6 pt-4 border-t border-outline-variant">
<div class="text-[10px] text-on-surface-variant font-bold uppercase mb-3 tracking-widest flex items-center gap-2"><span class="material-symbols-outlined text-xs">auto_stories</span>数据源与参考文献</div>
<div class="flex flex-wrap gap-2">
<span class="px-2 py-1 bg-surface-container text-[11px] text-on-surface-variant rounded flex items-center gap-1 cursor-pointer hover:bg-secondary-fixed transition-colors">[1] 华法林抗凝治疗中国专家共识 (2023)</span>
<span class="px-2 py-1 bg-surface-container text-[11px] text-on-surface-variant rounded flex items-center gap-1 cursor-pointer hover:bg-secondary-fixed transition-colors">[2] 临床药物相互作用数据库 v4.2</span>
</div>
</div>` }
    ]
  } finally {
    loadingMessages.value = false
  }
}

// 切换会话
const switchSession = async (sessionId: string) => {
  activeSessionId.value = sessionId
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

// 发送消息
const sendMessage = async () => {
  const content = inputText.value.trim()
  if (!content || !activeSessionId.value) return

  // 添加用户消息到UI
  const userMessage: ChatMessage = {
    id: Date.now().toString(),
    role: 'user',
    content: content
  }
  messages.value.push(userMessage)

  // 清空输入框
  inputText.value = ''

  try {
    // 保存用户消息到后端
    await conversationService.createMessage({
      conversationId: activeSessionId.value,
      content: content,
      role: 'user'
    })

    // 调用QA服务获取AI回复
    const response = await qaService.ask({
      question: content,
      conversationId: activeSessionId.value,
      userId: authService.getToken() ? 1 : undefined
    })

    // 从响应中提取参考文献引用
    const sourceRefs = response.citations?.map((c, i) => ({
      id: c.documentId,
      chunkId: c.chunkId,
      label: `[${i + 1}] ${c.title}`,
      snippet: c.snippet,
      score: c.relevanceScore
    })) || []

    // 构建带参考文献的AI回复内容
    let aiContent = response.answer
    if (sourceRefs.length > 0) {
      const refSection = `
<div class="mt-6 pt-4 border-t border-outline-variant">
<div class="text-[10px] text-on-surface-variant font-bold uppercase mb-3 tracking-widest flex items-center gap-2"><span class="material-symbols-outlined text-xs">auto_stories</span>数据源与参考文献</div>
<div class="flex flex-wrap gap-2">
${sourceRefs.map(ref => `<span class="px-2 py-1 bg-surface-container text-[11px] text-on-surface-variant rounded flex items-center gap-1 cursor-pointer hover:bg-secondary-fixed transition-colors" title="${ref.snippet}">${ref.label}</span>`).join('\n')}
</div>
</div>`
      aiContent += refSection
    }

    // 添加AI回复到UI
    const aiMessage: ChatMessage = {
      id: (Date.now() + 1).toString(),
      role: 'assistant',
      content: aiContent
    }
    messages.value.push(aiMessage)

    // 保存AI回复和引用到后端
    await conversationService.createMessage({
      conversationId: activeSessionId.value,
      content: response.answer,
      role: 'assistant',
      sourceReferences: JSON.stringify(sourceRefs)
    })

    // 更新右侧知识检索路径面板
    if (response.retrievalPath && response.retrievalPath.steps.length > 0) {
      retrievalSteps.value = response.retrievalPath.steps
      expandedSteps.value = false
    } else if (response.citations && response.citations.length > 0) {
      // 降级：从现有响应字段构建简单步骤
      retrievalSteps.value = [
        { stepName: '向量语义库检索', stepOrder: 1, durationMs: 0, inputCount: 0,
          outputCount: response.citations.length, status: 'SUCCESS' },
        { stepName: 'LLM推理生成', stepOrder: 2, durationMs: 0, inputCount: response.citations.length,
          outputCount: 1, status: 'SUCCESS' }
      ]
    }

  } catch (error) {
    console.error('发送消息失败:', error)
    // 添加错误提示消息
    const errorMessage: ChatMessage = {
      id: (Date.now() + 2).toString(),
      role: 'assistant',
      content: `抱歉，处理您的请求时出现错误：${(error as Error).message}。请稍后重试。`
    }
    messages.value.push(errorMessage)
  }
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
</style>
