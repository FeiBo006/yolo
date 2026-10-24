<template>
  <div class="page">
    <div class="page-title">
      <h2>工地管理<span class="sub">维护项目基础信息，摄像头与告警按工地归属统计</span></h2>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增工地</el-button>
    </div>

    <div class="panel">
      <el-table v-loading="loading" :data="sites" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="siteName" label="工地名称" min-width="180" />
        <el-table-column prop="address" label="地址（脱敏）" min-width="220" show-overflow-tooltip />
        <el-table-column prop="manager" label="负责人" width="100" />
        <el-table-column prop="phone" label="联系电话" width="140" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '在建' : '停工' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button text type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" :title="form.id ? '编辑工地' : '新增工地'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="工地名称" prop="siteName">
          <el-input v-model="form.siteName" placeholder="请输入工地名称" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" placeholder="请输入详细地址" />
          <div class="form-tip">列表默认脱敏展示为「区/县 + ****」，完整地址仅授权角色可见</div>
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="form.manager" placeholder="请输入负责人" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.phone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">在建</el-radio>
            <el-radio :value="0">停工</el-radio>
          </el-radio-group>
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
import { siteApi } from '@/api'

const loading = ref(false)
const saving = ref(false)
const sites = ref<any[]>([])
const dialog = ref(false)
const formRef = ref()
const form = reactive<any>({ id: null, siteName: '', address: '', manager: '', phone: '', status: 1 })
const rules = {
  siteName: [{ required: true, message: '请输入工地名称', trigger: 'blur' }],
}

async function load() {
  loading.value = true
  try {
    sites.value = (await siteApi.list()) || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: null, siteName: '', address: '', manager: '', phone: '', status: 1 })
  dialog.value = true
}

async function openEdit(row: any) {
  // 列表中的地址已脱敏，编辑时从详情接口取完整地址
  const detail: any = await siteApi.detail(row.id)
  Object.assign(form, detail)
  dialog.value = true
}

async function save() {
  await formRef.value?.validate(async (valid: boolean) => {
    if (!valid) return
    saving.value = true
    try {
      if (form.id) await siteApi.update(form.id, { ...form })
      else await siteApi.create({ ...form })
      ElMessage.success('保存成功')
      dialog.value = false
      await load()
    } finally {
      saving.value = false
    }
  })
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确认删除工地「${row.siteName}」？`, '删除确认', { type: 'warning' })
  await siteApi.remove(row.id)
  ElMessage.success('删除成功')
  await load()
}

onMounted(load)
</script>
