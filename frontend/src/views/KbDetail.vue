<template>
  <div class="page-container kb-detail">
    <!-- 顶部信息 -->
    <div class="detail-header">
      <div class="detail-header__left">
        <el-button text @click="router.push('/')">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <h2>{{ kb?.name || '加载中...' }}</h2>
        <el-tag v-if="kb" size="small" :type="roleTagType(kb.myRole)">{{ kb.myRole }}</el-tag>
      </div>
      <el-button v-if="kb" type="primary" @click="router.push(`/kb/${kb.id}/chat`)">
        <el-icon><ChatDotRound /></el-icon>
        RAG 对话
      </el-button>
    </div>

    <!-- Tab 切换 -->
    <el-tabs v-model="activeTab" type="border-card">
      <!-- ===== 文档管理 ===== -->
      <el-tab-pane label="文档" name="docs">
        <div class="tab-toolbar">
          <span class="tab-toolbar__count">共 {{ docs.length }} 个文档</span>
          <el-upload
            :action="`/api/knowledge-base/${kbId}/document`"
            :headers="uploadHeaders"
            :before-upload="beforeUpload"
            :on-success="onUploadSuccess"
            :show-file-list="false"
            :data="uploadData"
          >
            <el-button type="primary" size="small">
              <el-icon><Upload /></el-icon>
              上传文档
            </el-button>
          </el-upload>
        </div>

        <el-table :data="docs" stripe v-loading="docLoading" empty-text="暂无文档" style="width: 100%">
          <el-table-column prop="fileName" label="文件名" min-width="160">
            <template #default="{ row }">
              <a :href="row.fileUrl" target="_blank" class="file-link">{{ row.fileName }}</a>
            </template>
          </el-table-column>
          <el-table-column prop="fileType" label="类型" width="80" />
          <el-table-column prop="fileSize" label="大小" width="100">
            <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">
                {{ statusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="上传时间" width="120">
            <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status !== 1 && canManage"
                text
                type="primary"
                size="small"
                @click="showProcessDialog(row)"
              >
                处理
              </el-button>
              <el-button
                v-if="canManage"
                text
                type="danger"
                size="small"
                @click="handleDeleteDoc(row)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ===== 模型配置 ===== -->
      <el-tab-pane label="模型配置" name="models">
        <div class="tab-toolbar">
          <span class="tab-toolbar__count">共 {{ models.length }} 个配置</span>
          <el-button v-if="canManage" type="primary" size="small" @click="openModelDialog()">
            <el-icon><Plus /></el-icon>
            添加配置
          </el-button>
        </div>

        <el-table :data="models" stripe v-loading="modelLoading" empty-text="暂无模型配置" style="width: 100%">
          <el-table-column prop="name" label="名称" min-width="120" />
          <el-table-column prop="modelType" label="类型" width="80">
            <template #default="{ row }">
              <el-tag :type="row.modelType === 'EMBEDDING' ? 'success' : ''" size="small">
                {{ row.modelType === 'EMBEDDING' ? '嵌入' : '对话' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="provider" label="提供商" width="100" />
          <el-table-column prop="modelName" label="模型" min-width="180" />
          <el-table-column prop="apiKey" label="密钥" width="120">
            <template #default="{ row }">{{ row.apiKey }}</template>
          </el-table-column>
          <el-table-column prop="baseUrl" label="API 地址" min-width="180">
            <template #default="{ row }">{{ row.baseUrl || '-' }}</template>
          </el-table-column>
          <el-table-column prop="isActive" label="启用" width="70">
            <template #default="{ row }">
              <el-switch :model-value="row.isActive" disabled size="small" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right" v-if="canManage">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click="openModelDialog(row)">编辑</el-button>
              <el-button text type="danger" size="small" @click="handleDeleteModel(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ===== 成员管理 ===== -->
      <el-tab-pane label="成员" name="members">
        <div class="tab-toolbar">
          <span class="tab-toolbar__count">共 {{ members.length }} 人</span>
          <el-button v-if="canManageMembers" type="primary" size="small" @click="showAddMemberDialog">
            <el-icon><Plus /></el-icon>
            添加成员
          </el-button>
        </div>

        <el-table :data="members" stripe v-loading="memberLoading" empty-text="暂无成员" style="width: 100%">
          <el-table-column prop="account" label="账号" width="120" />
          <el-table-column prop="displayName" label="显示名" width="120" />
          <el-table-column prop="roleInKb" label="角色" width="100">
            <template #default="{ row }">
              <el-tag :type="roleTagType(row.roleInKb)" size="small">{{ row.roleInKb }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="joinTime" label="加入时间" width="120">
            <template #default="{ row }">{{ formatDate(row.joinTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <template v-if="canManageMembers && row.userId !== authStore.userId">
                <el-button text type="primary" size="small" @click="showEditMemberRole(row)">改角色</el-button>
                <el-button text type="danger" size="small" @click="handleRemoveMember(row)">移除</el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- ===== 模型配置弹窗 ===== -->
    <el-dialog
      v-model="modelDialogVisible"
      :title="editingModel ? '编辑模型配置' : '添加模型配置'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="modelFormRef" :model="modelForm" :rules="modelRules" label-position="top">
        <el-form-item label="配置名称" prop="name">
          <el-input v-model="modelForm.name" placeholder="如：GPT-4 对话模型" />
        </el-form-item>
        <el-form-item label="模型类型" prop="modelType">
          <el-select v-model="modelForm.modelType" style="width: 100%">
            <el-option label="🔍 嵌入模型 (Embedding)" value="EMBEDDING" />
            <el-option label="💬 对话模型 (Chat)" value="CHAT" />
          </el-select>
        </el-form-item>
        <el-form-item label="提供商" prop="provider">
          <el-select v-model="modelForm.provider" style="width: 100%">
            <el-option label="OpenAI 兼容协议 (OpenAI/硅基流动/vLLM等)" value="custom" />
            <el-option label="Anthropic (Claude)" value="anthropic" />
          </el-select>
        </el-form-item>
        <el-form-item label="API 地址" prop="baseUrl">
          <el-input v-model="modelForm.baseUrl" placeholder="留空默认 https://api.openai.com" />
        </el-form-item>
        <el-form-item label="API 密钥" prop="apiKey">
          <el-input v-model="modelForm.apiKey" placeholder="sk-..." show-password />
        </el-form-item>
        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="modelForm.modelName" placeholder="如 gpt-4 / text-embedding-ada-002" />
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="modelForm.isActive" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="modelDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="modelSubmitting" @click="handleModelSubmit">
          {{ editingModel ? '保存' : '添加' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- ===== 文档处理弹窗 ===== -->
    <el-dialog v-model="processDialogVisible" title="处理文档" width="400px">
      <p style="margin-bottom: 12px">选择嵌入模型来处理文档「{{ processingDoc?.fileName }}」</p>
      <el-select v-model="processEmbeddingId" placeholder="请选择嵌入模型" style="width: 100%">
        <el-option
          v-for="m in embeddingModels"
          :key="m.id"
          :label="`${m.name} (${m.modelName})`"
          :value="m.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="processDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="processSubmitting"
          :disabled="!processEmbeddingId"
          @click="handleProcessDoc"
        >
          开始处理
        </el-button>
      </template>
    </el-dialog>

    <!-- ===== 添加成员弹窗 ===== -->
    <el-dialog v-model="addMemberVisible" title="添加成员" width="400px">
      <el-form ref="addMemberFormRef" :model="addMemberForm" :rules="addMemberRules" label-position="top">
        <el-form-item label="用户 ID" prop="userId">
          <el-input-number v-model="addMemberForm.userId" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="角色" prop="roleInKb">
          <el-select v-model="addMemberForm.roleInKb" style="width: 100%">
            <el-option label="成员" value="MEMBER" />
            <el-option label="管理员" value="ADMIN" />
            <el-option v-if="kb?.myRole === '库主'" label="库主" value="BOSS" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addMemberVisible = false">取消</el-button>
        <el-button type="primary" :loading="addMemberSubmitting" @click="handleAddMember">添加</el-button>
      </template>
    </el-dialog>

    <!-- ===== 修改成员角色弹窗 ===== -->
    <el-dialog v-model="editRoleVisible" title="修改角色" width="400px">
      <p style="margin-bottom: 12px">用户：{{ editingMember?.account }}</p>
      <el-select v-model="editRoleValue" style="width: 100%">
        <el-option label="成员" value="MEMBER" />
        <el-option label="管理员" value="ADMIN" />
        <el-option v-if="kb?.myRole === '库主'" label="库主" value="BOSS" />
      </el-select>
      <template #footer>
        <el-button @click="editRoleVisible = false">取消</el-button>
        <el-button type="primary" :loading="editRoleSubmitting" @click="handleEditRole">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { ArrowLeft, ChatDotRound, Upload, Plus } from '@element-plus/icons-vue'
import { kbApi, type KnowledgeBase } from '@/api/knowledgeBase'
import { docApi, type Document } from '@/api/document'
import { modelConfigApi, type ModelConfig, type ModelConfigParams } from '@/api/modelConfig'
import { memberApi, type Member } from '@/api/member'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const kbId = Number(route.params.id)

// ===== 知识库信息 =====
const kb = ref<KnowledgeBase | null>(null)

// 权限判断
const canManage = computed(() => kb.value?.myRole === '库主' || kb.value?.myRole === '管理员')
const canManageMembers = computed(() => kb.value?.myRole === '库主' || kb.value?.myRole === '管理员')

// ===== Tab 状态 =====
const activeTab = ref('docs')

// ===== 文档管理 =====
const docs = ref<Document[]>([])
const docLoading = ref(false)

const uploadHeaders = computed(() => ({
  Authorization: `Bearer ${authStore.token}`,
}))
const uploadData = reactive({})

function beforeUpload(file: File) {
  const allowed = ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'md']
  const ext = file.name.split('.').pop()?.toLowerCase() || ''
  if (!allowed.includes(ext)) {
    ElMessage.error(`不支持的文件类型：.${ext}`)
    return false
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 10MB')
    return false
  }
  return true
}

function onUploadSuccess() {
  ElMessage.success('上传成功')
  fetchDocs()
}

async function fetchDocs() {
  docLoading.value = true
  try {
    const res = await docApi.list(kbId)
    docs.value = res.data
  } catch { /* handled */ }
  finally { docLoading.value = false }
}

async function handleDeleteDoc(doc: Document) {
  try {
    await ElMessageBox.confirm(`确定删除「${doc.fileName}」吗？`, '确认', { type: 'warning' })
    await docApi.remove(kbId, doc.id)
    ElMessage.success('已删除')
    fetchDocs()
  } catch { /* cancel */ }
}

// ===== 文档处理 =====
const processDialogVisible = ref(false)
const processingDoc = ref<Document | null>(null)
const processEmbeddingId = ref<number | null>(null)
const processSubmitting = ref(false)
const embeddingModels = computed(() => models.value.filter(m => m.isActive && m.modelType === 'EMBEDDING'))

function showProcessDialog(doc: Document) {
  processingDoc.value = doc
  processEmbeddingId.value = embeddingModels.value[0]?.id || null
  processDialogVisible.value = true
}

async function handleProcessDoc() {
  if (!processEmbeddingId.value || !processingDoc.value) return
  processSubmitting.value = true
  try {
    await docApi.process(kbId, processingDoc.value.id, processEmbeddingId.value)
    ElMessage.success('已开始处理')
    processDialogVisible.value = false
    fetchDocs()
  } catch { /* handled */ }
  finally { processSubmitting.value = false }
}

// ===== 模型配置 =====
const models = ref<ModelConfig[]>([])
const modelLoading = ref(false)
const modelDialogVisible = ref(false)
const modelSubmitting = ref(false)
const editingModel = ref<ModelConfig | null>(null)
const modelFormRef = ref<FormInstance>()
const modelForm = reactive<ModelConfigParams>({
  name: '',
  modelType: 'CHAT',
  provider: 'custom',
  baseUrl: '',
  apiKey: '',
  modelName: '',
  isActive: true,
})
const modelRules: FormRules = {
  name: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  modelType: [{ required: true }],
  provider: [{ required: true }],
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  apiKey: [{ required: true, message: '请输入 API 密钥', trigger: 'blur' }],
}

async function fetchModels() {
  modelLoading.value = true
  try {
    const res = await modelConfigApi.list(kbId)
    models.value = res.data
  } catch { /* handled */ }
  finally { modelLoading.value = false }
}

function openModelDialog(model?: ModelConfig) {
  if (model) {
    editingModel.value = model
    modelForm.name = model.name
    modelForm.modelType = model.modelType
    modelForm.provider = model.provider
    modelForm.baseUrl = model.baseUrl || ''
    modelForm.apiKey = ''
    modelForm.modelName = model.modelName
    modelForm.isActive = model.isActive
  } else {
    editingModel.value = null
    modelForm.name = ''
    modelForm.modelType = 'CHAT'
    modelForm.provider = 'custom'
    modelForm.baseUrl = ''
    modelForm.apiKey = ''
    modelForm.modelName = ''
    modelForm.isActive = true
  }
  modelDialogVisible.value = true
}

async function handleModelSubmit() {
  const valid = await modelFormRef.value?.validate().catch(() => false)
  if (!valid) return
  modelSubmitting.value = true
  try {
    const params = { ...modelForm }
    if (!params.baseUrl) delete params.baseUrl
    if (editingModel.value) {
      await modelConfigApi.update(kbId, editingModel.value.id, params)
      ElMessage.success('修改成功')
    } else {
      await modelConfigApi.create(kbId, params)
      ElMessage.success('添加成功')
    }
    modelDialogVisible.value = false
    fetchModels()
  } catch { /* handled */ }
  finally { modelSubmitting.value = false }
}

async function handleDeleteModel(model: ModelConfig) {
  try {
    await ElMessageBox.confirm(`确定删除配置「${model.name}」吗？`, '确认', { type: 'warning' })
    await modelConfigApi.remove(kbId, model.id)
    ElMessage.success('已删除')
    fetchModels()
  } catch { /* cancel */ }
}

// ===== 成员管理 =====
const members = ref<Member[]>([])
const memberLoading = ref(false)

async function fetchMembers() {
  memberLoading.value = true
  try {
    const res = await memberApi.list(kbId)
    members.value = res.data
  } catch { /* handled */ }
  finally { memberLoading.value = false }
}

const addMemberVisible = ref(false)
const addMemberSubmitting = ref(false)
const addMemberFormRef = ref<FormInstance>()
const addMemberForm = reactive({ userId: 0, roleInKb: 'MEMBER' })
const addMemberRules: FormRules = {
  userId: [{ required: true, message: '请输入用户 ID', trigger: 'blur' }],
  roleInKb: [{ required: true }],
}

function showAddMemberDialog() {
  addMemberForm.userId = 0
  addMemberForm.roleInKb = 'MEMBER'
  addMemberVisible.value = true
}

async function handleAddMember() {
  const valid = await addMemberFormRef.value?.validate().catch(() => false)
  if (!valid) return
  addMemberSubmitting.value = true
  try {
    await memberApi.add(kbId, { ...addMemberForm })
    ElMessage.success('添加成功')
    addMemberVisible.value = false
    fetchMembers()
  } catch { /* handled */ }
  finally { addMemberSubmitting.value = false }
}

const editRoleVisible = ref(false)
const editRoleSubmitting = ref(false)
const editingMember = ref<Member | null>(null)
const editRoleValue = ref('')

function showEditMemberRole(member: Member) {
  editingMember.value = member
  editRoleValue.value = member.roleInKb
  editRoleVisible.value = true
}

async function handleEditRole() {
  if (!editingMember.value) return
  editRoleSubmitting.value = true
  try {
    await memberApi.update(kbId, editingMember.value.userId, { roleInKb: editRoleValue.value })
    ElMessage.success('修改成功')
    editRoleVisible.value = false
    fetchMembers()
  } catch { /* handled */ }
  finally { editRoleSubmitting.value = false }
}

async function handleRemoveMember(member: Member) {
  try {
    await ElMessageBox.confirm(`确定移除成员「${member.account}」吗？`, '确认', { type: 'warning' })
    await memberApi.remove(kbId, member.userId)
    ElMessage.success('已移除')
    fetchMembers()
  } catch { /* cancel */ }
}

// ===== 工具函数 =====
function formatSize(bytes: number): string {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}

function formatDate(t: string): string {
  if (!t) return '-'
  return new Date(t).toLocaleDateString('zh-CN')
}

function statusText(s: number): string {
  return ['处理中', '就绪', '失败'][s] || '未知'
}

function statusType(s: number): 'warning' | 'success' | 'danger' | 'info' {
  return (['warning', 'success', 'danger'] as const)[s] || 'info'
}

function roleTagType(role: string): 'success' | 'warning' | '' {
  if (role === '库主') return ''
  if (role === '管理员') return 'warning'
  return 'success'
}

// ===== 初始化 =====
onMounted(async () => {
  try {
    const res = await kbApi.get(kbId)
    kb.value = res.data
  } catch {
    router.push('/')
    return
  }
  fetchDocs()
  fetchModels()
  fetchMembers()
})
</script>

<style scoped>
.kb-detail { padding-top: 16px; }

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.detail-header__left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.detail-header__left h2 {
  font-size: 20px;
  color: #303133;
}

.tab-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.tab-toolbar__count {
  font-size: 13px;
  color: #909399;
}

.file-link {
  color: #409eff;
  text-decoration: none;
}
.file-link:hover {
  text-decoration: underline;
}

@media (max-width: 768px) {
  .detail-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  .tab-toolbar {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
}
</style>
