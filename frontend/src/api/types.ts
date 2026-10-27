/**
 * 与后端统一响应结构对齐。
 *
 * <p><b>ID 一律用 string</b>：后端雪花 ID 是 19 位 Long，超过 JS 的
 * Number.MAX_SAFE_INTEGER，以数字传输会静默丢精度。
 * 这不是风格问题，是后端定下的硬约定。</p>
 */

export interface Result<T> {
  code: number
  msg: string
  data: T
}

export interface PageResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  userInfo: UserInfo
}

export interface UserInfo {
  userId: string
  username: string
  realName: string
  deptId: string
  roles: string[]
  permissions: string[]
}

export interface MenuVO {
  id: string
  parentId: string
  menuName: string
  /** 1 目录 / 2 菜单 / 3 按钮 */
  menuType: number
  path: string
  component: string
  perms: string
  icon: string
  sort: number
  children: MenuVO[]
}

export interface PatientVO {
  id: string
  patientNo: string
  name: string
  gender: number
  age: number | null
  birthDate: string
  idCard: string
  phone: string
  address: string
  bloodType: string
  allergyHistory: string
  medicalHistory: string
  familyHistory: string
  tags: string
  cardNo: string
  cardBalance: number
  createTime: string
}

export interface SysUserVO {
  id: string
  username: string
  realName: string
  deptId: string
  phone: string
  email: string
  roleId: string
  roleName: string
  status: number
  createTime: string
}

/* ---------------- 消息与通知 ---------------- */

/** 站内消息；列表只返回当前登录用户自己的 */
export interface MessageVO {
  id: string
  receiverId: number
  /** 1 系统 / 2 待办 / 3 预警 */
  msgType: number
  msgTypeName: string
  title: string
  content: string
  /** 业务类型，前端据此决定点击后跳哪个页面 */
  bizType: string
  bizId: string
  /** 0 未读 / 1 已读 */
  isRead: number
  createTime: string
}

export interface NoticeVO {
  id: string
  title: string
  content: string
  /** 1 院内 / 2 科室 */
  noticeType: number
  noticeTypeName: string
  deptId: string
  deptName: string
  publisherId: string
  publisherName: string
  publishTime: string
  /** 0 草稿 / 1 已发布 */
  status: number
  createTime: string
}

/** 短信记录；phone 与列表一律由后端脱敏后下发 */
export interface SmsVO {
  id: string
  phone: string
  templateCode: string
  content: string
  /** 0 失败 / 1 成功 */
  sendStatus: number
  failReason: string
  sendTime: string
}

/** 预警项；实时聚合，不落库 */
export interface WarningVO {
  /** stock_warn / drug_expire / critical / infectious_overdue / vaccine_overdue */
  bizType: string
  bizTypeName: string
  bizId: string
  title: string
  content: string
  /** 1 提示 / 2 警告 / 3 紧急 */
  level: number
  levelName: string
  value: number
  dueDate: string
}
