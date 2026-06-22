<template>
  <div class="knowledge-page">
    <!-- 顶部导航栏（与QA页面一致） -->
    <header class="qa-header">
      <div class="custom-flex custom-items-center custom-gap-8">
        <div class="qa-header-logo">
          Clinical Precision RAG
        </div>
        <nav class="custom-hidden custom-md-flex custom-items-center custom-gap-6">
          <router-link to="/qa" class="qa-nav-link" :class="$route.path === '/qa' ? 'qa-nav-link-active' : 'qa-nav-link-inactive'">智能问答</router-link>
          <router-link to="/knowledge" class="qa-nav-link" :class="$route.path === '/knowledge' ? 'qa-nav-link-active' : 'qa-nav-link-inactive'">知识库</router-link>
          <router-link to="/stats" class="qa-nav-link" :class="$route.path === '/stats' ? 'qa-nav-link-active' : 'qa-nav-link-inactive'">数据分析</router-link>
          <router-link to="/user" class="qa-nav-link" :class="$route.path === '/user' ? 'qa-nav-link-active' : 'qa-nav-link-inactive'">个人中心</router-link>
        </nav>
      </div>
      <div class="custom-flex custom-items-center custom-gap-4">
        <button class="qa-icon-btn material-symbols-outlined">notifications</button>
        <button class="qa-icon-btn material-symbols-outlined">settings</button>
        <img alt="User Profile" class="qa-header-avatar" src="https://lh3.googleusercontent.com/aida-public/AB6AXuDMKPVJL-B3aLQu4CtZ_KOGUSY3VDwcOYDbQaQbUQspANy_0Ie-w9P92EaTPnn6QSN0VqL5W2tyPmdWOra_LQYUSq7f3u8wKEjXbhb_oQmjYT9M-oJkgZJsjFsMfLtW2n5pRZV_wRSgR27cQLetYJP--OkjG_2v03qr2MRNl_66Ba7Aluj_lMEe5wlSKT2HJ-ATtZhSYgWpw4qILX2CIEX0Um5CbiBlIhnGqbbZoILW5Gl4rGmzfhFQrAERT2VMBn7-EYLXnzDmLBg"/>
      </div>
    </header>

    <!-- 主内容区域 -->
    <div class="knowledge-content">
      <!-- Dashboard Header -->
      <header class="custom-mb-10">
        <h1 class="text-3xl font-bold font-headline tracking-tight text-on-surface custom-mb-2">
          知识库管理
        </h1>
        <p class="text-on-surface-variant custom-max-w-2xl">
          集中化管理医疗文献、病历记录与诊疗指南。利用 RAG 技术实现精准的临床辅助决策。
        </p>
      </header>

      <div class="custom-grid custom-grid-cols-12 custom-gap-6">
        <!-- Left Column: Navigation & Upload -->
        <div class="col-span-3 space-y-6">
          <!-- Category Management - 侧边栏 -->
          <section
            class="bg-surface-container-lowest rounded-xl custom-p-6 shadow-ambient"
          >
            <div class="custom-flex custom-items-center custom-justify-between custom-mb-6">
              <h3 class="font-headline font-bold text-on-surface">分类管理</h3>
              <button
                class="text-primary hover-bg-primary-fixed custom-p-1 rounded-md custom-transition-colors"
              >
                <span class="material-symbols-outlined text-xl">add_circle</span>
              </button>
            </div>
            <nav class="space-y-1">
              <button
                v-for="(category, index) in categories"
                :key="index"
                :class="[
                  'custom-w-full custom-flex custom-items-center custom-justify-between custom-px-4 custom-py-3 custom-transition-all group rounded-full',
                  selectedCategory === category.name
                    ? 'bg-primary-fixed text-on-primary-fixed'
                    : 'text-on-surface-variant hover-bg-surface-container-low'
                ]"
                @click="selectCategory(category.name)"
              >
                <div class="custom-flex custom-items-center custom-gap-3">
                  <span
                    class="material-symbols-outlined text-xl"
                    :class="selectedCategory === category.name ? 'text-on-primary-fixed' : 'text-on-surface-variant'"
                  >
                    {{ selectedCategory === category.name ? 'folder_open' : 'folder' }}
                  </span>
                  <span class="font-medium text-sm">{{ category.name }}</span>
                </div>
                <span
                  :class="[
                    'text-xs custom-px-2 custom-py-0.5 rounded-full',
                    selectedCategory === category.name
                      ? 'bg-primary-container text-on-primary-container'
                      : 'text-on-surface-variant opacity-60'
                  ]"
                >
                  {{ category.count }}
                </span>
              </button>
            </nav>
          </section>

          <!-- Upload Area - 拖拽上传 -->
          <section
            class="bg-surface-container-lowest border-2 border-dashed border-outline-variant/30 rounded-xl custom-p-8 text-center group hover-border-primary/50 custom-transition-all cursor-pointer"
            :class="{ 'border-primary/50': isDragging }"
            @click="triggerFileUpload"
            @dragover.prevent="onDragOver"
            @dragleave.prevent="onDragLeave"
            @drop.prevent="onDrop"
          >
            <div
              class="bg-primary-fixed custom-w-16 custom-h-16 rounded-full custom-flex custom-items-center custom-justify-center custom-mx-auto custom-mb-4 group-hover-scale-110 custom-transition-transform"
            >
              <span class="material-symbols-outlined text-primary text-3xl">upload_file</span>
            </div>
            <h4 class="font-headline font-bold text-on-surface custom-mb-2">上传医学文档</h4>
            <p class="text-xs text-on-surface-variant custom-mb-6 leading-relaxed">
              仅支持 PDF 格式<br />单个文件最大支持 50MB
            </p>
            <button
              class="custom-w-full custom-py-3 custom-px-4 border border-outline-variant rounded-full text-sm font-semibold hover-bg-surface-container-low custom-transition-colors"
              @click.stop="triggerFileUpload"
            >
              选择文件
            </button>
            <input
              ref="fileInput"
              type="file"
              class="custom-hidden"
              accept=".pdf"
              multiple
              @change="handleFileSelect"
            />
          </section>
        </div>

        <!-- Right Column: Document List & Detail View -->
        <div class="col-span-9 space-y-8">
          <!-- Document List Card - 主内容区 -->
          <section
            class="bg-surface-container-lowest rounded-xl shadow-ambient custom-overflow-hidden"
          >
            <div class="custom-p-6 custom-flex custom-items-center custom-justify-between">
              <div class="custom-flex custom-items-center custom-gap-4">
                <div class="custom-relative">
                  <span
                    class="custom-absolute custom-left-3 top-1/2 -translate-y-1/2 material-symbols-outlined text-outline text-xl"
                  >search</span>
                  <input
                    v-model="searchQuery"
                    class="custom-pl-10 custom-pr-4 custom-py-2.5 bg-surface-container-low border-none rounded-full text-sm custom-w-80 focus-ring-2 focus-ring-primary-20 custom-transition-all placeholder-on-surface-variant"
                    placeholder="搜索文档标题或内容..."
                    type="text"
                  />
                </div>
                <button
                  class="custom-flex custom-items-center custom-gap-2 custom-px-4 custom-py-2.5 text-sm font-medium text-on-surface-variant hover-bg-surface-container-low rounded-full custom-transition-all"
                >
                  <span class="material-symbols-outlined text-lg">filter_list</span>
                  筛选器
                </button>
              </div>
              <div class="custom-flex custom-items-center custom-gap-2 text-xs font-medium text-on-surface-variant">
                显示 {{ filteredDocuments.length > 0 ? '1-' + Math.min(filteredDocuments.length, 10) : '0' }} / 共
                {{ filteredDocuments.length }} 个文件
              </div>
            </div>

            <!-- 表格区域 - 无边框，通过背景变化区分 -->
            <div class="custom-overflow-x-auto">
              <table class="custom-w-full text-left">
                <thead>
                  <tr class="bg-surface-container-low/50 text-on-surface-variant text-xs font-semibold uppercase tracking-wider">
                    <th class="custom-px-6 custom-py-4">文档名称</th>
                    <th class="custom-px-6 custom-py-4">大小</th>
                    <th class="custom-px-6 custom-py-4">分类</th>
                    <th class="custom-px-6 custom-py-4">上传时间</th>
                    <th class="custom-px-6 custom-py-4">处理状态</th>
                    <th class="custom-px-6 custom-py-4">操作</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-outline-variant-10">
                  <!-- 加载状态 -->
                  <tr v-if="loading">
                    <td colspan="6" class="custom-px-6 custom-py-12 text-center">
                      <div class="custom-flex custom-flex-col custom-items-center custom-justify-center custom-gap-3">
                        <span class="material-symbols-outlined text-3xl text-brand custom-animate-spin">refresh</span>
                        <p class="font-body text-sm text-secondary">加载文档列表中...</p>
                      </div>
                    </td>
                  </tr>
                  <!-- 空状态 -->
                  <tr v-else-if="filteredDocuments.length === 0">
                    <td colspan="6" class="custom-px-6 custom-py-12 text-center">
                      <div class="custom-flex custom-flex-col custom-items-center custom-justify-center custom-gap-3">
                        <span class="material-symbols-outlined text-3xl text-secondary">description</span>
                        <p class="font-body text-sm text-secondary">暂无文档数据</p>
                      </div>
                    </td>
                  </tr>
                  <!-- 文档列表 -->
                  <tr
                    v-for="doc in filteredDocuments.slice(0, 10)"
                    :key="doc.id"
                    class="hover-bg-surface custom-transition-colors group cursor-pointer"
                    :class="{ 'bg-brand/20': selectedDoc?.id === doc.id }"
                    @click="selectDocument(doc)"
                  >
                    <td class="custom-px-6 custom-py-5">
                      <div class="custom-flex custom-items-center custom-gap-3">
                        <div
                          class="custom-w-8 custom-h-8 rounded custom-flex custom-items-center custom-justify-center"
                          :class="doc.type === 'pdf' ? 'bg-red-50' : 'bg-blue-50'"
                        >
                          <span
                            class="material-symbols-outlined text-lg"
                            :class="doc.type === 'pdf' ? 'text-red-600' : 'text-blue-600'"
                          >{{ doc.type === 'pdf' ? 'description' : 'article' }}</span>
                        </div>
                        <span class="text-sm font-semibold text-on-surface">{{ doc.name }}</span>
                      </div>
                    </td>
                    <td class="custom-px-6 custom-py-4 text-sm text-on-surface-variant">{{ doc.size }}</td>
                    <td class="custom-px-6 custom-py-4">
                      <span class="text-xs bg-secondary-container text-on-secondary-container custom-px-2 custom-py-1 rounded">{{ doc.category }}</span>
                    </td>
                    <td class="custom-px-6 custom-py-4 text-sm text-on-surface-variant">{{ doc.uploadTime }}</td>
                    <td class="custom-px-6 custom-py-4">
                      <div
                        v-if="doc.status === 'completed'"
                        class="custom-flex custom-items-center custom-gap-2 text-xs font-medium text-success"
                      >
                        <span class="custom-w-1.5 custom-h-1.5 rounded-full bg-success"></span>
                        已完成向量化
                      </div>
                      <div
                        v-else-if="doc.status === 'processing'"
                        class="custom-flex custom-items-center custom-gap-2 text-xs font-medium text-blue-600"
                      >
                        <span class="custom-w-1.5 custom-h-1.5 rounded-full bg-blue-600 custom-animate-pulse"></span>
                        正在处理 ({{ doc.progress }}%)
                      </div>
                      <div
                        v-else
                        class="custom-flex custom-items-center custom-gap-2 text-xs font-medium text-on-surface-variant"
                      >
                        <span class="custom-w-1.5 custom-h-1.5 rounded-full bg-on-surface-variant"></span>
                        待处理
                      </div>
                    </td>
                    <td class="custom-px-6 custom-py-4">
                      <el-dropdown trigger="click" @command="(cmd: string) => handleDocAction(cmd, doc)">
                        <button class="text-outline hover-text-primary custom-transition-colors">
                          <span class="material-symbols-outlined">more_vert</span>
                        </button>
                        <template #dropdown>
                          <el-dropdown-menu>
                            <el-dropdown-item command="preview">
                              <span class="material-symbols-outlined custom-mr-2">visibility</span>
                              预览
                            </el-dropdown-item>
                            <el-dropdown-item command="delete">
                              <span class="material-symbols-outlined custom-mr-2">delete</span>
                              删除
                            </el-dropdown-item>
                            <el-dropdown-item command="reprocess">
                              <span class="material-symbols-outlined custom-mr-2">refresh</span>
                              重新处理
                            </el-dropdown-item>
                          </el-dropdown-menu>
                        </template>
                      </el-dropdown>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

          <!-- Split View: Preview & Chunk Browsing -->
          <section class="custom-grid custom-grid-cols-2 custom-gap-6 custom-h-580">
            <!-- Document Preview - 毛玻璃效果 -->
            <div
              class="bg-surface rounded-xl custom-flex custom-flex-col custom-overflow-hidden shadow-ambient"
              :class="{ 'backdrop-blur-xl bg-surface/80': selectedDoc }"
            >
              <div class="custom-p-5 custom-flex custom-items-center custom-justify-between bg-surface">
                <h3 class="font-headline font-bold text-sm text-primary custom-flex custom-items-center custom-gap-2">
                  <span class="material-symbols-outlined text-brand">description</span>
                  文档预览
                </h3>
                <div class="custom-flex custom-items-center custom-gap-1">
                  <button class="custom-p-2 hover-bg-subtle rounded-xl custom-transition-colors">
                    <span class="material-symbols-outlined text-lg">zoom_in</span>
                  </button>
                  <button class="custom-p-2 hover-bg-subtle rounded-xl custom-transition-colors">
                    <span class="material-symbols-outlined text-lg">zoom_out</span>
                  </button>
                  <button class="custom-p-2 hover-bg-subtle rounded-xl custom-transition-colors">
                    <span class="material-symbols-outlined text-lg">fullscreen</span>
                  </button>
                </div>
              </div>
              <div class="custom-flex-1 bg-surface custom-p-6 custom-overflow-y-auto">
                <!-- 文档预览内容区 -->
                <!-- 加载状态 -->
                <div
                  v-if="selectedDoc && previewLoading"
                  class="custom-flex custom-flex-col custom-items-center custom-justify-center custom-h-full"
                >
                  <span class="material-symbols-outlined text-3xl text-brand custom-animate-spin custom-mb-3">refresh</span>
                  <p class="font-body text-sm text-secondary">加载文档预览中...</p>
                </div>
                <!-- 错误状态 -->
                <div
                  v-else-if="selectedDoc && previewError"
                  class="custom-flex custom-flex-col custom-items-center custom-justify-center custom-h-full"
                >
                  <span class="material-symbols-outlined text-4xl text-red-400 custom-mb-3">error_outline</span>
                  <p class="font-headline font-medium text-primary">预览加载失败</p>
                  <p class="font-body text-xs custom-mt-2 text-secondary">该文档可能已被删除或格式不支持预览</p>
                  <button
                    class="custom-mt-4 custom-px-4 custom-py-2 text-xs font-body bg-brand text-white rounded-full hover-opacity-90 custom-transition-opacity"
                    @click="fetchDocumentPreview(selectedDoc)"
                  >
                    <span class="material-symbols-outlined text-sm custom-mr-1">refresh</span>
                    重试
                  </button>
                </div>
                <!-- PDF预览 -->
                <div
                  v-else-if="selectedDoc && selectedDoc.type === 'pdf' && previewBlobUrl"
                  class="custom-h-full custom-flex custom-flex-col"
                >
                  <h2 class="text-sm font-bold text-center border-b border-outline-variant/30 custom-pb-3 custom-mb-3 text-primary">
                    {{ selectedDoc.name }}
                  </h2>
                  <iframe
                    ref="previewContainer"
                    :src="previewBlobUrl"
                    class="custom-w-full custom-flex-1 rounded-lg border-0"
                    style="min-height: 400px;"
                  ></iframe>
                </div>
                <!-- 文本预览 -->
                <div
                  v-else-if="selectedDoc && previewText"
                  class="custom-h-full custom-flex custom-flex-col"
                >
                  <h2 class="text-sm font-bold text-center border-b border-outline-variant/30 custom-pb-3 custom-mb-3 text-primary">
                    {{ selectedDoc.name }}
                  </h2>
                  <pre
                    class="custom-flex-1 custom-overflow-y-auto text-xs leading-relaxed text-on-surface-variant bg-surface-container-low rounded-lg custom-p-4 whitespace-pre-wrap break-words font-body"
                    style="max-height: 480px;"
                  >{{ previewText }}</pre>
                </div>
                <!-- 未选择文档 -->
                <div v-else class="custom-flex custom-items-center custom-justify-center custom-h-full">
                  <div class="text-center custom-p-10 bg-surface rounded-xl">
                    <span class="material-symbols-outlined text-5xl custom-mb-4 text-secondary">description</span>
                    <p class="font-headline font-medium text-primary">请选择一个文档进行预览</p>
                    <p class="font-body text-xs custom-mt-2 text-secondary">点击左侧文档列表中的文档</p>
                  </div>
                </div>
              </div>
            </div>

            <!-- Chunk Browsing -->
            <div
              class="bg-surface rounded-xl custom-flex custom-flex-col custom-overflow-hidden shadow-ambient"
            >
              <div class="custom-p-5 custom-flex custom-items-center custom-justify-between bg-surface">
                <h3 class="font-headline font-bold text-sm text-primary custom-flex custom-items-center custom-gap-2">
                  <span class="material-symbols-outlined text-processing">hub</span>
                  向量分块浏览 (Chunks)
                </h3>
                <span
                  class="font-body text-xs font-medium text-secondary bg-subtle custom-px-3 custom-py-1.5 rounded-xl"
                >共 {{ displayChunks.length }} 个分块</span>
              </div>
              <div class="custom-flex-1 custom-overflow-y-auto custom-p-4 space-y-3">
                <!-- 加载状态 -->
                <div v-if="loadingChunks" class="custom-flex custom-flex-col custom-items-center custom-justify-center custom-p-8">
                  <span class="material-symbols-outlined text-2xl text-brand custom-animate-spin custom-mb-2">refresh</span>
                  <p class="font-body text-sm text-secondary">加载分块数据中...</p>
                </div>
                <!-- 空状态 -->
                <div v-else-if="displayChunks.length === 0" class="custom-flex custom-flex-col custom-items-center custom-justify-center custom-p-8">
                  <span class="material-symbols-outlined text-3xl text-secondary custom-mb-2">hub</span>
                  <p class="font-headline font-medium text-primary">暂无分块数据</p>
                  <p class="font-body text-xs custom-mt-2 text-secondary custom-mb-4">
                    <template v-if="selectedDoc">
                      该文档尚未进行分块处理
                    </template>
                    <template v-else>
                      点击左侧文档列表中的文档查看分块
                    </template>
                  </p>
                  <button
                    v-if="selectedDoc"
                    class="custom-px-4 custom-py-2 font-body text-xs bg-gradient-to-r from-brand to-brand text-white hover-opacity-90 custom-transition-opacity rounded-full"
                    @click="triggerReprocess"
                  >
                    <span class="material-symbols-outlined text-sm custom-mr-1">refresh</span>
                    进行分块处理
                  </button>
                </div>
                <!-- Active Chunk -->
                <div
                  v-else
                  v-for="(chunk, index) in displayChunks"
                  :key="chunk.id"
                  :class="[
                    'custom-p-4 space-y-3 custom-relative custom-overflow-hidden custom-transition-all custom-duration-200 cursor-pointer rounded-xl',
                    selectedChunkIndex === index
                      ? 'bg-surface border-2 border-brand'
                      : 'bg-surface hover-bg-subtle'
                  ]"
                  @click="selectChunk(index)"
                >
                  <div class="custom-flex custom-items-center custom-justify-between">
                    <span class="font-body text-10px font-bold tracking-widest uppercase text-secondary">
                      ID: {{ chunk.id }}
                    </span>
                    <span class="font-body text-10px font-medium custom-px-2 custom-py-0.5 rounded-xl bg-subtle text-secondary">
                      分块 #{{ chunk.chunkIndex + 1 }}
                      <template v-if="chunk.similarity != null">
                        · 相似度: {{ chunk.similarity.toFixed(3) }}
                      </template>
                    </span>
                  </div>
                  <p class="font-body text-sm leading-relaxed text-secondary">
                    {{ chunk.content }}
                  </p>
                  <div v-if="selectedChunkIndex === index && chunk.tags && chunk.tags.length > 0" class="custom-flex custom-flex-wrap custom-gap-2 custom-pt-1">
                    <span
                      v-for="tag in chunk.tags"
                      :key="tag"
                      class="font-body text-10px bg-brand text-on-brand custom-px-2 custom-py-0.5 rounded-xl"
                    >
                      {{ tag }}
                    </span>
                  </div>
                  <!-- 选中指示器 -->
                  <div v-if="selectedChunkIndex === index" class="custom-absolute custom-right-3 custom-top-3">
                    <span class="material-symbols-outlined text-brand text-sm">check_circle</span>
                  </div>
                </div>
              </div>
              <!-- Chunk 操作栏 -->
              <div class="custom-p-4 bg-surface custom-flex custom-justify-between custom-items-center">
                <span class="font-body text-xs text-secondary">
                  <template v-if="displayChunks.length > 0 && selectedChunkIndex < displayChunks.length">
                    当前选中: {{ displayChunks[selectedChunkIndex].id }}
                    <template v-if="displayChunks[selectedChunkIndex].similarity != null">
                      (相似度: {{ displayChunks[selectedChunkIndex].similarity!.toFixed(3) }})
                    </template>
                  </template>
                  <template v-else>
                    当前选中: 无
                  </template>
                </span>
                <div class="custom-flex custom-gap-2">
                  <button
                    class="custom-px-4 custom-py-2 font-body text-xs bg-gradient-to-r from-brand to-brand text-white hover-opacity-90 custom-transition-opacity rounded-full"
                    @click="copyChunkContent"
                    :disabled="displayChunks.length === 0 || selectedChunkIndex >= displayChunks.length"
                  >
                    <span class="material-symbols-outlined text-sm custom-mr-1">content_copy</span>
                    复制
                  </button>
                  <button
                    class="custom-px-4 custom-py-2 font-body text-xs bg-processing/10 text-processing hover-bg-processing/20 custom-transition-colors rounded-full"
                    @click="semanticSearch"
                    :disabled="displayChunks.length === 0 || selectedChunkIndex >= displayChunks.length"
                  >
                    <span class="material-symbols-outlined text-sm custom-mr-1">psychology</span>
                    语义搜索
                  </button>
                </div>
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { documentService } from '@/api/services/document.service'
import { documentChunkService } from '@/api/services/document-chunk.service'
import type { Document as ApiDocument } from '@/api/types/document.types'
import type { DocumentChunk } from '@/api/types/document-chunk.types'

// 类型定义
interface Category {
  name: string
  count: number
}

interface UiDocument {
  id: string  // 使用字符串类型存储大整数ID，避免JavaScript精度损失
  name: string
  size: string
  category: string
  uploadTime: string
  status: 'completed' | 'processing' | 'pending'
  progress?: number
  type: 'pdf' | 'doc'
}

interface UiChunk extends DocumentChunk {
  similarity?: number  // 相似度（向量搜索时使用）
  tags?: string[]     // 标签（前端展示用）
}

// 用于显示的分块数据接口
interface DisplayChunk {
  id: string
  similarity?: number  // 相似度仅向量搜索时有值
  chunkIndex: number   // 分块序号
  content: string
  tags?: string[]
}

// 响应式状态
const searchQuery = ref('')
const selectedCategory = ref('')
const selectedDoc = ref<UiDocument | null>(null)
const selectedChunkIndex = ref(0)
const fileInput = ref<HTMLInputElement | null>(null)
const isDragging = ref(false)
const loading = ref(false)
const loadingChunks = ref(false)
const rawDocuments = ref<ApiDocument[]>([]) // 原始API文档数据
const documentChunks = ref<UiChunk[]>([]) // 文档分块数据
const displayChunks = ref<DisplayChunk[]>([]) // 用于显示的分块数据（转换后）

// 预览相关状态
const previewLoading = ref(false)      // 预览加载中
const previewError = ref(false)         // 预览加载失败
const previewText = ref('')             // 文本预览内容
const previewBlobUrl = ref('')          // PDF预览的Blob URL
const previewContainer = ref<HTMLElement | null>(null) // 预览容器引用

// 分类数据
const categories = ref<Category[]>([
  { name: '内科学 (Internal Medicine)', count: 124 },
  { name: '外科学 (Surgery)', count: 86 },
  { name: '儿科学 (Pediatrics)', count: 42 },
  { name: '影像诊断 (Imaging)', count: 59 }
])

// 文档数据
const documents = computed<UiDocument[]>(() => {
  return rawDocuments.value.map((doc: ApiDocument) => {
    // 将API文档转换为UI文档格式
    const fileName = doc.title || '未命名文档'
    const fileExtension = fileName.split('.').pop()?.toLowerCase()
    const type = fileExtension === 'pdf' ? 'pdf' : 'doc' // 简化处理，实际可根据扩展名判断

    // 映射状态
    let status: 'completed' | 'processing' | 'pending'
    switch (doc.status) {
      case 'COMPLETED':
        status = 'completed'
        break
      case 'PROCESSING':
        status = 'processing'
        break
      case 'UPLOADED':
        status = 'pending'
        break
      default:
        status = 'pending'
    }

    // 计算文件大小（暂时使用占位值）
    const size = '未知大小'

    return {
      id: doc.id.toString(), // 转换为字符串，避免大整数精度问题
      name: fileName,
      size,
      category: doc.category || '未分类',
      uploadTime: doc.createdAt ? new Date(doc.createdAt).toLocaleDateString('zh-CN') : '未知时间',
      status,
      progress: doc.status === 'PROCESSING' ? 50 : undefined, // 暂时使用占位值
      type
    }
  })
})

// 转换文档分块为显示格式
const convertToDisplayChunks = (chunks: UiChunk[]): DisplayChunk[] => {
  return chunks.map((chunk) => {
    // 从内容中提取可能的标签
    const tags = extractTagsFromContent(chunk.content)

    // 截取内容，避免显示过长
    const displayContent = chunk.content.length > 200
      ? chunk.content.substring(0, 200) + '...'
      : chunk.content

    return {
      id: `chunk-${chunk.id}`,
      similarity: chunk.similarity,  // 仅向量搜索时才有值，按文档浏览时为 undefined
      chunkIndex: chunk.chunkIndex,
      content: displayContent,
      tags
    }
  })
}

// 从内容中提取标签
const extractTagsFromContent = (content: string): string[] => {
  const tags: string[] = []
  const medicalKeywords = [
    '糖尿病', '胰岛素', 'HbA1c', '血糖', '治疗', '诊断',
    '药物', '并发症', '筛查', '生活方式', '二甲双胍'
  ]

  medicalKeywords.forEach(keyword => {
    if (content.includes(keyword)) {
      tags.push(keyword)
    }
  })

  return tags.slice(0, 3) // 最多显示3个标签
}

// 计算属性
const filteredDocuments = computed(() => {
  let result = documents.value

  // 分类过滤
  if (selectedCategory.value) {
    const categoryName = selectedCategory.value.split(' ')[0]
    result = result.filter(
      (doc) => doc.category === categoryName || selectedCategory.value.includes(doc.category)
    )
  }

  // 搜索过滤
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter((doc) => doc.name.toLowerCase().includes(query))
  }

  return result
})

// 方法
const selectCategory = (name: string) => {
  selectedCategory.value = name
  selectedDoc.value = null
}

const selectDocument = async (doc: UiDocument) => {
  selectedDoc.value = doc
  // 重置 chunk 选中
  selectedChunkIndex.value = 0

  // 获取该文档的分块数据
  await fetchDocumentChunks(doc.id)

  // 获取预览内容
  await fetchDocumentPreview(doc)
}

// 获取文档分块数据
const fetchDocumentChunks = async (documentId: string) => {
  loadingChunks.value = true
  try {
    const response = await documentChunkService.getChunksByDocument(documentId, 1, 100)
    if (response.success && response.data) {
      documentChunks.value = response.data.data
      // 转换为显示格式
      displayChunks.value = convertToDisplayChunks(response.data.data)

      // 如果没有分块数据，显示提示
      if (documentChunks.value.length === 0) {
        ElMessage.info('该文档暂无分块数据')
      }
    } else {
      ElMessage.error(response.message || '获取文档分块失败')
    }
  } catch (error) {
    console.error('获取文档分块失败:', error)
    ElMessage.error('获取文档分块失败')
  } finally {
    loadingChunks.value = false
  }
}

// 获取文档预览内容
const fetchDocumentPreview = async (doc: UiDocument) => {
  // 清理之前的预览资源
  if (previewBlobUrl.value) {
    URL.revokeObjectURL(previewBlobUrl.value)
    previewBlobUrl.value = ''
  }
  previewText.value = ''
  previewError.value = false
  previewLoading.value = true

  try {
    if (doc.type === 'pdf') {
      // PDF文件：获取二进制Blob，生成Blob URL用于iframe渲染
      const blob = await documentService.getPreviewBlob(doc.id)
      if (blob && blob.size > 0) {
        previewBlobUrl.value = URL.createObjectURL(blob)
      } else {
        throw new Error('获取PDF预览内容为空')
      }
    } else {
      // 其他格式：获取提取的纯文本
      const text = await documentService.getPreviewText(doc.id)
      if (text !== null) {
        previewText.value = text
      } else {
        throw new Error('获取文本预览失败')
      }
    }
  } catch (error) {
    console.error('获取文档预览失败:', error)
    previewError.value = true
  } finally {
    previewLoading.value = false
  }
}

// 复制分块内容
const copyChunkContent = () => {
  if (displayChunks.value.length === 0 || selectedChunkIndex.value >= displayChunks.value.length) {
    return
  }

  const selectedChunk = displayChunks.value[selectedChunkIndex.value]
  // 获取完整内容（如果documentChunks中有完整内容）
  let fullContent = selectedChunk.content
  if (documentChunks.value.length > selectedChunkIndex.value) {
    const originalChunk = documentChunks.value[selectedChunkIndex.value]
    fullContent = originalChunk.content
  }

  navigator.clipboard.writeText(fullContent)
    .then(() => {
      ElMessage.success('内容已复制到剪贴板')
    })
    .catch(err => {
      console.error('复制失败:', err)
      ElMessage.error('复制失败')
    })
}

// 语义搜索
const semanticSearch = () => {
  if (displayChunks.value.length === 0 || selectedChunkIndex.value >= displayChunks.value.length) {
    return
  }

  const selectedChunk = displayChunks.value[selectedChunkIndex.value]
  ElMessage.info(`语义搜索功能开发中，将搜索: "${selectedChunk.content.substring(0, 50)}..."`)
  // TODO: 实现语义搜索功能
}

// 触发重新处理文档
const triggerReprocess = () => {
  if (!selectedDoc.value) {
    return
  }

  // 调用handleDocAction中的reprocess逻辑
  handleDocAction('reprocess', selectedDoc.value)
}

const selectChunk = (index: number) => {
  selectedChunkIndex.value = index
}

const triggerFileUpload = () => {
  fileInput.value?.click()
}

const handleFileSelect = (event: Event) => {
  const target = event.target as HTMLInputElement
  const files = target.files
  if (files && files.length > 0) {
    uploadFiles(files)
  }
}

const onDragOver = () => {
  isDragging.value = true
}

const onDragLeave = () => {
  isDragging.value = false
}

const onDrop = (event: DragEvent) => {
  isDragging.value = false
  const files = event.dataTransfer?.files
  if (files && files.length > 0) {
    uploadFiles(files)
  }
}

const uploadFiles = async (files: FileList) => {
  for (let i = 0; i < files.length; i++) {
    const file = files[i]
    const validTypes = ['.pdf']
    const fileExtension = '.' + file.name.split('.').pop()?.toLowerCase()

    if (!validTypes.includes(fileExtension)) {
      ElMessage.error(`不支持 ${fileExtension} 格式的文件`)
      continue
    }

    if (file.size > 50 * 1024 * 1024) {
      ElMessage.error('文件大小不能超过 50MB')
      continue
    }

    try {
      ElMessage.info(`文件 ${file.name} 上传中...`)
      const response = await documentService.uploadDocument({
        file,
        title: file.name,
        category: selectedCategory.value.split(' ')[0] // 使用当前选中的分类
      })

      if (response.success) {
        ElMessage.success(`文件 ${file.name} 上传成功，处理中...`)
        // 刷新文档列表
        fetchDocuments()
      } else {
        ElMessage.error(`上传失败: ${response.message}`)
      }
    } catch (error) {
      console.error('上传文件失败:', error)
      ElMessage.error(`文件 ${file.name} 上传失败`)
    }
  }
}

const handleDocAction = (command: string, doc: UiDocument) => {
  switch (command) {
    case 'preview':
      selectDocument(doc)
      break
    case 'delete':
      // 确认删除
      ElMessageBox.confirm(
        `确定要删除文档 "${doc.name}" 吗？`,
        '确认删除',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }
      ).then(async () => {
        const response = await documentService.deleteDocument(doc.id)
        if (response.success) {
          ElMessage.success('删除成功')
          // 刷新文档列表
          fetchDocuments()
          // 如果删除的是当前选中的文档，清空选中
          if (selectedDoc.value?.id === doc.id) {
            selectedDoc.value = null
          }
        } else {
          ElMessage.error(`删除失败: ${response.message}`)
        }
      }).catch(() => {
        // 用户取消删除
      })
      break
    case 'reprocess':
      // 确认重新处理
      ElMessageBox.confirm(
        `确定要重新处理文档 "${doc.name}" 吗？这将重新进行文本提取、分块和向量化。`,
        '确认重新处理',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }
      ).then(async () => {
        ElMessage.info(`文档 ${doc.name} 重新处理中...`)
        const response = await documentService.reprocessDocument(doc.id)
        if (response.success) {
          ElMessage.success('文档重新处理请求已提交，处理中...')
          // 轮询文档状态直到处理完成
          const maxAttempts = 60  // 最多等待5分钟
          let attempts = 0
          const pollInterval = setInterval(async () => {
            attempts++
            try {
              // 刷新文档列表获取最新状态
              const listResponse = await documentService.getDocuments(1, 100)
              if (listResponse.success && listResponse.data) {
                rawDocuments.value = listResponse.data.data
                updateCategoriesFromDocuments()
                // 查找当前文档的最新状态
                const updatedDoc = listResponse.data.data.find((d: ApiDocument) => d.id === doc.id)
                if (updatedDoc) {
                  if (updatedDoc.status === 'COMPLETED') {
                    clearInterval(pollInterval)
                    ElMessage.success('文档处理完成')
                    // 如果当前选中的是这个文档，刷新其分块数据
                    if (selectedDoc.value?.id === doc.id) {
                      fetchDocumentChunks(doc.id)
                    }
                  } else if (updatedDoc.status === 'FAILED') {
                    clearInterval(pollInterval)
                    ElMessage.error('文档处理失败，请重试')
                    if (selectedDoc.value?.id === doc.id) {
                      fetchDocumentChunks(doc.id)
                    }
                  }
                  // PROCESSING / UPLOADED 状态继续等待
                }
              }
            } catch (e) {
              console.error('轮询文档状态失败:', e)
            }
            if (attempts >= maxAttempts) {
              clearInterval(pollInterval)
              ElMessage.warning('文档处理超时，请刷新页面查看状态')
            }
          }, 5000)  // 每5秒轮询一次
        } else {
          ElMessage.error(`重新处理失败: ${response.message}`)
        }
      }).catch(() => {
        // 用户取消
      })
      break
  }
}

// 获取文档列表
const fetchDocuments = async () => {
  loading.value = true
  try {
    const response = await documentService.getDocuments(1, 100)
    if (response.success && response.data) {
      rawDocuments.value = response.data.data
      // 更新分类数据
      updateCategoriesFromDocuments()
    } else {
      ElMessage.error(response.message || '获取文档列表失败')
    }
  } catch (error) {
    console.error('获取文档列表失败:', error)
    ElMessage.error('获取文档列表失败')
  } finally {
    loading.value = false
  }
}

// 从文档数据更新分类
const updateCategoriesFromDocuments = () => {
  const categoryCounts: Record<string, number> = {}
  rawDocuments.value.forEach(doc => {
    if (doc.category) {
      categoryCounts[doc.category] = (categoryCounts[doc.category] || 0) + 1
    }
  })

  // 转换分类数据格式
  categories.value = Object.entries(categoryCounts).map(([name, count]) => ({
    name,
    count
  }))

  // 如果分类数据为空，使用默认分类
  if (categories.value.length === 0) {
    categories.value = [
      { name: '内科学 (Internal Medicine)', count: 0 },
      { name: '外科学 (Surgery)', count: 0 },
      { name: '儿科学 (Pediatrics)', count: 0 },
      { name: '影像诊断 (Imaging)', count: 0 }
    ]
  }

  // 如果当前没有选中分类，选中第一个分类
  if (!selectedCategory.value && categories.value.length > 0) {
    selectedCategory.value = categories.value[0].name
  }
}

// 组件挂载时获取数据
onMounted(() => {
  fetchDocuments()
})

// 组件卸载时清理预览资源
onUnmounted(() => {
  if (previewBlobUrl.value) {
    URL.revokeObjectURL(previewBlobUrl.value)
  }
})
</script>

<style scoped>
/* 导入 Google Fonts: Manrope 和 Inter（@import 必须在最前面） */
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Manrope:wght@400;500;600;700;800&display=swap');

/* Material Symbols Outlined 字体设置 */
.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}
</style>
