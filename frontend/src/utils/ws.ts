let socket: WebSocket | null = null
let reconnectTimer: number | undefined
let heartbeatTimer: number | undefined
let manualClose = false
let retries = 0

export interface SocketHandlers {
  onAlarm?: (type: string, data: any) => void
  onOpen?: () => void
  onClose?: () => void
}

export function connectAlarmSocket(handlers: SocketHandlers) {
  const token = localStorage.getItem('yuanyu_token')
  if (!token) return
  manualClose = false
  const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
  socket = new WebSocket(`${protocol}://${location.host}/ws/alarms?token=${encodeURIComponent(token)}`)

  socket.onopen = () => {
    retries = 0
    handlers.onOpen?.()
    heartbeatTimer = window.setInterval(() => socket?.send('ping'), 25000)
  }

  socket.onmessage = (event) => {
    try {
      const message = JSON.parse(event.data)
      if (message.type === 'ALARM' || message.type === 'ALARM_UPDATE') {
        handlers.onAlarm?.(message.type, message.data)
      }
    } catch {
      // 忽略非 JSON 消息
    }
  }

  socket.onclose = () => {
    if (heartbeatTimer) window.clearInterval(heartbeatTimer)
    handlers.onClose?.()
    if (!manualClose) {
      retries += 1
      reconnectTimer = window.setTimeout(() => connectAlarmSocket(handlers), Math.min(30000, 2000 * retries))
    }
  }

  socket.onerror = () => socket?.close()
}

export function disconnectAlarmSocket() {
  manualClose = true
  if (reconnectTimer) window.clearTimeout(reconnectTimer)
  if (heartbeatTimer) window.clearInterval(heartbeatTimer)
  socket?.close()
  socket = null
}

let audioContext: AudioContext | null = null

export function playAlarmSound() {
  try {
    audioContext = audioContext || new AudioContext()
    if (audioContext.state === 'suspended') audioContext.resume()
    const oscillator = audioContext.createOscillator()
    const gain = audioContext.createGain()
    oscillator.type = 'sine'
    oscillator.frequency.setValueAtTime(880, audioContext.currentTime)
    oscillator.frequency.setValueAtTime(660, audioContext.currentTime + 0.18)
    gain.gain.setValueAtTime(0.15, audioContext.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.001, audioContext.currentTime + 0.5)
    oscillator.connect(gain)
    gain.connect(audioContext.destination)
    oscillator.start()
    oscillator.stop(audioContext.currentTime + 0.5)
  } catch {
    // 浏览器未授权音频时静默失败
  }
}
