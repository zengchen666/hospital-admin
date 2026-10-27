import axios from 'axios'
import { ElMessage } from 'element-plus'
import { del, get, post, put } from './request'
import { useUserStore } from '@/stores/user'
import type { PageResult } from './types'

/**
 * 系统管理与数据安全（模块一剩余 + 模块十二）。
 *
 * <p>两类接口在这里共存，但权限逻辑完全不同：</p>
 * <ul>
 *   <li><b>系统管理</b>只做查询。角色与菜单是预置数据，改动走 SQL 补丁 ——
 *       界面上的一次点击没有 review 记录，三个月后没人能回答
 *       「为什么收费员现在能看全院收入」；</li>
 *   <li><b>数据安全</b>（备份、安全配置）会真实改变系统行为，
 *       因此每一项都单独授权，且都要留审计。</li>
 * </ul>
 */

/* ---------------- 角色 ---------------- */

export interface RoleVO {
  id: string
  roleName: string
  roleCode: string
  remark?: string
  /** 0 停用 / 1 启用 */
  status: number
  permCodes?: string[]
  permCount?: number
  userCount: number
  createTime?: string
}

export interface PermissionVO {
  id: string
  permCode: string
  permName: string
  /** 1 菜单 / 2 按钮 / 3 接口 */
  permType: number
  dataScope?: number
  /** 权限总表接口里复用为「该角色是否持有」：1 持有 / 0 未持有 */
  status: number
  module?: string
}

export interface MenuNode {
  id: string
  parentId: string
  menuName: string
  /** 1 目录 / 2 菜单 / 3 按钮 */
  menuType: number
  path?: string
  component?: string
  perms?: string
  icon?: string
  sort?: number
  children?: MenuNode[]
}

export function listRoles() {
  return get<RoleVO[]>('/system/role/page')
}

export function getRole(id: string) {
  return get<RoleVO>(`/system/role/${id}`)
}

export function listRolePermissions(id: string) {
  return get<PermissionVO[]>(`/system/role/${id}/permissions`)
}

/** 权限总表 + 该角色是否持有（status 复用为持有标记） */
export function rolePermissionMatrix(id: string) {
  return get<PermissionVO[]>(`/system/role/${id}/permission-matrix`)
}

export function listPermissions() {
  return get<PermissionVO[]>('/system/role/permissions')
}

/* ---------------- 菜单 ---------------- */

export function allMenus() {
  return get<MenuNode[]>('/system/menu/all')
}

/* ---------------- 数据备份 ---------------- */

export interface BackupVO {
  id: string
  backupName: string
  backupSize?: number
  /** 1 全量 / 2 增量 / 3 手动 */
  backupType: number
  /** 0 失败 / 1 成功 */
  status: number
  remark?: string
  sizeText?: string
  createTime?: string
}

export function listBackups() {
  return get<BackupVO[]>('/security/backup/list')
}

export function runBackup() {
  return post<string>('/security/backup/run')
}

export function deleteBackup(id: string) {
  return del<void>(`/security/backup/${id}`)
}

/* ---------------- 系统参数 ---------------- */

export interface ConfigVO {
  id: string
  configKey: string
  configValue?: string
  configName: string
  /** 1 系统内置（不可删除） / 2 业务参数 */
  configType: number
  remark?: string
  updateTime?: string
}

export interface ConfigPayload {
  configKey: string
  configName: string
  configValue?: string
  configType: number
  remark?: string
}

export function listConfigs(params?: {
  configName?: string
  configKey?: string
  configType?: number
}) {
  return get<ConfigVO[]>('/system/config/list', params)
}

export function getConfigValue(configKey: string) {
  return get<string | null>(`/system/config/value/${configKey}`)
}

export function createConfig(body: ConfigPayload) {
  return post<string>('/system/config', body)
}

export function updateConfig(id: string, body: ConfigPayload) {
  return put<void>(`/system/config/${id}`, body)
}

export function deleteConfig(id: string) {
  return del<void>(`/system/config/${id}`)
}

/* ---------------- 数据字典 ---------------- */

export interface DictItemVO {
  id: string
  dictType: string
  itemLabel: string
  itemValue: string
  sort?: number
  cssClass?: string
  /** 0 停用 / 1 启用 */
  status: number
}

export interface DictVO {
  id: string
  dictType: string
  dictName: string
  remark?: string
  /** 0 停用 / 1 启用 */
  status: number
  items?: DictItemVO[]
  createTime?: string
}

export interface DictPayload {
  dictType: string
  dictName: string
  remark?: string
  status: number
}

export interface DictItemPayload {
  itemLabel: string
  itemValue: string
  sort?: number
  cssClass?: string
  status: number
}

export function listDicts(params?: { dictName?: string; dictType?: string; status?: number }) {
  return get<DictVO[]>('/system/dict/list', params)
}

export function getDict(id: string) {
  return get<DictVO>(`/system/dict/${id}`)
}

export function listDictItems(dictType: string, onlyEnabled = true) {
  return get<DictItemVO[]>(`/system/dict/items/${dictType}`, { onlyEnabled })
}

export function createDict(body: DictPayload) {
  return post<string>('/system/dict', body)
}

export function updateDict(id: string, body: DictPayload) {
  return put<void>(`/system/dict/${id}`, body)
}

export function deleteDict(id: string) {
  return del<void>(`/system/dict/${id}`)
}

export function createDictItem(dictId: string, body: DictItemPayload) {
  return post<string>(`/system/dict/${dictId}/item`, body)
}

export function updateDictItem(dictId: string, itemId: string, body: DictItemPayload) {
  return put<void>(`/system/dict/${dictId}/item/${itemId}`, body)
}

export function deleteDictItem(dictId: string, itemId: string) {
  return del<void>(`/system/dict/${dictId}/item/${itemId}`)
}

/* ---------------- 操作日志 ---------------- */

export interface OperationLogVO {
  id: string
  userId?: string
  username?: string
  module?: string
  operation?: string
  method?: string
  requestUri?: string
  params?: string
  ip?: string
  userAgent?: string
  /** 0 失败 / 1 成功 */
  status: number
  errorMsg?: string
  costTime?: number
  createTime?: string
}

export interface OperationLogQuery {
  pageNum: number
  pageSize: number
  username?: string
  module?: string
  operation?: string
  requestUri?: string
  status?: number
  startTime?: string
  endTime?: string
}

export function pageOperationLogs(params: OperationLogQuery) {
  return get<PageResult<OperationLogVO>>('/system/oplog/page', params)
}

/**
 * 导出审计日志（二进制流）。
 *
 * <p>与报表导出同一套路：后端鉴权只读 {@code Authorization} 头，
 * 因此不能简单 {@code window.location.href = url}（那样带不上头，直接 401），
 * 必须走 axios 拿 Blob 再触发下载。</p>
 */
export async function exportOperationLogs(params: Record<string, unknown> = {}): Promise<void> {
  const store = useUserStore()
  const response = await axios.get('/api/system/oplog/export', {
    params,
    headers: store.token ? { Authorization: `Bearer ${store.token}` } : {},
    responseType: 'blob',
  })

  const contentType = String(response.headers['content-type'] || '')
  if (contentType.includes('application/json')) {
    const text = await (response.data as Blob).text()
    try {
      const body = JSON.parse(text) as { msg?: string }
      ElMessage.error(body.msg || '导出失败')
    } catch {
      ElMessage.error('导出失败')
    }
    return
  }

  const url = URL.createObjectURL(response.data as Blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `操作日志_${new Date().toISOString().slice(0, 10)}.xlsx`
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  setTimeout(() => URL.revokeObjectURL(url), 3000)
}

/** 权限码前缀 → 模块中文名。与后端按前缀切分的逻辑保持一致 */
export const MODULE_LABEL: Record<string, string> = {
  system: '系统管理',
  security: '数据安全',
  patient: '患者管理',
  registration: '挂号预约',
  clinic: '门诊管理',
  inpatient: '住院管理',
  pharmacy: '药房管理',
  exam: '检查检验',
  finance: '收费财务',
  health: '公共卫生',
  report: '统计报表',
  message: '消息中心',
}
