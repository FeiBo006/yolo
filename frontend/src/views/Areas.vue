<template>
  <div class="page">
    <div class="page-title">
      <h2>区域配置<span class="sub">在监控画面上框选危险区域，用于入侵检测与 ROI 过滤</span></h2>
    </div>

    <div class="area-layout">
      <div class="panel">
        <div class="toolbar area-toolbar">
          <div class="filter-row">
            <el-select v-model="cameraId" placeholder="选择摄像头" style="width: 190px" @change="onCameraChange">
              <el-option v-for="camera in cameras" :key="camera.id" :label="camera.cameraName" :value="camera.id" />
            </el-select>
            <el-input v-model="form.areaName" placeholder="区域名称，如：塔吊吊装危险区" style="width: 230px" />
            <el-select v-model="form.areaType" style="width: 120px">
              <el-option label="危险区域" value="DANGER" />
              <el-option label="安全区域" value="SAFE" />
            </el-select>
          </div>
          <div class="action-row">
            <el-button :icon="Refresh" @click="refreshSnapshot">刷新画面</el-button>
            <el-button :disabled="!points.length" @click="undo">撤销</el-button>
            <el-button :disabled="!points.length" @click="clearPoints">清空</el-button>
            <el-button type="primary" :loading="saving" @click="save">保存区域</el-button>
          </div>
        </div>

        <div ref="wrapRef" class="canvas-wrap">
          <img v-if="snapshotUrl && !snapshotError" :src="snapshotUrl" class="snapshot" @load="resizeCanvas" @error="snapshotError = true" />
          <div v-else class="no-snapshot">
            <el-icon :size="42"><VideoCamera /></el-icon>
            <p>暂无画面，请确认算法服务已启动且该摄像头已启用</p>
            <el-button size="small" @click="refreshSnapshot">重试</el-button>
          </div>
          <canvas ref="canvasRef" class="canvas" @click="onCanvasClick" />
          <div class="canvas-tip">在画面上依次点击顶点，至少 3 个点；点击「保存区域」生效</div>
        </div>

        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="坐标以百分比保存，切换分辨率不会失效；区域将下发给算法服务参与规则判定。"
          class="tip"
        />
      </div>

      <aside class="panel side">
        <div class="panel-header">
          <h3>已配置区域</h3>
          <el-tag size="small" effect="plain">{{ areas.length }} 个</el-tag>
        </div>
        <div v-for="area in areas" :key="area.id" class="area-item">
          <div class="area-top">
            <span class="area-name">{{ area.areaName }}</span>
            <el-tag size="small" :type="area.areaType === 'DANGER' ? 'danger' : 'success'" effect="dark">
              {{ area.areaType === 'DANGER' ? '危险' : '安全' }}
            </el-tag>
          </div>
          <div class="area-sub">{{ area.cameraName }} · {{ pointCount(area.points) }} 个顶点</div>
          <div class="area-actions">
            <el-switch
              :model-value="area.enabled === 1"
              size="small"
              @change="(val: boolean) => toggleArea(area, val)"
            />
            <el-button text type="danger" size="small" @click="removeArea(area)">删除</el-button>
          </div>
        </div>
        <el-empty v-if="!areas.length" description="尚未配置区域" :image-size="60" />
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, VideoCamera } from '@element-plus/icons-vue'
import { areaApi, cameraApi } from '@/api'

const cameras = ref<any[]>([])
const cameraId = ref<number | null>(null)
const areas = ref<any[]>([])
const points = ref<Array<[number, number]>>([])
const canvasRef = ref<HTMLCanvasElement>()
const wrapRef = ref<HTMLDivElement>()
const snapshotTs = ref(Date.now())
const snapshotError = ref(false)
const saving = ref(false)
const form = reactive({ areaName: '', areaType: 'DANGER' })

const snapshotUrl = computed(() =>
  cameraId.value ? `/ai/streams/${cameraId.value}/snapshot?t=${snapshotTs.value}` : '',
)

function pointCount(value: string) {
  try {
    return JSON.parse(value || '[]').length
  } catch {
    return 0
  }
}

async function loadAreas() {
  if (!cameraId.value) return
  areas.value = (await areaApi.list({ cameraId: cameraId.value })) || []
  await nextTick()
  resizeCanvas()
}

async function onCameraChange() {
  points.value = []
  snapshotError.value = false
  form.areaName = ''
  await loadAreas()
  refreshSnapshot()
}

function refreshSnapshot() {
  snapshotError.value = false
  snapshotTs.value = Date.now()
}

function resizeCanvas() {
  const canvas = canvasRef.value
  const wrap = wrapRef.value
  if (!canvas || !wrap) return
  const rect = wrap.getBoundingClientRect()
  if (rect.width === 0 || rect.height === 0) return
  canvas.width = Math.round(rect.width)
  canvas.height = Math.round(rect.height)
  draw()
}

function draw() {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  const w = canvas.width
  const h = canvas.height
  ctx.clearRect(0, 0, w, h)

  for (const area of areas.value) {
    let pts: number[][] = []
    try {
      pts = JSON.parse(area.points || '[]')
    } catch {
      pts = []
    }
    if (pts.length < 2) continue
    ctx.beginPath()
    pts.forEach(([x, y], index) => {
      const px = x * w
      const py = y * h
      if (index === 0) ctx.moveTo(px, py)
      else ctx.lineTo(px, py)
    })
    ctx.closePath()
    ctx.fillStyle = area.areaType === 'DANGER' ? 'rgba(239,68,68,0.12)' : 'rgba(16,185,129,0.12)'
    ctx.fill()
    ctx.strokeStyle = area.areaType === 'DANGER' ? '#f59e0b' : '#10b981'
    ctx.lineWidth = 2
    ctx.setLineDash([6, 4])
    ctx.stroke()
    ctx.setLineDash([])
    const [x0, y0] = pts[0]
    ctx.fillStyle = '#f8fafc'
    ctx.font = '12px sans-serif'
    ctx.fillText(area.areaName, x0 * w + 6, y0 * h + 18)
  }

  if (!points.value.length) return
  ctx.beginPath()
  points.value.forEach(([x, y], index) => {
    const px = x * w
    const py = y * h
    if (index === 0) ctx.moveTo(px, py)
    else ctx.lineTo(px, py)
  })
  if (points.value.length >= 3) ctx.closePath()
  ctx.fillStyle = 'rgba(37,99,235,0.18)'
  ctx.fill()
  ctx.strokeStyle = '#3b82f6'
  ctx.lineWidth = 2
  ctx.stroke()
  points.value.forEach(([x, y], index) => {
    const px = x * w
    const py = y * h
    ctx.beginPath()
    ctx.arc(px, py, 4, 0, Math.PI * 2)
    ctx.fillStyle = '#3b82f6'
    ctx.fill()
    ctx.strokeStyle = '#fff'
    ctx.lineWidth = 1.5
    ctx.stroke()
    ctx.fillStyle = '#fff'
    ctx.font = '11px sans-serif'
    ctx.fillText(String(index + 1), px + 7, py - 7)
  })
}

function onCanvasClick(event: MouseEvent) {
  const canvas = canvasRef.value
  if (!canvas || !snapshotUrl.value || snapshotError.value) return
  const rect = canvas.getBoundingClientRect()
  const x = Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width))
  const y = Math.min(1, Math.max(0, (event.clientY - rect.top) / rect.height))
  points.value.push([Number(x.toFixed(4)), Number(y.toFixed(4))])
  draw()
}

function undo() {
  points.value.pop()
  draw()
}

function clearPoints() {
  points.value = []
  draw()
}

async function save() {
  if (!cameraId.value) return ElMessage.warning('请先选择摄像头')
  if (!form.areaName.trim()) return ElMessage.warning('请填写区域名称')
  if (points.value.length < 3) return ElMessage.warning('至少需要 3 个顶点')
  saving.value = true
  try {
    await areaApi.create({
      cameraId: cameraId.value,
      areaName: form.areaName.trim(),
      areaType: form.areaType,
      points: JSON.stringify(points.value),
      enabled: 1,
    })
    ElMessage.success('区域已保存并下发算法服务')
    points.value = []
    form.areaName = ''
    await loadAreas()
  } finally {
    saving.value = false
  }
}

async function toggleArea(area: any, value: boolean) {
  await areaApi.update(area.id, { ...area, enabled: value ? 1 : 0 })
  await loadAreas()
}

async function removeArea(area: any) {
  await ElMessageBox.confirm(`确认删除区域「${area.areaName}」？`, '删除确认', { type: 'warning' })
  await areaApi.remove(area.id)
  ElMessage.success('已删除')
  await loadAreas()
}

function onResize() {
  resizeCanvas()
}

onMounted(async () => {
  const data: any = await cameraApi.list()
  cameras.value = (data || []).filter((c: any) => c.enabled === 1)
  if (cameras.value.length) {
    cameraId.value = cameras.value[0].id
    await loadAreas()
  }
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => window.removeEventListener('resize', onResize))
</script>

<style scoped>
.area-toolbar {
  flex-direction: column;
  align-items: stretch;
  gap: 10px;
}

.filter-row,
.action-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.area-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 16px;
  align-items: start;
}

.canvas-wrap {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  background: #0b1220;
  border-radius: 10px;
  overflow: hidden;
  cursor: crosshair;
}

.snapshot {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: fill;
}

.canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.no-snapshot {
  position: absolute;
  inset: 0;
  color: #64748b;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 13px;
}

.canvas-tip {
  position: absolute;
  left: 12px;
  bottom: 10px;
  color: rgba(255, 255, 255, 0.72);
  font-size: 11px;
  background: rgba(15, 23, 42, 0.55);
  padding: 4px 9px;
  border-radius: 6px;
}

.tip {
  margin-top: 12px;
}

.side {
  max-height: calc(100vh - 170px);
  overflow-y: auto;
}

.area-item {
  border: 1px solid var(--border);
  border-radius: 9px;
  padding: 11px 12px;
  margin-bottom: 9px;
}

.area-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.area-name {
  font-size: 13px;
  font-weight: 600;
}

.area-sub {
  font-size: 11px;
  color: var(--muted);
  margin: 5px 0 6px;
}

.area-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

@media (max-width: 1100px) {
  .area-layout {
    grid-template-columns: 1fr;
  }
}
</style>
