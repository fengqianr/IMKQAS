<template>
  <div class="patient-search-page">
    <!-- 页头 -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">患者检索</h1>
        <p class="page-subtitle">通过姓名、手机号或证件号查找患者档案</p>
      </div>
      <div class="stat-box">
        <span class="material-symbols-outlined stat-icon">group</span>
        <span class="stat-text">
          系统患者总数: <b class="stat-num">{{ totalCount.toLocaleString() }}</b> 位
        </span>
      </div>
    </div>

    <!-- 搜索卡片 -->
    <div class="search-card">
      <div class="search-row">
        <div class="search-mode">
          <label class="field-label">检索方式</label>
          <el-select v-model="searchMode" class="mode-select">
            <el-option label="姓名" value="name" />
            <el-option label="手机号" value="phone" />
            <el-option label="证件号" value="id" />
          </el-select>
        </div>
        <div class="search-keyword">
          <label class="field-label">搜索关键字</label>
          <div class="input-group">
            <el-input
              v-model="keyword"
              :placeholder="placeholderText"
              clearable
              class="keyword-input"
              @keyup.enter="handleSearch(true)"
            >
              <template #prefix>
                <span class="material-symbols-outlined input-prefix-icon">search</span>
              </template>
            </el-input>
            <el-button type="primary" class="search-btn" :loading="loading" @click="handleSearch(true)">
              搜索
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 检索结果 -->
    <div class="results-section">
      <h3 class="results-heading">检索结果 ({{ patients.length }})</h3>

      <!-- 结果表格 -->
      <div v-if="patients.length" class="table-card">
        <div class="overflow-x-auto">
          <table class="result-table">
            <thead>
              <tr class="table-head-row">
                <th>姓名</th>
                <th>性别/年龄</th>
                <th>手机号</th>
                <th>证件号</th>
                <th class="text-right">操作</th>
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
                <td class="text-right">
                  <span class="detail-link">查看档案</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 分页（姓名检索才有翻页；手机号/证件号为单条结果） -->
        <div v-if="searchMode === 'name'" class="pagination-bar">
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
      </div>

      <!-- 空态 -->
      <div v-else-if="!loading" class="empty-box">
        <div class="empty-icon">
          <span class="material-symbols-outlined">search_off</span>
        </div>
        <h3 class="empty-title">未找到匹配的患者档案</h3>
        <p class="empty-desc">请检查您输入的{{ placeholderText }}是否准确，或尝试使用其他检索条件。</p>
        <button class="btn-outline" @click="resetSearch">
          清除搜索条件
        </button>
      </div>
    </div>
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
}

/* ===== 页头 ===== */
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
  padding: 0 0 1.5rem;
  border-bottom: 1px solid #c2c6d4;
  margin-bottom: 1.5rem;
}

.page-title {
  font-size: 1.75rem;
  font-weight: 700;
  color: #191c1d;
  margin-bottom: 0.5rem;
  letter-spacing: -0.01em;
}

.page-subtitle {
  font-size: 0.875rem;
  color: #4a5f83;
}

.stat-box {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  background: #f2f4f6;
  padding: 0.5rem 1rem;
  border-radius: 0.375rem;
  border: 1px solid #c2c6d4;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  white-space: nowrap;
}

.stat-icon {
  color: #005eb8;
  font-size: 1.125rem;
}

.stat-text {
  font-size: 0.8125rem;
  font-weight: 600;
  color: #191c1d;
}

.stat-num {
  color: #005eb8;
  font-family: 'JetBrains Mono', monospace;
}

/* ===== 搜索卡片 ===== */
.search-card {
  background: #ffffff;
  border: 1px solid #c2c6d4;
  border-radius: 0.75rem;
  padding: 1.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  margin-bottom: 1.5rem;
}

.search-row {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

@media (min-width: 768px) {
  .search-row {
    flex-direction: row;
    align-items: flex-end;
  }
}

.search-mode {
  width: 100%;
  flex-shrink: 0;
}

@media (min-width: 768px) {
  .search-mode {
    width: 12rem;
  }
}

.search-keyword {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.field-label {
  font-size: 0.8125rem;
  font-weight: 600;
  color: #4a5f83;
  margin-bottom: 0.375rem;
  display: block;
}

.mode-select {
  width: 100%;
}

.input-group {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.keyword-input {
  flex: 1;
}

.search-btn {
  flex-shrink: 0;
}

.input-prefix-icon {
  font-size: 1.125rem;
  color: #6e797e;
}

/* ===== 结果区 ===== */
.results-section {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.results-heading {
  font-size: 0.8125rem;
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: #4a5f83;
}

.table-card {
  background: #ffffff;
  border: 1px solid #c2c6d4;
  border-radius: 0.75rem;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.result-table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
}

.table-head-row th {
  background: #f2f4f6;
  border-bottom: 1px solid #c2c6d4;
  padding: 0.75rem 1.5rem;
  font-size: 0.8125rem;
  font-weight: 600;
  color: #4a5f83;
}

.table-body {
  border-bottom: 1px solid #e2e8f0;
}

.result-row {
  cursor: pointer;
  transition: background 150ms;
  border-bottom: 1px solid #e2e8f0;
}

.result-row:hover {
  background: #f2f4f6;
}

.result-row td {
  padding: 1rem 1.5rem;
}

.name-cell {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.avatar {
  width: 2rem;
  height: 2rem;
  border-radius: 9999px;
  background: rgba(0, 94, 184, 0.1);
  color: #005eb8;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.8125rem;
  font-weight: 600;
  flex-shrink: 0;
}

.name-text {
  font-size: 0.875rem;
  font-weight: 600;
  color: #191c1d;
  transition: color 150ms;
}

.result-row:hover .name-text {
  color: #005eb8;
}

.gender-cell {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.gender-badge {
  padding: 0.125rem 0.5rem;
  border-radius: 9999px;
  background: #eceef0;
  border: 1px solid #c2c6d4;
  font-size: 0.75rem;
  font-family: 'JetBrains Mono', monospace;
  color: #4a5f83;
  white-space: nowrap;
}

.age-text {
  font-size: 0.875rem;
  color: #191c1d;
}

.code-text {
  font-size: 0.75rem;
  font-family: 'JetBrains Mono', monospace;
  color: #4a5f83;
}

.detail-link {
  color: #005eb8;
  font-size: 0.8125rem;
  font-weight: 600;
  padding: 0.25rem 0.5rem;
  border-radius: 0.375rem;
}

.result-row:hover .detail-link {
  background: rgba(0, 94, 184, 0.1);
}

.text-right {
  text-align: right;
}

/* ===== 分页 ===== */
.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1.5rem;
}

.page-info {
  font-size: 0.75rem;
  color: #4a5f83;
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
  border: 1px solid #c2c6d4;
  background: #ffffff;
  color: #4a5f83;
  border-radius: 0.375rem;
  display: flex;
  align-items: center;
  justify-content: center;
}

.page-btn:disabled {
  opacity: 0.5;
}

.page-btn:hover:not(:disabled) {
  background: #f2f4f6;
}

.page-num {
  min-width: 1.75rem;
  height: 1.75rem;
  border-radius: 0.375rem;
  background: #005eb8;
  color: #ffffff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.8125rem;
  font-weight: 600;
}

/* ===== 空态 ===== */
.empty-box {
  min-height: 20rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  text-align: center;
  background: #ffffff;
  border: 1px solid #c2c6d4;
  border-radius: 0.75rem;
  padding: 2rem;
}

.empty-icon {
  width: 4rem;
  height: 4rem;
  border-radius: 9999px;
  background: #f1f5f9;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #005eb8;
  margin-bottom: 0.5rem;
}

.empty-icon .material-symbols-outlined {
  font-size: 2.25rem;
}

.empty-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: #191c1d;
}

.empty-desc {
  font-size: 0.875rem;
  color: #727783;
  max-width: 28rem;
  line-height: 1.6;
  margin-bottom: 1rem;
}

.btn-outline {
  padding: 0.5rem 1rem;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  color: #4a5f83;
  border-radius: 0.375rem;
  font-size: 0.8125rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 150ms;
}

.btn-outline:hover {
  background: #f2f4f6;
}

@media (max-width: 640px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }
}

/* Material Symbols 字体设置 */
.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}
</style>
