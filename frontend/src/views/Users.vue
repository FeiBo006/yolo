<template>
  <div class="page">
    <div class="page-title">
      <h2>用户管理<span class="sub">账号、角色与状态管理</span></h2>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增用户</el-button>
    </div>

    <div class="panel">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索用户名 / 姓名" clearable style="width: 240px" @keyup.enter="search" />
        <el-button :icon="Search" @click="search">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="records" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="username" label="账号" width="130" />
        <el-table-column prop="realName" label="姓名" width="120" />
        <el-table-column prop="phone" label="手机号" width="150" />
        <el-table-column label="角色" width="130">
          <template #default="{ row }">
            <el-tag :type="roleType(row.roleCode)" size="small" effect="plain">{{ roleLabel(row.roleCode) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 1" @change="(val: boolean) => toggle(row, val)" />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button text type="danger" size="small" :disabled="row.username === 'admin'" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          background
          @current-change="load"
        />
      </div>
    </div>

    <el-dialog v-model="dialog" :title="form.id ? '编辑用户' : '新增用户'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" :disabled="!!form.id" placeholder="登录账号" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.realName" placeholder="真实姓名" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="联系电话" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleCode" style="width: 100%">
            <el-option label="系统管理员" value="ADMIN" />
            <el-option label="项目经理" value="MANAGER" />
            <el-option label="安全员" value="INSPECTOR" />
          </el-select>
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password :placeholder="form.id ? '留空则不修改' : '默认 123456'" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
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
import { Plus, Search } from '@element-plus/icons-vue'
import { userApi } from '@/api'

const loading = ref(false)
const saving = ref(false)
const records = ref<any[]>([])
const keyword = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const dialog = ref(false)
const formRef = ref()
const form = reactive<any>({
  id: null,
  username: '',
  realName: '',
  phone: '',
  roleCode: 'INSPECTOR',
  password: '',
  status: 1,
})
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
}

function roleLabel(code: string) {
  return { ADMIN: '系统管理员', MANAGER: '项目经理', INSPECTOR: '安全员' }[code] || code
}

function roleType(code: string) {
  return { ADMIN: 'danger', MANAGER: 'warning', INSPECTOR: 'success' }[code] || 'info'
}

async function load() {
  loading.value = true
  try {
    const data: any = await userApi.page({ keyword: keyword.value || undefined, page: page.value, size: size.value })
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

function openCreate() {
  Object.assign(form, { id: null, username: '', realName: '', phone: '', roleCode: 'INSPECTOR', password: '', status: 1 })
  dialog.value = true
}

function openEdit(row: any) {
  Object.assign(form, { ...row, password: '' })
  dialog.value = true
}

async function save() {
  await formRef.value?.validate(async (valid: boolean) => {
    if (!valid) return
    saving.value = true
    try {
      if (form.id) await userApi.update(form.id, { ...form })
      else await userApi.create({ ...form })
      ElMessage.success('保存成功')
      dialog.value = false
      await load()
    } finally {
      saving.value = false
    }
  })
}

async function toggle(row: any, value: boolean) {
  await userApi.update(row.id, { ...row, status: value ? 1 : 0, password: '' })
  await load()
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确认删除用户「${row.username}」？`, '删除确认', { type: 'warning' })
  await userApi.remove(row.id)
  ElMessage.success('删除成功')
  await load()
}

onMounted(load)
</script>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
