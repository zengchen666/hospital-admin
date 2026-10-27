import { get, post, put, del } from './request'
import type { PageResult } from './types'

/** 药品与药房管理（模块六） */

export interface DrugVO {
  id: string
  drugCode: string
  drugName: string
  genericName: string
  spec: string
  dosageForm: string
  unit: string
  manufacturer: string
  approvalNo: string
  purchasePrice: number
  salePrice: number
  category: string
  /** 0 否 / 1 麻精毒麻 */
  isSpecial: number
  /** 0 停用 / 1 启用 */
  status: number
  /** 跨库房库存合计（仅展示） */
  totalStock: number
}

export interface DrugQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  category?: string
  status?: number
  onlySpecial?: boolean
}

export interface DrugRequest {
  drugCode: string
  drugName: string
  genericName?: string
  spec?: string
  dosageForm?: string
  unit: string
  manufacturer?: string
  approvalNo?: string
  purchasePrice: number
  salePrice: number
  category?: string
  isSpecial?: number
  status?: number
}

export interface StockVO {
  id: string
  drugId: string
  drugCode: string
  drugName: string
  spec: string
  unit: string
  warehouseId: string
  warehouseName: string
  batchNo: string
  quantity: number
  warnNum: number
  produceDate: string
  expireDate: string
  warn: boolean
  expiring: boolean
  daysToExpire: number
}

export interface StockQuery {
  pageNum?: number
  pageSize?: number
  drugId?: string
  warehouseId?: string
  keyword?: string
  onlyWarn?: boolean
  expireDays?: number
}

export interface StockInRequest {
  drugId: string
  warehouseId: string
  batchNo: string
  quantity: number
  produceDate?: string
  expireDate: string
  warnNum?: number
  relatedNo?: string
  remark?: string
}

export interface WarehouseVO {
  id: string
  warehouseName: string
  warehouseCode: string
  /** 1 药库 / 2 药房 / 3 科室备药柜 */
  warehouseType: number
  deptId: string
  managerId: string
  managerName: string
  status: number
}

export interface SupplierVO {
  id: string
  supplierName: string
  supplierCode: string
  contactName: string
  contactPhone: string
  address: string
  licenseNo: string
  creditLevel: number
  status: number
}

export interface PurchaseItemVO {
  id: string
  drugId: string
  drugCode: string
  drugName: string
  spec: string
  unit: string
  quantity: number
  price: number
  amount: number
  arriveNum: number
}

export interface PurchaseVO {
  id: string
  purchaseNo: string
  supplierId: string
  supplierName: string
  warehouseId: string
  warehouseName: string
  totalAmount: number
  expectDate: string
  /** 0 草稿 / 1 待审核 / 2 已审核 / 3 已入库 / 4 已作废 */
  status: number
  applicantId: string
  applicantName: string
  auditorId: string
  auditorName: string
  auditTime: string
  remark: string
  createTime: string
  items: PurchaseItemVO[]
}

export interface PendingItemVO {
  drugId: string
  drugName: string
  spec: string
  unit: string
  quantity: number
  price: number
  amount: number
  availableStock: number
}

export interface PendingPrescVO {
  prescId: string
  prescNo: string
  patientId: string
  patientName: string
  doctorId: string
  doctorName: string
  diagnosis: string
  totalAmount: number
  prescType: number
  status: number
  createTime: string
  items: PendingItemVO[]
}

/* ---------------- 药品字典 ---------------- */

export function pageDrugs(params: DrugQuery) {
  return get<PageResult<DrugVO>>('/drug/page', params)
}

export function createDrug(data: DrugRequest) {
  return post<string>('/drug', data)
}

export function updateDrug(id: string, data: DrugRequest) {
  return put<void>(`/drug/${id}`, data)
}

export function deleteDrug(id: string) {
  return del<void>(`/drug/${id}`)
}

/* ---------------- 库存 ---------------- */

export function pageStock(params: StockQuery) {
  return get<PageResult<StockVO>>('/drug/stock/page', params)
}

export function stockIn(data: StockInRequest) {
  return post<void>('/drug/stock/in', data)
}

export function stockOut(data: {
  drugId: string
  warehouseId: string
  batchNo: string
  quantity: number
  relatedNo?: string
  remark?: string
}) {
  return post<void>('/drug/stock/out', data)
}

export function stockWarn() {
  return get<StockVO[]>('/drug/stock/warn')
}

export function stockExpire(expireDays?: number) {
  return get<StockVO[]>('/drug/stock/expire', expireDays ? { expireDays } : {})
}

/* ---------------- 库房 ---------------- */

export function listWarehouses() {
  return get<WarehouseVO[]>('/drug/warehouse/list')
}

/* ---------------- 供应商 ---------------- */

export function pageSuppliers(params: { pageNum?: number; pageSize?: number; keyword?: string; status?: number }) {
  return get<PageResult<SupplierVO>>('/drug/supplier/page', params)
}

export function createSupplier(data: Partial<SupplierVO>) {
  return post<string>('/drug/supplier', data)
}

export function updateSupplier(id: string, data: Partial<SupplierVO>) {
  return put<void>(`/drug/supplier/${id}`, data)
}

/* ---------------- 采购 ---------------- */

export function pagePurchases(params: {
  pageNum?: number
  pageSize?: number
  purchaseNo?: string
  supplierId?: string
  status?: number
}) {
  return get<PageResult<PurchaseVO>>('/drug/purchase/page', params)
}

export function getPurchase(id: string) {
  return get<PurchaseVO>(`/drug/purchase/${id}`)
}

export function createPurchase(data: {
  supplierId: string
  warehouseId: string
  expectDate?: string
  remark?: string
  items: { drugId: string; quantity: number; price: number }[]
}) {
  return post<string>('/drug/purchase', data)
}

export function auditPurchase(id: string) {
  return put<void>(`/drug/purchase/${id}/audit`)
}

export function purchaseIn(
  id: string,
  data: {
    produceDate?: string
    warnNum?: number
    remark?: string
    items: { itemId: string; arriveNum: number; batchNo: string; expireDate: string }[]
  },
) {
  return post<void>(`/drug/purchase/${id}/in`, data)
}

export function voidPurchase(id: string) {
  return put<void>(`/drug/purchase/${id}/void`)
}

/* ---------------- 待发药 / 发药 ---------------- */

export function pagePendingPresc(params: {
  prescNo?: string
  patientName?: string
  pageNum?: number
  pageSize?: number
}) {
  return get<PageResult<PendingPrescVO>>('/pharmacy/pending', params)
}

export interface DispenseResult {
  id: string
  dispenseNo: string
  /** 同一药品跨批次会被拆成多条明细 */
  itemCount: number
  totalAmount: number
  status: number
}

export function dispense(prescId: string, warehouseId: string) {
  return post<DispenseResult>('/pharmacy/dispense', { prescId, warehouseId })
}

export interface DispenseVO {
  id: string
  dispenseNo: string
  prescId: string
  patientId: string
  patientName: string
  warehouseId: string
  warehouseName: string
  /** 1 门诊发药 / 2 住院发药 / 3 退药 */
  dispenseType: number
  totalAmount: number
  operatorId: string
  operatorName: string
  dispenseTime: string
  /** 0 待发 / 1 已发 / 2 已退 */
  status: number
  items: { drugId: string; drugName: string; batchNo: string; quantity: number; price: number; amount: number }[]
}

export function pageDispense(params: {
  prescId?: string
  patientId?: string
  status?: number
  pageNum?: number
  pageSize?: number
}) {
  return get<PageResult<DispenseVO>>('/pharmacy/dispense/page', params)
}
