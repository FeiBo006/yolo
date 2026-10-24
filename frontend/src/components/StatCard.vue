<template>
  <div class="stat-card" :style="{ '--accent': color }">
    <div class="stat-top">
      <span class="label">{{ label }}</span>
      <span v-if="icon" class="icon-chip" :style="{ background: color + '14', color }">
        <el-icon :size="18"><component :is="icon" /></el-icon>
      </span>
    </div>
    <div class="value">
      {{ value }}<span v-if="suffix" class="suffix">{{ suffix }}</span>
    </div>
    <div v-if="hint" class="hint">{{ hint }}</div>
    <span class="glow" :style="{ background: color }" />
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    label: string
    value: string | number
    suffix?: string
    color?: string
    hint?: string
    icon?: any
  }>(),
  { color: '#2563eb', suffix: '', hint: '' },
)
</script>

<style scoped>
.stat-card {
  position: relative;
  overflow: hidden;
  min-height: 126px;
  padding: 19px 20px 17px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: linear-gradient(180deg, #ffffff 0%, #fbfdff 100%);
  box-shadow: var(--shadow-sm), 0 16px 34px -30px rgba(15, 23, 42, 0.45);
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.stat-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 18px;
  bottom: 18px;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: var(--accent);
  opacity: 0.9;
}

.stat-card:hover {
  transform: translateY(-2px);
  border-color: color-mix(in srgb, var(--accent) 26%, transparent);
  box-shadow: 0 20px 38px -26px color-mix(in srgb, var(--accent) 45%, transparent);
}

.stat-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.label {
  color: var(--muted);
  font-size: 12.5px;
  letter-spacing: 0.4px;
}

.icon-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 11px;
  flex: none;
}

.value {
  margin-top: 7px;
  font-family: var(--font-display);
  font-size: 33px;
  font-weight: 700;
  line-height: 1.15;
  letter-spacing: 0.5px;
  color: var(--ink);
}

.suffix {
  margin-left: 5px;
  font-family: 'PingFang SC', sans-serif;
  font-size: 12px;
  font-weight: 400;
  color: var(--muted);
}

.hint {
  margin-top: 7px;
  font-size: 11.5px;
  color: #94a3b8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.glow {
  position: absolute;
  right: -42px;
  bottom: -56px;
  width: 126px;
  height: 126px;
  border-radius: 50%;
  opacity: 0.08;
  filter: blur(18px);
  pointer-events: none;
}
</style>
