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
                  <div class="qa-message-ai">
                    <!-- 思考中状态 -->
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
import { ref, onMounted } from 'vue'
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
    messages.value = messagesList.map(msg => {
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
  } catch (error) {
    console.error('加载消息失败:', error)
    messages.value = []
  } finally {
    loadingMessages.value = false
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
  const content = inputText.value.trim()
  if (!content || !activeSessionId.value) return

  // 添加用户消息到UI
  const userMessage: ChatMessage = {
    id: Date.now().toString(),
    role: 'user',
    content: content
  }
  messages.value.push(userMessage)
  inputText.value = ''
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
</style>
