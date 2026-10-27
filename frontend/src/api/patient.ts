import { get, post, put, del } from './request'
import type { PageResult, PatientVO } from './types'

export interface PatientQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  gender?: number
  bloodType?: string
}

/** 建档/编辑入参（身份证、手机号是明文入参，后端负责 hash 与加密） */
export interface PatientSaveRequest {
  id?: string
  name: string
  gender: number
  birthDate?: string
  idCard?: string
  phone?: string
  address?: string
  bloodType?: string
  allergyHistory?: string
  medicalHistory?: string
  familyHistory?: string
  tags?: string
}

export function pagePatients(params: PatientQuery) {
  return get<PageResult<PatientVO>>('/patient/page', params)
}

export function getPatient(id: string) {
  return get<PatientVO>(`/patient/${id}`)
}

export function createPatient(data: PatientSaveRequest) {
  return post<string>('/patient', data)
}

export function updatePatient(id: string, data: PatientSaveRequest) {
  return put<void>(`/patient/${id}`, data)
}

export function deletePatient(id: string) {
  return del<void>(`/patient/${id}`)
}

/** 身份证查重（需 patient:add 权限；建档前先调，命中直接提示，比等 40904 友好） */
export function checkIdCard(idCard: string) {
  return get<boolean>('/patient/check/idcard', { idCard })
}

/** 明文查看，需 patient:decrypt 权限，每次调用都会进审计日志 */
export function getPatientPlain(id: string) {
  return get<PatientVO>(`/patient/${id}/plain`)
}
