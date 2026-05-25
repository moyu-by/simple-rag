# Plan: RAG 知识库前端

## 状态：已完成 ✅

## 阶段

- [x] 方案设计 — 用户确认
- [x] 项目脚手架搭建
- [x] 依赖安装
- [x] API 层封装
- [x] 认证 & 路由
- [x] 布局 & 响应式导航栏
- [x] 页面开发
- [x] 构建验证
- [x] 模型类型区分 (EMBEDDING/CHAT)
- [x] Anthropic 对话支持
- [x] Spring AI 升级到 2.0.0-M7（兼容 Spring Boot 4）
- [x] 去除 openai 单独选项，统一为 custom
- [x] 修复 AES 密钥长度 / 上传路径 / TIMESTAMPTZ 等问题

## 技术栈

- Vue 3 + TypeScript + Vite
- Element Plus
- Vue Router 4 + Pinia
- Axios + JSEncrypt

## 文件清单

| 文件 | 说明 |
|------|------|
| `vite.config.ts` | Vite 配置，含 `/api` 代理到 `localhost:8080` |
| `src/main.ts` | 入口，挂载 Element Plus / Pinia / Router |
| `src/App.vue` | 根组件，仅含 `<router-view>` |
| `src/style.css` | 全局样式 + 移动端适配 |
| `src/types/jsencrypt.d.ts` | JSEncrypt 类型声明 |
| `src/utils/crypto.ts` | RSA 公钥获取 + 加密工具 |
| `src/api/request.ts` | Axios 实例，JWT 拦截，错误处理 |
| `src/api/auth.ts` | 认证 API |
| `src/api/knowledgeBase.ts` | 知识库 CRUD |
| `src/api/document.ts` | 文档管理 API |
| `src/api/modelConfig.ts` | 模型配置 API |
| `src/api/member.ts` | 成员管理 API |
| `src/api/chat.ts` | 对话/检索/SSE 流式 API |
| `src/stores/auth.ts` | 认证状态 Pinia Store |
| `src/router/index.ts` | 路由 + 登录守卫 |
| `src/layout/MainLayout.vue` | 主布局，含响应式导航栏 |
| `src/views/Login.vue` | 登录页（RSA 加密） |
| `src/views/Register.vue` | 注册页 |
| `src/views/Home.vue` | 首页 — 知识库卡片列表 + 创建/编辑 |
| `src/views/KbDetail.vue` | 知识库详情 — 文档/模型/成员 Tab |
| `src/views/KbChat.vue` | RAG 对话 — SSE 流式输出 |

## 导航栏响应式策略

- 桌面端（≥768px）：水平导航 + 用户下拉菜单
- 移动端（<768px）：折叠为汉堡按钮 → 打开侧边抽屉
- 使用 CSS 媒体查询 + JS `resize` 监听
