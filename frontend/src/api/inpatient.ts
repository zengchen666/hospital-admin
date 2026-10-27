import { get, post, put } from './request'
import type { PageResult } from './types'

/** 住院管理 */

export interface BedVO {
  id: string
  wardId: number
  wardName: string
  bedNo: string
  /** 1 普通 / 2 重症 / 3 VIP */
  bedType: number
  price: number
  /** 0 空闲 / 1 占用 / 2 维修 */
  status: number
  occupantName: string
}

export interface BedQuery {
  pageNum?: number
  pageSize?: number
  wardId?: string
  status?: number
  bedType?: number
  onlyAvailable?: boolean
}

export interface AdmissionVO {
  id: string
  admissionNo: string
  patientId: number
  patientName: string
  patientNo: string
  deptId: number
  bedId: number
  bedNo: string
  wardId: number
  wardName: string
  doctorId: number
  doctorName: string
  admissionTime: string
  dischargeTime: string
  diagnosis: string
  deposit: number
  /** 0 在院 / 1 出院 / 2 转科 */
  status: number
  stayDays: number
}

export interface AdmissionQuery {
  pageNum?: number
  pageSize?: number
  patientId?: string
  deptId?: string
  doctorId?: string
  wardId?: string
  status?: number
}

export interface AdmissionRequest {
  patientId: string
  deptId: string
  /** 可空：先登记后分床是常态 */
  bedId?: string
  doctorId: string
  nurseId?: string
  diagnosis?: string
  deposit?: number
}

export interface OrderVO {
  id: string
  admissionId: string
  /** 1 长期 / 2 临时 */
  orderType: number
  content: string
  drugId: number
  dosage: string
  frequency: string
  startTime: string
  stopTime: string
  doctorId: number
  doctorName: string
  /** 0 未执行 / 1 已执行 / 2 已停止 */
  status: number
  createTime: string
}

export interface OrderRequest {
  admissionId: string
  orderType: number
  content: string
  drugId?: string
  dosage?: string
  frequency?: string
  startTime?: string
}

export interface WardVO {
  id: number
  wardName: string
  wardCode: string
  deptId: number
  floor: string
  bedCount: number
  /** 0 停用 / 1 启用 */
  status: number
}

export interface NursingVO {
  id: string
  admissionId: string
  patientName: string
  bedNo: string
  /** 1 护理记录 / 2 体温单 / 3 生命体征 */
  recordType: number
  recordTime: string
  temperature: number
  pulse: number
  breath: number
  bloodPressure: string
  spo2: number
  intake: number
  output: number
  content: string
  nurseId: number
  nurseName: string
}

export interface NursingQuery {
  pageNum?: number
  pageSize?: number
  admissionId?: string
  recordType?: number
}

export interface NursingRequest {
  admissionId: string
  recordType: number
  recordTime?: string
  temperature?: number
  pulse?: number
  breath?: number
  bloodPressure?: string
  spo2?: number
  intake?: number
  output?: number
  content?: string
}

/* ---------------- 病区 / 床位 ---------------- */

export function listWards() {
  return get<WardVO[]>('/inpatient/ward/list')
}

/** 可分配床位：只返回空闲床，分床弹窗用 */
export function listAvailableBeds(wardId?: string) {
  return get<BedVO[]>('/inpatient/bed/available', wardId ? { wardId } : {})
}

export function pageBeds(params: BedQuery) {
  return get<PageResult<BedVO>>('/inpatient/bed/page', params)
}

/* ---------------- 入院登记 ---------------- */

export function pageAdmissions(params: AdmissionQuery) {
  return get<PageResult<AdmissionVO>>('/inpatient/admission/page', params)
}

export function getAdmission(id: string) {
  return get<AdmissionVO>(`/inpatient/admission/${id}`)
}

export function admit(data: AdmissionRequest) {
  return post<string>('/inpatient/admission', data)
}

export function assignBed(id: string, bedId: string) {
  return put<void>(`/inpatient/admission/${id}/bed?bedId=${bedId}`)
}

export function discharge(id: string) {
  return put<void>(`/inpatient/admission/${id}/discharge`)
}

export function addDeposit(id: string, amount: number) {
  return post<void>(`/inpatient/admission/${id}/deposit`, { amount })
}

/* ---------------- 医嘱 ---------------- */

export function listOrders(admissionId: string) {
  return get<OrderVO[]>('/inpatient/order/list', { admissionId })
}

export function createOrder(data: OrderRequest) {
  return post<string>('/inpatient/order', data)
}

export function executeOrder(id: string) {
  return put<void>(`/inpatient/order/${id}/execute`)
}

export function stopOrder(id: string) {
  return put<void>(`/inpatient/order/${id}/stop`)
}

/* ---------------- 护理记录 ---------------- */

export function addNursing(data: NursingRequest) {
  return post<string>('/inpatient/nursing', data)
}

export function pageNursing(params: NursingQuery) {
  return get<PageResult<NursingVO>>('/inpatient/nursing/page', params)
}

/* ---------------- 转科 ---------------- */

export function transfer(id: string, deptId?: string, doctorId?: string) {
  return post<void>(`/inpatient/admission/${id}/transfer`, { deptId, doctorId })
}
