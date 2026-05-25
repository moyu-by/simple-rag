# RAG 知识库系统 —— 全流程详解

> 项目：`org.moyu:rag`  
> 技术栈：Spring Boot 4 + Spring AI 1.1.7 + pgvector + OpenAI 兼容协议  

---

## 第一章：三个基本概念

### 1. 向量（Vector）

把一段文字变成一串小数：

```
"怎么退款？"
    ↓ 嵌入模型处理
[0.012, -0.034, 0.087, 0.001, ..., 0.065]   ← 1536 个数
```

意思相近的文字，向量在数学上"距离"也近。

### 2. 嵌入模型（Embedding Model）

把文字变成向量的服务。本项目通过 `ModelFactory` 调用：

```java
// ModelFactory.java 第34-39行
public EmbeddingModel createEmbeddingModel(ModelConfig config) {
    var api = OpenAiApi.builder()
            .apiKey(aesEncryptor.decrypt(config.getApiKey()))  // AES 解密
            .baseUrl(emptyToDefault(config.getBaseUrl(), "https://api.openai.com"))
            .build();
    return new OpenAiEmbeddingModel(api);
}
```

### 3. RAG = 检索 + 增强 + 生成

| 步骤 | 中文 | 干什么 |
|------|------|--------|
| **R**etrieve | 检索 | 从知识库里找到最相关的几段文字 |
| **A**ugment | 增强 | 把这些文字拼到提示词里 |
| **G**enerate | 生成 | 让 LLM 根据拼好的提示词生成答案 |

---

## 第二章：文档入库流水线（上传 → 可检索）

一次性流程：文件上传 → 异步处理 → 向量化 → 可被检索。

### 完整调用链

```
用户 POST 上传 PDF
    │
    ▼
DocumentController.upload()           ──── 第19行
    │
    ▼
DocumentServiceImpl.doUpload()        ──── 第49行
    ├── 1. 写磁盘（同步）
    ├── 2. 写数据库（同步，status=0 处理中）
    └── 3. 抛给线程池（异步）→ 不阻塞 HTTP 响应
            │
            ▼
        DocumentProcessor.process()    ──── 第39行
            ├── ① Tika 解析文本        ──── 第69-74行
            ├── ② TokenTextSplitter 切块 ──── 第83-94行
            ├── ③ EmbeddingModel 向量化  ──── 第97-102行
            ├── ④ VectorStoreService 存库 ──── 第105行
            └── ⑤ 更新 status=1 就绪     ──── 第109-111行
```

### 逐步代码

#### Controller 接收文件

```java
// DocumentController.java 第19-27行
@PostMapping
public Result<DocumentResponse> upload(@PathVariable Long kbId,
                                       @RequestParam("file") MultipartFile file,
                                       @RequestParam(required = false) Long embeddingConfigId) {
    if (embeddingConfigId != null) {
        return Result.ok(documentService.upload(kbId, file, embeddingConfigId));
    }
    return Result.ok(documentService.upload(kbId, file));
}
```

- `embeddingConfigId` 传了 = 上传完立刻异步处理
- 不传 = 只上传不处理，以后手动触发

#### Service 执行同步部分

```java
// DocumentServiceImpl.java 第49-85行
private DocumentResponse doUpload(Long kbId, MultipartFile file, Long embeddingConfigId) {
    requireAdmin(userId, kbId);  // 权限：ADMIN 或 BOSS

    // 1. 磁盘写入（同步，几百毫秒）
    Path uploadDir = Path.of(fileProperties.getStorePath());  // 默认 ./upload
    String fullUrl = FileUploadUtil.uploadFile(file, uploadDir, ...);

    // 2. 数据库写入（同步，几十毫秒）
    Document doc = new Document();
    doc.setKbId(kbId);
    doc.setFileName("退款流程.pdf");
    doc.setFilePath("a1b2c3d4.pdf");  // UUID + 扩展名
    doc.setStatus(0);                  // 0 = 处理中
    documentMapper.insert(doc);

    // 3. 异步触发处理（不阻塞！）
    if (embeddingConfigId != null) {
        final Long finalDocId = doc.getId();
        taskExecutor.execute(() -> {
            documentProcessor.process(kbId, finalDocId, embeddingConfigId);
        });
    }

    return toResponse(doc);  // 立即返回 HTTP 200
}
```

#### 事务包裹的处理核心

```java
// DocumentProcessor.java 第39-63行
public void process(Long kbId, Long docId, Long embeddingConfigId) {
    Document doc = documentMapper.selectById(docId);
    ModelConfig embedConfig = modelConfigMapper.selectById(embeddingConfigId);

    try {
        // ★ 整个处理流程在一个数据库事务里
        transactionTemplate.executeWithoutResult(status -> {
            try {
                doProcess(kbId, doc, embedConfig);
            } catch (Exception e) {
                status.setRollbackOnly();  // 失败 → 全部回滚
                throw new RuntimeException(e);
            }
        });
    } catch (Exception e) {
        markFailed(docId);  // ★ 独立事务标记 status=2（不受回滚影响）
        throw new RuntimeException("文档处理失败: " + e.getMessage(), e);
    }
}
```

**双事务设计**：主事务失败回滚时，`markFailed` 在另一个独立事务里标记 `status=2`，确保失败状态一定落库。

#### doProcess：四步流水线

```java
// DocumentProcessor.java 第65-112行
private void doProcess(Long kbId, Document doc, ModelConfig embedConfig) throws Exception {

    // ① Tika 解析文本
    Path filePath = Path.of(fileProperties.getStorePath(), doc.getFilePath());
    Tika tika = new Tika();
    String rawText;
    try (InputStream is = Files.newInputStream(filePath)) {
        rawText = tika.parseToString(is);   // PDF → 纯文本
    }
    if (rawText.isBlank()) {
        doc.setStatus(2);  // 扫描图片 PDF，无文字层 → 失败
        return;
    }

    // ② 切块（500 token/块）
    TokenTextSplitter splitter = TokenTextSplitter.builder()
            .withChunkSize(500)
            .withMinChunkSizeChars(100)
            .build();
    List<org.springframework.ai.document.Document> chunks = splitter
            .apply(List.of(new org.springframework.ai.document.Document(rawText)));

    // 每块标记来源：知识库ID、文档ID、第几块
    for (int i = 0; i < chunks.size(); i++) {
        chunks.get(i).getMetadata().put("kb_id", kbId);
        chunks.get(i).getMetadata().put("doc_id", docId);
        chunks.get(i).getMetadata().put("chunk_index", i);
    }

    // ③ 向量化（调用嵌入 API）
    EmbeddingModel embeddingModel = modelFactory.createEmbeddingModel(embedConfig);
    List<float[]> embeddings = new ArrayList<>();
    for (var chunk : chunks) {
        var response = embeddingModel.embedForResponse(List.of(chunk.getText()));
        embeddings.add(response.getResults().getFirst().getOutput());
    }

    // ④ 存入 pgvector
    vectorStoreService.store(chunks, embeddings);

    // ⑤ 更新状态：处理成功
    doc.setStatus(1);
    doc.setChunkCount(chunks.size());
    documentMapper.updateById(doc);
}
```

#### VectorStoreService.store：写入 pgvector

```java
// VectorStoreService.java 第22-34行
public void store(List<Document> chunks, List<float[]> embeddings) {
    String sql = """
            INSERT INTO vector_store (content, metadata, embedding)
            VALUES (?, ?::jsonb, ?::vector)
            """;
    List<Object[]> batch = new ArrayList<>();
    for (int i = 0; i < chunks.size(); i++) {
        Document chunk = chunks.get(i);
        String metadataJson = JSONUtil.toJsonStr(chunk.getMetadata());
        batch.add(new Object[]{
            chunk.getText(),       // 文本
            metadataJson,          // JSONB：{"kb_id":"1","doc_id":"5","chunk_index":3}
            embeddings.get(i)      // float[] 向量
        });
    }
    jdbcTemplate.batchUpdate(sql, batch);  // 批量插入
}
```

metadata 的 JSONB 字段是实现多知识库隔离的关键：检索时用 `WHERE metadata->>'kb_id' = ?` 过滤。

---

## 第三章：对话查询流水线（提问 → 答案）

每次对话的流程：问题向量化 → pgvector 检索 → 拼提示词 → LLM 生成答案。

### 完整调用链

```
用户 POST 提问 "怎么退款？"
    │
    ▼
RagController.chat() / chatStream()     ──── 第54行/第69行
    │
    ▼
RagService.retrieve()                   ──── 第36行（共用）
    ├── 1. search()                     ──── 第22行
    │      ├── ModelFactory.createEmbeddingModel()
    │      ├── embeddingModel.embed("怎么退款？")
    │      └── VectorStoreService.search(向量, topK=3)
    │
    └── 2. 拼接 context                 ──── 第40-43行

RagService.chat() / chatStream()        ──── 第47行/第63行
    ├── 3. ModelFactory.createChatModel()
    ├── 4. 构建 Prompt（SystemMessage + UserMessage）
    └── 5. chatModel.call(prompt) / .stream(prompt)
           └── 返回文本 / Flux<String>
```

### 逐步代码

#### Controller：权限校验 + 路由

```java
// RagController.java 第53-65行
@PostMapping("/chat")
public Result<RichChatResponse> chat(@PathVariable Long kbId,
                                     @Valid @RequestBody ChatRequest request) {
    requireMembership(kbId);  // 必须是有成员才能访问
    int topK = request.topK() != null ? request.topK() : 3;
    RagService.ChatResult result = ragService.chat(
        kbId, request.query(),
        request.embeddingConfigId(),   // 用哪个嵌入模型做检索？
        request.chatConfigId(),        // 用哪个对话模型生成答案？
        topK                           // 检索几条？
    );
    return Result.ok(new RichChatResponse(result.answer(), sources));
}
```

`embeddingConfigId` 和 `chatConfigId` 是两个独立的配置——可以用便宜的模型检索，贵的模型生成答案。

#### RagService.retrieve()：检索 + 构建上下文

```java
// RagService.java 第36-44行
private RetrievalResult retrieve(Long kbId, String query,
                                 Long embeddingConfigId, int topK) {
    List<Document> chunks = search(kbId, query, topK, embeddingConfigId);
    if (chunks.isEmpty()) {
        return new RetrievalResult(List.of(), "");  // 没搜到
    }
    String context = chunks.stream()
            .map(Document::getText)
            .collect(java.util.stream.Collectors.joining("\n---\n"));
    return new RetrievalResult(chunks, context);
}
```

#### RagService.search()：文字 → 向量 → 检索

```java
// RagService.java 第22-28行
public List<Document> search(Long kbId, String query, int topK, Long embeddingConfigId) {
    ModelConfig config = modelConfigMapper.selectById(embeddingConfigId);
    EmbeddingModel embeddingModel = modelFactory.createEmbeddingModel(config);
    float[] queryEmbedding = embeddingModel.embed(query);
    // "怎么退款？" → [0.012, -0.034, 0.087, ...]
    return vectorStoreService.search(kbId, queryEmbedding, topK);
}
```

#### VectorStoreService.search()：pgvector 余弦距离检索

```java
// VectorStoreService.java 第37-58行
public List<Document> search(Long kbId, float[] queryEmbedding, int topK) {
    String sql = """
            SELECT content, metadata,
                   1 - (embedding <=> ?::vector) AS similarity
            FROM vector_store
            WHERE metadata->>'kb_id' = ?          ← 只搜当前知识库
            ORDER BY embedding <=> ?::vector       ← 余弦距离升序
            LIMIT ?
            """;
    return jdbcTemplate.query(sql, ps -> {
        ps.setObject(1, queryEmbedding);
        ps.setObject(2, String.valueOf(kbId));
        ps.setObject(3, queryEmbedding);
        ps.setInt(4, topK);
    }, (rs, rowNum) -> {
        Document doc = new Document(rs.getString("content"));
        doc.getMetadata().put("score", rs.getDouble("similarity"));
        return doc;
    });
}
```

#### RagService.chat()：拼提示词 + 调用 LLM

```java
// RagService.java 第47-59行
public ChatResult chat(Long kbId, String query, Long embeddingConfigId,
                       Long chatConfigId, int topK) {
    RetrievalResult rr = retrieve(kbId, query, embeddingConfigId, topK);
    if (rr.context.isEmpty()) {
        return new ChatResult("未找到相关资料。", List.of());
    }

    ModelConfig chatConfig = modelConfigMapper.selectById(chatConfigId);
    ChatModel chatModel = modelFactory.createChatModel(chatConfig);

    var response = chatModel.call(new Prompt(List.of(
        systemMsg(rr.context),        // SystemMessage：角色 + 资料
        new UserMessage(query)        // UserMessage：用户问题
    )));

    return new ChatResult(
        response.getResult().getOutput().getText(),  // 答案
        rr.chunks                                     // 引用来源
    );
}
```

#### systemMsg()：增强（Augment）步骤

```java
// RagService.java 第95-103行
private SystemMessage systemMsg(String context) {
    return new SystemMessage("""
            你是一个知识库助手。请根据以下资料回答用户的问题。
            如果资料中没有相关信息，请如实告知，不要编造。

            资料：
            %s
            """.formatted(context));
}
```

最终发送给 LLM 的完整 Prompt：

```
System: 你是一个知识库助手。请根据以下资料回答用户的问题。
  如果资料中没有相关信息，请如实告知，不要编造。

  资料：
  退款流程：1.进入订单详情页点击"申请退款"
  ---
  退款条件：购买后7天内且未使用可退款
  ---
  退款到账时间：支付宝/微信 1-3个工作日

User: 怎么退款？
```

---

## 第四章：流式对话（SSE）

前端可以像 ChatGPT 一样逐字显示。

```java
// RagController.java 第68-92行
@PostMapping("/chat/stream")
public SseEmitter chatStream(@PathVariable Long kbId,
                             @Valid @RequestBody ChatRequest request) {
    requireMembership(kbId);
    SseEmitter emitter = new SseEmitter(300_000L);  // 5分钟超时
    Flux<String> flux = ragService.chatStream(kbId, request.query(),
            request.embeddingConfigId(), request.chatConfigId(), topK);

    flux.subscribe(
        token -> emitter.send(SseEmitter.event().data(token)),  // 每来一个token推给前端
        emitter::completeWithError,   // 出错 → 结束
        emitter::complete             // 完成 → 结束
    );
    emitter.onTimeout(emitter::complete);
    return emitter;
}

// RagService.java 第63-77行
public Flux<String> chatStream(Long kbId, String query, ...) {
    RetrievalResult rr = retrieve(kbId, query, embeddingConfigId, topK);
    if (rr.context.isEmpty()) {
        return Flux.just("未找到相关资料。");
    }
    OpenAiChatModel chatModel = modelFactory.createStreamingChatModel(chatConfigId);
    return chatModel.stream(new Prompt(List.of(
            systemMsg(rr.context), new UserMessage(query))))
            .map(r -> r.getResult().getOutput().getText());
}
```

SSE（Server-Sent Events）：一次 HTTP 请求 → 保持连接 → 多次推送 → 自己结束或超时。

---

## 第五章：数据流全景图

### 文档入库

```
┌─ 同 步 ────────────────────────────┐  ┌─ 异 步（线程池）───────────┐
│                                     │  │                            │
│  POST /knowledge-base/1/document    │  │  DocumentProcessor.process()│
│  MultipartFile: 退款流程.pdf         │  │                            │
│      │                              │  │  Tika → TokenTextSplitter  │
│      ▼                              │  │   PDF→文本    500字/块     │
│  FileUploadUtil.uploadFile()        │  │           │                │
│      │                              │  │           ▼                │
│      ▼                              │  │  ModelFactory              │
│  磁盘: ./upload/a1b2c3d4.pdf        │  │  createEmbeddingModel()    │
│      │                              │  │  → API调用: 文本→向量      │
│      ▼                              │  │           │                │
│  数据库: document 表                 │  │           ▼                │
│  id=5, kb_id=1, status=0            │  │  VectorStoreService.store()│
│      │                              │  │  INSERT INTO vector_store  │
│      └── taskExecutor.execute() ────┘  │           │                │
│                                         │           ▼                │
│  ←─ HTTP 200（不等待处理完成）           │  UPDATE status=1（就绪）     │
└─────────────────────────────────────────┴────────────────────────────┘
```

### 对话查询

```
用户提问: "怎么退款？"
    │
    ▼
RagService.retrieve()
    │
    ├──→ RagService.search(kbId, query, topK=3)
    │       ├── ModelFactory.createEmbeddingModel(configId)
    │       ├── embeddingModel.embed("怎么退款？")
    │       └── VectorStoreService.search(kbId=1, 向量, topK=3)
    │            └── SELECT ... ORDER BY embedding <=> query_vector LIMIT 3
    │
    ├──→ 返回 3 个 Document:
    │    {text:"退款流程...", score:0.92}
    │    {text:"退款条件...", score:0.87}
    │    {text:"退款到账...", score:0.81}
    │
    └──→ 拼 context: "退款流程：...\n---\n退款条件：...\n---\n退款到账时间：..."
              │
              ▼
RagService.chat()
    ├── ModelFactory.createChatModel(chatConfigId)
    ├── Prompt: SystemMessage(资料) + UserMessage(问题)
    └── chatModel.call(prompt)
            │
            ▼
        返回: ChatResult(
          answer: "根据资料，退款流程如下..."  ← LLM生成
          chunks: [3个Document]               ← 引用来源
        )
```

---

## 第六章：异步处理详解

### 为什么异步？

文档处理太慢：Tika 解析 → 切 200 块 → 每块调嵌入 API（~0.2秒）→ 总耗时 40+ 秒。如果同步等，用户体验极差。

### 线程池配置

```java
// ThreadPoolConfig.java 第21-38行
@Bean("taskExecutor")
public Executor taskExecutor() {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);      // 平时 5 个线程
    executor.setMaxPoolSize(20);      // 忙时最多 20 个
    executor.setQueueCapacity(100);   // 排队上限 100
    executor.setRejectedExecutionHandler(new CallerRunsPolicy());
    // 满了 → 由 HTTP 请求线程自己执行（不丢任务）
    return executor;
}
```

### 异步时序图

```
时间轴 →

HTTP 线程：
  [T+0ms]   接收文件
  [T+50ms]  写磁盘 ✅
  [T+60ms]  INSERT document (status=0) ✅
  [T+70ms]  taskExecutor.execute(() -> process())  ← 不阻塞
  [T+71ms]  返回 HTTP 200 ──→ 用户："上传成功！"

task-1 线程（异步）：
  [T+72ms]     开始处理
  [72ms~500ms]  Tika 解析 PDF → 文本
  [500ms~600ms] 切块（内存操作，很快）
  [600ms~40s]   嵌入 API 调用 ← 最慢（200块 × 0.2秒）
  [40s~41s]     INSERT vector_store（批量）
  [T+41s]       UPDATE status=1 ✅ "就绪，200块"
```

### 双事务设计

```
主事务（处理流程）                    独立事务（失败标记）
    │                                      │
    ├── Tika 解析                          │
    ├── 切块                               │
    ├── 嵌入 API                            │
    ├── INSERT vector_store                │
    └── UPDATE status=1                    │
         │                                 │
         ├── 成功 → status=1               │
         └── 失败 → 回滚 ─────────────────→ markFailed(docId)
                                              │
                                          UPDATE status=2
                                          ↑ 不受主回滚影响
```

### 状态机

```
上传文件 → status=0（处理中）
              │
              ├── rawText为空 → status=2（失败：扫描图片PDF）
              ├── 嵌入API报错 → 回滚 + markFailed → status=2
              └── 全部成功 → status=1（就绪，可检索）
```

---

## 第七章：Spring AI 的本质

**Spring AI 把所有 LLM 调用统一成标准接口，换供应商不改代码。**

| 需求 | Spring AI 接口 | 项目对应 |
|------|---------------|---------|
| 文字→向量 | `EmbeddingModel.embed(text)` | `ModelFactory.createEmbeddingModel()` |
| 批量向量化 | `EmbeddingModel.embedForResponse(texts)` | `DocumentProcessor.doProcess()` |
| 对话生成 | `ChatModel.call(prompt)` | `RagService.chat()` |
| 流式生成 | `ChatModel.stream(prompt)` → `Flux<ChatResponse>` | `RagService.chatStream()` |
| 文本切块 | `TokenTextSplitter` | `DocumentProcessor.doProcess()` |

`OpenAiEmbeddingModel.embed("hello")` 底层实际做的事：

```
HTTP POST https://api.openai.com/v1/embeddings
Body: {"input":"hello","model":"text-embedding-ada-002"}
  ↓
解析 JSON 响应: {"data":[{"embedding":[0.012, -0.034, ...]}]}
  ↓
返回 float[]
```

你只写 `embeddingModel.embed(query)` 一行，Spring AI 做了 HTTP 调用、JSON 解析、错误重试。

---

## 引入的依赖一览

```xml
spring-ai-client-chat           → ChatModel 接口 + Prompt/Message 模型
spring-ai-openai                → OpenAiApi + OpenAiChatModel + OpenAiEmbeddingModel
spring-ai-tika-document-reader  → Tika 集成（项目直接用了 Apache Tika）
spring-ai-starter-model-anthropic → Claude 对话模型（无嵌入）
```

---

## 安全链路

```
传输中：   RSA 公钥加密（encrypted: true） + HTTPS/TLS
存储时：   AES-256 加密（数据库 api_key 字段）
使用中：   内存明文（调用 LLM API 时需要明文）
```

完整链路：前端 RSA 加密 → 后端 RSA 解密 → AES 加密落库 → ModelFactory AES 解密 → 构建 HTTP 请求 → LLM API。
