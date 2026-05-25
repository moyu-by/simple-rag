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
  history?: MessagePair[]
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

export interface ChatHistoryMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  sources?: SearchResult[]
  createTime: string
}

export interface MessagePair {
  role: 'user' | 'assistant'
  content: string
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

  /** 获取聊天历史 */
  loadHistory(kbId: number) {
    return request.get<any, { code: number; data: ChatHistoryMessage[] }>(
      `/knowledge-base/${kbId}/chat/history`,
    )
  },

  /** 保存单条聊天消息 */
  saveMessage(kbId: number, params: { role: string; content: string; sourcesJson?: string }) {
    return request.post<any, { code: number }>(
      `/knowledge-base/${kbId}/chat/message`,
      params,
    )
  },
}
