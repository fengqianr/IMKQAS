<template>
  <div class="contraindication-page">
    <!-- 主内容区域（导航由外部布局框架提供） -->
    <div class="contraindication-content">
      <!-- 统计概览 + 操作按钮 -->
      <section class="stats-row">
        <div class="stat-card">
          <p class="stat-label">活跃规则总数</p>
          <h3 class="stat-value stat-value-primary">{{ statsTotal }}</h3>
          <div class="stat-footer stat-footer-trend">
            <span class="material-symbols-outlined icon-sm">trending_up</span>
            <span>本月新增 {{ statsNewThisMonth }} 条</span>
          </div>
        </div>
        <div class="stat-card">
          <p class="stat-label">禁用等级规则</p>
          <h3 class="stat-value stat-value-error">{{ statsAbsolute }}</h3>
          <div class="stat-footer">
            <span>占比规则库 {{ absolutePercent }}%</span>
          </div>
        </div>
        <div class="stat-card">
          <p class="stat-label">相对禁忌规则</p>
          <h3 class="stat-value stat-value-tertiary">{{ statsTotal - statsAbsolute }}</h3>
          <div class="stat-footer">
            <span>占比规则库 {{ (100 - parseFloat(absolutePercent)).toFixed(1) }}%</span>
          </div>
        </div>
        <div class="stat-actions">
          <button class="btn-secondary" @click="openBatchImport">
            <span class="material-symbols-outlined text-[20px]">upload_file</span>
            <span>批量导入</span>
          </button>
          <button class="btn-primary" @click="openCreateDialog">
            <span class="material-symbols-outlined text-[20px]">add</span>
            <span>新增规则</span>
          </button>
        </div>
      </section>

      <!-- 统计加载失败提示 -->
      <p v-if="statsError" class="stats-error-hint">
        <span class="material-symbols-outlined text-sm">error</span>
        <span>统计数据加载失败</span>
        <button class="stats-error-retry" @click="loadStats">重试</button>
      </p>

      <!-- 筛选栏 -->
      <section class="filter-bar">
        <div class="filter-field filter-field-grow">
          <label class="filter-label">药物名称</label>
          <input
            v-model="searchDrugName"
            class="filter-input"
            placeholder="输入通用名或商品名"
            type="text"
            @input="debouncedSearch"
          />
        </div>
        <div class="filter-field filter-field-48">
          <label class="filter-label">适用人群</label>
          <select v-model="searchPopulation" class="filter-select" @change="loadRules">
            <option value="">全部人群</option>
            <option value="妊娠期女性">妊娠期女性</option>
            <option value="老年患者">老年患者</option>
            <option value="肝功能受损">肝功能受损</option>
            <option value="肾功能受损">肾功能受损</option>
            <option value="哺乳期">哺乳期</option>
          </select>
        </div>
        <div class="filter-field filter-field-40">
          <label class="filter-label">严重程度</label>
          <select v-model="searchType" class="filter-select" @change="loadRules">
            <option value="">所有等级</option>
            <option value="ABSOLUTE">禁用 (Absolute)</option>
            <option value="RELATIVE">慎用 (Relative)</option>
            <option value="CAUTION">慎用</option>
            <option value="UNCLEAR">尚不明确</option>
          </select>
        </div>
        <button class="btn-apply" @click="loadRules">应用筛选</button>
        <button class="btn-reset" @click="resetFilters">重置</button>
      </section>

      <!-- 规则表格：填满剩余空间 -->
      <div class="table-container">
        <div class="table-scroll">
          <table class="rules-table">
            <thead>
              <tr>
                <th>药物名称</th>
                <th>目标人群</th>
                <th>禁忌类型</th>
                <th>风险描述</th>
                <th>证据等级</th>
                <th>最后更新</th>
                <th>状态</th>
                <th class="th-actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="8">
                  <LoadingState text="加载中..." />
                </td>
              </tr>
              <tr v-else-if="listError">
                <td colspan="8">
                  <EmptyState title="加载失败" icon="error" description="规则列表加载失败，请稍后重试。">
                    <button class="btn-empty-create" @click="loadRules">
                      <span class="material-symbols-outlined text-lg">refresh</span>
                      重新加载
                    </button>
                  </EmptyState>
                </td>
              </tr>
              <tr v-else-if="rules.length === 0">
                <td colspan="8">
                  <EmptyState title="暂无禁忌规则数据" icon="clinical_notes">
                    <button class="btn-empty-create" @click="openCreateDialog">
                      <span class="material-symbols-outlined text-lg">add</span>
                      新增第一条规则
                    </button>
                  </EmptyState>
                </td>
              </tr>
              <tr v-for="rule in rules" :key="rule.id" class="data-row">
                <td>
                  <div class="drug-name">{{ rule.drugName }}</div>
                  <div class="drug-atc">{{ rule.atcCode ? 'ATC: ' + rule.atcCode : '-' }}</div>
                </td>
                <td>
                  <span class="pill" :class="populationPillClass(rule.populationName)">{{ rule.populationName }}</span>
                </td>
                <td>
                  <span class="pill" :class="typePillClass(rule.contraindicationType)">{{
                    typeLabel(rule.contraindicationType)
                  }}</span>
                </td>
                <td class="td-desc">
                  <p class="desc-text">{{ rule.description || '-' }}</p>
                </td>
                <td>
                  <span class="pill pill-evidence">{{ evidenceLabel(rule.evidenceLevel) }}</span>
                </td>
                <td class="td-date">{{ formatShortDate(rule.updatedAt) }}</td>
                <td>
                  <span class="pill" :class="statusClass(rule)">{{ statusLabel(rule) }}</span>
                </td>
                <td class="td-actions">
                  <button class="action-btn action-btn-edit" title="编辑" @click="openEditDialog(rule)">
                    <span class="material-symbols-outlined text-lg">edit</span>
                  </button>
                  <button class="action-btn action-btn-delete" title="删除" @click="handleDelete(rule)">
                    <span class="material-symbols-outlined text-lg">delete</span>
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 分页：固定在底部 -->
        <Pager
          v-model:current="page"
          :total-pages="totalPages"
          :info="`显示 ${(page - 1) * size + 1} 到 ${Math.min(page * size, total)} 条，共 ${total} 条规则`"
          @change="loadRules"
        />
      </div>
    </div>

    <!-- 背景装饰 -->
    <div class="bg-decor bg-decor-tr" />
    <div class="bg-decor bg-decor-bl" />

    <!-- 新增/编辑对话框 -->
    <Teleport to="body">
      <div v-if="dialogVisible" class="modal-overlay" @click.self="dialogVisible = false">
        <div class="modal-panel">
          <h3 class="modal-title">{{ editingRule ? '编辑规则' : '新增规则' }}</h3>
          <div class="modal-body">
            <div class="form-group">
              <label class="form-label">药物名称 <span class="required">*</span></label>
              <input v-model="form.drugName" class="form-input" placeholder="如：布洛芬" />
            </div>
            <div class="form-group">
              <label class="form-label">ATC编码</label>
              <input v-model="form.atcCode" class="form-input" placeholder="如：M01AE01" />
            </div>
            <div class="form-group">
              <label class="form-label">人群名称 <span class="required">*</span></label>
              <input v-model="form.populationName" class="form-input" placeholder="如：孕妇" />
            </div>
            <div class="form-group">
              <label class="form-label">禁忌类型 <span class="required">*</span></label>
              <select v-model="form.contraindicationType" class="form-select">
                <option value="ABSOLUTE">绝对禁用</option>
                <option value="RELATIVE">相对禁忌</option>
                <option value="CAUTION">慎用</option>
                <option value="UNCLEAR">尚不明确</option>
              </select>
            </div>
            <div class="form-group">
              <label class="form-label">证据等级</label>
              <select v-model="form.evidenceLevel" class="form-select">
                <option value="">-- 不指定 --</option>
                <option value="GUIDELINE">临床指南</option>
                <option value="DRUG_LABEL">药品说明书</option>
                <option value="RCT">随机对照试验</option>
                <option value="META_ANALYSIS">荟萃分析</option>
                <option value="EXPERT_CONSENSUS">专家共识</option>
                <option value="CASE_REPORT">病例报告</option>
              </select>
            </div>
            <div class="form-group">
              <label class="form-label">风险描述</label>
              <textarea v-model="form.description" class="form-textarea" rows="3" placeholder="规则描述..." />
            </div>
            <div class="form-group">
              <label class="form-label">来源</label>
              <input v-model="form.source" class="form-input" placeholder="如：NMPA药品说明书 2023版" />
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn-cancel" @click="dialogVisible = false">取消</button>
            <button class="btn-save" @click="handleSave">保存</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 批量导入对话框 -->
    <Teleport to="body">
      <div v-if="importDialogVisible" class="modal-overlay" @click.self="importDialogVisible = false">
        <div class="modal-panel">
          <h3 class="modal-title">批量导入</h3>
          <p class="import-hint">每行一条，格式：<code>药物名,ATC编码,人群名,禁忌类型,证据等级,描述</code></p>
          <textarea
            v-model="importText"
            class="form-textarea"
            rows="10"
            placeholder="布洛芬,M01AE01,孕妇,ABSOLUTE,GUIDELINE,孕妇禁用布洛芬&#10;四环素,J01AA07,儿童,ABSOLUTE,DRUG_LABEL,儿童禁用四环素类抗生素"
          />
          <div
            v-if="importResult"
            class="import-result"
            :class="importResult.skipped > 0 ? 'import-result-warn' : 'import-result-success'"
          >
            <span class="material-symbols-outlined text-lg">{{
              importResult.skipped > 0 ? 'warning' : 'check_circle'
            }}</span>
            导入完成：成功 <b>{{ importResult.success }}</b> 条，跳过 <b>{{ importResult.skipped }}</b> 条
          </div>
          <div class="modal-footer">
            <button class="btn-cancel" @click="importDialogVisible = false">关闭</button>
            <button class="btn-save" @click="handleBatchImport">导入</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
  <!-- /contraindication-content -->
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  contraindicationService,
  type ContraindicationRule,
  type BatchImportResult
} from '@/api/services/contraindication.service'
import Pager from '@/components/Pager.vue'
import LoadingState from '@/components/LoadingState.vue'
import EmptyState from '@/components/EmptyState.vue'

const rules = ref<ContraindicationRule[]>([])
const loading = ref(false)
/** 规则列表加载失败状态（区分"加载失败"与"暂无数据"） */
const listError = ref(false)
/** 统计卡片加载失败状态 */
const statsError = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const totalPages = ref(0)

const searchDrugName = ref('')
const searchPopulation = ref('')
const searchType = ref('')

// 统计数据
const statsTotal = ref(0)
const statsAbsolute = ref(0)
const statsNewThisMonth = ref(0)

const absolutePercent = computed(() => {
  // total 由后端 Long 序列化为字符串，需 Number 归一后再比较（避免 "0" === 0 为 false 导致除以 0 得 Infinity）
  if (Number(statsTotal.value) === 0) return '0.0'
  return ((statsAbsolute.value / statsTotal.value) * 100).toFixed(1)
})

const dialogVisible = ref(false)
const editingRule = ref<ContraindicationRule | null>(null)
const form = ref<ContraindicationRule>({
  drugName: '',
  atcCode: '',
  populationName: '',
  contraindicationType: 'ABSOLUTE',
  evidenceLevel: '',
  description: '',
  source: ''
})

const importDialogVisible = ref(false)
const importText = ref('')
const importResult = ref<BatchImportResult | null>(null)

let searchTimeout: ReturnType<typeof setTimeout> | null = null

function debouncedSearch() {
  if (searchTimeout) clearTimeout(searchTimeout)
  searchTimeout = setTimeout(() => {
    page.value = 1
    loadRules()
  }, 400)
}

function resetFilters() {
  searchDrugName.value = ''
  searchPopulation.value = ''
  searchType.value = ''
  page.value = 1
  loadRules()
}

async function loadStats() {
  statsError.value = false
  try {
    // 页面自动加载：silent 抑制全局弹窗，由 statsError 局部呈现
    const [allResult, absoluteResult] = await Promise.all([
      contraindicationService.list({ page: 1, size: 1 }, { silent: true }),
      contraindicationService.list({ page: 1, size: 1, contraindicationType: 'ABSOLUTE' }, { silent: true })
    ])
    statsTotal.value = allResult.total
    statsAbsolute.value = absoluteResult.total
  } catch {
    statsError.value = true
    // 统计加载失败不影响主流程
  }
}

async function loadRules() {
  loading.value = true
  listError.value = false
  try {
    // 页面自动加载：silent 抑制全局弹窗，由本页错误态呈现
    const result = await contraindicationService.list(
      {
        page: page.value,
        size: size.value,
        drugName: searchDrugName.value || undefined,
        populationName: searchPopulation.value || undefined,
        contraindicationType: searchType.value || undefined
      },
      { silent: true }
    )
    rules.value = result.data
    total.value = result.total
    totalPages.value = result.totalPages
    // 同步更新统计
    statsTotal.value = result.total
  } catch (e: any) {
    listError.value = true
    console.error('加载禁忌规则失败:', e)
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  editingRule.value = null
  form.value = {
    drugName: '',
    atcCode: '',
    populationName: '',
    contraindicationType: 'ABSOLUTE',
    evidenceLevel: '',
    description: '',
    source: ''
  }
  dialogVisible.value = true
}

function openEditDialog(rule: ContraindicationRule) {
  editingRule.value = rule
  form.value = { ...rule }
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.value.drugName.trim() || !form.value.populationName.trim()) {
    ElMessage.warning('药物名称和人群名称不能为空')
    return
  }
  if (!form.value.contraindicationType) {
    ElMessage.warning('请选择禁忌类型')
    return
  }
  try {
    if (editingRule.value?.id) {
      await contraindicationService.update(editingRule.value.id, form.value)
      ElMessage.success('更新成功')
    } else {
      await contraindicationService.create(form.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await loadRules()
    await loadStats()
  } catch (e: any) {
    // 写操作失败已由拦截器统一弹窗（后端 message），此处只记录日志避免重复提示
    console.error('保存禁忌规则失败:', e)
  }
}

async function handleDelete(rule: ContraindicationRule) {
  if (!confirm(`确定删除规则"${rule.drugName} - ${rule.populationName}"吗？`)) return
  try {
    await contraindicationService.delete(rule.id!)
    ElMessage.success('删除成功')
    await loadRules()
    await loadStats()
  } catch (e: any) {
    // 写操作失败已由拦截器统一弹窗，此处只记录日志避免重复提示
    console.error('删除禁忌规则失败:', e)
  }
}

function openBatchImport() {
  importText.value = ''
  importResult.value = null
  importDialogVisible.value = true
}

async function handleBatchImport() {
  if (!importText.value.trim()) {
    ElMessage.warning('请输入导入数据')
    return
  }
  const lines = importText.value
    .trim()
    .split('\n')
    .filter((l) => l.trim())
  const importRules: ContraindicationRule[] = lines
    .map((line) => {
      const parts = line.split(',').map((s) => s.trim())
      return {
        drugName: parts[0] || '',
        atcCode: parts[1] || '',
        populationName: parts[2] || '',
        contraindicationType: parts[3] || 'ABSOLUTE',
        evidenceLevel: parts[4] || '',
        description: parts[5] || '',
        isActive: 1
      }
    })
    .filter((r) => r.drugName && r.populationName)

  if (importRules.length === 0) {
    ElMessage.warning('没有有效的规则数据')
    return
  }
  try {
    importResult.value = await contraindicationService.batchImport(importRules)
    ElMessage.success(`导入完成: 成功 ${importResult.value.success} 条`)
    await loadRules()
    await loadStats()
  } catch (e: any) {
    // 写操作失败已由拦截器统一弹窗，此处只记录日志避免重复提示
    console.error('导入禁忌规则失败:', e)
  }
}

function typeLabel(type: string): string {
  const map: Record<string, string> = {
    ABSOLUTE: '禁用',
    RELATIVE: '慎用',
    CAUTION: '慎用',
    UNCLEAR: '尚不明确'
  }
  return map[type] || type
}

function typePillClass(type: string): string {
  const map: Record<string, string> = {
    ABSOLUTE: 'pill-danger',
    RELATIVE: 'pill-warning',
    CAUTION: 'pill-caution',
    UNCLEAR: 'pill-muted'
  }
  return map[type] || 'pill-muted'
}

function populationPillClass(name: string): string {
  const kidney = ['严重肾功能不全', '肾功能受损', '肾功能不全']
  if (kidney.some((k) => name.includes(k))) return 'pill-kidney'
  return 'pill-population'
}

function evidenceLabel(level?: string): string {
  const map: Record<string, string> = {
    GUIDELINE: '指南',
    DRUG_LABEL: '说明书',
    RCT: 'RCT',
    META_ANALYSIS: '荟萃分析',
    EXPERT_CONSENSUS: '专家共识',
    CASE_REPORT: '病例报告'
  }
  return level ? map[level] || level : '-'
}

function statusClass(rule: ContraindicationRule): string {
  if (rule.status === 'reviewing') return 'pill-warning'
  if (rule.isActive === 1) return 'pill-success'
  return 'pill-muted'
}

function statusLabel(rule: ContraindicationRule): string {
  if (rule.status === 'reviewing') return '审核中'
  if (rule.isActive === 1) return '已发布'
  return '草稿'
}

function formatShortDate(dateStr?: string): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return d.toISOString().split('T')[0]
}

onMounted(() => {
  loadRules()
  loadStats()
})
</script>

<style scoped>
/* ===== 页面根容器（嵌于布局内容区，自然撑开高度） ===== */
.contraindication-page {
  display: flex;
  flex-direction: column;
  min-height: 100%;
  background-color: var(--theme-surface);
}

/* ===== 主内容区域 ===== */
.contraindication-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 1.5rem 0 0;
  overflow: visible;
}

/* ===== 统计卡片行 ===== */
.stats-row {
  display: flex;
  gap: 1rem;
  margin-bottom: 1rem;
  flex-shrink: 0;
}

.stat-card {
  flex: 1;
  min-width: 0;
  background: #ffffff;
  padding: 1.25rem 1.5rem;
  border-radius: 0.75rem;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  border: 1px solid rgba(114, 119, 131, 0.08);
}

.stat-label {
  font-size: 0.8125rem;
  font-weight: 500;
  color: var(--theme-outline);
  margin-bottom: 0.25rem;
}

.stat-value {
  font-size: 1.75rem;
  font-weight: 700;
  font-family: 'Manrope', sans-serif;
  letter-spacing: -0.025em;
}

.stat-value-primary {
  color: var(--theme-primary);
}
.stat-value-error {
  color: var(--theme-error);
}
.stat-value-tertiary {
  color: var(--theme-on-surface-variant);
}

.stat-footer {
  margin-top: 0.75rem;
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.75rem;
  color: var(--theme-outline);
}

.stat-footer-trend {
  color: #16a34a;
}

.stat-actions {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding-top: 0.25rem;
  flex-shrink: 0;
}

/* ===== 按钮 ===== */
.btn-primary {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.625rem 1.5rem;
  background: linear-gradient(135deg, var(--theme-primary), var(--theme-brand));
  color: #ffffff;
  font-weight: 600;
  font-size: 0.875rem;
  border: none;
  border-radius: 9999px;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(0, 71, 141, 0.25);
  transition: all 150ms;
  white-space: nowrap;
}

.btn-primary:hover {
  box-shadow: 0 6px 16px rgba(0, 71, 141, 0.35);
  transform: translateY(-1px);
}

.btn-primary:active {
  transform: scale(0.97);
}

.btn-secondary {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.625rem 1.25rem;
  background: #f1f5f9;
  color: #1e40af;
  font-weight: 600;
  font-size: 0.875rem;
  border: none;
  border-radius: 9999px;
  cursor: pointer;
  transition: all 150ms;
  white-space: nowrap;
}

.btn-secondary:hover {
  background: #e2e8f0;
}

.btn-secondary:active {
  transform: scale(0.97);
}

/* ===== 筛选栏 ===== */
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 1rem;
  padding: 1rem 1.25rem;
  background: #ffffff;
  border-radius: 0.75rem;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  border: 1px solid rgba(114, 119, 131, 0.08);
  margin-bottom: 1rem;
  flex-shrink: 0;
}

.filter-field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.filter-field-grow {
  flex: 1;
  min-width: 180px;
}

.filter-field-48 {
  width: 12rem;
}

.filter-field-40 {
  width: 10rem;
}

.filter-label {
  font-size: 0.6875rem;
  font-weight: 600;
  color: var(--theme-outline);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-left: 0.25rem;
}

.filter-input,
.filter-select {
  width: 100%;
  background: var(--theme-surface);
  border: none;
  border-radius: 0.5rem;
  font-size: 0.8125rem;
  padding: 0.5rem 0.75rem;
  color: var(--theme-on-surface);
  outline: none;
  transition: box-shadow 150ms;
  font-family: inherit;
}

.filter-input:focus,
.filter-select:focus {
  box-shadow: 0 0 0 2px rgba(0, 71, 141, 0.2);
}

.filter-input::placeholder {
  color: #94a3b8;
}

.btn-apply {
  padding: 0.5rem 1.5rem;
  background: var(--theme-primary);
  color: #ffffff;
  font-size: 0.8125rem;
  font-weight: 600;
  border: none;
  border-radius: 0.5rem;
  cursor: pointer;
  transition: background-color 150ms;
  white-space: nowrap;
}

.btn-apply:hover {
  background: var(--theme-brand);
}

.btn-reset {
  padding: 0.5rem 0.75rem;
  background: none;
  border: none;
  color: var(--theme-outline);
  font-size: 0.8125rem;
  font-weight: 500;
  cursor: pointer;
  transition: color 150ms;
  white-space: nowrap;
}

.btn-reset:hover {
  color: var(--theme-primary);
}

/* ===== 表格容器：flex-1 填满剩余空间 ===== */
.table-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border-radius: 0.75rem 0.75rem 0 0;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  border: 1px solid rgba(114, 119, 131, 0.08);
  border-bottom: none;
  overflow: hidden;
  min-height: 0;
}

.table-scroll {
  flex: 1;
  overflow-y: auto;
  overflow-x: auto;
}

/* ===== 表格 ===== */
.rules-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: auto;
}

.rules-table thead {
  position: sticky;
  top: 0;
  z-index: 10;
  background: #f1f5f9;
}

.rules-table th {
  padding: 0.75rem 1.25rem;
  font-size: 0.6875rem;
  font-weight: 700;
  color: var(--theme-outline);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  text-align: left;
  white-space: nowrap;
  border-bottom: 1px solid rgba(114, 119, 131, 0.1);
}

.th-actions {
  text-align: right;
}

.rules-table tbody tr:not(:last-child) {
  border-bottom: 1px solid rgba(114, 119, 131, 0.06);
}

.data-row:hover {
  background-color: rgba(0, 71, 141, 0.02);
}

.rules-table td {
  padding: 0.875rem 1.25rem;
  font-size: 0.8125rem;
  color: var(--theme-on-surface);
  vertical-align: middle;
}

.td-desc {
  max-width: 320px;
}

.td-date {
  color: var(--theme-outline);
  white-space: nowrap;
}

.td-actions {
  text-align: right;
  white-space: nowrap;
}

.drug-name {
  font-weight: 600;
  color: var(--theme-on-surface);
}

.drug-atc {
  font-size: 0.75rem;
  color: var(--theme-outline);
  margin-top: 0.125rem;
}

.desc-text {
  font-size: 0.8125rem;
  color: #424752;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* ===== 状态标签 ===== */
.pill {
  display: inline-flex;
  align-items: center;
  padding: 0.1875rem 0.625rem;
  border-radius: 0.375rem;
  font-size: 0.75rem;
  font-weight: 500;
  white-space: nowrap;
}

.pill-evidence {
  background: #dbeafe;
  color: #1e40af;
}

.pill-danger {
  background: rgba(220, 38, 38, 0.1);
  color: var(--theme-error);
}

.pill-warning {
  background: rgba(74, 95, 131, 0.1);
  color: var(--theme-on-surface-variant);
}

.pill-caution {
  background: #fffbeb;
  color: #92400e;
}

.pill-muted {
  background: #f1f5f9;
  color: var(--theme-outline);
}

.pill-success {
  background: rgba(22, 163, 74, 0.1);
  color: #16a34a;
}

.pill-population {
  background: #dbeafe;
  color: #1e40af;
}

.pill-kidney {
  background: #e0e7ff;
  color: #3730a3;
}

/* ===== 操作按钮 ===== */
.action-btn {
  padding: 0.375rem;
  border-radius: 9999px;
  border: none;
  background: none;
  cursor: pointer;
  transition: background-color 150ms;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.action-btn-edit {
  color: var(--theme-primary);
}

.action-btn-edit:hover {
  background: rgba(0, 71, 141, 0.08);
}

.action-btn-delete {
  color: var(--theme-error);
}

.action-btn-delete:hover {
  background: rgba(220, 38, 38, 0.08);
}

/* ===== 空状态操作按钮 ===== */
.btn-empty-create {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.5rem 1rem;
  font-size: 0.8125rem;
  font-weight: 500;
  color: var(--theme-primary);
  background: none;
  border: none;
  border-radius: 9999px;
  cursor: pointer;
  transition: background-color 150ms;
}

.btn-empty-create:hover {
  background: rgba(0, 71, 141, 0.06);
}

/* ===== 统计加载失败提示 ===== */
.stats-error-hint {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  margin-bottom: 0.75rem;
  font-size: 0.75rem;
  color: var(--theme-error);
}
.stats-error-retry {
  border: none;
  background: none;
  padding: 0;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--theme-primary);
  cursor: pointer;
  text-decoration: underline;
}
.stats-error-retry:hover {
  opacity: 0.8;
}

/* ===== 背景装饰 ===== */
.bg-decor {
  position: fixed;
  pointer-events: none;
  z-index: -10;
}

.bg-decor-tr {
  top: 0;
  right: 0;
  width: 800px;
  height: 800px;
  background: radial-gradient(circle at center, rgba(0, 71, 141, 0.06), transparent 70%);
  border-radius: 9999px;
  filter: blur(120px);
}

.bg-decor-bl {
  bottom: 0;
  left: 0;
  width: 600px;
  height: 600px;
  background: radial-gradient(circle at center, rgba(74, 95, 131, 0.05), transparent 70%);
  border-radius: 9999px;
  filter: blur(100px);
}

/* ===== 对话框 ===== */
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.3);
}

.modal-panel {
  background: #ffffff;
  border-radius: 1rem;
  padding: 2rem;
  width: 100%;
  max-width: 32rem;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  max-height: 85vh;
  overflow-y: auto;
}

.modal-title {
  font-family: 'Manrope', sans-serif;
  font-weight: 700;
  font-size: 1.125rem;
  color: var(--theme-on-surface);
  margin-bottom: 1.5rem;
}

.modal-body {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.form-group {
  display: flex;
  flex-direction: column;
}

.form-label {
  font-size: 0.75rem;
  font-weight: 500;
  color: var(--theme-outline);
  margin-bottom: 0.375rem;
}

.form-label .required {
  color: #f87171;
}

.form-input,
.form-select,
.form-textarea {
  width: 100%;
  padding: 0.625rem 0.875rem;
  background: var(--theme-surface);
  border: none;
  border-radius: 0.75rem;
  font-size: 0.8125rem;
  color: var(--theme-on-surface);
  outline: none;
  transition: box-shadow 150ms;
  font-family: inherit;
}

.form-input:focus,
.form-select:focus,
.form-textarea:focus {
  box-shadow: 0 0 0 2px rgba(0, 71, 141, 0.15);
}

.form-textarea {
  resize: vertical;
}

.modal-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 2rem;
}

.btn-cancel {
  padding: 0.625rem 1.25rem;
  font-size: 0.8125rem;
  font-weight: 500;
  color: var(--theme-outline);
  background: none;
  border: none;
  border-radius: 9999px;
  cursor: pointer;
  transition: background-color 150ms;
}

.btn-cancel:hover {
  background: #f1f5f9;
}

.btn-save {
  padding: 0.625rem 1.5rem;
  font-size: 0.8125rem;
  font-weight: 600;
  color: #ffffff;
  background: var(--theme-primary);
  border: none;
  border-radius: 9999px;
  cursor: pointer;
  transition: opacity 150ms;
}

.btn-save:hover {
  opacity: 0.9;
}

/* 批量导入结果提示 */
.import-result {
  margin-top: 1rem;
  padding: 1rem;
  border-radius: 0.75rem;
  font-size: 0.8125rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.import-result-warn {
  background: #fffbeb;
  color: #92400e;
  border: 1px solid #fde68a;
}

.import-result-success {
  background: #f0fdf4;
  color: #16a34a;
  border: 1px solid #bbf7d0;
}

.import-hint {
  font-size: 0.8125rem;
  color: var(--theme-outline);
  margin-bottom: 1rem;
  line-height: 1.6;
}

.import-hint code {
  font-size: 0.75rem;
  background: #f1f5f9;
  padding: 0.125rem 0.375rem;
  border-radius: 0.25rem;
}

/* ===== Material Icons ===== */
.material-symbols-outlined {
  font-variation-settings:
    'FILL' 0,
    'wght' 400,
    'GRAD' 0,
    'opsz' 24;
}

.icon-sm {
  font-size: 0.875rem;
}
</style>
