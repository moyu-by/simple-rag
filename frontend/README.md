# RAG 知识库系统 — 前端

Vue 3 + TypeScript + Vite + Element Plus 构建的管理后台。

## 技术栈

| 组件 | 用途 |
|------|------|
| Vue 3 (Composition API) | UI 框架 |
| TypeScript | 类型安全 |
| Vite | 构建工具 |
| Element Plus | 组件库（表格、表单、对话框等） |
| Pinia | 状态管理（用户认证） |
| Axios | HTTP 请求（拦截器统一处理 401 跳登录） |
| Vue Router | 前端路由 |

## 目录结构

```
frontend/src/
├── api/              # API 调用层（Axios 封装）
│   ├── request.ts    # Axios 实例 + 拦截器
│   ├── chat.ts       # RAG 对话 + SSE 流式 + 聊天历史
│   ├── document.ts   # 文档上传/列表/删除
│   ├── knowledgeBase.ts
│   ├── member.ts     # 成员管理
│   └── modelConfig.ts
├── stores/
│   └── auth.ts       # 用户认证状态（Pinia）
├── views/
│   ├── Home.vue      # 知识库列表页
│   ├── Login.vue     # 登录页
│   ├── Register.vue  # 注册页
│   ├── KbDetail.vue  # 知识库详情（文档/模型/成员管理）
│   └── KbChat.vue    # RAG 对话页（流式 SSE + 历史记录）
├── router/
│   └── index.ts      # 路由定义
├── App.vue
└── main.ts
```

## 关键交互流程

### RAG 对话
```
用户在 KbChat.vue 输入问题
  → sendMessage() 推入 messages 数组
  → chatApi.chatStream() POST /api/knowledge-base/{id}/chat/stream
  → 后端返回 ReadableStream（SSE）
  → 逐行解析 data: 事件，逐 token 更新 UI
  → 对话完成后自动保存到后端（chat_message 表）
  → 页面刷新后自动加载历史记录
```

### 文档上传
```
KbDetail.vue 中：
  单个文件 → el-upload 组件直接 POST
  文件夹   → webkitdirectory input → 遍历文件循环调 docApi.upload()
  批量处理 → 勾选多行 → 选嵌入模型 → 逐个调 process API
```

### 认证
```
登录成功 → token 存入 localStorage
  → Axios 请求拦截器自动注入 Authorization header
  → 401 响应时拦截器清除 token 并跳转登录页
```

## 开发

```bash
# 安装依赖
npm install

# 启动开发服务器（默认 5173，代理 /api → localhost:8080）
npm run dev

# 构建
npm run build
```

## 与后端协作

- 开发时 Vite 代理 `/api` → `http://localhost:8080`（配置在 `vite.config.ts`）
- 生产部署需 nginx 或后端统一提供静态资源
