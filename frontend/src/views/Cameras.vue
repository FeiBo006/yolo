<template>
  <div class="page">
    <div class="page-title">
      <h2>摄像头管理<span class="sub">支持 RTSP、本地视频、USB 与 DEMO 合成源</span></h2>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增摄像头</el-button>
    </div>

    <div class="panel">
      <div class="toolbar">
        <el-select v-model="siteFilter" placeholder="按工地筛选" clearable style="width: 200px" @change="load">
          <el-option v-for="site in sites" :key="site.id" :label="site.siteName" :value="site.id" />
        </el-select>
        <el-input v-model="keyword" placeholder="搜索摄像头名称" clearable style="width: 220px" @keyup.enter="load" />
        <el-button :icon="Search" @click="load">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="cameras" stripe>
        <el-table-column prop="id" label="ID" width="54" />
        <el-table-column prop="cameraName" label="摄像头名称" min-width="140" />
        <el-table-column prop="siteName" label="所属工地" min-width="125" show-overflow-tooltip />
        <el-table-column label="类型" width="78">
          <template #default="{ row }">
            <el-tag size="small" :type="row.streamType === 'DEMO' ? 'warning' : 'info'" effect="plain">
              {{ row.streamType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="streamUrl" label="视频源地址" min-width="165" show-overflow-tooltip>
          <template #default="{ row }">{{ row.streamUrl || '-' }}</template>
        </el-table-column>
        <el-table-column prop="location" label="安装位置" width="108" show-overflow-tooltip />
        <el-table-column label="状态" width="76">
          <template #default="{ row }">
            <span class="status-dot" :class="{ online: row.online }" />
            {{ row.online ? '在线' : '离线' }}
          </template>
        </el-table-column>
        <el-table-column label="启用" width="66">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled === 1" @change="(val: boolean) => toggle(row, val)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="testCamera(row)">测试</el-button>
            <el-button text type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button text type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" :title="form.id ? '编辑摄像头' : '新增摄像头'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="所属工地" prop="siteId">
          <el-select v-model="form.siteId" placeholder="请选择工地" style="width: 100%">
            <el-option v-for="site in sites" :key="site.id" :label="site.siteName" :value="site.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称" prop="cameraName">
          <el-input v-model="form.cameraName" placeholder="如：1 号塔吊全景" />
        </el-form-item>
        <el-form-item label="视频源类型" prop="streamType">
          <el-select v-model="form.streamType" style="width: 100%">
            <el-option v-for="type in STREAM_TYPES" :key="type.value" :label="type.label" :value="type.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.streamType !== 'DEMO'" label="视频源地址" prop="streamUrl">
          <el-input v-model="form.streamUrl" :placeholder="urlPlaceholder" />
          <div class="form-tip">{{ urlTip }}</div>
        </el-form-item>
        <el-form-item label="安装位置">
          <el-input v-model="form.location" placeholder="如：工地南门" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { cameraApi, siteApi } from '@/api'
import { STREAM_TYPES } from '@/utils/format'

const loading = ref(false)
const saving = ref(false)
const cameras = ref<any[]>([])
const sites = ref<any[]>([])
const siteFilter = ref<number | null>(null)
const keyword = ref('')
const dialog = ref(false)
const formRef = ref()
const form = reactive<any>({
  id: null,
  siteId: null,
  cameraName: '',
  streamType: 'DEMO',
  streamUrl: '',
  location: '',
  enabled: 1,
})

const rules = {
  siteId: [{ required: true, message: '请选择工地', trigger: 'change' }],
  cameraName: [{ required: true, message: '请输入摄像头名称', trigger: 'blur' }],
  streamType: [{ required: true, message: '请选择视频源类型', trigger: 'change' }],
  streamUrl: [
    {
      validator: (_rule: any, value: string, callback: any) => {
        if (form.streamType !== 'DEMO' && !value) return callback(new Error('请填写视频源地址'))
        callback()
      },
      trigger: 'blur',
    },
  ],
}

const urlPlaceholder = computed(() => {
  if (form.streamType === 'RTSP') return 'rtsp://admin:password@192.168.1.10:554/stream1'
  if (form.streamType === 'VIDEO') return '/path/to/demo.mp4 或相对路径 videos/demo.mp4'
  return '0（本机默认摄像头）'
})

const urlTip = computed(() => {
  if (form.streamType === 'RTSP') return '算法服务通过 OpenCV/FFmpeg 拉流，需保证网络可达'
  if (form.streamType === 'VIDEO') return '相对路径以 ai-service 目录为基准，视频会自动循环播放'
  return '填设备编号，默认为 0'
})

async function load() {
  loading.value = true
  try {
    const data: any = await cameraApi.list({ siteId: siteFilter.value || undefined, keyword: keyword.value || undefined })
    cameras.value = data || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    id: null,
    siteId: sites.value[0]?.id || null,
    cameraName: '',
    streamType: 'DEMO',
    streamUrl: '',
    location: '',
    enabled: 1,
  })
  dialog.value = true
}

function openEdit(row: any) {
  Object.assign(form, row)
  dialog.value = true
}

async function save() {
  await formRef.value?.validate(async (valid: boolean) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = { ...form }
      if (payload.streamType === 'DEMO') payload.streamUrl = payload.streamUrl || `demo://${payload.cameraName}`
      if (form.id) {
        await cameraApi.update(form.id, payload)
      } else {
        await cameraApi.create(payload)
      }
      ElMessage.success('保存成功')
      dialog.value = false
      await load()
    } finally {
      saving.value = false
    }
  })
}

async function toggle(row: any, value: boolean) {
  await cameraApi.update(row.id, { ...row, enabled: value ? 1 : 0 })
  ElMessage.success(value ? '已启用' : '已停用')
  await load()
}

async function testCamera(row: any) {
  const message = await ElMessageBox.confirm('将请求算法服务探测该视频源，是否继续？', '连通性测试', {
    confirmButtonText: '开始测试',
    cancelButtonText: '取消',
    type: 'info',
  }).catch(() => null)
  if (!message) return
  const result: any = await cameraApi.test(row.id)
  ElMessageBox.alert(
    result?.message || (result?.ok ? '视频源可用' : '视频源不可用'),
    result?.ok ? '测试通过' : '测试未通过',
    { type: result?.ok ? 'success' : 'warning' },
  )
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确认删除摄像头「${row.cameraName}」？`, '删除确认', { type: 'warning' })
  await cameraApi.remove(row.id)
  ElMessage.success('删除成功')
  await load()
}

onMounted(async () => {
  const data: any = await siteApi.list()
  sites.value = data || []
  await load()
})
</script>

<style scoped>
.status-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #cbd5e1;
  margin-right: 6px;
}

.status-dot.online {
  background: #10b981;
}

.form-tip {
  font-size: 11px;
  color: var(--muted);
  line-height: 1.6;
  margin-top: 4px;
}
</style>
