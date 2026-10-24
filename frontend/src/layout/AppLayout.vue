<template>
  <el-container class="layout">
    <el-aside width="222px" class="aside">
      <div class="logo">
        <div class="logo-mark">
          <el-icon :size="22"><Monitor /></el-icon>
        </div>
        <div class="logo-text">
          <strong>YUANYU</strong>
          <span>工地安全智能监测</span>
        </div>
      </div>

      <el-menu
        :default-active="activeMenu"
        router
        background-color="#0f172a"
        text-color="#94a3b8"
        active-text-color="#ffffff"
        class="menu"
      >
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
          <el-badge
            v-if="item.path === '/alarms' && alarmStore.unread > 0"
            :value="alarmStore.unread"
            :max="99"
            class="menu-badge"
          />
        </el-menu-item>
      </el-menu>

      <div class="aside-footer">
        <span>YOLOv8 · Spring Boot 3</span>
        <span>v1.0.0</span>
      </div>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <span class="page-name">{{ route.meta.title || '安全监测' }}</span>
          <el-tag size="small" type="success" effect="plain" class="mode-tag">实时预警中</el-tag>
        </div>
        <div class="header-right">
          <span class="ws-dot" :class="{ online: wsOnline }" />
          <span class="ws-text">{{ wsOnline ? '实时通道已连接' : '实时通道重连中' }}</span>
          <el-badge :value="alarmStore.unread" :hidden="alarmStore.unread === 0" :max="99">
            <el-button text circle @click="goAlarms">
              <el-icon :size="19"><Bell /></el-icon>
            </el-button>
          </el-badge>
          <el-dropdown @command="onCommand">
            <span class="user">
              <el-avatar :size="30" class="avatar">{{ initials }}</el-avatar>
              <span class="user-name">{{ auth.displayName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>{{ roleLabel }}</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElNotification } from 'element-plus'
import {
  Aim,
  ArrowDown,
  Bell,
  Camera,
  DataLine,
  Document,
  FullScreen,
  Monitor,
  OfficeBuilding,
  SetUp,
  TrendCharts,
  User as UserIcon,
  VideoCamera,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useAlarmStore } from '@/stores/alarm'
import { alarmApi } from '@/api'
import { behaviorLabel, levelLabel } from '@/utils/format'
import { connectAlarmSocket, disconnectAlarmSocket, playAlarmSound } from '@/utils/ws'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const alarmStore = useAlarmStore()
const wsOnline = ref(false)

const menus = computed(() => [
  { path: '/', title: '安全驾驶舱', icon: DataLine },
  { path: '/monitor', title: '实时监控', icon: VideoCamera },
  { path: '/alarms', title: '告警中心', icon: Bell },
  ...(auth.hasRole(['ADMIN', 'MANAGER'])
    ? [
        { path: '/sites', title: '工地管理', icon: OfficeBuilding },
        { path: '/cameras', title: '摄像头管理', icon: Camera },
        { path: '/areas', title: '区域配置', icon: Aim },
      ]
    : []),
  ...(auth.hasRole(['ADMIN']) ? [{ path: '/rules', title: '告警规则', icon: SetUp }] : []),
  { path: '/statistics', title: '统计分析', icon: TrendCharts },
  ...(auth.hasRole(['ADMIN'])
    ? [
        { path: '/users', title: '用户管理', icon: UserIcon },
        { path: '/logs', title: '操作日志', icon: Document },
      ]
    : []),
  { path: '/screen', title: '可视化大屏', icon: FullScreen },
])

const activeMenu = computed(() => (route.path === '/' ? '/' : route.path))
const initials = computed(() => (auth.displayName || 'U').slice(0, 1))
const roleLabel = computed(() => {
  const map: Record<string, string> = { ADMIN: '系统管理员', MANAGER: '项目经理', INSPECTOR: '安全员' }
  return map[auth.role] || '用户'
})

function goAlarms() {
  alarmStore.clearUnread()
  router.push('/alarms')
}

async function onCommand(command: string) {
  if (command === 'logout') {
    disconnectAlarmSocket()
    await auth.logout()
    router.push('/login')
  }
}

onMounted(async () => {
  connectAlarmSocket({
    onOpen: () => (wsOnline.value = true),
    onClose: () => (wsOnline.value = false),
    onAlarm: (type, data) => {
      if (type === 'ALARM') {
        alarmStore.push(data)
        playAlarmSound()
        ElNotification({
          title: `安全告警 · ${behaviorLabel(data.behaviorType)}`,
          message: `${data.cameraName} · 置信度 ${(Number(data.confidence) * 100).toFixed(0)}% · ${levelLabel(data.level)}风险`,
          type: 'warning',
          duration: 6000,
          position: 'bottom-right',
        })
      } else {
        alarmStore.update(data)
      }
    },
  })
  try {
    const page: any = await alarmApi.page({ page: 1, size: 30 })
    if (!alarmStore.latest.length) {
      alarmStore.setList(page.records || [])
    }
  } catch {
    // 忽略
  }
})

onBeforeUnmount(() => disconnectAlarmSocket())
</script>

<style scoped>
.layout {
  min-height: 100vh;
}

.aside {
  background: #0f172a;
  display: flex;
  flex-direction: column;
  position: sticky;
  top: 0;
  height: 100vh;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 18px 14px;
  color: #fff;
}

.logo-mark {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  background: linear-gradient(135deg, #2563eb, #06b6d4);
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo-text {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.logo-text strong {
  font-size: 16px;
  letter-spacing: 1.5px;
}

.logo-text span {
  font-size: 11px;
  color: #7c8db5;
}

.menu {
  border-right: none;
  flex: 1;
  padding: 6px 10px;
}

.menu :deep(.el-menu-item) {
  border-radius: 9px;
  margin-bottom: 4px;
  height: 44px;
}

.menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, #1d4ed8, #2563eb);
}

.menu-badge {
  margin-left: auto;
}

.aside-footer {
  padding: 14px 18px;
  font-size: 11px;
  color: #52627f;
  display: flex;
  justify-content: space-between;
  border-top: 1px solid #1e293b;
}

.header {
  height: 62px;
  background: #fff;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 22px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.page-name {
  font-size: 16px;
  font-weight: 600;
}

.mode-tag {
  font-size: 11px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ws-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ef4444;
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.12);
}

.ws-dot.online {
  background: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.15);
}

.ws-text {
  font-size: 12px;
  color: var(--muted);
  margin-right: 8px;
}

.user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 8px;
}

.user:hover {
  background: #f1f5f9;
}

.avatar {
  background: linear-gradient(135deg, #2563eb, #06b6d4);
  font-size: 13px;
}

.user-name {
  font-size: 13px;
  color: var(--text);
}

.main {
  padding: 0;
  height: calc(100vh - 62px);
  overflow-y: auto;
}
</style>
