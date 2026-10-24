<template>
  <div class="page">
    <div class="page-title">
      <h2>统计分析<span class="sub">按时间维度统计告警趋势、行为分布与处置效率</span></h2>
      <div class="toolbar" style="margin: 0">
        <el-radio-group v-model="days" size="default" @change="load">
          <el-radio-button :value="7">近 7 天</el-radio-button>
          <el-radio-button :value="30">近 30 天</el-radio-button>
          <el-radio-button :value="90">近 90 天</el-radio-button>
        </el-radio-group>
        <el-button :icon="Download" @click="exportExcel">导出明细</el-button>
      </div>
    </div>

    <div class="stat-grid">
      <StatCard label="今日告警" :value="summary.todayAlarm || 0" suffix="起" color="#ef4444" hint="今日新增告警" :icon="Bell" />
      <StatCard label="待处理" :value="summary.pendingAlarm || 0" suffix="起" color="#f59e0b" hint="需现场核查" :icon="Timer" />
      <StatCard label="处置率" :value="summary.handleRate || 0" suffix="%" color="#10b981" hint="今日处置完成比例" :icon="CircleCheck" />
      <StatCard label="累计告警" :value="summary.totalAlarm || 0" suffix="起" color="#2563eb" hint="系统上线以来" :icon="TrendCharts" />
    </div>

    <div class="grid-2" style="margin-top: 16px">
      <div class="panel">
        <div class="panel-header">
          <h3>告警趋势</h3>
          <span class="muted">近 {{ days }} 天</span>
        </div>
        <ChartPanel :option="trendOption" height="300px" />
      </div>
      <div class="panel">
        <div class="panel-header">
          <h3>行为类型分布</h3>
          <span class="muted">近 {{ days }} 天</span>
        </div>
        <ChartPanel :option="rankingOption" height="300px" />
      </div>
    </div>

    <div class="grid-2" style="margin-top: 16px">
      <div class="panel">
        <div class="panel-header">
          <h3>处置状态</h3>
        </div>
        <ChartPanel :option="statusOption" height="280px" />
      </div>
      <div class="panel">
        <div class="panel-header">
          <h3>行为明细</h3>
          <span class="muted">共 {{ totalRanking }} 起</span>
        </div>
        <el-table :data="ranking" size="small" height="280">
          <el-table-column label="行为类型" min-width="140">
            <template #default="{ row }">{{ row.label }}</template>
          </el-table-column>
          <el-table-column prop="count" label="数量" width="80" />
          <el-table-column label="占比" min-width="160">
            <template #default="{ row }">
              <el-progress :percentage="percentage(row.count)" :stroke-width="10" :color="barColor(row.type)" />
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Bell, CircleCheck, Download, Timer, TrendCharts } from '@element-plus/icons-vue'
import StatCard from '@/components/StatCard.vue'
import ChartPanel from '@/components/ChartPanel.vue'
import { dashboardApi, exportAlarms } from '@/api'

const days = ref(7)
const summary = ref<any>({})
const trend = ref<any[]>([])
const ranking = ref<any[]>([])
const status = ref<any[]>([])
const totalRanking = computed(() => ranking.value.reduce((sum, item) => sum + item.count, 0))

const trendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['告警数', '已处置'], right: 0 },
  grid: { left: 44, right: 18, top: 42, bottom: 30 },
  xAxis: {
    type: 'category',
    data: trend.value.map((i) => i.date),
    axisLine: { lineStyle: { color: '#e2e8f0' } },
    axisLabel: { color: '#94a3b8' },
  },
  yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#f1f5f9' } }, axisLabel: { color: '#94a3b8' } },
  series: [
    {
      name: '告警数',
      type: 'bar',
      barWidth: 16,
      data: trend.value.map((i) => i.total),
      itemStyle: { color: '#2563eb', borderRadius: [4, 4, 0, 0] },
    },
    {
      name: '已处置',
      type: 'line',
      smooth: true,
      data: trend.value.map((i) => i.handled),
      itemStyle: { color: '#10b981' },
    },
  ],
}))

const rankingOption = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { bottom: 0, icon: 'circle', textStyle: { color: '#64748b' } },
  color: ['#ef4444', '#f59e0b', '#2563eb', '#8b5cf6', '#06b6d4', '#94a3b8'],
  series: [
    {
      type: 'pie',
      radius: ['42%', '66%'],
      center: ['50%', '44%'],
      itemStyle: { borderColor: '#fff', borderWidth: 3 },
      label: { formatter: '{b}\n{c}' },
      data: ranking.value.map((i) => ({ name: i.label, value: i.count })),
    },
  ],
}))

const statusOption = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { bottom: 0, icon: 'circle', textStyle: { color: '#64748b' } },
  color: ['#ef4444', '#f59e0b', '#10b981', '#94a3b8'],
  series: [
    {
      type: 'pie',
      roseType: 'radius',
      radius: ['26%', '66%'],
      center: ['50%', '44%'],
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { formatter: '{b}\n{c}' },
      data: status.value.map((i) => ({ name: i.label, value: i.count })),
    },
  ],
}))

function percentage(count: number) {
  return totalRanking.value === 0 ? 0 : Math.round((count / totalRanking.value) * 100)
}

function barColor(type: string) {
  const map: Record<string, string> = {
    NO_HELMET: '#ef4444',
    NO_VEST: '#f59e0b',
    INTRUSION: '#2563eb',
    SMOKING: '#8b5cf6',
    PHONE: '#06b6d4',
  }
  return map[type] || '#94a3b8'
}

async function load() {
  const [s, t, r, st] = await Promise.all([
    dashboardApi.summary(),
    dashboardApi.trend(days.value),
    dashboardApi.ranking(days.value),
    dashboardApi.status(days.value),
  ])
  summary.value = s || {}
  trend.value = t || []
  ranking.value = r || []
  status.value = st || []
}

async function exportExcel() {
  try {
    await exportAlarms({})
    ElMessage.success('导出已开始')
  } catch {
    ElMessage.error('导出失败')
  }
}

onMounted(load)
</script>
