import { request } from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types/common'

// ==================== 用户管理 ====================

export interface User {
  id: string
  username: string
  employeeNo?: string
  realName: string
  email?: string
  mobile?: string
  avatar?: string
  deptId?: string
  deptName?: string
  postId?: string
  postName?: string
  roles?: Role[]
  status: 'ACTIVE' | 'INACTIVE' | 'LOCKED'
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export interface UserQuery extends PageQuery {
  keyword?: string
  username?: string
  realName?: string
  deptId?: string | number
  postId?: string | number
  status?: string
}

export interface UserSaveRequest {
  username?: string
  password?: string
  realName: string
  email?: string
  mobile?: string
  avatar?: string
  deptId?: string | number
  postId?: string | number
  roleIds?: Array<string | number>
  status?: string
  employeeNo?: string
  remark?: string
}

export interface UserRoleAssignment {
  userId: string
  roleIds: string[]
}

// 用户API
export const getUsers = (params: UserQuery) => {
  return request.get<PageResponse<User>>('/system/users', {
    params: normalizeUserQuery(params)
  }).then((page) => ({
    ...page,
    records: page.records.map(normalizeUser)
  }))
}

export const getUser = (id: string | number) => {
  return request.get<User>(`/system/users/${id}`).then(normalizeUser)
}

export const createUser = (data: UserSaveRequest) => {
  return request.post<User>('/system/users', data).then(normalizeUser)
}

export const updateUser = (id: string | number, data: UserSaveRequest) => {
  return request.put<User>(`/system/users/${id}`, data).then(normalizeUser)
}

export const deleteUser = (id: string | number) => {
  return request.post<User>(`/system/users/${id}/disable`).then(normalizeUser)
}

export const enableUser = (id: string | number) => {
  return request.post<User>(`/system/users/${id}/enable`).then(normalizeUser)
}

export const resetUserPassword = (id: string | number, newPassword: string) => {
  return request.post<User>(`/system/users/${id}/reset-password`, { newPassword }).then(normalizeUser)
}

export const getAssignedUserRoles = (id: string | number) => {
  return request.get<UserRoleAssignment>(`/system/users/${id}/roles`).then(normalizeUserRoleAssignment)
}

export const assignUserRoles = (id: string | number, roleIds: Array<string | number>) => {
  return request.put<UserRoleAssignment>(`/system/users/${id}/roles`, { roleIds }).then(normalizeUserRoleAssignment)
}

export interface UserDataScope {
  userId: string
  hasAllScope: boolean
  deptScoped: boolean
  postScoped: boolean
  selfScoped: boolean
  warehouseIds: string[]
  /** 与角色范围并集后的生效结果 */
  effectiveHasAllScope: boolean
  effectiveDeptScoped: boolean
  effectivePostScoped: boolean
  effectiveSelfScoped: boolean
  effectiveWarehouseIds: string[]
}

export interface UserDataScopeAssignRequest {
  hasAllScope?: boolean
  deptScoped?: boolean
  postScoped?: boolean
  selfScoped?: boolean
  warehouseIds?: Array<string | number>
}

export const getAssignedUserDataScope = (id: string | number) => {
  return request.get<UserDataScope>(`/system/users/${id}/data-scope`).then(normalizeUserDataScope)
}

export const assignUserDataScope = (id: string | number, data: UserDataScopeAssignRequest) => {
  return request.put<UserDataScope>(`/system/users/${id}/data-scope`, {
    hasAllScope: !!data.hasAllScope,
    deptScoped: !!data.deptScoped,
    postScoped: !!data.postScoped,
    selfScoped: !!data.selfScoped,
    warehouseIds: data.warehouseIds || []
  }).then(normalizeUserDataScope)
}

const normalizeUserDataScope = (scope: UserDataScope): UserDataScope => ({
  ...scope,
  userId: String(scope.userId),
  hasAllScope: !!scope.hasAllScope,
  deptScoped: !!scope.deptScoped,
  postScoped: !!scope.postScoped,
  selfScoped: !!scope.selfScoped,
  warehouseIds: (scope.warehouseIds || []).map(String),
  effectiveHasAllScope: !!scope.effectiveHasAllScope,
  effectiveDeptScoped: !!scope.effectiveDeptScoped,
  effectivePostScoped: !!scope.effectivePostScoped,
  effectiveSelfScoped: !!scope.effectiveSelfScoped,
  effectiveWarehouseIds: (scope.effectiveWarehouseIds || []).map(String)
})

const normalizeUserQuery = (params: UserQuery) => ({
  ...params,
  keyword: params.keyword || params.username || params.realName || undefined
})

const normalizeUser = (user: User): User => ({
  ...user,
  id: String(user.id),
  deptId: user.deptId != null ? String(user.deptId) : undefined,
  postId: user.postId != null ? String(user.postId) : undefined,
  roles: user.roles || []
})

const normalizeUserRoleAssignment = (assignment: UserRoleAssignment): UserRoleAssignment => ({
  userId: String(assignment.userId),
  roleIds: assignment.roleIds.map(String)
})

// ==================== 角色管理 ====================

export interface Role {
  id: string
  code: string
  name: string
  roleCode?: string
  roleName?: string
  permissions: string[]
  status: 'ACTIVE' | 'INACTIVE'
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export interface RoleQuery extends PageQuery {
  code?: string
  name?: string
  status?: string
}

export interface RoleSaveRequest {
  code: string
  name: string
  permissions: string[]
  status?: string
  remark?: string
}

export interface RoleMenuAssignment {
  roleId: string
  menuIds: string[]
}

// 角色API
export const getRoles = (params: RoleQuery) => {
  return request.get<PageResponse<Role>>('/system/roles', {
    params: normalizeKeywordQuery(params)
  }).then((page) => ({
    ...page,
    records: page.records.map(normalizeRole)
  }))
}

export const getRole = (id: string | number) => {
  return request.get<Role>(`/system/roles/${id}`).then(normalizeRole)
}

export const createRole = (data: RoleSaveRequest) => {
  return request.post<Role>('/system/roles', toRoleCreatePayload(data)).then(normalizeRole)
}

export const updateRole = (id: string | number, data: RoleSaveRequest) => {
  return request.put<Role>(`/system/roles/${id}`, toRoleUpdatePayload(data)).then(normalizeRole)
}

export const deleteRole = (id: string | number) => {
  return request.post<Role>(`/system/roles/${id}/disable`).then(normalizeRole)
}

export const enableRole = (id: string | number) => {
  return request.post<Role>(`/system/roles/${id}/enable`).then(normalizeRole)
}

export const getAllRoles = () => {
  return request.get<PageResponse<Role>>('/system/roles', { params: { pageNo: 1, pageSize: 1000 } })
    .then((res) => res.records.map(normalizeRole))
}

export const assignRoleMenus = (id: string | number, menuIds: Array<string | number>) => {
  return request.put<RoleMenuAssignment>(`/system/roles/${id}/menus`, { menuIds })
    .then(normalizeRoleMenuAssignment)
}

export const getAssignedRoleMenus = (id: string | number) => {
  return request.get<RoleMenuAssignment>(`/system/roles/${id}/menus`)
    .then(normalizeRoleMenuAssignment)
}

export interface RoleDataScope {
  roleId: string
  hasAllScope: boolean
  deptScoped: boolean
  postScoped: boolean
  selfScoped: boolean
  warehouseIds: string[]
}

export interface RoleDataScopeAssignRequest {
  hasAllScope?: boolean
  deptScoped?: boolean
  postScoped?: boolean
  selfScoped?: boolean
  warehouseIds?: Array<string | number>
}

export const getAssignedRoleDataScope = (id: string | number) => {
  return request.get<RoleDataScope>(`/system/roles/${id}/data-scope`).then(normalizeRoleDataScope)
}

export const assignRoleDataScope = (id: string | number, data: RoleDataScopeAssignRequest) => {
  return request.put<RoleDataScope>(`/system/roles/${id}/data-scope`, {
    hasAllScope: !!data.hasAllScope,
    deptScoped: !!data.deptScoped,
    postScoped: !!data.postScoped,
    selfScoped: !!data.selfScoped,
    warehouseIds: data.warehouseIds || []
  }).then(normalizeRoleDataScope)
}

const normalizeRoleDataScope = (scope: RoleDataScope): RoleDataScope => ({
  ...scope,
  roleId: String(scope.roleId),
  hasAllScope: !!scope.hasAllScope,
  deptScoped: !!scope.deptScoped,
  postScoped: !!scope.postScoped,
  selfScoped: !!scope.selfScoped,
  warehouseIds: (scope.warehouseIds || []).map(String)
})

const normalizeRole = (role: Role): Role => ({
  ...role,
  id: String(role.id),
  code: role.code ?? role.roleCode ?? '',
  name: role.name ?? role.roleName ?? '',
  permissions: role.permissions || []
})

const toRoleCreatePayload = (data: RoleSaveRequest) => ({
  roleCode: data.code,
  roleName: data.name,
  remark: data.remark
})

const toRoleUpdatePayload = (data: RoleSaveRequest) => ({
  roleName: data.name,
  remark: data.remark
})

const normalizeRoleMenuAssignment = (assignment: RoleMenuAssignment): RoleMenuAssignment => ({
  roleId: String(assignment.roleId),
  menuIds: assignment.menuIds.map(String)
})

const normalizeKeywordQuery = <T extends PageQuery & { code?: string; name?: string }>(params: T) => {
  const { code, name, ...rest } = params
  return {
    ...rest,
    keyword: code || name || undefined
  }
}

// ==================== 菜单管理 ====================

export interface Menu {
  id: string
  parentId?: string
  code?: string
  name: string
  menuCode?: string
  menuName?: string
  path?: string
  component?: string
  icon?: string
  orderNum: number
  sortNo?: number
  type: 'MENU' | 'BUTTON'
  menuType?: 'MENU' | 'BUTTON' | string
  permission?: string
  status: 'ACTIVE' | 'INACTIVE'
  children?: Menu[]
}

export interface MenuSaveRequest {
  parentId?: string | number
  code?: string
  name: string
  path?: string
  component?: string
  icon?: string
  orderNum: number
  type: string
  permission?: string
  status?: string
}

// 菜单API
export const getMenuTree = () => {
  return request.get<Menu[]>('/system/menus/tree').then((items) => items.map(normalizeMenu))
}

export const getMenu = (id: string | number) => {
  return request.get<Menu>(`/system/menus/${id}`).then(normalizeMenu)
}

export const createMenu = (data: MenuSaveRequest) => {
  return request.post<Menu>('/system/menus', toMenuCreatePayload(data)).then(normalizeMenu)
}

export const updateMenu = (id: string | number, data: MenuSaveRequest) => {
  return request.put<Menu>(`/system/menus/${id}`, toMenuUpdatePayload(data)).then(normalizeMenu)
}

export const deleteMenu = (id: string | number) => {
  return request.post<Menu>(`/system/menus/${id}/disable`).then(normalizeMenu)
}

export const enableMenu = (id: string | number) => {
  return request.post<Menu>(`/system/menus/${id}/enable`).then(normalizeMenu)
}

const normalizeMenu = (menu: Menu): Menu => ({
  ...menu,
  id: String(menu.id),
  parentId: menu.parentId != null ? String(menu.parentId) : undefined,
  code: menu.code ?? menu.menuCode,
  name: menu.name ?? menu.menuName ?? '',
  type: (menu.type ?? menu.menuType ?? 'MENU') as Menu['type'],
  orderNum: menu.orderNum ?? menu.sortNo ?? 0,
  children: menu.children?.map(normalizeMenu)
})

const toMenuCreatePayload = (data: MenuSaveRequest) => ({
  parentId: data.parentId,
  menuType: data.type,
  menuCode: data.code || data.name,
  menuName: data.name,
  path: data.path,
  component: data.component,
  permission: data.permission,
  sortNo: data.orderNum
})

const toMenuUpdatePayload = (data: MenuSaveRequest) => ({
  menuName: data.name,
  path: data.path,
  component: data.component,
  permission: data.permission,
  sortNo: data.orderNum
})

// ==================== 部门管理 ====================

export interface Dept {
  id: string
  parentId?: string
  name: string
  deptName?: string
  code?: string
  deptCode?: string
  leaderUserId?: string
  manager?: string
  contact?: string
  orderNum: number
  sortNo?: number
  status: 'ACTIVE' | 'INACTIVE'
  remark?: string
  children?: Dept[]
}

export interface DeptSaveRequest {
  parentId?: string | number
  name: string
  code?: string
  manager?: string
  contact?: string
  orderNum: number
  status?: string
}

// 部门API
export const getDeptTree = () => {
  return request.get<Dept[]>('/system/depts/tree').then((items) => items.map(normalizeDept))
}

export const getDept = (id: string | number) => {
  return request.get<Dept>(`/system/depts/${id}`).then(normalizeDept)
}

export const createDept = (data: DeptSaveRequest) => {
  return request.post<Dept>('/system/depts', toDeptCreatePayload(data)).then(normalizeDept)
}

export const updateDept = (id: string | number, data: DeptSaveRequest) => {
  return request.put<Dept>(`/system/depts/${id}`, toDeptUpdatePayload(data)).then(normalizeDept)
}

export const deleteDept = (id: string | number) => {
  return request.post<Dept>(`/system/depts/${id}/disable`).then(normalizeDept)
}

export const enableDept = (id: string | number) => {
  return request.post<Dept>(`/system/depts/${id}/enable`).then(normalizeDept)
}

const normalizeDept = (dept: Dept): Dept => ({
  ...dept,
  id: String(dept.id),
  parentId: dept.parentId != null ? String(dept.parentId) : undefined,
  leaderUserId: dept.leaderUserId != null ? String(dept.leaderUserId) : undefined,
  name: dept.name ?? dept.deptName ?? '',
  code: dept.code ?? dept.deptCode,
  manager: dept.manager ?? (dept.leaderUserId != null ? String(dept.leaderUserId) : undefined),
  orderNum: dept.orderNum ?? dept.sortNo ?? 0,
  children: dept.children?.map(normalizeDept)
})

const toDeptCreatePayload = (data: DeptSaveRequest) => ({
  parentId: data.parentId,
  deptCode: data.code,
  deptName: data.name,
  leaderUserId: data.manager || undefined,
  sortNo: data.orderNum,
  remark: data.contact || undefined
})

const toDeptUpdatePayload = (data: DeptSaveRequest) => ({
  deptName: data.name,
  leaderUserId: data.manager || undefined,
  sortNo: data.orderNum,
  remark: data.contact || undefined
})

// ==================== 岗位管理 ====================

export interface Post {
  id: string
  deptId?: string
  code: string
  name: string
  postCode?: string
  postName?: string
  orderNum: number
  status: 'ACTIVE' | 'INACTIVE'
  remark?: string
}

export interface PostQuery extends PageQuery {
  code?: string
  name?: string
  status?: string
  deptId?: string | number
}

export interface PostSaveRequest {
  deptId?: string | number
  code: string
  name: string
  orderNum: number
  status?: string
  remark?: string
}

// 岗位API
export const getPosts = (params: PostQuery) => {
  return request.get<PageResponse<Post>>('/system/posts', {
    params: normalizeKeywordQuery(params)
  }).then((page) => ({
    ...page,
    records: page.records.map(normalizePost)
  }))
}

export const getPost = (id: string | number) => {
  return request.get<Post>(`/system/posts/${id}`).then(normalizePost)
}

export const createPost = (data: PostSaveRequest) => {
  return request.post<Post>('/system/posts', toPostCreatePayload(data)).then(normalizePost)
}

export const updatePost = (id: string | number, data: PostSaveRequest) => {
  return request.put<Post>(`/system/posts/${id}`, toPostUpdatePayload(data)).then(normalizePost)
}

export const deletePost = (id: string | number) => {
  return request.post<Post>(`/system/posts/${id}/disable`).then(normalizePost)
}

export const enablePost = (id: string | number) => {
  return request.post<Post>(`/system/posts/${id}/enable`).then(normalizePost)
}

export const getAllPosts = () => {
  return request.get<PageResponse<Post>>('/system/posts', { params: { pageNo: 1, pageSize: 1000 } })
    .then((res) => res.records.map(normalizePost))
}

const normalizePost = (post: Post): Post => ({
  ...post,
  id: String(post.id),
  deptId: post.deptId != null ? String(post.deptId) : undefined,
  code: post.code ?? post.postCode ?? '',
  name: post.name ?? post.postName ?? '',
  orderNum: post.orderNum ?? 0
})

const toPostCreatePayload = (data: PostSaveRequest) => ({
  deptId: data.deptId,
  postCode: data.code,
  postName: data.name,
  remark: data.remark
})

const toPostUpdatePayload = (data: PostSaveRequest) => ({
  postName: data.name,
  remark: data.remark
})

// ==================== 字典管理 ====================

export interface DictType {
  id: string
  code: string
  name: string
  dictType?: string
  dictName?: string
  status: 'ACTIVE' | 'INACTIVE'
  remark?: string
  items?: DictItem[]
}

export interface DictItem {
  id: string
  typeId?: string
  typeCode: string
  dictType?: string
  label: string
  itemLabel?: string
  value: string
  itemValue?: string
  orderNum: number
  sortNo?: number
  status: 'ACTIVE' | 'INACTIVE'
  remark?: string
}

export interface DictTypeQuery extends PageQuery {
  code?: string
  name?: string
  status?: string
}

export interface DictTypeSaveRequest {
  code: string
  name: string
  status?: string
  remark?: string
}

export interface DictItemSaveRequest {
  typeCode: string
  label: string
  value: string
  orderNum: number
  status?: string
}

// 字典API
export const getDictTypes = (params: DictTypeQuery) => {
  return request.get<PageResponse<DictType>>('/system/dict-types', {
    params: normalizeKeywordQuery(params)
  }).then((page) => ({
    ...page,
    records: page.records.map(normalizeDictType)
  }))
}

export const getDictType = (id: string | number) => {
  return request.get<DictType>(`/system/dict-types/${id}`).then(normalizeDictType)
}

export const createDictType = (data: DictTypeSaveRequest) => {
  return request.post<DictType>('/system/dict-types', toDictTypeCreatePayload(data)).then(normalizeDictType)
}

export const updateDictType = (id: string | number, data: DictTypeSaveRequest) => {
  return request.put<DictType>(`/system/dict-types/${id}`, toDictTypeUpdatePayload(data)).then(normalizeDictType)
}

export const deleteDictType = (id: string | number) => {
  return request.post<DictType>(`/system/dict-types/${id}/disable`).then(normalizeDictType)
}

export const enableDictType = (id: string | number) => {
  return request.post<DictType>(`/system/dict-types/${id}/enable`).then(normalizeDictType)
}

export const getDictItems = (typeCode: string) => {
  return request.get<DictItem[]>(`/system/dict-types/${typeCode}/items`).then((items) => items.map(normalizeDictItem))
}

export const createDictItem = (data: DictItemSaveRequest) => {
  return request.post<DictItem>('/system/dict-items', toDictItemCreatePayload(data)).then(normalizeDictItem)
}

export const updateDictItem = (id: string | number, data: DictItemSaveRequest) => {
  return request.put<DictItem>(`/system/dict-items/${id}`, toDictItemUpdatePayload(data)).then(normalizeDictItem)
}

export const deleteDictItem = (id: string | number) => {
  return request.post<DictItem>(`/system/dict-items/${id}/disable`).then(normalizeDictItem)
}

export const enableDictItem = (id: string | number) => {
  return request.post<DictItem>(`/system/dict-items/${id}/enable`).then(normalizeDictItem)
}

const normalizeDictType = (dict: DictType): DictType => ({
  ...dict,
  id: String(dict.id),
  code: dict.code ?? dict.dictType ?? '',
  name: dict.name ?? dict.dictName ?? ''
})

const normalizeDictItem = (item: DictItem): DictItem => ({
  ...item,
  id: String(item.id),
  typeId: item.typeId != null ? String(item.typeId) : undefined,
  typeCode: item.typeCode ?? item.dictType ?? '',
  label: item.label ?? item.itemLabel ?? '',
  value: item.value ?? item.itemValue ?? '',
  orderNum: item.orderNum ?? item.sortNo ?? 0
})

const toDictTypeCreatePayload = (data: DictTypeSaveRequest) => ({
  dictType: data.code,
  dictName: data.name,
  remark: data.remark
})

const toDictTypeUpdatePayload = (data: DictTypeSaveRequest) => ({
  dictName: data.name,
  remark: data.remark
})

const toDictItemCreatePayload = (data: DictItemSaveRequest) => ({
  dictType: data.typeCode,
  itemLabel: data.label,
  itemValue: data.value,
  sortNo: data.orderNum
})

const toDictItemUpdatePayload = (data: DictItemSaveRequest) => ({
  itemLabel: data.label,
  sortNo: data.orderNum
})

// ==================== 操作日志 ====================

export interface OperationLog {
  id: string
  userId?: string
  username?: string
  module: string
  operation: string
  bizNo?: string
  result?: 'SUCCESS' | 'FAIL' | 'FAILURE' | string
  message?: string
  requestMethod?: string
  requestUri?: string
  operationTime?: string
  method: string
  requestUrl: string
  requestParams?: string
  responseData?: string
  ipAddress: string
  userAgent?: string
  operatorId: string
  operatorName: string
  status: 'SUCCESS' | 'FAIL'
  errorMsg?: string
  executionTime: number
  createdAt: string
}

export interface OperationLogQuery extends PageQuery {
  userId?: string | number
  module?: string
  operation?: string
  bizNo?: string
  operatorName?: string
  username?: string
  status?: string
  result?: string
  startDate?: string
  endDate?: string
  operationTimeFrom?: string
  operationTimeTo?: string
}

// 操作日志API
export const getOperationLogs = (params: OperationLogQuery) => {
  return request.get<PageResponse<OperationLog>>('/system/operation-logs', {
    params: toOperationLogQueryParams(params)
  }).then((page) => ({
    ...page,
    records: page.records.map(normalizeOperationLog)
  }))
}

export const getOperationLog = (id: string | number) => {
  return request.get<OperationLog>(`/system/operation-logs/${id}`).then(normalizeOperationLog)
}

export const exportOperationLogs = (params: OperationLogQuery) => {
  return request.get<Blob>('/system/operation-logs/export', {
    params: toOperationLogQueryParams(params),
    responseType: 'blob'
  })
}

export interface LoginLog {
  id: string
  userId?: string
  username?: string
  result?: 'SUCCESS' | 'FAIL' | 'FAILURE' | string
  message?: string
  loginIp?: string
  userAgent?: string
  loginTime?: string
}

export interface LoginLogQuery extends PageQuery {
  userId?: string | number
  username?: string
  result?: string
  loginTimeFrom?: string
  loginTimeTo?: string
}

export const getLoginLogs = (params: LoginLogQuery) => {
  return request.get<PageResponse<LoginLog>>('/system/login-logs', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeLoginLog)
  }))
}

export interface AuditLog {
  id: string
  auditType?: string
  businessType?: string
  businessId?: string
  businessNo?: string
  action?: string
  operatorId?: string
  operatorName?: string
  snapshotJson?: string
  message?: string
  auditTime?: string
}

export interface AuditLogQuery extends PageQuery {
  auditType?: string
  businessType?: string
  businessId?: string | number
  businessNo?: string
  action?: string
  operatorId?: string | number
  operatorName?: string
  auditTimeFrom?: string
  auditTimeTo?: string
}

export const getAuditLogs = (params: AuditLogQuery) => {
  return request.get<PageResponse<AuditLog>>('/system/audit-logs', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeAuditLog)
  }))
}

const toOperationLogQueryParams = (params: OperationLogQuery) => {
  const { operatorName, status, startDate, endDate, ...rest } = params
  return {
    ...rest,
    username: operatorName || params.username || undefined,
    result: status || params.result || undefined,
    operationTimeFrom: toStartDateTime(startDate) || params.operationTimeFrom || undefined,
    operationTimeTo: toEndDateTime(endDate) || params.operationTimeTo || undefined
  }
}

const normalizeOperationLog = (log: OperationLog): OperationLog => {
  const result = log.result || log.status
  return {
    ...log,
    id: String(log.id),
    userId: log.userId != null ? String(log.userId) : undefined,
    operatorId: String(log.operatorId ?? log.userId ?? ''),
    operatorName: log.operatorName ?? log.username ?? '-',
    status: result === 'FAILURE' ? 'FAIL' : (result as 'SUCCESS' | 'FAIL') || 'SUCCESS',
    method: log.method ?? log.requestMethod ?? '-',
    requestUrl: log.requestUrl ?? log.requestUri ?? '-',
    ipAddress: log.ipAddress ?? '-',
    executionTime: log.executionTime ?? 0,
    createdAt: log.createdAt ?? log.operationTime ?? '',
    errorMsg: log.errorMsg ?? (result && result !== 'SUCCESS' ? log.message : undefined)
  }
}

const normalizeLoginLog = (log: LoginLog): LoginLog => ({
  ...log,
  id: String(log.id),
  userId: log.userId != null ? String(log.userId) : undefined
})

const normalizeAuditLog = (log: AuditLog): AuditLog => ({
  ...log,
  id: String(log.id),
  businessId: log.businessId != null ? String(log.businessId) : undefined,
  operatorId: log.operatorId != null ? String(log.operatorId) : undefined
})

const toStartDateTime = (date?: string) => {
  return date && !date.includes('T') ? `${date}T00:00:00` : date
}

const toEndDateTime = (date?: string) => {
  return date && !date.includes('T') ? `${date}T23:59:59` : date
}

// ==================== 系统配置 ====================

export interface SystemConfig {
  id: string
  configKey: string
  configCode?: string
  configName?: string
  configValue: string
  configType: 'STRING' | 'NUMBER' | 'BOOLEAN' | 'JSON'
  description?: string
  remark?: string
  status?: string
  updatedAt: string
}

export interface SystemConfigQuery extends PageQuery {
  configKey?: string
  status?: string
}

export interface SystemConfigSaveRequest {
  configKey?: string
  configName?: string
  configValue: string
  description?: string
  remark?: string
}

// 系统配置API
export const getSystemConfigs = (params: SystemConfigQuery) => {
  return request.get<PageResponse<SystemConfig>>('/system/configs', {
    params: {
      ...params,
      keyword: params.configKey || undefined
    }
  }).then((page) => ({
    ...page,
    records: page.records.map(normalizeSystemConfig)
  }))
}

export const getSystemConfig = (id: string | number) => {
  return request.get<SystemConfig>(`/system/configs/${id}`).then(normalizeSystemConfig)
}

export const createSystemConfig = (data: SystemConfigSaveRequest) => {
  return request.post<SystemConfig>('/system/configs', toSystemConfigCreatePayload(data))
    .then(normalizeSystemConfig)
}

export const updateSystemConfig = (id: string | number, data: SystemConfigSaveRequest) => {
  return request.put<SystemConfig>(`/system/configs/${id}`, toSystemConfigUpdatePayload(data))
    .then(normalizeSystemConfig)
}

export const enableSystemConfig = (id: string | number) => {
  return request.post<SystemConfig>(`/system/configs/${id}/enable`).then(normalizeSystemConfig)
}

export const disableSystemConfig = (id: string | number) => {
  return request.post<SystemConfig>(`/system/configs/${id}/disable`).then(normalizeSystemConfig)
}

const normalizeSystemConfig = (config: SystemConfig): SystemConfig => ({
  ...config,
  id: String(config.id),
  configKey: config.configKey ?? config.configCode ?? '',
  configName: config.configName || config.configKey || config.configCode || '',
  configType: config.configType ?? 'STRING',
  description: config.description ?? config.remark,
  updatedAt: config.updatedAt ?? ''
})

const toSystemConfigUpdatePayload = (data: SystemConfigSaveRequest) => ({
  configName: data.configName || data.configKey || '',
  configValue: data.configValue,
  remark: data.remark ?? data.description
})

const toSystemConfigCreatePayload = (data: SystemConfigSaveRequest) => ({
  configCode: data.configKey || '',
  configName: data.configName || data.configKey || '',
  configValue: data.configValue,
  remark: data.remark ?? data.description
})

// ==================== 编号规则 ====================

export interface SequenceRule {
  id: string
  companyId: string
  accountBookId: string
  bizType: string
  prefix: string
  datePattern: string
  seqLength: number
  currentValue: string
  status: 'ACTIVE' | 'DISABLED' | string
}

export interface SequenceRuleQuery extends PageQuery {
  keyword?: string
  status?: string
}

export interface SequenceRuleSaveRequest {
  bizType?: string
  prefix: string
  datePattern: string
  seqLength: number
  currentValue?: string | number
}

export const getSequenceRules = (params: SequenceRuleQuery) => {
  return request.get<PageResponse<SequenceRule>>('/system/sequence-rules', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeSequenceRule)
  }))
}

export const getSequenceRule = (id: string | number) => {
  return request.get<SequenceRule>(`/system/sequence-rules/${id}`).then(normalizeSequenceRule)
}

export const createSequenceRule = (data: SequenceRuleSaveRequest) => {
  return request.post<SequenceRule>('/system/sequence-rules', toSequenceRulePayload(data)).then(normalizeSequenceRule)
}

export const updateSequenceRule = (id: string | number, data: SequenceRuleSaveRequest) => {
  return request.put<SequenceRule>(`/system/sequence-rules/${id}`, toSequenceRulePayload(data)).then(normalizeSequenceRule)
}

export const enableSequenceRule = (id: string | number) => {
  return request.post<SequenceRule>(`/system/sequence-rules/${id}/enable`).then(normalizeSequenceRule)
}

export const disableSequenceRule = (id: string | number) => {
  return request.post<SequenceRule>(`/system/sequence-rules/${id}/disable`).then(normalizeSequenceRule)
}

const normalizeSequenceRule = (rule: SequenceRule): SequenceRule => ({
  ...rule,
  id: String(rule.id),
  companyId: String(rule.companyId),
  accountBookId: String(rule.accountBookId),
  seqLength: Number(rule.seqLength || 0),
  currentValue: String(rule.currentValue ?? 0)
})

const toSequenceRulePayload = (data: SequenceRuleSaveRequest) => ({
  ...(data.bizType ? { bizType: data.bizType } : {}),
  prefix: data.prefix,
  datePattern: data.datePattern,
  seqLength: data.seqLength,
  currentValue: data.currentValue != null && data.currentValue !== '' ? String(data.currentValue) : '0'
})

// ==================== 单据状态流转规则(只读) ====================

// 单据状态流转规则:后端 GET /api/system/document-state-rules 返回各单据类型
// 在各动作下允许的源状态、目标状态和门禁权限,供运维/管理员排查“为什么这张单据不能流转”。
export interface DocumentStateRule {
  documentType: string
  documentName: string
  action: string
  actionName: string
  method: string
  path: string
  permission: string
  allowedStatuses: string[]
  allowedApprovalStatuses: string[]
  executionStatusField: string | null
  allowedExecutionStatuses: string[]
  blockedExecutionStatuses: string[]
  targetStatus: string | null
  targetApprovalStatus: string | null
  stateFailureMessage: string | null
  executionFailureMessage: string | null
}

/**
 * 单据状态流转规则列表(只读)
 */
export const getDocumentStateRules = () => {
  return request.get<DocumentStateRule[]>('/system/document-state-rules')
}

// ==================== 系统环境 ====================

export interface SystemProfile {
  scope: string
  [key: string]: string
}

export const getSystemProfile = () => {
  return request.get<SystemProfile>('/system/profile')
}
