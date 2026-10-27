import { get, post, put } from './request'
import type { PageResult } from './types'

/**
 * 收费与财务管理（模块八）。
 *
 * <p>金额一律由服务端算：收费按价目表、退费按原明细单价、发票按收费单实收、
 * 医保按目录比例。前端只传「哪张单、哪种支付方式、退哪几项」，
 * 不传「多少钱」—— 传了就等于把定价权交给了浏览器。</p>
 */

/** 1 现金 / 2 微信 / 3 支付宝 / 4 医保 / 5 银行卡 */
export const PAY_TYPE_LABEL: Record<number, string> = {
  1: '现金',
  2: '微信',
  3: '支付宝',
  4: '医保',
  5: '银行卡',
}

/** 0 待支付 / 1 已支付 / 2 已退费 / 3 部分退费 */
export const PAY_STATUS_LABEL: Record<number, string> = {
  0: '待支付',
  1: '已支付',
  2: '已退费',
  3: '部分退费',
}

/** 1 诊疗 / 2 检查 / 3 检验 / 4 床位 / 5 手术 / 6 材料 */
export const TREAT_TYPE_LABEL: Record<number, string> = {
  1: '诊疗',
  2: '检查',
  3: '检验',
  4: '床位',
  5: '手术',
  6: '材料',
}

/** 1 甲类 / 2 乙类 / 3 丙类（自费） */
export const INSURANCE_TYPE_LABEL: Record<number, string> = { 1: '甲类', 2: '乙类', 3: '丙类' }

/** 退费：0 待审核 / 1 已审核 / 2 已退款 / 3 已驳回 */
export const REFUND_STATUS_LABEL: Record<number, string> = {
  0: '待审核',
  1: '已审核',
  2: '已退款',
  3: '已驳回',
}

/* ---------------- 收费记录 ---------------- */

export interface ChargeItemVO {
  id: string
  itemType: number
  itemName: string
  quantity: number
  price: number
  amount: number
  refundNum: number
}

export interface ChargeVO {
  id: string
  chargeNo: string
  patientId: number
  patientName: string
  visitId: number
  /** 1 处方 / 2 检查 / 3 挂号 / 4 住院 */
  sourceType: number
  sourceId: string
  /** 1 挂号 / 2 门诊 / 3 住院 */
  chargeType: number
  totalAmount: number
  discountAmount: number
  actualAmount: number
  payType: number
  payStatus: number
  operatorId: number
  operatorName: string
  chargeTime: string
  items: ChargeItemVO[]
}

export interface ChargeQuery {
  pageNum?: number
  pageSize?: number
  patientId?: number
  visitId?: number
  operatorId?: number
  chargeType?: number
  payStatus?: number
  payType?: number
  sourceType?: number
  /** 收费单号，前缀匹配 */
  chargeNo?: string
  startTime?: string
  endTime?: string
}

export function pageCharges(params: ChargeQuery) {
  return get<PageResult<ChargeVO>>('/finance/charge/page', params)
}

export function getCharge(id: string) {
  return get<ChargeVO>(`/finance/charge/${id}`)
}

/* ---------------- 价目表 ---------------- */

export interface TreatItemVO {
  id: string
  itemCode: string
  itemName: string
  /** 1 诊疗 / 2 检查 / 3 检验 / 4 床位 / 5 手术 / 6 材料 */
  itemType: number
  spec?: string
  unit: string
  price: number
  /** 1 甲类 / 2 乙类 / 3 丙类 */
  insuranceType: number
  insuranceRatio: number
  deptId?: number
  deptName?: string
  /** 0 停用 / 1 启用 */
  status: number
  effectiveDate: string
  /** 同一编码下还有别的版本 */
  hasHistory?: boolean
}

export interface TreatItemQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  itemType?: number
  status?: number
  /** true 时按编码去重，只看当前生效价 */
  onlyEffective?: boolean
  deptId?: number
}

export interface TreatItemRequest {
  itemCode: string
  itemName: string
  itemType: number
  spec?: string
  unit?: string
  price: number
  insuranceType?: number
  insuranceRatio?: number
  deptId?: number
  status?: number
  /** 生效日期 yyyy-MM-dd，null 视为今天 */
  effectiveDate?: string
}

export function pageTreatItems(params: TreatItemQuery) {
  return get<PageResult<TreatItemVO>>('/finance/treat/page', params)
}

export function getTreatItem(id: string) {
  return get<TreatItemVO>(`/finance/treat/${id}`)
}

export function createTreatItem(data: TreatItemRequest) {
  return post<string>('/finance/treat', data)
}

/** 维护非价格字段 */
export function updateTreatItem(id: string, data: TreatItemRequest) {
  return put<string>(`/finance/treat/${id}`, data)
}

/** 调价：新增一版价格，历史价保留 */
export function adjustTreatPrice(id: string, data: TreatItemRequest) {
  return post<string>(`/finance/treat/${id}/price`, data)
}

export function disableTreatItem(id: string) {
  return put<void>(`/finance/treat/${id}/disable`)
}

/* ---------------- 发票 ---------------- */

export interface InvoiceVO {
  id: string
  invoiceNo: string
  chargeId: string
  chargeNo: string
  patientId: number
  patientName: string
  /** 1 电子 / 2 纸质 */
  invoiceType: number
  amount: number
  /** 0 正常 / 1 已作废 */
  status: number
  printTime: string
  operatorName?: string
  createTime: string
}

export interface InvoiceQuery {
  pageNum?: number
  pageSize?: number
  invoiceNo?: string
  chargeNo?: string
  patientId?: number
  invoiceType?: number
  status?: number
}

export interface InvoicePrintRequest {
  chargeId: number
  invoiceType: number
}

export function printInvoice(data: InvoicePrintRequest) {
  return post<string>('/finance/invoice/print', data)
}

export function voidInvoice(id: string) {
  return put<void>(`/finance/invoice/${id}/void`)
}

export function pageInvoices(params: InvoiceQuery) {
  return get<PageResult<InvoiceVO>>('/finance/invoice/page', params)
}

export function getInvoice(id: string) {
  return get<InvoiceVO>(`/finance/invoice/${id}`)
}

/* ---------------- 退费 ---------------- */

export interface RefundItemVO {
  id: string
  chargeItemId: string
  itemName: string
  quantity: number
  amount: number
}

export interface RefundVO {
  id: string
  refundNo: string
  chargeId: string
  chargeNo: string
  patientId: number
  patientName: string
  refundAmount: number
  /** 1 全额 / 2 部分 */
  refundType: number
  reason: string
  /** 0 待审核 / 1 已审核 / 2 已退款 / 3 已驳回 */
  status: number
  applicantId: number
  applicantName: string
  auditorId?: number
  auditorName?: string
  auditTime?: string
  refundTime?: string
  createTime: string
  items: RefundItemVO[]
}

export interface RefundQuery {
  pageNum?: number
  pageSize?: number
  patientId?: number
  chargeNo?: string
  status?: number
}

export interface RefundRequest {
  chargeId: number
  reason: string
  items: { chargeItemId: number; quantity: number }[]
}

export function pageRefunds(params: RefundQuery) {
  return get<PageResult<RefundVO>>('/finance/refund/page', params)
}

export function getRefund(id: string) {
  return get<RefundVO>(`/finance/refund/${id}`)
}

export function applyRefund(data: RefundRequest) {
  return post<string>('/finance/refund/apply', data)
}

export function approveRefund(id: string) {
  return put<void>(`/finance/refund/${id}/approve`)
}

/** 驳回：reason 走 query，中文要先编码 */
export function rejectRefund(id: string, reason?: string) {
  const qs = reason ? `?reason=${encodeURIComponent(reason)}` : ''
  return put<void>(`/finance/refund/${id}/reject${qs}`)
}

export function completeRefund(id: string) {
  return put<void>(`/finance/refund/${id}/complete`)
}

/* ---------------- 医保结算 ---------------- */

export interface InsuranceSettleVO {
  id: string
  settleNo: string
  chargeId: string
  chargeNo: string
  patientId: number
  patientName: string
  insuranceType: string
  totalAmount: number
  insuranceAmount: number
  selfAmount: number
  /** 0 待结算 / 1 已结算 / 2 结算失败 */
  settleStatus: number
  failReason?: string
  settleTime?: string
  createTime: string
}

export function settleInsurance(data: { chargeId: number; insuranceType: string }) {
  return post<string>('/finance/insurance/settle', data)
}

export function getInsuranceSettle(chargeId: string) {
  return get<InsuranceSettleVO>(`/finance/insurance/settle/${chargeId}`)
}

/* ---------------- 日结 / 月结 ---------------- */

export interface PayTypeAmount {
  /** 1 现金 / 2 微信 / 3 支付宝 / 4 医保 / 5 银行卡 */
  payType: number
  amount: number
  count: number
}

export interface DailySettleVO {
  settleDate: string
  chargeCount: number
  totalAmount: number
  payTypeAmounts: PayTypeAmount[]
  refundCount: number
  refundAmount: number
  /** 应有现金 = 现金收入 − 现金退费 */
  expectedCash: number
  /** 已提交时回填 */
  actualCash?: number | null
  diffAmount?: number | null
  diffRemark?: string | null
  submitted: boolean
}

export interface MonthlyDailyRow {
  day: string
  chargeAmount: number
  refundAmount: number
  chargeCount: number
}

export interface MonthlySettleVO {
  month: string
  totalAmount: number
  refundAmount: number
  chargeCount: number
  days: MonthlyDailyRow[]
}

export function dailySettle(date?: string) {
  return get<DailySettleVO>('/finance/daily', date ? { date } : undefined)
}

export function monthlySettle(month?: string) {
  return get<MonthlySettleVO>('/finance/monthly', month ? { month } : undefined)
}

export function submitDailySettle(data: {
  settleDate: string
  actualCash: number
  diffRemark?: string
}) {
  return post<void>('/finance/daily/submit', data)
}
