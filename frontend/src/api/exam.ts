import { get, post, put } from './request'
import type { PageResult } from './types'

/**
 * 检查检验管理（模块七）。
 *
 * <p>三个岗位在这里是分开的，前端也要跟着分开：
 * 医生开单、技师采样录结果、审核医师发布。
 * 按钮权限用 <code>v-perm</code> 各自判，
 * 不要因为「技师也要看到申请单」就把发布按钮一并放出来。</p>
 */

/** 1 检验 / 2 检查（影像） */
export const EXAM_TYPE_LABEL: Record<number, string> = { 1: '检验', 2: '检查' }

/** 0 待缴费 / 1 待采样 / 2 检验中 / 3 已出报告 */
export const APPLY_STATUS_LABEL: Record<number, string> = {
  0: '待缴费',
  1: '待采样',
  2: '检验中',
  3: '已出报告',
}

/** 报告状态：0 草稿 / 1 已发布 */
export const REPORT_STATUS_LABEL: Record<number, string> = { 0: '草稿', 1: '已发布' }

/** 异常标记：0 正常 / 1 偏高 / 2 偏低 / 3 定性异常 */
export const ABNORMAL_LABEL: Record<number, string> = { 0: '正常', 1: '偏高', 2: '偏低', 3: '异常' }

export interface ExamItemVO {
  id: string
  itemCode: string
  itemName: string
  resultValue?: string
  refRange?: string
  unit?: string
  abnormalFlag?: number
  sort?: number
}

export interface ExamApplyVO {
  id: string
  applyNo: string
  patientId: number
  patientName: string
  visitId: number
  doctorId: number
  doctorName: string
  /** 1 检验 / 2 检查 */
  examType: number
  examItem: string
  bodyPart?: string
  clinicalDiagnosis?: string
  /** 0 待缴费 / 1 待采样 / 2 检验中 / 3 已出报告 */
  status: number
  /** 采样后才有 */
  barcodeNo?: string
  applyTime: string
  items: ExamItemVO[]
  /** 未出报告为 null */
  reportId?: string | null
  reportStatus?: number | null
  isCritical?: number | null
}

export interface ExamApplyQuery {
  pageNum?: number
  pageSize?: number
  patientId?: number
  visitId?: number
  doctorId?: number
  examType?: number
  status?: number
  startTime?: string
  endTime?: string
  criticalOnly?: boolean
}

export interface ExamItemRequest {
  itemCode: string
  itemName: string
  sort?: number
}

export interface ExamApplyRequest {
  visitId: number
  /** 1 检验 / 2 检查 */
  examType: number
  examItem?: string
  bodyPart?: string
  clinicalDiagnosis?: string
  items: ExamItemRequest[]
}

export interface ExamItemResultRequest {
  itemId: number
  resultValue?: string
  refRange?: string
  unit?: string
  abnormalFlag?: number
}

export interface ExamResultItemRequest {
  itemId: number
  resultValue?: string
  refRange?: string
  unit?: string
  abnormalFlag?: number
}

export interface ExamResultRequest {
  applyId: number
  result?: string
  conclusion?: string
  /** 0 否 / 1 是 */
  isCritical?: number
  items: ExamResultItemRequest[]
}

export interface ExamReportVO {
  id: string
  applyId: string
  applyNo: string
  reportNo: string
  patientId: number
  patientName: string
  result?: string
  conclusion?: string
  isCritical: number
  reporterId: number
  reporterName: string
  auditorId?: number
  auditorName?: string
  reportTime: string
  /** 0 草稿 / 1 已发布 */
  status: number
  items: ExamItemVO[]
  /** 0 未处理 / 1 已处理 */
  handleStatus?: number
  handlerName?: string
  handleTime?: string
  handleRemark?: string
}

export interface ExamBarcodeVO {
  applyId: string
  applyNo: string
  barcodeNo: string
  patientId: string
  patientName: string
  examType: number
  examItem: string
  bodyPart?: string
  itemNames: string[]
}

export interface ExamReportQuery {
  pageNum?: number
  pageSize?: number
  reportNo?: string
  patientName?: string
  /** 0 草稿 / 1 已发布 */
  status?: number
  isCritical?: number
  /** 只看已发布且未处理的危急值 */
  pendingCritical?: boolean
  startTime?: string
  endTime?: string
}

export interface CriticalHandleRequest {
  reportId: number
  remark: string
}

export interface SampleRequest {
  applyId: number
}

/* ---------------- 申请单 ---------------- */

/** 开检查单：需要 exam:apply:add */
export function createExamApply(data: ExamApplyRequest) {
  return post<string>('/exam/apply', data)
}

export function pageExamApplies(params: ExamApplyQuery) {
  return get<PageResult<ExamApplyVO>>('/exam/apply/page', params)
}

export function getExamApply(id: string) {
  return get<ExamApplyVO>(`/exam/apply/${id}`)
}

/* ---------------- 采样 / 条码 ---------------- */

/** 样本采集：1 待采样 → 2 检验中，并分配条码号。需要 exam:report:entry */
export function sampleExam(data: SampleRequest) {
  return post<string>('/exam/sample', data)
}

/** 条码信息（首次调用生成，重复调用返回同一个号） */
export function examBarcode(id: string) {
  return post<ExamBarcodeVO>(`/exam/sample/${id}/barcode`)
}

/* ---------------- 项目结果 ---------------- */

export function listExamItems(applyId: string) {
  return get<ExamItemVO[]>(`/exam/item/${applyId}`)
}

/** 批量保存项目结果（只填数值，不动报告主表）。需要 exam:report:entry */
export function saveExamItems(applyId: string, items: ExamItemResultRequest[]) {
  return put<void>(`/exam/item/${applyId}`, items)
}

/* ---------------- 报告 ---------------- */

/** 录入或覆盖草稿报告。需要 exam:report:entry */
export function saveExamReport(data: ExamResultRequest) {
  return post<string>('/exam/report', data)
}

export function getExamReportByApply(applyId: string) {
  return get<ExamReportVO | null>(`/exam/report/by-apply/${applyId}`)
}

export function getExamReport(id: string) {
  return get<ExamReportVO>(`/exam/report/${id}`)
}

/** 报告分页查询 */
export function pageExamReports(params: ExamReportQuery) {
  return get<PageResult<ExamReportVO>>('/exam/report/page', params)
}

/** 审核发布。需要 exam:report:audit */
export function auditExamReport(id: string) {
  return put<void>(`/exam/report/${id}/audit`)
}

/** 发布（与审核同一个动作，语义更清晰）。需要 exam:report:audit */
export function publishExamReport(id: string) {
  return put<void>(`/exam/report/${id}/publish`)
}

/** 危急值待处理列表。需要 exam:critical:handle */
export function pageCriticalReports(params: ExamApplyQuery) {
  return get<PageResult<ExamReportVO>>('/exam/report/critical', params)
}

/** 危急值处理登记。需要 exam:critical:handle */
export function handleCritical(data: CriticalHandleRequest) {
  return post<void>('/exam/critical/handle', data)
}
