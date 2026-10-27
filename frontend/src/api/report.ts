import axios from 'axios'
import { ElMessage } from 'element-plus'
import { get, post, put, del } from './request'
import { useUserStore } from '@/stores/user'
import type { PageResult } from './types'

/**
 * 统计报表（模块十）。
 *
 * <p>两条必须守住的约定：</p>
 * <ol>
 *   <li><b>查询区间始终显式下发</b>。后端默认近 30 天，但前端不传就永远
 *       说不清「这张表统计的是哪一段」，导出去的文件更没法追溯；</li>
 *   <li><b>导出走独立通道</b>。导出返回的是二进制流，不能经过
 *       {@code request} 封装 —— 那层会把响应当 JSON 拆包，
 *       拿到 Blob 直接报错。</li>
 * </ol>
 */

/* ---------------- 统计结果 ---------------- */

export interface ReportQueryParams {
  startTime?: string
  endTime?: string
  deptId?: number
  doctorId?: number
  topN?: number
}

export interface OutpatientStatVO {
  deptName?: string
  regCount: number
  patientCount: number
  regFeeAmount: number
  statDate?: string
  ratio: number
}

export interface InpatientStatVO {
  inHospitalCount: number
  admittedCount: number
  dischargedCount: number
  availableBeds: number
  occupiedBeds: number
  bedUsageRate: number
  avgStayDays: number
  depositBalance: number
}

export interface IncomeStatVO {
  dimension: string
  dimensionKey: string
  dimensionName: string
  amount: number
  chargeCount: number
  quantity?: number
  visitCount?: number
  prescAmount?: number
}

export interface DrugStatVO {
  rank: number
  drugId: string
  drugName: string
  spec?: string
  quantity: number
  amount: number
}

export interface PatientStatVO {
  dimension: string
  dimensionName?: string
  displayName?: string
  patientCount: number
  statMonth?: string
}

/* ---------------- 自定义报表 ---------------- */

export interface ReportColumnMeta {
  key: string
  title: string
}

export interface ReportResultVO {
  columns: ReportColumnMeta[]
  rows: Record<string, unknown>[]
  rangeDesc: string
}

export interface ReportConfigVO {
  id: string
  reportName: string
  reportType: string
  reportTypeName?: string
  dimension?: string
  dimensionName?: string
  chartType?: string
  params?: string
  configJson?: string
  creatorName?: string
  /** true 表示系统内置，不可删除 */
  builtin: boolean
  createTime?: string
}

export interface ReportConfigPayload {
  reportName: string
  reportType: string
  dimension: string
  chartType?: string
  params?: string
  configJson?: string
}

/** 数据集编码 → 中文名 */
export const REPORT_TYPE_LABEL: Record<string, string> = {
  outpatient: '门诊统计',
  inpatient: '住院统计',
  income: '收入统计',
  drug: '药品统计',
  patient: '患者分析',
}

/** 数据集 → 可用维度。与后端 ReportDimension 的矩阵保持一致 */
export const REPORT_DIMENSIONS: Record<string, { code: string; name: string }[]> = {
  outpatient: [
    { code: 'dept', name: '科室' },
    { code: 'date', name: '日期' },
  ],
  inpatient: [{ code: 'summary', name: '全院汇总' }],
  income: [
    { code: 'itemType', name: '项目类型' },
    { code: 'dept', name: '科室' },
    { code: 'doctor', name: '医生' },
  ],
  drug: [{ code: 'drug', name: '药品' }],
  patient: [
    { code: 'age', name: '年龄段' },
    { code: 'gender', name: '性别' },
    { code: 'disease', name: '病种' },
    { code: 'new', name: '建档趋势' },
  ],
}

/* ---------------- 内置统计接口 ---------------- */

export function outpatientByDept(params?: ReportQueryParams) {
  return get<OutpatientStatVO[]>('/report/outpatient', params)
}

export function outpatientTrend(params?: ReportQueryParams) {
  return get<OutpatientStatVO[]>('/report/outpatient/trend', params)
}

export function inpatientSummary(params?: ReportQueryParams) {
  return get<InpatientStatVO>('/report/inpatient', params)
}

export function incomeStat(dimension: string, params?: ReportQueryParams) {
  return get<IncomeStatVO[]>('/report/income', { dimension, ...params })
}

export function drugRanking(params?: ReportQueryParams) {
  return get<DrugStatVO[]>('/report/drug', params)
}

export function patientStat(dimension: string, params?: ReportQueryParams) {
  return get<PatientStatVO[]>('/report/patient', { dimension, ...params })
}

/* ---------------- 快照 ---------------- */

export function generateSnapshot(type: string, date?: string) {
  return post<string>(`/report/${type}/snapshot`, undefined, { params: { date } })
}

export function getSnapshot(type: string, date: string) {
  return get<string | null>(`/report/${type}/snapshot`, { date })
}

/* ---------------- 自定义报表配置 ---------------- */

export function pageReportConfigs(params: { pageNum: number; pageSize: number; reportType?: string }) {
  return get<PageResult<ReportConfigVO>>('/report/config/page', params)
}

export function listReportConfigs(reportType?: string) {
  return get<ReportConfigVO[]>('/report/config/list', { reportType })
}

export function getReportConfig(id: string) {
  return get<ReportConfigVO>(`/report/config/${id}`)
}

export function createReportConfig(body: ReportConfigPayload) {
  return post<string>('/report/config', body)
}

export function updateReportConfig(id: string, body: ReportConfigPayload) {
  return put<void>(`/report/config/${id}`, body)
}

export function deleteReportConfig(id: string) {
  return del<void>(`/report/config/${id}`)
}

export function previewReport(reportType: string, dimension: string, params?: ReportQueryParams) {
  return post<ReportResultVO>('/report/config/preview', undefined, { params: { reportType, dimension, ...params } })
}

export function executeReport(id: string, params?: ReportQueryParams) {
  return post<ReportResultVO>(`/report/config/${id}/execute`, undefined, { params })
}

/* ---------------- 导出 ---------------- */

/**
 * 导出报表文件。
 *
 * <p>失败时延后到 Blob 里的是一段 JSON（全局异常处理器统一返回
 * {@code {code,msg}}），而不是文件。直接触发下载会得到一个「打不开的 xlsx」，
 * 用户只会认为导出有 bug。这里先把 Blob 读回文本尝试解析，
 * 解析出业务 code 就按失败提示。</p>
 */
export async function exportReport(
  type: string,
  format: 'excel' | 'pdf',
  params?: ReportQueryParams & { dimension?: string },
): Promise<void> {
  const store = useUserStore()
  const response = await axios.get(`/api/report/${type}/export`, {
    params: { format, ...params },
    headers: store.token ? { Authorization: `Bearer ${store.token}` } : {},
    responseType: 'blob',
  })

  const contentType = String(response.headers['content-type'] || '')
  if (contentType.includes('application/json')) {
    const text = await (response.data as Blob).text()
    try {
      const body = JSON.parse(text) as { code?: number; msg?: string }
      ElMessage.error(body.msg || '导出失败')
      return
    } catch {
      ElMessage.error('导出失败')
      return
    }
  }

  const disposition = String(response.headers['content-disposition'] || '')
  const fileName = parseFileName(disposition) || `${REPORT_TYPE_LABEL[type] || '报表'}.${format === 'pdf' ? 'pdf' : 'xlsx'}`

  const url = URL.createObjectURL(response.data as Blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  // 不立刻 revoke：部分浏览器会取消尚未开始的下载
  setTimeout(() => URL.revokeObjectURL(url), 3000)
}

/** 从 Content-Disposition 取文件名，优先 UTF-8 版（filename*） */
function parseFileName(disposition: string): string | null {
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
  if (utf8?.[1]) {
    try {
      return decodeURIComponent(utf8[1])
    } catch {
      return utf8[1]
    }
  }
  const ascii = /filename="([^"]+)"/i.exec(disposition)
  return ascii?.[1] ?? null
}
