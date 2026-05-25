import request from './request'

export const userApi = {
  updateDisplayName(displayName: string) {
    return request.put('/user/display-name', { displayName })
  },
}
