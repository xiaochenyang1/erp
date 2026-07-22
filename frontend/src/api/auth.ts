import { request } from '@/utils/request'

// 登录请求参数
export interface LoginRequest {
  username: string
  password: string
}

// 登录响应数据
export interface LoginResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  refreshExpiresIn: number
  user: UserInfo
  permissions: string[]
}

// 用户信息
export interface LoginUserDataScope {
  hasAllScope: boolean
  deptScoped: boolean
  postScoped: boolean
  selfScoped: boolean
  warehouseIds: string[]
}

export interface UserInfo {
  id: string
  username: string
  realName?: string
  email?: string
  mobile?: string
  avatar?: string
  locale?: string
  timeZone?: string
  roles?: string[]
  permissions?: string[]
  dataScope?: LoginUserDataScope
}

// 修改密码请求
export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

// 个人资料自助更新（仅当前登录用户）
export interface UpdateProfileRequest {
  realName: string
  email?: string
  mobile?: string
  avatar?: string
  locale?: string
  timeZone?: string
}

// 运行时菜单树节点（后端按当前用户角色过滤后返回）
export interface RuntimeMenu {
  id: string
  parentId?: string
  menuType: string
  menuCode?: string
  menuName?: string
  path?: string
  component?: string
  permission?: string
  sortNo?: number
  visibleFlag?: number
  status?: string
  children?: RuntimeMenu[]
}

/**
 * 登录
 */
export const login = (data: LoginRequest) => {
  return request.post<LoginResponse>('/auth/login', data).then(normalizeLoginResponse)
}

/**
 * 登出
 */
export const logout = (refreshToken: string) => {
  return request.post('/auth/logout', { refreshToken })
}

/**
 * 刷新Token
 */
export const refreshToken = (refreshToken: string) => {
  return request.post<LoginResponse>('/auth/refresh', { refreshToken }).then(normalizeLoginResponse)
}

/**
 * 获取用户信息
 */
export const getUserInfo = () => {
  return request.get<UserInfo>('/auth/user-info').then(normalizeUserInfo)
}

/**
 * 修改密码
 */
export const changePassword = (data: ChangePasswordRequest) => {
  return request.post('/auth/change-password', data)
}

/**
 * 更新当前用户个人资料（姓名/邮箱/手机/头像）
 */
export const updateProfile = (data: UpdateProfileRequest) => {
  return request.put<UserInfo>('/auth/profile', data).then(normalizeUserInfo)
}

/**
 * 获取当前用户的运行时菜单树（后端按角色过滤，SUPER_ADMIN 返回全树）
 */
export const getRuntimeMenuTree = () => {
  return request
    .get<RuntimeMenu[]>('/auth/runtime-menu-tree')
    .then((items) => items.map(normalizeRuntimeMenu))
}

const normalizeRuntimeMenu = (menu: RuntimeMenu): RuntimeMenu => ({
  ...menu,
  id: String(menu.id),
  parentId: menu.parentId != null ? String(menu.parentId) : undefined,
  children: menu.children?.map(normalizeRuntimeMenu)
})

const normalizeLoginResponse = (response: LoginResponse): LoginResponse => ({
  ...response,
  user: normalizeUserInfo(response.user)
})

const normalizeUserInfo = (user: UserInfo): UserInfo => ({
  ...user,
  id: String(user.id),
  dataScope: normalizeUserDataScope(user.dataScope)
})

const normalizeUserDataScope = (dataScope?: LoginUserDataScope): LoginUserDataScope | undefined => {
  if (!dataScope) return undefined
  return {
    ...dataScope,
    warehouseIds: dataScope.warehouseIds.map(String)
  }
}
