<template>
  <div class="bg-surface text-on-surface min-h-screen">
    <!-- 页面标题 - 玻璃态效果 -->
    <div class="glass px-8 py-6 sticky top-0 z-50 backdrop-blur-md border-b border-outline/5">
      <h1 class="text-2xl font-bold font-headline text-on-surface tracking-tight">用户管理与个人中心</h1>
      <p class="text-on-surface-variant mt-1 text-sm tracking-wide">用户管理、健康档案与权限控制</p>
    </div>

    <main class="px-8 pb-12 flex gap-6">
      <!-- 左侧面板: 用户管理 -->
      <aside class="w-96 flex flex-col gap-6 shrink-0">
        <!-- 统计卡片 - 玻璃态效果 -->
        <div class="glass rounded-xl p-6 shadow-glow border border-outline/10">
          <div class="flex justify-between items-end">
            <div>
              <p class="text-on-surface-variant text-xs font-medium uppercase tracking-widest opacity-70">今日活跃用户</p>
              <p class="display-lg font-bold bg-gradient-to-r from-primary to-primary-container bg-clip-text text-transparent mt-2">1,284</p>
            </div>
            <div class="text-right">
              <p class="text-on-surface-variant text-xs font-medium uppercase tracking-widest opacity-70">查询总数</p>
              <p class="text-3xl font-semibold text-secondary mt-2">42.5k</p>
            </div>
          </div>
          <div class="mt-8 h-1.5 w-full bg-surface-container-high rounded-full overflow-hidden">
            <div class="h-full bg-gradient-to-r from-primary to-primary-container rounded-full w-3/4 shadow-[0_0_10px_rgba(0,94,184,0.3)]"></div>
          </div>
        </div>

        <!-- 用户列表区域 - 玻璃态效果 -->
        <div class="bg-surface-container-lowest rounded-xl flex flex-col shadow-ambient overflow-hidden border border-outline/5">
          <div class="p-6 flex items-center justify-between border-b border-outline/10">
            <div>
              <h2 class="text-xl font-bold text-on-surface tracking-tight flex items-center gap-3">
                <span class="w-2 h-6 bg-gradient-to-b from-primary to-primary-container rounded-full"></span>
                用户管理
              </h2>
              <p class="text-xs text-on-surface-variant opacity-70 mt-1">管理医疗系统内的用户账户和权限</p>
            </div>
            <button
              class="btn-primary text-xs font-bold px-4 py-2 rounded-full flex items-center gap-2 transition-all hover:scale-105 active:scale-95 shadow-md"
              @click="showAddUserDialog"
            >
              <span class="material-symbols-outlined text-sm">add</span>新增用户
            </button>
          </div>
          <div class="px-6 pb-5">
            <div class="relative group">
              <div class="absolute inset-0 bg-gradient-to-r from-primary/5 to-primary-container/5 rounded-xl blur-sm group-hover:blur-md transition-all"></div>
              <span class="material-symbols-outlined absolute left-4 top-1/2 -translate-y-1/2 text-outline text-sm z-10">search</span>
              <input
                v-model="searchQuery"
                class="relative w-full bg-surface-container-lowest border border-outline/20 rounded-xl pl-11 pr-4 py-3 text-sm focus:ring-2 focus:ring-primary/30 focus:border-primary/30 placeholder:text-outline/60 transition-all z-10"
                placeholder="搜索姓名、手机号或邮箱..."
                type="text"
                @input="searchUsers"
              />
            </div>
          </div>
          <div class="flex px-6 gap-1 text-xs font-bold border-b border-outline/10 pb-2">
            <button
              class="px-4 py-2 rounded-lg transition-all"
              :class="filterRole === '' ? 'bg-primary/10 text-primary shadow-sm' : 'text-on-surface-variant hover:bg-surface-container-low'"
              @click="filterRole = ''"
            >全部用户</button>
            <button
              class="px-4 py-2 rounded-lg transition-all"
              :class="filterRole === 'patient' ? 'bg-primary/10 text-primary shadow-sm' : 'text-on-surface-variant hover:bg-surface-container-low'"
              @click="filterRole = 'patient'"
            >患者</button>
            <button
              class="px-4 py-2 rounded-lg transition-all"
              :class="filterRole === 'doctor' ? 'bg-primary/10 text-primary shadow-sm' : 'text-on-surface-variant hover:bg-surface-container-low'"
              @click="filterRole = 'doctor'"
            >医生</button>
            <button
              class="px-4 py-2 rounded-lg transition-all"
              :class="filterRole === 'nurse' ? 'bg-primary/10 text-primary shadow-sm' : 'text-on-surface-variant hover:bg-surface-container-low'"
              @click="filterRole = 'nurse'"
            >护士</button>
            <button
              class="px-4 py-2 rounded-lg transition-all"
              :class="filterRole === 'admin' ? 'bg-primary/10 text-primary shadow-sm' : 'text-on-surface-variant hover:bg-surface-container-low'"
              @click="filterRole = 'admin'"
            >管理员</button>
          </div>

          <!-- 用户列表 -->
          <div class="flex-1 overflow-y-auto max-h-[500px]">
            <div
              v-for="user in filteredUsers.slice((currentPage - 1) * pageSize, currentPage * pageSize)"
              :key="user.id"
              class="px-6 py-4 flex items-center gap-4 hover:bg-surface-container-low/80 transition-all cursor-pointer group border-b border-outline/5 last:border-0"
              :class="{ 'bg-gradient-to-r from-primary/3 to-primary-container/3 border-l-4 border-primary': user.status === 'active' }"
              @click="selectUser(user)"
            >
              <div class="relative">
                <div class="w-12 h-12 rounded-full overflow-hidden bg-gradient-to-br from-surface-container-low to-surface-container-high flex items-center justify-center shadow-md border-2 border-surface-container-lowest">
                  <img v-if="user.avatar" :src="user.avatar" :alt="user.name" class="w-full h-full object-cover" />
                  <span v-else class="text-lg font-bold text-on-surface-variant">{{ user.name.charAt(0) }}</span>
                </div>
                <div class="w-3 h-3 rounded-full border-2 border-surface-container-lowest absolute -bottom-0.5 -right-0.5"
                  :class="{
                    'bg-success shadow-[0_0_8px_rgba(52,199,89,0.5)]': user.status === 'active',
                    'bg-outline-variant': user.status !== 'active'
                  }"
                ></div>
              </div>
              <div class="flex-1 min-w-0">
                <div class="flex items-center justify-between">
                  <p class="text-sm font-medium truncate group-hover:text-primary transition-colors">{{ user.name }}</p>
                  <span class="text-[10px] px-2 py-1 rounded-full font-bold tracking-wide shadow-sm"
                    :class="{
                      'bg-primary-container/20 text-primary border border-primary/20': user.role === 'patient',
                      'bg-secondary-container/20 text-secondary border border-secondary/20': user.role === 'doctor',
                      'bg-tertiary-container/20 text-tertiary border border-tertiary/20': user.role === 'nurse',
                      'bg-error-container/20 text-error border border-error/20': user.role === 'admin',
                      'bg-outline-variant/20 text-on-surface-variant border border-outline/20': user.role === 'guest'
                    }"
                  >
                    {{ getRoleLabel(user.role) }}
                  </span>
                </div>
                <div class="flex items-center gap-2 mt-1">
                  <p class="text-xs text-on-surface-variant opacity-80">{{ formatPhone(user.phone) }}</p>
                  <span class="w-1 h-1 rounded-full bg-outline/40"></span>
                  <p class="text-xs text-on-surface-variant opacity-80">{{ user.email }}</p>
                </div>
              </div>
              <span class="material-symbols-outlined text-outline/40 group-hover:text-primary transition-colors text-sm opacity-0 group-hover:opacity-100">chevron_right</span>
            </div>
          </div>

          <!-- 分页和统计 -->
          <div class="p-6 border-t border-outline/10 flex items-center justify-between bg-surface-container-low/50">
            <div class="flex items-center gap-4">
              <p class="text-xs text-on-surface-variant">
                显示 <span class="font-bold text-on-surface">{{ Math.min((currentPage - 1) * pageSize + 1, totalUsers) }}-{{ Math.min(currentPage * pageSize, totalUsers) }}</span> 共 <span class="font-bold text-primary">{{ totalUsers }}</span> 名用户
              </p>
              <div class="flex items-center gap-2">
                <span class="text-xs text-on-surface-variant opacity-70">每页</span>
                <select
                  v-model="pageSize"
                  class="text-xs bg-surface-container-lowest border border-outline/20 rounded px-2 py-1 focus:ring-1 focus:ring-primary/30"
                  @change="currentPage = 1"
                >
                  <option value="5">5</option>
                  <option value="10">10</option>
                  <option value="20">20</option>
                  <option value="50">50</option>
                </select>
                <span class="text-xs text-on-surface-variant opacity-70">条</span>
              </div>
            </div>
            <div class="flex gap-1">
              <button
                class="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-surface-container-high transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                @click="currentPage > 1 ? currentPage-- : null"
                :disabled="currentPage === 1"
              >
                <span class="material-symbols-outlined text-sm">chevron_left</span>
              </button>
              <button
                v-for="page in pageRange"
                :key="page"
                class="w-8 h-8 flex items-center justify-center rounded-lg text-sm transition-all"
                :class="{
                  'bg-gradient-to-br from-primary to-primary-container text-on-primary font-bold shadow-md': page === currentPage,
                  'hover:bg-surface-container-high text-on-surface-variant': page !== currentPage && page !== '...',
                  'text-on-surface-variant opacity-50': page === '...'
                }"
                @click="page !== '...' ? currentPage = page as number : null"
              >
                {{ page }}
              </button>
              <button
                class="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-surface-container-high transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                @click="currentPage < totalPages ? currentPage++ : null"
                :disabled="currentPage === totalPages"
              >
                <span class="material-symbols-outlined text-sm">chevron_right</span>
              </button>
            </div>
          </div>

        </div>
      </aside>
      <!-- 右侧主面板: 用户资料和健康档案 -->
      <section class="flex-1 flex flex-col gap-6">
        <!-- 用户个人资料英雄区域 -->
        <div class="glass rounded-xl p-8 shadow-ambient flex items-start gap-8 relative overflow-hidden border border-outline/10">
          <!-- 渐变背景效果 -->
          <div class="absolute top-0 right-0 w-80 h-80 bg-gradient-to-br from-primary/10 to-primary-container/10 rounded-full -mr-40 -mt-40 blur-3xl"></div>
          <div class="absolute bottom-0 left-0 w-64 h-64 bg-secondary/5 rounded-full -ml-32 -mb-32 blur-3xl"></div>

          <div class="relative group z-10">
            <div class="w-36 h-36 rounded-full overflow-hidden border-4 border-surface-container-lowest shadow-2xl bg-gradient-to-br from-surface-container-low to-surface-container-high flex items-center justify-center">
              <img v-if="currentUser.avatar" :src="currentUser.avatar" :alt="currentUser.name" class="w-full h-full object-cover" />
              <span v-else class="text-5xl font-bold bg-gradient-to-br from-primary to-primary-container bg-clip-text text-transparent">{{ currentUser.name.charAt(0) }}</span>
            </div>
            <button class="absolute bottom-2 right-2 bg-surface-container-lowest shadow-lg p-2.5 rounded-full hover:bg-primary hover:text-white transition-all hover:scale-110 border border-outline/10" @click="editProfile">
              <span class="material-symbols-outlined text-sm" style="font-variation-settings: 'FILL' 1;">edit</span>
            </button>
          </div>

          <div class="flex-1 pt-2 z-10">
            <div class="flex items-start justify-between">
              <div>
                <div class="flex items-center gap-3 mb-2">
                  <h1 class="text-4xl font-extrabold text-on-surface tracking-tight">{{ currentUser.name }}</h1>
                  <span class="px-3 py-1 rounded-full text-xs font-bold bg-primary/10 text-primary border border-primary/20">
                    {{ getRoleLabel(currentUser.role) }}
                  </span>
                </div>
                <div class="flex gap-6 mt-3">
                  <div class="flex items-center gap-2 text-sm text-on-surface-variant bg-surface-container-low/50 px-3 py-1.5 rounded-lg">
                    <span class="material-symbols-outlined text-sm opacity-70">badge</span>
                    <span class="font-mono">ID: {{ currentUser.id || 'MP-2024-88921' }}</span>
                  </div>
                  <div class="flex items-center gap-2 text-sm text-on-surface-variant bg-surface-container-low/50 px-3 py-1.5 rounded-lg">
                    <span class="material-symbols-outlined text-sm opacity-70">calendar_today</span>
                    <span>注册日期: {{ formatDate(currentUser.createdAt) || '2023-11-14' }}</span>
                  </div>
                  <div class="flex items-center gap-2 text-sm text-on-surface-variant bg-surface-container-low/50 px-3 py-1.5 rounded-lg">
                    <span class="material-symbols-outlined text-sm opacity-70">schedule</span>
                    <span>最后在线: {{ formatLastOnline(currentUser.lastLogin) }}</span>
                  </div>
                </div>
              </div>
              <div class="flex gap-3">
                <button class="btn-secondary px-5 py-2.5 rounded-full font-bold text-sm flex items-center gap-2 hover:shadow-md transition-all" @click="editProfile">
                  <span class="material-symbols-outlined text-sm">edit_note</span>编辑个人资料
                </button>
                <button class="btn-primary px-6 py-2.5 rounded-full font-bold text-sm flex items-center gap-2 shadow-lg hover:scale-[1.02] active:scale-95 transition-all" @click="exportHealthReport">
                  <span class="material-symbols-outlined text-sm">download</span>导出健康报告
                </button>
              </div>
            </div>

            <!-- 基本信息网格 -->
            <div class="grid grid-cols-4 gap-6 mt-10">
              <div class="bg-surface-container-low rounded-xl p-5 border border-outline/10 hover:border-primary/20 transition-all group hover:shadow-md">
                <p class="text-[11px] text-on-surface-variant font-bold uppercase tracking-widest opacity-70">年龄 / 性别</p>
                <p class="text-xl font-bold text-primary mt-2 group-hover:scale-105 transition-transform">{{ currentUser.age || '32' }}岁 / {{ currentUser.gender || '女' }}</p>
              </div>
              <div class="bg-surface-container-low rounded-xl p-5 border border-outline/10 hover:border-primary/20 transition-all group hover:shadow-md">
                <p class="text-[11px] text-on-surface-variant font-bold uppercase tracking-widest opacity-70">身高 / 体重</p>
                <p class="text-xl font-bold text-primary mt-2 group-hover:scale-105 transition-transform">{{ currentUser.height || '168' }}cm / {{ currentUser.weight || '54' }}kg</p>
              </div>
              <div class="bg-surface-container-low rounded-xl p-5 border border-outline/10 hover:border-primary/20 transition-all group hover:shadow-md">
                <p class="text-[11px] text-on-surface-variant font-bold uppercase tracking-widest opacity-70">血型</p>
                <p class="text-xl font-bold text-primary mt-2 group-hover:scale-105 transition-transform">{{ currentUser.bloodType || 'O型 (Rh+)' }}</p>
              </div>
              <div class="bg-surface-container-low rounded-xl p-5 border border-outline/10 hover:border-primary/20 transition-all group hover:shadow-md">
                <p class="text-[11px] text-on-surface-variant font-bold uppercase tracking-widest opacity-70">职业</p>
                <p class="text-xl font-bold text-primary mt-2 group-hover:scale-105 transition-transform">{{ currentUser.occupation || '高级软件工程师' }}</p>
              </div>
            </div>
          </div>
        </div>

        <!-- 健康档案区域 -->
        <div class="glass rounded-xl p-8 shadow-ambient border border-outline/10">
          <div class="flex items-start justify-between mb-10">
            <div>
              <h2 class="text-2xl font-bold flex items-center gap-4 mb-2">
                <span class="w-2 h-8 bg-gradient-to-b from-primary to-primary-container rounded-full"></span>
                健康档案
              </h2>
              <p class="text-sm text-on-surface-variant opacity-80">此信息将自动注入 RAG Prompt 以优化问答结果</p>
            </div>
            <div class="flex items-center gap-2 px-4 py-2 rounded-lg bg-primary/5 border border-primary/20">
              <span class="material-symbols-outlined text-sm text-primary">info</span>
              <span class="text-xs font-medium text-primary">RAG优化数据源</span>
            </div>
          </div>

          <div class="space-y-8">
            <!-- 过敏史和慢病史 -->
            <div class="grid grid-cols-2 gap-8">
              <div class="bg-surface-container-lowest rounded-xl p-6 border border-outline/10">
                <div class="flex items-center gap-2 mb-4">
                  <span class="material-symbols-outlined text-error" style="font-variation-settings: 'FILL' 1;">warning</span>
                  <label class="text-sm font-bold text-on-surface uppercase tracking-wide">过敏史</label>
                </div>
                <div class="flex flex-wrap gap-2">
                  <span
                    v-for="allergy in currentUser.allergies"
                    :key="allergy"
                    class="bg-error-container/20 text-error px-3 py-2 rounded-lg text-sm font-semibold flex items-center gap-2 border border-error/20 hover:border-error/40 transition-colors"
                  >
                    <span class="material-symbols-outlined text-xs">warning</span>
                    {{ allergy }}
                  </span>
                  <span v-if="!currentUser.allergies || currentUser.allergies.length === 0" class="text-sm text-on-surface-variant italic">无过敏记录</span>
                </div>
              </div>

              <div class="bg-surface-container-lowest rounded-xl p-6 border border-outline/10">
                <div class="flex items-center gap-2 mb-4">
                  <span class="material-symbols-outlined text-tertiary" style="font-variation-settings: 'FILL' 1;">clinical_notes</span>
                  <label class="text-sm font-bold text-on-surface uppercase tracking-wide">慢病史</label>
                </div>
                <div class="flex flex-wrap gap-2">
                  <span
                    v-for="condition in currentUser.chronicConditions"
                    :key="condition"
                    class="bg-tertiary-container/20 text-tertiary px-3 py-2 rounded-lg text-sm font-semibold border border-tertiary/20 hover:border-tertiary/40 transition-colors"
                  >
                    {{ condition }}
                  </span>
                  <span v-if="!currentUser.chronicConditions || currentUser.chronicConditions.length === 0" class="text-sm text-on-surface-variant italic">无慢性病史</span>
                </div>
              </div>
            </div>

            <!-- 常用药物 -->
            <div class="bg-surface-container-lowest rounded-xl p-6 border border-outline/10">
              <div class="flex items-center gap-2 mb-4">
                <span class="material-symbols-outlined text-secondary" style="font-variation-settings: 'FILL' 1;">medication</span>
                <label class="text-sm font-bold text-on-surface uppercase tracking-wide">常用药物</label>
              </div>
              <div class="bg-surface-container-low rounded-xl p-5 text-sm text-on-surface leading-relaxed border-l-4 border-secondary/50">
                <div class="flex items-start gap-3">
                  <span class="material-symbols-outlined text-secondary text-lg mt-0.5">pill</span>
                  <div>
                    <p class="font-medium">{{ currentUser.medications || '二甲双胍 (500mg/日), 氯沙坦钾 (50mg/日).' }}</p>
                    <p class="text-xs text-on-surface-variant mt-2 opacity-80">患者反馈偶有轻微头晕, 建议在 RAG 咨询中关注血压波动情况.</p>
                  </div>
                </div>
              </div>
            </div>

            <!-- 家族史 -->
            <div class="bg-surface-container-lowest rounded-xl p-6 border border-outline/10">
              <div class="flex items-center gap-2 mb-4">
                <span class="material-symbols-outlined text-primary" style="font-variation-settings: 'FILL' 1;">family_history</span>
                <label class="text-sm font-bold text-on-surface uppercase tracking-wide">家族史</label>
              </div>
              <div class="bg-surface-container-low rounded-xl p-5 text-sm text-on-surface leading-relaxed">
                <div class="flex items-start gap-3">
                  <span class="material-symbols-outlined text-primary text-lg mt-0.5">genetics</span>
                  <p>{{ currentUser.familyHistory || '父亲有冠心病史, 母亲有甲状腺结节病史. 建议定期进行心血管风险筛查.' }}</p>
                </div>
              </div>
            </div>
          </div>

          <!-- 底部状态栏 -->
          <div class="mt-12 pt-8 border-t border-outline/10 flex items-center justify-between">
            <div class="flex-1">
              <div class="flex items-center justify-between mb-3">
                <div class="flex items-center gap-2">
                  <span class="material-symbols-outlined text-primary text-sm">check_circle</span>
                  <span class="text-sm font-bold text-on-surface">资料完善度</span>
                </div>
                <span class="text-lg font-bold text-primary">{{ currentUser.profileCompletion || 85 }}%</span>
              </div>
              <div class="h-2.5 bg-surface-container-high rounded-full overflow-hidden">
                <div class="h-full bg-gradient-to-r from-primary to-primary-container rounded-full shadow-[0_0_12px_rgba(0,94,184,0.3)] transition-all duration-500" :style="{ width: (currentUser.profileCompletion || 85) + '%' }"></div>
              </div>
            </div>
            <div class="flex items-center gap-3 ml-12 px-4 py-2.5 rounded-lg bg-surface-container-low/80">
              <span class="material-symbols-outlined text-primary text-sm">schedule</span>
              <div>
                <p class="text-xs text-on-surface-variant opacity-80">最后在线时间</p>
                <p class="text-sm font-semibold text-on-surface">{{ formatLastOnline(currentUser.lastLogin) }}</p>
              </div>
            </div>
          </div>
        </div>
      </section>
    </main>
  </div>

    <!-- 新增用户对话框 -->
    <el-dialog
      v-model="addUserDialogVisible"
      title="新增用户"
      width="640px"
      :before-close="handleAddUserDialogClose"
      class="custom-dialog"
    >
      <template #header>
        <div class="flex items-center gap-3 py-2">
          <span class="w-1.5 h-6 bg-gradient-to-b from-primary to-primary-container rounded-full"></span>
          <h3 class="text-lg font-bold text-on-surface">新增用户</h3>
        </div>
      </template>

      <div class="glass rounded-lg p-1 mb-6">
        <div class="flex items-center gap-2 px-4 py-2">
          <span class="material-symbols-outlined text-primary text-sm">info</span>
          <p class="text-xs text-on-surface-variant">新用户将收到系统通知邮件，初始密码需满足至少8位字符</p>
        </div>
      </div>

      <el-form
        ref="addUserFormRef"
        :model="addUserForm"
        :rules="addUserRules"
        label-width="120px"
        class="space-y-6"
      >
        <div class="grid grid-cols-2 gap-6">
          <el-form-item label="姓名" prop="name" class="mb-0">
            <el-input
              v-model="addUserForm.name"
              placeholder="请输入用户姓名"
              size="large"
              :prefix-icon="User"
            />
          </el-form-item>
          <el-form-item label="用户名" prop="username" class="mb-0">
            <el-input
              v-model="addUserForm.username"
              placeholder="请输入用户名"
              size="large"
              :prefix-icon="User"
            />
          </el-form-item>
        </div>

        <div class="grid grid-cols-2 gap-6">
          <el-form-item label="手机号" prop="phone" class="mb-0">
            <el-input
              v-model="addUserForm.phone"
              placeholder="请输入手机号"
              size="large"
              :prefix-icon="Phone"
            />
          </el-form-item>
          <el-form-item label="邮箱" prop="email" class="mb-0">
            <el-input
              v-model="addUserForm.email"
              placeholder="请输入邮箱"
              size="large"
              :prefix-icon="Message"
            />
          </el-form-item>
        </div>

        <div class="grid grid-cols-2 gap-6">
          <el-form-item label="角色" prop="role" class="mb-0">
            <el-select
              v-model="addUserForm.role"
              placeholder="请选择角色"
              size="large"
              class="w-full"
            >
              <el-option label="管理员" value="admin" />
              <el-option label="医生" value="doctor" />
              <el-option label="护士" value="nurse" />
              <el-option label="患者" value="patient" />
              <el-option label="访客" value="guest" />
            </el-select>
          </el-form-item>
          <el-form-item label="部门" prop="department" class="mb-0">
            <el-input
              v-model="addUserForm.department"
              placeholder="请输入部门/科室"
              size="large"
              :prefix-icon="Setting"
            />
          </el-form-item>
        </div>

        <div class="grid grid-cols-2 gap-6">
          <el-form-item label="初始密码" prop="password" class="mb-0">
            <el-input
              v-model="addUserForm.password"
              type="password"
              placeholder="请输入初始密码"
              show-password
              size="large"
              :prefix-icon="Key"
            />
          </el-form-item>
          <el-form-item label="确认密码" prop="confirmPassword" class="mb-0">
            <el-input
              v-model="addUserForm.confirmPassword"
              type="password"
              placeholder="请确认密码"
              show-password
              size="large"
              :prefix-icon="Key"
            />
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <div class="flex items-center justify-between w-full">
          <p class="text-xs text-on-surface-variant opacity-70">* 所有字段均为必填项</p>
          <div class="flex gap-3">
            <el-button class="btn-secondary" @click="addUserDialogVisible = false" :disabled="addingUser">
              取消
            </el-button>
            <el-button
              class="btn-primary"
              type="primary"
              @click="submitAddUser"
              :loading="addingUser"
            >
              <span class="material-symbols-outlined text-sm mr-2">add</span>
              创建用户
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import {
  UserFilled,
  Refresh,
  Download,
  Search,
  Edit,
  Delete,
  Key,
  Plus,
  User,
  Phone,
  Message,
  Setting,
  TrendCharts,
  Document,
  VideoPlay,
  Files,
  Warning,
  SuccessFilled,
  Clock,
  CircleClose,
  InfoFilled,
  Check,
  WarningFilled,
  Close
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth.store'

const authStore = useAuthStore()


// 用户管理
const searchQuery = ref('')
const filterRole = ref('')
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const totalUsers = ref(0)
const selectedUsers = ref<any[]>([])
const addUserDialogVisible = ref(false)
const addingUser = ref(false)

// 模拟用户数据
const users = ref([
  {
    id: '1',
    username: 'admin',
    name: '陈晓敏',
    avatar: '',
    phone: '13800138000',
    email: 'admin@imkqas.com',
    role: 'admin',
    department: '系统管理部',
    status: 'active',
    lastLogin: new Date('2026-04-18T14:30:00'),
    createdAt: new Date('2026-01-01T00:00:00'),
    gender: '女',
    age: 32,
    birthdate: '1992-05-15',
    bloodType: 'O型 (Rh+)',
    height: 168,
    weight: 54,
    bloodPressure: '120/80',
    heartRate: 72,
    bloodSugar: 5.2,
    bmi: 22.9,
    occupation: '高级软件工程师',
    allergies: ['青霉素', '花粉'],
    chronicConditions: ['2型糖尿病', '高血压 (一级)'],
    medications: '二甲双胍 (500mg/日), 氯沙坦钾 (50mg/日). 患者反馈偶有轻微头晕, 建议在 RAG 咨询中关注血压波动情况.',
    familyHistory: '父亲有冠心病史, 母亲有甲状腺结节病史. 建议定期进行心血管风险筛查.',
    profileCompletion: 85,
    medicalHistory: '2型糖尿病病史3年，高血压病史2年'
  },
  {
    id: '2',
    username: 'doctor_zhang',
    name: '张医生',
    avatar: '',
    phone: '13900139000',
    email: 'zhang@hospital.com',
    role: 'doctor',
    department: '内科',
    status: 'active',
    lastLogin: new Date('2026-04-18T10:15:00'),
    createdAt: new Date('2026-02-15T09:00:00'),
    gender: '男',
    birthdate: '1980-08-20',
    bloodType: 'O型',
    height: 180,
    weight: 75,
    bloodPressure: '125/85',
    heartRate: 68,
    bloodSugar: 5.4,
    bmi: 23.1
  },
  {
    id: '3',
    username: 'nurse_li',
    name: '李护士',
    avatar: '',
    phone: '13700137000',
    email: 'li@hospital.com',
    role: 'nurse',
    department: '护理部',
    status: 'active',
    lastLogin: new Date('2026-04-17T16:45:00'),
    createdAt: new Date('2026-03-10T14:30:00'),
    gender: '女',
    birthdate: '1990-03-12',
    bloodType: 'B型',
    height: 165,
    weight: 55,
    bloodPressure: '115/75',
    heartRate: 70,
    bloodSugar: 5.1,
    bmi: 20.2
  },
  {
    id: '4',
    username: 'patient_wang',
    name: '王先生',
    avatar: '',
    phone: '13600136000',
    email: 'wang@example.com',
    role: 'patient',
    department: '-',
    status: 'active',
    lastLogin: new Date('2026-04-16T09:30:00'),
    createdAt: new Date('2026-04-01T10:00:00'),
    gender: '男',
    birthdate: '1975-11-25',
    bloodType: 'AB型',
    height: 172,
    weight: 80,
    bloodPressure: '140/90',
    heartRate: 80,
    bloodSugar: 6.8,
    bmi: 27.0,
    allergies: '青霉素过敏',
    medicalHistory: '高血压病史5年'
  },
  {
    id: '5',
    username: 'guest_test',
    name: '测试用户',
    avatar: '',
    phone: '13500135000',
    email: 'test@example.com',
    role: 'guest',
    department: '-',
    status: 'inactive',
    lastLogin: new Date('2026-03-20T11:20:00'),
    createdAt: new Date('2026-02-28T15:45:00')
  }
])






// 新增用户表单
const addUserFormRef = ref()
const addUserForm = reactive({
  name: '',
  username: '',
  phone: '',
  email: '',
  role: '',
  department: '',
  password: '',
  confirmPassword: ''
})

const validatePhone = (rule: any, value: string, callback: any) => {
  if (!value) {
    return callback(new Error('请输入手机号'))
  }
  if (!/^1[3-9]\d{9}$/.test(value)) {
    return callback(new Error('请输入正确的手机号'))
  }
  callback()
}

const validateEmail = (rule: any, value: string, callback: any) => {
  if (!value) {
    return callback(new Error('请输入邮箱'))
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
    return callback(new Error('请输入正确的邮箱地址'))
  }
  callback()
}

const validateConfirmPassword = (rule: any, value: string, callback: any) => {
  if (!value) {
    return callback(new Error('请确认密码'))
  }
  if (value !== addUserForm.password) {
    return callback(new Error('两次输入的密码不一致'))
  }
  callback()
}

const addUserRules = {
  name: [
    { required: true, message: '请输入姓名', trigger: 'blur' }
  ],
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度应为3-20个字符', trigger: 'blur' }
  ],
  phone: [
    { validator: validatePhone, trigger: 'blur' }
  ],
  email: [
    { validator: validateEmail, trigger: 'blur' }
  ],
  role: [
    { required: true, message: '请选择角色', trigger: 'change' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, message: '密码长度至少8位', trigger: 'blur' }
  ],
  confirmPassword: [
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

// 计算属性
const currentUser = computed(() => {
  // 当前登录用户，这里先用第一个用户作为示例
  return users.value[0]
})

const totalPages = computed(() => {
  return Math.ceil(filteredUsers.value.length / pageSize.value)
})

const pageRange = computed(() => {
  const range = []
  const total = totalPages.value
  const current = currentPage.value
  const delta = 2

  for (let i = Math.max(2, current - delta); i <= Math.min(total - 1, current + delta); i++) {
    range.push(i)
  }

  if (current - delta > 2) {
    range.unshift('...')
  }
  if (current + delta < total - 1) {
    range.push('...')
  }

  range.unshift(1)
  if (total > 1) {
    range.push(total)
  }

  return range.filter((page, index, array) => page !== array[index - 1])
})

const filteredUsers = computed(() => {
  let result = users.value

  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(user =>
      user.name.toLowerCase().includes(query) ||
      user.username.toLowerCase().includes(query) ||
      user.phone.includes(query) ||
      user.email.toLowerCase().includes(query)
    )
  }

  if (filterRole.value) {
    result = result.filter(user => user.role === filterRole.value)
  }

  return result
})

// 方法
const formatPhone = (phone: string) => {
  if (!phone) return ''
  return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1 **** $2')
}

const formatDate = (date: Date | string) => {
  if (!date) return ''
  const d = new Date(date)
  return d.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

const formatLastOnline = (date: Date | string) => {
  if (!date) return '从未登录'
  const now = new Date()
  const last = new Date(date)
  const diffMinutes = Math.floor((now.getTime() - last.getTime()) / (1000 * 60))

  if (diffMinutes < 1) return '刚刚'
  if (diffMinutes < 60) return `${diffMinutes}分钟前`
  if (diffMinutes < 1440) return `${Math.floor(diffMinutes / 60)}小时前`
  if (diffMinutes < 10080) return `${Math.floor(diffMinutes / 1440)}天前`
  return formatDate(last)
}

const selectUser = (user: any) => {
  // 设置当前选择的用户
  const index = users.value.findIndex(u => u.id === user.id)
  if (index !== -1) {
    // 将选中的用户移到数组开头，使其成为currentUser
    const [selectedUser] = users.value.splice(index, 1)
    users.value.unshift(selectedUser)
  }
}

const exportHealthReport = () => {
  ElMessage.info('健康报告导出功能开发中')
}

const getRoleLabel = (role: string) => {
  const labels: Record<string, string> = {
    admin: '管理员',
    doctor: '医生',
    nurse: '护士',
    patient: '患者',
    guest: '访客'
  }
  return labels[role] || role
}

const getRoleType = (role: string) => {
  const types: Record<string, string> = {
    admin: 'danger',
    doctor: 'primary',
    nurse: 'success',
    patient: 'info',
    guest: 'warning'
  }
  return types[role] || 'info'
}

const getStatusLabel = (status: string) => {
  const labels: Record<string, string> = {
    active: '激活',
    inactive: '停用',
    locked: '锁定',
    pending: '待审核'
  }
  return labels[status] || status
}

const getStatusType = (status: string) => {
  const types: Record<string, string> = {
    active: 'success',
    inactive: 'info',
    locked: 'warning',
    pending: 'warning'
  }
  return types[status] || 'info'
}




// 用户操作
const showAddUserDialog = () => {
  addUserDialogVisible.value = true
}

const handleAddUserDialogClose = () => {
  addUserDialogVisible.value = false
  addUserFormRef.value?.resetFields()
}

const refreshUsers = () => {
  loading.value = true
  setTimeout(() => {
    loading.value = false
    ElMessage.success('用户列表已刷新')
  }, 1000)
}

const searchUsers = () => {
  // 搜索逻辑已在计算属性中实现
}

const handleSelectionChange = (selection: any[]) => {
  selectedUsers.value = selection
}

const editUser = (user: any) => {
  ElMessage.info(`编辑用户: ${user.name}`)
  // TODO: 实际编辑逻辑
}

const resetPassword = async (user: any) => {
  try {
    await ElMessageBox.confirm(
      `确定要重置用户"${user.name}"的密码吗？`,
      '重置密码确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    // TODO: 实际重置密码逻辑
    await new Promise(resolve => setTimeout(resolve, 500))
    ElMessage.success('密码已重置，新密码已发送到用户邮箱')
  } catch (error) {
    // 用户取消
  }
}

const deleteUser = async (user: any) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除用户"${user.name}"吗？`,
      '删除用户确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const index = users.value.findIndex(u => u.id === user.id)
    if (index !== -1) {
      users.value.splice(index, 1)
      ElMessage.success('用户已删除')
    }
  } catch (error) {
    // 用户取消
  }
}

const activateSelected = () => {
  if (selectedUsers.value.length === 0) {
    ElMessage.warning('请先选择用户')
    return
  }

  selectedUsers.value.forEach(user => {
    const userIndex = users.value.findIndex(u => u.id === user.id)
    if (userIndex !== -1) {
      users.value[userIndex].status = 'active'
    }
  })

  ElMessage.success(`已激活 ${selectedUsers.value.length} 个用户`)
  selectedUsers.value = []
}

const deactivateSelected = () => {
  if (selectedUsers.value.length === 0) {
    ElMessage.warning('请先选择用户')
    return
  }

  selectedUsers.value.forEach(user => {
    const userIndex = users.value.findIndex(u => u.id === user.id)
    if (userIndex !== -1) {
      users.value[userIndex].status = 'inactive'
    }
  })

  ElMessage.success(`已停用 ${selectedUsers.value.length} 个用户`)
  selectedUsers.value = []
}

const deleteSelected = async () => {
  if (selectedUsers.value.length === 0) {
    ElMessage.warning('请先选择用户')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedUsers.value.length} 个用户吗？`,
      '批量删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    selectedUsers.value.forEach(user => {
      const index = users.value.findIndex(u => u.id === user.id)
      if (index !== -1) {
        users.value.splice(index, 1)
      }
    })

    ElMessage.success(`已删除 ${selectedUsers.value.length} 个用户`)
    selectedUsers.value = []
  } catch (error) {
    // 用户取消
  }
}

const exportUsers = () => {
  ElMessage.info('导出功能开发中')
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  // TODO: 重新加载数据
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
  // TODO: 重新加载数据
}


// 健康档案操作
const editProfile = () => {
  ElMessage.info('编辑资料功能开发中')
}

const showSettings = () => {
  ElMessage.info('账号设置功能开发中')
}

// 新增用户提交
const submitAddUser = async () => {
  if (!addUserFormRef.value) return

  const isValid = await addUserFormRef.value.validate()
  if (!isValid) return

  addingUser.value = true

  try {
    // TODO: 实际新增用户逻辑
    await new Promise(resolve => setTimeout(resolve, 1000))

    const newUser = {
      id: Date.now().toString(),
      username: addUserForm.username,
      name: addUserForm.name,
      avatar: '',
      phone: addUserForm.phone,
      email: addUserForm.email,
      role: addUserForm.role,
      department: addUserForm.department,
      status: 'active',
      lastLogin: new Date(),
      createdAt: new Date()
    }

    users.value.unshift(newUser)
    addUserDialogVisible.value = false
    addUserFormRef.value.resetFields()

    ElMessage.success('用户添加成功')
  } catch (error: any) {
    ElMessage.error(error.message || '添加用户失败')
  } finally {
    addingUser.value = false
  }
}

// 初始化
onMounted(() => {
  totalUsers.value = users.value.length
})
</script>