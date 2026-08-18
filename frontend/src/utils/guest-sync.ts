/**
 * 游客会话静默同步模块
 *
 * 游客（未登录）的问答会话保存在浏览器 localStorage（见 guest-session.ts），
 * 登录成功后由前端自动上传到当前账号：逐个创建账号会话并回放消息。
 * 全程无需用户操作，完成后由调用方给出轻提示。
 */

import { conversationService } from '@/api/services/conversation.service'
import { useAuthStore } from '@/stores/auth.store'
import { listGuestSessions, removeGuestSession, clearGuestSessions } from './guest-session'

/** 同步结果统计 */
export interface GuestSyncResult {
  /** 本地游客会话总数 */
  total: number
  /** 成功同步数 */
  succeeded: number
  /** 失败数 */
  failed: number
}

/**
 * 将本地游客会话同步到当前登录账号。
 * 全部成功 → 清空本地；部分成功 → 仅移除已成功的（失败保留可重试）；全部失败 → 本地数据保留。
 * 无本地会话或未登录时直接返回空结果，不抛错。
 */
export async function syncGuestSessionsToAccount(): Promise<GuestSyncResult> {
  const authStore = useAuthStore()
  const userId = authStore.userId
  if (!userId) return { total: 0, succeeded: 0, failed: 0 }

  const sessions = listGuestSessions()
  if (sessions.length === 0) return { total: 0, succeeded: 0, failed: 0 }

  let succeeded = 0
  let failed = 0
  const failedIds: string[] = []

  for (const session of sessions) {
    try {
      // 1. 创建账号下的会话（POST /conversations 直接收 Conversation 实体，带 userId 即归属）
      const created = await conversationService.createConversation({
        title: session.title,
        type: 'general',
        userId
      })
      // 2. 回放消息
      for (const msg of session.messages) {
        await conversationService.createMessage({
          conversationId: created.id,
          content: msg.content,
          role: msg.role,
          sourceReferences: msg.sourceReferences
        })
      }
      succeeded++
    } catch (error) {
      failed++
      failedIds.push(session.localId)
      console.warn('同步游客会话失败:', session.localId, error)
    }
  }

  if (failed === 0) {
    // 全部成功：清空本地
    clearGuestSessions()
  } else if (succeeded > 0) {
    // 部分成功：移除已成功的，保留失败的供下次重试
    const successIds = new Set(sessions.filter((s) => !failedIds.includes(s.localId)).map((s) => s.localId))
    successIds.forEach((id) => removeGuestSession(id))
  }
  // 全部失败：本地数据保留

  return { total: sessions.length, succeeded, failed }
}
