import request from './request'

export interface KnowledgeBase {
  id: number
  name: string
  description: string | null
  ownerId: number
  myRole: string
  createTime: string
}

export const kbApi = {
  list() {
    return request.get<any, { code: number; data: KnowledgeBase[] }>('/knowledge-base')
  },
  get(id: number) {
    return request.get<any, { code: number; data: KnowledgeBase }>(`/knowledge-base/${id}`)
  },
  create(params: { name: string; description?: string }) {
    return request.post<any, { code: number; data: KnowledgeBase }>('/knowledge-base', params)
  },
  update(id: number, params: { name?: string; description?: string }) {
    return request.put<any, { code: number; data: KnowledgeBase }>(`/knowledge-base/${id}`, params)
  },
  remove(id: number) {
    return request.delete(`/knowledge-base/${id}`)
  },
}
