import { get, post } from './request'
import type { LoginResponse, MenuVO, UserInfo } from './types'

export function login(username: string, password: string) {
  return post<LoginResponse>('/system/auth/login', { username, password })
}

export function fetchUserInfo() {
  return get<UserInfo>('/system/auth/me')
}

export function fetchMenuTree() {
  return get<MenuVO[]>('/system/menu/tree')
}

export function logout() {
  return post<void>('/system/auth/logout')
}
