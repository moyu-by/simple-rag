import request from './request'

export interface SearchParams {
  query: string
  embeddingConfigId: number
  topK: number
}

export interface ChatParams {
  query: string
  embeddingConfigId: number
  chatConfigId: number
  topK?: number
}

export interface SearchResult {
  content: string
  metadata: {
    kb_id: number
    score: number
  }
}

export interface ChatResult {
  answer: string
  sources: SearchResult[]
}

export const chatApi = {
  search(kbId: number, params: SearchParams) {
    return request.post<any, { code: number; data: SearchResult[] }>(
      `/knowledge-base/${kbId}/search`,
      params,
    )
  },
  chat(kbId: number, params: ChatParams) {
    return request.post<any, { code: number; data: ChatResult }>(
      `/knowledge-base/${kbId}/chat`,
      params,
    )
  },
  /**
   * 流式对话 — 返回 ReadableStream，调用方自己读
   */
  async chatStream(kbId: number, params: ChatParams): Promise<ReadableStream<Uint8Array> | null> {
    const token = localStorage.getItem('token')
    const response = await fetch(`/api/knowledge-base/${kbId}/chat/stream`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(params),
    })
    if (!response.ok) {
      const err = await response.json().catch(() => ({ message: '流式请求失败' }))
      throw new Error(err.message || '流式请求失败')
    }
    return response.body
  },
}
