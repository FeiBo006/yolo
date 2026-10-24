<template>
  <div class="login-page">
    <div class="brand">
      <div class="brand-inner">
        <div class="badge">毕业设计 · 计算机视觉 + 全栈</div>
        <h1>工地安全行为<br /><span>实时检测预警系统</span></h1>
        <p class="desc">
          基于 YOLOv8 的实时视频分析平台，自动识别未佩戴安全帽、未穿反光衣、
          危险区域入侵等不安全行为，2 秒内完成告警推送与处置闭环。
        </p>
        <div class="features">
          <div class="feature"><el-icon><VideoCamera /></el-icon><span>多路视频实时推理</span></div>
          <div class="feature"><el-icon><Bell /></el-icon><span>WebSocket 秒级告警</span></div>
          <div class="feature"><el-icon><DataAnalysis /></el-icon><span>多维统计与可视化</span></div>
        </div>
        <div class="stack">YOLOv8 · Spring Boot 3 · Vue 3 · Redis · RabbitMQ</div>
      </div>
    </div>

    <div class="form-side">
      <div class="form-card">
        <div class="form-logo">
          <el-icon :size="24"><Monitor /></el-icon>
        </div>
        <h2>欢迎登录</h2>
        <p class="sub">YUANYU Safety Monitoring Platform</p>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="submit">
          <el-form-item label="账号" prop="username">
            <el-input v-model="form.username" size="large" placeholder="请输入账号" :prefix-icon="User" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              size="large"
              type="password"
              show-password
              placeholder="请输入密码"
              :prefix-icon="Lock"
            />
          </el-form-item>
          <el-button type="primary" size="large" class="submit" :loading="loading" @click="submit">
            登 录
          </el-button>
        </el-form>

        <div class="demo">
          <span>演示账号：</span>
          <el-tag v-for="item in demoAccounts" :key="item.username" class="demo-tag" @click="fill(item)">
            {{ item.label }} / 123456
          </el-tag>
        </div>
        <p class="copyright">© 2026 YUANYU · 基于 YOLO 的工地安全行为实时检测预警系统</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell, DataAnalysis, Lock, Monitor, User, VideoCamera } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({ username: 'admin', password: '123456' })
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const demoAccounts = [
  { username: 'admin', password: '123456', label: '管理员' },
  { username: 'manager', password: '123456', label: '项目经理' },
  { username: 'inspector', password: '123456', label: '安全员' },
]

function fill(item: { username: string; password: string }) {
  form.username = item.username
  form.password = item.password
}

async function submit() {
  await formRef.value?.validate(async (valid: boolean) => {
    if (!valid) return
    loading.value = true
    try {
      await auth.login({ ...form })
      ElMessage.success('登录成功')
      const redirect = (route.query.redirect as string) || '/'
      router.push(redirect)
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.15fr 1fr;
  background: #0b1220;
}

.brand {
  position: relative;
  background:
    radial-gradient(circle at 18% 18%, rgba(37, 99, 235, 0.4), transparent 45%),
    radial-gradient(circle at 80% 80%, rgba(6, 182, 212, 0.28), transparent 42%),
    linear-gradient(145deg, #0b1220 0%, #111c33 55%, #0f172a 100%);
  color: #fff;
  display: flex;
  align-items: center;
  overflow: hidden;
}

.brand::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image: linear-gradient(rgba(148, 163, 184, 0.07) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.07) 1px, transparent 1px);
  background-size: 42px 42px;
  mask-image: linear-gradient(to bottom, transparent, #000 30%, #000 70%, transparent);
}

.brand-inner {
  position: relative;
  z-index: 1;
  padding: 0 9vw;
  max-width: 720px;
}

.badge {
  display: inline-block;
  padding: 6px 12px;
  border: 1px solid rgba(96, 165, 250, 0.35);
  border-radius: 999px;
  font-size: 12px;
  color: #93c5fd;
  letter-spacing: 0.5px;
  margin-bottom: 26px;
}

h1 {
  font-size: 44px;
  line-height: 1.25;
  margin: 0 0 18px;
  font-weight: 700;
  letter-spacing: 1px;
}

h1 span {
  background: linear-gradient(90deg, #60a5fa, #22d3ee);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.desc {
  color: #94a3b8;
  font-size: 15px;
  line-height: 1.9;
  margin-bottom: 30px;
}

.features {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
  margin-bottom: 36px;
}

.feature {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #cbd5e1;
  font-size: 13px;
}

.feature .el-icon {
  color: #38bdf8;
  font-size: 18px;
}

.stack {
  font-size: 12px;
  color: #475569;
  letter-spacing: 1px;
}

.form-side {
  background:
    radial-gradient(480px 320px at 100% 0%, rgba(37, 99, 235, 0.09), transparent 60%),
    radial-gradient(420px 320px at 0% 100%, rgba(6, 182, 212, 0.08), transparent 55%),
    #f8fafc;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 30px;
}

.form-card {
  width: 100%;
  max-width: 404px;
  padding: 36px 34px 28px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 34px 70px -46px rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(10px);
}

.form-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  margin-bottom: 18px;
  border-radius: 14px;
  color: #fff;
  background: linear-gradient(135deg, #2563eb, #06b6d4);
  box-shadow: 0 12px 26px -12px rgba(37, 99, 235, 0.8);
}

h2 {
  font-size: 28px;
  margin: 0 0 6px;
}

.sub {
  color: #94a3b8;
  font-size: 12px;
  letter-spacing: 0.5px;
  margin-bottom: 30px;
}

.submit {
  width: 100%;
  margin-top: 8px;
  letter-spacing: 4px;
}

.demo {
  margin-top: 24px;
  font-size: 12px;
  color: #94a3b8;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.demo-tag {
  cursor: pointer;
}

.copyright {
  margin-top: 34px;
  font-size: 11px;
  color: #cbd5e1;
}

@media (max-width: 900px) {
  .login-page {
    grid-template-columns: 1fr;
  }
  .brand {
    display: none;
  }
  .form-side {
    min-height: 100vh;
  }
}
</style>
