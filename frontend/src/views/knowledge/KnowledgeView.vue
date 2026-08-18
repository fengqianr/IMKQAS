<template>
  <div class="knowledge-page">
    <!-- 文档管理标签页（顶部：上传+分类 左右；底部：文档列表在上、内容区在下） -->
    <div v-if="activeTab === 'documents'" class="kb-page">
      <!-- 顶部行：上传区 + 知识库分类（左右布局） -->
      <section class="kb-top-row">
        <!-- 上传区 -->
        <div
          class="kb-upload"
          :class="{ 'kb-upload-active': isDragging }"
          @click="triggerFileUpload"
          @dragover.prevent="onDragOver"
          @dragleave.prevent="onDragLeave"
          @drop.prevent="onDrop"
        >
          <span class="material-symbols-outlined kb-upload-icon">upload_file</span>
          <div class="kb-upload-text">
            <p class="kb-upload-title">拖拽 PDF 到此处</p>
            <p class="kb-upload-hint">或点击选择文件（≤50MB）</p>
          </div>
          <input
            ref="fileInput"
            type="file"
            class="custom-hidden"
            accept=".pdf"
            multiple
            @change="handleFileSelect"
          />
        </div>

        <!-- 分类（chips 横向排列） -->
        <div class="kb-categories">
          <h3 class="kb-section-title">知识库分类</h3>
          <div class="kb-category-nav">
            <!-- 全部文档 -->
            <button
              class="kb-category-item"
              :class="{ 'kb-category-active': !selectedCategory }"
              @click="selectCategory('')"
            >
              <span class="kb-cat-label">全部文档</span>
              <span class="kb-cat-count">{{ rawDocuments.length }}</span>
            </button>
            <!-- 分类列表（由文档数据派生） -->
            <button
              v-for="cat in categories"
              :key="cat.name"
              class="kb-category-item"
              :class="{ 'kb-category-active': selectedCategory === cat.name }"
              @click="selectCategory(cat.name)"
            >
              <span class="kb-cat-label">{{ cat.name }}</span>
              <span class="kb-cat-count">{{ cat.count }}</span>
            </button>
          </div>
        </div>
      </section>

      <!-- 底部行：文档列表在上，内容区（预览+分块）在下，垂直堆叠 -->
      <section class="kb-bottom-row">
        <!-- 中栏：文档列表 -->
        <div class="kb-middle">
          <div class="kb-panel-header">
            <div class="kb-header-left">
              <button class="kb-icon-btn" title="返回列表" @click="backToList">
                <span class="material-symbols-outlined">arrow_back</span>
              </button>
              <h2 class="kb-panel-title">文档列表</h2>
            </div>
            <div class="kb-header-right">
              <div class="kb-search">
                <span class="material-symbols-outlined kb-search-icon">search</span>
                <input v-model="searchQuery" class="kb-search-input" placeholder="搜索文档..." type="text" />
              </div>
            </div>
          </div>

          <div class="kb-table-wrap">
            <table class="kb-table">
              <thead>
                <tr>
                  <th>文档名称</th>
                  <th class="kb-th-category">分类</th>
                  <th class="kb-th-status">状态</th>
                  <th class="kb-th-action">操作</th>
                </tr>
              </thead>
              <tbody>
                <!-- 加载状态 -->
                <tr v-if="loading">
                  <td colspan="4" class="kb-empty-cell">
                    <div class="kb-empty">
                      <span class="material-symbols-outlined kb-empty-icon">refresh</span>
                      <p>加载文档列表中...</p>
                    </div>
                  </td>
                </tr>
                <!-- 空状态 -->
                <tr v-else-if="filteredDocuments.length === 0">
                  <td colspan="4" class="kb-empty-cell">
                    <div class="kb-empty">
                      <span class="material-symbols-outlined kb-empty-icon">description</span>
                      <p>暂无匹配的文档</p>
                      <p class="kb-empty-hint">试试清除搜索条件或更换分类</p>
                    </div>
                  </td>
                </tr>
                <!-- 文档列表 -->
                <tr
                  v-for="doc in filteredDocuments.slice(0, 10)"
                  :key="doc.id"
                  class="kb-row"
                  :class="{ 'kb-row-selected': selectedDoc?.id === doc.id }"
                  @click="selectDocument(doc)"
                >
                  <td>
                    <div class="kb-doc-name">
                      <span class="material-symbols-outlined kb-doc-icon">picture_as_pdf</span>
                      <div class="kb-doc-info">
                        <span class="kb-doc-title">{{ doc.name }}</span>
                        <span class="kb-doc-meta">{{ doc.size }} • {{ doc.uploadTime }}</span>
                      </div>
                    </div>
                  </td>
                  <td>
                    <span class="kb-cat-pill">{{ doc.category }}</span>
                  </td>
                  <td>
                    <StatusBadge
                      :tone="doc.status === 'completed' ? 'success' : doc.status === 'processing' ? 'warning' : 'neutral'"
                      dot
                      :pulse="doc.status === 'processing'"
                    >
                      <template v-if="doc.status === 'completed'">已完成</template>
                      <template v-else-if="doc.status === 'processing'">处理中 ({{ doc.progress }}%)</template>
                      <template v-else>待处理</template>
                    </StatusBadge>
                  </td>
                  <td>
                    <el-dropdown trigger="click" @command="(cmd: string) => handleDocAction(cmd, doc)">
                      <button class="kb-more-btn" @click.stop>
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
        </div>

        <!-- 右栏：预览 + 分块 -->
        <aside class="kb-right-panel">
          <!-- 预览头部 -->
          <div class="kb-preview-header">
            <div class="kb-preview-info">
              <h3 class="kb-preview-title">{{ selectedDoc?.name || '请选择文档' }}</h3>
              <p v-if="selectedDoc" class="kb-preview-meta">分块数: {{ displayChunks.length }}</p>
            </div>
            <div class="kb-preview-actions">
              <button
                class="kb-icon-btn kb-icon-btn-bordered"
                title="下载"
                :disabled="!selectedDoc"
                @click="downloadDocument"
              >
                <span class="material-symbols-outlined">download</span>
              </button>
            </div>
          </div>

          <!-- 文档源视图 -->
          <div class="kb-source">
            <div class="kb-sub-header">
              <span>文档源视图</span>
              <div class="kb-sub-actions">
                <button class="kb-mini-btn" title="缩小" @click="zoomOut">
                  <span class="material-symbols-outlined">zoom_out</span>
                </button>
                <span class="kb-zoom-label">{{ zoomPercent }}%</span>
                <button class="kb-mini-btn" title="放大" @click="zoomIn">
                  <span class="material-symbols-outlined">zoom_in</span>
                </button>
              </div>
            </div>
            <div class="kb-source-body">
              <!-- 加载状态 -->
              <div v-if="selectedDoc && previewLoading" class="kb-source-state">
                <span class="material-symbols-outlined kb-source-icon">refresh</span>
                <p>加载文档预览中...</p>
              </div>
              <!-- 错误状态 -->
              <div v-else-if="selectedDoc && previewError" class="kb-source-state">
                <span class="material-symbols-outlined kb-source-icon">error_outline</span>
                <p>预览加载失败</p>
                <button class="kb-retry-btn" @click="fetchDocumentPreview(selectedDoc)">
                  <span class="material-symbols-outlined kb-retry-icon">refresh</span>
                  重试
                </button>
              </div>
              <!-- PDF预览 -->
              <div v-else-if="selectedDoc && selectedDoc.type === 'pdf' && previewBlobUrl" class="kb-source-frame">
                <iframe ref="previewContainer" :src="previewBlobUrl" class="kb-iframe"></iframe>
              </div>
              <!-- 文本预览 -->
              <div v-else-if="selectedDoc && previewText" class="kb-source-frame">
                <pre class="kb-pre">{{ previewText }}</pre>
              </div>
              <!-- 未选择文档 -->
              <div v-else class="kb-source-state">
                <span class="material-symbols-outlined kb-source-icon">description</span>
                <p>请选择一个文档进行预览</p>
                <p class="kb-empty-hint">点击中栏文档列表中的文档</p>
              </div>
            </div>
          </div>

          <!-- 向量分块浏览 -->
          <div class="kb-chunks">
            <div class="kb-sub-header">
              <span>向量分块浏览 (Chunks)</span>
              <span class="kb-chunks-total">Total: {{ displayChunks.length }}</span>
            </div>
            <div class="kb-chunks-list">
              <!-- 加载状态 -->
              <div v-if="loadingChunks" class="kb-chunks-state">
                <span class="material-symbols-outlined kb-source-icon">refresh</span>
                <p>加载分块数据中...</p>
              </div>
              <!-- 空状态 -->
              <div v-else-if="displayChunks.length === 0" class="kb-chunks-state">
                <span class="material-symbols-outlined kb-source-icon">hub</span>
                <p v-if="selectedDoc">该文档尚未进行分块处理</p>
                <p v-else>点击中栏文档列表中的文档查看分块</p>
                <button v-if="selectedDoc" class="kb-retry-btn" @click="triggerReprocess">
                  <span class="material-symbols-outlined kb-retry-icon">refresh</span>
                  进行分块处理
                </button>
              </div>
              <!-- 分块卡片 -->
              <div
                v-else
                v-for="(chunk, index) in displayChunks"
                :key="chunk.id"
                class="kb-chunk-card"
                :class="{ 'kb-chunk-selected': selectedChunkIndex === index }"
                @click="selectChunk(index)"
              >
                <div class="kb-chunk-head">
                  <span class="kb-chunk-id">分块 #{{ chunk.chunkIndex + 1 }}</span>
                  <template v-if="chunk.similarity != null">
                    <span class="kb-chunk-sim">相似度: {{ chunk.similarity.toFixed(3) }}</span>
                  </template>
                </div>
                <p class="kb-chunk-content">{{ chunk.content }}</p>
                <div v-if="selectedChunkIndex === index && chunk.tags && chunk.tags.length > 0" class="kb-chunk-tags">
                  <span v-for="tag in chunk.tags" :key="tag" class="kb-chunk-tag">{{ tag }}</span>
                </div>
              </div>
            </div>
            <!-- 分块操作栏 -->
            <div class="kb-chunks-footer">
              <span class="kb-chunk-selected-info">
                <template v-if="canOperateChunk">
                  当前选中: {{ displayChunks[selectedChunkIndex].id }}
                  <template v-if="displayChunks[selectedChunkIndex].similarity != null">
                    (相似度: {{ displayChunks[selectedChunkIndex].similarity!.toFixed(3) }})
                  </template>
                </template>
                <template v-else>当前选中: 无</template>
              </span>
              <div class="kb-footer-actions">
                <button class="kb-action-btn kb-action-primary" :disabled="!canOperateChunk" @click="copyChunkContent">
                  <span class="material-symbols-outlined">content_copy</span>
                  复制
                </button>
                <button class="kb-action-btn kb-action-secondary" :disabled="!canOperateChunk" @click="semanticSearch">
                  <span class="material-symbols-outlined">psychology</span>
                  语义搜索
                </button>
              </div>
            </div>
          </div>
        </aside>
      </section>
    </div>

    <!-- 禁忌规则标签页 -->
    <ContraindicationRules v-if="activeTab === 'contraindications'" />

    <!-- 词条审核标签页 -->
    <TermReview v-if="activeTab === 'termReview'" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiErrorMessage } from '@/utils/error'
import { downloadBlob } from '@/utils/format'
import { documentService } from '@/api/services/document.service'
import { documentChunkService } from '@/api/services/document-chunk.service'
import type { Document as ApiDocument } from '@/api/types/document'
import type { DocumentChunk } from '@/api/types/document-chunk'
import ContraindicationRules from '@/views/clinical/ContraindicationRules.vue'
import TermReview from '@/views/clinical/TermReview.vue'
import StatusBadge from '@/components/StatusBadge.vue'

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

// 预览缩放比例（视觉占位，缩放按钮暂不改变 iframe 内容）
const zoomPercent = ref(100)

// 预览相关状态
const previewLoading = ref(false)      // 预览加载中
const previewError = ref(false)         // 预览加载失败
const previewText = ref('')             // 文本预览内容
const previewBlobUrl = ref('')          // PDF预览的Blob URL
const previewContainer = ref<HTMLElement | null>(null) // 预览容器引用

// 标签页切换
const activeTab = ref<'documents' | 'contraindications' | 'termReview'>('documents')

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

  // 分类过滤（空值表示全部文档）
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

// 分块操作可用性（有分块且选中项有效）
const canOperateChunk = computed(() => {
  return displayChunks.value.length > 0 && selectedChunkIndex.value < displayChunks.value.length
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

// 返回列表：清空选中，预览区回到未选择状态
const backToList = () => {
  selectedDoc.value = null
  selectedChunkIndex.value = 0
  displayChunks.value = []
  documentChunks.value = []
  if (previewBlobUrl.value) {
    URL.revokeObjectURL(previewBlobUrl.value)
    previewBlobUrl.value = ''
  }
  previewText.value = ''
  previewError.value = false
}

// 缩放（视觉占位）
const zoomIn = () => {
  zoomPercent.value = Math.min(200, zoomPercent.value + 10)
}
const zoomOut = () => {
  zoomPercent.value = Math.max(50, zoomPercent.value - 10)
}

// 下载当前文档（复用预览 Blob 接口）
const downloadDocument = async () => {
  if (!selectedDoc.value) return
  const doc = selectedDoc.value
  const blob = await documentService.getPreviewBlob(doc.id)
  if (!blob || blob.size === 0) {
    ElMessage.error('预览文件不可用，无法下载')
    return
  }
  downloadBlob(blob, doc.name)
  ElMessage.success('已开始下载')
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
      ElMessage.error(apiErrorMessage(response, '获取文档分块失败'))
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
  if (!canOperateChunk.value) {
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
  if (!canOperateChunk.value) {
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
        ElMessage.error(`上传失败: ${apiErrorMessage(response)}`)
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
          ElMessage.error(`删除失败: ${apiErrorMessage(response)}`)
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
          ElMessage.error(`重新处理失败: ${apiErrorMessage(response)}`)
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
      ElMessage.error(apiErrorMessage(response, '获取文档列表失败'))
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

/* ===== 页面整体（顶部 + 底部两行；内容自适应，不足一屏时占满，超出时由外层滚动） ===== */
.kb-page {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  min-height: max(calc(100vh - 11rem), 520px); /* 顶栏 4rem + 底部 3rem + 内容区上下内边距 4rem */
}

/* ===== 顶部行：上传 + 分类 ===== */
.kb-top-row {
  display: flex;
  gap: 1.25rem;
  align-items: stretch;
  flex-shrink: 0;
}

/* 上传区：横向虚线框 */
.kb-upload {
  flex: 0 0 300px;
  height: 8.5rem;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  border: 2px dashed var(--theme-outline-variant);
  border-radius: 0.5rem;
  background: #ffffff;
  cursor: pointer;
  transition: all 0.15s ease;
}

.kb-upload:hover,
.kb-upload-active {
  border-color: var(--theme-primary);
  background: #e5eeff;
}

.kb-upload-icon {
  font-size: 2.5rem;
  color: var(--theme-primary);
  flex-shrink: 0;
}

.kb-upload-text {
  text-align: left;
}

.kb-upload-title {
  font-size: 0.9375rem;
  font-weight: 600;
  color: #0b1c30;
  margin: 0;
}

.kb-upload-hint {
  font-size: 0.8125rem;
  color: #424752;
  margin: 0.25rem 0 0;
}

/* 分类卡片 */
.kb-categories {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 1.5rem;
  padding: 1rem 1.25rem;
  background: #ffffff;
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.5rem;
  overflow: hidden;
}

.kb-section-title {
  flex-shrink: 0;
  font-size: 0.6875rem;
  font-weight: 700;
  color: var(--theme-on-surface-variant);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin: 0;
}

.kb-category-nav {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

/* 分类项（chip 样式） */
.kb-category-item {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.375rem 0.875rem;
  border-radius: 9999px;
  border: 1px solid var(--theme-outline-variant);
  background: var(--theme-surface);
  font-size: 0.8125rem;
  color: #0b1c30;
  cursor: pointer;
  transition: all 0.15s ease;
  white-space: nowrap;
}

.kb-category-item:hover {
  background: #f1f5f9;
  border-color: var(--theme-outline);
}

.kb-category-active {
  background: rgba(0, 71, 141, 0.10);
  border-color: var(--theme-primary);
  color: var(--theme-primary);
  font-weight: 600;
}

.kb-cat-count {
  flex-shrink: 0;
  font-size: 0.6875rem;
  min-width: 1.125rem;
  height: 1.125rem;
  padding: 0 0.25rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 9999px;
  background: var(--theme-surface-container);
  color: #424752;
}

.kb-category-active .kb-cat-count {
  background: rgba(0, 71, 141, 0.15);
  color: var(--theme-primary);
}

/* ===== 底部行：文档列表 + 内容区（上下垂直堆叠，内容自然展开） ===== */
.kb-bottom-row {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  align-items: stretch;
}

/* 文档列表区：固定高度，表格内部滚动 */
.kb-middle {
  flex: none;
  height: 18rem;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.5rem;
  overflow: hidden;
}

.kb-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 1rem 1.25rem;
  border-bottom: 1px solid var(--theme-outline-variant);
  flex-shrink: 0;
}

.kb-header-left,
.kb-header-right {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.kb-panel-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: #0b1c30;
  margin: 0;
}

.kb-icon-btn {
  width: 2.25rem;
  height: 2.25rem;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 0.5rem;
  border: none;
  background: transparent;
  color: #424752;
  cursor: pointer;
  transition: all 0.15s ease;
}

.kb-icon-btn:hover {
  background: var(--theme-surface-container);
  color: var(--theme-primary);
}

.kb-icon-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.kb-icon-btn-bordered {
  border: 1px solid var(--theme-outline-variant);
}

.kb-search {
  position: relative;
}

.kb-search-icon {
  position: absolute;
  left: 0.75rem;
  top: 50%;
  transform: translateY(-50%);
  font-size: 1rem;
  color: var(--theme-outline);
  pointer-events: none;
}

.kb-search-input {
  width: 16rem;
  padding: 0.5rem 0.75rem 0.5rem 2.25rem;
  border-radius: 0.5rem;
  border: 1px solid var(--theme-outline-variant);
  background: var(--theme-surface);
  font-size: 0.875rem;
  color: #0b1c30;
  outline: none;
  transition: all 0.15s ease;
}

.kb-search-input:focus {
  border-color: var(--theme-primary);
  box-shadow: 0 0 0 1px var(--theme-primary);
}

/* 表格 */
.kb-table-wrap {
  flex: 1;
  overflow-y: auto;
}

.kb-table {
  width: 100%;
  text-align: left;
  border-collapse: collapse;
}

.kb-table th {
  position: sticky;
  top: 0;
  z-index: 1;
  padding: 0.875rem 1.25rem;
  font-size: 0.75rem;
  font-weight: 600;
  color: #424752;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  background: var(--theme-surface-container-low);
  border-bottom: 1px solid var(--theme-outline-variant);
}

.kb-th-category,
.kb-th-status {
  width: 7rem;
}

.kb-th-action {
  width: 4rem;
}

.kb-table td {
  padding: 1rem 1.25rem;
  font-size: 0.8125rem;
  border-bottom: 1px solid #e2e8f0;
}

.kb-row {
  cursor: pointer;
  border-left: 4px solid transparent;
  transition: background 0.15s ease;
}

.kb-row:hover {
  background: #f1f5f9;
}

.kb-row-selected {
  background: rgba(0, 71, 141, 0.06);
  border-left-color: var(--theme-primary);
}

.kb-doc-name {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  min-width: 0;
}

.kb-doc-icon {
  font-size: 1.375rem;
  color: #ba1a1a;
  flex-shrink: 0;
}

.kb-doc-info {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.kb-doc-title {
  font-size: 0.875rem;
  font-weight: 600;
  color: #0b1c30;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kb-doc-meta {
  font-size: 0.75rem;
  color: #424752;
}

.kb-cat-pill {
  display: inline-block;
  font-size: 0.75rem;
  padding: 0.25rem 0.5rem;
  border-radius: 0.25rem;
  background: var(--theme-surface-container);
  color: #424752;
}

.kb-more-btn {
  border: none;
  background: transparent;
  color: #424752;
  cursor: pointer;
  padding: 0.25rem;
  border-radius: 0.25rem;
  transition: all 0.15s ease;
}

.kb-more-btn:hover {
  color: var(--theme-primary);
  background: #f1f5f9;
}

/* 表格空态 */
.kb-empty-cell {
  padding: 3rem 1.25rem !important;
}

.kb-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.375rem;
  text-align: center;
  color: #424752;
}

.kb-empty-icon {
  font-size: 2rem;
  color: var(--theme-outline);
  margin-bottom: 0.25rem;
}

.kb-empty-hint {
  font-size: 0.75rem;
  color: var(--theme-outline);
  margin: 0;
}

/* ===== 内容区：预览 + 分块（随内容自然展开，完整可见） ===== */
.kb-right-panel {
  flex: none;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.kb-preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 1rem 1.25rem;
  background: #ffffff;
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.5rem;
  flex-shrink: 0;
}

.kb-preview-info {
  min-width: 0;
}

.kb-preview-title {
  font-size: 1rem;
  font-weight: 600;
  color: #0b1c30;
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kb-preview-meta {
  font-size: 0.75rem;
  color: #424752;
  margin: 0.25rem 0 0;
}

.kb-preview-actions {
  display: flex;
  gap: 0.5rem;
  flex-shrink: 0;
}

/* 子面板（源视图 / 分块） */
.kb-source,
.kb-chunks {
  background: #ffffff;
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.5rem;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.kb-source {
  height: 14rem;
  flex-shrink: 0;
}

.kb-chunks {
  flex: none;
}

.kb-sub-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.5rem 0.75rem;
  background: var(--theme-surface-container-low);
  border-bottom: 1px solid var(--theme-outline-variant);
  font-size: 0.8125rem;
  color: #424752;
  flex-shrink: 0;
}

.kb-sub-actions {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.kb-mini-btn {
  border: none;
  background: transparent;
  color: #424752;
  cursor: pointer;
  padding: 0.25rem;
  border-radius: 0.25rem;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s ease;
}

.kb-mini-btn:hover {
  color: var(--theme-primary);
}

.kb-mini-btn .material-symbols-outlined {
  font-size: 1rem;
}

.kb-zoom-label {
  font-size: 0.75rem;
  color: #424752;
}

.kb-chunks-total {
  font-size: 0.75rem;
  padding: 0.125rem 0.5rem;
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.25rem;
  color: #424752;
}

/* 源视图内容 */
.kb-source-body {
  flex: 1;
  padding: 0.75rem;
  overflow-y: auto;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8fafc;
}

.kb-source-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.375rem;
  text-align: center;
  color: #424752;
}

.kb-source-icon {
  font-size: 1.75rem;
  color: var(--theme-outline);
  margin-bottom: 0.25rem;
}

.kb-retry-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  margin-top: 0.5rem;
  padding: 0.375rem 0.75rem;
  border-radius: 9999px;
  border: none;
  background: var(--theme-primary);
  color: #ffffff;
  font-size: 0.75rem;
  cursor: pointer;
  transition: background 0.15s ease;
}

.kb-retry-btn:hover {
  background: #00386f;
}

.kb-retry-icon {
  font-size: 0.875rem;
}

.kb-source-frame {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.kb-iframe {
  width: 100%;
  height: 100%;
  border: none;
  border-radius: 0.25rem;
  background: #ffffff;
}

.kb-pre {
  width: 100%;
  height: 100%;
  overflow-y: auto;
  font-size: 0.75rem;
  line-height: 1.6;
  color: #424752;
  background: var(--theme-surface-container-low);
  border-radius: 0.25rem;
  padding: 0.75rem;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: Inter, sans-serif;
  margin: 0;
}

/* 分块列表 */
.kb-chunks-list {
  flex: 1;
  overflow-y: auto;
  max-height: 26rem; /* 分块较多时列表内部滚动，避免把页面撑得过长 */
  padding: 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.625rem;
}

.kb-chunks-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.375rem;
  padding: 1.5rem 0;
  text-align: center;
  color: #424752;
}

.kb-chunk-card {
  padding: 0.75rem;
  border-radius: 0.5rem;
  border: 1px solid var(--theme-outline-variant);
  background: var(--theme-surface);
  cursor: pointer;
  transition: all 0.15s ease;
}

.kb-chunk-card:hover {
  background: #f1f5f9;
}

.kb-chunk-selected {
  border-color: var(--theme-primary);
  background: rgba(0, 71, 141, 0.06);
}

.kb-chunk-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  margin-bottom: 0.375rem;
}

.kb-chunk-id {
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.03em;
  color: var(--theme-primary);
  text-transform: uppercase;
}

.kb-chunk-sim {
  font-size: 0.6875rem;
  color: #424752;
  padding: 0.125rem 0.375rem;
  border-radius: 0.25rem;
  background: var(--theme-surface-container);
}

.kb-chunk-content {
  font-size: 0.8125rem;
  line-height: 1.6;
  color: #424752;
  margin: 0;
}

.kb-chunk-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
  margin-top: 0.5rem;
}

.kb-chunk-tag {
  font-size: 0.6875rem;
  padding: 0.125rem 0.5rem;
  border-radius: 9999px;
  background: #d6e3ff;
  color: var(--theme-primary);
}

/* 分块操作栏 */
.kb-chunks-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.75rem;
  border-top: 1px solid var(--theme-outline-variant);
  flex-shrink: 0;
}

.kb-chunk-selected-info {
  font-size: 0.75rem;
  color: var(--theme-outline);
}

.kb-footer-actions {
  display: flex;
  gap: 0.5rem;
}

.kb-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.375rem 0.75rem;
  border-radius: 9999px;
  font-size: 0.75rem;
  cursor: pointer;
  transition: all 0.15s ease;
}

.kb-action-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.kb-action-btn .material-symbols-outlined {
  font-size: 0.875rem;
}

.kb-action-primary {
  border: none;
  background: var(--theme-primary);
  color: #ffffff;
}

.kb-action-primary:hover:not(:disabled) {
  background: #00386f;
}

.kb-action-secondary {
  border: none;
  background: rgba(237, 108, 2, 0.12);
  color: var(--theme-processing);
}

.kb-action-secondary:hover:not(:disabled) {
  background: rgba(237, 108, 2, 0.20);
}

/* ===== 响应式：窄屏时顶部行（上传+分类）上下堆叠 ===== */
@media (max-width: 1100px) {
  .kb-top-row {
    flex-direction: column;
  }
  .kb-upload {
    flex: none;
    height: 6.5rem;
  }
  .kb-middle {
    height: 16rem;
  }
  .kb-chunks-list {
    max-height: 20rem;
  }
}

/* Material Symbols 字体设置 */
.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}
</style>
