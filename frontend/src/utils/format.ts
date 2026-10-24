export const BEHAVIOR_LABELS: Record<string, string> = {
  NO_HELMET: '未佩戴安全帽',
  NO_VEST: '未穿反光衣',
  INTRUSION: '危险区域入侵',
  SMOKING: '吸烟',
  PHONE: '打电话',
  FALL: '人员跌倒',
}

export const STATUS_LABELS: Record<string, string> = {
  PENDING: '待处理',
  CONFIRMED: '已确认',
  REPAIRED: '已整改',
  FALSE_ALARM: '误报',
}

export const LEVEL_LABELS: Record<string, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
}

export const STREAM_TYPES = [
  { value: 'DEMO', label: 'DEMO 合成流' },
  { value: 'RTSP', label: 'RTSP 摄像头' },
  { value: 'VIDEO', label: '本地视频文件' },
  { value: 'USB', label: 'USB 摄像头' },
]

export function behaviorLabel(value?: string) {
  return value ? BEHAVIOR_LABELS[value] || value : '-'
}

export function statusLabel(value?: string) {
  return value ? STATUS_LABELS[value] || value : '-'
}

export function levelLabel(value?: string) {
  return value ? LEVEL_LABELS[value] || value : '-'
}

export function levelType(value?: string) {
  if (value === 'HIGH') return 'danger'
  if (value === 'MEDIUM') return 'warning'
  return 'info'
}

export function statusType(value?: string) {
  if (value === 'PENDING') return 'danger'
  if (value === 'REPAIRED') return 'success'
  if (value === 'CONFIRMED') return 'warning'
  return 'info'
}

export function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

export function percent(value?: number) {
  if (value === null || value === undefined) return '0%'
  return `${(Number(value) * 100).toFixed(0)}%`
}
