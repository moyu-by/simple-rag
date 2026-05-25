import request from './request'

export interface Member {
  userId: number
  account: string
  displayName: string
  roleInKb: string
  joinTime: string
}

export const memberApi = {
  list(kbId: number) {
    return request.get<any, { code: number; data: Member[] }>(`/knowledge-base/${kbId}/member`)
  },
  add(kbId: number, params: { userId: number; roleInKb: string }) {
    return request.post(`/knowledge-base/${kbId}/member`, params)
  },
  update(kbId: number, userId: number, params: { roleInKb: string }) {
    return request.put(`/knowledge-base/${kbId}/member/${userId}`, params)
  },
  remove(kbId: number, userId: number) {
    return request.delete(`/knowledge-base/${kbId}/member/${userId}`)
  },
}
