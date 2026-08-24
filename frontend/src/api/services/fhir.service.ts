import request from '../request'
import type { FhirCondition, FhirObservation, FhirPatient, FhirQuestionnaireResponse } from '../types/fhir'
import type { HealthProfile } from './user.service'

/** 医生端概览结构化问卷记录（来自 /his/fhir/Patient/{fhirId}/overview） */
export interface PatientOverviewRecord {
  fhirId?: string
  sessionId?: string
  questionnaireId?: string
  questionnaireTitle?: string
  score?: number | null
  severity?: string | null
  authoredDate?: string
  status?: string
}

/** 患者档案概览：FHIR Patient + 健康档案 + 问卷记录（聚合接口返回） */
export interface PatientOverview {
  patient: FhirPatient | null
  localUserId: number | null
  hasHealthProfile: boolean
  healthProfile: HealthProfile | null
  questionnaireResponses: PatientOverviewRecord[]
}

/**
 * 证件号检索使用的 identifier.system 约定值（身份证体系）。
 * 需与 HIS 导入数据中 Patient.identifier.system 的取值保持一致，
 * 若约定值变更，仅需修改此处常量。
 */
export const IDENTIFIER_SYSTEM = 'idcard'

/**
 * FHIR 患者查询服务
 *
 * 错误策略：所有读方法使用 silent:true 抑制全局错误弹窗，
 * 请求失败时**抛出异常**交由调用方呈现错误态，从而区分
 * "未找到患者/无记录"与"加载失败"——医生端误判会造成医疗风险。
 */
class FhirService {
  // 姓名模糊搜索（返回当页列表；后端不返回总条数 total）
  async searchByName(name: string, page = 0, size = 10): Promise<FhirPatient[]> {
    const response = await request.get('/his/fhir/Patient/search', { params: { name, page, size }, silent: true })
    return (response.data.data || []) as FhirPatient[]
  }

  // 手机号精确查询（返回单个患者，统一包装为数组）
  async findByPhone(phone: string): Promise<FhirPatient[]> {
    const response = await request.get('/his/fhir/Patient/by-phone', { params: { phone }, silent: true })
    const data = response.data.data
    return data ? [data as FhirPatient] : []
  }

  // 证件号查询（按约定 system 查询单个患者，统一包装为数组）
  async findByIdentifier(value: string): Promise<FhirPatient[]> {
    const response = await request.get('/his/fhir/Patient/by-identifier', {
      params: { system: IDENTIFIER_SYSTEM, value },
      silent: true
    })
    const data = response.data.data
    return data ? [data as FhirPatient] : []
  }

  // 患者详情
  async getPatient(fhirId: string): Promise<FhirPatient | null> {
    const response = await request.get(`/his/fhir/Patient/${fhirId}`, { silent: true })
    return (response.data.data as FhirPatient) || null
  }

  // 患者档案概览（FHIR Patient + 健康档案 + 问卷记录，聚合一次返回）
  async getPatientOverview(fhirId: string): Promise<PatientOverview | null> {
    const response = await request.get(`/his/fhir/Patient/${fhirId}/overview`, { silent: true })
    return (response.data.data as PatientOverview) || null
  }

  // 系统患者总数
  async countPatients(): Promise<number> {
    const response = await request.get('/his/fhir/Patient/count', { silent: true })
    return Number(response.data.data ?? 0)
  }

  // 患者病情记录
  async getConditions(patientFhirId: string, page = 1, size = 10): Promise<FhirCondition[]> {
    const response = await request.get('/his/fhir/Condition/by-patient', { params: { patientFhirId, page, size }, silent: true })
    return (response.data.data || []) as FhirCondition[]
  }

  // 患者检验与观察
  async getObservations(patientFhirId: string, page = 1, size = 10): Promise<FhirObservation[]> {
    const response = await request.get('/his/fhir/Observation/by-patient', { params: { patientFhirId, page, size }, silent: true })
    return (response.data.data || []) as FhirObservation[]
  }

  // 患者问卷回答记录
  async getQuestionnaireResponses(patientFhirId: string, page = 1, size = 10): Promise<FhirQuestionnaireResponse[]> {
    const response = await request.get('/his/fhir/QuestionnaireResponse/by-patient', {
      params: { patientFhirId, page, size },
      silent: true
    })
    return (response.data.data || []) as FhirQuestionnaireResponse[]
  }
}

export const fhirService = new FhirService()
