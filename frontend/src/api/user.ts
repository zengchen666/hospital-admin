import { get, post, put, del } from './request'
import type { PageResult, SysUserVO } from './types'

/** 科室下拉选项（后端只下发 id / parentId / deptName） */
export interface DeptOption {
  id: string
  parentId: string
  deptName: string
}

/**
 * 科室下拉。
 *
 * <p>后端判权是 {@code isAuthenticated()} 而非某个权限码 —— 科室是字典型数据，
 * 用户管理选科室、发公告选科室都要用，挂到单一权限上会让别的模块取不到。</p>
 */
export function listDepts() {
  return get<DeptOption[]>('/system/dept/list')
}

/** 用户下拉选项（排班选医生、医嘱选执行人）；roleCode 为空时返回全部启用用户 */
export interface UserOption {
  id: string
  realName: string
  deptId: string
}

export function listUserOptions(roleCode?: string) {
  return get<UserOption[]>('/system/user/options', roleCode ? { roleCode } : undefined)
}

export interface UserQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  deptId?: string
  roleId?: string
  status?: number
}

export interface UserSaveRequest {
  id?: string
  username: string
  realName: string
  gender?: number
  phone?: string
  email?: string
  deptId?: string
  roleId?: string
  status?: number
  /** 新建时必填；编辑时留空表示不改密码 */
  password?: string
}

export function pageUsers(params: UserQuery) {
  return get<PageResult<SysUserVO>>('/system/user/page', params)
}

export function getUser(id: string) {
  return get<SysUserVO>(`/system/user/${id}`)
}

export function createUser(data: UserSaveRequest) {
  return post<string>('/system/user', data)
}

export function updateUser(id: string, data: UserSaveRequest) {
  return put<void>(`/system/user/${id}`, data)
}

export function deleteUser(id: string) {
  return del<void>(`/system/user/${id}`)
}

/** 注意：status 与 newPassword 后端都是 @RequestParam，不是 body */
export function changeUserStatus(id: string, status: number) {
  return put<void>(`/system/user/${id}/status?status=${status}`)
}

export function resetPassword(id: string, password: string) {
  return put<void>(`/system/user/${id}/password?newPassword=${encodeURIComponent(password)}`)
}
