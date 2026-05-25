import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userId = ref(Number(localStorage.getItem('userId')) || 0)
  const displayName = ref(localStorage.getItem('displayName') || '')

  const isLoggedIn = computed(() => !!token.value)

  function setAuth(t: string, uid: number) {
    token.value = t
    userId.value = uid
    localStorage.setItem('token', t)
    localStorage.setItem('userId', String(uid))
  }

  function setDisplayName(name: string) {
    displayName.value = name
    localStorage.setItem('displayName', name)
  }

  function logout() {
    token.value = ''
    userId.value = 0
    displayName.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('displayName')
  }

  return { token, userId, displayName, isLoggedIn, setAuth, setDisplayName, logout }
})
