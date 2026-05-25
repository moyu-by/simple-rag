-- ==================== RAG 系统初始化 DDL ====================
-- 数据库: PostgreSQL
-- 说明: 不使用逻辑删除，不使用物理外键约束

-- ==================== 1. 用户表 ====================
CREATE TABLE "user" (
    id               BIGSERIAL       PRIMARY KEY,
    account          VARCHAR(100)    NOT NULL UNIQUE,
    password_hash    VARCHAR(255)    NOT NULL,
    display_name     VARCHAR(100)    NOT NULL DEFAULT '',
    last_login_at    TIMESTAMP,
    create_time      TIMESTAMP     NOT NULL DEFAULT now(),
    update_time      TIMESTAMP     NOT NULL DEFAULT now()
);

COMMENT ON TABLE "user" IS '系统用户';

-- ==================== 2. 知识库表 ====================
CREATE TABLE knowledge_base (
    id               BIGSERIAL       PRIMARY KEY,
    name             VARCHAR(200)    NOT NULL,
    description      TEXT,
    owner_id         BIGINT          NOT NULL,
    status           SMALLINT        NOT NULL DEFAULT 1,   -- 0=停用, 1=启用
    create_time      TIMESTAMP     NOT NULL DEFAULT now(),
    update_time      TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE INDEX idx_kb_owner ON knowledge_base(owner_id);

COMMENT ON TABLE knowledge_base IS '知识库';
COMMENT ON COLUMN knowledge_base.owner_id IS '创建者ID，即该知识库的BOSS';

-- ==================== 3. 知识库成员关系 ====================
CREATE TABLE kb_membership (
    id               BIGSERIAL       PRIMARY KEY,
    user_id          BIGINT          NOT NULL,
    kb_id            BIGINT          NOT NULL,
    role_in_kb       SMALLINT        NOT NULL DEFAULT 0,   -- 0=MEMBER, 1=ADMIN, 2=BOSS
    create_time      TIMESTAMP     NOT NULL DEFAULT now(),
    UNIQUE (user_id, kb_id)
);
CREATE INDEX idx_membership_user ON kb_membership(user_id);
CREATE INDEX idx_membership_kb   ON kb_membership(kb_id);

COMMENT ON TABLE kb_membership IS '知识库成员关系';
COMMENT ON COLUMN kb_membership.role_in_kb IS '库内角色: 0=群员, 1=管理员, 2=BOSS(库主)';

-- ==================== 4. 模型配置表 ====================
-- 支持 OpenAI、Anthropic 及兼容 API
CREATE TABLE model_config (
    id               BIGSERIAL       PRIMARY KEY,
    kb_id            BIGINT          NOT NULL,
    name             VARCHAR(100)    NOT NULL,
    provider         VARCHAR(50)     NOT NULL,              -- openai / anthropic / azure / custom
    base_url         VARCHAR(500),                          -- 兼容API地址，标准API可为空
    api_key          TEXT            NOT NULL,              -- 加密存储
    model_name       VARCHAR(200)    NOT NULL,
    parameters       JSONB           DEFAULT '{}',
    model_type       VARCHAR(20)     NOT NULL DEFAULT 'CHAT',  -- EMBEDDING / CHAT
    is_active        BOOLEAN         NOT NULL DEFAULT true,
    created_by       BIGINT          NOT NULL,
    create_time      TIMESTAMP     NOT NULL DEFAULT now(),
    update_time      TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE INDEX idx_model_kb ON model_config(kb_id);

COMMENT ON TABLE model_config IS '模型配置（LLM 提供商 API 配置）';

-- ==================== 5. 文档表 ====================
CREATE TABLE document (
    id               BIGSERIAL       PRIMARY KEY,
    kb_id            BIGINT          NOT NULL,
    file_name        VARCHAR(500)    NOT NULL,
    file_path        VARCHAR(1000)   NOT NULL,
    file_size        BIGINT          NOT NULL DEFAULT 0,
    file_type        VARCHAR(100),
    status           SMALLINT        NOT NULL DEFAULT 0,    -- 0=处理中, 1=就绪, 2=失败
    chunk_count      INT             DEFAULT 0,
    uploaded_by      BIGINT          NOT NULL,
    create_time      TIMESTAMP     NOT NULL DEFAULT now(),
    update_time      TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE INDEX idx_doc_kb ON document(kb_id);

COMMENT ON TABLE document IS '知识库文档';

-- ==================== 6. pgvector 扩展 ====================
-- 以超级用户身份在目标数据库执行：
--   CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE vector_store (
    id          UUID            DEFAULT gen_random_uuid()   PRIMARY KEY,
    content     TEXT            NOT NULL,
    metadata    JSONB           DEFAULT '{}',
    embedding   vector          -- 不限制维度，适配不同嵌入模型（如 OpenAI 1536、BGE 1024、Ollama 768）
);
CREATE INDEX idx_vector_kb ON vector_store USING btree (((metadata->>'kb_id')::bigint));
CREATE INDEX idx_vector_embedding ON vector_store USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
