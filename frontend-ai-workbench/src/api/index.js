export const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8088'
const TOKEN_KEY = 'wms.auth.token'
const SESSION_KEY = 'wms.auth.session'
const AUTH_ERROR_CODES = new Set([401, 702])

export function getAuthToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setAuthToken(token) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  }
}

export function clearAuthToken() {
  localStorage.removeItem(TOKEN_KEY)
}

function forceReLogin() {
  clearAuthToken()
  localStorage.removeItem(SESSION_KEY)
  if (!window.location.pathname.includes('/login')) {
    window.location.assign('/login')
  }
}

function buildHeaders(headers = {}) {
  const token = getAuthToken()
  return {
    ...headers,
    ...(token ? { Authorization: `Bearer ${token}` } : {})
  }
}

async function parseResponse(response) {
  if (!response.ok) {
    if (response.status === 401) forceReLogin()
    throw new Error(`请求失败: ${response.status}`)
  }
  const result = await response.json()
  if (result.code !== 200) {
    if (AUTH_ERROR_CODES.has(result.code)) {
      forceReLogin()
    }
    throw new Error(result.message || '接口返回异常')
  }
  return result.data
}

export async function apiGet(path, params = {}) {
  const url = new URL(`${API_BASE}${path}`)
  Object.entries(params).forEach(([k, v]) => {
    if (v != null && v !== '') url.searchParams.set(k, v)
  })
  const response = await fetch(url.toString(), {
    headers: buildHeaders()
  })
  return parseResponse(response)
}

export async function apiPost(path, body) {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: buildHeaders({ 'Content-Type': 'application/json' }),
    body: body === undefined ? '{}' : JSON.stringify(body)
  })
  return parseResponse(response)
}

/** POST 请求，参数走 query string（适用于 @RequestParam 接口） */
export async function apiPostQuery(path, params = {}) {
  const url = new URL(`${API_BASE}${path}`)
  Object.entries(params).forEach(([k, v]) => {
    if (v != null && v !== '') url.searchParams.set(k, String(v))
  })
  const response = await fetch(url.toString(), {
    method: 'POST',
    headers: buildHeaders({ 'Content-Type': 'application/json' }),
    body: '{}'
  })
  return parseResponse(response)
}

export async function apiPut(path, body) {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'PUT',
    headers: buildHeaders({ 'Content-Type': 'application/json' }),
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  return parseResponse(response)
}

/** 将后端返回的 /temp、/upload 等资源路径转为可访问的完整 URL */
export function resolveApiAssetUrl(path) {
  if (!path) return ''
  if (/^https?:\/\//i.test(path) || path.startsWith('blob:') || path.startsWith('data:')) return path
  return `${API_BASE}${path.startsWith('/') ? path : `/${path}`}`
}

/** multipart/form-data POST（不设置 Content-Type，由浏览器自动带 boundary） */
export async function apiPostForm(path, formData, params = {}, options = {}) {
  const url = new URL(`${API_BASE}${path}`)
  Object.entries(params).forEach(([k, v]) => {
    if (v != null && v !== '') url.searchParams.set(k, String(v))
  })
  const timeoutMs = options.timeoutMs ?? 90000
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const response = await fetch(url.toString(), {
      method: 'POST',
      headers: buildHeaders(),
      body: formData,
      signal: controller.signal
    })
    return parseResponse(response)
  } catch (error) {
    if (error?.name === 'AbortError') {
      throw new Error(`请求超时（${Math.round(timeoutMs / 1000)}s），请确认后端服务已启动`)
    }
    throw error
  } finally {
    clearTimeout(timer)
  }
}
