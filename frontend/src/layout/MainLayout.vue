<template>
  <div class="main-layout">
    <!-- 桌面端导航栏 -->
    <header class="navbar" :class="{ 'navbar--mobile': isMobile }">
      <div class="navbar__inner">
        <!-- Logo -->
        <router-link to="/" class="navbar__logo">
          <span class="logo-icon">📚</span>
          <span class="logo-text">RAG 知识库</span>
        </router-link>

        <!-- 桌面端菜单 -->
        <nav v-if="!isMobile" class="navbar__nav">
          <router-link to="/" class="nav-link" active-class="nav-link--active">
            我的知识库
          </router-link>
        </nav>

        <!-- 桌面端用户区 -->
        <div v-if="!isMobile" class="navbar__user">
          <el-dropdown trigger="click">
            <span class="user-dropdown">
              <el-icon><UserFilled /></el-icon>
              <span class="user-name">{{ auth.displayName || '用户 ' + auth.userId }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="showNameDialog">
                  <el-icon><EditPen /></el-icon>
                  修改昵称
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <!-- 移动端汉堡按钮 -->
        <button v-if="isMobile" class="hamburger" @click="drawerVisible = true">
          <span></span>
          <span></span>
          <span></span>
        </button>
      </div>
    </header>

    <!-- 移动端侧边抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      direction="ltr"
      size="260px"
      :with-header="false"
    >
      <div class="drawer-content">
        <div class="drawer__header">
          <span class="logo-text">📚 RAG 知识库</span>
        </div>
        <el-menu
          :default-active="currentRoute"
          @select="handleMenuSelect"
        >
          <el-menu-item index="/">
            <el-icon><HomeFilled /></el-icon>
            <span>我的知识库</span>
          </el-menu-item>
        </el-menu>
        <div class="drawer__footer">
          <el-button plain @click="showNameDialog" style="width: 100%; margin-bottom: 8px">
            修改昵称
          </el-button>
          <el-button type="danger" plain @click="handleLogout" style="width: 100%">
            退出登录
          </el-button>
        </div>
      </div>
    </el-drawer>

    <!-- 修改昵称弹窗 -->
    <el-dialog v-model="nameDialogVisible" title="修改昵称" width="380px">
      <el-form @submit.prevent="handleSaveName">
        <el-form-item>
          <el-input
            v-model="nameInput"
            placeholder="输入新昵称（留空则显示用户ID）"
            maxlength="50"
            show-word-limit
            clearable
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nameDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="nameSaving" @click="handleSaveName">保存</el-button>
      </template>
    </el-dialog>

    <!-- 页面内容 -->
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ArrowDown, UserFilled, SwitchButton, HomeFilled, EditPen } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api/user'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const drawerVisible = ref(false)
const windowWidth = ref(window.innerWidth)
const isMobile = computed(() => windowWidth.value < 768)
const currentRoute = computed(() => route.path)

// 修改昵称
const nameDialogVisible = ref(false)
const nameSaving = ref(false)
const nameInput = ref(auth.displayName)

function showNameDialog() {
  nameInput.value = auth.displayName
  nameDialogVisible.value = true
}

async function handleSaveName() {
  nameSaving.value = true
  try {
    await userApi.updateDisplayName(nameInput.value.trim())
    auth.setDisplayName(nameInput.value.trim())
    ElMessage.success('昵称已更新')
    nameDialogVisible.value = false
  } catch {
    // 错误在拦截器处理
  } finally {
    nameSaving.value = false
  }
}

function onResize() {
  windowWidth.value = window.innerWidth
}

onMounted(() => window.addEventListener('resize', onResize))
onUnmounted(() => window.removeEventListener('resize', onResize))

function handleMenuSelect(index: string) {
  drawerVisible.value = false
  router.push(index)
}

function handleLogout() {
  auth.logout()
  drawerVisible.value = false
  router.push('/login')
}
</script>

<style scoped>
.main-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* ===== 导航栏 ===== */
.navbar {
  position: sticky;
  top: 0;
  z-index: 100;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  height: 60px;
}

.navbar__inner {
  max-width: 1200px;
  margin: 0 auto;
  height: 100%;
  display: flex;
  align-items: center;
  padding: 0 20px;
  gap: 24px;
}

.navbar__logo {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
  flex-shrink: 0;
}

.logo-icon { font-size: 24px; }
.logo-text {
  font-size: 18px;
  font-weight: 700;
  color: #409eff;
}

.navbar__nav {
  display: flex;
  gap: 4px;
  flex: 1;
}

.nav-link {
  padding: 8px 16px;
  border-radius: 6px;
  text-decoration: none;
  color: #606266;
  font-size: 14px;
  transition: all 0.2s;
}
.nav-link:hover {
  background: #ecf5ff;
  color: #409eff;
}
.nav-link--active {
  background: #ecf5ff;
  color: #409eff;
  font-weight: 600;
}

.navbar__user {
  flex-shrink: 0;
}

.user-dropdown {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #606266;
  font-size: 14px;
  padding: 6px 12px;
  border-radius: 6px;
  transition: background 0.2s;
}
.user-dropdown:hover {
  background: #f5f7fa;
}

/* 汉堡按钮 */
.hamburger {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 5px;
  width: 36px;
  height: 36px;
  background: none;
  border: none;
  cursor: pointer;
  padding: 6px;
  margin-left: auto;
}
.hamburger span {
  display: block;
  height: 2px;
  background: #303133;
  border-radius: 2px;
  transition: all 0.3s;
}

/* 抽屉内容 */
.drawer-content {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.drawer__header {
  padding: 20px;
  border-bottom: 1px solid #ebeef5;
  text-align: center;
}
.drawer__footer {
  margin-top: auto;
  padding: 20px;
  border-top: 1px solid #ebeef5;
}

/* 主内容区 */
.main-content {
  flex: 1;
}
</style>
