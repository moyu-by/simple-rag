import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/Login.vue'),
      meta: { noAuth: true },
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('@/views/Register.vue'),
      meta: { noAuth: true },
    },
    {
      path: '/',
      component: () => import('@/layout/MainLayout.vue'),
      children: [
        {
          path: '',
          name: 'Home',
          component: () => import('@/views/Home.vue'),
        },
        {
          path: 'kb/:id',
          name: 'KbDetail',
          component: () => import('@/views/KbDetail.vue'),
        },
        {
          path: 'kb/:id/chat',
          name: 'KbChat',
          component: () => import('@/views/KbChat.vue'),
        },
      ],
    },
  ],
})

router.beforeEach((to, _from, next) => {
  const auth = useAuthStore()
  if (to.meta.noAuth) {
    // 已登录用户访问登录页，直接跳转首页
    if (auth.isLoggedIn && (to.name === 'Login' || to.name === 'Register')) {
      next('/')
    } else {
      next()
    }
  } else {
    if (!auth.isLoggedIn) {
      next('/login')
    } else {
      next()
    }
  }
})

export default router
