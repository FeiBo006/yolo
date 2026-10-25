<template>
  <div class="page dashboard">
    <section class="hero">
      <div class="hero-glow" />
      <div class="hero-copy">
        <span class="eyebrow">SAFETY COMMAND CENTER</span>
        <h1>工地安全态势</h1>
        <p>{{ today }} · 实时视频分析 · 告警处置闭环</p>
        <div class="hero-actions">
          <el-button class="hero-btn primary" @click="$router.push('/monitor')">
            进入实时监控<el-icon><ArrowRight /></el-icon>
          </el-button>
          <el-button class="hero-btn ghost" @click="$router.push('/alarms')">处理告警</el-button>
        </div>
      </div>

      <div class="hero-side">
        <div class="score-ring" :style="{ '--p': (summary.handleRate || 0) + '%' }">
          <div class="score-inner">
            <strong>{{ Math.round(summary.handleRate || 0) }}<small>%</small></strong>
            <span>今日处置率</span>
          </div>
        </div>
        <div class="hero-meta">
          <div><i class="on" /> {{ summary.cameraOnline || 0 }} 路视频在线</div>
          <div><i class="warn" /> {{ summary.pendingAlarm || 0 }} 条待处理告警</div>
        </div>
      </div>
    </section>

    <div class="stat-grid">
      <StatCard
        label="今日告警"
        :value="summary.todayAlarm || 0"
        suffix="起"
        color="#ef4444"
        :hint="`累计告警 ${summary.totalAlarm || 0} 起`"
        :icon="Bell"
      />
      <StatCard
        label="待处理告警"
        :value="summary.pendingAlarm || 0"
        suffix="起"
        color="#f59e0b"
        hint="请及时安排现场核查"
        :icon="Timer"
      />
      <StatCard
        label="在线摄像头"
        :value="summary.cameraOnline || 0"
        :suffix="`/ ${summary.cameraTotal || 0} 路`"
        color="#10b981"
        hint="算法服务心跳检测"
        :icon="VideoCamera"
      />
      <StatCard
        label="今日处置率"
        :value="summary.handleRate || 0"
        suffix="%"
        color="#2563eb"
        hint="已确认 / 已整改 / 误报"
        :icon="CircleCheck"
      />
    </div>

    <div class="grid-2" style="margin-top: 16px">
      <div class="panel">
        <div class="panel-header">
          <h3>告警趋势</h3>
          <el-radio-group v-model="days" size="small" @change="loadCharts">
            <el-radio-button :value="7">7 天</el-radio-button>
            <el-radio-button :value="14">14 天</el-radio-button>
            <el-radio-button :value="30">30 天</el-radio-button>
          </el-radio-group>
        </div>
        <ChartPanel :option="trendOption" height="296px" />
      </div>

      <div class="panel">
        <div class="panel-header">
          <h3>不安全行为排行</h3>
          <span class="muted">近 {{ days }} 天</span>
        </div>
        <ChartPanel :option="rankingOption" height="296px" />
      </div>
    </div>

    <div class="grid-2" style="margin-top: 16px">
      <div class="panel">
        <div class="panel-header">
          <h3>告警处置分布</h3>
          <span class="muted">近 {{ days }} 天</span>
        </div>
        <ChartPanel :option="statusOption" height="286px" />
      </div>

      <div class="panel">
        <div class="panel-header">
          <h3>最新告警</h3>
          <el-button text type="primary" @click="$router.push('/alarms')">查看全部</el-button>
        </div>
        <el-table :data="recentAlarms" size="small" height="286" @row-click="openAlarm">
          <el-table-column prop="cameraName" label="摄像头" min-width="112" show-overflow-tooltip />
          <el-table-column label="行为" min-width="122">
            <template #default="{ row }">
              <span class="behavior">
                <i :class="row.level?.toLowerCase()" />
                {{ behaviorLabel(row.behaviorType) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small" effect="plain">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="100">
            <template #default="{ row }">{{ formatTime(row.alarmTime).slice(5, 16) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Bell, CircleCheck, Timer, VideoCamera } from '@element-plus/icons-vue'
import StatCard from '@/components/StatCard.vue'
import ChartPanel from '@/components/ChartPanel.vue'
import { alarmApi, dashboardApi } from '@/api'
import { behaviorLabel, formatTime, statusLabel, statusType } from '@/utils/format'

const router = useRouter()
const days = ref(7)
const summary = ref<any>({})
const trend = ref<any[]>([])
const ranking = ref<any[]>([])
const status = ref<any[]>([])
const recentAlarms = ref<any[]>([])

const today = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  weekday: 'long',
})

const tooltip = {
  backgroundColor: 'rgba(15,23,42,0.92)',
  borderWidth: 0,
  textStyle: { color: '#e2e8f0', fontSize: 12 },
}

const trendOption = computed(() => ({
  tooltip: { ...tooltip, trigger: 'axis' },
  legend: { data: ['告警数', '已处置'], right: 4, top: 0, itemWidth: 10, textStyle: { color: '#64748b', fontSize: 11 } },
  grid: { left: 40, right: 16, top: 40, bottom: 28 },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: trend.value.map((i) => i.date),
    axisLine: { lineStyle: { color: '#e2e8f0' } },
    axisTick: { show: false },
    axisLabel: { color: '#94a3b8', fontSize: 11 },
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    splitLine: { lineStyle: { color: '#eef2f7' } },
    axisLabel: { color: '#94a3b8', fontSize: 11 },
  },
  series: [
    {
      name: '告警数',
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 5,
      showSymbol: false,
      data: trend.value.map((i) => i.total),
      lineStyle: { width: 3, color: '#2563eb' },
      itemStyle: { color: '#2563eb' },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0,
          y: 0,
          x2: 0,
          y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(37,99,235,0.26)' },
            { offset: 1, color: 'rgba(37,99,235,0.01)' },
          ],
        },
      },
    },
    {
      name: '已处置',
      type: 'line',
      smooth: true,
      showSymbol: false,
      data: trend.value.map((i) => i.handled),
      lineStyle: { width: 2, color: '#10b981' },
      itemStyle: { color: '#10b981' },
    },
  ],
}))

const rankingOption = computed(() => {
  const items = [...ranking.value].reverse()
  return {
    tooltip: { ...tooltip, trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 104, right: 34, top: 12, bottom: 22 },
    xAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#eef2f7' } },
      axisLabel: { color: '#94a3b8', fontSize: 11 },
    },
    yAxis: {
      type: 'category',
      data: items.map((i) => i.label),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#475569', fontSize: 12 },
    },
    series: [
      {
        type: 'bar',
        data: items.map((i) => i.count),
        barWidth: 16,
        itemStyle: {
          borderRadius: [0, 8, 8, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 1,
            y2: 0,
            colorStops: [
              { offset: 0, color: '#3b82f6' },
              { offset: 1, color: '#22d3ee' },
            ],
          },
        },
        label: { show: true, position: 'right', color: '#64748b', fontSize: 11 },
      },
    ],
  }
})

const statusOption = computed(() => {
  const total = status.value.reduce((sum, item) => sum + Number(item.count || 0), 0)
  return {
    tooltip: { ...tooltip, trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, icon: 'circle', itemWidth: 8, textStyle: { color: '#64748b', fontSize: 11 } },
    color: ['#ef4444', '#f59e0b', '#10b981', '#94a3b8'],
    title: {
      text: String(total),
      subtext: '告警总数',
      left: 'center',
      top: '31%',
      textStyle: { fontSize: 25, fontWeight: 700, color: '#0f172a' },
      subtextStyle: { fontSize: 11, color: '#94a3b8' },
      itemGap: 2,
    },
    series: [
      {
        type: 'pie',
        radius: ['48%', '70%'],
        center: ['50%', '43%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#fff', borderWidth: 3 },
        label: { show: false },
        emphasis: { scaleSize: 4 },
        data: status.value.map((i) => ({ name: i.label, value: i.count })),
      },
    ],
  }
})

async function loadSummary() {
  summary.value = await dashboardApi.summary()
}

async function loadCharts() {
  const [t, r, s] = await Promise.all([
    dashboardApi.trend(days.value),
    dashboardApi.ranking(days.value),
    dashboardApi.status(days.value),
  ])
  trend.value = t || []
  ranking.value = r || []
  status.value = s || []
}

async function loadRecent() {
  const page: any = await alarmApi.page({ page: 1, size: 6 })
  recentAlarms.value = page.records || []
}

async function loadAll() {
  await Promise.all([loadSummary(), loadCharts(), loadRecent()])
}

function openAlarm(row: any) {
  router.push({ path: '/alarms', query: { id: row.id } })
}

onMounted(loadAll)
</script>

<style scoped>
.hero {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  overflow: hidden;
  margin-bottom: 16px;
  padding: 26px 30px;
  border-radius: 20px;
  color: #fff;
  background:
    radial-gradient(520px 240px at 92% 18%, rgba(34, 211, 238, 0.24), transparent 60%),
    linear-gradient(118deg, #0a1324 0%, #102346 55%, #0b3350 100%);
  box-shadow: 0 24px 50px -34px rgba(2, 12, 27, 0.9);
}

.hero::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(148, 163, 184, 0.07) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.07) 1px, transparent 1px);
  background-size: 38px 38px;
  mask-image: linear-gradient(90deg, #000, transparent 72%);
  pointer-events: none;
}

.hero-glow {
  position: absolute;
  right: 18%;
  top: -90px;
  width: 260px;
  height: 260px;
  border-radius: 50%;
  background: rgba(37, 99, 235, 0.35);
  filter: blur(70px);
  pointer-events: none;
}

.hero-copy {
  position: relative;
  z-index: 1;
}

.hero-copy h1 {
  margin: 10px 0 6px;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 1.2px;
}

.hero-copy p {
  margin: 0;
  color: rgba(203, 213, 225, 0.78);
  font-size: 12.5px;
  letter-spacing: 0.3px;
}

.hero-actions {
  display: flex;
  gap: 10px;
  margin-top: 20px;
}

.hero-btn {
  height: 36px;
  padding: 0 16px;
  border-radius: 10px;
  font-size: 12.5px;
}

.hero-btn.primary {
  position: relative;
  overflow: hidden;
  border: none;
  color: #0b1a33;
  background: linear-gradient(90deg, #e0f2fe, #a5f3fc);
  box-shadow: 0 8px 20px -10px rgba(165, 243, 252, 0.8);
}

.hero-btn.primary::after {
  content: '';
  position: absolute;
  top: -30%;
  left: -80%;
  width: 46%;
  height: 160%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.75), transparent);
  transform: skewX(-22deg);
  transition: left 0.65s ease;
  pointer-events: none;
}

.hero-btn.primary:hover::after {
  left: 132%;
}

.hero-btn.primary:hover {
  color: #0b1a33;
  background: linear-gradient(90deg, #f0f9ff, #cffafe);
}

.hero-btn.ghost {
  border: 1px solid rgba(226, 232, 240, 0.22);
  background: rgba(15, 23, 42, 0.18);
  color: #e2e8f0;
}

.hero-btn.ghost:hover {
  border-color: rgba(165, 243, 252, 0.5);
  color: #fff;
  background: rgba(15, 23, 42, 0.32);
}

.hero-side {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 22px;
  padding-right: 8px;
}

.score-ring {
  --p: 0%;
  width: 118px;
  height: 118px;
  padding: 7px;
  border-radius: 50%;
  background: conic-gradient(#22d3ee var(--p), rgba(148, 163, 184, 0.16) 0);
  box-shadow: 0 0 34px rgba(34, 211, 238, 0.12);
}

.score-inner {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: #0c1b33;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
}

.score-inner strong {
  display: flex;
  align-items: baseline;
  font-family: var(--font-display);
  font-size: 27px;
  line-height: 1;
}

.score-inner small {
  font-size: 15px;
  color: #a5f3fc;
  margin-left: 2px;
}

.score-inner span {
  font-size: 10px;
  color: #7c8db5;
  letter-spacing: 0.4px;
}

.hero-meta {
  display: flex;
  flex-direction: column;
  gap: 10px;
  font-size: 11.5px;
  color: #cbd5e1;
}

.hero-meta div {
  display: flex;
  align-items: center;
  gap: 7px;
}

.hero-meta i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #22d3ee;
}

.hero-meta i.warn {
  background: #fbbf24;
}

.behavior {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.behavior i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #94a3b8;
}

.behavior i.high {
  background: #ef4444;
}

.behavior i.medium {
  background: #f59e0b;
}

.behavior i.low {
  background: #10b981;
}

@media (prefers-reduced-motion: reduce) {
  .hero-btn.primary::after {
    transition: none;
  }
}

@media (max-width: 900px) {
  .hero {
    flex-direction: column;
    align-items: flex-start;
  }
  .hero-side {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
