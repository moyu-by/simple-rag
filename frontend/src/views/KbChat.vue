<template>
  <div class="page-container chat-page">
    <!-- 头部 -->
    <div class="chat-header">
      <div class="chat-header__left">
        <el-button text @click="router.push(`/kb/${kbId}`)">
          <el-icon><ArrowLeft /></el-icon>
          返回知识库
        </el-button>
        <h2>{{ kb?.name || 'RAG 对话' }}</h2>
      </div>
    </div>

    <!-- 模型选择 -->
    <div class="chat-config" v-if="models.length > 0">
      <el-select v-model="embeddingConfigId" placeholder="嵌入模型" size="small" style="width: 200px">
        <el-option
          v-for="m in embeddingModels"
          :key="'e' + m.id"
          :label="`🔍 ${m.name}`"
          :value="m.id"
        />
      </el-select>
      <el-select v-model="chatConfigId" placeholder="对话模型" size="small" style="width: 200px">
        <el-option
          v-for="m in chatModels"
          :key="'c' + m.id"
          :label="`💬 ${m.name}`"
          :value="m.id"
        />
      </el-select>
      <span style="font-size: 12px; color: #909399">
        Top-K:
        <el-input-number
          v-model="topK"
          :min="1"
          :max="20"
          size="small"
          style="width: 80px"
        />
      </span>
    </div>
    <el-alert
      v-else
      title="请先在知识库详情页添加至少一个嵌入模型和一个对话模型，才能进行对话"
      type="warning"
      show-icon
      :closable="false"
      style="margin-bottom: 16px"
    />

    <!-- 对话区 -->
    <div class="chat-messages" ref="msgContainer">
      <!-- 欢迎提示 -->
      <div v-if="messages.length === 0" class="chat-welcome">
        <div class="welcome-icon">🤖</div>
        <h3>RAG 知识库问答</h3>
        <p>基于知识库文档内容回答你的问题，支持流式输出</p>
        <div class="welcome-hints">
          <el-tag
            v-for="hint in hints"
            :key="hint"
            class="hint-tag"
            @click="sendMessage(hint)"
          >
            {{ hint }}
          </el-tag>
        </div>
      </div>

      <!-- 消息列表 -->
      <div
        v-for="(msg, idx) in messages"
        :key="idx"
        class="chat-msg"
        :class="{ 'chat-msg--user': msg.role === 'user' }"
      >
        <div class="msg-avatar">
          {{ msg.role === 'user' ? '👤' : '🤖' }}
        </div>
        <div class="msg-content">
          <!-- 内容为空且正在流式输出时显示打字动画 -->
          <div v-if="!msg.content && msg.role === 'assistant' && idx === messages.length - 1 && streaming" class="typing-indicator">
            <span></span><span></span><span></span>
          </div>
          <div v-else class="msg-text" v-html="renderMarkdown(msg.content)"></div>
          <!-- 溯源引用 -->
          <div v-if="msg.sources && msg.sources.length > 0" class="msg-sources">
            <el-collapse>
              <el-collapse-item :title="`📎 引用来源 (${msg.sources.length} 条)`">
                <div
                  v-for="(src, si) in msg.sources"
                  :key="si"
                  class="source-item"
                >
                  <div class="source-score">
                    相关度：{{ (src.metadata.score * 100).toFixed(1) }}%
                  </div>
                  <div class="source-text">{{ src.content }}</div>
                </div>
              </el-collapse-item>
            </el-collapse>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="chat-input">
      <el-input
        v-model="inputText"
        type="textarea"
        :rows="2"
        placeholder="输入你的问题，按 Enter 发送，Shift+Enter 换行"
        :disabled="streaming || !canChat"
        @keydown.enter.exact="handleSend"
        resize="none"
      />
      <div class="chat-input__actions">
        <span class="input-hint">Enter 发送 · Shift+Enter 换行</span>
        <el-button
          type="primary"
          :disabled="!inputText.trim() || streaming || !canChat"
          :loading="streaming"
          @click="handleSend"
        >
          <el-icon><Promotion /></el-icon>
          发送
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Promotion } from '@element-plus/icons-vue'
import { kbApi, type KnowledgeBase } from '@/api/knowledgeBase'
import { modelConfigApi, type ModelConfig } from '@/api/modelConfig'
import { chatApi, type SearchResult, type MessagePair } from '@/api/chat'

const route = useRoute()
const router = useRouter()
const kbId = Number(route.params.id)

interface Message {
  role: 'user' | 'assistant'
  content: string
  sources?: SearchResult[]
}

const kb = ref<KnowledgeBase | null>(null)
const models = ref<ModelConfig[]>([])
const embeddingModels = computed(() => models.value.filter(m => m.isActive && m.modelType === 'EMBEDDING'))
const chatModels = computed(() => models.value.filter(m => m.isActive && m.modelType === 'CHAT'))
const canChat = computed(() => embeddingModels.value.length > 0 && chatModels.value.length > 0)

const embeddingConfigId = ref<number | null>(null)
const chatConfigId = ref<number | null>(null)
const topK = ref(3)

const messages = ref<Message[]>([])
const inputText = ref('')
const streaming = ref(false)
const msgContainer = ref<HTMLElement>()

const hints = [
  '知识库中有哪些文档？',
  '请总结知识库的核心内容',
  '帮我检索最近的文档',
]

onMounted(async () => {
  try {
    const [kbRes, modelRes, historyRes] = await Promise.all([
      kbApi.get(kbId),
      modelConfigApi.list(kbId),
      chatApi.loadHistory(kbId),
    ])
    kb.value = kbRes.data
    models.value = modelRes.data
    // 加载聊天历史
    messages.value = historyRes.data.map(m => ({
      role: m.role,
      content: m.content,
      sources: m.sources || undefined,
    }))
    if (embeddingModels.value.length > 0) {
      embeddingConfigId.value = embeddingModels.value[0].id
    }
    if (chatModels.value.length > 0) {
      chatConfigId.value = chatModels.value[0].id
    }
  } catch {
    router.push('/')
  }
})

// 滚动到底部
watch(messages, () => {
  nextTick(() => {
    if (msgContainer.value) {
      msgContainer.value.scrollTop = msgContainer.value.scrollHeight
    }
  })
}, { deep: true })

function handleSend() {
  if (!inputText.value.trim() || streaming.value) return
  if (!embeddingConfigId.value || !chatConfigId.value) {
    ElMessage.warning('请选择嵌入模型和对话模型')
    return
  }
  sendMessage(inputText.value.trim())
}

async function sendMessage(text: string) {
  inputText.value = ''
  messages.value.push({ role: 'user', content: text })

  // 保存用户消息到后端
  chatApi.saveMessage(kbId, { role: 'user', content: text }).catch(() => {})

  // 创建 AI 回复占位
  const aiMsg: Message = { role: 'assistant', content: '' }
  messages.value.push(aiMsg)

  streaming.value = true

  // 提取历史消息（除当前这轮外）
  const history: MessagePair[] = messages.value
    .slice(0, -2) // 去掉刚加的 user 和占位的 assistant
    .map(m => ({ role: m.role, content: m.content }))

  try {
    const stream = await chatApi.chatStream(kbId, {
      query: text,
      embeddingConfigId: embeddingConfigId.value!,
      chatConfigId: chatConfigId.value!,
      topK: topK.value,
      history,
    })

    if (!stream) {
      aiMsg.content = '请求失败，请重试'
      streaming.value = false
      return
    }

    const reader = stream.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let currentEvent = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      // 按行解析 SSE，每处理一个 data 行就更新 UI 并让出事件循环
      let idx
      while ((idx = buffer.indexOf('\n')) !== -1) {
        const line = buffer.slice(0, idx).trimEnd()
        buffer = buffer.slice(idx + 1)

        if (line.startsWith('event:')) {
          currentEvent = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          const data = line.slice(5).trim()
          if (data && (currentEvent === 'data' || !currentEvent)) {
            messages.value[messages.value.length - 1].content += data
            messages.value = [...messages.value]
            // 每个 token 后都让出，确保浏览器逐字渲染
            await new Promise(r => setTimeout(r, 0))
          }
        }
      }
    }
  } catch (err: any) {
    aiMsg.content = aiMsg.content || `错误：${err.message || '请求失败'}`
  } finally {
    streaming.value = false
    // 保存 AI 回复到后端
    chatApi.saveMessage(kbId, { role: 'assistant', content: aiMsg.content }).catch(() => {})
  }
}

// 简单的 Markdown 渲染（处理代码块和粗体）
function renderMarkdown(text: string): string {
  if (!text) return ''
  // 转义 HTML
  let html = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  // 代码块
  html = html.replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code>$2</code></pre>')
  // 行内代码
  html = html.replace(/`([^`]+)`/g, '<code>$1</code>')
  // 粗体
  html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  // 换行
  html = html.replace(/\n/g, '<br>')
  return html
}
</script>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 60px);
  padding-top: 16px;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  flex-shrink: 0;
}
.chat-header__left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.chat-header__left h2 {
  font-size: 18px;
  color: #303133;
}

.chat-config {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  background: #fff;
  border-radius: 8px;
  margin-bottom: 12px;
  flex-shrink: 0;
  flex-wrap: wrap;
}

/* 消息区 */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px 0;
  min-height: 0;
}

.chat-welcome {
  text-align: center;
  padding: 60px 20px;
  color: #909399;
}
.welcome-icon { font-size: 48px; margin-bottom: 16px; }
.chat-welcome h3 { font-size: 20px; color: #303133; margin-bottom: 8px; }
.chat-welcome p { font-size: 14px; margin-bottom: 24px; }
.welcome-hints { display: flex; gap: 8px; justify-content: center; flex-wrap: wrap; }
.hint-tag { cursor: pointer; }

.chat-msg {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  padding: 0 16px;
}
.chat-msg--user {
  flex-direction: row-reverse;
}
.chat-msg--user .msg-content {
  background: #409eff;
  color: #fff;
  border-radius: 12px 4px 12px 12px;
}
.chat-msg--user .msg-text {
  color: #fff;
}

.msg-avatar {
  font-size: 28px;
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.msg-content {
  max-width: 75%;
  background: #fff;
  border-radius: 4px 12px 12px 12px;
  padding: 12px 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.msg-text {
  font-size: 14px;
  line-height: 1.7;
  color: #303133;
  word-break: break-word;
}
.msg-text :deep(pre) {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 8px 0;
}
.msg-text :deep(code) {
  font-family: 'Courier New', monospace;
  font-size: 13px;
}
.msg-text :deep(strong) {
  font-weight: 600;
}

.msg-sources {
  margin-top: 8px;
}
.source-item {
  padding: 8px 0;
  border-bottom: 1px solid #ebeef5;
}
.source-item:last-child { border-bottom: none; }
.source-score {
  font-size: 12px;
  color: #67c23a;
  margin-bottom: 4px;
}
.source-text {
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
}

/* 打字动画 */
.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 4px 0;
}
.typing-indicator span {
  width: 8px;
  height: 8px;
  background: #909399;
  border-radius: 50%;
  animation: typing 1.4s infinite ease-in-out both;
}
.typing-indicator span:nth-child(1) { animation-delay: -0.32s; }
.typing-indicator span:nth-child(2) { animation-delay: -0.16s; }
@keyframes typing {
  0%, 80%, 100% { transform: scale(0.6); }
  40% { transform: scale(1); }
}

/* 输入区 */
.chat-input {
  flex-shrink: 0;
  padding: 12px 16px;
  background: #fff;
  border-top: 1px solid #ebeef5;
  border-radius: 8px 8px 0 0;
}

.chat-input__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}
.input-hint {
  font-size: 12px;
  color: #c0c4cc;
}

@media (max-width: 768px) {
  .chat-page {
    height: calc(100vh - 60px);
  }
  .msg-content {
    max-width: 85%;
  }
  .chat-config {
    flex-direction: column;
    align-items: stretch;
  }
  .chat-config .el-select {
    width: 100% !important;
  }
}
</style>
