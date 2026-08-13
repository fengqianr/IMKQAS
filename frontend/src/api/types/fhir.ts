// FHIR R4 资源类型定义与解析工具
// 服务端通过 HAPI FHIR 序列化返回标准 R4 JSON，此处按实际返回结构声明
// 说明：HIS 通过 FHIR Bundle 导入患者数据，因此各字段可能缺失，解析函数均做容错处理

// ==================== 通用基础类型 ====================

export interface FhirCoding {
  system?: string
  code?: string
  display?: string
}

export interface FhirCodeableConcept {
  coding?: FhirCoding[]
  text?: string
}

/** 从 CodeableConcept 提取可读文本（优先 text，其次 coding.display，最后 coding.code） */
export function conceptText(concept?: FhirCodeableConcept | null): string {
  if (!concept) return ''
  if (concept.text) return concept.text
  const display = concept.coding?.find(c => c.display)?.display
  if (display) return display
  return concept.coding?.find(c => c.code)?.code ?? ''
}

// ==================== Patient 患者 ====================

export interface FhirHumanName {
  family?: string
  given?: string[]
  text?: string
}

export interface FhirIdentifier {
  system?: string
  value?: string
}

export interface FhirContactPoint {
  system?: string
  value?: string
}

export interface FhirAddress {
  text?: string
}

export interface FhirPatient {
  resourceType?: string
  id?: string
  name?: FhirHumanName[]
  gender?: string
  birthDate?: string
  identifier?: FhirIdentifier[]
  telecom?: FhirContactPoint[]
  address?: FhirAddress[]
  maritalStatus?: { text?: string }
  meta?: { versionId?: string; lastUpdated?: string }
}

/** 患者姓名（family + given 拼接） */
export function patientName(p?: FhirPatient | null): string {
  if (!p?.name?.length) return '未知患者'
  const name = p.name[0]
  if (name.text) return name.text
  return [name.family, ...(name.given ?? [])].filter(Boolean).join('')
}

/** 姓名首字（头像占位用） */
export function patientInitial(p?: FhirPatient | null): string {
  const name = patientName(p)
  return name === '未知患者' ? '?' : name.charAt(0)
}

/** 性别中文映射 */
export function genderText(gender?: string): string {
  const map: Record<string, string> = { male: '男', female: '女', other: '其他', unknown: '未知' }
  return (gender && map[gender]) || gender || '未知'
}

/** 由出生日期计算年龄（周岁），无效时返回 null */
export function calcAge(birthDate?: string): number | null {
  if (!birthDate) return null
  const birth = new Date(birthDate)
  if (isNaN(birth.getTime())) return null
  const now = new Date()
  let age = now.getFullYear() - birth.getFullYear()
  const monthDiff = now.getMonth() - birth.getMonth()
  if (monthDiff < 0 || (monthDiff === 0 && now.getDate() < birth.getDate())) age--
  return age >= 0 ? age : null
}

/** 联系电话（telecom 中 phone 类型） */
export function patientPhone(p?: FhirPatient | null): string {
  return p?.telecom?.find(t => !t.system || t.system === 'phone')?.value ?? ''
}

/** 证件号（identifier 第一条） */
export function patientIdentifier(p?: FhirPatient | null): FhirIdentifier | null {
  return p?.identifier?.length ? p.identifier[0] : null
}

/** 家庭住址 */
export function patientAddress(p?: FhirPatient | null): string {
  return p?.address?.[0]?.text ?? ''
}

/** 手机号脱敏：13812345678 -> 138-****-5678 */
export function maskPhone(phone: string): string {
  const digits = phone.replace(/\D/g, '')
  if (digits.length >= 11) {
    return `${digits.slice(0, 3)}-****-${digits.slice(-4)}`
  }
  if (digits.length > 7) {
    return `${digits.slice(0, 3)}-****-${digits.slice(-4)}`
  }
  return phone || '—'
}

/** 证件号脱敏：前4位 + 星号 + 后4位 */
export function maskIdNumber(value?: string): string {
  if (!value) return '—'
  if (value.length <= 8) return `${value.slice(0, 2)}***${value.slice(-2)}`
  const maskLen = value.length - 8
  return `${value.slice(0, 4)}${'*'.repeat(maskLen)}${value.slice(-4)}`
}

/** 显示出生日期（取日期部分） */
export function formatDate(dateStr?: string): string {
  if (!dateStr) return '—'
  return dateStr.slice(0, 10)
}

// ==================== Condition 病情 ====================

export interface FhirCondition {
  resourceType?: string
  id?: string
  clinicalStatus?: FhirCodeableConcept
  verificationStatus?: FhirCodeableConcept
  code?: FhirCodeableConcept
  category?: FhirCodeableConcept[]
  severity?: FhirCodeableConcept
  subject?: { reference?: string }
  onsetDateTime?: string
  abatementDateTime?: string
  recordedDate?: string
  note?: { text?: string }[]
}

/** 病情诊断名 */
export function conditionName(c?: FhirCondition | null): string {
  return conceptText(c?.code) || '未命名诊断'
}

/** 临床状态中文与颜色 */
export function clinicalStatusMeta(status?: string): { text: string; color: string } {
  const map: Record<string, { text: string; color: string }> = {
    active: { text: '活动', color: 'text-error' },
    recurrence: { text: '复发', color: 'text-error' },
    relapse: { text: '复发', color: 'text-error' },
    inactive: { text: '已愈', color: 'text-success' },
    remission: { text: '缓解', color: 'text-success' },
    resolved: { text: '已解决', color: 'text-success' }
  }
  return (status && map[status]) || { text: status || '未知', color: 'text-secondary' }
}

/** 病情记录备注 */
export function conditionNote(c?: FhirCondition | null): string {
  return c?.note?.[0]?.text ?? ''
}

// ==================== Observation 检验观察 ====================

export interface FhirObservation {
  resourceType?: string
  id?: string
  status?: string
  code?: FhirCodeableConcept
  category?: FhirCodeableConcept[]
  subject?: { reference?: string }
  effectiveDateTime?: string
  valueQuantity?: { value?: number; unit?: string; code?: string }
  valueString?: string
  valueCodeableConcept?: FhirCodeableConcept
  component?: {
    code?: FhirCodeableConcept
    valueQuantity?: { value?: number; unit?: string }
  }[]
  interpretation?: FhirCodeableConcept[]
}

/** 检验项目名 */
export function observationName(o?: FhirObservation | null): string {
  return conceptText(o?.code) || '未命名项目'
}

/** 检验结果值（含复合值，如血压） */
export function observationValue(o?: FhirObservation | null): string {
  if (!o) return '—'
  if (o.valueQuantity?.value != null) {
    return `${o.valueQuantity.value}${o.valueQuantity.unit ?? ''}`
  }
  if (o.valueString != null) return o.valueString
  if (o.valueCodeableConcept) return conceptText(o.valueCodeableConcept)
  if (o.component?.length) {
    return o.component
      .map(c => {
        const q = c.valueQuantity
        if (q?.value == null) return ''
        const label = conceptText(c.code)
        return label ? `${label}: ${q.value}${q.unit ?? ''}` : `${q.value}${q.unit ?? ''}`
      })
      .filter(Boolean)
      .join(' / ')
  }
  return '—'
}

/** 观察状态中文 */
export function observationStatusText(status?: string): string {
  const map: Record<string, string> = {
    final: '完成',
    amended: '修正',
    corrected: '已修正',
    cancelled: '已取消',
    registered: '已登记',
    preliminary: '初步',
    unknown: '未知'
  }
  return (status && map[status]) || status || '—'
}

/** 是否有异常标记 */
export function hasAbnormal(o?: FhirObservation | null): boolean {
  return !!o?.interpretation?.length
}

// ==================== QuestionnaireResponse 问卷回答 ====================

export interface FhirQuestionnaireResponseItemAnswer {
  valueString?: string
  valueBoolean?: boolean
  valueInteger?: number
  valueDecimal?: number
  valueDate?: string
  valueDateTime?: string
  valueCoding?: FhirCoding
  valueQuantity?: { value?: number; unit?: string }
}

export interface FhirQuestionnaireResponseItem {
  linkId?: string
  text?: string
  answer?: FhirQuestionnaireResponseItemAnswer[]
  item?: FhirQuestionnaireResponseItem[]
}

export interface FhirQuestionnaireResponse {
  resourceType?: string
  id?: string
  questionnaire?: string
  status?: string
  subject?: { reference?: string }
  authored?: string
  item?: FhirQuestionnaireResponseItem[]
}

/** 问卷回答文本 */
export function answerText(ans?: FhirQuestionnaireResponseItemAnswer | null): string {
  if (!ans) return ''
  if (ans.valueString != null) return ans.valueString
  if (ans.valueBoolean != null) return ans.valueBoolean ? '是' : '否'
  if (ans.valueInteger != null) return String(ans.valueInteger)
  if (ans.valueDecimal != null) return String(ans.valueDecimal)
  if (ans.valueDate) return ans.valueDate
  if (ans.valueDateTime) return ans.valueDateTime
  if (ans.valueCoding?.display) return ans.valueCoding.display
  if (ans.valueCoding?.code) return ans.valueCoding.code
  if (ans.valueQuantity?.value != null) {
    return `${ans.valueQuantity.value}${ans.valueQuantity.unit ?? ''}`
  }
  return ''
}

/** 问卷回答状态中文 */
export function questionnaireStatusText(status?: string): string {
  const map: Record<string, string> = {
    completed: '已完成',
    'in-progress': '进行中',
    stopped: '已停止',
    entered_in_error: '已作废'
  }
  return (status && map[status]) || status || '—'
}
