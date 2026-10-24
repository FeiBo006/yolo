import axios from 'axios'
import request from './request'

export const authApi = {
  login: (data: { username: string; password: string }) => request.post('/auth/login', data),
  me: () => request.get('/auth/me'),
  logout: () => request.post('/auth/logout'),
}

export const siteApi = {
  list: (params?: any) => request.get('/sites', { params }),
  detail: (id: number) => request.get(`/sites/${id}`),
  create: (data: any) => request.post('/sites', data),
  update: (id: number, data: any) => request.put(`/sites/${id}`, data),
  remove: (id: number) => request.delete(`/sites/${id}`),
}

export const cameraApi = {
  list: (params?: any) => request.get('/cameras', { params }),
  create: (data: any) => request.post('/cameras', data),
  update: (id: number, data: any) => request.put(`/cameras/${id}`, data),
  remove: (id: number) => request.delete(`/cameras/${id}`),
  test: (id: number) => request.post(`/cameras/${id}/test`),
}

export const areaApi = {
  list: (params?: any) => request.get('/areas', { params }),
  create: (data: any) => request.post('/areas', data),
  update: (id: number, data: any) => request.put(`/areas/${id}`, data),
  remove: (id: number) => request.delete(`/areas/${id}`),
}

export const ruleApi = {
  list: (params?: any) => request.get('/rules', { params }),
  create: (data: any) => request.post('/rules', data),
  update: (id: number, data: any) => request.put(`/rules/${id}`, data),
  remove: (id: number) => request.delete(`/rules/${id}`),
}

export const alarmApi = {
  page: (params: any) => request.get('/alarms', { params }),
  detail: (id: number) => request.get(`/alarms/${id}`),
  handle: (id: number, data: any) => request.put(`/alarms/${id}/handle`, data),
}

export const dashboardApi = {
  summary: () => request.get('/dashboard/summary'),
  trend: (days = 7) => request.get('/dashboard/trend', { params: { days } }),
  ranking: (days = 7) => request.get('/dashboard/ranking', { params: { days } }),
  status: (days = 7) => request.get('/dashboard/status', { params: { days } }),
}

export const detectionApi = {
  latest: (cameraId?: number) => request.get('/detections/latest', { params: { cameraId } }),
  recent: (cameraId?: number, limit = 24) => request.get('/detections/recent', { params: { cameraId, limit } }),
}

export const userApi = {
  page: (params: any) => request.get('/users', { params }),
  create: (data: any) => request.post('/users', data),
  update: (id: number, data: any) => request.put(`/users/${id}`, data),
  remove: (id: number) => request.delete(`/users/${id}`),
}

export const modelApi = {
  list: () => request.get('/models'),
}

export async function exportAlarms(params: any) {
  const token = localStorage.getItem('yuanyu_token')
  const response = await axios.get('/api/alarms/export', {
    params,
    responseType: 'blob',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  const url = URL.createObjectURL(new Blob([response.data]))
  const link = document.createElement('a')
  link.href = url
  link.download = `告警记录_${new Date().toISOString().slice(0, 10)}.xlsx`
  link.click()
  URL.revokeObjectURL(url)
}
