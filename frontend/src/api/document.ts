
import request from './request'

export interface Document {
  id: number
  kbId: number
  fileName: string
  fileUrl: string
  fileSize: number
  fileType: string
  status: number
  chunkCount: number | null
  uploadedBy: number
  createTime: string
}

export const docApi = {
  list(kbId: number) {
    return request.get<any, { code: number; data: Document[] }>(`/knowledge-base/${kbId}/document`)
  },
  get(kbId: number, docId: number) {
    return request.get<any, { code: number; data: Document }>(`/knowledge-base/${kbId}/document/${docId}`)
  },
  upload(kbId: number, file: File, embeddingConfigId?: number) {
    const form = new FormData()
    form.append('file', file)
    if (embeddingConfigId) {
      form.append('embeddingConfigId', String(embeddingConfigId))
    }
    return request.post<any, { code: number; data: Document }>(
      `/knowledge-base/${kbId}/document`,
      form,
      { headers: { 'Content-Type': 'multipart/form-data' } },
    )
  },
  remove(kbId: number, docId: number) {
    return request.delete(`/knowledge-base/${kbId}/document/${docId}`)
  },
  process(kbId: number, docId: number, embeddingConfigId: number) {
    return request.post(`/knowledge-base/${kbId}/document/${docId}/process`, { embeddingConfigId })
  },
}
