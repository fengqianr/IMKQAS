# IMKQAS 前端设计方案

## 项目概述

IMKQAS（医疗知识智能问答系统）前端是基于 Vue 3 的现代化 Web 应用，为医疗问答、知识库管理、用户管理和数据可视化提供完整的用户界面。

## 技术栈选择

### 核心框架
- **Vue 3** (Composition API) - 主框架，提供响应式开发体验
- **TypeScript 5.0+** - 类型安全，提升代码质量
- **Vite 5.0+** - 现代化构建工具，快速开发体验

### UI 组件库
- **Element Plus 2.4+** - 基于 Vue 3 的组件库，提供丰富的UI组件
- **ECharts 5.4+** - 数据可视化图表库
- **Iconify** - 图标库，支持多种图标集

### 状态管理
- **Pinia 2.1+** - Vue 官方推荐的状态管理库
- **Vue Router 4** - 路由管理
- **VueUse 10.0+** - Vue 组合式API实用工具集

### 网络请求
- **Axios 1.6+** - HTTP 客户端
- **EventSource API** - SSE（Server-Sent Events）流式通信

### 开发工具
- **ESLint + Prettier** - 代码规范和格式化
- **Stylelint** - CSS/SCSS 代码规范
- **Husky + lint-staged** - Git 提交前检查
- **Vitest + Vue Test Utils** - 单元测试框架

## 项目结构

```
imkqas-frontend/
├── public/                    # 静态资源
├── src/
│   ├── api/                  # API 服务层
│   │   ├── services/         # 各模块API服务
│   │   ├── types/            # API 类型定义
│   │   ├── interceptors/     # 请求拦截器
│   │   └── index.ts          # API 入口
│   ├── assets/               # 资源文件
│   │   ├── styles/           # 全局样式
│   │   └── images/           # 图片资源
│   ├── components/           # 通用组件
│   │   ├── common/           # 基础通用组件
│   │   ├── layout/           # 布局组件
│   │   ├── ui/               # UI 组件
│   │   └── charts/           # 图表组件
│   ├── composables/          # 组合式函数
│   ├── layouts/              # 页面布局
│   │   ├── DefaultLayout.vue # 默认布局
│   │   └── AuthLayout.vue    # 认证布局
│   ├── pages/                # 页面组件
│   │   ├── auth/             # 认证页面
│   │   ├── dashboard/        # 仪表盘页面
│   │   ├── qa/               # 问答页面
│   │   ├── knowledge/        # 知识库页面
│   │   ├── user/             # 用户管理页面
│   │   └── settings/         # 设置页面
│   ├── router/               # 路由配置
│   │   ├── index.ts          # 路由入口
│   │   ├── guards.ts         # 路由守卫
│   │   └── routes/           # 路由配置
│   ├── stores/               # Pinia 状态管理
│   │   ├── auth.store.ts     # 认证状态
│   │   ├── user.store.ts     # 用户状态
│   │   ├── qa.store.ts       # 问答状态
│   │   ├── knowledge.store.ts # 知识库状态
│   │   └── stats.store.ts    # 统计状态
│   ├── types/                # TypeScript 类型定义
│   ├── utils/                # 工具函数
│   ├── App.vue               # 根组件
│   └── main.ts               # 应用入口
├── .env                      # 环境变量
├── .env.development          # 开发环境变量
├── .env.production           # 生产环境变量
├── vite.config.ts            # Vite 配置
├── tsconfig.json             # TypeScript 配置
└── package.json              # 项目依赖
```

## API 服务层设计

### API 基础配置

```typescript
// src/api/config.ts
export const API_CONFIG = {
  BASE_URL: import.meta.env.VITE_API_BASE_URL || '/api',
  TIMEOUT: 30000,
  SSE_TIMEOUT: 60000,
  RETRY_COUNT: 3,
  RETRY_DELAY: 1000,
};

// 认证配置
export const AUTH_CONFIG = {
  TOKEN_KEY: 'imkqas_token',
  REFRESH_TOKEN_KEY: 'imkqas_refresh_token',
  TOKEN_PREFIX: 'Bearer ',
  TOKEN_REFRESH_THRESHOLD: 300000, // 5分钟前刷新
};
```

### API 端点映射表

| 模块 | 端点 | 方法 | 描述 | 权限要求 |
|------|------|------|------|----------|
| **认证管理** | `/api/auth/send-code` | POST | 发送验证码 | 无 |
| | `/api/auth/login` | POST | 验证码登录 | 无 |
| | `/api/auth/refresh` | POST | 刷新令牌 | 需要令牌 |
| | `/api/auth/logout` | POST | 用户登出 | 需要令牌 |
| | `/api/auth/validate` | GET | 验证令牌 | 需要令牌 |
| | `/api/auth/me` | GET | 获取当前用户 | 需要令牌 |
| | `/api/auth/register` | POST | 用户注册 | 无 |
| **问答服务** | `/api/qa/stream` | POST | 流式问答（SSE） | 需要令牌 |
| | `/api/qa/ask` | POST | 同步问答 | 需要令牌 |
| | `/api/qa/triage` | POST | 科室导诊 | 需要令牌 |
| | `/api/qa/drug` | GET | 药物查询 | 需要令牌 |
| | `/api/qa/health` | GET | 健康检查 | 无 |
| **科室导诊** | `/api/triage` | POST | 单次症状分流 | 需要令牌 |
| | `/api/triage/batch` | POST | 批量症状分流 | 需要令牌 |
| | `/api/triage/stats` | GET | 服务统计信息 | 需要令牌 |
| | `/api/triage/health` | GET | 健康检查 | 无 |
| **药物查询** | `/api/drugs/search` | GET | 搜索药品 | 需要令牌 |
| | `/api/drugs/{id}` | GET | 获取药品详情 | 需要令牌 |
| | `/api/drugs/interactions` | GET | 检查相互作用 | 需要令牌 |
| | `/api/drugs/interactions/batch` | POST | 批量检查相互作用 | 需要令牌 |
| | `/api/drugs/classes` | GET | 获取药品分类 | 需要令牌 |
| | `/api/drugs/classes/{drugClass}` | GET | 按分类查询药品 | 需要令牌 |
| **文档管理** | `/api/documents` | POST | 创建文档 | ADMIN |
| | `/api/documents` | GET | 文档列表 | 需要令牌 |
| | `/api/documents/{id}` | GET | 获取文档 | 需要令牌 |
| | `/api/documents/{id}` | PUT | 更新文档 | ADMIN |
| | `/api/documents/{id}` | DELETE | 删除文档 | ADMIN |
| | `/api/documents/search` | GET | 搜索文档 | 需要令牌 |
| **RAG管理** | `/api/rag/process-document` | POST | 文档处理 | ADMIN |
| | `/api/rag/stats` | GET | RAG统计信息 | ADMIN |
| | `/api/rag/health` | GET | 健康检查 | 无 |
| **用户管理** | `/api/users` | POST | 创建用户 | ADMIN |
| | `/api/users` | GET | 用户列表 | ADMIN |
| | `/api/users/{id}` | GET | 获取用户 | 需要令牌 |
| | `/api/users/{id}` | PUT | 更新用户 | ADMIN/本人 |
| | `/api/users/{id}` | DELETE | 删除用户 | ADMIN |
| | `/api/users/search` | GET | 搜索用户 | ADMIN |
| | `/api/users/{userId}/health-profile` | PUT | 更新健康档案 | ADMIN/本人 |
| | `/api/users/{userId}/health-profile` | GET | 获取健康档案 | ADMIN/本人 |
| | `/api/users/{userId}/health-profile` | DELETE | 删除健康档案 | ADMIN/本人 |
| **对话管理** | `/api/conversations` | POST | 创建会话 | 需要令牌 |
| | `/api/conversations` | GET | 会话列表 | 需要令牌 |
| | `/api/conversations/{id}` | GET | 获取会话 | 需要令牌 |
| | `/api/conversations/{id}` | PUT | 更新会话 | 需要令牌 |
| | `/api/conversations/{id}` | DELETE | 删除会话 | 需要令牌 |
| | `/api/conversations/by-user/{userId}` | GET | 按用户查询会话 | 需要令牌 |
| | `/api/conversations/search` | GET | 搜索会话 | 需要令牌 |
| | `/api/conversations/{conversationId}/export` | GET | 导出会话 | 需要令牌 |
| **消息管理** | `/api/messages` | POST | 创建消息 | 需要令牌 |
| | `/api/messages` | GET | 消息列表 | 需要令牌 |
| | `/api/messages/{id}` | GET | 获取消息 | 需要令牌 |
| | `/api/messages/{id}` | PUT | 更新消息 | 需要令牌 |
| | `/api/messages/{id}` | DELETE | 删除消息 | 需要令牌 |
| | `/api/messages/by-conversation/{conversationId}` | GET | 按对话查询消息 | 需要令牌 |
| | `/api/messages/search` | GET | 搜索消息 | 需要令牌 |

### API 服务实现示例

```typescript
// src/api/services/auth.service.ts
import axios from 'axios';
import { API_CONFIG, AUTH_CONFIG } from '../config';
import type { LoginRequest, LoginResponse, ApiResponse } from '../types';

export class AuthService {
  private baseURL = API_CONFIG.BASE_URL;

  // 发送验证码
  async sendLoginCode(phone: string): Promise<ApiResponse> {
    try {
      const response = await axios.post(`${this.baseURL}/auth/send-code`, null, {
        params: { phone },
      });
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  // 验证码登录
  async loginWithCode(request: LoginRequest): Promise<LoginResponse> {
    try {
      const response = await axios.post<ApiResponse<LoginResponse>>(
        `${this.baseURL}/auth/login`,
        request
      );
      
      if (response.data.success && response.data.data?.token) {
        this.saveToken(response.data.data.token);
      }
      
      return response.data.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  // 保存令牌
  private saveToken(token: string): void {
    localStorage.setItem(AUTH_CONFIG.TOKEN_KEY, token);
  }

  // 获取令牌
  getToken(): string | null {
    return localStorage.getItem(AUTH_CONFIG.TOKEN_KEY);
  }

  // 清除令牌
  clearToken(): void {
    localStorage.removeItem(AUTH_CONFIG.TOKEN_KEY);
  }

  private handleError(error: any): Error {
    if (axios.isAxiosError(error)) {
      return new Error(error.response?.data?.message || error.message);
    }
    return error instanceof Error ? error : new Error('未知错误');
  }
}
```

### SSE 流式问答服务

```typescript
// src/api/services/qa.service.ts
import { API_CONFIG } from '../config';
import type { QaResponse, QaStats, StreamChunk } from '../types';

export class QaService {
  private baseURL = API_CONFIG.BASE_URL;

  // 流式问答
  async streamAnswer(
    query: string,
    userId?: number,
    conversationId?: number,
    onChunk: (chunk: StreamChunk) => void,
    onComplete: () => void,
    onError: (error: Error) => void
  ): Promise<void> {
    const token = localStorage.getItem('imkqas_token');
    
    const eventSource = new EventSource(
      `${this.baseURL}/qa/stream?query=${encodeURIComponent(query)}` +
      `${userId ? `&userId=${userId}` : ''}` +
      `${conversationId ? `&conversationId=${conversationId}` : ''}` +
      `&authorization=${token}`
    );

    eventSource.onmessage = (event) => {
      try {
        const chunk: StreamChunk = JSON.parse(event.data);
        onChunk(chunk);
      } catch (error) {
        console.error('解析SSE数据失败:', error);
      }
    };

    eventSource.addEventListener('complete', () => {
      onComplete();
      eventSource.close();
    });

    eventSource.onerror = (error) => {
      onError(new Error('SSE连接错误'));
      eventSource.close();
    };

    // 设置超时
    setTimeout(() => {
      eventSource.close();
      onError(new Error('流式问答超时'));
    }, API_CONFIG.SSE_TIMEOUT);
  }

  // 同步问答
  async askQuestion(
    question: string,
    userId?: number,
    conversationId?: number
  ): Promise<QaResponse> {
    const token = localStorage.getItem('imqkas_token');
    
    try {
      const response = await axios.post<ApiResponse<QaResponse>>(
        `${this.baseURL}/qa/ask`,
        { question, userId, conversationId },
        {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        }
      );
      
      return response.data.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  // 科室导诊
  async triage(symptoms: string): Promise<DepartmentTriageResult> {
    const token = localStorage.getItem('imqkas_token');
    
    try {
      const response = await axios.post<ApiResponse<DepartmentTriageResult>>(
        `${this.baseURL}/qa/triage`,
        { symptoms },
        {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        }
      );
      
      return response.data.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }
}
```

## 组件设计

### 1. 问答对话界面组件

```vue
<!-- src/components/qa/QaChat.vue -->
<template>
  <div class="qa-chat-container">
    <!-- 会话侧边栏 -->
    <ConversationSidebar 
      :conversations="conversations"
      @select="handleSelectConversation"
      @create="handleCreateConversation"
      @delete="handleDeleteConversation"
    />
    
    <!-- 主聊天区域 -->
    <div class="chat-main">
      <!-- 消息列表 -->
      <div class="messages-container" ref="messagesContainer">
        <MessageBubble
          v-for="message in currentMessages"
          :key="message.id"
          :message="message"
          :is-streaming="isStreaming && message.role === 'assistant'"
        />
        
        <!-- 流式消息占位符 -->
        <div v-if="isStreaming" class="streaming-placeholder">
          <div class="typing-indicator">
            <span></span><span></span><span></span>
          </div>
        </div>
      </div>
      
      <!-- 输入区域 -->
      <div class="input-area">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="3"
          :maxlength="1000"
          placeholder="请输入医疗问题或症状描述..."
          @keyup.enter.ctrl="handleSend"
        />
        
        <div class="input-actions">
          <!-- 快速操作按钮 -->
          <el-button-group>
            <el-button @click="handleTriage" :disabled="!inputText.trim()">
              科室导诊
            </el-button>
            <el-button @click="handleDrugSearch" :disabled="!inputText.trim()">
              药物查询
            </el-button>
            <el-button @click="handleClear">
              清空
            </el-button>
          </el-button-group>
          
          <!-- 发送按钮 -->
          <el-button
            type="primary"
            :disabled="!inputText.trim() || isStreaming"
            @click="handleSend"
            :loading="isStreaming"
          >
            发送
          </el-button>
        </div>
        
        <!-- 安全提示 -->
        <div v-if="showSafetyWarning" class="safety-warning">
          <el-alert
            title="⚠️ 紧急症状提醒"
            type="warning"
            :closable="false"
            show-icon
          >
            检测到紧急症状关键词：{{ emergencySymptoms.join(', ') }}，建议立即就医！
          </el-alert>
        </div>
      </div>
    </div>
    
    <!-- 引用面板 -->
    <ReferencePanel 
      v-if="selectedMessage?.references?.length"
      :references="selectedMessage.references"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue';
import ConversationSidebar from './ConversationSidebar.vue';
import MessageBubble from './MessageBubble.vue';
import ReferencePanel from './ReferencePanel.vue';
import { useQaStore } from '@/stores/qa.store';
import { useAuthStore } from '@/stores/auth.store';
import { useConversationStore } from '@/stores/conversation.store';
import { emergencyKeywords } from '@/utils/safety-detector';

const qaStore = useQaStore();
const authStore = useAuthStore();
const conversationStore = useConversationStore();

const inputText = ref('');
const isStreaming = ref(false);
const messagesContainer = ref<HTMLElement>();
const selectedMessage = ref<any>(null);

// 计算属性
const currentMessages = computed(() => 
  conversationStore.currentConversation?.messages || []
);

const showSafetyWarning = computed(() => {
  if (!inputText.value) return false;
  return emergencyKeywords.some(keyword => 
    inputText.value.toLowerCase().includes(keyword)
  );
});

const emergencySymptoms = computed(() => {
  if (!inputText.value) return [];
  return emergencyKeywords.filter(keyword => 
    inputText.value.toLowerCase().includes(keyword)
  );
});

// 方法
const handleSend = async () => {
  if (!inputText.value.trim() || isStreaming.value) return;
  
  const userMessage = {
    content: inputText.value,
    role: 'user' as const,
    timestamp: new Date(),
  };
  
  // 添加用户消息
  await conversationStore.addMessage(userMessage);
  
  // 清空输入
  const question = inputText.value;
  inputText.value = '';
  
  // 开始流式问答
  isStreaming.value = true;
  try {
    await qaStore.streamAnswer({
      query: question,
      userId: authStore.user?.id,
      conversationId: conversationStore.currentConversation?.id,
      onChunk: (chunk) => {
        conversationStore.updateStreamingMessage(chunk);
      },
      onComplete: () => {
        isStreaming.value = false;
        scrollToBottom();
      },
      onError: (error) => {
        console.error('流式问答错误:', error);
        isStreaming.value = false;
      },
    });
  } catch (error) {
    console.error('问答错误:', error);
    isStreaming.value = false;
  }
};

const handleTriage = async () => {
  const symptoms = inputText.value;
  if (!symptoms.trim()) return;
  
  try {
    const result = await qaStore.triage(symptoms);
    
    // 显示导诊结果
    conversationStore.addMessage({
      content: `根据症状"${symptoms}"，推荐就诊科室：${result.recommendations[0]?.name}（置信度：${(result.confidence * 100).toFixed(1)}%）`,
      role: 'assistant',
      timestamp: new Date(),
      type: 'triage',
      data: result,
    });
    
    // 如果是紧急症状，显示警告
    if (result.emergencyCheck?.level === 'CRITICAL') {
      conversationStore.addMessage({
        content: '⚠️ **紧急情况提醒**：检测到危急症状，建议立即就医！',
        role: 'system',
        timestamp: new Date(),
        type: 'emergency',
      });
    }
    
    scrollToBottom();
  } catch (error) {
    console.error('导诊错误:', error);
  }
};

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
    }
  });
};

onMounted(() => {
  conversationStore.loadConversations();
});

onUnmounted(() => {
  qaStore.cancelStreaming();
});
</script>
```

### 2. 知识库管理组件

```vue
<!-- src/components/knowledge/DocumentManager.vue -->
<template>
  <div class="document-manager">
    <!-- 头部操作栏 -->
    <div class="header">
      <div class="header-left">
        <el-button type="primary" @click="handleUpload">
          <el-icon><Upload /></el-icon>
          上传文档
        </el-button>
        
        <el-select
          v-model="filterCategory"
          placeholder="所有分类"
          clearable
          @change="handleFilterChange"
        >
          <el-option
            v-for="category in categories"
            :key="category.value"
            :label="category.label"
            :value="category.value"
          />
        </el-select>
        
        <el-input
          v-model="searchKeyword"
          placeholder="搜索文档标题..."
          clearable
          @input="handleSearch"
          style="width: 300px;"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>
      
      <div class="header-right">
        <el-button @click="refreshList">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>
    
    <!-- 文档列表 -->
    <el-table
      :data="documents"
      v-loading="loading"
      style="width: 100%"
      @row-click="handleRowClick"
    >
      <el-table-column prop="title" label="文档标题" min-width="200">
        <template #default="{ row }">
          <div class="document-title">
            <el-icon :color="getStatusColor(row.status)">
              <component :is="getStatusIcon(row.status)" />
            </el-icon>
            <span>{{ row.title }}</span>
          </div>
        </template>
      </el-table-column>
      
      <el-table-column prop="category" label="分类" width="120">
        <template #default="{ row }">
          <el-tag :type="getCategoryType(row.category)" size="small">
            {{ row.category || '未分类' }}
          </el-tag>
        </template>
      </el-table-column>
      
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)" size="small">
            {{ getStatusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      
      <el-table-column prop="createdAt" label="上传时间" width="180">
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>
      
      <el-table-column prop="size" label="文件大小" width="100">
        <template #default="{ row }">
          {{ formatFileSize(row.size) }}
        </template>
      </el-table-column>
      
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button-group size="small">
            <el-button @click.stop="handlePreview(row)" title="预览">
              <el-icon><View /></el-icon>
            </el-button>
            
            <el-button
              @click.stop="handleProcess(row)"
              :disabled="row.status !== 'UPLOADED'"
              :loading="processingId === row.id"
              title="处理"
            >
              <el-icon><MagicStick /></el-icon>
            </el-button>
            
            <el-button
              @click.stop="handleDelete(row)"
              type="danger"
              title="删除"
            >
              <el-icon><Delete /></el-icon>
            </el-button>
          </el-button-group>
        </template>
      </el-table-column>
    </el-table>
    
    <!-- 分页 -->
    <div class="pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
    
    <!-- 文档上传对话框 -->
    <el-dialog
      v-model="uploadDialogVisible"
      title="上传文档"
      width="500px"
      :before-close="handleUploadDialogClose"
    >
      <el-upload
        ref="uploadRef"
        drag
        action="#"
        :auto-upload="false"
        :on-change="handleFileChange"
        :show-file-list="false"
        :accept="supportedFormats"
        multiple
      >
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="upload-text">
          <div>将文件拖到此处，或<em>点击上传</em></div>
          <div class="upload-hint">支持 {{ supportedFormatsText }}</div>
        </div>
      </el-upload>
      
      <div v-if="selectedFiles.length > 0" class="selected-files">
        <div class="files-header">
          <span>已选择 {{ selectedFiles.length }} 个文件</span>
          <el-button type="text" @click="clearSelectedFiles">
            清空
          </el-button>
        </div>
        
        <div class="files-list">
          <div v-for="file in selectedFiles" :key="file.name" class="file-item">
            <el-icon><Document /></el-icon>
            <span class="file-name">{{ file.name }}</span>
            <span class="file-size">{{ formatFileSize(file.size) }}</span>
          </div>
        </div>
        
        <!-- 分类选择 -->
        <div class="upload-options">
          <el-form label-width="80px">
            <el-form-item label="文档分类">
              <el-select
                v-model="uploadCategory"
                placeholder="请选择分类"
                style="width: 100%;"
              >
                <el-option
                  v-for="category in categories"
                  :key="category.value"
                  :label="category.label"
                  :value="category.value"
                />
              </el-select>
            </el-form-item>
          </el-form>
        </div>
      </div>
      
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="uploading"
          :disabled="selectedFiles.length === 0"
          @click="handleUploadSubmit"
        >
          上传
        </el-button>
      </template>
    </el-dialog>
    
    <!-- 文档预览对话框 -->
    <el-dialog
      v-model="previewDialogVisible"
      :title="previewDocument?.title"
      width="80%"
      top="5vh"
    >
      <DocumentPreview :document="previewDocument" />
    </el-dialog>
  </div>
</template>
```

## 状态管理设计

### Pinia 存储模块设计

```typescript
// src/stores/auth.store.ts
import { defineStore } from 'pinia';
import { AuthService } from '@/api/services/auth.service';
import type { User, LoginRequest, LoginResponse } from '@/types';

interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  loading: boolean;
  error: string | null;
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    user: null,
    token: null,
    isAuthenticated: false,
    loading: false,
    error: null,
  }),

  getters: {
    // 用户角色检查
    isAdmin: (state) => state.user?.role === 'ADMIN',
    isMedicalStaff: (state) => state.user?.role === 'MEDICAL_STAFF',
    isRegularUser: (state) => state.user?.role === 'USER',
    
    // 权限检查
    canUploadDocuments: (state) => 
      state.user?.role === 'ADMIN' || state.user?.role === 'MEDICAL_STAFF',
    canManageUsers: (state) => state.user?.role === 'ADMIN',
    canViewDashboard: (state) => 
      state.user?.role === 'ADMIN' || state.user?.role === 'MEDICAL_STAFF',
  },

  actions: {
    // 初始化认证状态
    async initialize() {
      const token = localStorage.getItem('imqkas_token');
      if (token) {
        this.token = token;
        await this.validateToken();
      }
    },

    // 验证码登录
    async loginWithCode(request: LoginRequest) {
      this.loading = true;
      this.error = null;
      
      try {
        const authService = new AuthService();
        const response = await authService.loginWithCode(request);
        
        this.user = response.user;
        this.token = response.token;
        this.isAuthenticated = true;
        
        // 保存到本地存储
        localStorage.setItem('imqkas_token', response.token);
        
        return response;
      } catch (error) {
        this.error = error instanceof Error ? error.message : '登录失败';
        throw error;
      } finally {
        this.loading = false;
      }
    },

    // 验证令牌
    async validateToken() {
      try {
        const authService = new AuthService();
        const response = await authService.validateToken(this.token!);
        
        if (response.valid) {
          const userResponse = await authService.getCurrentUser();
          this.user = userResponse;
          this.isAuthenticated = true;
        } else {
          this.logout();
        }
      } catch (error) {
        this.logout();
      }
    },

    // 登出
    logout() {
      this.user = null;
      this.token = null;
      this.isAuthenticated = false;
      localStorage.removeItem('imqkas_token');
    },

    // 刷新令牌
    async refreshToken() {
      if (!this.token) return;
      
      try {
        const authService = new AuthService();
        const newToken = await authService.refreshToken(this.token);
        
        this.token = newToken;
        localStorage.setItem('imqkas_token', newToken);
      } catch (error) {
        this.logout();
        throw error;
      }
    },
  },

  // 持久化配置
  persist: {
    key: 'imqkas-auth',
    paths: ['user', 'token', 'isAuthenticated'],
  },
});
```

### 问答状态管理

```typescript
// src/stores/qa.store.ts
import { defineStore } from 'pinia';
import { QaService } from '@/api/services/qa.service';
import type { QaResponse, StreamChunk, DepartmentTriageResult } from '@/types';

interface QaState {
  currentQuestion: string | null;
  currentAnswer: string | null;
  isStreaming: boolean;
  streamError: string | null;
  confidence: number | null;
  references: any[];
  triageResults: DepartmentTriageResult[];
  drugSearchResults: any[];
  history: QaResponse[];
}

export const useQaStore = defineStore('qa', {
  state: (): QaState => ({
    currentQuestion: null,
    currentAnswer: null,
    isStreaming: false,
    streamError: null,
    confidence: null,
    references: [],
    triageResults: [],
    drugSearchResults: [],
    history: [],
  }),

  actions: {
    // 流式问答
    async streamAnswer(params: {
      query: string;
      userId?: number;
      conversationId?: number;
      onChunk: (chunk: StreamChunk) => void;
      onComplete: () => void;
      onError: (error: Error) => void;
    }) {
      if (this.isStreaming) {
        throw new Error('已有正在进行的流式问答');
      }

      this.isStreaming = true;
      this.streamError = null;
      this.currentQuestion = params.query;
      this.currentAnswer = '';

      try {
        const qaService = new QaService();
        await qaService.streamAnswer(
          params.query,
          params.userId,
          params.conversationId,
          (chunk) => {
            // 更新当前回答
            this.currentAnswer += chunk.content;
            
            // 更新置信度和引用
            if (chunk.confidence) this.confidence = chunk.confidence;
            if (chunk.references) this.references = chunk.references;
            
            // 回调
            params.onChunk(chunk);
          },
          () => {
            this.isStreaming = false;
            this.saveToHistory();
            params.onComplete();
          },
          (error) => {
            this.isStreaming = false;
            this.streamError = error.message;
            params.onError(error);
          }
        );
      } catch (error) {
        this.isStreaming = false;
        this.streamError = error instanceof Error ? error.message : '未知错误';
        throw error;
      }
    },

    // 取消流式问答
    cancelStreaming() {
      this.isStreaming = false;
      this.currentAnswer = null;
    },

    // 科室导诊
    async triage(symptoms: string) {
      try {
        const qaService = new QaService();
        const result = await qaService.triage(symptoms);
        
        this.triageResults.unshift(result);
        return result;
      } catch (error) {
        throw error;
      }
    },

    // 药物查询
    async searchDrugs(name: string) {
      try {
        const qaService = new QaService();
        const results = await qaService.searchDrugs(name);
        
        this.drugSearchResults = results;
        return results;
      } catch (error) {
        throw error;
      }
    },

    // 保存到历史记录
    saveToHistory() {
      if (this.currentQuestion && this.currentAnswer) {
        this.history.unshift({
          question: this.currentQuestion,
          answer: this.currentAnswer,
          confidence: this.confidence,
          references: this.references,
          timestamp: new Date(),
        });
        
        // 限制历史记录数量
        if (this.history.length > 100) {
          this.history = this.history.slice(0, 100);
        }
      }
    },

    // 清除历史记录
    clearHistory() {
      this.history = [];
    },
  },
});
```

## 路由设计

### 路由配置

```typescript
// src/router/routes/index.ts
import type { RouteRecordRaw } from 'vue-router';

export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/dashboard',
    meta: { requiresAuth: true },
  },
  
  // 认证路由
  {
    path: '/auth',
    component: () => import('@/layouts/AuthLayout.vue'),
    meta: { guestOnly: true },
    children: [
      {
        path: 'login',
        name: 'Login',
        component: () => import('@/pages/auth/LoginPage.vue'),
      },
      {
        path: 'register',
        name: 'Register',
        component: () => import('@/pages/auth/RegisterPage.vue'),
      },
    ],
  },
  
  // 主应用路由
  {
    path: '/',
    component: () => import('@/layouts/DefaultLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      // 仪表盘
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/pages/dashboard/DashboardPage.vue'),
        meta: {
          title: '仪表盘',
          icon: 'DataAnalysis',
          requiresRole: ['ADMIN', 'MEDICAL_STAFF'],
        },
      },
      
      // 智能问答
      {
        path: 'qa',
        name: 'QaChat',
        component: () => import('@/pages/qa/QaChatPage.vue'),
        meta: {
          title: '智能问答',
          icon: 'ChatRound',
        },
      },
      
      // 知识库管理
      {
        path: 'knowledge',
        name: 'KnowledgeBase',
        component: () => import('@/pages/knowledge/KnowledgePage.vue'),
        meta: {
          title: '知识库管理',
          icon: 'Document',
          requiresRole: ['ADMIN', 'MEDICAL_STAFF'],
        },
        children: [
          {
            path: 'documents',
            name: 'DocumentManager',
            component: () => import('@/pages/knowledge/DocumentManagerPage.vue'),
            meta: { title: '文档管理' },
          },
          {
            path: 'categories',
            name: 'CategoryManager',
            component: () => import('@/pages/knowledge/CategoryManagerPage.vue'),
            meta: { title: '分类管理' },
          },
          {
            path: 'chunks',
            name: 'ChunkBrowser',
            component: () => import('@/pages/knowledge/ChunkBrowserPage.vue'),
            meta: { title: '切片浏览' },
          },
        ],
      },
      
      // 用户管理
      {
        path: 'users',
        name: 'UserManagement',
        component: () => import('@/pages/user/UserPage.vue'),
        meta: {
          title: '用户管理',
          icon: 'User',
          requiresRole: ['ADMIN'],
        },
        children: [
          {
            path: 'list',
            name: 'UserList',
            component: () => import('@/pages/user/UserListPage.vue'),
            meta: { title: '用户列表' },
          },
          {
            path: 'profile/:id?',
            name: 'UserProfile',
            component: () => import('@/pages/user/UserProfilePage.vue'),
            meta: { title: '用户档案' },
          },
          {
            path: 'health-profiles',
            name: 'HealthProfiles',
            component: () => import('@/pages/user/HealthProfilePage.vue'),
            meta: { title: '健康档案' },
          },
        ],
      },
      
      // 对话管理
      {
        path: 'conversations',
        name: 'ConversationManager',
        component: () => import('@/pages/conversation/ConversationPage.vue'),
        meta: {
          title: '对话管理',
          icon: 'ChatLineRound',
        },
      },
      
      // 设置
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/pages/settings/SettingsPage.vue'),
        meta: {
          title: '系统设置',
          icon: 'Setting',
          requiresRole: ['ADMIN'],
        },
      },
    ],
  },
  
  // 404页面
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/pages/error/NotFoundPage.vue'),
  },
];
```

### 路由守卫

```typescript
// src/router/guards.ts
import type { Router } from 'vue-router';
import { useAuthStore } from '@/stores/auth.store';

export function setupRouterGuards(router: Router) {
  // 全局前置守卫
  router.beforeEach(async (to, from, next) => {
    const authStore = useAuthStore();
    
    // 初始化认证状态
    if (!authStore.initialized) {
      await authStore.initialize();
    }
    
    // 检查是否需要认证
    if (to.meta.requiresAuth && !authStore.isAuthenticated) {
      next({ name: 'Login', query: { redirect: to.fullPath } });
      return;
    }
    
    // 检查是否仅限游客访问
    if (to.meta.guestOnly && authStore.isAuthenticated) {
      next({ name: 'Dashboard' });
      return;
    }
    
    // 检查角色权限
    if (to.meta.requiresRole) {
      const requiredRoles = Array.isArray(to.meta.requiresRole) 
        ? to.meta.requiresRole 
        : [to.meta.requiresRole];
      
      const userRole = authStore.user?.role;
      
      if (!userRole || !requiredRoles.includes(userRole)) {
        next({ name: 'Forbidden' });
        return;
      }
    }
    
    // 设置页面标题
    if (to.meta.title) {
      document.title = `${to.meta.title} - IMKQAS医疗问答系统`;
    }
    
    next();
  });
  
  // 全局后置守卫
  router.afterEach((to) => {
    // 滚动到顶部
    window.scrollTo(0, 0);
    
    // 发送页面访问统计
    if (to.name) {
      // TODO: 发送页面访问统计
    }
  });
}
```

## 数据可视化设计

### ECharts 图表组件

```vue
<!-- src/components/charts/DashboardCharts.vue -->
<template>
  <div class="dashboard-charts">
    <!-- 问答统计图表 -->
    <el-card class="chart-card">
      <template #header>
        <div class="chart-header">
          <span class="chart-title">📊 问答统计</span>
          <div class="chart-actions">
            <el-radio-group v-model="qaStatsPeriod" size="small">
              <el-radio-button label="day">今日</el-radio-button>
              <el-radio-button label="week">本周</el-radio-button>
              <el-radio-button label="month">本月</el-radio-button>
            </el-radio-group>
          </div>
        </div>
      </template>
      
      <div class="chart-container" ref="qaChartRef"></div>
    </el-card>
    
    <!-- 科室导诊分布 -->
    <el-card class="chart-card">
      <template #header>
        <div class="chart-header">
          <span class="chart-title">🏥 科室导诊分布</span>
        </div>
      </template>
      
      <div class="chart-container" ref="triageChartRef"></div>
    </el-card>
    
    <!-- 知识库覆盖统计 -->
    <el-card class="chart-card">
      <template #header>
        <div class="chart-header">
          <span class="chart-title">📚 知识库覆盖情况</span>
        </div>
      </template>
      
      <div class="chart-container" ref="knowledgeChartRef"></div>
    </el-card>
    
    <!-- 用户活跃度 -->
    <el-card class="chart-card">
      <template #header>
        <div class="chart-header">
          <span class="chart-title">👥 用户活跃度</span>
        </div>
      </template>
      
      <div class="chart-container" ref="userChartRef"></div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue';
import * as echarts from 'echarts';
import type { EChartsType } from 'echarts';
import { useStatsStore } from '@/stores/stats.store';
import { useResizeObserver } from '@vueuse/core';

const statsStore = useStatsStore();

// 图表引用
const qaChartRef = ref<HTMLElement>();
const triageChartRef = ref<HTMLEPTElement>();
const knowledgeChartRef = ref<HTMLElement>();
const userChartRef = ref<HTMLElement>();

// 图表实例
let qaChart: EChartsType;
let triageChart: EChartsType;
let knowledgeChart: EChartsType;
let userChart: EChartsType;

// 统计周期
const qaStatsPeriod = ref<'day' | 'week' | 'month'>('day');

// 初始化图表
const initCharts = () => {
  if (qaChartRef.value) {
    qaChart = echarts.init(qaChartRef.value);
    updateQaChart();
  }
  
  if (triageChartRef.value) {
    triageChart = echarts.init(triageChartRef.value);
    updateTriageChart();
  }
  
  if (knowledgeChartRef.value) {
    knowledgeChart = echarts.init(knowledgeChartRef.value);
    updateKnowledgeChart();
  }
  
  if (userChartRef.value) {
    userChart = echarts.init(userChartRef.value);
    updateUserChart();
  }
};

// 更新问答统计图表
const updateQaChart = () => {
  if (!qaChart) return;
  
  const stats = statsStore.qaStats;
  
  const option = {
    tooltip: {
      trigger: 'axis',
      formatter: '{b}<br/>{a}: {c}次',
    },
    legend: {
      data: ['问答量', '成功次数', '失败次数'],
      bottom: 0,
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '15%',
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: stats.timestamps,
      axisLabel: {
        rotate: 45,
      },
    },
    yAxis: {
      type: 'value',
      name: '次数',
    },
    series: [
      {
        name: '问答量',
        type: 'line',
        data: stats.totalQueries,
        smooth: true,
        lineStyle: {
          color: '#409EFF',
        },
        itemStyle: {
          color: '#409EFF',
        },
      },
      {
        name: '成功次数',
        type: 'bar',
        data: stats.successQueries,
        barWidth: '60%',
        itemStyle: {
          color: '#67C23A',
        },
      },
      {
        name: '失败次数',
        type: 'bar',
        data: stats.failedQueries,
        barWidth: '60%',
        itemStyle: {
          color: '#F56C6C',
        },
      },
    ],
  };
  
  qaChart.setOption(option);
};

// 更新科室导诊分布图表
const updateTriageChart = () => {
  if (!triageChart) return;
  
  const stats = statsStore.triageStats;
  
  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{a} <br/>{b}: {c}次 ({d}%)',
    },
    legend: {
      type: 'scroll',
      orient: 'vertical',
      right: 10,
      top: 20,
      bottom: 20,
      data: stats.departments,
    },
    series: [
      {
        name: '导诊次数',
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['40%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2,
        },
        label: {
          show: false,
          position: 'center',
        },
        emphasis: {
          label: {
            show: true,
            fontSize: '20',
            fontWeight: 'bold',
          },
        },
        labelLine: {
          show: false,
        },
        data: stats.departmentCounts.map((count, index) => ({
          value: count,
          name: stats.departments[index],
        })),
      },
    ],
  };
  
  triageChart.setOption(option);
};

// 响应式调整
const setupResizeHandlers = () => {
  [qaChartRef, triageChartRef, knowledgeChartRef, userChartRef].forEach((chartRef) => {
    useResizeObserver(chartRef, () => {
      const chart = getChartByRef(chartRef);
      chart?.resize();
    });
  });
};

const getChartByRef = (ref: any): EChartsType | undefined => {
  if (ref === qaChartRef) return qaChart;
  if (ref === triageChartRef) return triageChart;
  if (ref === knowledgeChartRef) return knowledgeChart;
  if (ref === userChartRef) return userChart;
  return undefined;
};

// 监听统计周期变化
watch(qaStatsPeriod, async (newPeriod) => {
  await statsStore.fetchQaStats(newPeriod);
  updateQaChart();
});

// 生命周期
onMounted(async () => {
  await Promise.all([
    statsStore.fetchQaStats('day'),
    statsStore.fetchTriageStats(),
    statsStore.fetchKnowledgeStats(),
    statsStore.fetchUserStats(),
  ]);
  
  initCharts();
  setupResizeHandlers();
});

onUnmounted(() => {
  [qaChart, triageChart, knowledgeChart, userChart].forEach((chart) => {
    chart?.dispose();
  });
});
</script>
```

## 权限控制设计

### 权限指令

```typescript
// src/directives/permission.ts
import type { Directive } from 'vue';
import { useAuthStore } from '@/stores/auth.store';

interface PermissionValue {
  action: string;
  resource: string;
}

export const permissionDirective: Directive<HTMLElement, PermissionValue | string> = {
  mounted(el, binding) {
    const authStore = useAuthStore();
    let hasPermission = false;

    if (typeof binding.value === 'string') {
      // 简单权限检查（如 'upload_documents'）
      hasPermission = checkSimplePermission(binding.value, authStore);
    } else {
      // 复杂权限检查（如 { action: 'create', resource: 'document' }）
      hasPermission = checkComplexPermission(binding.value, authStore);
    }

    if (!hasPermission) {
      el.style.display = 'none';
    }
  },
};

function checkSimplePermission(permission: string, authStore: any): boolean {
  const permissions = {
    // 管理员权限
    manage_users: authStore.isAdmin,
    manage_documents: authStore.canUploadDocuments,
    view_dashboard: authStore.canViewDashboard,
    
    // 医护人员权限
    upload_documents: authStore.canUploadDocuments,
    
    // 普通用户权限
    ask_questions: authStore.isAuthenticated,
    view_history: authStore.isAuthenticated,
  };

  return permissions[permission as keyof typeof permissions] || false;
}

function checkComplexPermission(permission: PermissionValue, authStore: any): boolean {
  // RBAC（基于角色的访问控制）检查
  const rolePermissions = {
    ADMIN: {
      document: ['create', 'read', 'update', 'delete', 'process'],
      user: ['create', 'read', 'update', 'delete'],
      conversation: ['read', 'delete'],
      stats: ['read'],
    },
    MEDICAL_STAFF: {
      document: ['create', 'read', 'process'],
      user: ['read'],
      conversation: ['read'],
      stats: ['read'],
    },
    USER: {
      document: ['read'],
      conversation: ['create', 'read', 'update', 'delete'],
    },
  };

  const userRole = authStore.user?.role as keyof typeof rolePermissions;
  if (!userRole) return false;

  const rolePermission = rolePermissions[userRole];
  if (!rolePermission) return false;

  const resourcePermissions = rolePermission[permission.resource as keyof typeof rolePermission];
  if (!resourcePermissions) return false;

  return resourcePermissions.includes(permission.action);
}
```

### 权限组件

```vue
<!-- src/components/common/PermissionGuard.vue -->
<template>
  <slot v-if="hasPermission"></slot>
  <slot v-else name="fallback">
    <el-empty
      v-if="showEmpty"
      :description="fallbackText"
      :image-size="100"
    />
  </slot>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useAuthStore } from '@/stores/auth.store';

interface Props {
  // 权限检查方式
  permission?: string; // 简单权限字符串
  action?: string; // 操作类型
  resource?: string; // 资源类型
  role?: string | string[]; // 角色要求
  
  // 回退配置
  showEmpty?: boolean;
  fallbackText?: string;
}

const props = withDefaults(defineProps<Props>(), {
  showEmpty: true,
  fallbackText: '您没有权限访问此内容',
});

const authStore = useAuthStore();

const hasPermission = computed(() => {
  // 检查角色权限
  if (props.role) {
    const requiredRoles = Array.isArray(props.role) ? props.role : [props.role];
    const userRole = authStore.user?.role;
    
    if (!userRole || !requiredRoles.includes(userRole)) {
      return false;
    }
  }
  
  // 检查简单权限
  if (props.permission) {
    return checkSimplePermission(props.permission);
  }
  
  // 检查复杂权限
  if (props.action && props.resource) {
    return checkComplexPermission(props.action, props.resource);
  }
  
  // 默认允许访问
  return true;
});

function checkSimplePermission(permission: string): boolean {
  const permissionMap: Record<string, boolean> = {
    manage_users: authStore.isAdmin,
    manage_documents: authStore.canUploadDocuments,
    view_dashboard: authStore.canViewDashboard,
    upload_documents: authStore.canUploadDocuments,
    ask_questions: authStore.isAuthenticated,
  };
  
  return permissionMap[permission] || false;
}

function checkComplexPermission(action: string, resource: string): boolean {
  const role = authStore.user?.role;
  
  if (!role) return false;
  
  const permissionMatrix: Record<string, Record<string, string[]>> = {
    ADMIN: {
      document: ['create', 'read', 'update', 'delete', 'process'],
      user: ['create', 'read', 'update', 'delete'],
      conversation: ['read', 'delete'],
      stats: ['read'],
    },
    MEDICAL_STAFF: {
      document: ['create', 'read', 'process'],
      user: ['read'],
      conversation: ['read'],
      stats: ['read'],
    },
    USER: {
      document: ['read'],
      conversation: ['create', 'read', 'update', 'delete'],
    },
  };
  
  const rolePermissions = permissionMatrix[role];
  if (!rolePermissions) return false;
  
  const resourcePermissions = rolePermissions[resource];
  if (!resourcePermissions) return false;
  
  return resourcePermissions.includes(action);
}
</script>
```

## 部署配置

### 环境变量配置

```bash
# .env.development
VITE_API_BASE_URL=http://localhost:8080/api
VITE_APP_TITLE=IMKQAS医疗问答系统（开发环境）
VITE_SSE_ENABLED=true
VITE_DEBUG=true
```

```bash
# .env.production
VITE_API_BASE_URL=https://api.imqkas.com/api
VITE_APP_TITLE=IMKQAS医疗问答系统
VITE_SSE_ENABLED=true
VITE_DEBUG=false
```

### Docker 部署配置

```dockerfile
# Dockerfile
FROM node:18-alpine as builder

WORKDIR /app

# 复制包管理文件
COPY package*.json ./

# 安装依赖
RUN npm ci --only=production

# 复制源代码
COPY . .

# 构建应用
RUN npm run build

# 生产环境
FROM nginx:alpine

# 复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制 Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 暴露端口
EXPOSE 80

# 启动 Nginx
CMD ["nginx", "-g", "daemon off;"]
```

```nginx
# nginx.conf
server {
    listen 80;
    server_name localhost;
    
    # 根目录
    root /usr/share/nginx/html;
    index index.html;
    
    # Gzip 压缩
    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_types text/plain text/css text/xml text/javascript application/javascript application/xml+rss application/json;
    
    # 静态资源缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
    
    # API 代理
    location /api {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # WebSocket 支持
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
    
    # SSE 支持
    location /api/qa/stream {
        proxy_pass http://backend:8080;
        proxy_buffering off;
        proxy_cache off;
        proxy_set_header Connection '';
        proxy_http_version 1.1;
        chunked_transfer_encoding off;
    }
    
    # 单页应用路由支持
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

### CI/CD 配置

```yaml
# .github/workflows/deploy.yml
name: Deploy IMKQAS Frontend

on:
  push:
    branches: [main, master]
  pull_request:
    branches: [main, master]

jobs:
  test-and-build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Setup Node.js
      uses: actions/setup-node@v3
      with:
        node-version: '18'
        cache: 'npm'
    
    - name: Install dependencies
      run: npm ci
    
    - name: Run linting
      run: npm run lint
    
    - name: Run tests
      run: npm run test
    
    - name: Build application
      run: npm run build
      env:
        VITE_API_BASE_URL: ${{ secrets.API_BASE_URL }}
    
    - name: Upload build artifacts
      uses: actions/upload-artifact@v3
      with:
        name: dist
        path: dist/
  
  deploy:
    needs: test-and-build
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main' || github.ref == 'refs/heads/master'
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Download build artifacts
      uses: actions/download-artifact@v3
      with:
        name: dist
        path: dist/
    
    - name: Configure Docker Buildx
      uses: docker/setup-buildx-action@v2
    
    - name: Login to Docker Hub
      uses: docker/login-action@v2
      with:
        username: ${{ secrets.DOCKER_USERNAME }}
        password: ${{ secrets.DOCKER_PASSWORD }}
    
    - name: Build and push Docker image
      uses: docker/build-push-action@v4
      with:
        context: .
        push: true
        tags: |
          ${{ secrets.DOCKER_USERNAME }}/imqkas-frontend:latest
          ${{ secrets.DOCKER_USERNAME }}/imqkas-frontend:${{ github.sha }}
    
    - name: Deploy to server
      uses: appleboy/ssh-action@v0.1.5
      with:
        host: ${{ secrets.SERVER_HOST }}
        username: ${{ secrets.SERVER_USERNAME }}
        key: ${{ secrets.SERVER_SSH_KEY }}
        script: |
          cd /opt/imqkas
          docker-compose pull
          docker-compose up -d
          docker system prune -f
```

## 性能优化

### 1. 代码分割
```javascript
// 路由懒加载
const QaChatPage = () => import('@/pages/qa/QaChatPage.vue');
const DocumentManagerPage = () => import('@/pages/knowledge/DocumentManagerPage.vue');

// 组件懒加载
const HeavyChartComponent = defineAsyncComponent(() =>
  import('@/components/charts/HeavyChart.vue')
);
```

### 2. 图片优化
- 使用 WebP 格式
- 实现懒加载
- 使用 CDN 加速

### 3. API 请求优化
- 实现请求防抖和节流
- 使用 SWR（Stale-While-Revalidate）策略
- 实现请求缓存

### 4. 包体积优化
- 使用 Tree Shaking
- 按需引入组件库
- 压缩和混淆代码

## 安全考虑

### 1. XSS 防护
- 使用 Vue 的文本插值自动转义
- 对用户输入进行严格验证
- 使用 Content Security Policy (CSP)

### 2. CSRF 防护
- 使用 JWT 令牌
- 实现令牌刷新机制
- 设置合理的令牌过期时间

### 3. 数据安全
- 敏感数据不存储在 localStorage
- 使用 HTTPS 加密传输
- 实现输入验证和输出编码

### 4. 权限安全
- 前端权限验证作为辅助手段
- 后端必须进行权限验证
- 实现最小权限原则

## 测试策略

### 1. 单元测试
- 使用 Vitest 进行组件测试
- 测试工具函数和组合式函数
- 覆盖核心业务逻辑

### 2. 集成测试
- 测试组件间交互
- 测试 API 集成
- 测试路由导航

### 3. E2E 测试
- 使用 Cypress 或 Playwright
- 测试完整用户流程
- 测试关键业务路径

### 4. 性能测试
- 使用 Lighthouse 进行性能审计
- 监控首屏加载时间
- 优化关键渲染路径

## 监控和错误处理

### 1. 错误边界
```vue
<!-- src/components/common/ErrorBoundary.vue -->
<template>
  <slot v-if="!hasError"></slot>
  <div v-else class="error-boundary">
    <h3>应用发生错误</h3>
    <p>{{ errorMessage }}</p>
    <el-button @click="handleRetry">重试</el-button>
  </div>
</template>

<script setup lang="ts">
import { ref, onErrorCaptured } from 'vue';

const hasError = ref(false);
const errorMessage = ref('');

onErrorCaptured((error) => {
  hasError.value = true;
  errorMessage.value = error.message;
  
  // 发送错误报告
  logError(error);
  
  return false; // 阻止错误继续向上传播
});

const handleRetry = () => {
  hasError.value = false;
  errorMessage.value = '';
  window.location.reload();
};

const logError = (error: Error) => {
  // 发送到错误监控服务
  console.error('应用错误:', error);
};
</script>
```

### 2. 性能监控
```typescript
// src/utils/performance-monitor.ts
export class PerformanceMonitor {
  private metrics: Map<string, number> = new Map();
  
  startMeasurement(name: string) {
    this.metrics.set(name, performance.now());
  }
  
  endMeasurement(name: string): number {
    const startTime = this.metrics.get(name);
    if (!startTime) return 0;
    
    const duration = performance.now() - startTime;
    this.metrics.delete(name);
    
    // 记录性能指标
    this.logPerformance(name, duration);
    
    return duration;
  }
  
  private logPerformance(name: string, duration: number) {
    // 发送到监控服务
    if (duration > 1000) {
      console.warn(`慢操作: ${name} 耗时 ${duration.toFixed(2)}ms`);
    }
  }
}
```

## 总结

IMKQAS 前端设计方案基于现代化的 Vue 3 技术栈，采用模块化、组件化的架构设计，确保系统的可维护性、可扩展性和高性能。通过完善的权限控制、数据可视化、错误处理和监控机制，为用户提供安全、可靠、高效的医疗知识问答体验。

### 主要特点
1. **模块化架构** - 清晰的代码组织，便于团队协作和维护
2. **类型安全** - 全面使用 TypeScript，减少运行时错误
3. **响应式设计** - 适配桌面、平板和手机设备
4. **高性能优化** - 代码分割、懒加载、缓存策略
5. **完善的安全机制** - XSS防护、CSRF防护、权限控制
6. **完整的监控体系** - 性能监控、错误追踪、用户行为分析

### 后续工作
1. 实现所有组件和页面
2. 编写完整的测试用例
3. 配置 CI/CD 流水线
4. 部署到生产环境
5. 持续监控和优化