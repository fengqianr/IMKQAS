/**
 * 前端通用日期格式化工具
 * 统一各页面的日期显示格式，避免时区漂移
 */
import dayjs from 'dayjs'

/**
 * 仅取日期部分（YYYY-MM-DD）
 * 刻意保留 slice(0,10) 语义而非 dayjs(v).format('YYYY-MM-DD')：
 * 对带时区的 ISO 串，dayjs 会转换本地时间导致日期偏移，slice 版本更稳定
 */
export const formatDate = (v?: string | null, fb = '—'): string => (v ? v.slice(0, 10) : fb)

/** 日期时间（YYYY-MM-DD HH:mm） */
export const formatDateTime = (v?: string | null, fb = '—'): string => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : fb)

/** 仅时间（HH:mm） */
export const formatTime = (v?: string | null, fb = '—'): string => (v ? dayjs(v).format('HH:mm') : fb)
