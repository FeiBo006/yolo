<template>
  <div class="page">
    <div class="page-title">
      <h2>告警中心<span class="sub">按行为、摄像头、状态与时间检索，支持现场处置闭环</span></h2>
      <div>
        <el-button :icon="Download" @click="exportExcel">导出 Excel</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="panel">
      <div class="toolbar">
        <el-select v-model="filters.status" placeholder="处置状态" clearable style="width: 126px">
          <el-option v-for="(label, value) in STATUS_LABELS" :key="value" :label="label" :value="value" />
        </el-select>
        <el-select v-model="filters.behaviorType" placeholder="行为类型" clearable style="width: 145px">
          <el-option v-for="(label, value) in BEHAVIOR_LABELS" :key="value" :label="label" :value="value" />
        </el-select>
        <el-select v-model="filters.cameraId" placeholder="摄像头" clearable style="width: 160px">
          <el-option v-for="camera in cameras" :key="camera.id" :label="camera.cameraName" :value="camera.id" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="width: 228px"
        />
        <el-input v-model="filters.keyword" placeholder="摄像头 / 告警编号" clearable style="width: 168px" @keyup.enter="search" />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="records" stripe height="calc(100vh - 360px)">
        <el-table-column label="截图" width="76">
          <template #default="{ row }">
            <el-image
              v-if="row.snapshotUrl"
              :src="row.snapshotUrl"
              :preview-src-list="[row.snapshotUrl]"
              fit="cover"
              preview-teleported
              class="thumb"
            />
            <div v-else class="thumb empty">无</div>
          </template>
        </el-table-column>
        <el-table-column prop="alarmNo" label="告警编号" min-width="170" show-overflow-tooltip />
        <el-table-column prop="cameraName" label="摄像头" min-width="110" show-overflow-tooltip />
        <el-table-column label="行为类型" width="130">
          <template #default="{ row }">
            <span class="behavior">
              <i :class="row.level?.toLowerCase()" />
              {{ behaviorLabel(row.behaviorType) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="级别" width="70">
          <template #default="{ row }">
            <el-tag :type="levelType(row.level)" size="small" effect="dark">{{ levelLabel(row.level) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="置信度" width="78">
          <template #default="{ row }">{{ percent(row.confidence) }}</template>
        </el-table-column>
        <el-table-column label="告警时间" width="150">
          <template #default="{ row }">{{ formatTime(row.alarmTime).slice(5) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="84">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small" effect="plain">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handlerName" label="处置人" width="78">
          <template #default="{ row }">{{ row.handlerName || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'PENDING'" text type="warning" size="small" @click="quickHandle(row)">处置</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="load"
          @size-change="search"
        />
      </div>
    </div>

    <el-drawer v-model="drawer" title="告警详情" size="560px">
      <div v-if="current" class="detail">
        <el-image
          v-if="current.snapshotUrl"
          :src="current.snapshotUrl"
          :preview-src-list="[current.snapshotUrl]"
          fit="contain"
          class="detail-img"
          preview-teleported
        />
        <div v-else class="detail-img placeholder">该告警暂无截图</div>

        <el-descriptions :column="2" border size="small" class="desc">
          <el-descriptions-item label="告警编号" :span="2">{{ current.alarmNo }}</el-descriptions-item>
          <el-descriptions-item label="摄像头">{{ current.cameraName }}</el-descriptions-item>
          <el-descriptions-item label="行为类型">{{ behaviorLabel(current.behaviorType) }}</el-descriptions-item>
          <el-descriptions-item label="风险级别">{{ levelLabel(current.level) }}</el-descriptions-item>
          <el-descriptions-item label="置信度">{{ percent(current.confidence) }}</el-descriptions-item>
          <el-descriptions-item label="告警时间" :span="2">{{ formatTime(current.alarmTime) }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">{{ statusLabel(current.status) }}</el-descriptions-item>
          <el-descriptions-item label="处置人">{{ current.handlerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="处置时间" :span="2">{{ formatTime(current.handleTime) }}</el-descriptions-item>
          <el-descriptions-item label="处置备注" :span="2">{{ current.handleRemark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="current.status === 'PENDING'" class="handle-box">
          <h4>现场处置</h4>
          <el-form label-position="top">
            <el-form-item label="处置结果">
              <el-radio-group v-model="handleForm.status">
                <el-radio-button value="CONFIRMED">已确认</el-radio-button>
                <el-radio-button value="REPAIRED">已整改</el-radio-button>
                <el-radio-button value="FALSE_ALARM">误报</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="处置说明">
              <el-input v-model="handleForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="填写现场核查情况" />
            </el-form-item>
            <el-button type="primary" :loading="handling" @click="submitHandle">提交处置</el-button>
          </el-form>
        </div>
        <el-alert v-else type="success" :closable="false" show-icon title="该告警已完成处置" class="done-alert" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Download, Refresh, Search } from '@element-plus/icons-vue'
import { alarmApi, cameraApi, exportAlarms } from '@/api'
import {
  BEHAVIOR_LABELS,
  STATUS_LABELS,
  behaviorLabel,
  formatTime,
  levelLabel,
  levelType,
  percent,
  statusLabel,
  statusType,
} from '@/utils/format'

const route = useRoute()
const loading = ref(false)
const records = ref<any[]>([])
const cameras = ref<any[]>([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
const dateRange = ref<[string, string] | null>(null)
const drawer = ref(false)
const current = ref<any>(null)
const handling = ref(false)
const handleForm = reactive({ status: 'REPAIRED', remark: '' })
const filters = reactive({ status: '', behaviorType: '', cameraId: null as number | null, keyword: '' })

function buildParams(exporting = false) {
  const params: any = {}
  if (!exporting) {
    params.page = page.value
    params.size = size.value
  }
  if (filters.status) params.status = filters.status
  if (filters.behaviorType) params.behaviorType = filters.behaviorType
  if (filters.cameraId) params.cameraId = filters.cameraId
  if (filters.keyword) params.keyword = filters.keyword
  if (dateRange.value?.length === 2) {
    params.start = `${dateRange.value[0]} 00:00:00`
    params.end = `${dateRange.value[1]} 23:59:59`
  }
  return params
}

async function load() {
  loading.value = true
  try {
    const data: any = await alarmApi.page(buildParams())
    records.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function reset() {
  filters.status = ''
  filters.behaviorType = ''
  filters.cameraId = null
  filters.keyword = ''
  dateRange.value = null
  search()
}

async function openDetail(row: any) {
  current.value = await alarmApi.detail(row.id)
  handleForm.status = 'REPAIRED'
  handleForm.remark = ''
  drawer.value = true
}

function quickHandle(row: any) {
  openDetail(row)
}

async function submitHandle() {
  if (!current.value) return
  handling.value = true
  try {
    await alarmApi.handle(current.value.id, { ...handleForm })
    ElMessage.success('处置成功')
    drawer.value = false
    await load()
  } finally {
    handling.value = false
  }
}

async function exportExcel() {
  try {
    await exportAlarms(buildParams(true))
    ElMessage.success('导出已开始')
  } catch {
    ElMessage.error('导出失败')
  }
}

onMounted(async () => {
  const data: any = await cameraApi.list()
  cameras.value = data || []
  await load()
  if (route.query.id) {
    await openDetail({ id: Number(route.query.id) })
  }
})
</script>

<style scoped>
.behavior {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.behavior i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #94a3b8;
  flex: none;
}

.behavior i.high {
  background: #ef4444;
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.1);
}

.behavior i.medium {
  background: #f59e0b;
  box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.1);
}

.behavior i.low {
  background: #10b981;
}

.thumb {
  width: 58px;
  height: 38px;
  border-radius: 6px;
  display: block;
  border: 1px solid #eef2f7;
}

.thumb.empty {
  background: #f1f5f9;
  color: #94a3b8;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.detail-img {
  width: 100%;
  max-height: 300px;
  background: #070d18;
  border: 1px solid rgba(56, 189, 248, 0.14);
  border-radius: 12px;
  margin-bottom: 14px;
}

.detail-img.placeholder {
  height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  font-size: 13px;
}

.desc {
  margin-bottom: 16px;
}

.handle-box {
  border-top: 1px dashed var(--border);
  padding-top: 14px;
}

.handle-box h4 {
  margin: 0 0 10px;
}

.done-alert {
  margin-top: 10px;
}
</style>
