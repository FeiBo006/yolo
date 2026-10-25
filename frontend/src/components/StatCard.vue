<template>
  <div
    ref="cardRef"
    class="stat-card"
    :style="{ '--accent': color }"
    @mousemove="onMove"
    @mouseleave="onLeave"
  >
    <span class="spot" />
    <div class="stat-top">
      <span class="label">{{ label }}</span>
      <span v-if="icon" class="icon-chip" :style="{ background: color + '14', color }">
        <el-icon :size="18"><component :is="icon" /></el-icon>
      </span>
    </div>
    <div class="value">
      {{ display }}<span v-if="suffix" class="suffix">{{ suffix }}</span>
    </div>
    <div v-if="hint" class="hint">{{ hint }}</div>
    <span class="glow" :style="{ background: color }" />
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = withDefaults(
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

const cardRef = ref<HTMLElement>()
const display = ref<string | number>(typeof props.value === 'number' ? 0 : props.value)
let frame = 0

function formatNumber(value: number) {
  return Number.isInteger(value) ? Math.round(value) : Number(value.toFixed(1))
}

function animate(to: number) {
  cancelAnimationFrame(frame)
  const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  if (reduce) {
    display.value = formatNumber(to)
    return
  }
  const from = typeof display.value === 'number' ? display.value : 0
  const started = performance.now()
  const duration = 680
  const step = (now: number) => {
    const progress = Math.min(1, (now - started) / duration)
    const eased = 1 - Math.pow(1 - progress, 3)
    display.value = formatNumber(from + (to - from) * eased)
    if (progress < 1) {
      frame = requestAnimationFrame(step)
    }
  }
  frame = requestAnimationFrame(step)
}

watch(
  () => props.value,
  (value) => {
    if (typeof value === 'number') {
      animate(value)
    } else {
      display.value = value
    }
  },
)

onMounted(() => {
  if (typeof props.value === 'number') {
    animate(props.value)
  }
})

onBeforeUnmount(() => cancelAnimationFrame(frame))

function onMove(event: MouseEvent) {
  const el = cardRef.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const x = (event.clientX - rect.left) / rect.width
  const y = (event.clientY - rect.top) / rect.height
  el.style.setProperty('--mx', `${x * 100}%`)
  el.style.setProperty('--my', `${y * 100}%`)
  el.style.setProperty('--rx', `${(0.5 - y) * 6}deg`)
  el.style.setProperty('--ry', `${(x - 0.5) * 6}deg`)
}

function onLeave() {
  const el = cardRef.value
  if (!el) return
  el.style.setProperty('--mx', '50%')
  el.style.setProperty('--my', '50%')
  el.style.setProperty('--rx', '0deg')
  el.style.setProperty('--ry', '0deg')
}
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
  transform: perspective(900px) rotateX(var(--rx, 0deg)) rotateY(var(--ry, 0deg)) translateY(0);
  transition: transform 0.22s ease, box-shadow 0.22s ease, border-color 0.22s ease;
  will-change: transform;
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
  z-index: 1;
}

.stat-card:hover {
  transform: perspective(900px) rotateX(var(--rx, 0deg)) rotateY(var(--ry, 0deg)) translateY(-3px);
  border-color: color-mix(in srgb, var(--accent) 26%, transparent);
  box-shadow: 0 24px 42px -26px color-mix(in srgb, var(--accent) 45%, transparent);
}

.spot {
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: radial-gradient(
    240px circle at var(--mx, 50%) var(--my, 50%),
    color-mix(in srgb, var(--accent) 17%, transparent),
    transparent 66%
  );
  opacity: 0;
  transition: opacity 0.25s ease;
  pointer-events: none;
}

.stat-card:hover .spot {
  opacity: 1;
}

.stat-top,
.value,
.hint {
  position: relative;
  z-index: 1;
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
  transition: transform 0.22s ease;
}

.stat-card:hover .icon-chip {
  transform: scale(1.08) rotate(-4deg);
}

.value {
  display: flex;
  align-items: baseline;
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
  letter-spacing: 0;
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

@media (prefers-reduced-motion: reduce) {
  .stat-card,
  .stat-card:hover {
    transform: none;
    transition: none;
  }
  .spot,
  .icon-chip {
    transition: none;
  }
}
</style>
