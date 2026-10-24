<template>
  <div class="page monitor-page">
    <div class="page-title">
      <h2>实时监控<span class="sub">YOLO 推理画面 · 服务端标注 · 秒级告警</span></h2>
      <div>
        <el-button :icon="FullScreen" @click="$router.push('/screen')">可视化大屏</el-button>
        <el-button :icon="Refresh" @click="reloadStream">刷新画面</el-button>
      </div>
    </div>

    <div class="monitor-grid">
      <aside class="panel camera-list">
        <div class="panel-header">
          <h3>摄像头</h3>
          <span class="muted">{{ cameras.length }} 路</span>
        </div>
        <div
          v-for="camera in cameras"
          :key="camera.id"
          class="camera-item"
          :class="{ active: camera.id === selectedId, disabled: !camera.enabled }"
          @click="selectCamera(camera)"
        >
          <div class="camera-icon">
            <el-icon><VideoCamera /></el-icon>
            <span class="dot" :class="{ online: camera.online && camera.enabled }" />
          </div>
          <div class="camera-meta">
            <div class="camera-name">{{ camera.cameraName }}</div>
            <div class="camera-sub">{{ camera.siteName || '未绑定工地' }}</div>
          </div>
          <el-tag size="small" :type="camera.streamType === 'DEMO' ? 'warning' : 'info'" effect="plain">
            {{ camera.streamType }}
          </el-tag>
        </div>
        <el-empty v-if="!cameras.length" description="暂无摄像头" :image-size="70" />
      </aside>

      <section class="panel video-panel">
        <div class="panel-header">
          <div class="video-title">
            <h3>{{ selected?.cameraName || '未选择摄像头' }}</h3>
            <el-tag v-if="selected" size="small" :type="selected.online ? 'success' : 'info'" effect="plain">
              {{ selected.online ? '在线' : '离线' }}
            </el-tag>
            <el-tag v-if="selected && !selected.enabled" size="small" type="danger" effect="plain">已停用</el-tag>
          </div>
          <span class="muted">{{ selected?.location }}</span>
        </div>

        <div class="video-wrap">
          <img
            v-if="selected && selected.enabled && !streamError"
            :key="streamKey"
            :src="streamUrl"
            class="video-frame"
            alt="实时视频流"
            @error="onStreamError"
          />
          <div v-else class="video-placeholder">
            <el-icon :size="46"><VideoCamera /></el-icon>
            <p v-if="!selected">请选择左侧摄像头</p>
            <p v-else-if="!selected.enabled">该摄像头已停用</p>
            <p v-else>画面加载中或算法服务未启动</p>
            <el-button v-if="selected" size="small" @click="reloadStream">重新加载</el-button>
          </div>
          <div v-if="selected && selected.enabled" class="live-badge">
            <span class="live-dot" />LIVE
          </div>
        </div>

        <div class="metric-row">
          <div class="metric">
            <span class="metric-label">当前人数</span>
            <strong>{{ latest?.personCount ?? 0 }}</strong>
          </div>
          <div class="metric danger">
            <span class="metric-label">未戴安全帽</span>
            <strong>{{ latest?.noHelmetCount ?? 0 }}</strong>
          </div>
          <div class="metric warning">
            <span class="metric-label">未穿反光衣</span>
            <strong>{{ latest?.noVestCount ?? 0 }}</strong>
          </div>
          <div class="metric">
            <span class="metric-label">推理 FPS</span>
            <strong>{{ latest?.fps ?? 0 }}</strong>
          </div>
          <div class="metric">
            <span class="metric-label">单帧耗时</span>
            <strong>{{ latest?.inferenceMs ?? 0 }}<small>ms</small></strong>
          </div>
        </div>
      </section>

      <aside class="panel alarm-panel">
        <div class="panel-header">
          <h3>实时告警</h3>
          <el-tag type="danger" effect="dark" size="small">{{ alarms.length }} 条告警</el-tag>
        </div>
        <div class="alarm-scroll">
          <div v-for="alarm in alarms" :key="alarm.alarmNo" class="alarm-item" @click="openAlarm(alarm)">
            <div class="alarm-top">
              <span class="alarm-behavior">{{ behaviorLabel(alarm.behaviorType) }}</span>
              <el-tag :type="levelType(alarm.level)" size="small" effect="dark">{{ levelLabel(alarm.level) }}</el-tag>
            </div>
            <div class="alarm-meta">
              <span>{{ alarm.cameraName }}</span>
              <span>{{ formatTime(alarm.alarmTime).slice(11) }}</span>
            </div>
            <div class="alarm-conf">置信度 {{ percent(alarm.confidence) }} · {{ statusLabel(alarm.status) }}</div>
          </div>
          <el-empty v-if="!alarms.length" description="暂无告警，现场安全" :image-size="64" />
        </div>
      </aside>
    </div>

    <el-dialog v-model="detailVisible" title="告警详情" width="720px">
      <div v-if="currentAlarm" class="detail">
        <img v-if="currentAlarm.snapshotUrl" :src="currentAlarm.snapshotUrl" class="detail-img" />
        <div v-else class="detail-img placeholder">暂无截图</div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="摄像头">{{ currentAlarm.cameraName }}</el-descriptions-item>
          <el-descriptions-item label="行为">{{ behaviorLabel(currentAlarm.behaviorType) }}</el-descriptions-item>
          <el-descriptions-item label="置信度">{{ percent(currentAlarm.confidence) }}</el-descriptions-item>
          <el-descriptions-item label="级别">{{ levelLabel(currentAlarm.level) }}</el-descriptions-item>
          <el-descriptions-item label="时间">{{ formatTime(currentAlarm.alarmTime) }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(currentAlarm.status) }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" @click="goHandle">前往处置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { FullScreen, Refresh, VideoCamera } from '@element-plus/icons-vue'
import { alarmApi, cameraApi, detectionApi } from '@/api'
import { useAlarmStore } from '@/stores/alarm'
import { behaviorLabel, formatTime, levelLabel, levelType, percent, statusLabel } from '@/utils/format'

const router = useRouter()
const alarmStore = useAlarmStore()
const cameras = ref<any[]>([])
const selectedId = ref<number | null>(null)
const latest = ref<any>(null)
const streamKey = ref(0)
const streamError = ref(false)
const detailVisible = ref(false)
const currentAlarm = ref<any>(null)

const selected = computed(() => cameras.value.find((c) => c.id === selectedId.value))
const streamUrl = computed(() =>
  selectedId.value ? `/ai/streams/${selectedId.value}/mjpeg?t=${streamKey.value}` : '',
)
const alarms = computed(() => alarmStore.latest.slice(0, 30))

let detectionTimer: number | undefined
let cameraTimer: number | undefined

function selectCamera(camera: any) {
  selectedId.value = camera.id
  streamError.value = false
  streamKey.value += 1
  loadDetection()
}

function reloadStream() {
  streamError.value = false
  streamKey.value += 1
}

function onStreamError() {
  streamError.value = true
}

async function loadCameras() {
  const data: any = await cameraApi.list()
  cameras.value = data || []
  if (!selectedId.value && cameras.value.length) {
    const preferred = cameras.value.find((c) => c.enabled && c.streamType === 'DEMO') || cameras.value[0]
    selectCamera(preferred)
  }
}

async function loadDetection() {
  if (!selectedId.value) return
  try {
    latest.value = await detectionApi.latest(selectedId.value)
  } catch {
    latest.value = null
  }
}

async function loadAlarms() {
  try {
    const page: any = await alarmApi.page({ page: 1, size: 20 })
    alarmStore.setList(page.records || [])
  } catch {
    // 忽略
  }
}

function openAlarm(alarm: any) {
  currentAlarm.value = alarm
  detailVisible.value = true
}

function goHandle() {
  detailVisible.value = false
  router.push({ path: '/alarms', query: { id: currentAlarm.value?.id } })
}

onMounted(async () => {
  await Promise.all([loadCameras(), loadAlarms()])
  detectionTimer = window.setInterval(loadDetection, 8000)
  cameraTimer = window.setInterval(loadCameras, 30000)
})

onBeforeUnmount(() => {
  if (detectionTimer) window.clearInterval(detectionTimer)
  if (cameraTimer) window.clearInterval(cameraTimer)
})
</script>

<style scoped>
.monitor-page {
  height: calc(100vh - 62px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.monitor-grid {
  flex: 1;
  display: grid;
  grid-template-columns: 236px minmax(0, 1fr) 322px;
  gap: 14px;
  min-height: 0;
}

.camera-list,
.alarm-panel {
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.camera-list {
  padding: 14px 12px;
}

.camera-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 9px;
  cursor: pointer;
  border: 1px solid transparent;
  margin-bottom: 6px;
}

.camera-item:hover {
  background: #f8fafc;
}

.camera-item.active {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.camera-item.disabled {
  opacity: 0.6;
}

.camera-icon {
  position: relative;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #475569;
  flex-shrink: 0;
}

.camera-icon .dot {
  position: absolute;
  right: -2px;
  top: -2px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #cbd5e1;
  border: 1px solid #fff;
}

.camera-icon .dot.online {
  background: #10b981;
}

.camera-meta {
  flex: 1;
  min-width: 0;
}

.camera-name {
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.camera-sub {
  font-size: 11px;
  color: var(--muted);
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.video-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.video-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.video-wrap {
  position: relative;
  flex: 1;
  min-height: 0;
  background: #0b1220;
  border-radius: 10px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.video-frame {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

.video-placeholder {
  color: #64748b;
  text-align: center;
  font-size: 13px;
}

.live-badge {
  position: absolute;
  left: 12px;
  top: 12px;
  background: rgba(15, 23, 42, 0.65);
  color: #fff;
  font-size: 11px;
  padding: 3px 9px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  gap: 5px;
  letter-spacing: 1px;
}

.live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ef4444;
  animation: blink 1.2s infinite;
}

@keyframes blink {
  50% {
    opacity: 0.25;
  }
}

.metric-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
  padding-top: 12px;
}

.metric {
  background: #f8fafc;
  border-radius: 9px;
  padding: 9px 12px;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.metric-label {
  font-size: 11px;
  color: var(--muted);
}

.metric strong {
  font-size: 20px;
}

.metric strong small {
  font-size: 11px;
  font-weight: 400;
  color: var(--muted);
  margin-left: 2px;
}

.metric.danger strong {
  color: #dc2626;
}

.metric.warning strong {
  color: #d97706;
}

.alarm-panel {
  padding: 14px;
}

.alarm-scroll {
  flex: 1;
  overflow-y: auto;
}

.alarm-item {
  padding: 10px 11px;
  border-radius: 9px;
  border: 1px solid #eef2f7;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.15s;
}

.alarm-item:hover {
  border-color: #bfdbfe;
  background: #f8fafc;
}

.alarm-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.alarm-behavior {
  font-size: 13px;
  font-weight: 600;
}

.alarm-meta {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--muted);
  margin-top: 5px;
}

.alarm-conf {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 3px;
}

.detail-img {
  width: 100%;
  max-height: 340px;
  object-fit: contain;
  background: #0b1220;
  border-radius: 8px;
  margin-bottom: 14px;
}

.detail-img.placeholder {
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #64748b;
}

@media (max-width: 1280px) {
  .monitor-grid {
    grid-template-columns: 200px minmax(0, 1fr);
  }
  .alarm-panel {
    display: none;
  }
}
</style>
