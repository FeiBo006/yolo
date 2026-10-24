<template>
  <el-container class="layout">
    <el-aside width="234px" class="aside">
      <div class="brand">
        <div class="brand-mark">
          <el-icon :size="21"><Monitor /></el-icon>
        </div>
        <div class="brand-text">
          <strong>YUANYU</strong>
          <span>智慧工地安全监测</span>
        </div>
      </div>

      <div class="aside-status">
        <span class="status-dot" :class="{ online: wsOnline }" />
        <span>{{ wsOnline ? '实时预警通道已连接' : '实时通道重连中' }}</span>
      </div>

      <el-menu
        :default-active="activeMenu"
        router
        background-color="transparent"
        text-color="#94a3b8"
        active-text-color="#ffffff"
        class="menu"
      >
        <el-menu-item-group v-for="group in menuGroups" :key="group.label" :title="group.label">
          <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
            <span v-if="item.path === '/alarms' && alarmStore.unread > 0" class="menu-badge">
              {{ alarmStore.unread > 99 ? '99+' : alarmStore.unread }}
            </span>
          </el-menu-item>
        </el-menu-item-group>
      </el-menu>

      <div class="aside-footer">
        <span>YOLOv8 · Spring Boot 3</span>
        <span class="version">v1.0.0</span>
      </div>
    </el-aside>

    <el-container class="body">
      <el-header class="header">
        <div class="header-left">
          <span class="crumb">YUANYU</span>
          <span class="crumb-divider">/</span>
          <span class="crumb-current">{{ route.meta.title || '安全监测' }}</span>
          <span class="live-tag"><i /> 实时预警中</span>
        </div>

        <div class="header-right">
          <div class="ws-pill" :class="{ online: wsOnline }">
            <span class="ws-dot" />
            {{ wsOnline ? '实时通道在线' : '连接恢复中' }}
          </div>
          <el-badge :value="alarmStore.unread" :hidden="alarmStore.unread === 0" :max="99">
            <button class="icon-btn" title="告警中心" @click="goAlarms">
              <el-icon :size="18"><Bell /></el-icon>
            </button>
          </el-badge>
          <span class="divider" />
          <el-dropdown @command="onCommand">
            <span class="user">
              <el-avatar :size="32" class="avatar">{{ initials }}</el-avatar>
              <span class="user-meta">
                <strong>{{ auth.displayName }}</strong>
                <small>@{{ auth.user?.username }}</small>
              </span>
              <el-icon class="caret"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>{{ auth.user?.username }} · {{ roleLabel }}</el-dropdown-item>
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

const menuGroups = computed(() => {
  const groups = [
    {
      label: '总览',
      items: [
        { path: '/', title: '安全驾驶舱', icon: DataLine },
        { path: '/monitor', title: '实时监控', icon: VideoCamera },
        { path: '/alarms', title: '告警中心', icon: Bell },
      ],
    },
    {
      label: '业务配置',
      items: [
        { path: '/sites', title: '工地管理', icon: OfficeBuilding, roles: ['ADMIN', 'MANAGER'] },
        { path: '/cameras', title: '摄像头管理', icon: Camera, roles: ['ADMIN', 'MANAGER'] },
        { path: '/areas', title: '区域配置', icon: Aim, roles: ['ADMIN', 'MANAGER'] },
        { path: '/rules', title: '告警规则', icon: SetUp, roles: ['ADMIN'] },
      ],
    },
    {
      label: '分析',
      items: [
        { path: '/statistics', title: '统计分析', icon: TrendCharts },
        { path: '/screen', title: '可视化大屏', icon: FullScreen },
      ],
    },
    {
      label: '系统',
      items: [
        { path: '/users', title: '用户管理', icon: UserIcon, roles: ['ADMIN'] },
        { path: '/logs', title: '操作日志', icon: Document, roles: ['ADMIN'] },
      ],
    },
  ]
  return groups
    .map((group) => ({ ...group, items: group.items.filter((item: any) => auth.hasRole(item.roles)) }))
    .filter((group) => group.items.length > 0)
})

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
  position: sticky;
  top: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background:
    radial-gradient(420px 260px at 10% 0%, rgba(37, 99, 235, 0.28), transparent 60%),
    radial-gradient(360px 260px at 100% 100%, rgba(6, 182, 212, 0.1), transparent 55%),
    linear-gradient(180deg, #0b1220 0%, #0d1830 52%, #080f1d 100%);
  border-right: 1px solid rgba(148, 163, 184, 0.08);
  z-index: 20;
}

.aside::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(148, 163, 184, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.04) 1px, transparent 1px);
  background-size: 36px 36px;
  pointer-events: none;
  mask-image: linear-gradient(to bottom, #000, transparent 72%);
}

.brand {
  position: relative;
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 22px 18px 14px;
  color: #fff;
}

.brand-mark {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, #2563eb 0%, #06b6d4 100%);
  box-shadow: 0 8px 22px -8px rgba(37, 99, 235, 0.8);
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.brand-text strong {
  font-size: 16.5px;
  letter-spacing: 2.2px;
}

.brand-text span {
  margin-top: 2px;
  font-size: 10.5px;
  color: #7c8db5;
  letter-spacing: 0.6px;
}

.aside-status {
  position: relative;
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 0 16px 10px;
  padding: 7px 10px;
  border: 1px solid rgba(148, 163, 184, 0.1);
  border-radius: 8px;
  background: rgba(148, 163, 184, 0.05);
  color: #7c8db5;
  font-size: 10.5px;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #f59e0b;
  box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.12);
}

.status-dot.online {
  background: #34d399;
  box-shadow: 0 0 0 3px rgba(52, 211, 153, 0.12);
}

.menu {
  position: relative;
  flex: 1;
  overflow-y: auto;
  padding: 2px 10px 12px;
  border-right: none;
}

.menu :deep(.el-menu-item-group__title) {
  padding: 15px 12px 6px !important;
  font-size: 10px;
  letter-spacing: 2px;
  color: #475569;
  text-transform: uppercase;
}

.menu :deep(.el-menu-item) {
  height: 42px;
  margin: 2px 0;
  border-radius: 10px;
  font-size: 13px;
  transition: background 0.18s ease, color 0.18s ease;
}

.menu :deep(.el-menu-item .el-icon) {
  margin-right: 9px;
  font-size: 17px;
}

.menu :deep(.el-menu-item:hover) {
  background: rgba(148, 163, 184, 0.09) !important;
  color: #e2e8f0;
}

.menu :deep(.el-menu-item.is-active) {
  color: #fff !important;
  background: linear-gradient(90deg, rgba(37, 99, 235, 0.95), rgba(6, 182, 212, 0.55)) !important;
  box-shadow: 0 10px 24px -14px rgba(37, 99, 235, 0.9);
}

.menu :deep(.el-menu-item.is-active::before) {
  content: '';
  position: absolute;
  left: 0;
  top: 12px;
  bottom: 12px;
  width: 3px;
  border-radius: 3px;
  background: #7dd3fc;
}

.menu :deep(.el-menu-item.is-active .el-icon) {
  color: #e0f2fe;
}

.menu-badge {
  margin-left: auto;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: #ef4444;
  color: #fff;
  font-size: 10px;
  line-height: 18px;
  text-align: center;
  box-shadow: 0 0 0 2px rgba(239, 68, 68, 0.15);
}

.aside-footer {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-top: 1px solid rgba(148, 163, 184, 0.08);
  color: #52627f;
  font-size: 10px;
  letter-spacing: 0.2px;
}

.version {
  font-family: monospace;
  color: #3f5577;
}

.body {
  min-width: 0;
}

.header {
  position: sticky;
  top: 0;
  z-index: 15;
  height: 58px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: rgba(255, 255, 255, 0.82);
  border-bottom: 1px solid var(--border);
  backdrop-filter: blur(14px);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 9px;
}

.crumb {
  font-size: 9.5px;
  letter-spacing: 1.8px;
  color: #94a3b8;
}

.crumb-divider {
  color: #cbd5e1;
  font-size: 10px;
}

.crumb-current {
  font-size: 13.5px;
  font-weight: 600;
  letter-spacing: 0.2px;
  color: #334155;
}

.live-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 7px;
  border: 1px solid #d1fae5;
  border-radius: 999px;
  background: #ecfdf5;
  color: #059669;
  font-size: 10px;
}

.live-tag i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #10b981;
  animation: pulse 1.8s infinite;
}

@keyframes pulse {
  50% {
    opacity: 0.35;
  }
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ws-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 6px 10px;
  border-radius: 999px;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 11px;
}

.ws-pill.online {
  background: #ecfdf5;
  color: #047857;
}

.ws-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ef4444;
}

.ws-pill.online .ws-dot {
  background: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.12);
}

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: #fff;
  color: #475569;
  cursor: pointer;
  transition: all 0.18s ease;
}

.icon-btn:hover {
  color: var(--brand);
  border-color: #bfdbfe;
  background: #eff6ff;
}

.divider {
  width: 1px;
  height: 22px;
  background: var(--border);
}

.user {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 4px 6px;
  border-radius: 10px;
  cursor: pointer;
}

.user:hover {
  background: #f1f5f9;
}

.avatar {
  background: linear-gradient(135deg, #2563eb, #06b6d4);
  font-size: 13px;
  font-weight: 600;
}

.user-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.user-meta strong {
  font-size: 12.5px;
  color: #1e293b;
  font-weight: 600;
}

.user-meta small {
  font-size: 10px;
  color: #94a3b8;
}

.caret {
  color: #94a3b8;
  font-size: 12px;
}

.main {
  padding: 0;
  height: calc(100vh - 58px);
  overflow-y: auto;
  background: transparent;
}

@media (max-width: 980px) {
  .aside {
    width: 76px !important;
  }
  .brand-text,
  .aside-status span:last-child,
  .aside-footer span:first-child,
  .menu :deep(.el-menu-item span) {
    display: none;
  }
  .brand {
    justify-content: center;
    padding: 18px 0 10px;
  }
  .aside-status {
    justify-content: center;
    margin: 0 12px 8px;
    padding: 7px;
  }
  .menu :deep(.el-menu-item) {
    justify-content: center;
    padding: 0 !important;
  }
  .user-meta,
  .ws-pill,
  .crumb,
  .crumb-divider {
    display: none;
  }
}
</style>
