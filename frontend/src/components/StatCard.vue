<template>
  <div class="stat-card">
    <div class="label">{{ label }}</div>
    <div class="value" :style="{ color }">
      {{ value }}<span v-if="suffix" class="suffix">{{ suffix }}</span>
    </div>
    <div v-if="hint" class="hint">{{ hint }}</div>
    <div class="accent" :style="{ background: color }" />
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
  }>(),
  { color: '#2563eb', suffix: '', hint: '' },
)
</script>

<style scoped>
.stat-card {
  min-height: 118px;
}

.hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--muted);
}
</style>
