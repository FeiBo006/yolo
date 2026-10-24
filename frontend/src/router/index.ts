import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/Login.vue'),
      meta: { title: '登录', public: true },
    },
    {
      path: '/screen',
      name: 'screen',
      component: () => import('@/views/Screen.vue'),
      meta: { title: '可视化大屏' },
    },
    {
      path: '/',
      component: () => import('@/layout/AppLayout.vue'),
      children: [
        { path: '', name: 'dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '安全驾驶舱' } },
        { path: 'monitor', name: 'monitor', component: () => import('@/views/Monitor.vue'), meta: { title: '实时监控' } },
        { path: 'alarms', name: 'alarms', component: () => import('@/views/Alarms.vue'), meta: { title: '告警中心' } },
        {
          path: 'cameras',
          name: 'cameras',
          component: () => import('@/views/Cameras.vue'),
          meta: { title: '摄像头管理', roles: ['ADMIN', 'MANAGER'] },
        },
        {
          path: 'sites',
          name: 'sites',
          component: () => import('@/views/Sites.vue'),
          meta: { title: '工地管理', roles: ['ADMIN', 'MANAGER'] },
        },
        {
          path: 'areas',
          name: 'areas',
          component: () => import('@/views/Areas.vue'),
          meta: { title: '区域配置', roles: ['ADMIN', 'MANAGER'] },
        },
        {
          path: 'rules',
          name: 'rules',
          component: () => import('@/views/Rules.vue'),
          meta: { title: '告警规则', roles: ['ADMIN'] },
        },
        { path: 'statistics', name: 'statistics', component: () => import('@/views/Statistics.vue'), meta: { title: '统计分析' } },
        {
          path: 'users',
          name: 'users',
          component: () => import('@/views/Users.vue'),
          meta: { title: '用户管理', roles: ['ADMIN'] },
        },
        {
          path: 'logs',
          name: 'logs',
          component: () => import('@/views/Logs.vue'),
          meta: { title: '操作日志', roles: ['ADMIN'] },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach((to) => {
  document.title = `${to.meta.title || '安全监测'} · YUANYU`
  const token = localStorage.getItem('yuanyu_token')
  if (!to.meta.public && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && token) {
    return { path: '/' }
  }
  const roles = to.meta.roles as string[] | undefined
  if (roles && roles.length) {
    try {
      const user = JSON.parse(localStorage.getItem('yuanyu_user') || 'null')
      if (user && !roles.includes(user.roleCode)) {
        return { path: '/' }
      }
    } catch {
      return { path: '/' }
    }
  }
  return true
})

export default router
