<template>
  <div class="user-manage-page">
    <!-- 页头 -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">用户管理</h1>
        <p class="page-subtitle">管理系统用户账号与角色</p>
      </div>
      <el-button type="primary" class="add-btn" @click="openCreate">
        <span class="material-symbols-outlined add-icon">person_add</span>
        新增用户
      </el-button>
    </div>

    <!-- 搜索工具条 -->
    <div class="search-card">
      <div class="search-row">
        <div class="search-keyword">
          <el-input
            v-model="keyword"
            placeholder="搜索用户名或手机号..."
            clearable
            class="keyword-input"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          >
            <template #prefix>
              <span class="material-symbols-outlined input-prefix-icon">search</span>
            </template>
          </el-input>
        </div>
        <div class="role-filter">
          <el-select v-model="roleFilter" class="role-select" placeholder="全部角色" @change="page = 1">
            <el-option label="全部角色" value="all" />
            <el-option v-for="opt in ROLE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </div>
      </div>
    </div>

    <!-- 用户表格卡片 -->
    <div class="table-card">
      <div v-loading="loading" class="table-wrap">
        <table v-if="pagedUsers.length" class="result-table">
          <thead>
            <tr class="table-head-row">
              <th>用户</th>
              <th>手机号</th>
              <th>角色</th>
              <th>创建时间</th>
              <th class="text-right">操作</th>
            </tr>
          </thead>
          <tbody class="table-body">
            <tr v-for="user in pagedUsers" :key="user.id" class="result-row">
              <td>
                <div class="user-cell">
                  <span class="avatar" :class="avatarClass(user.role)">{{ avatarText(user.username) }}</span>
                  <span class="user-name">{{ user.username }}</span>
                </div>
              </td>
              <td class="code-text">{{ maskPhone(user.phone) }}</td>
              <td>
                <span class="role-badge" :class="roleBadgeClass(user.role)">{{ roleLabel(user.role) }}</span>
              </td>
              <td class="time-text">{{ formatTime(user.createdAt) }}</td>
              <td class="text-right">
                <div class="row-actions">
                  <button class="action-btn" @click="openEdit(user)">
                    <span class="material-symbols-outlined action-icon">edit</span>
                    编辑
                  </button>
                  <button class="action-btn danger" @click="handleDelete(user)">
                    <span class="material-symbols-outlined action-icon">delete</span>
                    删除
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <!-- 空态 -->
        <div v-else-if="!loading" class="empty-box">
          <div class="empty-icon">
            <span class="material-symbols-outlined">search_off</span>
          </div>
          <h3 class="empty-title">暂无匹配的用户</h3>
          <p class="empty-desc">请调整搜索条件，或尝试清除筛选。</p>
          <el-button class="empty-btn" @click="resetSearch">清除筛选</el-button>
        </div>
      </div>

      <!-- 分页 -->
      <div class="pagination-bar">
        <span class="page-info">显示 {{ pageStart }}-{{ pageEnd }} 条，共 {{ totalShown }} 位用户</span>
        <div class="page-actions">
          <el-button class="page-btn" :disabled="!hasPrev" @click="goPrev">
            <span class="material-symbols-outlined">chevron_left</span>
          </el-button>
          <span class="page-num">{{ page }}</span>
          <el-button class="page-btn" :disabled="!hasNext" @click="goNext">
            <span class="material-symbols-outlined">chevron_right</span>
          </el-button>
        </div>
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑用户' : '新增用户'"
      width="440px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top" class="user-form">
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" placeholder="请输入用户名" maxlength="30" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="请输入手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="form.role" class="form-role-select">
            <el-option v-for="opt in ROLE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="isEdit ? '密码（留空则不修改）' : '密码'" required>
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="至少 6 位"
            maxlength="32"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ isEdit ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { adminUserService, type UserItem, type UserUpsertRequest } from '@/api/services/admin-user.service'
import { ROLE_LABELS } from '@/config/menus'

/** 角色下拉选项（对齐后端 Role 枚举） */
const ROLE_OPTIONS = [
  { value: 'PATIENT', label: '患者' },
  { value: 'DOCTOR', label: '医生' },
  { value: 'ADMIN', label: '管理员' },
  { value: 'STUDENT', label: '学生' },
  { value: 'NURSE', label: '护士' },
  { value: 'HEALTH_MANAGER', label: '健康管理师' }
]

const PAGE_SIZE = 10
/** 单次拉取条数（后端分页 size 参数），再按 totalPages 循环拉全量供前端过滤 */
const FETCH_SIZE = 100

/** 全部已加载用户（关键词搜索或全量列表） */
const allUsers = ref<UserItem[]>([])
const keyword = ref('')
const roleFilter = ref('all')
const page = ref(1)
const loading = ref(false)

/** 前端角色过滤（后端 search 接口无角色参数，故全量加载后本地过滤） */
const filteredUsers = computed(() => {
  if (roleFilter.value === 'all') return allUsers.value
  return allUsers.value.filter((u) => u.role === roleFilter.value)
})

/** 前端分页切片 */
const pagedUsers = computed(() => {
  const start = (page.value - 1) * PAGE_SIZE
  return filteredUsers.value.slice(start, start + PAGE_SIZE)
})

const totalShown = computed(() => filteredUsers.value.length)
const pageStart = computed(() => (totalShown.value === 0 ? 0 : (page.value - 1) * PAGE_SIZE + 1))
const pageEnd = computed(() => Math.min(page.value * PAGE_SIZE, totalShown.value))
const hasPrev = computed(() => page.value > 1)
const hasNext = computed(() => page.value * PAGE_SIZE < totalShown.value)

const goPrev = () => {
  if (hasPrev.value) page.value -= 1
}
const goNext = () => {
  if (hasNext.value) page.value += 1
}

/** 按 totalPages 循环拉取全部结果（后端分页 → 前端全量，供本地过滤） */
const fetchAllPages = async (
  fetcher: (current: number, size: number) => Promise<{ data: UserItem[]; totalPages: number }>
): Promise<UserItem[]> => {
  const first = await fetcher(1, FETCH_SIZE)
  const users = [...first.data]
  for (let p = 2; p <= first.totalPages; p++) {
    const next = await fetcher(p, FETCH_SIZE)
    users.push(...next.data)
  }
  return users
}

/** 加载列表：有关键词走搜索，否则全量 */
const handleSearch = async () => {
  const kw = keyword.value.trim()
  loading.value = true
  try {
    if (kw) {
      allUsers.value = await fetchAllPages((c, s) => adminUserService.searchUsers(kw, c, s))
    } else {
      allUsers.value = await fetchAllPages((c, s) => adminUserService.listUsers(c, s))
    }
    page.value = 1
  } catch {
    ElMessage.error('获取用户列表失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const resetSearch = () => {
  keyword.value = ''
  roleFilter.value = 'all'
  handleSearch()
}

// ===== 新增/编辑弹窗 =====
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<string | null>(null)
const form = reactive({ username: '', phone: '', role: 'PATIENT', password: '' })
const isEdit = computed(() => editingId.value !== null)

const openCreate = () => {
  editingId.value = null
  form.username = ''
  form.phone = ''
  form.role = 'PATIENT'
  form.password = ''
  dialogVisible.value = true
}

const openEdit = (user: UserItem) => {
  editingId.value = user.id
  form.username = user.username
  form.phone = user.phone || ''
  form.role = user.role
  form.password = ''
  dialogVisible.value = true
}

const save = async () => {
  if (!form.username.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (form.phone && !/^1\d{10}$/.test(form.phone)) {
    ElMessage.warning('手机号格式不正确')
    return
  }
  if (form.password && form.password.length < 6) {
    ElMessage.warning('密码至少需要 6 位')
    return
  }
  if (!isEdit.value && !form.password) {
    ElMessage.warning('新增用户请设置初始密码')
    return
  }

  const payload: UserUpsertRequest = {
    username: form.username.trim(),
    phone: form.phone,
    role: form.role
  }
  if (form.password) {
    payload.password = form.password
  }

  saving.value = true
  try {
    if (isEdit.value && editingId.value) {
      await adminUserService.updateUser(editingId.value, payload)
      ElMessage.success('用户已更新')
    } else {
      await adminUserService.createUser(payload)
      ElMessage.success('用户已创建')
    }
    dialogVisible.value = false
    await handleSearch()
  } catch (error) {
    const msg = (error as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

const handleDelete = async (user: UserItem) => {
  try {
    await ElMessageBox.confirm(`确定要删除用户「${user.username}」吗？此操作不可恢复。`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return // 用户取消删除
  }
  try {
    await adminUserService.deleteUser(user.id)
    ElMessage.success('用户已删除')
    await handleSearch()
  } catch {
    ElMessage.error('删除失败，请稍后重试')
  }
}

// ===== 展示辅助 =====
const roleLabel = (role: string) => ROLE_LABELS[role] || role
const avatarText = (name: string) => (name ? name.slice(0, 2).toUpperCase() : 'U')

/** 手机号脱敏：138****8888 */
const maskPhone = (phone?: string) => {
  if (!phone) return '—'
  const digits = phone.replace(/\D/g, '')
  if (digits.length >= 7) return `${digits.slice(0, 3)}****${digits.slice(-4)}`
  return phone
}

const formatTime = (t?: string) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '—')

/** 头像配色（按角色区分） */
const avatarClass = (role: string) => {
  if (role === 'ADMIN') return 'avatar-admin'
  if (role === 'DOCTOR') return 'avatar-doctor'
  return 'avatar-default'
}

/** 角色徽标配色（管理员=品牌蓝、医生=成功绿、其余=中性灰） */
const roleBadgeClass = (role: string) => {
  if (role === 'ADMIN') return 'badge-admin'
  if (role === 'DOCTOR') return 'badge-doctor'
  return 'badge-default'
}

onMounted(handleSearch)
</script>

<style scoped>
/* ===== 页面容器 ===== */
.user-manage-page {
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

.add-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
}

.add-icon {
  font-size: 1.125rem;
}

/* ===== 搜索卡片 ===== */
.search-card {
  background: #ffffff;
  border: 1px solid #c2c6d4;
  border-radius: 0.75rem;
  padding: 1rem 1.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  margin-bottom: 1.5rem;
}

.search-row {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
}

.search-keyword {
  flex: 1;
  min-width: 15rem;
  max-width: 24rem;
}

.role-filter {
  width: 10rem;
}

.input-prefix-icon {
  font-size: 1.125rem;
  color: #4a5f83;
}

/* ===== 表格卡片 ===== */
.table-card {
  background: #ffffff;
  border: 1px solid #c2c6d4;
  border-radius: 0.75rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.table-wrap {
  min-height: 24rem;
}

.result-table {
  width: 100%;
  border-collapse: collapse;
  min-width: 48rem;
}

.table-head-row th {
  padding: 0.75rem 1.5rem;
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #4a5f83;
  background: #f2f4f6;
  border-bottom: 1px solid #c2c6d4;
  text-align: left;
}

.table-head-row th.text-right {
  text-align: right;
  width: 7rem;
}

.result-row {
  border-bottom: 1px solid #eceff1;
  transition: background 0.15s ease;
}

.result-row:last-child {
  border-bottom: none;
}

.result-row:hover {
  background: rgba(0, 94, 184, 0.06);
}

.result-row td {
  padding: 0.875rem 1.5rem;
  vertical-align: middle;
}

/* 用户列 */
.user-cell {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.avatar {
  width: 2rem;
  height: 2rem;
  border-radius: 9999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.75rem;
  font-weight: 700;
  flex-shrink: 0;
}

.avatar-admin {
  background: #005eb8;
  color: #ffffff;
}

.avatar-doctor {
  background: #2e7d32;
  color: #ffffff;
}

.avatar-default {
  background: #dbeafe;
  color: #005eb8;
}

.user-name {
  font-size: 0.875rem;
  font-weight: 500;
  color: #191c1d;
}

.code-text {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.8125rem;
  color: #4a5f83;
}

.time-text {
  font-size: 0.8125rem;
  color: #4a5f83;
}

/* 角色徽标 */
.role-badge {
  display: inline-flex;
  align-items: center;
  padding: 0.125rem 0.625rem;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 600;
}

.badge-admin {
  background: rgba(0, 94, 184, 0.1);
  color: #005eb8;
}

.badge-doctor {
  background: rgba(46, 125, 50, 0.1);
  color: #2e7d32;
}

.badge-default {
  background: #f1f5f9;
  color: #4a5f83;
}

/* 操作列（行 hover 显示） */
.row-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.75rem;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.result-row:hover .row-actions {
  opacity: 1;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.8125rem;
  font-weight: 600;
  color: #005eb8;
  background: none;
  border: none;
  cursor: pointer;
  transition: color 0.15s ease;
}

.action-btn:hover {
  color: #004a9e;
}

.action-btn.danger {
  color: #d32f2f;
}

.action-btn.danger:hover {
  color: #93000a;
}

.action-icon {
  font-size: 1rem;
}

/* ===== 空态 ===== */
.empty-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem 2rem;
  text-align: center;
}

.empty-icon {
  width: 3.5rem;
  height: 3.5rem;
  border-radius: 9999px;
  background: #f1f5f9;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 1rem;
}

.empty-icon .material-symbols-outlined {
  font-size: 1.75rem;
  color: #4a5f83;
}

.empty-title {
  font-size: 1rem;
  font-weight: 700;
  color: #191c1d;
  margin-bottom: 0.5rem;
}

.empty-desc {
  font-size: 0.8125rem;
  color: #4a5f83;
  margin-bottom: 1.25rem;
}

/* ===== 分页 ===== */
.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1.5rem;
  border-top: 1px solid #c2c6d4;
}

.page-info {
  font-size: 0.8125rem;
  color: #4a5f83;
}

.page-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.page-btn {
  padding: 0.375rem;
}

.page-num {
  font-size: 0.875rem;
  font-weight: 600;
  color: #191c1d;
  min-width: 1.5rem;
  text-align: center;
}

/* ===== 弹窗表单 ===== */
.user-form .el-select {
  width: 100%;
}
</style>
