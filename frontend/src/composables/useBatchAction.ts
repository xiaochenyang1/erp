import { ElMessage, ElMessageBox } from 'element-plus'

/**
 * 批量操作辅助：对选中行逐个调用单条端点，用 Promise.allSettled 收集结果并汇总提示。
 *
 * 后端当前没有原生批量端点，这里诚实地循环调用既有单条端点，
 * 部分失败时不整体回滚（每条独立），并把成功/失败数如实反馈给用户。
 */

export interface BatchActionOptions<T> {
  /** 选中的行 */
  rows: T[]
  /** 操作中文名，用于确认框和结果提示，如“启用”“停用” */
  actionLabel: string
  /** 对单行执行的异步操作 */
  handler: (row: T) => Promise<unknown>
  /** 从行取展示名，用于失败明细，可选 */
  rowLabel?: (row: T) => string
  /** 是否需要二次确认，默认 true */
  confirm?: boolean
}

export interface BatchActionResult {
  total: number
  succeeded: number
  failed: number
}

export async function runBatchAction<T>(options: BatchActionOptions<T>): Promise<BatchActionResult | null> {
  const { rows, actionLabel, handler, rowLabel, confirm = true } = options

  if (!rows || rows.length === 0) {
    ElMessage.warning(`请先勾选要${actionLabel}的记录`)
    return null
  }

  if (confirm) {
    try {
      await ElMessageBox.confirm(
        `确认对选中的 ${rows.length} 条记录执行“${actionLabel}”吗？`,
        `批量${actionLabel}`,
        { type: 'warning' }
      )
    } catch {
      return null
    }
  }

  const results = await Promise.allSettled(rows.map((row) => handler(row)))

  const failedItems: string[] = []
  results.forEach((result, index) => {
    if (result.status === 'rejected') {
      failedItems.push(rowLabel ? rowLabel(rows[index]) : String(index + 1))
    }
  })

  const succeeded = rows.length - failedItems.length
  const summary: BatchActionResult = {
    total: rows.length,
    succeeded,
    failed: failedItems.length
  }

  if (failedItems.length === 0) {
    ElMessage.success(`批量${actionLabel}成功，共 ${succeeded} 条`)
  } else if (succeeded === 0) {
    ElMessage.error(`批量${actionLabel}失败，共 ${failedItems.length} 条`)
  } else {
    ElMessage.warning(
      `批量${actionLabel}完成：成功 ${succeeded} 条，失败 ${failedItems.length} 条（${failedItems.slice(0, 5).join('、')}${failedItems.length > 5 ? ' 等' : ''}）`
    )
  }

  return summary
}
