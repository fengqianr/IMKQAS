// 批量导诊类型定义与解析工具
// 注意：DepartmentTriageController 返回裸 JSON（List<DepartmentTriageResult>），
// 非 ApiResponse 包装，因此响应取 response.data（数组）。
// 枚举字段序列化为英文名（如 source: "RULE_ENGINE"），需在此处映射为中文。

// ==================== 科室推荐 ====================

export interface DepartmentRecommendation {
  /** 科室 ID */
  departmentId?: string
  /** 科室名称 */
  departmentName: string
  /** 推荐置信度（0-1） */
  confidence?: number
  /** 推荐理由 */
  reason?: string
  /** 命中的症状关键词 */
  matchedSymptoms?: string[]
  /** 是否急诊科室 */
  emergency?: boolean
  /** 优先级（数值越小越优先） */
  priority?: number
}

// ==================== 急诊检查 ====================

export interface EmergencyCheckResult {
  /** 是否紧急（后端 isEmergency，Jackson 序列化为 emergency） */
  emergency?: boolean
  /** 急诊分级：CRITICAL / HIGH / MEDIUM / LOW */
  emergencyLevel?: string
  /** 急诊症状关键词 */
  emergencySymptoms?: string[]
  /** 应立即采取的行动 */
  immediateAction?: string
  /** 警示信息 */
  warningMessage?: string
  /** 处置建议 */
  advice?: string
}

// ==================== 单条分流结果 ====================

export interface DepartmentTriageResult {
  /** 症状描述（后端为 String，非数组） */
  symptoms?: string
  /** 科室推荐列表（按优先级排序） */
  recommendations: DepartmentRecommendation[]
  /** 急诊检查结果 */
  emergencyCheck?: EmergencyCheckResult
  /** 总体置信度 */
  confidence?: number
  /** 结果来源：RULE_ENGINE / LLM / HYBRID 等（英文枚举） */
  source?: string
  /** 处理耗时（毫秒） */
  processingTimeMs?: number
  /** 综合建议 */
  advice?: string
  /** 用户 ID */
  userId?: number
}

// ==================== 批量请求 ====================

export interface BatchTriageRequest {
  /** 症状描述列表（最多 20 条，不能为空） */
  symptomsList: string[]
  /** 用户 ID（可选） */
  userId?: number
  /** 是否包含急诊检查（默认 true） */
  includeEmergencyCheck?: boolean
}

// ==================== 枚举 → 中文映射 ====================

/** 结果来源中文描述 */
export function sourceText(source?: string): string {
  const map: Record<string, string> = {
    RULE_ENGINE: '规则引擎',
    RULE_ENGINE_FALLBACK: '规则引擎(降级)',
    LLM: '大模型',
    LLM_FALLBACK: '大模型(降级)',
    HYBRID: '混合模式',
    FALLBACK: '降级模式'
  }
  return (source && map[source]) || source || '未知'
}

/** 急诊分级中文描述 */
export function emergencyLevelText(level?: string): string {
  const map: Record<string, string> = {
    CRITICAL: '危急',
    HIGH: '高危',
    MEDIUM: '中危',
    LOW: '低危'
  }
  return (level && map[level]) || level || ''
}

/** 置信度格式化：0.85 → "85%"，空值返回 "—" */
export function confidenceText(conf?: number): string {
  if (conf == null || Number.isNaN(conf)) return '—'
  return `${(conf * 100).toFixed(0)}%`
}
