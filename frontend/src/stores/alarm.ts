import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAlarmStore = defineStore('alarm', () => {
  const latest = ref<any[]>([])
  const unread = ref(0)

  function push(alarm: any) {
    latest.value = [alarm, ...latest.value].slice(0, 60)
    unread.value += 1
  }

  function update(alarm: any) {
    const index = latest.value.findIndex((item) => item.id === alarm.id)
    if (index >= 0) {
      latest.value.splice(index, 1, alarm)
    }
  }

  function setList(list: any[]) {
    latest.value = list
  }

  function clearUnread() {
    unread.value = 0
  }

  return { latest, unread, push, update, setList, clearUnread }
})
