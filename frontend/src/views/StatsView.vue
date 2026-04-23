<template>
  <div class="bg-surface text-on-surface min-h-screen flex flex-col enhanced-bg">
    <!-- TopNavBar -->
    <header class="fixed top-0 w-full z-50 header-enhanced h-16 flex justify-between items-center px-6">
      <div class="flex items-center gap-8">
        <div class="text-xl font-bold text-brand tracking-tight font-headline">
          Clinical Precision RAG
        </div>
        <nav class="hidden md:flex items-center gap-6">
          <a class="font-headline tracking-tight font-semibold text-text-secondary hover:text-brand transition-colors" href="/qa">
            智能问答
          </a>
          <a class="font-headline tracking-tight font-semibold text-text-secondary hover:text-brand transition-colors" href="#">
            知识库
          </a>
          <a class="font-headline tracking-tight font-semibold text-brand border-b-2 border-brand pb-1 transition-colors" href="/stats">
            数据分析
          </a>
          <a class="font-headline tracking-tight font-semibold text-text-secondary hover:text-brand transition-colors" href="#">
            个人中心
          </a>
        </nav>
      </div>
      <div class="flex items-center gap-4">
        <button class="btn-icon-ghost material-symbols-outlined">notifications</button>
        <button class="btn-icon-ghost material-symbols-outlined">settings</button>
        <img alt="User Profile" class="w-8 h-8 rounded-full border border-outline-variant/15" src="https://lh3.googleusercontent.com/aida-public/AB6AXuDMKPVJL-B3aLQu4CtZ_KOGUSY3VDwcOYDbQaQbUQspANy_0Ie-w9P92EaTPnn6QSN0VqL5W2tyPmdWOra_LQYUSq7f3u8wKEjXbhb_oQmjYT9M-oJkgZJsjFsMfLtW2n5pRZV_wRSgR27cQLetYJP--OkjG_2v03qr2MRNl_66Ba7Aluj_lMEe5wlSKT2HJ-ATtZhSYgWpw4qILX2CIEX0Um5CbiBlIhnGqbbZoILW5Gl4rGmzfhFQrAERT2VMBn7-EYLXnzDmLBg" />
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex-1 pt-16 overflow-y-auto p-6">
      <!-- Background Decorative Elements -->
      <div class="fixed top-0 right-0 -z-10 w-[600px] h-[600px] bg-primary/5 rounded-full blur-3xl opacity-50 translate-x-1/2 -translate-y-1/2"></div>
      <div class="fixed bottom-0 left-0 -z-10 w-[400px] h-[400px] bg-secondary-container/10 rounded-full blur-3xl opacity-50 -translate-x-1/4 translate-y-1/4"></div>

      <!-- Header & Controls -->
      <div class="flex flex-col md:flex-row justify-between items-start md:items-center mb-8 gap-4">
        <div>
          <h1 class="text-3xl font-bold text-on-surface tracking-tight font-headline">数据分析中心</h1>
          <div class="flex items-center gap-3 mt-2">
            <div class="flex items-center gap-1.5 px-3 py-1.5 bg-success/10 text-success rounded-full">
              <span class="relative flex h-2 w-2">
                <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-500 opacity-75"></span>
                <span class="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
              </span>
              <span class="text-xs font-semibold">系统运行正常</span>
            </div>
            <span class="text-xs text-on-surface-variant">最后更新: 刚刚</span>
          </div>
        </div>
        <div class="flex items-center gap-3">
          <button class="flex items-center gap-2 px-5 py-2.5 bg-primary text-white rounded-xl hover:bg-primary/90 transition-all shadow-[0px_4px_6px_-1px_rgba(0,94,184,0.2)]">
            <span class="material-symbols-outlined text-lg">download</span>
            <span class="text-sm font-medium">导出数据</span>
          </button>
          <button class="flex items-center gap-2 px-5 py-2.5 bg-white text-on-surface rounded-xl hover:bg-surface-container transition-all shadow-[0px_1px_2px_0px_rgba(0,0,0,0.05)]">
            <span class="material-symbols-outlined text-lg">refresh</span>
            <span class="text-sm font-medium">刷新</span>
          </button>
        </div>
      </div>

      <!-- Metric Bento Grid -->
      <div class="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
        <!-- Metric 1: 总查询量 -->
        <div class="bg-surface-container-lowest p-6 rounded-xl relative overflow-hidden group transition-all hover:shadow-[0_12px_40px_rgba(0,94,184,0.06)]">
          <div class="relative z-10">
            <div class="flex justify-between items-start">
              <div>
                <p class="text-sm font-medium text-on-surface-variant mb-1">总查询量 (24h)</p>
                <h3 class="text-4xl font-bold text-on-surface">12,842</h3>
              </div>
              <div class="w-12 h-12 rounded-xl bg-primary/10 flex items-center justify-center">
                <span class="material-symbols-outlined text-2xl text-primary" style="font-variation-settings: 'FILL' 1;">query_stats</span>
              </div>
            </div>
            <div class="flex items-center gap-1 mt-4">
              <span class="material-symbols-outlined text-sm text-success" style="font-variation-settings: 'FILL' 1;">trending_up</span>
              <span class="text-xs font-bold text-success">+12.4% 对比昨日</span>
            </div>
          </div>
        </div>
        <!-- Metric 2: 检索准确率 -->
        <div class="bg-surface-container-lowest p-6 rounded-xl relative overflow-hidden group transition-all hover:shadow-[0_12px_40px_rgba(0,94,184,0.06)]">
          <div class="relative z-10">
            <div class="flex justify-between items-start">
              <div>
                <p class="text-sm font-medium text-on-surface-variant mb-1">检索准确率</p>
                <h3 class="text-4xl font-bold text-on-surface">99.2%</h3>
              </div>
              <div class="w-12 h-12 rounded-xl bg-success/10 flex items-center justify-center">
                <span class="material-symbols-outlined text-2xl text-success" style="font-variation-settings: 'FILL' 1;">verified</span>
              </div>
            </div>
            <div class="flex items-center gap-1 mt-4">
              <span class="material-symbols-outlined text-sm text-success" style="font-variation-settings: 'FILL' 1;">check_circle</span>
              <span class="text-xs font-bold text-success">符合临床标准</span>
            </div>
          </div>
        </div>
        <!-- Metric 3: 平均响应延迟 -->
        <div class="bg-surface-container-lowest p-6 rounded-xl relative overflow-hidden group transition-all hover:shadow-[0_12px_40px_rgba(0,94,184,0.06)]">
          <div class="relative z-10">
            <div class="flex justify-between items-start">
              <div>
                <p class="text-sm font-medium text-on-surface-variant mb-1">平均响应延迟</p>
                <h3 class="text-4xl font-bold text-on-surface">1.2s</h3>
              </div>
              <div class="w-12 h-12 rounded-xl bg-processing/10 flex items-center justify-center">
                <span class="material-symbols-outlined text-2xl text-processing" style="font-variation-settings: 'FILL' 1;">speed</span>
              </div>
            </div>
            <div class="flex items-center gap-1 mt-4">
              <span class="material-symbols-outlined text-sm text-processing" style="font-variation-settings: 'FILL' 1;">bolt</span>
              <span class="text-xs font-bold text-processing">高负载波动: +0.2s</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Charts Section -->
      <div class="grid grid-cols-1 lg:grid-cols-5 gap-6 mb-8">
        <!-- Line Chart: Query Trends -->
        <div class="lg:col-span-3 bg-surface-container-lowest p-6 rounded-xl transition-all hover:shadow-[0_12px_40px_rgba(0,94,184,0.06)]">
          <div class="flex justify-between items-center mb-8">
            <div>
              <h2 class="text-lg font-bold text-on-surface">查询趋势分析</h2>
              <p class="text-sm text-on-surface-variant">过去24小时系统查询量变化</p>
            </div>
            <div class="flex gap-2">
              <button class="px-4 py-2 bg-primary text-white rounded-full text-xs font-semibold hover:bg-primary/90 transition-colors shadow-[0px_4px_6px_-1px_rgba(0,94,184,0.2)]">24小时</button>
              <button class="px-4 py-2 bg-white text-on-surface-variant rounded-full text-xs font-semibold hover:bg-surface-container transition-colors shadow-[0px_1px_2px_0px_rgba(0,0,0,0.05)]">7天</button>
              <button class="px-4 py-2 bg-white text-on-surface-variant rounded-full text-xs font-semibold hover:bg-surface-container transition-colors shadow-[0px_1px_2px_0px_rgba(0,0,0,0.05)]">30天</button>
            </div>
          </div>
          <div class="h-72 w-full flex items-end justify-between gap-2 px-2">
            <div class="flex flex-col items-center flex-1 group cursor-pointer">
              <div class="w-full bg-primary/80 hover:bg-primary transition-all duration-300 rounded-t-lg group-hover:rounded-xl" style="height: 40%"></div>
              <span class="text-[10px] text-on-surface-variant mt-2">00:00</span>
            </div>
            <div class="flex flex-col items-center flex-1 group cursor-pointer">
              <div class="w-full bg-primary/80 hover:bg-primary transition-all duration-300 rounded-t-lg group-hover:rounded-xl" style="height: 25%"></div>
              <span class="text-[10px] text-on-surface-variant mt-2">04:00</span>
            </div>
            <div class="flex flex-col items-center flex-1 group cursor-pointer">
              <div class="w-full bg-primary/80 hover:bg-primary transition-all duration-300 rounded-t-lg group-hover:rounded-xl" style="height: 65%"></div>
              <span class="text-[10px] text-on-surface-variant mt-2">08:00</span>
            </div>
            <div class="flex flex-col items-center flex-1 group cursor-pointer">
              <div class="w-full bg-primary hover:bg-primary/80 transition-all duration-300 rounded-t-lg group-hover:rounded-xl" style="height: 90%"></div>
              <span class="text-[10px] text-primary font-semibold mt-2">12:00</span>
            </div>
            <div class="flex flex-col items-center flex-1 group cursor-pointer">
              <div class="w-full bg-primary/80 hover:bg-primary transition-all duration-300 rounded-t-lg group-hover:rounded-xl" style="height: 75%"></div>
              <span class="text-[10px] text-on-surface-variant mt-2">16:00</span>
            </div>
            <div class="flex flex-col items-center flex-1 group cursor-pointer">
              <div class="w-full bg-primary/80 hover:bg-primary transition-all duration-300 rounded-t-lg group-hover:rounded-xl" style="height: 55%"></div>
              <span class="text-[10px] text-on-surface-variant mt-2">20:00</span>
            </div>
            <div class="flex flex-col items-center flex-1 group cursor-pointer">
              <div class="w-full bg-primary/80 hover:bg-primary transition-all duration-300 rounded-t-lg group-hover:rounded-xl" style="height: 45%"></div>
              <span class="text-[10px] text-on-surface-variant mt-2">23:59</span>
            </div>
          </div>
        </div>
        <!-- Donut Chart: Dept Distribution -->
        <div class="lg:col-span-2 bg-surface-container-lowest p-6 rounded-xl transition-all hover:shadow-[0_12px_40px_rgba(0,94,184,0.06)] flex flex-col">
          <div class="flex justify-between items-start mb-8">
            <div>
              <h2 class="text-lg font-bold text-on-surface">科室调用分布</h2>
              <p class="text-sm text-on-surface-variant">各科室对系统的使用频率</p>
            </div>
            <button class="text-primary text-sm font-semibold flex items-center gap-1 hover:underline transition-all">
              <span class="material-symbols-outlined text-base">info</span>
              详情
            </button>
          </div>
          <div class="flex-1 flex items-center justify-center relative">
            <svg class="w-48 h-48 transform -rotate-90" viewBox="0 0 100 100">
              <circle cx="50" cy="50" fill="transparent" r="40" stroke="var(--color-subtle)" stroke-width="12"></circle>
              <circle cx="50" cy="50" fill="transparent" r="40" stroke="var(--color-primary)" stroke-dasharray="163 251" stroke-linecap="round" stroke-width="12"></circle>
              <circle cx="50" cy="50" fill="transparent" r="40" stroke="var(--color-secondary)" stroke-dasharray="60 251" stroke-dashoffset="-163" stroke-linecap="round" stroke-width="12"></circle>
              <circle cx="50" cy="50" fill="transparent" r="40" stroke="var(--color-tertiary)" stroke-dasharray="28 251" stroke-dashoffset="-223" stroke-linecap="round" stroke-width="12"></circle>
            </svg>
            <div class="absolute inset-0 flex flex-col items-center justify-center">
              <span class="text-3xl font-bold text-on-surface">85%</span>
              <span class="text-[10px] text-on-surface-variant font-medium">核心临床科室</span>
            </div>
          </div>
          <div class="grid grid-cols-2 gap-4 mt-6">
            <div class="flex items-center gap-3 p-3 rounded-xl hover:bg-surface-container-low transition-all cursor-pointer">
              <div class="w-3 h-3 rounded-full bg-primary"></div>
              <div class="flex-1">
                <span class="text-sm font-medium text-on-surface">内科</span>
                <span class="text-xs text-on-surface-variant block">65%</span>
              </div>
            </div>
            <div class="flex items-center gap-3 p-3 rounded-xl hover:bg-surface-container-low transition-all cursor-pointer">
              <div class="w-3 h-3 rounded-full bg-secondary"></div>
              <div class="flex-1">
                <span class="text-sm font-medium text-on-surface">外科</span>
                <span class="text-xs text-on-surface-variant block">24%</span>
              </div>
            </div>
            <div class="flex items-center gap-3 p-3 rounded-xl hover:bg-surface-container-low transition-all cursor-pointer">
              <div class="w-3 h-3 rounded-full bg-tertiary"></div>
              <div class="flex-1">
                <span class="text-sm font-medium text-on-surface">放射科</span>
                <span class="text-xs text-on-surface-variant block">11%</span>
              </div>
            </div>
            <div class="flex items-center gap-3 p-3 rounded-xl hover:bg-surface-container-low transition-all cursor-pointer">
              <div class="w-3 h-3 rounded-full bg-surface-variant"></div>
              <div class="flex-1">
                <span class="text-sm font-medium text-on-surface">其他</span>
                <span class="text-xs text-on-surface-variant block">5%</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Data Table Section -->
      <div class="bg-surface-container-lowest rounded-xl overflow-hidden transition-all hover:shadow-[0_12px_40px_rgba(0,94,184,0.06)]">
        <div class="p-6 flex justify-between items-center">
          <div>
            <h2 class="text-lg font-bold text-on-surface">查询日志</h2>
            <p class="text-sm text-on-surface-variant">最近100条系统查询记录</p>
          </div>
          <div class="flex gap-3">
            <div class="relative">
              <span class="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant text-lg">search</span>
              <input class="pl-10 pr-4 py-2.5 bg-white rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 w-64 transition-all shadow-[0px_1px_2px_0px_rgba(0,0,0,0.05)]" placeholder="搜索科室、关键词..." type="text" />
            </div>
            <button class="px-4 py-2.5 bg-white text-on-surface-variant rounded-xl hover:bg-surface-container transition-all flex items-center gap-2 shadow-[0px_1px_2px_0px_rgba(0,0,0,0.05)]">
              <span class="material-symbols-outlined text-base">filter_list</span>
              <span class="text-sm font-medium">筛选</span>
            </button>
            <button class="px-4 py-2.5 bg-primary text-white rounded-xl hover:bg-primary/90 transition-all flex items-center gap-2 shadow-[0px_4px_6px_-1px_rgba(0,94,184,0.2)]">
              <span class="material-symbols-outlined text-base">add</span>
              <span class="text-sm font-medium">新增</span>
            </button>
          </div>
        </div>
        <div class="overflow-x-auto">
          <table class="w-full text-left">
            <thead>
              <tr class="bg-surface-container-low/50">
                <th class="px-6 py-4 text-xs font-semibold text-on-surface-variant uppercase tracking-wider text-left">时间戳</th>
                <th class="px-6 py-4 text-xs font-semibold text-on-surface-variant uppercase tracking-wider text-left">查询科室</th>
                <th class="px-6 py-4 text-xs font-semibold text-on-surface-variant uppercase tracking-wider text-left">查询摘要</th>
                <th class="px-6 py-4 text-xs font-semibold text-on-surface-variant uppercase tracking-wider text-left">文献数</th>
                <th class="px-6 py-4 text-xs font-semibold text-on-surface-variant uppercase tracking-wider text-left">处理时长</th>
                <th class="px-6 py-4 text-xs font-semibold text-on-surface-variant uppercase tracking-wider text-left">准确度</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-surface-container">
              <tr class="hover:bg-primary/5 cursor-pointer transition-colors">
                <td class="px-6 py-4 whitespace-nowrap">
                  <div class="flex flex-col">
                    <span class="text-sm font-medium text-on-surface">14:28:45</span>
                    <span class="text-xs text-on-surface-variant">今天</span>
                  </div>
                </td>
                <td class="px-6 py-4 whitespace-nowrap">
                  <span class="px-3 py-1 rounded-full bg-primary/10 text-primary text-xs font-bold">心血管内科</span>
                </td>
                <td class="px-6 py-4">
                  <span class="text-sm font-medium text-on-surface line-clamp-1">分析阿司匹林对长期高血压患者的二级预防效果评价</span>
                  <span class="text-xs text-on-surface-variant line-clamp-1 block mt-1">心血管疾病，药物治疗，二级预防</span>
                </td>
                <td class="px-6 py-4 whitespace-nowrap">
                  <div class="flex items-center justify-center">
                    <span class="text-sm font-bold text-primary">12</span>
                    <span class="text-xs text-on-surface-variant ml-1">篇</span>
                  </div>
                </td>
                <td class="px-6 py-4 whitespace-nowrap">
                  <div class="flex items-center">
                    <span class="material-symbols-outlined text-sm text-primary mr-1">schedule</span>
                    <span class="text-sm font-medium">0.8s</span>
                  </div>
                </td>
                <td class="px-6 py-4 whitespace-nowrap">
                  <div class="flex items-center gap-2">
                    <div class="w-20 h-2 bg-surface-container rounded-full overflow-hidden">
                      <div class="bg-emerald-500 h-full rounded-full" style="width: 98%"></div>
                    </div>
                    <span class="text-xs font-bold text-success">98%</span>
                  </div>
                </td>
              </tr>
              <tr class="hover:bg-primary/5 cursor-pointer transition-colors">
                <td class="px-6 py-4 whitespace-nowrap">
                  <div class="flex flex-col">
                    <span class="text-sm font-medium text-on-surface">14:25:12</span>
                    <span class="text-xs text-on-surface-variant">今天</span>
                  </div>
                </td>
                <td class="px-6 py-4 whitespace-nowrap">
                  <span class="px-3 py-1 rounded-full bg-secondary/10 text-secondary text-xs font-bold">内分泌科</span>
                </td>
                <td class="px-6 py-4">
                  <span class="text-sm font-medium text-on-surface line-clamp-1">SGLT2抑制剂在2型糖尿病合并慢性肾病患者中的用药剂量建议</span>
                  <span class="text-xs text-on-surface-variant line-clamp-1 block mt-1">糖尿病，肾病，药物治疗，剂量调整</span>
                </td>
                <td class="px-6 py-4 whitespace-nowrap">
                  <div class="flex items-center justify-center">
                    <span class="text-sm font-bold text-primary">8</span>
                    <span class="text-xs text-on-surface-variant ml-1">篇</span>
                  </div>
                </td>
                <td class="px-6 py-4 whitespace-nowrap">
                  <div class="flex items-center">
                    <span class="material-symbols-outlined text-sm text-primary mr-1">schedule</span>
                    <span class="text-sm font-medium">1.1s</span>
                  </div>
                </td>
                <td class="px-6 py-4 whitespace-nowrap">
                  <div class="flex items-center gap-2">
                    <div class="w-20 h-2 bg-surface-container rounded-full overflow-hidden">
                      <div class="bg-emerald-500 h-full rounded-full" style="width: 95%"></div>
                    </div>
                    <span class="text-xs font-bold text-success">95%</span>
                  </div>
                </td>
              </tr>
              <tr class="hover:bg-primary/5 cursor-pointer transition-colors">
                <td class="px-6 py-4 whitespace-nowrap">
                  <div class="flex flex-col">
                    <span class="text-sm font-medium text-on-surface">14:22:05</span>
                    <span class="text-xs text-on-surface-variant">今天</span>
                  </div>
                </td>
                <td class="px-6 py-4 whitespace-nowrap">
                  <span class="px-3 py-1 rounded-full bg-tertiary/10 text-tertiary text-xs font-bold">影像科</span>
                </td>
                <td class="px-6 py-4">
                  <span class="text-sm font-medium text-on-surface line-clamp-1">检索最新关于早期肺结节AI筛查的影像学诊断共识</span>
                  <span class="text-xs text-on-surface-variant line-clamp-1 block mt-1">影像诊断，AI筛查，肺结节，诊断共识</span>
                </td>
                <td class="px-6 py-4 whitespace-nowrap">
                  <div class="flex items-center justify-center">
                    <span class="text-sm font-bold text-primary">15</span>
                    <span class="text-xs text-on-surface-variant ml-1">篇</span>
                  </div>
                </td>
                <td class="px-6 py-4 whitespace-nowrap">
                  <div class="flex items-center">
                    <span class="material-symbols-outlined text-sm text-primary mr-1">schedule</span>
                    <span class="text-sm font-medium">1.4s</span>
                  </div>
                </td>
                <td class="px-6 py-4 whitespace-nowrap">
                  <div class="flex items-center gap-2">
                    <div class="w-20 h-2 bg-surface-container rounded-full overflow-hidden">
                      <div class="bg-orange-500 h-full rounded-full" style="width: 82%"></div>
                    </div>
                    <span class="text-xs font-bold text-processing">82%</span>
                  </div>
                </td>
              </tr>
              <tr class="hover:bg-primary/5 cursor-pointer transition-colors">
                <td class="px-6 py-4 whitespace-nowrap">
                  <div class="flex flex-col">
                    <span class="text-sm font-medium text-on-surface">14:18:30</span>
                    <span class="text-xs text-on-surface-variant">今天</span>
                  </div>
                </td>
                <td class="px-6 py-4 whitespace-nowrap">
                  <span class="px-3 py-1 rounded-full bg-quaternary/10 text-quaternary text-xs font-bold">骨科</span>
                </td>
                <td class="px-6 py-4">
                  <span class="text-sm font-medium text-on-surface line-clamp-1">老年性骨质疏松患者全髋置换术后康复流程优化研究</span>
                  <span class="text-xs text-on-surface-variant line-clamp-1 block mt-1">骨科手术，骨质疏松，康复治疗，流程优化</span>
                </td>
                <td class="px-6 py-4 whitespace-nowrap">
                  <div class="flex items-center justify-center">
                    <span class="text-sm font-bold text-primary">5</span>
                    <span class="text-xs text-on-surface-variant ml-1">篇</span>
                  </div>
                </td>
                <td class="px-6 py-4 whitespace-nowrap">
                  <div class="flex items-center">
                    <span class="material-symbols-outlined text-sm text-primary mr-1">schedule</span>
                    <span class="text-sm font-medium">0.7s</span>
                  </div>
                </td>
                <td class="px-6 py-4 whitespace-nowrap">
                  <div class="flex items-center gap-2">
                    <div class="w-20 h-2 bg-surface-container rounded-full overflow-hidden">
                      <div class="bg-emerald-500 h-full rounded-full" style="width: 99%"></div>
                    </div>
                    <span class="text-xs font-bold text-success">99%</span>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="p-4 bg-surface-container-low/30 flex justify-between items-center">
          <span class="text-xs text-on-surface-variant font-medium">显示 1 至 10 条，共 1,248 条结果</span>
          <div class="flex gap-1">
            <button class="w-8 h-8 flex items-center justify-center rounded-xl bg-white text-on-surface-variant hover:bg-surface-container transition-all shadow-[0px_1px_2px_0px_rgba(0,0,0,0.05)]">
              <span class="material-symbols-outlined text-sm">chevron_left</span>
            </button>
            <button class="w-8 h-8 flex items-center justify-center rounded-xl bg-primary text-white shadow-[0px_4px_6px_-1px_rgba(0,94,184,0.2)] font-bold text-xs">1</button>
            <button class="w-8 h-8 flex items-center justify-center rounded-xl bg-white text-on-surface-variant hover:bg-surface-container transition-all shadow-[0px_1px_2px_0px_rgba(0,0,0,0.05)] text-xs font-medium">2</button>
            <button class="w-8 h-8 flex items-center justify-center rounded-xl bg-white text-on-surface-variant hover:bg-surface-container transition-all shadow-[0px_1px_2px_0px_rgba(0,0,0,0.05)] text-xs font-medium">3</button>
            <button class="w-8 h-8 flex items-center justify-center rounded-xl bg-white text-on-surface-variant hover:bg-surface-container transition-all shadow-[0px_1px_2px_0px_rgba(0,0,0,0.05)]">
              <span class="material-symbols-outlined text-sm">chevron_right</span>
            </button>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import '@/assets/styles/enhancements.css'
</script>

<style scoped>
</style>

<style>
.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
  vertical-align: middle;
}
</style>
