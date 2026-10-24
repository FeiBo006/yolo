<template>
  <div class="page">
    <div class="page-title">
      <h2>操作日志<span class="sub">记录非查询类业务操作，便于审计追溯</span></h2>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div class="panel">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索用户 / 模块 / 操作" clearable style="width: 260px" @keyup.enter="search" />
        <el-button :icon="Search" @click="search">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="records" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="操作人" width="120" />
        <el-table-column prop="module" label="模块" width="140" />
        <el-table-column prop="operation" label="操作" width="150" />
        <el-table-column prop="method" label="请求" min-width="220" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column label="耗时" width="90">
          <template #default="{ row }">{{ row.costMs }} ms</template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="170" />
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import request from '@/api/request'

const loading = ref(false)
const records = ref<any[]>([])
const keyword = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)

async function load() {
  loading.value = true
  try {
    const data: any = await request.get('/logs', { params: { keyword: keyword.value || undefined, page: page.value, size: size.value } })
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

onMounted(load)
</script>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
