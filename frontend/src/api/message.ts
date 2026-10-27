import { get, post, put, del } from './request'
import type { MessageVO, NoticeVO, PageResult, SmsVO, WarningVO } from './types'

/**
 * 消息与通知。
 *
 * <p>四个根路径与后端 Controller 一一对应（/message /notice /sms /warning）。</p>
 *
 * <p><b>站内消息的查询一律不带 userId</b>：接收人由后端从登录态取。
 * 这不是偷懒 —— 一旦接口允许传 userId，改个参数就能读别人的通知。</p>
 */

export interface MessageQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  /** 1 系统 / 2 待办 / 3 预警 */
  msgType?: number
  /** 0 未读 / 1 已读 */
  isRead?: number
}

export interface NoticeQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  noticeType?: number
  status?: number
}

export interface NoticeSaveRequest {
  title: string
  content: string
  /** 1 院内 / 2 科室 */
  noticeType: number
  /** 科室公告必填 */
  deptId?: string
}

export interface SmsSendRequest {
  phone: string
  templateCode: string
  params?: string
}

/* ---------------- 站内消息 ---------------- */

export function pageMessages(params: MessageQuery) {
  return get<PageResult<MessageVO>>('/message/page', params)
}

export function unreadCount() {
  return get<number>('/message/unread-count')
}

export function markRead(id: string) {
  return put<void>(`/message/${id}/read`)
}

export function markAllRead() {
  return put<number>('/message/read-all')
}

/* ---------------- 公告 ---------------- */

export function pageNotices(params: NoticeQuery) {
  return get<PageResult<NoticeVO>>('/notice/page', params)
}

export function visibleNotices() {
  return get<NoticeVO[]>('/notice/visible')
}

export function createNotice(data: NoticeSaveRequest) {
  return post<string>('/notice', data)
}

export function updateNotice(id: string, data: NoticeSaveRequest) {
  return put<void>(`/notice/${id}`, data)
}

export function publishNotice(id: string) {
  return put<void>(`/notice/${id}/publish`)
}

export function deleteNotice(id: string) {
  return del<void>(`/notice/${id}`)
}

/* ---------------- 短信 ---------------- */

export function sendSms(data: SmsSendRequest) {
  return post<SmsVO>('/sms/send', data)
}

export function pageSms(params: { pageNum?: number; pageSize?: number; keyword?: string }) {
  return get<PageResult<SmsVO>>('/sms/page', params)
}

/* ---------------- 预警 ---------------- */

export function listWarnings(limit: number) {
  return get<WarningVO[]>('/warning/list', { limit })
}

export function countWarnings() {
  return get<number>('/warning/count')
}
