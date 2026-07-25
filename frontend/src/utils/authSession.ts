export interface AuthSessionHandlers {
  resetRuntimeState?: () => void
  redirectToLogin?: () => void
}

export interface InvalidateAuthSessionOptions {
  redirect?: boolean
}

let handlers: AuthSessionHandlers = {}

/**
 * Bind application-owned state and navigation without making the HTTP layer
 * import Pinia stores or the router (which would create an auth import cycle).
 */
export const configureAuthSessionHandlers = (nextHandlers: AuthSessionHandlers) => {
  handlers = nextHandlers
}

export const redirectToLogin = () => {
  handlers.redirectToLogin?.()
}

export const invalidateAuthSession = (
  options: InvalidateAuthSessionOptions = {}
) => {
  localStorage.removeItem('token')
  localStorage.removeItem('refreshToken')
  handlers.resetRuntimeState?.()
  if (options.redirect !== false) {
    redirectToLogin()
  }
}
