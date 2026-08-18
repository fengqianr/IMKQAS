/**
 * 认证相关操作组合式函数
 * 统一 QaView / MainLayout 两处逐字复用的退出登录流程
 */
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth.store'
import { ElMessage, ElMessageBox } from 'element-plus'

export function useAuthActions() {
  const router = useRouter()
  const auth = useAuthStore()

  /**
   * 退出登录：二次确认 → 清空本地令牌 → 提示 → 回登录页
   * 与原有逐字实现保持语义一致（含取消时静默）
   * @param opts.redirectTo 退出后跳转地址（默认 /login）
   * @param opts.successMsg  成功提示文案（默认 已退出登录）
   */
  const logoutWithConfirm = async (opts?: { redirectTo?: string; successMsg?: string }): Promise<void> => {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '退出确认', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
      await auth.logout()
      ElMessage.success(opts?.successMsg ?? '已退出登录')
      router.push(opts?.redirectTo ?? '/login')
    } catch {
      // 用户取消退出或退出失败，保持静默（与原实现一致）
    }
  }

  return { logoutWithConfirm }
}
