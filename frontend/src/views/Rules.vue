<template>
  <div class="page">
    <div class="page-title">
      <h2>告警规则<span class="sub">置信度 + 连续帧 + 冷却时间 + ROI 四重过滤</span></h2>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增规则</el-button>
    </div>

    <div class="panel">
      <el-table v-loading="loading" :data="rules" stripe>
        <el-table-column prop="ruleName" label="规则名称" min-width="170" />
        <el-table-column label="行为类型" width="140">
          <template #default="{ row }">{{ behaviorLabel(row.behaviorType) }}</template>
        </el-table-column>
        <el-table-column label="生效范围" width="150">
          <template #default="{ row }">{{ row.cameraId ? row.cameraName : '全部摄像头' }}</template>
        </el-table-column>
        <el-table-column label="置信度" width="90">
          <template #default="{ row }">{{ percent(row.minConfidence) }}</template>
        </el-table-column>
        <el-table-column label="连续帧" width="80">
          <template #default="{ row }">{{ row.consecutiveFrames }} 帧</template>
        </el-table-column>
        <el-table-column label="冷却" width="90">
          <template #default="{ row }">{{ row.cooldownSeconds }} 秒</template>
        </el-table-column>
        <el-table-column label="级别" width="80">
          <template #default="{ row }">
            <el-tag :type="levelType(row.level)" size="small" effect="dark">{{ levelLabel(row.level) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生效时段" width="130">
          <template #default="{ row }">
            {{ row.startTime && row.endTime ? `${row.startTime} - ${row.endTime}` : '全天' }}
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled === 1" @change="(val: boolean) => toggle(row, val)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button text type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" :title="form.id ? '编辑规则' : '新增规则'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rulesForm" label-width="100px">
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="form.ruleName" placeholder="如：未佩戴安全帽监测" />
        </el-form-item>
        <el-form-item label="行为类型" prop="behaviorType">
          <el-select v-model="form.behaviorType" style="width: 100%">
            <el-option v-for="(label, value) in BEHAVIOR_LABELS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="生效摄像头">
          <el-select v-model="form.cameraId" placeholder="不选则全局生效" clearable style="width: 100%">
            <el-option v-for="camera in cameras" :key="camera.id" :label="camera.cameraName" :value="camera.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="置信度阈值">
          <el-slider v-model="form.minConfidence" :min="0.2" :max="0.95" :step="0.05" show-input />
        </el-form-item>
        <el-form-item label="连续帧确认">
          <el-input-number v-model="form.consecutiveFrames" :min="1" :max="10" />
          <span class="form-tip">连续 N 帧命中才告警，抑制偶发误检</span>
        </el-form-item>
        <el-form-item label="冷却时间">
          <el-input-number v-model="form.cooldownSeconds" :min="0" :max="3600" :step="10" />
          <span class="form-tip">秒，同一目标冷却期内不重复告警</span>
        </el-form-item>
        <el-form-item label="告警级别">
          <el-radio-group v-model="form.level">
            <el-radio-button value="LOW">低</el-radio-button>
            <el-radio-button value="MEDIUM">中</el-radio-button>
            <el-radio-button value="HIGH">高</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="生效时段">
          <el-time-picker v-model="form.startTime" placeholder="开始" format="HH:mm" value-format="HH:mm" style="width: 130px" />
          <span style="margin: 0 8px">至</span>
          <el-time-picker v-model="form.endTime" placeholder="结束" format="HH:mm" value-format="HH:mm" style="width: 130px" />
          <div class="form-tip">不填则全天生效</div>
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { cameraApi, ruleApi } from '@/api'
import { BEHAVIOR_LABELS, behaviorLabel, levelLabel, levelType, percent } from '@/utils/format'

const loading = ref(false)
const saving = ref(false)
const rules = ref<any[]>([])
const cameras = ref<any[]>([])
const dialog = ref(false)
const formRef = ref()
const form = reactive<any>({
  id: null,
  ruleName: '',
  behaviorType: 'NO_HELMET',
  cameraId: null,
  minConfidence: 0.45,
  consecutiveFrames: 3,
  cooldownSeconds: 60,
  level: 'HIGH',
  startTime: null,
  endTime: null,
  enabled: 1,
})
const rulesForm = {
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  behaviorType: [{ required: true, message: '请选择行为类型', trigger: 'change' }],
}

async function load() {
  loading.value = true
  try {
    rules.value = (await ruleApi.list()) || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    id: null,
    ruleName: '',
    behaviorType: 'NO_HELMET',
    cameraId: null,
    minConfidence: 0.45,
    consecutiveFrames: 3,
    cooldownSeconds: 60,
    level: 'HIGH',
    startTime: null,
    endTime: null,
    enabled: 1,
  })
  dialog.value = true
}

function openEdit(row: any) {
  Object.assign(form, row, { minConfidence: Number(row.minConfidence) })
  dialog.value = true
}

async function save() {
  await formRef.value?.validate(async (valid: boolean) => {
    if (!valid) return
    saving.value = true
    try {
      if (form.id) await ruleApi.update(form.id, { ...form })
      else await ruleApi.create({ ...form })
      ElMessage.success('保存成功')
      dialog.value = false
      await load()
    } finally {
      saving.value = false
    }
  })
}

async function toggle(row: any, value: boolean) {
  await ruleApi.update(row.id, { ...row, enabled: value ? 1 : 0 })
  await load()
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确认删除规则「${row.ruleName}」？`, '删除确认', { type: 'warning' })
  await ruleApi.remove(row.id)
  ElMessage.success('删除成功')
  await load()
}

onMounted(async () => {
  const data: any = await cameraApi.list()
  cameras.value = data || []
  await load()
})
</script>

<style scoped>
.form-tip {
  font-size: 11px;
  color: var(--muted);
  margin-left: 8px;
}
</style>
