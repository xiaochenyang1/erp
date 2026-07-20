import { ElLoading, ElMessageBox } from 'element-plus'
import type { LoadingInstance } from 'element-plus/es/components/loading/src/loading'

/**
 * 全局Loading实例
 */
let loadingInstance: LoadingInstance | null = null

/**
 * 显示全局Loading
 * @param text 加载文本
 */
export function showLoading(text: string = '加载中...'): LoadingInstance {
  if (loadingInstance) {
    loadingInstance.close()
  }
  loadingInstance = ElLoading.service({
    lock: true,
    text,
    background: 'rgba(0, 0, 0, 0.7)'
  })
  return loadingInstance
}

/**
 * 隐藏全局Loading
 */
export function hideLoading(): void {
  if (loadingInstance) {
    loadingInstance.close()
    loadingInstance = null
  }
}

/**
 * 确认对话框
 * @param message 提示消息
 * @param title 标题
 * @param options 其他选项
 */
export function confirm(
  message: string,
  title: string = '提示',
  options?: {
    confirmButtonText?: string
    cancelButtonText?: string
    type?: 'success' | 'warning' | 'info' | 'error'
  }
): Promise<void> {
  return ElMessageBox.confirm(message, title, {
    confirmButtonText: options?.confirmButtonText || '确定',
    cancelButtonText: options?.cancelButtonText || '取消',
    type: options?.type || 'warning',
    closeOnClickModal: false
  }).then(() => undefined)
}

/**
 * 删除确认对话框
 * @param message 提示消息
 */
export function confirmDelete(message: string = '此操作将永久删除该数据，是否继续？'): Promise<void> {
  return confirm(message, '删除确认', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
}

/**
 * 提交确认对话框
 * @param message 提示消息
 */
export function confirmSubmit(message: string = '确定要提交吗？'): Promise<void> {
  return confirm(message, '提交确认', {
    confirmButtonText: '提交',
    cancelButtonText: '取消',
    type: 'info'
  })
}

/**
 * 审批确认对话框
 * @param message 提示消息
 */
export function confirmApprove(message: string = '确定要审批通过吗？'): Promise<void> {
  return confirm(message, '审批确认', {
    confirmButtonText: '通过',
    cancelButtonText: '取消',
    type: 'success'
  })
}

/**
 * 驳回确认对话框（带输入框）
 * @param message 提示消息
 */
export function confirmReject(message: string = '请输入驳回原因'): Promise<string> {
  return ElMessageBox.prompt(message, '驳回确认', {
    confirmButtonText: '驳回',
    cancelButtonText: '取消',
    inputType: 'textarea',
    inputPlaceholder: '请输入驳回原因',
    inputValidator: (value: string) => {
      if (!value || value.trim() === '') {
        return '请输入驳回原因'
      }
      return true
    }
  }).then(({ value }) => value)
}

/**
 * 取消确认对话框
 * @param message 提示消息
 */
export function confirmCancel(message: string = '确定要取消吗？'): Promise<void> {
  return confirm(message, '取消确认', {
    confirmButtonText: '确定',
    cancelButtonText: '返回',
    type: 'warning'
  })
}
