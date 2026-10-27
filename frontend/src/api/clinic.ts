import { get, post, put, del } from './request'
import type { PageResult } from './types'

/**
 * 门诊管理。
 *
 * <p><b>开处方只传药品 ID 与用法用量</b>：药品名、规格、单价、小计全部由服务端
 * 取快照并计算。前端传价格的话，改一个请求体就能一分钱买药。</p>
 */

/** 一期「一次挂号 = 一次就诊」，就诊列表直接复用挂号 VO */
export interface VisitVO {
  id: string
  regNo: string
  patientId: number
  patientName: string
  patientNo: string
  doctorId: number
  doctorName: string
  deptId: number
  scheduleId: number
  regType: number
  regFee: number
  queueNo: number
  /** 0 待就诊 / 1 就诊中 / 2 已完成 / 3 已退号 */
  status: number
  regTime: string
}

export interface ReceiveResult {
  visitId: string
  regNo: string
  queueNo: number
  patientId: number
  patientNo: string
  patientName: string
  gender: number
  age: number
  /** 过敏史，开方前必看 */
  allergyHistory: string
  visitStatus: number
  recordId: string
  recordStatus: number
}

export interface MedicalRecordVO {
  id: string
  visitId: string
  visitNo: string
  patientId: number
  patientName: string
  doctorId: number
  doctorName: string
  visitType: number
  chiefComplaint: string
  presentIllness: string
  physicalExam: string
  diagnosis: string
  icdCode: string
  visitTime: string
  /** 0 草稿 / 1 已提交 / 2 已归档 */
  status: number
}

export interface MedicalRecordRequest {
  visitId: string
  chiefComplaint?: string
  presentIllness?: string
  physicalExam?: string
  diagnosis?: string
  icdCode?: string
  /** true 提交（不可再改）/ false 存草稿 */
  submit?: boolean
}

export interface DrugOption {
  id: string
  drugCode: string
  drugName: string
  spec: string
  unit: string
  salePrice: number
  category: string
  /** 1 麻精毒麻 */
  isSpecial: number
}

export interface PrescriptionItem {
  drugId: string
  usage?: string
  dosage?: string
  frequency?: string
  days?: number
  quantity: number
}

export interface PrescriptionRequest {
  visitId: string
  /** 1 西药 / 2 中药 / 3 中成药 */
  prescType: number
  diagnosis?: string
  items: PrescriptionItem[]
}

export interface PrescriptionItemVO {
  id: string
  drugId: number
  drugName: string
  spec: string
  usage: string
  dosage: string
  frequency: string
  days: number
  quantity: number
  price: number
  amount: number
}

export interface PrescriptionVO {
  id: string
  prescNo: string
  patientId: number
  patientName: string
  doctorId: number
  doctorName: string
  visitId: string
  prescType: number
  diagnosis: string
  totalAmount: number
  /** 0 待审核 / 1 已审核 / 2 已收费 / 3 已发药 / 4 已退 */
  status: number
  createTime: string
  items: PrescriptionItemVO[]
}

export interface PrescriptionQuery {
  pageNum?: number
  pageSize?: number
  visitId?: string
  patientId?: string
  doctorId?: string
  status?: number
  prescType?: number
}

/* ---------------- 就诊 ---------------- */

/** 就诊分页：keyword 走 RegistrationQuery 的模糊搜索（患者姓名或挂号号） */
export function pageVisits(params: PrescriptionQuery & { status?: number; keyword?: string }) {
  return get<PageResult<VisitVO>>('/clinic/visit/page', params)
}

export function receiveVisit(id: string) {
  return post<ReceiveResult>(`/clinic/visit/${id}/receive`)
}

/* ---------------- 病历 ---------------- */

export function saveRecord(data: MedicalRecordRequest) {
  return post<string>('/clinic/record', data)
}

export function getRecord(visitId: string) {
  return get<MedicalRecordVO | null>('/clinic/record', { visitId })
}

/* ---------------- 处方 ---------------- */

export function pagePrescriptions(params: PrescriptionQuery) {
  return get<PageResult<PrescriptionVO>>('/clinic/prescription/page', params)
}

export function getPrescription(id: string) {
  return get<PrescriptionVO>(`/clinic/prescription/${id}`)
}

export function createPrescription(data: PrescriptionRequest) {
  return post<string>('/clinic/prescription', data)
}

export function auditPrescription(id: string) {
  return put<void>(`/clinic/prescription/${id}/audit`)
}

export function cancelPrescription(id: string) {
  return del<void>(`/clinic/prescription/${id}`)
}

export function drugOptions(keyword?: string) {
  return get<DrugOption[]>('/clinic/drug/options', keyword ? { keyword } : undefined)
}
