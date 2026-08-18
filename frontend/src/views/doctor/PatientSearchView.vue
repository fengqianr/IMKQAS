<template>
  <div class="patient-search-page">
    <!-- ===== Bento 风格统计卡（3 格）===== -->
    <section class="stats-grid">
      <!-- 卡 1：在册患者（真实数据）-->
      <div class="stat-card stat-card-primary">
        <div class="stat-head">
          <h3 class="stat-label">在册患者</h3>
          <span class="material-symbols-outlined stat-icon">supervised_user_circle</span>
        </div>
        <div class="stat-value">{{ totalCount.toLocaleString() }}</div>
        <div class="stat-foot">系统患者总数</div>
      </div>
      <!-- 卡 2：需关注（后端无接口 → 占位）-->
      <div class="stat-card stat-card-error">
        <div class="stat-head">
          <h3 class="stat-label">需关注</h3>
          <span class="material-symbols-outlined stat-icon">warning</span>
        </div>
        <div class="stat-value">—</div>
        <div class="stat-foot">数据接入中</div>
      </div>
      <!-- 卡 3：24 小时新增（后端无接口 → 占位）-->
      <div class="stat-card stat-card-plain">
        <div class="stat-head">
          <h3 class="stat-label">24 小时新增</h3>
          <span class="material-symbols-outlined stat-icon">person_add</span>
        </div>
        <div class="stat-value">—</div>
        <div class="stat-foot">数据接入中</div>
      </div>
    </section>

    <!-- ===== 组合搜索条 ===== -->
    <section class="search-bar">
      <div class="search-group">
        <el-select v-model="searchMode" class="mode-select" aria-label="检索方式">
          <el-option label="姓名" value="name" />
          <el-option label="手机号" value="phone" />
          <el-option label="证件号" value="id" />
        </el-select>
        <div class="search-input-wrap">
          <span class="material-symbols-outlined search-icon">search</span>
          <el-input
            v-model="keyword"
            :placeholder="placeholderText"
            clearable
            class="keyword-input"
            @keyup.enter="handleSearch(true)"
          />
        </div>
      </div>
      <div class="search-actions">
        <button type="button" class="btn-filter" title="高级筛选即将上线">
          <span class="material-symbols-outlined">filter_list</span>
          更多筛选
        </button>
        <el-button type="primary" class="search-btn" :loading="loading" @click="handleSearch(true)">
          搜索
        </el-button>
      </div>
    </section>

    <!-- ===== 患者名册表格 ===== -->
    <section class="table-card">
      <!-- 表头区 -->
      <div class="table-card-head">
        <h3 class="roster-title">
          患者名册
          <span class="count-badge">{{ patients.length }}</span>
        </h3>
      </div>

      <div v-if="patients.length" class="table-scroll">
        <table class="result-table">
          <thead>
            <tr class="table-head-row">
              <th>姓名</th>
              <th>性别/年龄</th>
              <th>联系方式</th>
              <th>证件号</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody class="table-body">
            <tr
              v-for="p in patients"
              :key="p.id"
              class="result-row"
              @click="goDetail(p.id)"
            >
              <td>
                <div class="name-cell">
                  <span class="avatar">{{ patientInitial(p) }}</span>
                  <span class="name-text">{{ patientName(p) }}</span>
                </div>
              </td>
              <td>
                <div class="gender-cell">
                  <span class="gender-badge">{{ genderText(p.gender) }}</span>
                  <span class="age-text">{{ ageOf(p) }} 岁</span>
                </div>
              </td>
              <td class="code-text">{{ maskPhone(phoneOf(p)) }}</td>
              <td class="code-text">{{ maskIdNumber(identifierOf(p)?.value) }}</td>
              <td>
                <span class="status-pill">
                  <span class="status-dot" />
                  已建档
                </span>
              </td>
              <td>
                <span class="detail-link">查看档案</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 分页（姓名检索才有翻页；手机号/证件号为单条结果） -->
      <div v-if="patients.length && searchMode === 'name'" class="pagination-bar">
        <span class="page-info">第 {{ page }} 页 · 本页 {{ patients.length }} 条结果</span>
        <div class="page-actions">
          <el-button class="page-btn" :disabled="page <= 1" @click="goPrev">
            <span class="material-symbols-outlined">chevron_left</span>
          </el-button>
          <span class="page-num">{{ page }}</span>
          <el-button class="page-btn" :disabled="!hasNext" @click="goNext">
            <span class="material-symbols-outlined">chevron_right</span>
          </el-button>
        </div>
      </div>

      <!-- 空态 -->
      <div v-if="!patients.length && !loading" class="empty-box">
        <div class="empty-icon">
          <span class="material-symbols-outlined">search_off</span>
        </div>
        <h3 class="empty-title">未找到匹配的患者档案</h3>
        <p class="empty-desc">请检查您输入的{{ placeholderText }}是否准确，或尝试使用其他检索条件。</p>
        <button class="btn-outline" @click="resetSearch">
          清除搜索条件
        </button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { fhirService } from '@/api/services/fhir.service'
import {
  type FhirPatient,
  patientInitial,
  patientName,
  genderText,
  calcAge,
  patientPhone,
  maskPhone,
  patientIdentifier,
  maskIdNumber
} from '@/api/types/fhir'

const router = useRouter()

/** 检索方式：姓名 / 手机号 / 证件号 */
const searchMode = ref<'name' | 'phone' | 'id'>('name')
const keyword = ref('')

/** 患者列表、加载态、总数 */
const patients = ref<FhirPatient[]>([])
const loading = ref(false)
const totalCount = ref(0)

/** 当前页码（从 1 开始显示；后端 page+1 约定，请求时传 page-1） */
const page = ref(1)
const pageSize = 10

/** 不同检索方式的占位提示 */
const placeholderText = computed(() => {
  const map: Record<string, string> = {
    name: '输入姓名，如：张伟',
    phone: '输入手机号，如：13812345678',
    id: '输入证件号，如：110105********341X'
  }
  return map[searchMode.value]
})

/** 下一页是否可用：当页返回满 size 时可能存在下一页 */
const hasNext = computed(() => patients.value.length >= pageSize)

/** 单个患者年龄 */
const ageOf = (p: FhirPatient) => {
  const age = calcAge(p.birthDate)
  return age === null ? '—' : age
}

const phoneOf = (p: FhirPatient) => patientPhone(p)
const identifierOf = (p: FhirPatient) => patientIdentifier(p)

// 执行搜索（resetPage 为 true 时回到第一页）
const handleSearch = async (resetPage = false) => {
  if (resetPage) page.value = 1
  loading.value = true
  try {
    const kw = keyword.value.trim()
    if (searchMode.value === 'name') {
      patients.value = await fhirService.searchByName(kw, page.value - 1, pageSize)
    } else if (searchMode.value === 'phone') {
      patients.value = await fhirService.findByPhone(kw)
    } else {
      patients.value = await fhirService.findByIdentifier(kw)
    }
  } finally {
    loading.value = false
  }
}

// 上一页
const goPrev = () => {
  if (page.value <= 1) return
  page.value -= 1
  handleSearch(false)
}

// 下一页
const goNext = () => {
  if (!hasNext.value) return
  page.value += 1
  handleSearch(false)
}

// 清除搜索条件
const resetSearch = () => {
  keyword.value = ''
  handleSearch(true)
}

// 跳转患者详情
const goDetail = (fhirId?: string) => {
  if (!fhirId) return
  router.push(`/patients/${fhirId}`)
}

// 初始化：加载患者总数 + 默认列出前 10 位患者
onMounted(async () => {
  totalCount.value = await fhirService.countPatients()
  handleSearch(false)
})
</script>

<style scoped>
/* ===== 页面容器 ===== */
.patient-search-page {
  max-width: 80rem;
  margin: 0 auto;
  padding-bottom: 3rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

/* ===== Bento 统计卡 ===== */
.stats-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1rem;
  flex-shrink: 0;
}

@media (min-width: 768px) {
  .stats-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

.stat-card {
  border-radius: 0.75rem;
  padding: 1.25rem;
  min-height: 8rem;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  transition: transform 200ms;
}

.stat-card:hover {
  transform: translateY(-2px);
}

.stat-card-primary {
  background: var(--theme-primary);
  color: var(--theme-on-primary);
  border: 1px solid transparent;
  position: relative;
  overflow: hidden;
}

/* 右上角柔光装饰（对齐设计稿） */
.stat-card-primary::after {
  content: '';
  position: absolute;
  right: -1rem;
  top: -1rem;
  width: 6rem;
  height: 6rem;
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.1);
  filter: blur(20px);
}

.stat-card-error {
  background: var(--theme-error-container);
  color: var(--theme-on-error-container);
  border: 1px solid rgba(186, 26, 26, 0.2);
}

.stat-card-plain {
  background: var(--theme-surface-container-lowest);
  color: var(--theme-on-surface);
  border: 1px solid var(--theme-outline-variant);
}

.stat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
  z-index: 1;
}

.stat-label {
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  opacity: 0.9;
}

.stat-icon {
  font-size: 1.25rem;
  opacity: 0.85;
}

.stat-card-error .stat-icon {
  color: var(--theme-error);
}

.stat-card-plain .stat-icon {
  color: var(--theme-secondary);
}

.stat-value {
  font-size: 2rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.15;
  position: relative;
  z-index: 1;
  font-variant-numeric: tabular-nums;
}

.stat-foot {
  font-size: 0.8125rem;
  opacity: 0.8;
  position: relative;
  z-index: 1;
}

/* ===== 组合搜索条 ===== */
.search-bar {
  background: var(--theme-surface-container-lowest);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.5rem;
  padding: 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  align-items: center;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  flex-shrink: 0;
}

@media (min-width: 640px) {
  .search-bar {
    flex-direction: row;
  }
}

.search-group {
  display: flex;
  width: 100%;
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.375rem;
  overflow: hidden;
  transition: border-color 150ms, box-shadow 150ms;
}

.search-group:focus-within {
  border-color: var(--theme-primary);
  box-shadow: 0 0 0 1px var(--theme-primary);
}

.mode-select {
  width: 8.5rem;
  flex-shrink: 0;
  border-right: 1px solid var(--theme-outline-variant);
  background: var(--theme-surface-container-lowest);
}

.mode-select :deep(.el-select__wrapper) {
  box-shadow: none !important;
  background: transparent;
  border-radius: 0;
}

.search-input-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  position: relative;
  background: var(--theme-surface-container-lowest);
}

.search-icon {
  position: absolute;
  left: 0.75rem;
  font-size: 1.25rem;
  color: var(--theme-on-surface-variant);
  pointer-events: none;
  z-index: 1;
}

.keyword-input {
  flex: 1;
}

.keyword-input :deep(.el-input__wrapper) {
  box-shadow: none !important;
  background: transparent;
  border-radius: 0;
  padding-left: 2.5rem;
}

.search-actions {
  display: flex;
  gap: 0.5rem;
  width: 100%;
}

@media (min-width: 640px) {
  .search-actions {
    width: auto;
    margin-left: auto;
    flex-shrink: 0;
  }
}

.btn-filter {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.375rem;
  padding: 0.5rem 1rem;
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.375rem;
  background: var(--theme-surface-container-lowest);
  color: var(--theme-on-surface);
  font-size: 0.8125rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 150ms;
  white-space: nowrap;
}

@media (min-width: 640px) {
  .btn-filter {
    flex: none;
  }
}

.btn-filter:hover {
  background: var(--theme-surface-container);
}

.btn-filter .material-symbols-outlined {
  font-size: 1.125rem;
}

.search-btn {
  flex-shrink: 0;
}

/* ===== 表格卡片 ===== */
.table-card {
  background: var(--theme-surface-container-lowest);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.75rem;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  min-height: 400px;
}

.table-card-head {
  padding: 1rem 1.25rem;
  border-bottom: 1px solid var(--theme-outline-variant);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.roster-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--theme-on-surface);
  letter-spacing: -0.01em;
}

.count-badge {
  font-size: 0.6875rem;
  font-weight: 600;
  padding: 0.125rem 0.5rem;
  border-radius: 9999px;
  background: var(--theme-primary-soft);
  color: var(--theme-primary);
}

.table-scroll {
  flex: 1;
  overflow: auto;
}

.result-table {
  width: 100%;
  border-collapse: collapse;
  text-align: center;
}

/* 粘性表头 */
.table-head-row th {
  position: sticky;
  top: 0;
  z-index: 10;
  background: var(--theme-surface-container-low);
  border-bottom: 1px solid var(--theme-outline-variant);
  padding: 0.75rem 1.25rem;
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--theme-on-surface-variant);
  white-space: nowrap;
  
}

.table-body {
  font-size: 0.875rem;
}

.result-row {
  cursor: pointer;
  transition: background 150ms, border-color 150ms;
  border-bottom: 1px solid rgba(195, 198, 208, 0.5);
  border-left: 2px solid transparent;
}

.result-row:last-child {
  border-bottom: none;
}

.result-row:hover {
  background: var(--theme-primary-soft);
  border-left-color: var(--theme-primary);
}

.result-row td {
  padding: 0.75rem 1.25rem;
  vertical-align: middle;
}

.name-cell {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.avatar {
  width: 1.5rem;
  height: 1.5rem;
  border-radius: 0.25rem;
  background: var(--theme-primary-container);
  color: var(--theme-secondary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.6875rem;
  font-weight: 600;
  flex-shrink: 0;
}

.name-text {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--theme-on-surface);
  transition: color 150ms;
}

.result-row:hover .name-text {
  color: var(--theme-primary);
}

.gender-cell {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.gender-badge {
  padding: 0.125rem 0.5rem;
  border-radius: 0.25rem;
  background: var(--theme-surface-container);
  border: 1px solid var(--theme-outline-variant);
  font-size: 0.6875rem;
  font-family: 'JetBrains Mono', monospace;
  color: var(--theme-on-surface-variant);
  white-space: nowrap;
}

.age-text {
  font-size: 0.875rem;
  color: var(--theme-on-surface);
}

.code-text {
  font-size: 0.75rem;
  font-family: 'JetBrains Mono', monospace;
  color: var(--theme-on-surface-variant);
  white-space: nowrap;
}

/* 状态 pill：无真实状态字段 → 中性「已建档」 */
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.125rem 0.625rem;
  border-radius: 9999px;
  background: var(--theme-primary-soft);
  color: var(--theme-primary);
  font-size: 0.6875rem;
  font-weight: 600;
  border: 1px solid var(--theme-outline-variant);
  white-space: nowrap;
}

.status-dot {
  width: 0.375rem;
  height: 0.375rem;
  border-radius: 9999px;
  background: var(--theme-secondary);
}

.detail-link {
  color: var(--theme-primary);
  font-size: 0.8125rem;
  font-weight: 600;
  padding: 0.25rem 0.5rem;
  border-radius: 0.375rem;
  white-space: nowrap;
}

.result-row:hover .detail-link {
  background: var(--theme-primary-soft);
}

.text-right {
  text-align: right;
}

/* ===== 分页 ===== */
.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1.25rem;
  border-top: 1px solid var(--theme-outline-variant);
}

.page-info {
  font-size: 0.75rem;
  color: var(--theme-on-surface-variant);
}

.page-actions {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.page-btn {
  padding: 0;
  width: 2rem;
  height: 2rem;
  border: 1px solid var(--theme-outline-variant);
  background: var(--theme-surface-container-lowest);
  color: var(--theme-on-surface-variant);
  border-radius: 0.375rem;
  display: flex;
  align-items: center;
  justify-content: center;
}

.page-btn:disabled {
  opacity: 0.5;
}

.page-btn:hover:not(:disabled) {
  background: var(--theme-surface-container);
}

.page-num {
  min-width: 1.75rem;
  height: 1.75rem;
  border-radius: 0.375rem;
  background: var(--theme-primary);
  color: var(--theme-on-primary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.8125rem;
  font-weight: 600;
}

/* ===== 空态 ===== */
.empty-box {
  flex: 1;
  min-height: 16rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  text-align: center;
  padding: 2rem;
}

.empty-icon {
  width: 4rem;
  height: 4rem;
  border-radius: 9999px;
  background: var(--theme-surface-container-low);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--theme-primary);
  margin-bottom: 0.5rem;
}

.empty-icon .material-symbols-outlined {
  font-size: 2.25rem;
}

.empty-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--theme-on-surface);
}

.empty-desc {
  font-size: 0.875rem;
  color: var(--theme-outline);
  max-width: 28rem;
  line-height: 1.6;
  margin-bottom: 1rem;
}

.btn-outline {
  padding: 0.5rem 1rem;
  background: var(--theme-surface-container-lowest);
  border: 1px solid var(--theme-outline-variant);
  color: var(--theme-on-surface-variant);
  border-radius: 0.375rem;
  font-size: 0.8125rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 150ms;
}

.btn-outline:hover {
  background: var(--theme-surface-container);
}

/* Material Symbols 字体设置 */
.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}
</style>
