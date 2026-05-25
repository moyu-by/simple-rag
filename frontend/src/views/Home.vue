<template>
  <div class="page-container home-page">
    <div class="page-header">
      <h2>我的知识库</h2>
      <el-button type="primary" @click="showCreateDialog">
        <el-icon><Plus /></el-icon>
        创建知识库
      </el-button>
    </div>

    <!-- 加载中 -->
    <el-skeleton v-if="loading" :rows="3" animated />

    <!-- 空状态 -->
    <el-empty v-else-if="list.length === 0" description="还没有知识库，点击上方按钮创建一个吧" />

    <!-- 知识库卡片列表 -->
    <div v-else class="kb-grid">
      <el-card
        v-for="kb in list"
        :key="kb.id"
        class="kb-card"
        shadow="hover"
        @click="goDetail(kb.id)"
      >
        <div class="kb-card__header">
          <h3 class="kb-name">{{ kb.name }}</h3>
          <el-tag size="small" :type="roleTagType(kb.myRole)">
            {{ kb.myRole }}
          </el-tag>
        </div>
        <p class="kb-desc">{{ kb.description || '暂无描述' }}</p>
        <div class="kb-card__footer">
          <span class="kb-time">{{ formatTime(kb.createTime) }}</span>
          <div class="kb-actions" @click.stop>
            <el-button text type="primary" size="small" @click="goChat(kb.id)">
              对话
            </el-button>
            <el-dropdown trigger="click">
              <el-button text size="small">
                <el-icon><MoreFilled /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="openEdit(kb)">编辑</el-dropdown-item>
                  <el-dropdown-item
                    v-if="kb.myRole === '库主'"
                    divided
                    style="color: #f56c6c"
                    @click="handleDelete(kb)"
                  >
                    删除
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 创建/编辑 弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingKb ? '编辑知识库' : '创建知识库'"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form ref="kbFormRef" :model="kbForm" :rules="kbRules" label-position="top">
        <el-form-item label="名称" prop="name">
          <el-input v-model="kbForm.name" placeholder="请输入知识库名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="kbForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入描述（选填）"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ editingKb ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, MoreFilled } from '@element-plus/icons-vue'
import { kbApi, type KnowledgeBase } from '@/api/knowledgeBase'

const router = useRouter()
const loading = ref(true)
const list = ref<KnowledgeBase[]>([])
const dialogVisible = ref(false)
const submitting = ref(false)
const editingKb = ref<KnowledgeBase | null>(null)
const kbFormRef = ref<FormInstance>()

const kbForm = reactive({ name: '', description: '' })
const kbRules: FormRules = {
  name: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }],
}

onMounted(fetchList)

async function fetchList() {
  loading.value = true
  try {
    const res = await kbApi.list()
    list.value = res.data
  } catch { /* handled */ }
  finally { loading.value = false }
}

function showCreateDialog() {
  editingKb.value = null
  kbForm.name = ''
  kbForm.description = ''
  dialogVisible.value = true
}

function openEdit(kb: KnowledgeBase) {
  editingKb.value = kb
  kbForm.name = kb.name
  kbForm.description = kb.description || ''
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await kbFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editingKb.value) {
      await kbApi.update(editingKb.value.id, {
        name: kbForm.name,
        description: kbForm.description || undefined,
      })
      ElMessage.success('修改成功')
    } else {
      await kbApi.create({
        name: kbForm.name,
        description: kbForm.description || undefined,
      })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch { /* handled */ }
  finally { submitting.value = false }
}

async function handleDelete(kb: KnowledgeBase) {
  try {
    await ElMessageBox.confirm(`确定删除知识库「${kb.name}」吗？此操作会同时删除所有文档、模型配置和成员关系，不可恢复。`, '警告', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger',
    })
    await kbApi.remove(kb.id)
    ElMessage.success('已删除')
    fetchList()
  } catch { /* 取消或失败 */ }
}

function goDetail(id: number) {
  router.push(`/kb/${id}`)
}

function goChat(id: number) {
  router.push(`/kb/${id}/chat`)
}

function roleTagType(role: string): 'success' | 'warning' | '' {
  if (role === '库主') return ''
  if (role === '管理员') return 'warning'
  return 'success'
}

function formatTime(time: string): string {
  if (!time) return ''
  return new Date(time).toLocaleDateString('zh-CN')
}
</script>

<style scoped>
.home-page { padding-top: 24px; }

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}
.page-header h2 {
  font-size: 20px;
  color: #303133;
}

.kb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.kb-card {
  cursor: pointer;
  transition: transform 0.2s;
}
.kb-card:hover {
  transform: translateY(-2px);
}

.kb-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.kb-name {
  font-size: 16px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  margin-right: 8px;
}

.kb-desc {
  color: #909399;
  font-size: 13px;
  line-height: 1.6;
  min-height: 40px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.kb-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}
.kb-time {
  font-size: 12px;
  color: #c0c4cc;
}

@media (max-width: 768px) {
  .kb-grid {
    grid-template-columns: 1fr;
  }
}
</style>
