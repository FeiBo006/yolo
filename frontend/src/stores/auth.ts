import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api'

const TOKEN_KEY = 'yuanyu_token'
const USER_KEY = 'yuanyu_user'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const user = ref<any>(JSON.parse(localStorage.getItem(USER_KEY) || 'null'))

  const isLogin = computed(() => !!token.value)
  const role = computed(() => user.value?.roleCode || '')
  const displayName = computed(() => user.value?.realName || user.value?.username || '')

  async function login(form: { username: string; password: string }) {
    const data: any = await authApi.login(form)
    token.value = data.token
    user.value = data.user
    localStorage.setItem(TOKEN_KEY, data.token)
    localStorage.setItem(USER_KEY, JSON.stringify(data.user))
    return data.user
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch {
      // 忽略登出接口异常
    }
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  function hasRole(roles?: string[]) {
    if (!roles || roles.length === 0) return true
    return roles.includes(role.value)
  }

  return { token, user, isLogin, role, displayName, login, logout, hasRole }
})
