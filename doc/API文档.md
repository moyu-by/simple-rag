# RAG 知识库系统 — API 文档

> 版本：0.0.1-SNAPSHOT  
> 基础地址：`http://localhost:8080`  
> Swagger UI：`http://localhost:8080/swagger-ui.html`  
> OpenAPI JSON：`http://localhost:8080/v3/api-docs`

---

## 目录

- [1. 通用约定](#1-通用约定)
- [2. 认证接口](#2-认证接口)
- [3. 知识库管理](#3-知识库管理)
- [4. 成员管理](#4-成员管理)
- [5. 模型配置](#5-模型配置)
- [6. 文档管理](#6-文档管理)
- [7. RAG 对话](#7-rag-对话)
- [8. 幂等 Token](#8-幂等-token)
- [9. 错误码参考](#9-错误码参考)
- [10. 业务枚举](#10-业务枚举)

---

## 1. 通用约定

### 1.1 统一响应格式

所有接口返回以下 JSON 结构：

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | `int` | 业务状态码，200 = 成功 |
| `message` | `string` | 提示信息 |
| `data` | `T` / `null` | 业务数据，无数据时为 `null` |

### 1.2 认证方式

除标记 `@NoLoginRequired` 的接口外，所有请求必须在 Header 中携带 JWT Token：

```
Authorization: Bearer <token>
```

Token 通过登录/注册接口获取，默认有效期 7 天。

### 1.3 密码加密说明

密码（`password` 字段）需用 **RSA 公钥** 加密后传输。公钥通过 `GET /auth/public-key` 获取。

### 1.4 API Key 加密说明

模型配置中的 `apiKey` 支持两种传输方式：

| 方式 | `encrypted` 字段 | 说明 |
|------|:---:|------|
| HTTPS 明文 | 不传或 `false` | 依赖 TLS 保护传输，服务端 AES 加密落库 |
| RSA 加密 | `true` | 前端用 RSA 公钥加密 apiKey，服务端先 RSA 解密再 AES 落库 |

---

## 2. 认证接口

### 2.1 获取 RSA 公钥

```http
GET /auth/public-key
```

**认证**：无需登录

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "publicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A..."
  }
}
```

### 2.2 注册

```http
POST /auth/register
```

**认证**：无需登录

**请求体**：

```json
{
  "account": "zhangsan",
  "password": "<RSA公钥加密后的密码>"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `account` | `string` | ✅ | 账号，唯一 |
| `password` | `string` | ✅ | 密码，RSA 公钥加密后的密文 |

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": 1,
    "token": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `userId` | `long` | 新用户 ID |
| `token` | `string` | JWT Token，后续请求携带 |

> **错误**：账号已存在 → `401` `"账号已存在"`

### 2.3 登录

```http
POST /auth/login
```

**认证**：无需登录

**请求体**：

```json
{
  "account": "zhangsan",
  "password": "<RSA公钥加密后的密码>"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `account` | `string` | ✅ | 账号 |
| `password` | `string` | ✅ | 密码，RSA 公钥加密后的密文 |

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": 1,
    "token": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

> **错误**：账号或密码错误 → `401` `"账号或密码错误"`

---

## 3. 知识库管理

### 3.1 创建知识库

```http
POST /knowledge-base
```

**认证**：需登录

**请求体**：

```json
{
  "name": "产品手册",
  "description": "客服使用的产品知识库"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `name` | `string` | ✅ | 知识库名称 |
| `description` | `string` | ❌ | 描述 |

**响应**：

```json
{
  "code": 200,
  "data": {
    "id": 1,
    "name": "产品手册",
    "description": "客服使用的产品知识库",
    "ownerId": 1,
    "myRole": "库主",
    "createTime": "2026-05-25T12:00:00"
  }
}
```

> 创建者自动成为该知识库的 **BOSS**（库主）。

### 3.2 列出我的知识库

```http
GET /knowledge-base
```

**认证**：需登录

**响应**：

```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "name": "产品手册",
      "description": "客服使用的产品知识库",
      "ownerId": 1,
      "myRole": "库主",
      "createTime": "2026-05-25T12:00:00"
    }
  ]
}
```

> 只返回当前用户参与的知识库（作为 BOSS / ADMIN / MEMBER）。

### 3.3 查看知识库

```http
GET /knowledge-base/{id}
```

**认证**：需登录，需为该知识库成员

### 3.4 修改知识库

```http
PUT /knowledge-base/{id}
```

**认证**：需登录，需 ADMIN 或 BOSS 身份

**请求体**：

```json
{
  "name": "产品手册 V2",
  "description": "更新后的描述"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `name` | `string` | ❌ | 留空则不更新 |
| `description` | `string` | ❌ | 留空则不更新 |

### 3.5 删除知识库

```http
DELETE /knowledge-base/{id}
```

**认证**：需登录，需 BOSS 身份

> **级联删除**：同时删除该知识库下的所有文档记录、模型配置、成员关系、向量数据。  
> ⚠️ 注意：不会自动删除磁盘上的文件。

---

## 4. 成员管理

### 4.1 列出成员

```http
GET /knowledge-base/{kbId}/member
```

**认证**：需登录，需为该知识库成员

**响应**：

```json
{
  "code": 200,
  "data": [
    {
      "userId": 1,
      "account": "zhangsan",
      "displayName": "zhangsan",
      "roleInKb": "库主",
      "joinTime": "2026-05-25T12:00:00"
    }
  ]
}
```

### 4.2 添加成员

```http
POST /knowledge-base/{kbId}/member
```

**认证**：需登录

| 调用者角色 | 可添加的角色 |
|-----------|-------------|
| BOSS | MEMBER / ADMIN / BOSS |
| ADMIN | 仅 MEMBER |
| MEMBER | ❌ 无权限 |

**请求体**：

```json
{
  "userId": 2,
  "roleInKb": "MEMBER"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `userId` | `long` | ✅ | 目标用户 ID |
| `roleInKb` | `string` | ✅ | 角色：`MEMBER` / `ADMIN` / `BOSS` |

> **错误**：用户不存在 → `401`；已在知识库中 → `401`；权限不足 → `401`

### 4.3 修改成员角色

```http
PUT /knowledge-base/{kbId}/member/{userId}
```

**认证**：需登录

| 调用者角色 | 可操作的成员 | 可设置的角色 |
|-----------|-------------|-------------|
| BOSS | 任何人（除自己） | MEMBER / ADMIN / BOSS |
| ADMIN | 仅 MEMBER | MEMBER / ADMIN |
| MEMBER | ❌ 无权限 | — |

**请求体**：

```json
{
  "roleInKb": "ADMIN"
}
```

> **错误**：不能修改自己 → `401`

### 4.4 移除成员

```http
DELETE /knowledge-base/{kbId}/member/{userId}
```

**认证**：需登录

| 调用者角色 | 可移除的成员 |
|-----------|-------------|
| BOSS | 任何人（除自己） |
| ADMIN | 仅 MEMBER |
| MEMBER | ❌ 无权限 |

---

## 5. 模型配置

每个知识库可配置多个模型（嵌入模型 + 对话模型），支持不同提供商。

### 5.1 列出模型配置

```http
GET /knowledge-base/{kbId}/model-config
```

**认证**：需登录，需为该知识库成员

**响应**：

```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "kbId": 1,
      "name": "OpenAI GPT-4",
      "provider": "openai",
      "baseUrl": "https://api.openai.com",
      "apiKey": "****-abcd",
      "modelName": "gpt-4",
      "parameters": { "temperature": 0.7 },
      "isActive": true,
      "createdBy": 1,
      "createTime": "2026-05-25T12:00:00"
    }
  ]
}
```

> `apiKey` 返回的是掩码后的值（`****` + 末4位）。完整 API Key 无法通过接口获取。

### 5.2 添加模型配置

```http
POST /knowledge-base/{kbId}/model-config
```

**认证**：需登录，需 ADMIN 或 BOSS 身份

**请求体（HTTPS 明文传输）**：

```json
{
  "name": "硅基流动嵌入",
  "provider": "openai",
  "baseUrl": "https://api.siliconflow.cn",
  "apiKey": "sk-your-api-key",
  "modelName": "BAAI/bge-large-zh-v1.5",
  "parameters": null,
  "isActive": true
}
```

**请求体（RSA 加密传输，推荐）**：

```json
{
  "name": "硅基流动嵌入",
  "provider": "openai",
  "baseUrl": "https://api.siliconflow.cn",
  "apiKey": "<RSA公钥加密后的密钥>",
  "modelName": "BAAI/bge-large-zh-v1.5",
  "encrypted": true
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `name` | `string` | ✅ | 配置名称 |
| `provider` | `string` | ✅ | 提供商，当前支持 `openai` / `custom`（OpenAI 兼容协议） |
| `baseUrl` | `string` | ❌ | API 地址，留空默认 `https://api.openai.com` |
| `apiKey` | `string` | ✅ | API 密钥 |
| `modelName` | `string` | ✅ | 模型名称，如 `gpt-4`、`text-embedding-ada-002`、`BAAI/bge-large-zh-v1.5` |
| `parameters` | `object` | ❌ | 模型参数（JSON 对象，如 `{"temperature":0.7}`） |
| `isActive` | `boolean` | ❌ | 是否启用，默认 `true` |
| `encrypted` | `boolean` | ❌ | apiKey 是否经 RSA 加密，默认 `false` |

**响应**：

```json
{
  "code": 200,
  "data": {
    "id": 2,
    "kbId": 1,
    "name": "硅基流动嵌入",
    "provider": "openai",
    "baseUrl": "https://api.siliconflow.cn",
    "apiKey": "****-f3a2",
    "modelName": "BAAI/bge-large-zh-v1.5",
    "parameters": null,
    "isActive": true,
    "createdBy": 1,
    "createTime": "2026-05-25T12:05:00"
  }
}
```

### 5.3 修改模型配置

```http
PUT /knowledge-base/{kbId}/model-config/{configId}
```

**认证**：需登录，需 ADMIN 或 BOSS 身份

请求体与 [5.2 添加模型配置](#52-添加模型配置) 相同。

### 5.4 删除模型配置

```http
DELETE /knowledge-base/{kbId}/model-config/{configId}
```

**认证**：需登录，需 ADMIN 或 BOSS 身份

---

## 6. 文档管理

### 6.1 上传文档

```http
POST /knowledge-base/{kbId}/document
Content-Type: multipart/form-data
```

**认证**：需登录，需 ADMIN 或 BOSS 身份

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `file` | file | ✅ | 文件，支持 `pdf / doc / docx / xls / xlsx / ppt / pptx / txt / md`，上限 10MB |
| `embeddingConfigId` | long | ❌ | 模型配置 ID。传了则上传后自动异步处理；不传则仅上传 |

**curl 示例**：

```bash
curl -X POST http://localhost:8080/knowledge-base/1/document \
  -H "Authorization: Bearer <token>" \
  -F "file=@退款流程.pdf" \
  -F "embeddingConfigId=2"
```

**响应**：

```json
{
  "code": 200,
  "data": {
    "id": 5,
    "kbId": 1,
    "fileName": "退款流程.pdf",
    "fileUrl": "http://localhost:8080/file/a1b2c3d4.pdf",
    "fileSize": 245760,
    "fileType": "pdf",
    "status": 0,
    "chunkCount": null,
    "uploadedBy": 1,
    "createTime": "2026-05-25T12:10:00"
  }
}
```

| `status` 值 | 含义 |
|:---:|------|
| `0` | 处理中 |
| `1` | 就绪（可被检索） |
| `2` | 失败 |

> 上传后立即返回 `status=0`。如果传了 `embeddingConfigId`，后台线程池异步处理，完成后 `status` 变为 `1`。

### 6.2 列出文档

```http
GET /knowledge-base/{kbId}/document
```

**认证**：需登录，需为该知识库成员

**响应**：与上传响应结构相同，数组形式，按创建时间倒序。

### 6.3 查看文档

```http
GET /knowledge-base/{kbId}/document/{docId}
```

**认证**：需登录，需为该知识库成员

### 6.4 删除文档

```http
DELETE /knowledge-base/{kbId}/document/{docId}
```

**认证**：需登录，需 ADMIN 或 BOSS 身份

> 删除数据库记录并尝试清理磁盘文件。  
> ⚠️ 不会自动删除 pgvector 中该文档的向量数据。

---

## 7. RAG 对话

### 7.1 触发文档处理

```http
POST /knowledge-base/{kbId}/document/{docId}/process
```

**认证**：需登录，需 ADMIN 或 BOSS 身份

**请求体**：

```json
{
  "embeddingConfigId": 2
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `embeddingConfigId` | `long` | ✅ | 嵌入模型配置 ID |

> 用于对之前上传时未传 `embeddingConfigId` 的文档手动触发处理，或对处理失败的文档重新处理。

### 7.2 纯检索

```http
POST /knowledge-base/{kbId}/search
```

**认证**：需登录，需为该知识库成员

**请求体**：

```json
{
  "query": "怎么退款？",
  "embeddingConfigId": 2,
  "topK": 5
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `query` | `string` | ✅ | 检索内容 |
| `embeddingConfigId` | `long` | ✅ | 嵌入模型配置 ID |
| `topK` | `int` | ✅ | 返回条数 |

**响应**：

```json
{
  "code": 200,
  "data": [
    {
      "content": "退款流程：1.进入订单详情页点击\"申请退款\"...",
      "metadata": {
        "kb_id": 1,
        "score": 0.92
      }
    },
    {
      "content": "退款条件：购买后7天内且未使用可退款...",
      "metadata": {
        "kb_id": 1,
        "score": 0.87
      }
    }
  ]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `content` | `string` | 文档块文本内容 |
| `metadata` | `object` | 元数据，含 `kb_id` 和 `score`（余弦相似度，范围 0~1） |

### 7.3 RAG 对话（一次性返回）

```http
POST /knowledge-base/{kbId}/chat
```

**认证**：需登录，需为该知识库成员

**请求体**：

```json
{
  "query": "怎么退款？",
  "embeddingConfigId": 2,
  "chatConfigId": 1,
  "topK": 3
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `query` | `string` | ✅ | 问题 |
| `embeddingConfigId` | `long` | ✅ | 嵌入模型配置 ID（用于检索） |
| `chatConfigId` | `long` | ✅ | 对话模型配置 ID（用于生成答案） |
| `topK` | `int` | ❌ | 检索条数，默认 3 |

**响应**：

```json
{
  "code": 200,
  "data": {
    "answer": "根据资料，退款流程如下：1.进入订单详情页点击\"申请退款\"按钮...",
    "sources": [
      {
        "content": "退款流程：1.进入订单详情页点击\"申请退款\"...",
        "metadata": { "kb_id": 1, "score": 0.92 }
      }
    ]
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `answer` | `string` | LLM 生成的答案 |
| `sources` | `array` | 引用的文档块列表（溯源） |

> 如果未找到相关资料，`answer` 返回 `"未找到相关资料。"`，`sources` 为空数组。

### 7.4 RAG 对话（流式 SSE）

```http
POST /knowledge-base/{kbId}/chat/stream
```

**认证**：需登录，需为该知识库成员

**请求体**：与 [7.3 RAG 对话（一次性返回）](#73-rag-对话一次性返回) 相同

**响应**：`text/event-stream`

```
event:data
data:根据

event:data
data:资料

event:data
data:，退款

...
```

每个 `data` 事件包含一段生成的 token。前端可用 `EventSource` API 接收并逐字显示。

**curl 测试**：

```bash
curl -X POST http://localhost:8080/knowledge-base/1/chat/stream \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"query":"怎么退款？","embeddingConfigId":2,"chatConfigId":1}' \
  --no-buffer
```

**JavaScript 前端示例**：

```javascript
const response = await fetch('/knowledge-base/1/chat/stream', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    query: '怎么退款？',
    embeddingConfigId: 2,
    chatConfigId: 1
  })
});

const reader = response.body.getReader();
const decoder = new TextDecoder();

while (true) {
  const { done, value } = await reader.read();
  if (done) break;
  const text = decoder.decode(value);
  // text 格式：event:data\ndata:增量文本\n\n
  // 根据需要解析 SSE 事件
}
```

---

## 8. 幂等 Token

防止表单重复提交（如重复创建知识库）。

### 8.1 获取幂等 Token

```http
GET /idempotent/token
```

**认证**：无需登录

**响应**：

```json
{
  "code": 200,
  "data": "550e8400-e29b-41d4-a716-446655440000"
}
```

**使用方式**：

1. 页面加载时调用此接口获取 token
2. 提交表单时在 Header 中携带：`Idempotent-Token: <token>`
3. 在接口方法上加 `@Idempotent` 注解（需代码层面添加）

> Token 有效期 5 分钟，存储在 Redis 中。已被消费的 token 无法再次使用。

---

## 9. 错误码参考

| HTTP 状态码 | `code` | 场景 | 响应示例 |
|:---:|:---:|------|---------|
| `400` | 400 | 参数校验失败 | `{"code":400,"message":"知识库名称不能为空","data":null}` |
| `400` | 400 | 文件上传校验失败 | `{"code":400,"message":"File extension '.exe' is not allowed","data":null}` |
| `401` | 401 | JWT 为空/无效/过期 | `{"code":401,"message":"Token 已过期","data":null}` |
| `401` | 401 | 无权限访问/操作 | `{"code":401,"message":"权限不足","data":null}` |
| `401` | 401 | 账号或密码错误 | `{"code":401,"message":"账号或密码错误","data":null}` |
| `409` | 409 | 数据已存在 | `{"code":409,"message":"数据已存在","data":null}` |
| `429` | 429 | 触发限流 | `{"code":429,"message":"请求过于频繁，请稍后再试","data":null}` |
| `500` | 500 | 文件 IO 异常 | `{"code":500,"message":"文件上传失败","data":null}` |
| `500` | 500 | 服务器内部错误 | `{"code":500,"message":"服务器内部错误","data":null}` |

---

## 10. 业务枚举

### 10.1 角色（KbRole）

| 值 | code | 权限范围 |
|----|:---:|---------|
| `MEMBER` | 0 | 查看知识库、检索、RAG 对话 |
| `ADMIN` | 1 | MEMBER 权限 + 管理文档/模型配置/普通成员 |
| `BOSS` | 2 | ADMIN 权限 + 管理所有成员 + 删除知识库 |

### 10.2 模型提供商（provider）

| 值 | 说明 | 可用性 |
|----|------|:---:|
| `openai` | OpenAI 官方服务 | 嵌入 ✅ 对话 ✅ 流式 ✅ |
| `custom` | OpenAI 兼容协议（OneAPI、硅基流动、vLLM 等） | 嵌入 ✅ 对话 ✅ 流式 ✅ |
| `ollama` | 本地 Ollama 部署 | 需添加依赖（扩展点已预留） |
| `anthropic` | Anthropic Claude | 仅对话（无嵌入模型） |

### 10.3 文档状态（status）

| 值 | 含义 |
|:---:|------|
| `0` | 处理中 |
| `1` | 就绪（可检索） |
| `2` | 失败 |

### 10.4 支持的文件类型

上传文档支持的扩展名：`pdf`、`doc`、`docx`、`xls`、`xlsx`、`ppt`、`pptx`、`txt`、`md`

单文件上限：10 MB

---

> 在线 Swagger UI：`http://localhost:8080/swagger-ui.html`（需项目运行中）
