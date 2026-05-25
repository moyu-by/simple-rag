import request from './request'

export interface LoginParams {
  account: string
  password: string
}

export interface AuthResult {
  userId: number
  token: string
}

export const authApi = {
  login(params: LoginParams) {
    return request.post<any, { code: number; message: string; data: AuthResult }>('/auth/login', params)
  },
  register(params: LoginParams) {
    return request.post<any, { code: number; message: string; data: AuthResult }>('/auth/register', params)
  },
}
