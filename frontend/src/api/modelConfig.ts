import request from './request'

export interface ModelConfig {
  id: number
  kbId: number
  name: string
  modelType: string
  provider: string
  baseUrl: string | null
  apiKey: string
  modelName: string
  parameters: Record<string, any> | null
  isActive: boolean
  createdBy: number
  createTime: string
}

export interface ModelConfigParams {
  name: string
  modelType: string
  provider: string
  baseUrl?: string
  apiKey: string
  modelName: string
  parameters?: Record<string, any> | null
  isActive?: boolean
  encrypted?: boolean
}

export const modelConfigApi = {
  list(kbId: number) {
    return request.get<any, { code: number; data: ModelConfig[] }>(`/knowledge-base/${kbId}/model-config`)
  },
  create(kbId: number, params: ModelConfigParams) {
    return request.post<any, { code: number; data: ModelConfig }>(`/knowledge-base/${kbId}/model-config`, params)
  },
  update(kbId: number, configId: number, params: ModelConfigParams) {
    return request.put<any, { code: number; data: ModelConfig }>(
      `/knowledge-base/${kbId}/model-config/${configId}`,
      params,
    )
  },
  remove(kbId: number, configId: number) {
    return request.delete(`/knowledge-base/${kbId}/model-config/${configId}`)
  },
}
