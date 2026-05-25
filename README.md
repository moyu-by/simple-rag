# RAG 知识库系统

基于 Spring Boot 4 + Spring AI + pgvector 的 RAG（检索增强生成）知识库系统。上传文档 → 自动向量化 → 对话问答，每个知识库可独立配置 LLM 提供商。

## 架构总览

```
┌──────────────────────────────────────────────────────────────────┐
│                         前端 / API 调用方                         │
└────────────┬───────────────┬───────────────┬─────────────────────┘
             │               │               │
             ▼               ▼               ▼
     ┌──────────┐   ┌──────────────┐   ┌──────────┐
     │ 知识库管理 │   │  文档上传      │   │ RAG 对话 │
     │ CRUD +   │   │  异步处理队列  │   │ 检索+生成 │
     │ 成员权限  │   │               │   │ (SSE流式) │
     └────┬─────┘   └──────┬───────┘   └────┬─────┘
          │                │                │
          ▼                ▼                ▼
     ┌─────────────────────────────────────────────┐
     │              ModelFactory                    │
     │  根据 provider 动态构建 Embedding/Chat 模型   │
     │  支持 OpenAI 协议 + 兼容 API (OneAPI/vLLM)   │
     └────────────────┬────────────────────────────┘
                      │
          ┌───────────┴───────────┐
          ▼                       ▼
   ┌─────────────┐        ┌─────────────┐
   │ 嵌入模型 API │        │ 对话模型 API │
   │ (向量化文字) │        │ (生成答案)   │
   └──────┬──────┘        └─────────────┘
          │
          ▼
   ┌─────────────┐
   │  pgvector    │
   │  向量相似度   │
   │  检索        │
   └─────────────┘
```

**一条请求的完整旅程**：

```
用户提问："怎么退款？"
  │
  ▼
1. 嵌入模型把问题变成 1536 维向量
  │
  ▼
2. pgvector 查余弦距离最近的 5 个文档块
  │
  ▼
3. 拼成提示词：
   "你是知识库助手。资料：...[退款流程说明]...  用户问题：怎么退款？"
  │
  ▼
4. 对话模型生成答案 → 流式返回前端逐字显示
```

## 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 4.0.6 | 应用框架 |
| Java | 25 | 语言 |
| Spring AI | 1.1.7 | OpenAI 聊天/嵌入模型接入 |
| PostgreSQL + pgvector | 16+ | 数据库 + 向量存储/检索 |
| MyBatis-Plus | 3.5.16 | ORM（分页、自动填充） |
| Redis | 7.x | 令牌桶限流、幂等校验 |
| JWT (jjwt) | 0.13.0 | 用户认证 |
| Apache Tika | 3.1.0 | 文档解析（PDF/Word/PPT/TXT） |
| Hutool | 5.8.44 | 工具库（RSA/AES 加密、JSON 等） |
| SpringDoc | 3.0.3 | Swagger UI |
| Bouncy Castle | 1.83 | 加密算法支持 |
| Lombok | 1.18+ | 代码简化 |

## 快速开始

### 1. 环境准备

- **Java 25**（Spring Boot 4 要求 Java 24+）
- **PostgreSQL 16+** + pgvector 扩展
- **Redis 7+**
- **Maven 3.9+**

### 2. 安装 pgvector 扩展

```bash
# 连接 rag 数据库
psql -h localhost -U postgres -d rag

# 安装扩展
CREATE EXTENSION IF NOT EXISTS vector;

# 验证
SELECT extname FROM pg_extension;
```

### 3. 初始化数据库

执行 `src/main/resources/db/init.sql`：

```bash
psql -h localhost -U postgres -d rag -f src/main/resources/db/init.sql
```

### 4. 配置环境

```bash
# 复制配置模板
cp src/main/resources/application-dev.yml.example src/main/resources/application-dev.yml

# 编辑 application-dev.yml，填入真实配置
vim src/main/resources/application-dev.yml
```

必填项：

```yaml
# 数据库
env-database:
  url: jdbc:postgresql://localhost:5432/rag
  username: postgres
  password: your_password

# JWT 签名密钥（openssl rand -hex 32 生成）
env-jwt:
  key: <64位十六进制密钥>

# AES 加密密钥（openssl rand -base64 32 生成）
env-aes:
  key: <Base64编码的32字节密钥>

# RSA 密钥对（用于登录密码 + API Key 传输加密）
# 放入 src/main/resources/key/private.pem 和 public.pem
```

### 5. 生成 RSA 密钥

```bash
mkdir -p src/main/resources/key
openssl genpkey -algorithm RSA -out src/main/resources/key/private.pem -pkeyopt rsa_keygen_bits:2048
openssl rsa -pubout -in src/main/resources/key/private.pem -out src/main/resources/key/public.pem
```

### 6. 启动

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
# 或
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## 核心流程：RAG 是怎么工作的？

### 文档入库流程（一次性）

```
上传 PDF/Word/TXT
    │
    ▼
FileUploadUtil.uploadFile()        ← 存磁盘，返回 UUID 文件名
    │
    ▼
documentMapper.insert(doc)         ← 写入 document 表（status=0 处理中）
    │
    ▼
taskExecutor.execute(() -> {       ← 异步线程池，不阻塞 HTTP 响应
    │
    ▼
  Tika 解析文本                    ← PDF/Word → 纯文本
    │
    ▼
  TokenTextSplitter 切块           ← 500字符/块，上下文重叠
    │
    ▼
  EmbeddingModel.embedForResponse() ← 调用嵌入 API，每块 → 1536维向量
    │
    ▼
  存 pgvector                      ← INSERT INTO vector_store
    │
    ▼
  status=1 就绪                    ← 对话时可被检索到
})
```

### 对话查询流程（每次对话）

```
用户提问
    │
    ▼
EmbeddingModel.embed(问题)          ← 问题 → 向量
    │
    ▼
pgvector 余弦距离检索               ← SELECT ... ORDER BY embedding <=> query_vector
    │
    ▼
取出 Top-K 文档块                   ← 语义最相关的 K 段文字
    │
    ▼
拼成 System Prompt                 ← "你是助手，资料：...[chunks]..."
    │
    ▼
ChatModel 生成答案                  ← 调用 LLM API（支持流式 SSE）
    │
    ▼
返回：答案 + 引用来源               ← 前端可展示"答案来自哪些文档"
```

## API 接口

### 认证（无需登录）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/auth/public-key` | 获取 RSA 公钥（前端加密密码/API Key 用） |
| POST | `/auth/register` | 注册（密码用 RSA 公钥加密） |
| POST | `/auth/login` | 登录 → 返回 JWT token |

### 知识库管理（需登录）

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/knowledge-base` | 创建知识库 | 登录即可 |
| GET | `/knowledge-base` | 列出我参与的知识库 | 登录即可 |
| GET | `/knowledge-base/{id}` | 查看知识库详情 | 成员 |
| PUT | `/knowledge-base/{id}` | 修改知识库信息 | ADMIN+ |
| DELETE | `/knowledge-base/{id}` | 删除知识库（级联删除） | BOSS |

### 成员管理

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/knowledge-base/{kbId}/member` | 列出成员 | 成员 |
| POST | `/knowledge-base/{kbId}/member` | 添加成员 | ADMIN+ |
| PUT | `/knowledge-base/{kbId}/member/{userId}` | 修改成员角色 | ADMIN+ |
| DELETE | `/knowledge-base/{kbId}/member/{userId}` | 移除成员 | ADMIN+ |

### 模型配置

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/knowledge-base/{kbId}/model-config` | 列出模型配置 | 成员 |
| POST | `/knowledge-base/{kbId}/model-config` | 添加模型配置 | ADMIN+ |
| PUT | `/knowledge-base/{kbId}/model-config/{id}` | 修改模型配置 | ADMIN+ |
| DELETE | `/knowledge-base/{kbId}/model-config/{id}` | 删除模型配置 | ADMIN+ |

### 文档管理

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/knowledge-base/{kbId}/document` | 上传文档（可选自动处理） | ADMIN+ |
| GET | `/knowledge-base/{kbId}/document` | 列出文档 | 成员 |
| GET | `/knowledge-base/{kbId}/document/{id}` | 查看文档详情 | 成员 |
| DELETE | `/knowledge-base/{kbId}/document/{id}` | 删除文档 | ADMIN+ |

### RAG 对话

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/knowledge-base/{kbId}/search` | 纯检索（返回文档块） | 成员 |
| POST | `/knowledge-base/{kbId}/chat` | RAG 对话（一次返回） | 成员 |
| POST | `/knowledge-base/{kbId}/chat/stream` | RAG 对话（SSE 流式） | 成员 |
| POST | `/knowledge-base/{kbId}/document/{id}/process` | 手动触发文档处理 | ADMIN+ |

### 接口示例

```bash
# 1. 获取 RSA 公钥
curl http://localhost:8080/auth/public-key

# 2. 注册（前端 RSA 加密密码）
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"account":"test","password":"<RSA加密后的密码>","encrypted":true}'

# 3. 登录 → 拿到 token
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"account":"test","password":"<RSA加密后的密码>","encrypted":true}'

# 4. 后续请求带上 token
curl -H "Authorization: Bearer <token>" \
  http://localhost:8080/knowledge-base

# 5. RAG 流式对话
curl -X POST http://localhost:8080/knowledge-base/1/chat/stream \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"query":"怎么退款？","embeddingConfigId":2,"chatConfigId":1}'
```

## 安全体系

### 加密分层

```
              ┌──── 传输中 ────┬──── 存储时 ────┬──── 使用中 ────┐
登录密码       │  RSA 公钥加密  │  BCrypt 哈希   │  不可逆        │
模型 API Key  │  RSA 公钥加密  │  AES 对称加密  │  内存明文      │
             │ (encrypted:true)│  (自动)        │  (调用LLM时)   │
```

### 三层防护

| 层 | 机制 | 保护什么 |
|---|------|---------|
| 传输层 | HTTPS/TLS + RSA 公钥加密 | 密码和 API Key 不在网络中明文传输 |
| 存储层 | AES-256 加密（数据库字段） | 即使数据库泄露，API Key 也是密文 |
| 认证层 | JWT Token + 知识库角色权限 | 每个操作都校验用户是否有权限 |

### API Key 安全链路

```
前端拿公钥 → RSA加密 apiKey → POST 请求
                                     ↓
后端 resolveApiKey():  RSA私钥解密 → AES加密 → 存入DB
                                     ↓
ModelFactory:  从DB读 → AES解密 → 构建OpenAiApi（内存明文 → 调用LLM）
```

## 项目结构

```
src/main/java/org/moyu/rag/
├── RagApplication.java          # 启动类
├── annotation/                  # 自定义注解
│   ├── Idempotent.java         # 幂等校验
│   ├── NoLoginRequired.java    # 跳过 JWT 校验
│   └── RateLimit.java          # 接口限流
├── aspect/                      # 切面
│   ├── IdempotentAspect.java   # Redis 原子判重
│   └── RateLimitAspect.java    # Lua 令牌桶限流
├── common/                      # 通用类
│   ├── ContextUtil.java        # ThreadLocal 获取当前用户
│   ├── PageResult.java         # 分页结果封装
│   ├── Result.java             # 统一响应体
│   └── UserContext.java        # 用户上下文
├── config/                      # 配置类
│   ├── FileProperties.java     # 文件上传配置
│   ├── JacksonConfig.java      # JSON 序列化配置
│   ├── JwtProperties.java      # JWT 配置
│   ├── MybatisPlusConfig.java  # MyBatis-Plus 配置
│   ├── RedisConfig.java        # Redis 序列化配置
│   ├── ThreadPoolConfig.java   # 异步线程池（核心5/最大20/队列100）
│   └── WebMvcConfig.java       # CORS + 拦截器 + 文件映射
├── controller/                  # 接口层
│   ├── AuthController.java     # 登录/注册/公钥
│   ├── DocumentController.java # 文档上传/列表/删除
│   ├── KbMemberController.java # 成员管理
│   ├── KnowledgeBaseController.java # 知识库 CRUD
│   ├── ModelConfigController.java   # 模型配置 CRUD
│   ├── RagController.java      # RAG 检索/对话/流式
│   └── TokenController.java    # 幂等 token
├── dto/                         # 数据传输对象
├── entity/                      # 数据库实体
│   ├── Document.java           # 文档表
│   ├── KbMembership.java       # 知识库成员关系表
│   ├── KnowledgeBase.java      # 知识库表
│   ├── ModelConfig.java        # 模型配置表
│   └── User.java               # 用户表
├── enums/
│   └── KbRole.java             # 库内角色：MEMBER/ADMIN/BOSS
├── exception/                   # 异常和全局处理
│   ├── AuthException.java
│   ├── FileUploadException.java
│   ├── GlobalExceptionHandler.java
│   ├── JwtException.java
│   └── RateLimitException.java
├── interceptor/
│   └── JwtInterceptor.java     # JWT 认证拦截器
├── mapper/                      # MyBatis-Plus Mapper
├── service/                     # 业务层
│   ├── DocumentProcessor.java  # ★ 文档处理流水线（解析→切块→向量化）
│   ├── DocumentService.java    # 文档上传/列表/删除接口
│   ├── ModelFactory.java       # ★ 动态模型工厂（provider 分支）
│   ├── RagService.java         # ★ RAG 编排层（检索→增强→生成）
│   ├── VectorStoreService.java # ★ pgvector 向量存储/检索
│   ├── AuthService.java        # 登录/注册服务
│   ├── KbMemberService.java    # 成员管理服务
│   ├── KnowledgeBaseService.java # 知识库管理服务
│   ├── ModelConfigService.java # 模型配置服务
│   └── impl/                   # 服务实现类
└── utils/                       # 工具类
    ├── AesEncryptor.java       # AES 加密/解密（API Key 数据库保护）
    ├── FileUploadUtil.java     # 通用文件上传（校验 + 原子写入）
    ├── JwtUtil.java            # JWT 签发/解析
    └── RsaUtil.java            # RSA 加密/解密（密码 + API Key 传输保护）
```

## 扩展指南

### 接入新的嵌入模型提供商

在 `ModelFactory` 的 switch 中添加新分支：

```java
// 例如接入 Ollama 本地模型
// 1. pom.xml 加依赖: spring-ai-ollama
// 2. ModelFactory.createEmbeddingModel() 加 case:
case "ollama" -> {
    var api = new OllamaApi(config.getBaseUrl());
    yield new OllamaEmbeddingModel(api,
        OllamaEmbeddingOptions.builder().model(config.getModelName()).build());
}
```

然后在 `model_config` 表插入配置即可使用。

### 添加新角色

在 `KbRole` 枚举加值，`atLeast()` 自动生效：

```java
public enum KbRole {
    MEMBER(0, "群员"),
    ADMIN(1, "管理员"),
    CUSTOM_ADMIN(2, "自定义管理员"),  // 新增
    BOSS(3, "库主");                 // code 改为 3
}
```

### 自定义限流规则

```java
// 发送验证码：1秒1个，无突发
@RateLimit(ratePerSecond = 1, maxCapacity = 1)

// 查询接口：每秒100个，突发200
@RateLimit(ratePerSecond = 100, maxCapacity = 200)
```

### 文件类型白名单扩展

```java
// FileUploadUtil 中已内置 IMAGE_EXTENSIONS 和 DOCUMENT_EXTENSIONS
// 自定义新增类型：
FileUploadUtil.UploadConfig config = UploadConfig.builder()
    .allowedExtensions(Set.of("mp4", "avi", "mov"))
    .maxFileSize(100 * 1024 * 1024)  // 视频 100MB
    .build();
```

## 角色权限矩阵

| 操作 | MEMBER（群员） | ADMIN（管理员） | BOSS（库主） |
|------|:---:|:---:|:---:|
| 查看知识库信息 | ✅ | ✅ | ✅ |
| 检索 / RAG 对话 | ✅ | ✅ | ✅ |
| 查看成员列表 | ✅ | ✅ | ✅ |
| 修改知识库信息 | ❌ | ✅ | ✅ |
| 上传文档 | ❌ | ✅ | ✅ |
| 管理模型配置 | ❌ | ✅ | ✅ |
| 添加/移除成员 | ❌ | ⚠️ 仅群员 | ✅ |
| 修改成员角色 | ❌ | ⚠️ 仅群员 | ✅ |
| 删除知识库 | ❌ | ❌ | ✅ |

## 数据库表

| 表 | 说明 |
|---|------|
| `user` | 系统用户（账号、密码哈希、显示名） |
| `knowledge_base` | 知识库（名称、描述、所有者、状态） |
| `kb_membership` | 知识库成员关系（user_id + kb_id + 角色） |
| `model_config` | 模型配置（provider、base_url、AES加密的 api_key、model_name） |
| `document` | 上传文档（文件名、路径、状态、chunk 数） |
| `vector_store` | 向量存储（文本块 + JSONB 元数据 + 向量） |

## 默认配置一览

```yaml
server.port: 8080                     # 服务端口
file.store-path: ./upload             # 文件存储目录
jwt.ttl: 604800000                    # Token 7 天过期
thread-pool: 核心5/最大20/队列100      # 异步处理线程池
rate-limit: 默认需要登录用户           # 未登录按 IP 限流
```

## 许可证

MIT
