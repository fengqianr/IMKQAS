<template>
  <div class="qa-view">
    <!-- 顶部导航栏 -->
    <header class="fixed top-0 w-full z-50 bg-white/80 dark:bg-slate-900/80 backdrop-blur-md shadow-sm h-16 flex justify-between items-center px-6">
      <div class="flex items-center gap-8">
        <div class="text-xl font-bold text-blue-800 dark:text-blue-200 tracking-tight font-manrope">
          Clinical Precision RAG
        </div>
        <nav class="hidden md:flex items-center gap-6">
          <a class="font-manrope tracking-tight font-semibold text-blue-700 dark:text-blue-400 border-b-2 border-blue-700 dark:border-blue-400 pb-1 transition-colors" href="/qa">智能问答</a>
          <a class="font-manrope tracking-tight font-semibold text-slate-500 dark:text-slate-400 hover:text-blue-600 dark:hover:text-blue-300 transition-colors" href="/knowledge">知识库</a>
          <a class="font-manrope tracking-tight font-semibold text-slate-500 dark:text-slate-400 hover:text-blue-600 dark:hover:text-blue-300 transition-colors" href="/stats">数据分析</a>
          <a class="font-manrope tracking-tight font-semibold text-slate-500 dark:text-slate-400 hover:text-blue-600 dark:hover:text-blue-300 transition-colors" href="/user">个人中心</a>
        </nav>
      </div>
      <div class="flex items-center gap-4">
        <button class="p-2 text-on-surface-variant hover:bg-surface-container-high dark:hover:bg-surface-container rounded-lg transition-colors material-symbols-outlined">notifications</button>
        <button class="p-2 text-on-surface-variant hover:bg-surface-container-high dark:hover:bg-surface-container rounded-lg transition-colors material-symbols-outlined">settings</button>
        <img alt="User Profile" class="w-8 h-8 rounded-full border border-outline-variant/15" data-alt="close-up professional portrait of a medical doctor in a white coat with a clean clinical background" src="https://lh3.googleusercontent.com/aida-public/AB6AXuDMKPVJL-B3aLQu4CtZ_KOGUSY3VDwcOYDbQaQbUQspANy_0Ie-w9P92EaTPnn6QSN0VqL5W2tyPmdWOra_LQYUSq7f3u8wKEjXbhb_oQmjYT9M-oJkgZJsjFsMfLtW2n5pRZV_wRSgR27cQLetYJP--OkjG_2v03qr2MRNl_66Ba7Aluj_lMEe5wlSKT2HJ-ATtZhSYgWpw4qILX2CIEX0Um5CbiBlIhnGqbbZoILW5Gl4rGmzfhFQrAERT2VMBn7-EYLXnzDmLBg"/>
      </div>
    </header>
    <div class="h-16"></div>
    <div class="flex flex-1 pt-16 h-screen overflow-hidden">
      <!-- 侧边栏 -->
      <aside class="h-screen w-64 bg-secondary-left dark:bg-secondary-left flex flex-col pb-4 border-r-0">
        <div class="p-6 pt-4">
          <div class="flex items-center gap-3 mb-6">
            <div class="w-10 h-10 rounded-xl bg-primary flex items-center justify-center text-white">
              <span class="material-symbols-outlined" style="font-variation-settings: 'FILL' 1;">clinical_notes</span>
            </div>
            <div>
              <div class="text-blue-800 dark:text-blue-300 font-manrope font-bold text-sm">会话管理</div>
              <div class="text-[10px] text-on-surface-variant uppercase tracking-wider">最近咨询列表</div>
            </div>
          </div>
          <button @click="createNewSession" class="w-full bg-primary hover:bg-primary-container text-white py-3 rounded-full font-semibold text-sm flex items-center justify-center gap-2 transition-all active:scale-95 shadow-lg shadow-primary/10">
            <span class="material-symbols-outlined text-sm">add</span>
            新建咨询
          </button>
        </div>
        <nav class="flex-1 overflow-y-auto no-scrollbar space-y-1">
          <div v-for="session in sessions" :key="session.id" @click="switchSession(session.id)" :class="['px-4 py-2 mx-2 flex items-center gap-3 cursor-pointer transition-all rounded-lg', session.id === activeSessionId ? 'bg-blue-100 dark:bg-blue-900/40 text-blue-800 dark:text-blue-200' : 'text-slate-600 dark:text-slate-400 hover:bg-slate-200 dark:hover:bg-slate-800']">
            <span class="material-symbols-outlined text-lg">{{ session.icon }}</span>
            <span class="font-inter text-sm truncate">{{ session.title }}</span>
          </div>
        </nav>
        <div class="mt-auto px-2 space-y-1">
          <div class="text-slate-600 dark:text-slate-400 px-4 py-2 hover:bg-slate-200 dark:hover:bg-slate-800 rounded-lg flex items-center gap-3 cursor-pointer text-sm">
            <span class="material-symbols-outlined text-lg">delete</span>
            回收站
          </div>
          <div class="text-slate-600 dark:text-slate-400 px-4 py-2 hover:bg-slate-200 dark:hover:bg-slate-800 rounded-lg flex items-center gap-3 cursor-pointer text-sm">
            <span class="material-symbols-outlined text-lg">help</span>
            帮助中心
          </div>
        </div>
      </aside>
      <!-- 主内容区 -->
      <main class="flex flex-1 mt-16 h-screen overflow-hidden relative bg-surface">
        <!-- 聊天界面 -->
        <section class="flex-1 flex flex-col h-full">
          <!-- 正文内容——统一滑动区域 -->
          <div class="flex-1 overflow-y-auto no-scrollbar scroll-smooth">
            <!-- 医疗安全提示 -->
            <div class="mx-6 mt-4">
              <div class="bg-tertiary-fixed/30 border-l-4 border-tertiary p-3 rounded-r-lg flex items-start gap-3">
                <span class="material-symbols-outlined text-tertiary text-lg">warning</span>
                <p class="text-xs text-on-tertiary-fixed-variant leading-relaxed">
                  <span class="font-bold">医疗安全提示：</span>本系统提供的信息仅供临床参考，不作为最终诊断及用药依据。请结合患者实际体征及相关检查结果，由执业医师进行最终决策。
                </p>
              </div>
            </div>
            <!-- 聊天消息列表 -->
            <div class="px-8 pb-8 space-y-10">
            <!-- 用户消息 -->
            <div v-for="message in messages" :key="message.id" :class="['flex', message.role === 'user' ? 'justify-end' : 'justify-start']">
              <div v-if="message.role === 'user'" class="max-w-[80%] bg-white rounded-xl rounded-br-sm p-4 shadow-sm border border-outline-variant/10">
                <p class="text-on-surface text-sm leading-relaxed">{{ message.content }}</p>
              </div>
              <!-- AI响应 -->
              <div v-else class="flex flex-col gap-5">
                <div class="flex items-center gap-3">
                  <div class="w-8 h-8 rounded-full bg-brand flex items-center justify-center text-on-primary">
                    <span class="material-symbols-outlined text-sm" style="font-variation-settings: 'FILL' 1;">smart_toy</span>
                  </div>
                  <span class="text-xs font-bold text-brand font-headline">PRECISION AI 临床决策支持</span>
                </div>
                <div class="bg-surface-container-lowest rounded-xl rounded-bl-sm p-6 shadow-lg border-0 max-w-[90%]">
                  <div class="prose prose-sm text-on-surface leading-relaxed" v-html="message.content"></div>
                </div>
              </div>
            </div>
            </div>
          </div>
          <!-- 输入区域 -->
          <div class="p-6 bg-surface-container-lowest/50 backdrop-blur-md">
            <div class="max-w-4xl mx-auto relative">
              <textarea v-model="inputText" @keyup.enter="sendMessage" class="w-full bg-surface-container-lowest border-0 ring-1 ring-outline-variant/30 focus:ring-2 focus:ring-brand rounded-full py-4 pl-6 pr-16 text-sm resize-none shadow-lg shadow-brand/5 font-inter" placeholder="输入临床问题或上传病例附件..." rows="1"></textarea>
              <div class="absolute right-2 top-1/2 -translate-y-1/2 flex items-center gap-1">
                <button class="p-2 text-on-surface-variant hover:text-brand transition-colors material-symbols-outlined">attach_file</button>
                <button @click="sendMessage" class="w-10 h-10 bg-brand text-on-primary rounded-full flex items-center justify-center hover:bg-brand-container transition-all shadow-md shadow-brand/20">
                  <span class="material-symbols-outlined text-sm">send</span>
                </button>
              </div>
            </div>
            <div class="text-center mt-3">
              <span class="text-[10px] text-on-surface-variant">支持 PDF 病历解析 · 实时检索临床指南</span>
            </div>
          </div>
        </section>
        <!-- 检索路径面板（右侧）——独立滑动 -->
        <aside class="w-80 bg-surface-container-low border-l-0 hidden xl:flex flex-col p-8 overflow-y-auto no-scrollbar scroll-smooth h-full">
          <div class="mb-10">
            <h3 class="text-sm font-bold text-on-surface font-headline mb-6 flex items-center gap-2">
              <span class="material-symbols-outlined text-brand text-lg">schema</span>
              知识检索路径
            </h3>
            <div class="space-y-6">
              <div v-for="(step, index) in retrievalSteps" :key="index" class="relative pl-6 pb-6 border-l border-brand/20 last:border-l-0">
                <div class="absolute left-[-5px] top-1 w-2.5 h-2.5 rounded-full" :class="[step.color === 'secondary' ? 'bg-secondary ring-4 ring-secondary/10' : 'bg-brand ring-4 ring-brand/10']"></div>
                <div class="text-[11px] font-bold mb-2" :class="[step.color === 'secondary' ? 'text-secondary' : 'text-brand']">{{ step.title }}</div>
                <div class="bg-surface p-4 rounded-lg shadow-sm text-xs text-on-surface-variant">
                  {{ step.description }}
                  <div v-if="step.confidence" class="mt-3 h-1.5 w-full bg-surface-container-low rounded-full overflow-hidden">
                    <div class="h-full bg-secondary" :style="{ width: step.confidence * 100 + '%' }"></div>
                  </div>
                  <div v-if="step.confidence" class="mt-2 text-right text-[10px]">置信度: {{ step.confidence.toFixed(2) }}</div>
                </div>
              </div>
            </div>
          </div>
          <div class="mt-8">
            <h3 class="text-sm font-bold text-on-surface font-headline mb-6 flex items-center gap-2">
              <span class="material-symbols-outlined text-brand text-lg">monitoring</span>
              患者体征快照
            </h3>
            <div class="bg-surface rounded-xl p-6 shadow-sm space-y-6 border-0">
              <div class="flex justify-between items-end" v-for="(value, key) in patientVitals" :key="key">
                <span class="text-xs text-on-surface-variant">{{ key }}</span>
                <span class="text-xl font-bold" :class="[key === '最新 INR' ? 'text-brand' : key === '近7日出血风险' ? 'text-accent' : 'text-on-surface']">{{ value }}</span>
              </div>
            </div>
          </div>
        </aside>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { qaService } from '@/api/services/qa.service'
import { conversationService } from '@/api/services/conversation.service'
import { authService } from '@/api/services/auth.service'
import type { Conversation } from '@/api/types/qa.types'

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

interface RetrievalStep {
  title: string
  description: string
  color: 'primary' | 'secondary'
  confidence?: number
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

// 检索路径步骤
const retrievalSteps = ref<RetrievalStep[]>([
  { title: '关键实体识别', description: '识别对象：王某, 65岁, 华法林, 牙龈出血', color: 'primary', confidence: undefined },
  { title: '向量语义库检索', description: '命中章节：《抗凝药物出血管理规范》第4章', color: 'secondary', confidence: 0.89 },
  { title: '逻辑推理生成', description: '生成策略：多源整合 + 风险量化提示', color: 'primary', confidence: undefined }
])

// 患者体征
const patientVitals = reactive({
  '最新 INR': '2.85',
  '血压 (mmHg)': '132/84',
  '近7日出血风险': '中度'
})

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
    if (response.citations && response.citations.length > 0) {
      retrievalSteps.value = [
        { title: '向量语义库检索', description: `命中 ${response.citations.length} 个相关文档片段`, color: 'secondary', confidence: response.confidence },
        { title: '逻辑推理生成', description: `模型: ${response.modelUsed}`, color: 'primary', confidence: undefined }
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
/* 平滑滚动效果 */
.scroll-smooth {
  scroll-behavior: smooth;
}

/* 自定义滚动条隐藏 */
.no-scrollbar::-webkit-scrollbar {
  display: none;
}

/* 确保Material Symbols图标正确显示 */
.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}

/* 字体设置 */
:deep(body) {
  font-family: 'Inter', sans-serif;
}

:deep(h1), :deep(h2), :deep(h3), .brand-font {
  font-family: 'Manrope', sans-serif;
}


/* 玻璃效果 */
.glass-effect {
  backdrop-filter: blur(20px);
}



/* 交互效果增强 - 确保与原型图一致 */
/* 悬停状态 */
.hover\:bg-primary-container:hover {
  background-color: var(--color-primary-container);
}

.hover\:bg-secondary-fixed:hover {
  background-color: var(--color-secondary-fixed);
}

.hover\:bg-tertiary-fixed:hover {
  background-color: var(--color-tertiary-fixed);
}

/* 聚焦状态 */
.focus\:ring-2:focus {
  outline: 2px solid transparent;
  outline-offset: 2px;
  ring-width: 2px;
  ring-color: var(--color-primary);
}

.focus\:ring-primary:focus {
  --tw-ring-color: var(--color-primary);
}

/* 活动状态动画 */
.active\:scale-95:active {
  transform: scale(0.95);
  transition: transform 0.1s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 过渡效果优化 */
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

/* 阴影效果增强 */
.shadow-lg {
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05);
}

.shadow-md {
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
}

.shadow-sm {
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
}

/* 玻璃效果增强 */
.glass-effect {
  backdrop-filter: blur(20px);
  background: linear-gradient(
    135deg,
    rgba(255, 255, 255, 0.7) 0%,
    rgba(255, 255, 255, 0.4) 100%
  );
  border: 1px solid rgba(255, 255, 255, 0.2);
}

/* 响应式断点优化 */
@media (max-width: 768px) {
  .md\:hidden {
    display: none;
  }
}

@media (min-width: 1280px) {
  .xl\:flex {
    display: flex;
  }
}

/* 确保Material Symbols图标在所有状态下正确显示 */
.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
  transition: font-variation-settings 150ms cubic-bezier(0.4, 0, 0.2, 1);
}

.material-symbols-outlined[style*="font-variation-settings: 'FILL' 1"] {
  font-variation-settings: 'FILL' 1, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}
</style>