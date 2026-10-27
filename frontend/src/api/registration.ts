import { get, post, put } from './request'
import type { PageResult } from './types'

/**
 * 挂号与预约。
 *
 * <p><b>挂号只传患者 ID 与排班 ID</b>：医生、科室、号别、挂号费全部由排班带出。
 * 前端传的价格后端一律忽略 —— 否则改一下请求体就能一元挂号。</p>
 */

export interface ScheduleVO {
  id: string
  doctorId: number
  doctorName: string
  deptId: number
  scheduleDate: string
  /** 1 上午 / 2 下午 / 3 晚上 */
  timePeriod: number
  /** 1 普通 / 2 专家 / 3 急诊 */
  regType: number
  totalNum: number
  remainNum: number
  regFee: number
  /** 0 停诊 / 1 正常 */
  status: number
}

export interface ScheduleQuery {
  pageNum?: number
  pageSize?: number
  doctorId?: string
  deptId?: string
  timePeriod?: number
  regType?: number
  status?: number
  onlyAvailable?: boolean
  startTime?: string
  endTime?: string
}

export interface ScheduleSaveRequest {
  doctorId: string
  deptId: string
  scheduleDate: string
  timePeriod: number
  regType: number
  totalNum: number
  regFee: number
}

export interface RegistrationVO {
  id: string
  regNo: string
  patientId: number
  patientName: string
  patientNo: string
  doctorId: number
  doctorName: string
  deptId: number
  scheduleId: number
  /** 1 普通 / 2 专家 / 3 急诊 */
  regType: number
  regFee: number
  queueNo: number
  /** 0 待就诊 / 1 就诊中 / 2 已完成 / 3 已退号 */
  status: number
  regTime: string
}

export interface RegistrationQuery {
  pageNum?: number
  pageSize?: number
  patientId?: string
  doctorId?: string
  deptId?: string
  scheduleId?: string
  status?: number
  startTime?: string
  endTime?: string
}

export interface RegistrationResult {
  id: string
  regNo: string
  queueNo: number
  regFee: number
  status: number
}

/* ---------------- 排班 ---------------- */

export function pageSchedules(params: ScheduleQuery) {
  return get<PageResult<ScheduleVO>>('/registration/schedule', params)
}

export function createSchedule(data: ScheduleSaveRequest) {
  return post<string>('/registration/schedule', data)
}

export function stopSchedule(id: string) {
  return put<void>(`/registration/schedule/${id}/stop`)
}

/* ---------------- 挂号 ---------------- */

export function register(data: { patientId: string; scheduleId: string; regType?: number }) {
  return post<RegistrationResult>('/registration', data)
}

export function cancelRegistration(id: string) {
  return put<void>(`/registration/${id}/cancel`)
}

export function pageRegistrations(params: RegistrationQuery) {
  return get<PageResult<RegistrationVO>>('/registration/page', params)
}

/* ---------------- 候诊队列 ---------------- */

export function queueOfDept(deptId: string) {
  return get<RegistrationVO[]>(`/registration/queue/${deptId}`)
}

export function callPatient(id: string) {
  return post<void>(`/registration/call/${id}`)
}
