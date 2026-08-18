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

class FhirService {
  // 姓名模糊搜索（返回当页列表；后端不返回总条数 total）
  async searchByName(name: string, page = 0, size = 10): Promise<FhirPatient[]> {
    try {
      const response = await request.get('/his/fhir/Patient/search', { params: { name, page, size } })
      return (response.data.data || []) as FhirPatient[]
    } catch (error: any) {
      console.error('患者姓名搜索失败:', error)
      return []
    }
  }

  // 手机号精确查询（返回单个患者，统一包装为数组）
  async findByPhone(phone: string): Promise<FhirPatient[]> {
    try {
      const response = await request.get('/his/fhir/Patient/by-phone', { params: { phone } })
      const data = response.data.data
      return data ? [data as FhirPatient] : []
    } catch (error: any) {
      console.error('手机号查询失败:', error)
      return []
    }
  }

  // 证件号查询（按约定 system 查询单个患者，统一包装为数组）
  async findByIdentifier(value: string): Promise<FhirPatient[]> {
    try {
      const response = await request.get('/his/fhir/Patient/by-identifier', {
        params: { system: IDENTIFIER_SYSTEM, value }
      })
      const data = response.data.data
      return data ? [data as FhirPatient] : []
    } catch (error: any) {
      console.error('证件号查询失败:', error)
      return []
    }
  }

  // 患者详情
  async getPatient(fhirId: string): Promise<FhirPatient | null> {
    try {
      const response = await request.get(`/his/fhir/Patient/${fhirId}`)
      return (response.data.data as FhirPatient) || null
    } catch (error: any) {
      console.error('患者详情获取失败:', error)
      return null
    }
  }

  // 患者档案概览（FHIR Patient + 健康档案 + 问卷记录，聚合一次返回）
  async getPatientOverview(fhirId: string): Promise<PatientOverview | null> {
    try {
      const response = await request.get(`/his/fhir/Patient/${fhirId}/overview`)
      return (response.data.data as PatientOverview) || null
    } catch (error: any) {
      console.error('患者档案概览获取失败:', error)
      return null
    }
  }

  // 系统患者总数
  async countPatients(): Promise<number> {
    try {
      const response = await request.get('/his/fhir/Patient/count')
      return Number(response.data.data ?? 0)
    } catch (error: any) {
      console.error('患者总数获取失败:', error)
      return 0
    }
  }

  // 患者病情记录
  async getConditions(patientFhirId: string, page = 1, size = 10): Promise<FhirCondition[]> {
    try {
      const response = await request.get('/his/fhir/Condition/by-patient', { params: { patientFhirId, page, size } })
      return (response.data.data || []) as FhirCondition[]
    } catch (error: any) {
      console.error('病情记录获取失败:', error)
      return []
    }
  }

  // 患者检验与观察
  async getObservations(patientFhirId: string, page = 1, size = 10): Promise<FhirObservation[]> {
    try {
      const response = await request.get('/his/fhir/Observation/by-patient', { params: { patientFhirId, page, size } })
      return (response.data.data || []) as FhirObservation[]
    } catch (error: any) {
      console.error('检验观察获取失败:', error)
      return []
    }
  }

  // 患者问卷回答记录
  async getQuestionnaireResponses(patientFhirId: string, page = 1, size = 10): Promise<FhirQuestionnaireResponse[]> {
    try {
      const response = await request.get('/his/fhir/QuestionnaireResponse/by-patient', {
        params: { patientFhirId, page, size }
      })
      return (response.data.data || []) as FhirQuestionnaireResponse[]
    } catch (error: any) {
      console.error('问卷记录获取失败:', error)
      return []
    }
  }
}

export const fhirService = new FhirService()
