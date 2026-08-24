<template>
  <el-drawer
    v-model="visible"
    title="词条审核管理"
    direction="rtl"
    size="720px"
    :before-close="handleClose"
    append-to-body
  >
    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-label">待审核数</div>
        <div class="stat-value">{{ stats.pendingCount }}</div>
      </div>
      <div class="stat-card" :class="{ 'stat-alert': stats.alertTriggered }">
        <div class="stat-label">未映射率</div>
        <div class="stat-value">{{ Number(stats.unmappedRate).toFixed(1) }}%</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">今日已审核</div>
        <div class="stat-value">{{ stats.approvedTodayCount }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">告警状态</div>
        <div class="stat-value stat-alert-text">{{ stats.alertTriggered ? '⚠ 告警中' : '正常' }}</div>
      </div>
    </div>

    <!-- 统计加载失败提示 -->
    <div v-if="statsError" class="stats-error-hint">
      <span style="color: var(--theme-error); font-size: 12px">统计数据加载失败</span>
      <el-button size="small" text type="primary" @click="loadStats">重试</el-button>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-select v-model="filterStatus" placeholder="状态筛选" style="width: 140px" @change="loadData">
        <el-option label="待审核" value="PENDING" />
        <el-option label="已通过" value="APPROVED" />
        <el-option label="已拒绝" value="REJECTED" />
        <el-option label="全部" value="ALL" />
      </el-select>
      <el-input
        v-model="searchKeyword"
        placeholder="搜索词条..."
        style="width: 220px; margin-left: 12px"
        clearable
        @input="filterTable"
      />
      <el-button
        type="primary"
        :disabled="selectedRows.length === 0"
        style="margin-left: auto"
        @click="showBatchApprove"
      >
        批量标注 ({{ selectedRows.length }})
      </el-button>
    </div>

    <!-- 词条表格 -->
    <el-table
      ref="tableRef"
      :data="displayTerms"
      row-key="id"
      style="width: 100%; margin-top: 16px"
      max-height="calc(100vh - 380px)"
      size="small"
      border
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="40" :resizable="false" />
      <el-table-column prop="term" label="口语表达" min-width="100" show-overflow-tooltip :resizable="false" />
      <!-- 上下文列：可拖拽调整列宽（配合 border），内容过长悬停查看全文 -->
      <el-table-column prop="contextQuery" label="上下文" min-width="140" show-overflow-tooltip resizable />
      <el-table-column label="实体类型" width="80" :resizable="false">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.guessedEntityType || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="LLM猜测" min-width="100" show-overflow-tooltip :resizable="false">
        <template #default="{ row }">
          <span v-if="row.llmGuess">{{ row.llmGuess }}</span>
          <span v-else style="color: #999">-</span>
        </template>
      </el-table-column>
      <el-table-column label="LLM置信度" width="90" :resizable="false">
        <template #default="{ row }">
          <span
            v-if="row.llmConfidence != null"
            :style="{ color: row.llmConfidence >= 0.85 ? '#22c55e' : row.llmConfidence >= 0.6 ? '#f59e0b' : '#ef4444' }"
          >
            {{ (row.llmConfidence * 100).toFixed(0) }}%
          </span>
          <span v-else style="color: #999">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="occurrenceCount" label="频次" width="60" align="center" :resizable="false" />
      <el-table-column label="首次出现" width="110" :resizable="false">
        <template #default="{ row }">
          <span style="font-size: 12px; color: #666">{{ formatDate(row.firstSeenAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" :resizable="false">
        <template #default="{ row }">
          <div v-if="row.status === 'PENDING'" class="action-cell">
            <el-input v-model="approveInputs[row.id]" placeholder="标准术语" size="small" style="width: 110px" />
            <el-button type="success" size="small" @click="approveOne(row as UnmappedTermItem)"> 通过 </el-button>
            <el-button type="danger" size="small" :icon="'Close'" @click="rejectOne(row as UnmappedTermItem)" />
          </div>
          <el-tag v-else size="small" :type="row.status === 'APPROVED' ? 'success' : 'danger'">
            {{ row.status === 'APPROVED' ? '已通过' : '已拒绝' }}
          </el-tag>
        </template>
      </el-table-column>
      <!-- 加载失败 / 空数据 -->
      <template #empty>
        <EmptyState v-if="listError" icon="error" title="加载失败" description="词条列表加载失败，请稍后重试。">
          <el-button size="small" type="primary" @click="loadData">重新加载</el-button>
        </EmptyState>
        <EmptyState v-else icon="text_snippet" title="暂无词条数据" />
      </template>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @change="loadData"
      />
    </div>

    <!-- 批量标注弹窗 -->
    <el-dialog v-model="batchDialogVisible" title="批量标注" width="420px">
      <p style="margin-bottom: 12px">
        已选择 <b>{{ selectedRows.length }}</b> 个词条，请输入统一的标准术语：
      </p>
      <el-input v-model="batchStandardTerm" placeholder="标准医学术语" />
      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchSubmitting" @click="doBatchApprove">确认标注</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import EmptyState from '@/components/EmptyState.vue'
import { adminService } from '@/api/services/admin.service'
import type { UnmappedTermItem, AdminStats } from '@/api/services/admin.service'

// ========== Props & Emits ==========
const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

// ========== 数据状态 ==========
const terms = ref<UnmappedTermItem[]>([])
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
/** 词条列表加载失败状态（区分"加载失败"与"暂无数据"） */
const listError = ref(false)
/** 统计卡片加载失败状态 */
const statsError = ref(false)
const filterStatus = ref('PENDING')
const searchKeyword = ref('')
const selectedRows = ref<UnmappedTermItem[]>([])
const approveInputs = ref<Record<number, string>>({})

const stats = ref<AdminStats>({
  pendingCount: 0,
  approvedTodayCount: 0,
  unmappedRate: 0,
  alertThreshold: 5.0,
  alertTriggered: false,
  totalAlertCount: 0,
  topUnmappedTerms: []
})

// 批量标注
const batchDialogVisible = ref(false)
const batchStandardTerm = ref('')
const batchSubmitting = ref(false)

// ========== 过滤后的数据 ==========
const displayTerms = computed(() => {
  if (!searchKeyword.value) return terms.value
  const kw = searchKeyword.value.toLowerCase()
  return terms.value.filter(
    (t) =>
      t.term.toLowerCase().includes(kw) ||
      (t.contextQuery && t.contextQuery.toLowerCase().includes(kw)) ||
      (t.llmGuess && t.llmGuess.toLowerCase().includes(kw))
  )
})

// ========== 方法 ==========
async function loadData() {
  listError.value = false
  try {
    // 页面自动加载：silent 抑制全局弹窗，由本页错误态呈现
    const res = await adminService.getUnmappedTerms(
      {
        page: currentPage.value,
        size: pageSize.value,
        status: filterStatus.value === 'ALL' ? undefined : filterStatus.value
      },
      { silent: true }
    )
    terms.value = res.data || []
    total.value = res.total
    currentPage.value = res.page
  } catch (e: any) {
    listError.value = true
    console.error('加载词条列表失败:', e)
  }
}

async function loadStats() {
  statsError.value = false
  try {
    // 传 silent 避免全局弹窗噪音，失败由 statsError 局部呈现
    stats.value = await adminService.getStats({ silent: true })
  } catch (e) {
    statsError.value = true
    console.error('加载统计数据失败:', e)
  }
}

async function approveOne(row: UnmappedTermItem) {
  const standardTerm = approveInputs.value[row.id]?.trim()
  if (!standardTerm) {
    ElMessage.warning('请输入标准术语')
    return
  }
  try {
    await adminService.approveTerm(row.id, standardTerm)
    ElMessage.success(`标注成功: ${row.term} → ${standardTerm}`)
    delete approveInputs.value[row.id]
    await loadData()
    await loadStats()
  } catch (e: any) {
    console.error('标注失败:', e)
    ElMessage.error('标注失败，请稍后重试')
  }
}

async function rejectOne(row: UnmappedTermItem) {
  try {
    await ElMessageBox({
      title: '确认拒绝',
      message: `确定拒绝词条 "${row.term}" 吗？`,
      confirmButtonText: '拒绝',
      cancelButtonText: '取消',
      type: 'warning',
      showCancelButton: true
    })
    await adminService.rejectTerm(row.id, '审核拒绝')
    ElMessage.info(`已拒绝: ${row.term}`)
    await loadData()
    await loadStats()
  } catch (e: any) {
    // 用户取消对话框不提示；其余为接口异常
    if (e === 'cancel' || e === 'close') return
    console.error('拒绝词条失败:', e)
    ElMessage.error('拒绝失败，请稍后重试')
  }
}

function showBatchApprove() {
  batchStandardTerm.value = ''
  batchDialogVisible.value = true
}

async function doBatchApprove() {
  const term = batchStandardTerm.value?.trim()
  if (!term) {
    ElMessage.warning('请输入标准术语')
    return
  }
  batchSubmitting.value = true
  try {
    const requests = selectedRows.value.map((r) => ({
      id: r.id,
      standardTerm: term
    }))
    const result = await adminService.batchApprove(requests)
    ElMessage.success(`批量标注完成: 成功 ${result.success} 条`)
    batchDialogVisible.value = false
    selectedRows.value = []
    batchStandardTerm.value = ''
    await loadData()
    await loadStats()
  } catch (e: any) {
    console.error('批量标注失败:', e)
    ElMessage.error('批量标注失败，请稍后重试')
  } finally {
    batchSubmitting.value = false
  }
}

function handleSelectionChange(rows: UnmappedTermItem[]) {
  selectedRows.value = rows
}

function filterTable() {
  currentPage.value = 1
  loadData()
}

function formatDate(dateStr: string): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

function handleClose() {
  visible.value = false
}

// ========== 生命周期 ==========
watch(visible, (val) => {
  if (val) {
    currentPage.value = 1
    loadData()
    loadStats()
  }
})

watch(
  () => filterStatus.value,
  () => {
    currentPage.value = 1
    loadData()
  }
)
</script>

<style scoped>
.stats-row {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.stat-card {
  flex: 1;
  padding: 12px 16px;
  border-radius: 8px;
  background: var(--theme-surface);
  border: 1px solid #e5e7eb;
  text-align: center;
}
.stat-card.stat-alert {
  background: #fef2f2;
  border-color: #fecaca;
}
.stat-label {
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 4px;
}
.stat-value {
  font-size: 20px;
  font-weight: 600;
  color: #111827;
}
.stat-alert-text {
  color: var(--theme-error) !important;
}
.filter-bar {
  display: flex;
  align-items: center;
}
.action-cell {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: nowrap;
  white-space: nowrap;
}
/* 编辑输入框在操作列中禁止压缩，确保完整显示 */
.action-cell :deep(.el-input) {
  flex-shrink: 0;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.stats-error-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
</style>
