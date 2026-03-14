<template>
  <el-container class="chat-wrapper">
    <el-header class="header">
      <el-button class="menu-btn" :icon="Operation" text @click="drawerVisible = true" />
      <span class="header-title">AI Chat</span>
      <div class="header-right">
        <el-text type="info">{{ username }}</el-text>
        <el-button type="info" size="small" @click="$emit('logout')">退出</el-button>
      </div>
    </el-header>

    <el-drawer v-model="drawerVisible" direction="ltr" size="280px" :with-header="false" class="sidebar-drawer">
      <div class="sidebar-content">
        <el-button type="primary" :icon="Plus" style="width: 100%; margin-bottom: 12px" @click="newChat">
          新对话
        </el-button>
        <el-scrollbar>
          <div v-if="loadingConversations" class="conv-loading">
            <el-icon class="is-loading"><Loading /></el-icon>
            <el-text type="info" size="small">加载中...</el-text>
          </div>
          <template v-else>
          <div
            v-for="conv in conversations"
            :key="conv.conversationId"
            :class="['conv-item', { active: conv.conversationId === currentConvId }]"
            @click="loadConversation(conv.conversationId)"
          >
            <el-icon><ChatDotRound /></el-icon>
            <el-text class="conv-title" truncated>{{ conv.title }}</el-text>
            <el-button :icon="Delete" text size="small" class="conv-delete"
              @click.stop="removeConversation(conv.conversationId)" />
          </div>
          <el-empty v-if="conversations.length === 0" description="暂无历史对话" :image-size="60" />
          </template>
        </el-scrollbar>
      </div>
    </el-drawer>

    <el-container class="main">
      <el-aside width="260px" class="sidebar-aside">
        <el-button type="primary" :icon="Plus" style="width: 100%; margin-bottom: 12px" @click="newChat">
          新对话
        </el-button>
        <el-scrollbar>
          <div v-if="loadingConversations" class="conv-loading">
            <el-icon class="is-loading"><Loading /></el-icon>
            <el-text type="info" size="small">加载中...</el-text>
          </div>
          <template v-else>
          <div
            v-for="conv in conversations"
            :key="conv.conversationId"
            :class="['conv-item', { active: conv.conversationId === currentConvId }]"
            @click="loadConversation(conv.conversationId)"
          >
            <el-icon><ChatDotRound /></el-icon>
            <el-text class="conv-title" truncated>{{ conv.title }}</el-text>
            <el-button :icon="Delete" text size="small" class="conv-delete"
              @click.stop="removeConversation(conv.conversationId)" />
          </div>
          <el-empty v-if="conversations.length === 0" description="暂无历史对话" :image-size="60" />
          </template>
        </el-scrollbar>
      </el-aside>

      <el-main class="chat-main">
        <el-scrollbar ref="scrollbarRef" class="chat-scrollbar">
          <div class="chat-container" ref="chatContainer">
            <div v-if="loadingChat" class="chat-loading">
              <el-icon class="is-loading" :size="24"><Loading /></el-icon>
              <el-text type="info">加载对话中...</el-text>
            </div>
            <template v-else>
            <div v-for="(msg, i) in messages" :key="i" :class="['message', msg.role]">
              <div v-if="msg.role === 'assistant'" class="md-content" v-html="renderMd(msg.content)"></div>
              <template v-else>{{ msg.content }}</template>
            </div>
            <div v-if="thinking" class="message assistant">
              <el-text type="info" size="small"><el-icon class="is-loading"><Loading /></el-icon> 思考中...</el-text>
            </div>
            </template>
          </div>
        </el-scrollbar>

        <div class="input-area">
          <el-input
            ref="inputRef"
            v-model="input"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 4 }"
            placeholder="输入消息... (Enter 发送, Shift+Enter 换行)"
            @keydown.enter.exact.prevent="send"
            :disabled="sending"
            resize="none"
          />
          <el-button type="primary" :icon="Promotion" circle :disabled="sending || !input.trim()" @click="send" />
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { Plus, Delete, Promotion, Operation, ChatDotRound, Loading } from '@element-plus/icons-vue'
import { marked } from 'marked'
import { chatStream, listConversations, getConversation, deleteConversation } from '../api.js'

marked.setOptions({ breaks: true })

function renderMd(text) {
  if (!text) return ''
  return marked.parse(text)
}

defineProps({ username: String })
defineEmits(['logout'])

const messages = ref([])
const input = ref('')
const sending = ref(false)
const thinking = ref(false)
const chatContainer = ref(null)
const scrollbarRef = ref(null)
const inputRef = ref(null)
const drawerVisible = ref(false)
const conversations = ref([])
const currentConvId = ref(null)
const loadingConversations = ref(false)
const loadingChat = ref(false)

function generateConvId() {
  return Date.now().toString(36) + Math.random().toString(36).substring(2, 6)
}

function scrollToBottom() {
  nextTick(() => {
    if (scrollbarRef.value) {
      scrollbarRef.value.setScrollTop(chatContainer.value?.scrollHeight || 99999)
    }
  })
}

async function refreshConversations() {
  loadingConversations.value = true
  try {
    conversations.value = await listConversations()
  } catch (e) {
    // ignore
  } finally {
    loadingConversations.value = false
  }
}

function newChat() {
  currentConvId.value = null
  messages.value = []
  drawerVisible.value = false
  nextTick(() => inputRef.value?.focus())
}

async function loadConversation(convId) {
  loadingChat.value = true
  try {
    const msgs = await getConversation(convId)
    messages.value = msgs
    currentConvId.value = convId
    drawerVisible.value = false
    scrollToBottom()
  } catch (e) {
    messages.value = [{ role: 'assistant', content: '加载对话失败: ' + e.message }]
  } finally {
    loadingChat.value = false
  }
}

async function removeConversation(convId) {
  try {
    await deleteConversation(convId)
    if (currentConvId.value === convId) {
      newChat()
    }
    await refreshConversations()
  } catch (e) {
    // ignore
  }
}

async function send() {
  const message = input.value.trim()
  if (!message || sending.value) return

  if (!currentConvId.value) {
    currentConvId.value = generateConvId()
  }

  input.value = ''
  sending.value = true
  thinking.value = true

  messages.value.push({ role: 'user', content: message })
  scrollToBottom()

  try {
    thinking.value = false
    messages.value.push({ role: 'assistant', content: '' })
    const aiMsgIndex = messages.value.length - 1

    await chatStream(message, (fullText) => {
      messages.value[aiMsgIndex].content = fullText
      scrollToBottom()
    }, null, currentConvId.value)

    await refreshConversations()
  } catch (e) {
    thinking.value = false
    messages.value.push({ role: 'assistant', content: '出错了: ' + e.message })
  } finally {
    sending.value = false
    scrollToBottom()
    nextTick(() => inputRef.value?.focus())
  }
}

onMounted(() => {
  refreshConversations()
})
</script>

<style scoped>
.chat-wrapper {
  height: 100vh;
  flex-direction: column;
}
.header {
  background: var(--el-color-primary);
  color: white;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 20px;
  height: 56px;
}
.header-title {
  font-size: 18px;
  font-weight: 600;
}
.menu-btn {
  color: white !important;
  font-size: 20px;
}
.header-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
}
.header-right .el-text { color: rgba(255,255,255,0.85); }
.header-right .el-button { color: white; border-color: rgba(255,255,255,0.4); }

.main {
  flex: 1;
  overflow: hidden;
}

/* Desktop sidebar */
.sidebar-aside {
  background: var(--el-bg-color-page);
  border-right: 1px solid var(--el-border-color-lighter);
  padding: 12px;
  display: none;
  flex-direction: column;
  overflow: hidden;
}
@media (min-width: 768px) {
  .sidebar-aside { display: flex; }
  .menu-btn { display: none !important; }
}

/* Drawer sidebar content */
.sidebar-content {
  padding: 12px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.conv-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  margin: 2px 0;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
}
.conv-item:hover { background: var(--el-fill-color-light); }
.conv-item.active { background: var(--el-color-primary-light-9); }
.conv-title { flex: 1; min-width: 0; }
.conv-delete { opacity: 0; transition: opacity 0.2s; }
.conv-item:hover .conv-delete { opacity: 1; }

.chat-main {
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
}
.chat-scrollbar {
  flex: 1;
}
.chat-container {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.message {
  max-width: 75%;
  padding: 10px 14px;
  border-radius: 12px;
  line-height: 1.6;
  word-wrap: break-word;
  font-size: 14px;
}
.message.user {
  align-self: flex-end;
  background: var(--el-color-primary-light-8);
  border-bottom-right-radius: 4px;
  white-space: pre-wrap;
}
.message.assistant {
  align-self: flex-start;
  background: var(--el-bg-color);
  border-bottom-left-radius: 4px;
  box-shadow: var(--el-box-shadow-lighter);
}

/* Markdown styles */
.md-content :deep(p) { margin: 0.4em 0; }
.md-content :deep(p:first-child) { margin-top: 0; }
.md-content :deep(p:last-child) { margin-bottom: 0; }
.md-content :deep(pre) {
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
  padding: 10px 12px;
  overflow-x: auto;
  font-size: 13px;
}
.md-content :deep(code) {
  background: var(--el-fill-color-light);
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 13px;
}
.md-content :deep(pre code) { background: none; padding: 0; }
.md-content :deep(ul), .md-content :deep(ol) { padding-left: 1.5em; margin: 0.4em 0; }
.md-content :deep(blockquote) {
  border-left: 3px solid var(--el-border-color);
  padding-left: 10px;
  margin: 0.4em 0;
  color: var(--el-text-color-secondary);
}
.md-content :deep(table) { border-collapse: collapse; margin: 0.4em 0; }
.md-content :deep(th), .md-content :deep(td) {
  border: 1px solid var(--el-border-color-lighter);
  padding: 4px 8px;
}

.input-area {
  padding: 12px 16px;
  display: flex;
  gap: 10px;
  align-items: flex-end;
  border-top: 1px solid var(--el-border-color-lighter);
}

.conv-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 20px 0;
}

.chat-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px 0;
}
</style>
