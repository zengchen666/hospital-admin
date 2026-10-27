import { get, post, put } from './request'
import type { PageResult } from './types'

/**
 * 公共卫生与健康管理（模块九）。
 *
 * <p>四个子模块各自独立，但都绕不开两件事：
 * <b>慢病 / 接种 / 传染病 都要能按患者聚合看</b>，
 * <b>传染病上报有法定时限（24 小时）</b>。后者是硬性约束，
 * 超时的卡片必须在列表里一眼看出来，所以后端直接算好 overdueHours 返回。</p>
 */

/** 传染病报告状态：0 待上报 / 1 已上报 */
export const INFECTIOUS_STATUS_LABEL: Record<number, string> = { 0: '待上报', 1: '已上报' }

/** 体检状态：0 待检 / 1 已检待出报告 / 2 已出报告 */
export const CHECKUP_STATUS_LABEL: Record<number, string> = {
  0: '待检',
  1: '已检待出报告',
  2: '已出报告',
}

/* ---------------- 健康档案 ---------------- */

export interface HealthRecordVO {
  id: string
  patientId: number
  patientName: string
  gender: number
  age: number
  recordNo: string
  /** 慢病，逗号分隔 */
  chronicDisease?: string
  /** 0 否 / 1 是 */
  smoking?: number
  drinking?: number
  exercise?: string
  createTime: string
}

export interface HealthRecordQuery {
  pageNum?: number
  pageSize?: number
  patientId?: number
  /** 慢病关键字 */
  chronicDisease?: string
  smoking?: number
}

export interface HealthRecordRequest {
  patientId: number
  /** 慢病，逗号分隔 */
  chronicDisease?: string
  smoking?: number
  drinking?: number
  exercise?: string
}

export function pageHealthRecords(params: HealthRecordQuery) {
  return get<PageResult<HealthRecordVO>>('/health/record/page', params)
}

export function getHealthRecord(id: string) {
  return get<HealthRecordVO>(`/health/record/${id}`)
}

export function createHealthRecord(data: HealthRecordRequest) {
  return post<string>('/health/record', data)
}

export function updateHealthRecord(id: string, data: HealthRecordRequest) {
  return put<void>(`/health/record/${id}`, data)
}

/* ---------------- 疫苗接种 ---------------- */

export interface VaccinationVO {
  id: string
  patientId: number
  patientName: string
  vaccineName: string
  batchNo?: string
  doseNo?: number
  vaccinateDate: string
  operatorId: number
  operatorName: string
  nextDate?: string
  /** 下次接种是否已逾期 */
  overdue?: boolean
}

export interface VaccinationQuery {
  pageNum?: number
  pageSize?: number
  patientId?: number
  vaccineName?: string
  batchNo?: string
}

export interface VaccinationRequest {
  patientId: number
  vaccineName: string
  /** 疫苗批号，必须存在于库存 */
  batchNo?: string
  /** 剂次，为空则按历史推算 */
  doseNo?: number
  vaccinateDate: string
  nextDate?: string
}

export function pageVaccinations(params: VaccinationQuery) {
  return get<PageResult<VaccinationVO>>('/health/vaccination/page', params)
}

export function addVaccination(data: VaccinationRequest) {
  return post<string>('/health/vaccination', data)
}

/** 接种提醒：未来 N 天内到期或已逾期 */
export function vaccinationRemind(days?: number) {
  return get<VaccinationVO[]>('/health/vaccination/remind', days ? { days } : undefined)
}

/* ---------------- 传染病上报 ---------------- */

export interface InfectiousVO {
  id: string
  patientId: number
  patientName: string
  diseaseName: string
  icdCode?: string
  reportDate: string
  reporterId: number
  reporterName: string
  /** 0 待上报 / 1 已上报 */
  reportStatus: number
  cdcResponse?: string
  createTime: string
  /** 待上报且已超 24 小时的小时数；未超时为 0 */
  overdueHours?: number
}

export interface InfectiousQuery {
  pageNum?: number
  pageSize?: number
  patientId?: number
  diseaseName?: string
  reportStatus?: number
}

export interface InfectiousRequest {
  patientId: number
  diseaseName: string
  icdCode?: string
  reportDate: string
}

export function pageInfectious(params: InfectiousQuery) {
  return get<PageResult<InfectiousVO>>('/health/infectious/page', params)
}

export function getInfectious(id: string) {
  return get<InfectiousVO>(`/health/infectious/${id}`)
}

/** 填卡：先落一条待上报的卡片 */
export function createInfectious(data: InfectiousRequest) {
  return post<string>('/health/infectious', data)
}

/** 上报疾控 */
export function reportInfectious(id: string) {
  return put<void>(`/health/infectious/${id}/report`)
}

/** 登记疾控反馈（只追加，不改病名与日期） */
export function feedbackInfectious(id: string, response: string) {
  return put<void>(`/health/infectious/${id}/feedback?response=${encodeURIComponent(response)}`)
}

/* ---------------- 体检管理 ---------------- */

export interface CheckupVO {
  id: string
  checkupNo: string
  patientId: number
  patientName: string
  gender: number
  age: number
  packageId?: number
  packageName?: string
  amount: number
  checkupDate: string
  summary?: string
  abnormalItems?: string
  /** 0 待检 / 1 已检待出报告 / 2 已出报告 */
  status: number
}

export interface CheckupQuery {
  pageNum?: number
  pageSize?: number
  patientId?: number
  packageId?: number
  status?: number
}

export interface CheckupRequest {
  patientId: number
  /** 套餐 ID，散查（自选单项）时为空 */
  packageId?: number
  checkupDate: string
}

export interface CheckupReportRequest {
  summary: string
  /** 异常项，逗号分隔 */
  abnormalItems?: string
  reportFileId?: number
}

export interface CheckupPackageVO {
  id: string
  packageName: string
  itemIds?: string
  itemNames?: string[]
  price: number
  /** 0 停用 / 1 启用 */
  status: number
}

export interface CheckupPackageRequest {
  packageName: string
  /** 诊疗项目 ID 列表，逗号分隔 */
  itemIds?: string
  price: number
  status?: number
}

export function pageCheckups(params: CheckupQuery) {
  return get<PageResult<CheckupVO>>('/health/checkup/page', params)
}

export function getCheckup(id: string) {
  return get<CheckupVO>(`/health/checkup/${id}`)
}

export function createCheckup(data: CheckupRequest) {
  return post<string>('/health/checkup', data)
}

/** 体检完成：0 待检 → 1 已检待出报告 */
export function completeCheckup(id: string) {
  return put<void>(`/health/checkup/${id}/complete`)
}

/** 出报告：1 → 2 */
export function publishCheckupReport(id: string, data: CheckupReportRequest) {
  return put<void>(`/health/checkup/${id}/report`, data)
}

export function listCheckupPackages(onlyEnabled?: boolean, keyword?: string) {
  return get<CheckupPackageVO[]>('/health/package/list', {
    onlyEnabled,
    keyword: keyword || undefined,
  })
}

export function getCheckupPackage(id: string) {
  return get<CheckupPackageVO>(`/health/package/${id}`)
}

export function createCheckupPackage(data: CheckupPackageRequest) {
  return post<string>('/health/package', data)
}

export function updateCheckupPackage(id: string, data: CheckupPackageRequest) {
  return put<void>(`/health/package/${id}`, data)
}

export function changeCheckupPackageStatus(id: string, status: number) {
  return put<void>(`/health/package/${id}/status?status=${status}`)
}
