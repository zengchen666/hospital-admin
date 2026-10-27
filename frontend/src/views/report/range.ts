/**
 * 报表查询区间的公共定义。
 *
 * <p>五个报表页都要「日期范围 + 快捷选项」，各写一份迟早不一致
 * （有的默认 7 天、有的默认 30 天），用户换一页就以为数据变了。</p>
 *
 * <p><b>默认近 30 天且必须显式下发</b>：后端也有 30 天兜底，
 * 但前端不传的话，页面上的日期选择器就是空的 ——
 * 使用者无法确认自己看的是哪一段，导出的文件更是无从追溯。</p>
 */

export interface Shortcut {
  text: string
  value: () => [Date, Date]
}

function daysAgo(n: number): Date {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return d
}

/** 默认区间：近 30 天（含今天） */
export function defaultRange(): string[] {
  const end = new Date()
  const start = daysAgo(29)
  return [toDateStr(start), toDateStr(end)]
}

function toDateStr(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/** Element Plus 的 shortcuts 需要返回 Date 数组 */
export const SHORTCUTS: Shortcut[] = [
  { text: '今天', value: () => [new Date(), new Date()] },
  { text: '近 7 天', value: () => [daysAgo(6), new Date()] },
  { text: '近 30 天', value: () => [daysAgo(29), new Date()] },
  { text: '本月', value: () => [new Date(new Date().getFullYear(), new Date().getMonth(), 1), new Date()] },
]
