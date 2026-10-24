<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>安全驾驶舱<span class="sub">{{ today }} · 数据实时更新</span></h2>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新数据</el-button>
    </div>

    <div class="stat-grid">
      <StatCard label="今日告警" :value="summary.todayAlarm || 0" suffix="起" color="#ef4444" :hint="`累计告警 ${summary.totalAlarm || 0} 起`" />
      <StatCard label="待处理告警" :value="summary.pendingAlarm || 0" suffix="起" color="#f59e0b" hint="请及时安排现场核查" />
      <StatCard label="在线摄像头" :value="summary.cameraOnline || 0" :suffix="`/ ${summary.cameraTotal || 0} 路`" color="#10b981" hint="算法服务心跳检测" />
      <StatCard label="今日处置率" :value="summary.handleRate || 0" suffix="%" color="#2563eb" hint="已确认 / 已整改 / 误报" />
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
        <ChartPanel :option="trendOption" height="300px" />
      </div>

      <div class="panel">
        <div class="panel-header">
          <h3>不安全行为排行</h3>
          <span class="muted">近 {{ days }} 天</span>
        </div>
        <ChartPanel :option="rankingOption" height="300px" />
      </div>
    </div>

    <div class="grid-2" style="margin-top: 16px">
      <div class="panel">
        <div class="panel-header">
          <h3>告警处置分布</h3>
          <span class="muted">近 {{ days }} 天</span>
        </div>
        <ChartPanel :option="statusOption" height="280px" />
      </div>

      <div class="panel">
        <div class="panel-header">
          <h3>最新告警</h3>
          <el-button text type="primary" @click="$router.push('/alarms')">查看全部</el-button>
        </div>
        <el-table :data="recentAlarms" size="small" height="280" @row-click="openAlarm">
          <el-table-column prop="cameraName" label="摄像头" min-width="120" show-overflow-tooltip />
          <el-table-column label="行为" width="120">
            <template #default="{ row }">{{ behaviorLabel(row.behaviorType) }}</template>
          </el-table-column>
          <el-table-column label="级别" width="70">
            <template #default="{ row }">
              <el-tag :type="levelType(row.level)" size="small" effect="dark">{{ levelLabel(row.level) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="150">
            <template #default="{ row }">{{ formatTime(row.alarmTime).slice(5) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import StatCard from '@/components/StatCard.vue'
import ChartPanel from '@/components/ChartPanel.vue'
import { alarmApi, dashboardApi } from '@/api'
import { behaviorLabel, formatTime, levelLabel, levelType } from '@/utils/format'

const router = useRouter()
const loading = ref(false)
const days = ref(7)
const summary = ref<any>({})
const trend = ref<any[]>([])
const ranking = ref<any[]>([])
const status = ref<any[]>([])
const recentAlarms = ref<any[]>([])

const today = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })

const trendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['告警数', '已处置'], right: 0, top: 0, textStyle: { color: '#64748b' } },
  grid: { left: 42, right: 18, top: 42, bottom: 30 },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: trend.value.map((i) => i.date),
    axisLine: { lineStyle: { color: '#e2e8f0' } },
    axisLabel: { color: '#94a3b8' },
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    splitLine: { lineStyle: { color: '#f1f5f9' } },
    axisLabel: { color: '#94a3b8' },
  },
  series: [
    {
      name: '告警数',
      type: 'line',
      smooth: true,
      symbolSize: 6,
      data: trend.value.map((i) => i.total),
      itemStyle: { color: '#2563eb' },
      areaStyle: { color: 'rgba(37,99,235,0.12)' },
    },
    {
      name: '已处置',
      type: 'line',
      smooth: true,
      symbolSize: 6,
      data: trend.value.map((i) => i.handled),
      itemStyle: { color: '#10b981' },
    },
  ],
}))

const rankingOption = computed(() => {
  const items = [...ranking.value].reverse()
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 110, right: 30, top: 16, bottom: 24 },
    xAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#f1f5f9' } }, axisLabel: { color: '#94a3b8' } },
    yAxis: {
      type: 'category',
      data: items.map((i) => i.label),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#475569' },
    },
    series: [
      {
        type: 'bar',
        data: items.map((i) => i.count),
        barWidth: 18,
        itemStyle: {
          borderRadius: [0, 8, 8, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 1,
            y2: 0,
            colorStops: [
              { offset: 0, color: '#f59e0b' },
              { offset: 1, color: '#fb7185' },
            ],
          },
        },
        label: { show: true, position: 'right', color: '#64748b' },
      },
    ],
  }
})

const statusOption = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { bottom: 0, icon: 'circle', textStyle: { color: '#64748b' } },
  color: ['#ef4444', '#f59e0b', '#10b981', '#94a3b8'],
  series: [
    {
      type: 'pie',
      radius: ['46%', '68%'],
      center: ['50%', '44%'],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: '#fff', borderWidth: 3 },
      label: { formatter: '{b}\n{c}' },
      data: status.value.map((i) => ({ name: i.label, value: i.count })),
    },
  ],
}))

async function loadSummary() {
  summary.value = await dashboardApi.summary()
}

async function loadCharts() {
  const [t, r, s] = await Promise.all([dashboardApi.trend(days.value), dashboardApi.ranking(days.value), dashboardApi.status(days.value)])
  trend.value = t || []
  ranking.value = r || []
  status.value = s || []
}

async function loadRecent() {
  const page: any = await alarmApi.page({ page: 1, size: 6 })
  recentAlarms.value = page.records || []
}

async function loadAll() {
  loading.value = true
  try {
    await Promise.all([loadSummary(), loadCharts(), loadRecent()])
  } finally {
    loading.value = false
  }
}

function openAlarm(row: any) {
  router.push({ path: '/alarms', query: { id: row.id } })
}

onMounted(loadAll)
</script>
