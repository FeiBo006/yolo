<template>
  <div class="screen">
    <header class="topbar">
      <div class="top-left">
        <span class="logo-dot" />
        <span class="brand">YUANYU · 智慧工地</span>
      </div>
      <div class="top-title">基于 YOLO 的工地安全行为实时检测预警系统</div>
      <div class="top-right">
        <span class="clock">{{ clock }}</span>
        <el-button text class="exit" @click="$router.push('/')">退出大屏</el-button>
      </div>
    </header>

    <div class="kpis">
      <div class="kpi">
        <span>今日告警</span>
        <strong>{{ summary.todayAlarm || 0 }}<small>起</small></strong>
      </div>
      <div class="kpi danger">
        <span>待处理</span>
        <strong>{{ summary.pendingAlarm || 0 }}<small>起</small></strong>
      </div>
      <div class="kpi success">
        <span>在线摄像头</span>
        <strong>{{ summary.cameraOnline || 0 }}<small>/ {{ summary.cameraTotal || 0 }} 路</small></strong>
      </div>
      <div class="kpi">
        <span>今日处置率</span>
        <strong>{{ summary.handleRate || 0 }}<small>%</small></strong>
      </div>
      <div class="kpi">
        <span>累计告警</span>
        <strong>{{ summary.totalAlarm || 0 }}<small>起</small></strong>
      </div>
    </div>

    <div class="main">
      <section class="col">
        <div class="card">
          <h3><i />不安全行为排行</h3>
          <ChartPanel class="chart-body" :option="rankingOption" height="100%" />
        </div>
        <div class="card">
          <h3><i />告警处置状态</h3>
          <ChartPanel class="chart-body" :option="statusOption" height="100%" />
        </div>
      </section>

      <section class="col wide">
        <div class="card">
          <h3><i />近 7 天告警趋势</h3>
          <ChartPanel class="chart-body" :option="trendOption" height="100%" />
        </div>
        <div class="card">
          <h3><i />实时告警流</h3>
          <div class="ticker">
            <div v-for="alarm in tickerAlarms" :key="alarm.alarmNo" class="ticker-row">
              <span class="ticker-time">{{ formatTime(alarm.alarmTime).slice(11) }}</span>
              <span class="ticker-cam">{{ alarm.cameraName }}</span>
              <span class="ticker-behavior">{{ behaviorLabel(alarm.behaviorType) }}</span>
              <span class="ticker-level" :class="alarm.level?.toLowerCase()">{{ levelLabel(alarm.level) }}</span>
            </div>
            <div v-if="!tickerAlarms.length" class="ticker-empty">暂无告警</div>
          </div>
        </div>
      </section>

      <section class="col">
        <div class="card camera-card">
          <h3><i />摄像头状态</h3>
          <div class="camera-list">
            <div v-for="camera in cameras" :key="camera.id" class="camera-row">
              <span class="dot" :class="{ online: camera.online && camera.enabled }" />
              <span class="camera-name">{{ camera.cameraName }}</span>
              <span class="camera-state">{{ camera.enabled ? (camera.online ? '在线' : '离线') : '停用' }}</span>
            </div>
            <div v-if="!cameras.length" class="ticker-empty">暂无摄像头</div>
          </div>
        </div>
        <div class="card grow">
          <h3><i />最新告警</h3>
          <div class="alarm-list">
            <div v-for="alarm in latestAlarms" :key="alarm.alarmNo" class="alarm-row">
              <div class="alarm-head">
                <span>{{ behaviorLabel(alarm.behaviorType) }}</span>
                <em :class="alarm.level?.toLowerCase()">{{ levelLabel(alarm.level) }}</em>
              </div>
              <div class="alarm-sub">{{ alarm.cameraName }} · {{ formatTime(alarm.alarmTime).slice(5) }}</div>
            </div>
            <div v-if="!latestAlarms.length" class="ticker-empty">暂无告警</div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import ChartPanel from '@/components/ChartPanel.vue'
import { alarmApi, cameraApi, dashboardApi } from '@/api'
import { useAlarmStore } from '@/stores/alarm'
import { behaviorLabel, formatTime, levelLabel } from '@/utils/format'

const alarmStore = useAlarmStore()
const summary = ref<any>({})
const trend = ref<any[]>([])
const ranking = ref<any[]>([])
const status = ref<any[]>([])
const cameras = ref<any[]>([])
const fetchedAlarms = ref<any[]>([])
const clock = ref('')
let clockTimer: number | undefined
let refreshTimer: number | undefined

const latestAlarms = computed(() => (alarmStore.latest.length ? alarmStore.latest.slice(0, 8) : fetchedAlarms.value.slice(0, 6)))
const tickerAlarms = computed(() => (alarmStore.latest.length ? alarmStore.latest.slice(0, 10) : fetchedAlarms.value.slice(0, 10)))

const axis = {
  axisLine: { lineStyle: { color: 'rgba(148,163,184,0.25)' } },
  axisLabel: { color: '#94a3b8', fontSize: 11 },
}
const split = { splitLine: { lineStyle: { color: 'rgba(148,163,184,0.12)' } } }

const trendOption = computed(() => ({
  tooltip: { trigger: 'axis', backgroundColor: 'rgba(15,23,42,0.92)', borderColor: '#1e40af', textStyle: { color: '#e2e8f0' } },
  legend: { data: ['告警数', '已处置'], right: 10, textStyle: { color: '#94a3b8' } },
  grid: { left: 40, right: 20, top: 42, bottom: 28 },
  xAxis: { type: 'category', boundaryGap: false, data: trend.value.map((i) => i.date), ...axis },
  yAxis: { type: 'value', minInterval: 1, ...axis, ...split },
  series: [
    {
      name: '告警数',
      type: 'line',
      smooth: true,
      symbolSize: 6,
      data: trend.value.map((i) => i.total),
      lineStyle: { width: 3, color: '#38bdf8' },
      itemStyle: { color: '#38bdf8' },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0,
          y: 0,
          x2: 0,
          y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(56,189,248,0.4)' },
            { offset: 1, color: 'rgba(56,189,248,0.02)' },
          ],
        },
      },
    },
    {
      name: '已处置',
      type: 'line',
      smooth: true,
      data: trend.value.map((i) => i.handled),
      lineStyle: { width: 2, color: '#34d399' },
      itemStyle: { color: '#34d399' },
    },
  ],
}))

const rankingOption = computed(() => {
  const items = [...ranking.value].reverse()
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 94, right: 30, top: 10, bottom: 20 },
    xAxis: { type: 'value', minInterval: 1, ...axis, ...split },
    yAxis: { type: 'category', data: items.map((i) => i.label), ...axis, axisLine: { show: false }, axisTick: { show: false } },
    series: [
      {
        type: 'bar',
        data: items.map((i) => i.count),
        barWidth: 14,
        itemStyle: {
          borderRadius: [0, 7, 7, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 1,
            y2: 0,
            colorStops: [
              { offset: 0, color: '#2563eb' },
              { offset: 1, color: '#22d3ee' },
            ],
          },
        },
        label: { show: true, position: 'right', color: '#94a3b8' },
      },
    ],
  }
})

const statusOption = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { bottom: 0, icon: 'circle', textStyle: { color: '#94a3b8' }, itemWidth: 8 },
  color: ['#f87171', '#fbbf24', '#34d399', '#94a3b8'],
  series: [
    {
      type: 'pie',
      radius: ['46%', '68%'],
      center: ['50%', '42%'],
      itemStyle: { borderColor: '#0b1220', borderWidth: 3 },
      label: { color: '#cbd5e1', fontSize: 11, formatter: '{b}\n{c}' },
      data: status.value.map((i) => ({ name: i.label, value: i.count })),
    },
  ],
}))

function tickClock() {
  clock.value = new Date().toLocaleString('zh-CN', { hour12: false })
}

async function load() {
  const [s, t, r, st, c] = await Promise.all([
    dashboardApi.summary(),
    dashboardApi.trend(7),
    dashboardApi.ranking(7),
    dashboardApi.status(7),
    cameraApi.list(),
  ])
  summary.value = s || {}
  trend.value = t || []
  ranking.value = r || []
  status.value = st || []
  cameras.value = c || []
  if (!alarmStore.latest.length) {
    const page: any = await alarmApi.page({ page: 1, size: 12 })
    fetchedAlarms.value = page.records || []
    alarmStore.setList(fetchedAlarms.value)
  }
}

onMounted(() => {
  tickClock()
  clockTimer = window.setInterval(tickClock, 1000)
  load()
  refreshTimer = window.setInterval(load, 30000)
})

onBeforeUnmount(() => {
  if (clockTimer) window.clearInterval(clockTimer)
  if (refreshTimer) window.clearInterval(refreshTimer)
})
</script>

<style scoped>
.screen {
  min-height: 100vh;
  background:
    radial-gradient(circle at 50% -20%, rgba(37, 99, 235, 0.28), transparent 55%),
    radial-gradient(circle at 100% 100%, rgba(6, 182, 212, 0.12), transparent 40%),
    #060d1a;
  color: #e2e8f0;
  padding: 0 18px 18px;
  overflow: hidden;
}

.topbar {
  height: 64px;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  border-bottom: 1px solid rgba(56, 189, 248, 0.18);
}

.top-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #22d3ee;
  box-shadow: 0 0 12px #22d3ee;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  50% {
    opacity: 0.45;
  }
}

.brand {
  font-size: 14px;
  letter-spacing: 3px;
  color: #7dd3fc;
}

.top-title {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 3px;
  background: linear-gradient(90deg, #93c5fd, #67e8f9, #93c5fd);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  text-shadow: 0 0 30px rgba(56, 189, 248, 0.18);
}

.top-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 14px;
}

.clock {
  font-size: 13px;
  color: #7dd3fc;
  letter-spacing: 1px;
}

.exit {
  color: #64748b;
}

.kpis {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  padding: 14px 0;
}

.kpi {
  background: linear-gradient(160deg, rgba(30, 58, 138, 0.28), rgba(15, 23, 42, 0.65));
  border: 1px solid rgba(56, 189, 248, 0.16);
  border-radius: 10px;
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.kpi span {
  font-size: 12px;
  color: #94a3b8;
  letter-spacing: 1px;
}

.kpi strong {
  font-size: 28px;
  font-weight: 700;
  color: #f8fafc;
  font-family: 'DIN Alternate', 'SF Pro Display', sans-serif;
}

.kpi small {
  font-size: 11px;
  color: #64748b;
  margin-left: 4px;
  font-weight: 400;
}

.kpi.danger strong {
  color: #fca5a5;
}

.kpi.success strong {
  color: #6ee7b7;
}

.main {
  display: grid;
  grid-template-columns: 1fr 1.5fr 1fr;
  gap: 12px;
  height: calc(100vh - 64px - 108px);
}

.col {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}

.card {
  background: linear-gradient(170deg, rgba(15, 32, 60, 0.72), rgba(8, 18, 36, 0.72));
  border: 1px solid rgba(56, 189, 248, 0.14);
  border-radius: 10px;
  padding: 10px 14px 6px;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.card.grow {
  flex: 1;
}

.camera-card {
  flex: 0 0 250px;
}

.chart-body {
  flex: 1;
  min-height: 0;
}

.card h3 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: #cbd5e1;
  letter-spacing: 1px;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 4px 0 8px;
}

.card h3 i {
  width: 3px;
  height: 13px;
  background: linear-gradient(#38bdf8, #2563eb);
  border-radius: 2px;
}

.ticker {
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow-y: auto;
  flex: 1;
  min-height: 0;
}

.ticker-row {
  display: grid;
  grid-template-columns: 66px 1fr 1.1fr 46px;
  gap: 8px;
  font-size: 12px;
  padding: 6px 4px;
  border-bottom: 1px dashed rgba(148, 163, 184, 0.1);
  color: #cbd5e1;
}

.ticker-time {
  color: #64748b;
  font-family: monospace;
}

.ticker-cam {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ticker-behavior {
  color: #fbbf24;
}

.ticker-level.high {
  color: #f87171;
}

.ticker-level.medium {
  color: #fbbf24;
}

.ticker-level.low {
  color: #34d399;
}

.camera-list,
.alarm-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow-y: auto;
}

.camera-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 2px;
  font-size: 12px;
  border-bottom: 1px dashed rgba(148, 163, 184, 0.08);
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #475569;
}

.dot.online {
  background: #34d399;
  box-shadow: 0 0 8px rgba(52, 211, 153, 0.8);
}

.camera-name {
  flex: 1;
  color: #cbd5e1;
}

.camera-state {
  color: #64748b;
}

.alarm-row {
  padding: 8px 2px;
  border-bottom: 1px dashed rgba(148, 163, 184, 0.08);
}

.alarm-head {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #e2e8f0;
}

.alarm-head em {
  font-style: normal;
  font-size: 11px;
}

.alarm-head em.high {
  color: #f87171;
}

.alarm-head em.medium {
  color: #fbbf24;
}

.alarm-sub {
  font-size: 11px;
  color: #64748b;
  margin-top: 3px;
}

.ticker-empty {
  color: #475569;
  font-size: 12px;
  padding: 16px 0;
  text-align: center;
}

@media (max-width: 1280px) {
  .main {
    grid-template-columns: 1fr 1fr;
  }
  .col:last-child {
    display: none;
  }
  .top-title {
    font-size: 17px;
  }
}
</style>
