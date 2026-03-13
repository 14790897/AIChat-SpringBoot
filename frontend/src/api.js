const TOKEN_KEY = 'jwt_token'

let onUnauthorized = null

export function setOnUnauthorized(fn) {
  onUnauthorized = fn
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken() {
  localStorage.removeItem(TOKEN_KEY)
}

export function getUsername() {
  const token = getToken()
  if (!token) return ''
  try {
    return JSON.parse(atob(token.split('.')[1])).sub
  } catch {
    return ''
  }
}

async function authFetch(url, options = {}) {
  const token = getToken()
  const headers = { 'Content-Type': 'application/json', ...options.headers }
  if (token) {
    headers['Authorization'] = 'Bearer ' + token
  }
  const res = await fetch(url, { ...options, headers })
  if (res.status === 401 || res.status === 403) {
    removeToken()
    if (onUnauthorized) onUnauthorized()
    throw new Error('认证已过期，请重新登录')
  }
  return res
}

export async function login(username, password) {
  const res = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  })
  if (!res.ok) {
    const msg = await res.text()
    throw new Error(msg || '登录失败')
  }
  const data = await res.json()
  setToken(data.token)
  return data
}

export async function register(username, password) {
  const res = await fetch('/api/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  })
  if (!res.ok) {
    const msg = await res.text()
    throw new Error(msg || '注册失败')
  }
  const data = await res.json()
  setToken(data.token)
  return data
}

/**
 * SSE 流式聊天请求。
 * 使用 ReadableStream 手动解析 SSE，而非 EventSource（因为需要 POST + 自定义 Header）。
 * 关键：用 buffer 缓存不完整的行，防止 TCP 分包导致 data: 行被截断而丢字。
 * lines.pop() 将最后一个可能不完整的行留在 buffer，等下一个 chunk 拼接后再处理。
 */
export async function chatStream(message, onChunk, onDone, conversationId) {
  const res = await authFetch('/api/chat/stream', {
    method: 'POST',
    body: JSON.stringify({ message, conversationId })
  })
  if (!res.ok) {
    throw new Error('请求失败: ' + res.status)
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let fullText = ''
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop()
    for (const line of lines) {
      if (line.startsWith('data:')) {
        const content = line.substring(5)
        fullText += content
        onChunk(fullText)
      }
    }
  }
  if (buffer.startsWith('data:')) {
    fullText += buffer.substring(5)
    onChunk(fullText)
  }
  if (onDone) onDone(fullText)
}

export async function listConversations() {
  const res = await authFetch('/api/chat/conversations')
  if (!res.ok) throw new Error('获取对话列表失败')
  return res.json()
}

export async function getConversation(conversationId) {
  const res = await authFetch('/api/chat/conversation?conversationId=' + encodeURIComponent(conversationId))
  if (!res.ok) throw new Error('获取对话失败')
  return res.json()
}

export async function deleteConversation(conversationId) {
  const res = await authFetch('/api/chat/clear', {
    method: 'POST',
    body: JSON.stringify({ conversationId })
  })
  if (!res.ok) throw new Error('删除对话失败')
  return res.text()
}
