/**
 * 游客会话本地存储模块
 *
 * 游客（未登录）的问答会话保存在浏览器 localStorage，登录后可由 GuestSyncDialog 一键同步到账号。
 * 数据结构、读写、数量上限均收敛于此，QaView 游客模式与 GuestSyncDialog 共用同一数据源。
 */

/** 本地一条问答消息（与后端 Message 对齐，role 仅问答两种） */
export interface GuestMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  /** 参考文献 JSON 字符串（与后端 Message.sourceReferences 一致） */
  sourceReferences?: string
}

/** 本地一个游客会话 */
export interface GuestSession {
  /** 本地 id（local- 前缀，避免与后端数字 id 冲突） */
  localId: string
  title: string
  createdAt: string
  messages: GuestMessage[]
}

const STORAGE_KEY = 'imkqas.guest.sessions.v1'
/** 本地会话数量上限（防止撑爆 localStorage） */
export const GUEST_SESSION_LIMIT = 50

/** 读取全部本地游客会话（容错：无数据或数据损坏时返回空数组） */
export function listGuestSessions(): GuestSession[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const data = JSON.parse(raw)
    return Array.isArray(data) ? data.filter(isGuestSession) : []
  } catch {
    return []
  }
}

function isGuestSession(value: unknown): value is GuestSession {
  if (!value || typeof value !== 'object') return false
  const obj = value as Record<string, unknown>
  return typeof obj.localId === 'string' && Array.isArray(obj.messages)
}

/** 按本地 id 读取单个会话，不存在返回 null */
export function getGuestSession(localId: string): GuestSession | null {
  return listGuestSessions().find((s) => s.localId === localId) || null
}

/** 生成本地会话 id（时间戳 + 随机段，避免与后端数字 id 冲突） */
function generateLocalId(): string {
  return `local-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

/**
 * 新建本地会话；超过上限返回 null（由调用方提示用户）
 */
export function createGuestSession(title: string): GuestSession | null {
  const sessions = listGuestSessions()
  if (sessions.length >= GUEST_SESSION_LIMIT) return null
  const session: GuestSession = {
    localId: generateLocalId(),
    title,
    createdAt: new Date().toISOString(),
    messages: []
  }
  sessions.push(session)
  writeSessions(sessions)
  return session
}

/** 保存（新增或覆盖）一个本地会话 */
export function saveGuestSession(session: GuestSession): void {
  const sessions = listGuestSessions()
  const index = sessions.findIndex((s) => s.localId === session.localId)
  if (index >= 0) sessions[index] = session
  else sessions.push(session)
  writeSessions(sessions)
}

/** 删除本地会话 */
export function removeGuestSession(localId: string): void {
  writeSessions(listGuestSessions().filter((s) => s.localId !== localId))
}

/** 本地会话数量 */
export function countGuestSessions(): number {
  return listGuestSessions().length
}

/** 是否存在本地游客会话（登录后用于决定是否提示同步） */
export function hasGuestSessions(): boolean {
  return listGuestSessions().length > 0
}

/** 清空全部本地游客会话（同步成功后调用） */
export function clearGuestSessions(): void {
  localStorage.removeItem(STORAGE_KEY)
}

function writeSessions(sessions: GuestSession[]): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(sessions))
}
