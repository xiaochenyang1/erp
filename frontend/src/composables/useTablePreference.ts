import { reactive, watch } from 'vue'

/**
 * 列表页偏好持久化：列显隐 + 查询条件记忆。
 *
 * 按 storageKey 存入 localStorage，不同页面用不同 key 隔离。
 * 列定义变更（增删列）时以最新 columns 为准：历史偏好里不存在的 prop 会被丢弃，
 * 新增列默认可见，避免版本升级后旧偏好把新列永久藏掉。
 *
 * composable 内部创建并返回 searchForm（reactive），页面直接用它做查询即可，
 * 查询条件按 persistentSearchKeys 白名单记忆，翻页/临时筛选不污染持久化。
 */

export interface TableColumnOption {
  /** 列唯一标识，对应 el-table-column 的 prop */
  prop: string
  /** 列显示名 */
  label: string
  /** 是否允许在列设置里隐藏，缺省 true */
  hideable?: boolean
  /** 默认是否可见，缺省 true */
  defaultVisible?: boolean
}

interface StoredPreference {
  version: number
  hidden: string[]
  query?: Record<string, unknown>
}

const STORAGE_PREFIX = 'erp:table-pref:'
const PREF_VERSION = 1

function readStored(storageKey: string): StoredPreference | null {
  try {
    const raw = localStorage.getItem(STORAGE_PREFIX + storageKey)
    if (!raw) return null
    const parsed = JSON.parse(raw) as StoredPreference
    if (!parsed || parsed.version !== PREF_VERSION) return null
    return parsed
  } catch {
    return null
  }
}

function writeStored(storageKey: string, value: StoredPreference): void {
  try {
    localStorage.setItem(STORAGE_PREFIX + storageKey, JSON.stringify(value))
  } catch {
    /* localStorage 不可用（隐私模式/超配额）时静默降级，不影响页面 */
  }
}

export interface UseTablePreferenceOptions<Q extends object> {
  /** 查询表单默认值，composable 据此创建 reactive searchForm */
  defaultSearchForm: Q
  /** 需要记忆的查询字段白名单（其余字段如 pageNo 不持久化，避免记住临时翻页位置） */
  persistentSearchKeys: Array<keyof Q>
  /** 可显隐的列定义（固定列如编码/名称/操作不必列入） */
  columns: TableColumnOption[]
}

export interface TableColumnState extends TableColumnOption {
  visible: boolean
}

export function useTablePreference<Q extends object>(
  storageKey: string,
  options: UseTablePreferenceOptions<Q>
) {
  const { defaultSearchForm, persistentSearchKeys, columns } = options
  const stored = readStored(storageKey)

  // 查询表单：从默认值克隆，再叠加持久化过的白名单字段
  const searchForm = reactive({ ...defaultSearchForm }) as Q
  if (stored?.query) {
    for (const key of persistentSearchKeys) {
      const k = key as string
      if (k in stored.query) {
        const writableSearchForm = searchForm as Record<string, unknown>
        writableSearchForm[k] = stored.query[k]
      }
    }
  }

  // 列显隐：prop -> visible
  const hiddenSet = new Set(stored?.hidden ?? [])
  const columnVisible = reactive<Record<string, boolean>>({})
  for (const col of columns) {
    const defaultVisible = col.defaultVisible !== false
    columnVisible[col.prop] = !hiddenSet.has(col.prop) && defaultVisible
  }

  function persist(): void {
    const hidden = columns
      .filter((col) => !columnVisible[col.prop])
      .map((col) => col.prop)
    const query: Record<string, unknown> = {}
    for (const key of persistentSearchKeys) {
      query[key as string] = (searchForm as Record<string, unknown>)[key as string]
    }
    writeStored(storageKey, { version: PREF_VERSION, hidden, query })
  }

  watch(columnVisible, persist, { deep: true })
  watch(
    () => persistentSearchKeys.map((key) => (searchForm as Record<string, unknown>)[key as string]),
    persist,
    { deep: true }
  )

  /** 列设置面板用的列状态数组 */
  const columnOptions = () =>
    columns.map<TableColumnState>((col) => ({ ...col, visible: columnVisible[col.prop] !== false }))

  /** 某列是否可见，绑定到 el-table-column 的 v-if */
  function isColumnVisible(prop: string): boolean {
    return columnVisible[prop] !== false
  }

  function setColumnVisible(prop: string, visible: boolean): void {
    if (prop in columnVisible) {
      columnVisible[prop] = visible
    }
  }

  /** 重置列显隐为默认 */
  function resetColumns(): void {
    for (const col of columns) {
      columnVisible[col.prop] = col.defaultVisible !== false
    }
  }

  return {
    searchForm,
    columnVisible,
    columnOptions,
    isColumnVisible,
    setColumnVisible,
    resetColumns
  }
}
